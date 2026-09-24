package br.com.centralmidia.android.core

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText

fun Context.dp(v:Int):Int=(v*resources.displayMetrics.density).toInt()

object Ui {
    fun page(activity: AppCompatActivity, title:String, subtitle:String=""): LinearLayout {
        val scroll=ScrollView(activity).apply { isFillViewport=true; setBackgroundColor(Color.rgb(243,247,252)) }
        val root=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(activity.dp(18),activity.dp(18),activity.dp(18),activity.dp(28)) }
        if(activity !is br.com.centralmidia.android.ui.MainActivity && activity !is br.com.centralmidia.android.ui.LoginActivity){
            val back=button(activity,"← Voltar"){ activity.finish() }; root.addView(back, lp())
        }
        root.addView(TextView(activity).apply { text=title; textSize=24f; setTextColor(Color.rgb(8,46,99)); setTypeface(typeface,1); setPadding(0,activity.dp(12),0,activity.dp(2)) })
        if(subtitle.isNotBlank()) root.addView(TextView(activity).apply { text=subtitle; textSize=13f; setTextColor(Color.rgb(85,109,139)); setPadding(0,0,0,activity.dp(14)) })
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)); activity.setContentView(scroll); return root
    }
    fun card(context:Context):MaterialCardView=MaterialCardView(context).apply { radius=context.dp(18).toFloat(); cardElevation=context.dp(2).toFloat(); setCardBackgroundColor(Color.WHITE); setContentPadding(context.dp(16),context.dp(16),context.dp(16),context.dp(16)) }
    fun button(context:Context,text:String,onClick:()->Unit):MaterialButton=MaterialButton(context).apply { this.text=text; isAllCaps=false; textSize=14f; cornerRadius=context.dp(14); setOnClickListener{onClick()} }
    fun edit(context:Context,hint:String,password:Boolean=false):TextInputEditText=TextInputEditText(context).apply { this.hint=hint; textSize=15f; setPadding(context.dp(12),context.dp(10),context.dp(12),context.dp(10)); if(password) inputType=0x00000081 }
    fun label(context:Context,text:String,size:Float=14f):TextView=TextView(context).apply { this.text=text; textSize=size; setTextColor(Color.rgb(18,50,100)); setPadding(0,context.dp(6),0,context.dp(6)) }
    fun lp(top:Int=8):LinearLayout.LayoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{topMargin=top}
}
