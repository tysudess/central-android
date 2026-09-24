package br.com.centralmidia.android.core

import android.content.Context
import org.json.JSONObject

object SyncedConfig {
    private var cached: JSONObject? = null

    private fun root(context: Context): JSONObject {
        cached?.let { return it }
        val text = context.assets.open("synced_config.json")
            .bufferedReader().use { it.readText() }
        return JSONObject(text).also { cached = it }
    }

    fun authUrl(context: Context): String = root(context).optString("auth_api_url")
    fun coversUrl(context: Context): String = root(context).optString("covers_apps_script_url")
    fun coversAccessKey(context: Context): String = root(context).optString("covers_access_key")
    fun desktopHead(context: Context): String = root(context).optString("desktop_head_sha")
}
