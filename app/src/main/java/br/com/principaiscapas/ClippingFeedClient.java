package br.com.principaiscapas;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class ClippingFeedClient {
    private static volatile String lastFeedVersion = "";
    private static final Map<String,Integer> lastSentCounts = new LinkedHashMap<>();

    private static final String ACCESS_KEY="PC26-8F2D4A7B-31C9E6F0-5A1D";
    public static final String PREFS="gmail_feed";
    public static final String KEY_URL="feed_url";

    // Central Android: Web App oficial da v0.7.7.4 já vem configurado no APK.
    // A chave de migração força a nova URL uma única vez ao atualizar a partir
    // de versões anteriores, mas depois continua permitindo alterar/remover
    // manualmente pelo botão Gmail automático.
    private static final String DEFAULT_WEB_APP_URL =
            "https://script.google.com/macros/s/AKfycbwO46cUIb0O--6_LUrysIjhCAlJJqjw0PRCuRLOTndiy4BkFZhNbXPQgA3rc9H8YC5l/exec";
    private static final String KEY_BUNDLED_URL_VERSION = "bundled_url_version";
    private static final String BUNDLED_URL_VERSION = "0.7.7.4-central";

    public static String getConfiguredUrl(Context context){
        android.content.SharedPreferences sp=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String applied=sp.getString(KEY_BUNDLED_URL_VERSION,"");
        if(!BUNDLED_URL_VERSION.equals(applied)){
            sp.edit()
                    .putString(KEY_URL,DEFAULT_WEB_APP_URL)
                    .putString(KEY_BUNDLED_URL_VERSION,BUNDLED_URL_VERSION)
                    .apply();
            return DEFAULT_WEB_APP_URL;
        }
        return sp.getString(KEY_URL,DEFAULT_WEB_APP_URL).trim();
    }

    public static void saveConfiguredUrl(Context context,String url){
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY_URL,url==null?"":url.trim()).apply();
    }

    public static synchronized String getLastFeedVersion(){ return lastFeedVersion; }

    public static synchronized int getLastSentCount(String name){
        Integer n=lastSentCounts.get(name);
        return n==null?0:n;
    }

    private static synchronized void resetLastFeedInfo(){
        lastFeedVersion="";
        lastSentCounts.clear();
    }

    private static synchronized void recordSent(String name){
        lastSentCounts.put(name, getLastSentCount(name)+1);
    }

    public static boolean isValidWebAppUrl(String url){
        if(url==null) return false;
        String u=url.trim().toLowerCase(Locale.ROOT);
        return u.startsWith("https://script.google.com/macros/s/") && u.contains("/exec");
    }

    private static JSONObject requestJson(String endpoint) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
        c.setConnectTimeout(7000);
        c.setReadTimeout(40000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent","PrincipaisCapas/0.7.0 Android");
        int code=c.getResponseCode();
        if(code<200||code>=300) throw new Exception("Ponte Gmail HTTP "+code);
        StringBuilder sb=new StringBuilder();
        try(BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))){
            String line; while((line=br.readLine())!=null) sb.append(line);
        } finally { c.disconnect(); }
        String body=sb.toString().trim();
        if(body.startsWith("<!DOCTYPE") || body.startsWith("<!doctype") || body.startsWith("<html") || body.startsWith("<HTML"))
            throw new Exception("A ponte abriu HTML. Atualize a implantação do Apps Script e mantenha acesso como Qualquer pessoa.");
        if(!body.startsWith("{")) throw new Exception("Resposta inválida da ponte Gmail");
        return new JSONObject(body);
    }

    private static String endpoint(Context context, String extra) throws Exception {
        String base=getConfiguredUrl(context);
        if(base.isEmpty()) throw new Exception("Gmail automático não configurado");
        if(!isValidWebAppUrl(base)) throw new Exception("URL inválida do Apps Script");
        String sep=base.contains("?")?"&":"?";
        return base+sep+"key="+URLEncoder.encode(ACCESS_KEY,"UTF-8")+(extra==null?"":extra);
    }

    /**
     * Retorna: nome oficial do jornal -> lista de URLs "Leia mais" do Central Clipping.
     * Se o feed vier vazio, gera uma mensagem de diagnóstico específica em vez de
     * simplesmente dizer que a ponte está indisponível.
     */
    public static Map<String,List<String>> fetchMatterUrls(Context context, Date targetDate) throws Exception {
        Date safeDate = targetDate == null ? new Date() : targetDate;
        String dateParam = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(safeDate);
        resetLastFeedInfo();
        JSONObject root=requestJson(endpoint(context,"&action=matters&date="+URLEncoder.encode(dateParam,"UTF-8")));
        synchronized (ClippingFeedClient.class) { lastFeedVersion=root.optString("version","").trim(); }
        if(!root.optBoolean("ok",false)) throw new Exception(root.optString("error","Falha na ponte Gmail"));

        JSONArray arr=root.optJSONArray("matters");
        Map<String,List<String>> out=new LinkedHashMap<>();
        if(arr!=null){
            for(int i=0;i<arr.length();i++){
                JSONObject o=arr.optJSONObject(i);
                if(o==null) continue;
                String name=o.optString("name","").trim();
                String matterUrl=o.optString("matterUrl","").trim();
                if(name.isEmpty()||matterUrl.isEmpty()) continue;
                List<String> list=out.computeIfAbsent(name,k->new ArrayList<>());
                if(!list.contains(matterUrl)) { list.add(matterUrl); recordSent(name); }
            }
        }

        if(out.isEmpty()){
            int threads=root.optInt("threads",0);
            int messages=root.optInt("messagesScanned",0);
            int rawLinks=root.optInt("rawLeiaMaisFound",0);
            int dated=root.optInt("datedItemsMatched",0);
            String mailbox=root.optString("mailbox","").trim();
            String account = mailbox.isEmpty() ? "" : " Conta da ponte: "+maskEmail(mailbox)+".";
            if(threads==0 || messages==0){
                throw new Exception("A ponte está ativa, mas não encontrou e-mails de capas aceitos (ex.: 'Monitoramento: Capa(s) de Jornais' ou 'CAPA DE JORNAIS 1 APP') na conta autorizada para a janela da data selecionada."+account);
            }
            if(rawLinks==0){
                throw new Exception("O e-mail foi encontrado, mas o Apps Script não localizou nenhum link 'Leia mais'. Atualize o Código.gs para a versão 0.7.7.4."+account);
            }
            if(dated==0){
                throw new Exception("Foram encontrados links 'Leia mais', mas nenhum item corresponde à data "+new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT).format(safeDate)+"."+account);
            }
            throw new Exception("Nenhuma capa compatível foi classificada no Gmail para a data selecionada."+account);
        }
        return out;
    }

    private static String maskEmail(String email){
        int at=email.indexOf('@');
        if(at<=1) return email;
        String local=email.substring(0,at);
        String domain=email.substring(at);
        if(local.length()<=3) return local.charAt(0)+"***"+domain;
        return local.substring(0,2)+"***"+local.substring(local.length()-1)+domain;
    }
}
