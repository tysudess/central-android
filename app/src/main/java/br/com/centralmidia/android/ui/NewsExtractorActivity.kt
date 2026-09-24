package br.com.centralmidia.android.ui

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.*
import android.widget.LinearLayout
import br.com.centralmidia.android.core.*
import org.json.JSONTokener
import org.json.JSONObject

class NewsExtractorActivity:BaseActivity(){
 private lateinit var web:WebView;private lateinit var output:android.widget.EditText
 @SuppressLint("SetJavaScriptEnabled")
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val root=Ui.page(this,"Extrator de Notícias","Abra uma matéria e extraia o texto principal")
  val url=Ui.edit(this,"URL da matéria").apply{setText(intent.getStringExtra("url").orEmpty())};root.addView(url,Ui.lp())
  root.addView(Ui.button(this,"Abrir página"){val u=url.text?.toString()?.trim().orEmpty();if(u.isNotBlank())web.loadUrl(u)},Ui.lp())
  web=WebView(this).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true;webViewClient=WebViewClient();webChromeClient=WebChromeClient()};root.addView(web,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,resources.displayMetrics.heightPixels/2))
  output=Ui.edit(this,"Texto extraído").apply{minLines=8;gravity=android.view.Gravity.TOP};root.addView(output,Ui.lp())
  root.addView(Ui.button(this,"Extrair texto"){
    val js="""(function(){var a=document.querySelector('article');var t=(a?a.innerText:document.body.innerText)||'';return JSON.stringify({title:document.title||'',text:t,url:location.href});})()"""
    web.evaluateJavascript(js){raw->runCatching{val inner=JSONTokener(raw).nextValue() as String;val o=JSONObject(inner);output.setText((o.optString("title")+"\n\n"+o.optString("text")).trim());CentralDb(this).addHistory("extract_news",o.optString("title"),url=o.optString("url"))}.onFailure{toast("Não foi possível extrair o texto.")}}
  },Ui.lp())
  root.addView(Ui.button(this,"Salvar TXT em Downloads"){saveText(output.text?.toString().orEmpty())},Ui.lp())
  root.addView(Ui.button(this,"Compartilhar texto"){val text=output.text?.toString().orEmpty();if(text.isBlank())return@button;startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,text)},"Compartilhar"))},Ui.lp())
  if(url.text?.isNotBlank()==true)web.loadUrl(url.text.toString())
 }
 private fun saveText(text:String){if(text.isBlank()){toast("Nada para salvar.");return};val values=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,"materia-${System.currentTimeMillis()}.txt");put(MediaStore.Downloads.MIME_TYPE,"text/plain");put(MediaStore.Downloads.RELATIVE_PATH,"Download/Central Inteligente de Midia/Noticias")};val uri=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?:return;contentResolver.openOutputStream(uri)?.use{it.write(text.toByteArray())};toast("Texto salvo em Downloads.")}
}
