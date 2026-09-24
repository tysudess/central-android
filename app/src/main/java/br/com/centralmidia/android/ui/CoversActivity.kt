package br.com.centralmidia.android.ui

import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.centralmidia.android.core.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class CoversActivity:BaseActivity(){
 private val client by lazy{CoversClient(this)};private lateinit var adapter:SimpleTextAdapter;private val matters=mutableMapOf<String,List<String>>();private var papers=listOf<Newspaper>()
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);papers=CatalogRepository.newspapers(this);val root=Ui.page(this,"Capas","Gmail/Apps Script + fontes web, sem proxy")
  val date=Ui.edit(this,"Data AAAA-MM-DD").apply{setText(SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date()))};val status=Ui.label(this,"Toque em um jornal para abrir a melhor fonte disponível.",12f);root.addView(date,Ui.lp());root.addView(Ui.button(this,"Consultar Gmail / Apps Script"){
    status.text="Consultando…";io({client.matters(date.text.toString())},{json->parseMatters(json);status.text="Ponte Gmail consultada. ${matters.values.sumOf{it.size}} link(s) localizado(s).";refresh()},{status.text=it.message})
  },Ui.lp());root.addView(Ui.button(this,"Baixar Página 1 do Valor Econômico"){
    val d=date.text.toString();status.text="Baixando Valor…";io({client.valorPdf(d,1)},{bytes->savePdf(bytes,"Valor-Economico-$d-p1.pdf");status.text="Valor salvo em Downloads."},{status.text=it.message})
  },Ui.lp());root.addView(status,Ui.lp())
  val rv=RecyclerView(this).apply{layoutManager=LinearLayoutManager(this@CoversActivity)};adapter=SimpleTextAdapter(emptyList(),{i->val p=papers[i];val u=matters[p.name.uppercase()]?.firstOrNull()?:p.urls.firstOrNull();if(u!=null)openUrl(u)else toast("Fonte não disponível.")},{i->val p=papers[i];if(p.name.contains("VALOR",true)){val d=date.text.toString();io({client.valorManifest(d)},{o->toast(o.toString(2))})}});rv.adapter=adapter;root.addView(rv,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,resources.displayMetrics.heightPixels/2));refresh()
 }
 private fun parseMatters(root:JSONObject){matters.clear();val arr=root.optJSONArray("matters")?:return;for(i in 0 until arr.length()){val o=arr.optJSONObject(i)?:continue;val name=o.optString("name").uppercase();val urls=mutableListOf<String>();val one=o.optString("matterUrl");if(one.isNotBlank())urls+=one;val many=o.optJSONArray("urls");if(many!=null)for(j in 0 until many.length()){val u=many.optString(j);if(u.isNotBlank())urls+=u};if(urls.isNotEmpty())matters[name]=urls.distinct()}}
 private fun refresh(){adapter.submit(papers.map{p->"${p.name}\n${matters[p.name.uppercase()]?.size?:0} link(s) do Gmail • toque para abrir"})}
 private fun savePdf(bytes:ByteArray,name:String){val cv=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,name);put(MediaStore.Downloads.MIME_TYPE,"application/pdf");put(MediaStore.Downloads.RELATIVE_PATH,"Download/Central Inteligente de Midia/Capas")};val uri=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv)?:return;contentResolver.openOutputStream(uri)?.use{it.write(bytes)};CentralDb(this).addHistory("cover","Capa/PDF: $name",url=uri.toString())}
}
