package br.com.centralmidia.android.ui

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.centralmidia.android.core.*
import br.com.centralmidia.android.pdf.PdfComposer

class PdfEditorActivity:BaseActivity(){
 private val items=mutableListOf<PdfComposer.Item>();private var cover:Uri?=null;private lateinit var adapter:SimpleTextAdapter;private lateinit var status:android.widget.TextView
 private val picker=registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->uris.forEach{contentResolver.takePersistableUriPermission(it,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);items+=PdfComposer.Item(it)};refresh()}
 private val coverPicker=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->cover=uri;status.text=if(uri!=null)"Capa personalizada selecionada." else "Sem capa personalizada."}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val root=Ui.page(this,"Editor PDF","Imagens + PDFs • alta qualidade • toque gira 90° • segure remove")
  root.addView(Ui.button(this,"Adicionar imagens / PDFs"){picker.launch(arrayOf("image/*","application/pdf"))},Ui.lp());root.addView(Ui.button(this,"Selecionar capa personalizada"){coverPicker.launch(arrayOf("image/*"))},Ui.lp());root.addView(Ui.button(this,"Limpar páginas"){items.clear();refresh()},Ui.lp());status=Ui.label(this,"Nenhum arquivo adicionado.",12f);root.addView(status,Ui.lp())
  val rv=RecyclerView(this).apply{layoutManager=LinearLayoutManager(this@PdfEditorActivity)};adapter=SimpleTextAdapter(emptyList(),{i->items[i].rotation=(items[i].rotation+90)%360;refresh()},{i->items.removeAt(i);refresh()});rv.adapter=adapter;root.addView(rv,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,resources.displayMetrics.heightPixels/3))
  root.addView(Ui.button(this,"Gerar PDF"){
   if(items.isEmpty()&&cover==null){toast("Adicione páginas.");return@button};status.text="Gerando PDF em alta qualidade…";io({
    val cv=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,"Central-PDF-${System.currentTimeMillis()}.pdf");put(MediaStore.Downloads.MIME_TYPE,"application/pdf");put(MediaStore.Downloads.RELATIVE_PATH,"Download/Central Inteligente de Midia/PDF")};val uri=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv)?:error("Não foi possível criar o PDF");contentResolver.openOutputStream(uri)?.use{PdfComposer.compose(this,items,cover,it)}?:error("Não foi possível abrir a saída");uri
   },{uri->status.text="PDF gerado em Downloads.";CentralDb(this).addHistory("pdf","PDF gerado",url=uri.toString())},{status.text=it.message})
  },Ui.lp(14))
 }
 private fun refresh(){adapter.submit(items.mapIndexed{i,x->"${i+1}. ${x.uri.lastPathSegment ?: x.uri}\nRotação: ${x.rotation}°"});status.text="${items.size} item(ns) • capa: ${if(cover!=null)"sim" else "não"}"}
}
