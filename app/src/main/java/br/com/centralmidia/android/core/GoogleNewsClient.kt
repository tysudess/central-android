package br.com.centralmidia.android.core

import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class GoogleNewsClient {
    private val client=OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).build()
    fun search(query:String, limit:Int=50): List<NewsItem> {
        val q=URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
        val url="https://news.google.com/rss/search?q=$q&hl=pt-BR&gl=BR&ceid=BR:pt-419"
        val request=Request.Builder().url(url).header("User-Agent","Mozilla/5.0 CentralAndroid").build()
        val xml=client.newCall(request).execute().use { r-> if(!r.isSuccessful) error("Falha HTTP ${r.code}"); r.body?.string().orEmpty() }
        val parser=XmlPullParserFactory.newInstance().newPullParser().apply { setInput(StringReader(xml)) }
        val out=mutableListOf<NewsItem>(); var inItem=false; var title="";var link="";var pub="";var source="";var event=parser.eventType
        while(event!=XmlPullParser.END_DOCUMENT && out.size<limit){
            when(event){
                XmlPullParser.START_TAG -> { when(parser.name){ "item"->{inItem=true;title="";link="";pub="";source=""}; "title"->if(inItem) title=parser.nextText(); "link"->if(inItem) link=parser.nextText(); "pubDate"->if(inItem) pub=parser.nextText(); "source"->if(inItem) source=parser.nextText() } }
                XmlPullParser.END_TAG -> if(parser.name=="item" && inItem){ if(title.isNotBlank()&&link.isNotBlank()) out+=NewsItem(title,source,link,pub); inItem=false }
            }
            event=parser.next()
        }
        return out
    }
}
