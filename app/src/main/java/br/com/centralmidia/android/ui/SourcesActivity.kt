package br.com.centralmidia.android.ui

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.centralmidia.android.core.*
import java.net.URLEncoder

class SourcesActivity:BaseActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val root=Ui.page(this,"Fontes","Todas as fontes de notícias e vídeos do catálogo desktop")
  val news=CatalogRepository.news(this);val videos=CatalogRepository.videos(this);var modeNews=true;var labels=news.map{"📰 ${it.name}\n${it.group} • ${it.region}"};val rv=RecyclerView(this).apply{layoutManager=LinearLayoutManager(this@SourcesActivity)}
  val adapter=SimpleTextAdapter(labels,{i->if(modeNews){val n=news[i];openUrl("https://www.google.com/search?q="+URLEncoder.encode(n.name,"UTF-8"))}else openUrl(videos[i].landingUrl)});rv.adapter=adapter
  root.addView(Ui.button(this,"Alternar Notícias / Vídeos"){modeNews=!modeNews;labels=if(modeNews)news.map{"📰 ${it.name}\n${it.group} • ${it.region}"}else videos.map{"🎥 ${it.name}\n${it.group}"};adapter.submit(labels)},Ui.lp())
  root.addView(rv,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,resources.displayMetrics.heightPixels*2/3))
 }
}
