package br.com.centralmidia.android.ui

import android.content.Intent
import android.os.Bundle
import br.com.centralmidia.android.core.Ui

class AccountActivity:BaseActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val s=auth.session;val root=Ui.page(this,"Minha conta","Gerencie seu acesso à Central")
  root.addView(Ui.label(this,"Nome: ${s?.user?.name.orEmpty()}\nUsuário: ${s?.user?.username.orEmpty()}\nPerfil: ${s?.user?.profile.orEmpty()}\nPermissões: ${s?.user?.permissions?.sorted()?.joinToString(", ").orEmpty()}",15f),Ui.lp(16))
  root.addView(Ui.button(this,"Alterar senha"){PasswordDialogs.show(this,false){recreate()}},Ui.lp(16))
  root.addView(Ui.button(this,"Sair da conta"){io({auth.logout()},{startActivity(Intent(this,LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK));finishAffinity()})},Ui.lp())
 }
}
