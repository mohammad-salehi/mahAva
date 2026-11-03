package com.mahava.app.ui.extra

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mahava.app.MahavaApplication
import com.mahava.app.ui.theme.MahavaPrimary
import com.mahava.app.ui.theme.MahavaTextSecondary
import com.mahava.app.util.PersianDigits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** Partner chat photo / voice helpers. Everything is kept small: photos ~1280px JPEG q72 (server re-encodes to webp), voice AAC 24 kbps mono. */
object ChatMedia {
    const val MAX_VOICE_MS = 120_000L
    private const val SIDE = 1280
    private const val MAX_PHOTO_BYTES = 600 * 1024

    /** Decode a picked photo, downscale to <= 1280px, fix EXIF rotation, JPEG-compress. */
    fun compressPhoto(context: Context, uri: Uri): ByteArray {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIDE) sample *= 2
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw IllegalArgumentException("bad image")
        val rot = runCatching {
            cr.openInputStream(uri)?.use {
                when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90; 3 -> 180; 8 -> 270; else -> 0 }
            } ?: 0
        }.getOrDefault(0)
        val scale = minOf(1f, SIDE.toFloat() / maxOf(raw.width, raw.height))
        val m = Matrix().apply { if (scale < 1f) postScale(scale, scale); if (rot != 0) postRotate(rot.toFloat()) }
        val bmp = if (scale < 1f || rot != 0) Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true) else raw
        var q = 72
        var out: ByteArray
        do {
            val bos = java.io.ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, q, bos)
            out = bos.toByteArray()
            q -= 12
        } while (out.size > MAX_PHOTO_BYTES && q >= 36)
        return out
    }

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun cacheFile(context: Context, id: String) = File(File(context.cacheDir, "chat_media").apply { mkdirs() }, id.filter { it.isLetterOrDigit() })

    /** Download (once) and cache a message's media. */
    suspend fun file(context: Context, id: String): File {
        val f = cacheFile(context, id)
        if (f.exists() && f.length() > 0) return f
        val app = context.applicationContext as MahavaApplication
        val r = app.authRepository.withToken { t -> app.apiClient.extraCall("GET", "/api/mah/partner/chat/media/$id", null, t) }
        val data = r.get("data")?.asString ?: throw IllegalStateException("no data")
        withContext(Dispatchers.IO) { f.writeBytes(Base64.decode(data, Base64.DEFAULT)) }
        return f
    }

    /** Pre-seed the cache with what we just sent so our own media shows instantly. */
    fun seed(context: Context, id: String, bytes: ByteArray) { runCatching { cacheFile(context, id).writeBytes(bytes) } }
}

class VoiceRecorder(private val context: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var started = 0L

    fun start() {
        val f = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1)
        r.setAudioSamplingRate(16000)
        r.setAudioEncodingBitRate(24000)
        r.setMaxDuration(ChatMedia.MAX_VOICE_MS.toInt())
        r.setOutputFile(f.absolutePath)
        r.prepare()
        r.start()
        rec = r; file = f; started = System.currentTimeMillis()
    }

    fun elapsed() = if (rec == null) 0L else System.currentTimeMillis() - started

    /** Returns (bytes, durationMs) or null if too short / failed. */
    fun stop(): Pair<ByteArray, Long>? {
        val r = rec ?: return null
        val dur = elapsed()
        rec = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        val f = file ?: return null
        file = null
        val bytes = if (ok && dur >= 700) runCatching { f.readBytes() }.getOrNull() else null
        f.delete()
        return bytes?.let { it to minOf(dur, ChatMedia.MAX_VOICE_MS) }
    }

    fun cancel() { runCatching { rec?.stop() }; rec?.release(); rec = null; file?.delete(); file = null }
}

private fun mmss(ms: Long): String { val s = (ms / 1000).toInt(); return PersianDigits.toPersian("%d:%02d".format(s / 60, s % 60)) }

@Composable
fun ChatPhoto(id: String) {
    val context = LocalContext.current
    var img by remember(id) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        runCatching {
            val f = ChatMedia.file(context, id)
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }
        }.onSuccess { img = it; failed = it == null }.onFailure { failed = true }
    }
    Box(Modifier.widthIn(min = 160.dp, max = 240.dp).heightIn(min = 120.dp, max = 320.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x11000000)).clickable(enabled = img != null) { full = true }, contentAlignment = Alignment.Center) {
        val b = img
        when {
            b != null -> Image(b, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
            failed -> Text("عکس باز نشد", color = MahavaTextSecondary)
            else -> CircularProgressIndicator(Modifier.size(28.dp), color = MahavaPrimary, strokeWidth = 2.dp)
        }
    }
    val b = img
    if (full && b != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { full = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxWidth().background(Color.Black).clickable { full = false }, contentAlignment = Alignment.Center) {
                Image(b, null, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
            }
        }
    }
}

@Composable
fun ChatVoice(id: String, durationMs: Long, accent: Color) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var go by remember { mutableStateOf(0) }
    DisposableEffect(id) { onDispose { player?.release(); player = null } }
    LaunchedEffect(go) {
        if (go == 0) return@LaunchedEffect
        val p = player
        if (p != null) { if (p.isPlaying) { p.pause(); playing = false } else { p.start(); playing = true } }
        else {
            loading = true
            runCatching {
                val f = ChatMedia.file(context, id)
                MediaPlayer().apply { setDataSource(f.absolutePath); prepare() }
            }.onSuccess { mp ->
                mp.setOnCompletionListener { playing = false; progress = 0f }
                player = mp; mp.start(); playing = true
            }
            loading = false
        }
    }
    LaunchedEffect(playing) {
        while (playing) { player?.let { if (it.duration > 0) progress = it.currentPosition.toFloat() / it.duration }; delay(150) }
    }
    Row(Modifier.widthIn(min = 180.dp, max = 240.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(accent).clickable(enabled = !loading) { go++ }, contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            else Text(if (playing) "❚❚" else "▶", color = Color.White)
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)), color = accent, trackColor = accent.copy(alpha = 0.2f))
        Text(mmss(durationMs), style = MaterialTheme.typography.labelSmall, color = MahavaTextSecondary)
    }
}

fun voiceTimer(ms: Long) = mmss(ms)