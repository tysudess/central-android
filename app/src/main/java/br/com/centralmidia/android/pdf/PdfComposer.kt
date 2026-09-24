package br.com.centralmidia.android.pdf

import android.content.ContentResolver
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlin.math.max

object PdfComposer {
 data class Item(val uri:Uri,var rotation:Int=0)
 fun compose(context:Context, items:List<Item>, cover:Uri?, out:java.io.OutputStream){
  val doc=PdfDocument();var pageNo=1
  try{
   if(cover!=null){decodeImage(context.contentResolver,cover)?.let{pageNo=addBitmap(doc,it,0,pageNo);it.recycle()}}
   for(item in items){
    val mime=context.contentResolver.getType(item.uri).orEmpty()
    if(mime=="application/pdf" || item.uri.toString().lowercase().endsWith(".pdf")){
     val pfd=context.contentResolver.openFileDescriptor(item.uri,"r")?:continue
     PdfRenderer(pfd).use{renderer->for(i in 0 until renderer.pageCount){renderer.openPage(i).use{p->
       val scale=3;val bmp=Bitmap.createBitmap(max(1,p.width*scale),max(1,p.height*scale),Bitmap.Config.ARGB_8888);bmp.eraseColor(Color.WHITE);p.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);pageNo=addBitmap(doc,bmp,item.rotation,pageNo);bmp.recycle()
     }}}
    } else decodeImage(context.contentResolver,item.uri)?.let{bmp->pageNo=addBitmap(doc,bmp,item.rotation,pageNo);bmp.recycle()}
   }
   doc.writeTo(out)
  }finally{doc.close()}
 }
 private fun decodeImage(cr:ContentResolver,uri:Uri):Bitmap?=cr.openInputStream(uri)?.use{BitmapFactory.decodeStream(it)}
 private fun addBitmap(doc:PdfDocument,source:Bitmap,rotation:Int,pageNo:Int):Int{
  val bmp=if(rotation%360==0)source else Bitmap.createBitmap(source,0,0,source.width,source.height,Matrix().apply{postRotate(rotation.toFloat())},true)
  val maxEdge=8000f;val factor=minOf(1f,maxEdge/max(bmp.width,bmp.height).toFloat());val w=max(1,(bmp.width*factor).toInt());val h=max(1,(bmp.height*factor).toInt())
  val info=PdfDocument.PageInfo.Builder(w,h,pageNo).create();val page=doc.startPage(info);page.canvas.drawColor(Color.WHITE);page.canvas.drawBitmap(bmp,null,Rect(0,0,w,h),Paint(Paint.FILTER_BITMAP_FLAG));doc.finishPage(page);if(bmp!==source)bmp.recycle();return pageNo+1
 }
}
