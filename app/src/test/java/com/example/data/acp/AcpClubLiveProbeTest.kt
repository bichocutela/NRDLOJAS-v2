package com.example.data.acp

import com.example.BuildConfig
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Temporary live smoke probe for the ACP Clube catalog.
 * It prints only status/shape/count metadata. Credentials and bearer tokens are never logged.
 */
class AcpClubLiveProbeTest {
    private val client = OkHttpClient.Builder().followRedirects(false).build()

    @Test
    fun probeClubCatalogResponseShapes() {
        val login = BuildConfig.ACP_LOGIN
        val password = BuildConfig.ACP_PASSWORD
        assumeTrue("ACP credentials unavailable", login.isNotBlank() && password.isNotBlank())

        val jar = mutableListOf<String>()

        fun request(request: Request): Pair<Int, String> {
            val withCookies = request.newBuilder().apply {
                if (jar.isNotEmpty()) header("Cookie", jar.joinToString("; "))
            }.build()
            client.newCall(withCookies).execute().use { response ->
                response.headers("Set-Cookie").forEach { header ->
                    val cookie = header.substringBefore(';')
                    val name = cookie.substringBefore('=')
                    jar.removeAll { it.substringBefore('=') == name }
                    jar += cookie
                }
                return response.code to response.body?.string().orEmpty()
            }
        }

        val csrfBody = request(Request.Builder().url("${AcpApi.ORIGIN}/api/auth/csrf").get().build()).second
        val csrf = JSONObject(csrfBody).getString("csrfToken")
        val signIn = Request.Builder()
            .url("${AcpApi.ORIGIN}/api/auth/callback/credentials")
            .header("Origin", AcpApi.ORIGIN)
            .header("Referer", "${AcpApi.ORIGIN}/login")
            .post(
                FormBody.Builder()
                    .add("login", login)
                    .add("password", password)
                    .add("csrfToken", csrf)
                    .add("callbackUrl", "${AcpApi.ORIGIN}/print-template")
                    .add("json", "true")
                    .build()
            ).build()
        request(signIn)

        val sessionBody = request(Request.Builder().url("${AcpApi.ORIGIN}/api/auth/session").get().build()).second
        val user = JSONObject(sessionBody).getJSONObject("user")
        val token = user.getString("accessToken")
        val base = if (user.optBoolean("proxyEnable")) "${AcpApi.ORIGIN}/api/proxy/api/v1/" else "${AcpApi.API_ORIGIN}/api/v1/"

        fun apiGet(path: String, params: List<Pair<String, String>>): Pair<Int, String> {
            val url = (base + path).toHttpUrl().newBuilder().apply {
                params.forEach { (key, value) -> addQueryParameter(key, value) }
            }.build()
            return request(
                Request.Builder().url(url)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-cache")
                    .get().build()
            )
        }

        fun shape(raw: String): String = when (raw.trimStart().firstOrNull()) {
            '{' -> "object"
            '[' -> "array"
            '<' -> "html"
            null -> "empty"
            else -> "other"
        }

        fun itemCount(raw: String): Int = runCatching {
            when (shape(raw)) {
                "object" -> JSONObject(raw).optJSONArray("items")?.length() ?: -1
                "array" -> JSONArray(raw).length()
                else -> -1
            }
        }.getOrDefault(-1)

        val categories = apiGet("ProductCategory/all", listOf("pageSize" to "100", "pageIndex" to "0"))
        println("ACP_PROBE categories status=${categories.first} shape=${shape(categories.second)} items=${itemCount(categories.second)} bytes=${categories.second.length}")

        val categoryItems = when (shape(categories.second)) {
            "object" -> JSONObject(categories.second).optJSONArray("items") ?: JSONArray()
            "array" -> JSONArray(categories.second)
            else -> JSONArray()
        }
        var clubId: String? = null
        for (i in 0 until categoryItems.length()) {
            val item = categoryItems.optJSONObject(i) ?: continue
            val name = item.optString("description").lowercase()
                .replace(" ", "").replace("-", "").replace("_", "")
            if (name.contains("clubedevantagens") || name.contains("clubvantagens")) {
                clubId = item.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                break
            }
        }
        println("ACP_PROBE clubCategoryFound=${clubId != null}")

        if (clubId != null) {
            val page20 = apiGet("Product/all", listOf("pageSize" to "20", "pageIndex" to "0", "productCategoryIds" to clubId!!))
            println("ACP_PROBE products20 status=${page20.first} shape=${shape(page20.second)} items=${itemCount(page20.second)} bytes=${page20.second.length}")

            val page250 = apiGet("Product/all", listOf("pageSize" to "250", "pageIndex" to "0", "productCategoryIds" to clubId!!))
            println("ACP_PROBE products250 status=${page250.first} shape=${shape(page250.second)} items=${itemCount(page250.second)} bytes=${page250.second.length}")
        }

        assertTrue(true)
    }
}
