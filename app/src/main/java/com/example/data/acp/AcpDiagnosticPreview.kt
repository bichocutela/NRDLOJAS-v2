package com.example.data.acp

import org.json.JSONArray
import org.json.JSONObject

/** Clipboard previews must never retain a second full catalog or campaign tree. */
internal fun diagnosticPreview(response: JSONObject): JSONObject {
    var remainingNodes = 800
    var remainingChars = 24_000
    fun preview(value: Any?, depth: Int): Any? {
        if (remainingNodes-- <= 0 || remainingChars <= 0 || depth > 8) return "[TRUNCATED]"
        return when (value) {
            is JSONObject -> JSONObject().also { target ->
                val keys = value.keys()
                var count = 0
                while (keys.hasNext() && remainingNodes > 0 && remainingChars > 0 && count++ < 64) {
                    val key = keys.next()
                    remainingChars -= key.length
                    val sensitive = key.lowercase().replace("_", "").replace("-", "") in
                        setOf("password", "passwd", "accesstoken", "refreshtoken", "token", "authorization", "cookie", "setcookie", "csrftoken", "secret")
                    target.put(key, if (sensitive) "[REDACTED]" else preview(value.opt(key), depth + 1))
                }
                if (keys.hasNext()) target.put("nrdPreviewTruncated", true)
            }
            is JSONArray -> JSONArray().also { target ->
                var index = 0
                while (index < value.length() && index < 10 && remainingNodes > 0 && remainingChars > 0) {
                    target.put(preview(value.opt(index++), depth + 1))
                }
                if (index < value.length()) target.put("[TRUNCATED]")
            }
            is String -> {
                // Embedded JSON may contain credentials; full redaction is handled by explicit evidence export.
                if (value.trimStart().startsWith("{") || value.trimStart().startsWith("[")) "[JSON: use evidence export]"
                else value.take(minOf(512, remainingChars.coerceAtLeast(0))).also { remainingChars -= it.length }
            }
            else -> value
        }
    }
    return preview(response, 0) as JSONObject
}
