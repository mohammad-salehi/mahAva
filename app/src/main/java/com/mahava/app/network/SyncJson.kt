package com.mahava.app.network

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Reads the /api/mah/data answers by hand. The server sends `"data": null` for deleted items
 * (tombstones in an incremental pull, deleted copies in push conflicts); Gson 2.11 cannot bind
 * JSON null to a `JsonObject?` field and threw "Expected a JsonObject but was JsonNull", so every
 * round after any deletion failed after the push, the synced state was never saved and the same
 * changes stayed "pending" (and the background job backed off for hours).
 */
object SyncJson {
    fun parsePull(text: String): DataPullDto {
        val o = root(text)
        return DataPullDto(
            now = o.long("now"),
            cursor = o.long("cursor"),
            more = o.bool("more"),
            full = o.bool("full"),
            records = o.records("records")
        )
    }

    fun parsePush(text: String): DataPushDto {
        val o = root(text)
        return DataPushDto(
            now = o.long("now"),
            applied = o.long("applied").toInt(),
            conflicts = o.records("conflicts")
        )
    }

    private fun root(text: String): JsonObject {
        val el = JsonParser.parseString(text)
        if (!el.isJsonObject) throw com.google.gson.JsonParseException("not a JSON object")
        return el.asJsonObject
    }

    private fun JsonObject.prim(name: String): JsonElement? = get(name)?.takeIf { it.isJsonPrimitive }
    private fun JsonObject.long(name: String): Long = prim(name)?.let { runCatching { it.asLong }.getOrNull() } ?: 0L
    private fun JsonObject.bool(name: String): Boolean = prim(name)?.let { runCatching { it.asBoolean }.getOrNull() } ?: false
    private fun JsonObject.str(name: String): String = prim(name)?.asString ?: ""

    private fun JsonObject.records(name: String): List<SyncRecordDto> {
        val arr = get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val r = el.asJsonObject
            val kind = r.str("kind")
            if (kind.isBlank()) return@mapNotNull null
            val data = r.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            SyncRecordDto(
                kind = kind,
                key = r.str("key"),
                data = data,
                deleted = r.bool("deleted"),
                updatedAt = r.long("updatedAt"),
                serverUpdatedAt = r.long("serverUpdatedAt")
            )
        }
    }
}
