package br.com.centralmidia.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.centralmidia.android.core.*

class NewsActivity:BaseActivity(){
 private val client=GoogleNewsClient();private var items=listOf<NewsItem>();private lateinit var adapter:SimpleTextAdapter
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val root=Ui.page(this,"Notícias","Busca de notícias com filtros de fonte")
  val query=Ui.edit(this,"Termo de busca").apply{setText(intent.getStringExtra("query").orEmpty())};root.addView(query,Ui.lp())
  val sources=listOf("Todas as fontes")+CatalogRepository.news(this).map{it.name}.distinct().sorted();val spinner=Spinner(this).apply{adapter=ArrayAdapter(this@NewsActivity,android.R.layout.simple_spinner_dropdown_item,sources)};root.addView(spinner,Ui.lp())
  val status=Ui.label(this,"",12f);val searchButton=Ui.button(this,"Buscar agora"){
    var q=query.text?.toString()?.trim().orEmpty();if(q.isBlank()){toast("Informe um termo.");return@button};if(spinner.selectedItemPosition>0)q+=" \"${spinner.selectedItem}\"";status.text="Buscando…"
    io({client.search(q)},{list->items=list;adapter.submit(list.map{"${it.title}\n${it.source} • ${it.pubDate}"});status.text="${list.size} resultado(s)";CentralDb(this).addHistory("news","Busca: $q","${list.size} resultados")},{status.text=it.message})
  };root.addView(searchButton,Ui.lp());root.addView(status,Ui.lp())
  val rv=RecyclerView(this).apply{layoutManager=LinearLayoutManager(this@NewsActivity)};adapter=SimpleTextAdapter(emptyList(),{i->openUrl(items[i].link)},{i->startActivity(Intent(this,NewsExtractorActivity::class.java).putExtra("url",items[i].link))});rv.adapter=adapter;root.addView(rv,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,resources.displayMetrics.heightPixels/2))
  if(query.text?.isNotBlank()==true) searchButton.performClick()
 }
}
