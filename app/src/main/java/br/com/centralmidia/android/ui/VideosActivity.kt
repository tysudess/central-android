package br.com.centralmidia.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.centralmidia.android.core.*
import java.net.URLEncoder

class VideosActivity:BaseActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val root=Ui.page(this,"Vídeos","Catálogo de fontes de vídeo sincronizado com o desktop")
  val query=Ui.edit(this,"Termo para pesquisar nos canais");root.addView(query,Ui.lp())
  val sources=CatalogRepository.videos(this);val filtered=sources.toMutableList();val rv=RecyclerView(this).apply{layoutManager=LinearLayoutManager(this@VideosActivity)}
  val adapter=SimpleTextAdapter(filtered.map{"${it.name}\n${it.group}${if(it.state.isNotBlank())" • ${it.state}" else ""}"},{i->
    val src=filtered[i];val q=query.text?.toString()?.trim().orEmpty();val effective=listOf(src.searchPrefix,q).filter{it.isNotBlank()}.joinToString(" ")
    val url=when{effective.isBlank()->src.landingUrl;src.searchUrlTemplate.isNotBlank()->src.searchUrlTemplate.replace("{query}",URLEncoder.encode(effective,"UTF-8"));else->src.landingUrl};openUrl(url);CentralDb(this).addHistory("video","${src.name}: $q",url=url)
  });rv.adapter=adapter
  root.addView(Ui.button(this,"Abrir Extrator de Vídeos"){startActivity(Intent(this,VideoExtractorActivity::class.java))},Ui.lp())
  root.addView(rv,android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,resources.displayMetrics.heightPixels*2/3))
 }
}
