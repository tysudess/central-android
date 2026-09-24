package br.com.centralmidia.android.core

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AuthException(val code: String, override val message: String) : Exception(message)

class AuthClient(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun post(payload: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(SyncedConfig.authUrl(context))
            .header("Accept", "application/json")
            .header("User-Agent", "CentralAndroid/1.0")
            .post(payload.toString().toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw AuthException("HTTP_${response.code}", "Falha HTTP ${response.code}.")
            val data = JSONObject(response.body?.string().orEmpty())
            if (!data.optBoolean("ok")) {
                throw AuthException(data.optString("code", "AUTH_DENIED"), data.optString("message", "Acesso não autorizado."))
            }
            return data
        }
    }

    fun testServer(): String {
        val request = Request.Builder().url(SyncedConfig.authUrl(context)).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw AuthException("HTTP_${response.code}", "Servidor indisponível.")
            val root = JSONObject(response.body?.string().orEmpty())
            if (!root.optBoolean("ok")) throw AuthException("SERVER", "Servidor não confirmou status online.")
            return root.optString("version", "desconhecida")
        }
    }

    fun login(username: String, password: String): AuthSession = sessionFrom(
        post(JSONObject().apply {
            put("action", "login")
            put("username", username)
            put("password", password)
            put("device_id", DeviceIdentity.id(context))
            put("device_name", DeviceIdentity.name())
            put("os", DeviceIdentity.os())
        })
    )

    fun validate(token: String): AuthSession = sessionFrom(
        post(JSONObject().apply {
            put("action", "validate")
            put("token", token)
            put("device_id", DeviceIdentity.id(context))
        })
    )

    fun changePassword(token: String, current: String, next: String): AuthSession = sessionFrom(
        post(JSONObject().apply {
            put("action", "change_password")
            put("token", token)
            put("device_id", DeviceIdentity.id(context))
            put("current_password", current)
            put("new_password", next)
        })
    )

    fun logout(token: String) {
        runCatching {
            post(JSONObject().apply {
                put("action", "logout")
                put("token", token)
                put("device_id", DeviceIdentity.id(context))
            })
        }
    }

    private fun sessionFrom(root: JSONObject): AuthSession {
        val userRoot = root.optJSONObject("user") ?: JSONObject()
        val permissionsJson = userRoot.optJSONArray("permissions")
        val permissions = buildSet {
            if (permissionsJson != null) {
                for (i in 0 until permissionsJson.length()) add(permissionsJson.optString(i).lowercase())
            }
        }
        return AuthSession(
            token = root.getString("token"),
            expiresAt = root.optString("expires_at").ifBlank { null },
            user = AuthUser(
                username = userRoot.optString("username"),
                name = userRoot.optString("name"),
                profile = userRoot.optString("profile"),
                permissions = permissions,
                mustChangePassword = userRoot.optBoolean("must_change_password", false),
            )
        )
    }
}
