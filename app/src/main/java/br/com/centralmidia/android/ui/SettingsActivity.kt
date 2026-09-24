package br.com.centralmidia.android.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Switch
import br.com.centralmidia.android.automation.MonitoringScheduler
import br.com.centralmidia.android.core.*

class SettingsActivity:BaseActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val root=Ui.page(this,"Configurações","Android • sem Proxy Geral")
  val prefs=getSharedPreferences("central_settings",MODE_PRIVATE);val enabled=Switch(this).apply{text="Monitoramento automático";isChecked=prefs.getBoolean("auto",false)};root.addView(enabled,Ui.lp())
  val options=listOf(15,30,60,180);val spinner=Spinner(this).apply{adapter=ArrayAdapter(this@SettingsActivity,android.R.layout.simple_spinner_dropdown_item,options.map{"A cada $it minutos"});setSelection(options.indexOf(prefs.getInt("interval",30)).coerceAtLeast(0))};root.addView(spinner,Ui.lp())
  root.addView(Ui.button(this,"Salvar configurações"){val interval=options[spinner.selectedItemPosition];prefs.edit().putBoolean("auto",enabled.isChecked).putInt("interval",interval).apply();if(enabled.isChecked)MonitoringScheduler.schedule(this,interval.toLong())else MonitoringScheduler.cancel(this);toast("Configurações salvas.")},Ui.lp())
  root.addView(Ui.label(this,"No Android, tarefas periódicas usam WorkManager. O sistema define mínimo de 15 minutos e pode ajustar o horário para economizar bateria. Não existe configuração de proxy nesta versão.",12f),Ui.lp())
 }
}
