package br.com.centralmidia.android.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import br.com.centralmidia.android.core.Ui
import java.io.File

class GloboplayLoginActivity:BaseActivity(){
 @SuppressLint("SetJavaScriptEnabled")
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val root=Ui.page(this,"Login Globoplay","Faça login normalmente; depois toque em Salvar sessão")
  val web=WebView(this).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.userAgentString=settings.userAgentString+" CentralAndroid";CookieManager.getInstance().setAcceptCookie(true);webViewClient=WebViewClient();webChromeClient=WebChromeClient();loadUrl("https://globoplay.globo.com/")};root.addView(web,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,resources.displayMetrics.heightPixels*2/3))
  root.addView(Ui.button(this,"Salvar sessão Globoplay"){saveCookies();toast("Sessão salva para o Extrator de Vídeos.");finish()},Ui.lp())
 }
 private fun saveCookies(){
  val domains=listOf("https://globoplay.globo.com/","https://globo.com/","https://login.globo.com/","https://accounts.globo.com/")
  val file=File(filesDir,"cookies/globoplay.txt");file.parentFile?.mkdirs();val lines=mutableListOf("# Netscape HTTP Cookie File")
  for(url in domains){val host=android.net.Uri.parse(url).host?:continue;val raw=CookieManager.getInstance().getCookie(url).orEmpty();for(part in raw.split(';')){val p=part.trim();val eq=p.indexOf('=');if(eq<=0)continue;val name=p.substring(0,eq).trim();val value=p.substring(eq+1);lines += ".${host.removePrefix("www.")}\tTRUE\t/\tTRUE\t2147483647\t$name\t$value"}}
  file.writeText(lines.distinct().joinToString("\n")+"\n")
 }
}
