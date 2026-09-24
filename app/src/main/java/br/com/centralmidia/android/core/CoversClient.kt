package br.com.centralmidia.android.core

import android.content.Context
import android.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CoversClient(private val context:Context) {
    private val client=OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(35,TimeUnit.SECONDS).build()
    private fun get(action:String,date:String,page:Int=0):JSONObject {
        val builder=SyncedConfig.coversUrl(context).toHttpUrl().newBuilder()
            .addQueryParameter("key",SyncedConfig.coversAccessKey(context)).addQueryParameter("action",action).addQueryParameter("date",date)
        if(page>0) builder.addQueryParameter("page",page.toString())
        val r=Request.Builder().url(builder.build()).header("User-Agent","PrincipaisCapas/Android-Central").build()
        return client.newCall(r).execute().use { resp-> if(!resp.isSuccessful) error("HTTP ${resp.code}"); JSONObject(resp.body?.string().orEmpty()) }
    }
    fun matters(date:String):JSONObject=get("matters",date)
    fun valorManifest(date:String):JSONObject=get("valor_manifest",date)
    fun valorPdf(date:String,page:Int):ByteArray {
        val root=get("valor_pdf",date,page); if(!root.optBoolean("ok")) error(root.optString("error","PDF indisponível"))
        val data=root.optString("dataBase64",root.optString("data_base64",root.optString("base64")))
        return Base64.decode(data,Base64.DEFAULT)
    }
}
