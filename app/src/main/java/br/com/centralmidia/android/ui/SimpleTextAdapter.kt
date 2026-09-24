package br.com.centralmidia.android.ui

import android.graphics.Color
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.com.centralmidia.android.core.dp

class SimpleTextAdapter(
    private var items:List<String>,
    private val onClick:(Int)->Unit = {},
    private val onLong:(Int)->Unit = {},
): RecyclerView.Adapter<SimpleTextAdapter.Holder>() {
    class Holder(val box:LinearLayout):RecyclerView.ViewHolder(box)
    fun submit(next:List<String>){ items=next; notifyDataSetChanged() }
    override fun getItemCount()=items.size
    override fun onCreateViewHolder(parent:ViewGroup, viewType:Int):Holder {
        val c=parent.context
        val box=LinearLayout(c).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(c.dp(14),c.dp(12),c.dp(14),c.dp(12))
            setBackgroundColor(Color.WHITE)
        }
        val text=TextView(c).apply { tag="text"; textSize=14f; setTextColor(Color.rgb(18,50,100)); setLineSpacing(0f,1.12f) }
        box.addView(text,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
        return Holder(box)
    }
    override fun onBindViewHolder(holder:Holder, position:Int){
        (holder.box.findViewWithTag<TextView>("text")).text=items[position]
        holder.box.setOnClickListener{onClick(position)}
        holder.box.setOnLongClickListener{onLong(position);true}
        val lp=holder.box.layoutParams as? RecyclerView.LayoutParams
        lp?.bottomMargin=holder.box.context.dp(8)
    }
}
