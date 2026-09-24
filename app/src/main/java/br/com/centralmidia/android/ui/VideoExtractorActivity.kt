package br.com.centralmidia.android.ui

import android.content.ContentValues
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import br.com.centralmidia.android.R
import br.com.centralmidia.android.core.CentralDb
import br.com.centralmidia.android.core.dp
import com.google.android.material.button.MaterialButton
import dev.ffmpegkit_maintained.ytdlp.YtDlp
import dev.ffmpegkit_maintained.ytdlp.YtDlpRequest
import dev.ffmpegkit_maintained.ytdlp.YtDlpResponse
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Future

class VideoExtractorActivity : BaseActivity() {
    private enum class Tab { DOWNLOAD, HISTORY, SETTINGS }

    private lateinit var db: CentralDb
    private lateinit var urlInput: android.widget.EditText
    private lateinit var progress: ProgressBar
    private lateinit var progressLabel: TextView
    private lateinit var status: TextView
    private lateinit var historyContainer: LinearLayout
    private lateinit var downloadView: View
    private lateinit var historyView: View
    private lateinit var settingsView: View
    private lateinit var cancelButton: MaterialButton
    private lateinit var downloadButton: MaterialButton
    private lateinit var qualityGroup: RadioGroup
    private val tabButtons = linkedMapOf<Tab, MaterialButton>()
    private var selectedTab = Tab.DOWNLOAD
    private var downloadFuture: Future<YtDlpResponse>? = null
    @Volatile private var cancelRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = CentralDb(this)
        val root = MobileScaffold.page(this,"Extrator de Vídeos","Download direto com seleção de qualidade, histórico local e sessão Globoplay.",MobileScaffold.Tab.MORE,showAutomation = true)
        root.addView(heroCard(), MobileUi.match(dp(14)))
        root.addView(tabBar(), MobileUi.match(dp(10)))
        downloadView = downloadSection(); historyView = historySection(); settingsView = settingsSection()
        root.addView(downloadView, MobileUi.match(dp(10))); root.addView(historyView, MobileUi.match(dp(10))); root.addView(settingsView, MobileUi.match(dp(10)))
        updateTabs()
        val incoming = intent.getStringExtra("url").orEmpty().trim(); if (incoming.isNotBlank()) urlInput.setText(incoming)
    }

    private fun heroCard(): View { val card=MobileUi.card(this); val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}; val iconBox=LinearLayout(this).apply{gravity=Gravity.CENTER;background=MobileUi.rounded(MobileUi.PURPLE_TINT,dp(14).toFloat());addView(MobileUi.icon(this@VideoExtractorActivity,R.drawable.ic_video,MobileUi.PURPLE,28))}; row.addView(iconBox,LinearLayout.LayoutParams(dp(58),dp(58)).apply{marginEnd=dp(11)}); val copy=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}; copy.addView(MobileUi.text(this,"Baixar e organizar vídeos",19f,MobileUi.NAVY,true)); copy.addView(MobileUi.text(this,"yt-dlp Android • MP4 • conexão direta",10f,MobileUi.MUTED),MobileUi.match(dp(4))); row.addView(copy,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)); row.addView(MobileUi.statusChip(this,"Android",MobileUi.GREEN)); card.addView(row); return card }

    private fun tabBar(): View { val card=MobileUi.card(this,8); val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}; fun add(tab:Tab,label:String){val b=MobileUi.button(this,label,false){selectedTab=tab;updateTabs()};tabButtons[tab]=b;row.addView(b,LinearLayout.LayoutParams(0,dp(44),1f).apply{marginEnd=dp(4)})}; add(Tab.DOWNLOAD,"Download");add(Tab.HISTORY,"Histórico");add(Tab.SETTINGS,"Configurações");card.addView(row);return card }

    private fun downloadSection(): View {
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}; val downloadCard=MobileUi.card(this); val d=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}; d.addView(MobileUi.text(this,"Baixar vídeo",17f,MobileUi.NAVY,true)); d.addView(MobileUi.text(this,"Cole o link do vídeo. O arquivo será publicado em Filmes/Central Inteligente de Midia/Videos.",9.5f,MobileUi.MUTED),MobileUi.match(dp(4))); urlInput=MobileUi.input(this,"https://..."); d.addView(urlInput,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)).apply{topMargin=dp(10)}); downloadButton=MobileUi.button(this,"Baixar vídeo",true,MobileUi.BLUE,R.drawable.ic_video){startDownload()}; d.addView(downloadButton,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)).apply{topMargin=dp(8)}); downloadCard.addView(d);box.addView(downloadCard)
        val qualityCard=MobileUi.card(this);val q=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};q.addView(MobileUi.text(this,"Qualidade e formato",17f,MobileUi.NAVY,true));qualityGroup=RadioGroup(this).apply{orientation=RadioGroup.VERTICAL};listOf("360p" to "360","480p" to "480","720p HD" to "720","1080p Full HD" to "1080","Melhor disponível" to "best").forEachIndexed{index,(label,value)->qualityGroup.addView(RadioButton(this).apply{text=label;tag=value;setTextColor(MobileUi.NAVY);textSize=12f;isChecked=index==1})};q.addView(qualityGroup,MobileUi.match(dp(8)));q.addView(MobileUi.text(this,"Formato de saída: MP4",9.5f,MobileUi.MUTED),MobileUi.match(dp(5)));qualityCard.addView(q);box.addView(qualityCard,MobileUi.match(dp(10)))
        val progressCard=MobileUi.card(this);val p=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};head.addView(MobileUi.text(this,"Progresso do download",17f,MobileUi.NAVY,true),LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));progressLabel=MobileUi.text(this,"0%",17f,MobileUi.BLUE,true);head.addView(progressLabel);p.addView(head);progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;progress=0;progressTintList=ColorStateList.valueOf(MobileUi.BLUE)};p.addView(progress,MobileUi.match(dp(8)));status=MobileUi.text(this,"Cole o link, escolha a qualidade e toque em Baixar vídeo.",10f,MobileUi.MUTED);p.addView(status,MobileUi.match(dp(6)));val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};actions.addView(MobileUi.button(this,"Abrir vídeos",false,MobileUi.NAVY,R.drawable.ic_open){openVideosCollection()},LinearLayout.LayoutParams(0,dp(44),1f).apply{marginEnd=dp(4)});cancelButton=MobileUi.button(this,"Cancelar",false,MobileUi.PINK,R.drawable.ic_delete){cancelDownload()}.apply{isEnabled=false};actions.addView(cancelButton,LinearLayout.LayoutParams(0,dp(44),1f).apply{marginStart=dp(4)});p.addView(actions,MobileUi.match(dp(9)));p.addView(MobileUi.text(this,"Motor: yt-dlp Android • FFmpegKit disponível • Proxy Geral: não utilizado",9.5f,MobileUi.GREEN,true).apply{setPadding(dp(10),dp(8),dp(10),dp(8));background=MobileUi.rounded(MobileUi.GREEN_TINT,dp(9).toFloat())},MobileUi.match(dp(8)));progressCard.addView(p);box.addView(progressCard,MobileUi.match(dp(10)));return box
    }

    private fun historySection(): View { val card=MobileUi.card(this);val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};head.addView(MobileUi.text(this,"Histórico de downloads",17f,MobileUi.NAVY,true),LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));head.addView(MobileUi.button(this,"Atualizar",false){refreshHistory()});box.addView(head);historyContainer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};box.addView(historyContainer,MobileUi.match(dp(8)));card.addView(box);refreshHistory();return card }
    private fun settingsSection(): View { val card=MobileUi.card(this);val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};box.addView(MobileUi.text(this,"Configurações do extrator",17f,MobileUi.NAVY,true));box.addView(MobileUi.text(this,"Para vídeos do Globoplay, faça login no navegador interno e salve a sessão. As outras fontes usam conexão direta.",10f,MobileUi.MUTED),MobileUi.match(dp(4)));box.addView(MobileUi.button(this,"Login Globoplay",true,MobileUi.PURPLE,R.drawable.ic_open){startActivity(Intent(this,GloboplayLoginActivity::class.java))},LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)).apply{topMargin=dp(10)});box.addView(MobileUi.button(this,"Limpar sessão Globoplay",false,MobileUi.PINK,R.drawable.ic_delete){val file=File(filesDir,"cookies/globoplay.txt");if(file.exists())file.delete();toast("Sessão Globoplay removida.")},LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)).apply{topMargin=dp(8)});card.addView(box);return card }

    private fun updateTabs(){tabButtons.forEach{(tab,button)->val selected=tab==selectedTab;button.backgroundTintList=ColorStateList.valueOf(if(selected)MobileUi.BLUE else Color.WHITE);button.setTextColor(if(selected)Color.WHITE else MobileUi.NAVY);button.strokeColor=ColorStateList.valueOf(if(selected)MobileUi.BLUE else MobileUi.BORDER);button.strokeWidth=dp(1)};if(::downloadView.isInitialized)downloadView.visibility=if(selectedTab==Tab.DOWNLOAD)View.VISIBLE else View.GONE;if(::historyView.isInitialized)historyView.visibility=if(selectedTab==Tab.HISTORY)View.VISIBLE else View.GONE;if(::settingsView.isInitialized)settingsView.visibility=if(selectedTab==Tab.SETTINGS)View.VISIBLE else View.GONE;if(selectedTab==Tab.HISTORY&&::historyContainer.isInitialized)refreshHistory()}
    private fun selectedQuality():String{val id=qualityGroup.checkedRadioButtonId;val button=qualityGroup.findViewById<RadioButton>(id);return button?.tag?.toString().orEmpty().ifBlank{"480"}}

    private fun startDownload(){if(downloadFuture?.isDone==false){toast("Já existe um download em andamento.");return};val u=urlInput.text?.toString().orEmpty().trim();if(u.isBlank()){toast("Informe a URL do vídeo.");return};val quality=selectedQuality();val dir=File(getExternalFilesDir(Environment.DIRECTORY_MOVIES),"CentralDownloads").apply{mkdirs()};val before=dir.listFiles()?.associateBy{it.absolutePath}.orEmpty();val request=YtDlpRequest(u).setOutputTemplate(File(dir,"%(title).120s.%(ext)s").absolutePath).addOption("--no-playlist").addOption("-f",if(quality=="best")"best[ext=mp4]/best" else "best[height<=$quality][ext=mp4]/best[height<=$quality]");val cookies=File(filesDir,"cookies/globoplay.txt");if(cookies.exists())request.addOption("--cookies",cookies.absolutePath);cancelRequested=false;downloadButton.isEnabled=false;cancelButton.isEnabled=true;progress.progress=0;progressLabel.text="0%";status.text="Preparando download…";val future=YtDlp.executeAsync(request){value,eta,line->runOnUiThread{val pct=value.toInt().coerceIn(0,100);progress.progress=pct;progressLabel.text="$pct%";status.text="${line.take(140)}\nETA ${eta}s"}};downloadFuture=future;io({future.get()},{response->downloadButton.isEnabled=true;cancelButton.isEnabled=false;if(cancelRequested){status.text="Download cancelado.";return@io};if(!response.isSuccess){status.text=response.errorOutput.ifBlank{"Falha no yt-dlp."};return@io};val file=dir.listFiles()?.filter{it.absolutePath !in before}?.maxByOrNull{it.lastModified()}?:dir.listFiles()?.maxByOrNull{it.lastModified()}?:run{status.text="Download finalizado, mas o arquivo não foi localizado.";return@io};val saved=publishVideo(file);progress.progress=100;progressLabel.text="100%";status.text="Concluído: ${file.name}";db.addHistory("video_download",file.name,u,saved.toString());refreshHistory();toast("Vídeo salvo em Filmes/Central Inteligente de Midia/Videos.")},{error->downloadButton.isEnabled=true;cancelButton.isEnabled=false;status.text=if(cancelRequested)"Download cancelado." else(error.message?:"Falha no download.")})}
    private fun cancelDownload(){cancelRequested=true;downloadFuture?.cancel(true);cancelButton.isEnabled=false;downloadButton.isEnabled=true;status.text="Cancelamento solicitado."}
    private fun publishVideo(file:File):Uri{val cv=ContentValues().apply{put(MediaStore.Video.Media.DISPLAY_NAME,file.name);put(MediaStore.Video.Media.MIME_TYPE,MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())?:"video/mp4");put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/Central Inteligente de Midia/Videos")};val uri=contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,cv)?:error("Não foi possível salvar vídeo");contentResolver.openOutputStream(uri)?.use{out->file.inputStream().use{it.copyTo(out)}};return uri}
    private fun refreshHistory(){if(!::historyContainer.isInitialized)return;val rows=db.history(100,"video_download");historyContainer.removeAllViews();if(rows.isEmpty()){historyContainer.addView(MobileUi.text(this,"Nenhum download registrado.",11f,MobileUi.MUTED).apply{gravity=Gravity.CENTER;setPadding(dp(8),dp(24),dp(8),dp(24))});return};val fmt=SimpleDateFormat("dd/MM/yyyy HH:mm",Locale("pt","BR"));rows.forEach{row->val item=MobileUi.card(this,10);val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};box.addView(MobileUi.text(this,row[3],12.5f,MobileUi.NAVY,true));box.addView(MobileUi.text(this,fmt.format(Date(row[1].toLongOrNull()?:0L)),9.5f,MobileUi.MUTED),MobileUi.match(dp(3)));if(row[4].isNotBlank())box.addView(MobileUi.text(this,row[4],9.5f,MobileUi.MUTED),MobileUi.match(dp(3)));if(row[5].isNotBlank())box.addView(MobileUi.button(this,"Abrir vídeo",false,MobileUi.NAVY,R.drawable.ic_open){openContentUri(row[5])},LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(42)).apply{topMargin=dp(7)});item.addView(box);historyContainer.addView(item,MobileUi.match(dp(6)))}}
    private fun openContentUri(raw:String){runCatching{val uri=Uri.parse(raw);startActivity(Intent(Intent.ACTION_VIEW).apply{setDataAndType(uri,"video/*");addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)})}.onFailure{toast("Não foi possível abrir o vídeo.")}}
    private fun openVideosCollection(){runCatching{startActivity(Intent(Intent.ACTION_VIEW,MediaStore.Video.Media.EXTERNAL_CONTENT_URI))}.onFailure{toast("Abra o app Arquivos e acesse Filmes/Central Inteligente de Midia/Videos.")}}
}
