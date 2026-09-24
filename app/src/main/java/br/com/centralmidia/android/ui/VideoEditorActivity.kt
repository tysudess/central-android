package br.com.centralmidia.android.ui

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import br.com.centralmidia.android.core.*
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

class VideoEditorActivity:BaseActivity(){
 private var source:Uri?=null;private lateinit var status:android.widget.TextView
 private val picker=registerForActivityResult(ActivityResultContracts.OpenDocument()){u->source=u;status.text=if(u!=null)"Vídeo selecionado: ${u.lastPathSegment}" else "Nenhum vídeo."}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val root=Ui.page(this,"Editor de Vídeo","Cortar, comprimir, girar e extrair áudio com FFmpeg")
  status=Ui.label(this,"Nenhum vídeo selecionado.",12f);root.addView(Ui.button(this,"Selecionar vídeo"){picker.launch(arrayOf("video/*"))},Ui.lp());root.addView(status,Ui.lp())
  val start=Ui.edit(this,"Início em segundos (ex: 0)");val end=Ui.edit(this,"Fim em segundos (ex: 30)");root.addView(start,Ui.lp());root.addView(end,Ui.lp())
  root.addView(Ui.button(this,"Cortar trecho"){val s=start.text?.toString()?.ifBlank{"0"}?:"0";val e=end.text?.toString()?.ifBlank{"30"}?:"30";runFfmpeg("-ss $s -to $e -i {IN} -c copy {OUT}","corte")},Ui.lp())
  root.addView(Ui.button(this,"Comprimir vídeo"){runFfmpeg("-i {IN} -c:v mpeg4 -q:v 5 -c:a aac -b:a 128k {OUT}","comprimido")},Ui.lp())
  root.addView(Ui.button(this,"Girar 90°"){runFfmpeg("-i {IN} -vf transpose=1 -c:v mpeg4 -q:v 3 -c:a copy {OUT}","girado")},Ui.lp())
  root.addView(Ui.button(this,"Extrair áudio"){runFfmpeg("-i {IN} -vn -c:a aac -b:a 192k {OUTA}","audio",true)},Ui.lp())
 }
 private fun runFfmpeg(template:String,label:String,audio:Boolean=false){val uri=source?:run{toast("Selecione um vídeo.");return};status.text="Processando…";io({copyToCache(uri)},{input->
  val out=File(cacheDir,"central-${label}-${System.currentTimeMillis()}.${if(audio)"m4a" else "mp4"}");val command=template.replace("{IN}",q(input.absolutePath)).replace("{OUT}",q(out.absolutePath)).replace("{OUTA}",q(out.absolutePath))
  FFmpegKit.executeAsync(command){session->runOnUiThread{if(ReturnCode.isSuccess(session.returnCode)){publish(out,audio);status.text="Concluído: ${out.name}";CentralDb(this).addHistory("video_edit",label)}else status.text="Falha FFmpeg: ${session.failStackTrace ?: session.allLogsAsString.takeLast(500)}"}}
 },{status.text=it.message})}
 private fun copyToCache(uri:Uri):File{val f=File(cacheDir,"video-input-${System.currentTimeMillis()}.bin");contentResolver.openInputStream(uri)?.use{i->f.outputStream().use{i.copyTo(it)}}?:error("Não abriu o vídeo");return f}
 private fun q(path:String)="'"+path.replace("'","'\\''")+"'"
 private fun publish(file:File,audio:Boolean){val collection=if(audio)MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI;val cv=ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,file.name);put(MediaStore.MediaColumns.MIME_TYPE,if(audio)"audio/mp4" else "video/mp4");put(MediaStore.MediaColumns.RELATIVE_PATH,if(audio)"Music/Central Inteligente de Midia" else "Movies/Central Inteligente de Midia/Editados")};contentResolver.insert(collection,cv)?.let{u->contentResolver.openOutputStream(u)?.use{o->file.inputStream().use{it.copyTo(o)}}}}
}
