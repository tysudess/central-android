package br.com.centralmidia.android.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.*
import android.widget.LinearLayout
import br.com.centralmidia.android.core.Ui

class BrowserActivity:BaseActivity(){
 @SuppressLint("SetJavaScriptEnabled")
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val root=Ui.page(this,"Navegador","Navegação interna da Central")
  val url= intent.getStringExtra("url").orEmpty()
  val web=WebView(this).apply{ settings.javaScriptEnabled=true;settings.domStorageEnabled=true;webViewClient=WebViewClient();webChromeClient=WebChromeClient(); if(url.isNotBlank())loadUrl(url)}
  root.addView(web,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f).apply{height=resources.displayMetrics.heightPixels*3/4})
 }
}
