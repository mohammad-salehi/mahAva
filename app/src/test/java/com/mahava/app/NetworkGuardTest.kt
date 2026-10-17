package com.mahava.app

import com.mahava.app.network.MahApiException
import com.mahava.app.util.NetworkGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkGuardTest {
    @Test fun serverFailuresAreNetworkFailures() {
        assertTrue(NetworkGuard.isNetworkFailure(java.io.IOException("demo build is offline")))
        assertTrue(NetworkGuard.isNetworkFailure(java.net.UnknownHostException("api")))
        assertTrue(NetworkGuard.isNetworkFailure(java.net.SocketTimeoutException()))
        assertTrue(NetworkGuard.isNetworkFailure(MahApiException(500, "x")))
        assertTrue(NetworkGuard.isNetworkFailure(com.google.gson.JsonSyntaxException("<html>")))
        assertTrue(NetworkGuard.isNetworkFailure(RuntimeException(java.io.IOException("wrapped"))))
        assertFalse(NetworkGuard.isNetworkFailure(IllegalStateException("bug")))
        assertFalse(NetworkGuard.isNetworkFailure(NullPointerException()))
    }

    @Test fun offlineFailureInGuardedScopeDoesNotEscape() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + NetworkGuard.handler)
        val job = scope.launch { throw java.io.IOException("offline") }
        job.join()
        assertTrue(job.isCancelled)
        // The scope itself survives and can keep running work.
        var ran = false
        scope.launch { ran = true }.join()
        assertTrue(ran)
    }
}
