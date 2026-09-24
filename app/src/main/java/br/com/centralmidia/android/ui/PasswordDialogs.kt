package br.com.centralmidia.android.ui

import android.content.Context
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import br.com.centralmidia.android.core.Ui
import br.com.centralmidia.android.core.dp

object PasswordDialogs {
    fun show(
        activity:BaseActivity,
        forced:Boolean,
        prefilledCurrent:String="",
        onDone:()->Unit,
    ) {
        val box=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(activity.dp(18),0,activity.dp(18),0) }
        val current=Ui.edit(activity,"Senha atual / temporária",true).apply { setText(prefilledCurrent) }
        val next=Ui.edit(activity,"Nova senha",true)
        val confirm=Ui.edit(activity,"Confirmar nova senha",true)
        box.addView(current,Ui.lp());box.addView(next,Ui.lp());box.addView(confirm,Ui.lp())
        val dlg=MaterialAlertDialogBuilder(activity)
            .setTitle(if(forced) "Crie sua nova senha" else "Alterar senha")
            .setMessage(if(forced) "Sua senha atual é temporária. É obrigatório alterá-la para continuar." else "Informe a senha atual e escolha uma nova senha.")
            .setView(box)
            .setNegativeButton(if(forced) "Sair" else "Cancelar",null)
            .setPositiveButton("Alterar",null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val c=current.text?.toString().orEmpty(); val n=next.text?.toString().orEmpty(); val f=confirm.text?.toString().orEmpty()
                if(c.isBlank()||n.length<8||n!=f||n==c){ activity.toast("Confira as senhas. A nova senha deve ter pelo menos 8 caracteres e ser diferente da atual."); return@setOnClickListener }
                dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled=false
                activity.io({ activity.auth.changePassword(c,n) },{ dlg.dismiss(); activity.toast("Senha alterada com sucesso."); onDone() },{ dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled=true; activity.toast(it.message?:"Falha ao alterar senha") })
            }
            if(forced) dlg.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener { activity.auth.logout(); activity.finishAffinity() }
        }
        dlg.setCancelable(!forced); dlg.show()
    }
}
