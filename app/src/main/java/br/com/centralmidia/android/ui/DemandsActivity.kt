package br.com.centralmidia.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.centralmidia.android.core.*

class DemandsActivity:BaseActivity(){
 private lateinit var db:CentralDb;private lateinit var adapter:SimpleTextAdapter;private var data=listOf<Triple<Long,String,String>>()
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);db=CentralDb(this);val root=Ui.page(this,"Demandas","Cadastre assuntos e veículos para consulta rápida")
  val vehicle=Ui.edit(this,"Veículo / fonte");val subject=Ui.edit(this,"Assunto");root.addView(vehicle,Ui.lp());root.addView(subject,Ui.lp());root.addView(Ui.button(this,"Adicionar demanda"){val v=vehicle.text?.toString()?.trim().orEmpty();val s=subject.text?.toString()?.trim().orEmpty();if(s.isBlank()){toast("Informe o assunto.");return@button};db.addDemand(v,s);vehicle.setText("");subject.setText("");refresh()},Ui.lp())
  root.addView(Ui.label(this,"Toque para pesquisar. Segure para excluir.",12f),Ui.lp())
  val rv=RecyclerView(this).apply{layoutManager=LinearLayoutManager(this@DemandsActivity)};adapter=SimpleTextAdapter(emptyList(),{i->val d=data[i];startActivity(Intent(this,NewsActivity::class.java).putExtra("query",listOf(d.second,d.third).filter{it.isNotBlank()}.joinToString(" ")))},{i->db.deleteDemand(data[i].first);refresh()});rv.adapter=adapter;root.addView(rv,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,resources.displayMetrics.heightPixels/2));refresh()
 }
 private fun refresh(){data=db.demands();adapter.submit(data.map{"${it.third}\nVeículo: ${it.second.ifBlank{"Todos"}}"})}
}
