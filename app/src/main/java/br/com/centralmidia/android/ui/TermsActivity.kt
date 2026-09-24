package br.com.centralmidia.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.centralmidia.android.core.*

class TermsActivity:BaseActivity(){
 private lateinit var db:CentralDb;private lateinit var adapter:SimpleTextAdapter;private var rows=listOf<Pair<Long,String>>()
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);db=CentralDb(this);val root=Ui.page(this,"Termos","Termos usados na automação e em buscas rápidas")
  val edit=Ui.edit(this,"Novo termo");root.addView(edit,Ui.lp());root.addView(Ui.button(this,"Adicionar"){val t=edit.text?.toString()?.trim().orEmpty();if(t.isNotBlank()){db.addTerm(t);edit.setText("");refresh()}},Ui.lp())
  root.addView(Ui.label(this,"Toque para pesquisar. Segure para excluir.",12f),Ui.lp());val rv=RecyclerView(this).apply{layoutManager=LinearLayoutManager(this@TermsActivity)};adapter=SimpleTextAdapter(emptyList(),{i->startActivity(Intent(this,NewsActivity::class.java).putExtra("query",rows[i].second))},{i->db.deleteTerm(rows[i].first);refresh()});rv.adapter=adapter;root.addView(rv,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,resources.displayMetrics.heightPixels/2));refresh()
 }
 private fun refresh(){rows=db.terms();adapter.submit(rows.map{it.second})}
}
