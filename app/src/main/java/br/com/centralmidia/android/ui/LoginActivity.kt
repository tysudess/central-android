package br.com.centralmidia.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import br.com.centralmidia.android.core.AuthManager
import br.com.centralmidia.android.core.Ui
import br.com.centralmidia.android.core.dp

class LoginActivity : BaseActivity() {
    private lateinit var status:android.widget.TextView
    private lateinit var username:android.widget.EditText
    private lateinit var password:android.widget.EditText
    private lateinit var loginButton:android.widget.Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root=Ui.page(this,"Central Inteligente de Mídia","Android • acesso direto à internet • sem proxy")
        val card=Ui.card(this); val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val brand=Ui.label(this,"CENTRAL INTELIGENTE DE MÍDIA",20f).apply{gravity=Gravity.CENTER;setTypeface(typeface,1)}
        box.addView(brand); username=Ui.edit(this,"Usuário"); password=Ui.edit(this,"Senha",true); box.addView(username,Ui.lp());box.addView(password,Ui.lp())
        val remember=CheckBox(this).apply{text="Manter conectado neste aparelho";isChecked=true}; box.addView(remember,Ui.lp())
        status=Ui.label(this,"Informe seu usuário e senha.",13f);box.addView(status,Ui.lp())
        loginButton=Ui.button(this,"ENTRAR"){
            val u=username.text?.toString()?.trim().orEmpty();val p=password.text?.toString().orEmpty()
            if(u.isBlank()||p.isBlank()){toast("Informe usuário e senha.");return@button}
            setBusy(true);status.text="Validando acesso…"
            io({auth.login(u,p,remember.isChecked)},{session->setBusy(false); if(session.user.mustChangePassword) PasswordDialogs.show(this,true,p){openMain()} else openMain()},{setBusy(false);status.text=it.message?:"Falha no login"})
        }
        box.addView(loginButton,Ui.lp(14))
        box.addView(Ui.button(this,"Testar servidor"){ status.text="Testando…"; io({auth.client().testServer()},{status.text="Servidor acessível • versão $it"},{status.text=it.message}) },Ui.lp())
        card.addView(box);root.addView(card,Ui.lp(20))

        val saved=auth.savedToken()
        if(!saved.isNullOrBlank()){
            setBusy(true);status.text="Validando sessão salva…"
            io({auth.validateSaved()},{s->setBusy(false); if(s!=null){ if(s.user.mustChangePassword) PasswordDialogs.show(this,true){openMain()} else openMain() } else status.text="Sessão expirada. Faça login novamente."},{setBusy(false);status.text="Faça login novamente."})
        }
    }
    private fun setBusy(b:Boolean){ loginButton.isEnabled=!b;username.isEnabled=!b;password.isEnabled=!b }
    private fun openMain(){ startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish() }
}
