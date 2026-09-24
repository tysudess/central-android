package br.com.centralmidia.android.core

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

object DeviceIdentity {
    fun id(context: Context): String {
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "android"
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("CentralInteligenteDeMidia|$raw".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun name(): String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }.joinToString(" ").take(120)

    fun os(): String = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
}
