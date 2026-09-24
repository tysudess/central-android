package br.com.centralmidia.android.ui

import android.content.Intent
import android.os.Bundle
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.work.WorkManager
import br.com.centralmidia.android.core.*

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session=auth.session ?: run { startActivity(Intent(this,LoginActivity::class.java));finish();return }
        val root=Ui.page(this,"Central Inteligente de Mídia","${session.user.name.ifBlank{session.user.username}} • ${session.user.profile}")
        val grid=GridLayout(this).apply{columnCount=2;useDefaultMargins=true}
        data class Feature(val label:String,val permission:String?,val cls:Class<*>)
        val features=listOf(
            Feature("📰 Notícias","news",NewsActivity::class.java),Feature("🎥 Vídeos","videos",VideosActivity::class.java),
            Feature("📌 Demandas","demands",DemandsActivity::class.java),Feature("🌐 Fontes","sources",SourcesActivity::class.java),
            Feature("🕘 Histórico","history",HistoryActivity::class.java),Feature("🔤 Termos","terms",TermsActivity::class.java),
            Feature("🧾 Extrator de Notícias","news_extractor",NewsExtractorActivity::class.java),Feature("🗞 Capas","covers",CoversActivity::class.java),
            Feature("📄 Editor PDF","pdf_editor",PdfEditorActivity::class.java),Feature("⬇ Extrator de Vídeos","extractor",VideoExtractorActivity::class.java),
            Feature("🎬 Editor de Vídeo","video_editor",VideoEditorActivity::class.java),Feature("⏺ Gravador de Tela","video_editor",ScreenRecorderActivity::class.java),
            Feature("⚙ Configurações","settings",SettingsActivity::class.java),Feature("👤 Minha conta",null,AccountActivity::class.java)
        )
        val all=session.user.profile.equals("ADMIN",true)||session.user.profile.equals("OPERADOR",true)
        for(f in features){
            if(!all && f.permission!=null && f.permission !in session.user.permissions) continue
            val b=Ui.button(this,f.label){startActivity(Intent(this,f.cls))}
            val lp=GridLayout.LayoutParams().apply{width=0;height=dp(74);columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);setMargins(dp(4),dp(4),dp(4),dp(4))}
            grid.addView(b,lp)
        }
        root.addView(grid,Ui.lp(12))
        root.addView(Ui.button(this,"⏹ Parar automações"){WorkManager.getInstance(this).cancelAllWorkByTag("central-monitoring");stopService(Intent(this,br.com.centralmidia.android.recording.ScreenRecordService::class.java));toast("Automações e gravações solicitadas para parar.")},Ui.lp(12))
        root.addView(Ui.label(this,"Catálogos sincronizados do desktop: ${SyncedConfig.desktopHead(this).take(7)}",11f),Ui.lp(12))
    }
}
