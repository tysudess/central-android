package br.com.centralmidia.android.ui

import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import br.com.centralmidia.android.core.*
import dev.ffmpegkit_maintained.ytdlp.YtDlp
import dev.ffmpegkit_maintained.ytdlp.YtDlpRequest
import java.io.File

class VideoExtractorActivity:BaseActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val root=Ui.page(this,"Extrator de Vídeos","yt-dlp Android • sem proxy • suporte a login Globoplay por cookies")
  val url=Ui.edit(this,"URL do vídeo").apply{setText(intent.getStringExtra("url").orEmpty())};val status=Ui.label(this,"",12f);root.addView(url,Ui.lp());root.addView(Ui.button(this,"Login Globoplay"){startActivity(Intent(this,GloboplayLoginActivity::class.java))},Ui.lp())
  root.addView(Ui.button(this,"Baixar vídeo"){
   val u=url.text?.toString()?.trim().orEmpty();if(u.isBlank()){toast("Informe a URL.");return@button};status.text="Preparando download…";val dir=File(getExternalFilesDir(Environment.DIRECTORY_MOVIES),"CentralDownloads").apply{mkdirs()};val before=dir.listFiles()?.associateBy{it.absolutePath}.orEmpty();io({
    val req=YtDlpRequest(u).setOutputTemplate(File(dir,"%(title).120s.%(ext)s").absolutePath).addOption("--no-playlist").addOption("-f","best[ext=mp4]/best");val cookies=File(filesDir,"cookies/globoplay.txt");if(cookies.exists())req.addOption("--cookies",cookies.absolutePath)
    val response=YtDlp.execute(req){progress,eta,line->runOnUiThread{status.text="${progress.toInt()}% • ETA ${eta}s\n${line.take(100)}"}};if(!response.isSuccess)error(response.errorOutput.ifBlank{"Falha no yt-dlp"});dir.listFiles()?.filter{it.absolutePath !in before}?.maxByOrNull{it.lastModified()} ?: dir.listFiles()?.maxByOrNull{it.lastModified()} ?: error("Arquivo não localizado")
   },{file->val saved=publishVideo(file);status.text="Concluído: ${file.name}";CentralDb(this).addHistory("video_download",file.name,url=u);toast("Vídeo salvo em Downloads/Central Inteligente de Midia/Videos")},{status.text=it.message})
  },Ui.lp());root.addView(status,Ui.lp())
 }
 private fun publishVideo(file:File):android.net.Uri{val cv=ContentValues().apply{put(MediaStore.Video.Media.DISPLAY_NAME,file.name);put(MediaStore.Video.Media.MIME_TYPE, MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "video/mp4");put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/Central Inteligente de Midia/Videos")};val uri=contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,cv)?:error("Não foi possível salvar vídeo");contentResolver.openOutputStream(uri)?.use{o->file.inputStream().use{it.copyTo(o)}};return uri}
}
