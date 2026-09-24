package br.com.principaiscapas;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v0.7.7.0 • todas as candidatas consolidadas + captura robusta de original_page
 * Cada link "Leia mais" é aberto em um WebView NOVO. Isso impede qualquer
 * resposta atrasada, DOM, redirect ou window.open de um jornal anterior de
 * contaminar o próximo jornal.
 *
 * O Apps Script já entrega apenas veículos com nome EXATO. Aqui apenas
 * repetimos o fluxo manual: Leia mais -> Ver página -> original_page.
 */
public class CentralClippingWebResolver {
    public interface Callback {
        void onComplete(Map<String, List<String>> coverUrls, List<String> errors);
    }

    private static final long JOB_TIMEOUT_MS = 12000;

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Job> jobs = new ArrayList<>();
    private final Map<String, List<String>> resolved = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();

    private Callback callback;
    private WebView webView;
    private int jobIndex = 0;
    private int probeCount = 0;
    private long generation = 0;
    private Job currentJob;
    private String currentToken = "";
    private boolean armedForNavigation = false;

    private static class Job {
        final String name;
        final String url;
        Job(String name, String url) { this.name = name; this.url = url; }
    }

    public CentralClippingWebResolver(Activity activity) { this.activity = activity; }

    public void resolve(Map<String, List<String>> matterUrls, Callback callback) {
        this.callback = callback;
        jobs.clear(); resolved.clear(); errors.clear(); jobIndex = 0;
        destroyWebView();
        currentJob = null; currentToken = ""; armedForNavigation = false;

        if (matterUrls != null) {
            for (Map.Entry<String, List<String>> entry : matterUrls.entrySet()) {
                if (isWebOnly(entry.getKey()) || entry.getValue() == null) continue;
                for (String url : entry.getValue()) {
                    if (url == null || url.trim().isEmpty()) continue;
                    jobs.add(new Job(entry.getKey(), url.trim()));
                }
            }
        }
        startNextJob();
    }

    private void createWebView() {
        destroyWebView();
        webView = new WebView(activity);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setLoadsImagesAutomatically(true);
        s.setBlockNetworkImage(false);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setAlpha(0.01f);
        webView.addJavascriptInterface(new JsBridge(), "PcBridge");
        activity.addContentView(webView, new ViewGroup.LayoutParams(2, 2));
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return inspectNavigation(request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return inspectNavigation(url);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String u = request == null || request.getUrl() == null ? "" : request.getUrl().toString();
                captureResourceIfOriginal(u);
                return super.shouldInterceptRequest(view, request);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                captureResourceIfOriginal(url);
                return super.shouldInterceptRequest(view, url);
            }
            @Override public void onLoadResource(WebView view, String url) {
                captureResourceIfOriginal(url);
                super.onLoadResource(view, url);
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (view != webView || currentJob == null) return;
                if (armedForNavigation && isOriginalImageUrl(url)) {
                    successCurrent(url, currentToken);
                    return;
                }
                installHooks();
                scheduleProbe(250);
            }
        });
    }

    private class JsBridge {
        @JavascriptInterface public void arm(String token) {
            handler.post(() -> { if (isCurrentToken(token)) armedForNavigation = true; });
        }
        @JavascriptInterface public void capture(String token, String url) {
            if (url == null) return;
            final String clean = cleanup(url);
            handler.post(() -> {
                if (!isCurrentToken(token)) return;
                if (isOriginalImageUrl(clean)) successCurrent(clean, token);
            });
        }
    }

    private void captureResourceIfOriginal(String url) {
        if (url == null || !isOriginalImageUrl(url)) return;
        final String clean = cleanup(url);
        final String token = currentToken;
        handler.post(() -> successCurrent(clean, token));
    }

    private boolean inspectNavigation(String url) {
        if (armedForNavigation && isOriginalImageUrl(url)) {
            successCurrent(url, currentToken);
            return true;
        }
        return false;
    }

    private void startNextJob() {
        destroyWebView();
        while (jobIndex < jobs.size()) {
            Job j = jobs.get(jobIndex++);
            currentJob = j;
            probeCount = 0;
            armedForNavigation = false;
            generation++;
            final long g = generation;
            currentToken = j.name + "#" + g + "#" + jobIndex;
            createWebView();
            webView.loadUrl(j.url);
            handler.postDelayed(() -> {
                if (g == generation && currentJob == j) failCurrent(j.name, "Ver página não localizado no link exato");
            }, JOB_TIMEOUT_MS);
            return;
        }
        finish();
    }

    private void installHooks() {
        if (webView == null || currentJob == null) return;
        final String token = jsQuote(currentToken);
        final String hook =
                "(function(){" +
                "var TOKEN=" + token + ";" +
                "function clean(u){if(!u)return '';try{return new URL(u,document.baseURI).href;}catch(e){return u;}}" +
                "if(window.__pc_token===TOKEN)return;window.__pc_token=TOKEN;" +
                "var oldOpen=window.open;window.open=function(u){var x=clean(u);try{PcBridge.capture(TOKEN,x);}catch(e){};" +
                "try{return oldOpen?oldOpen.apply(window,arguments):null;}catch(e){return null;}};" +
                "})()";
        webView.evaluateJavascript(hook, null);
    }

    private void scheduleProbe(long delay) {
        final long g = generation;
        handler.postDelayed(() -> {
            if (g != generation || webView == null || currentJob == null) return;
            probeDom();
        }, delay);
    }

    private void probeDom() {
        probeCount++;
        installHooks();
        final String token = jsQuote(currentToken);
        final String js =
                "(function(){" +
                "var TOKEN=" + token + ";" +
                "function norm(s){try{return (s||'').normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').toUpperCase();}catch(e){return (s||'').toUpperCase();}}" +
                "function clean(u){if(!u)return '';try{u=String(u).replace(/\\\\\\//g,'/').replace(/&amp;/g,'&');return new URL(u,document.baseURI).href;}catch(e){return String(u||'');}}" +
                "function isOrig(u){u=String(u||'').toLowerCase();return u.indexOf('/original_page/')>=0||u.indexOf('static.resources/original_page/')>=0||u.indexOf('static.resources%2foriginal_page%2f')>=0;}" +
                "function directFrom(v){v=clean(v);return isOrig(v)?v:'';}" +
                // 1) recursos já requisitados pela página
                "try{var pe=performance.getEntriesByType('resource')||[];for(var pi=0;pi<pe.length;pi++){var pu=directFrom(pe[pi].name);if(pu)return pu;}}catch(e){}" +
                // 2) atributos de qualquer elemento; cobre href, lazy-load e data-*
                "var nodes=[].slice.call(document.querySelectorAll('*'));" +
                "for(var ni=0;ni<nodes.length&&ni<9000;ni++){var n=nodes[ni];if(!n||!n.attributes)continue;for(var ai=0;ai<n.attributes.length;ai++){var du=directFrom(n.attributes[ai].value);if(du)return du;}}" +
                // 3) URL eventualmente embutida no HTML/JS
                "try{var html=document.documentElement?document.documentElement.innerHTML:'';var hm=html.match(/https?:\\/\\/[^'\\\"<>\\s]+original_page[^'\\\"<>\\s]*/i);if(hm&&hm[0]){var hu=directFrom(hm[0]);if(hu)return hu;}}catch(e){}" +
                // 4) localiza o botão/ação Ver página
                "var all=[].slice.call(document.querySelectorAll('a,button,[role=button],input[type=button],input[type=submit],[onclick],[data-url],[data-href]'));" +
                "var el=null;for(var i=0;i<all.length;i++){var t=norm(all[i].innerText||all[i].textContent||all[i].value||all[i].getAttribute('aria-label')||all[i].getAttribute('title')||'').trim();if(t==='VER PAGINA'||t.indexOf('VER PAGINA')===0){el=all[i];break;}}" +
                "if(!el){var every=[].slice.call(document.querySelectorAll('span,div,p,strong'));for(var j=0;j<every.length&&j<7000;j++){var tt=norm(every[j].innerText||every[j].textContent||'').trim();if(tt==='VER PAGINA'||tt.indexOf('VER PAGINA')===0){el=every[j].closest('a,button,[role=button],[onclick],[data-url],[data-href]');if(!el){var ca=every[j].querySelector?every[j].querySelector('a,button,[role=button]'):null;if(ca)el=ca;}if(el)break;}}}" +
                "if(!el)return '';" +
                "var attrs=['href','data-href','data-url','data-original','data-image','data-page','src'];" +
                "var cur=el;for(var up=0;up<4&&cur;up++,cur=cur.parentElement){for(var k=0;k<attrs.length;k++){var u=cur.getAttribute?cur.getAttribute(attrs[k]):'';u=clean(u);if(u&&u.indexOf('javascript:')!==0){if(isOrig(u))return u;if(/^https?:/i.test(u))return u;}}}" +
                "var child=el.querySelector?el.querySelector('a[href]'):null;if(child){var cu=clean(child.getAttribute('href'));if(cu)return cu;}" +
                "var oc=el.getAttribute?el.getAttribute('onclick')||'':'';var mm=oc.match(/https?:\\/\\/[^'\\\"\\s)]+/i);if(mm&&mm[0])return clean(mm[0]);" +
                "try{PcBridge.arm(TOKEN);}catch(e){};try{el.click();}catch(e){};return '__CLICKED__';" +
                "})()";
        webView.evaluateJavascript(js, value -> {
            if (value == null) { retryProbe(); return; }
            String decoded = decodeJsString(value);
            if (isOriginalImageUrl(decoded)) { successCurrent(decoded, currentToken); return; }
            if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                armedForNavigation = true;
                webView.loadUrl(decoded);
                return;
            }
            retryProbe();
        });
    }

    private void retryProbe() { if (probeCount < 20) scheduleProbe(450); }

    private String decodeJsString(String raw) {
        try { JSONArray a = new JSONArray("[" + raw + "]"); return cleanup(a.optString(0, "")); }
        catch (Exception e) {
            String s = raw;
            if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) s = s.substring(1, s.length()-1);
            return cleanup(s.replace("\\/", "/"));
        }
    }

    private boolean isOriginalImageUrl(String url) {
        if (url == null) return false;
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("/original_page/") || u.contains("static.resources/original_page/") || u.contains("static.resources%2foriginal_page%2f");
    }

    private synchronized void successCurrent(String url, String token) {
        if (webView == null || currentJob == null || !isCurrentToken(token)) return;
        String clean = cleanup(url);
        if (!isOriginalImageUrl(clean)) return;
        // v0.7.6.0: NÃO deduplicar aqui. Cada job representa uma das páginas
        // recebidas do Gmail e deve ocupar sua própria posição na revisão, ainda que
        // duas entradas eventualmente apontem para a mesma imagem original.
        resolved.computeIfAbsent(currentJob.name, k -> new ArrayList<>()).add(clean);
        generation++;
        currentJob = null;
        armedForNavigation = false;
        startNextJob();
    }

    private void failCurrent(String name, String reason) {
        if (currentJob != null) {
            errors.add(name + ": " + reason);
            // Preserva a posição recebida do Gmail. String vazia = página recebida,
            // porém sem original_page resolvido. O scanner transforma isso numa
            // candidata indisponível em vez de escondê-la.
            resolved.computeIfAbsent(name, k -> new ArrayList<>()).add("");
        }
        generation++;
        currentJob = null;
        armedForNavigation = false;
        startNextJob();
    }

    private boolean isCurrentToken(String token) { return token != null && currentJob != null && token.equals(currentToken); }

    private void destroyWebView() {
        if (webView == null) return;
        try {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.removeJavascriptInterface("PcBridge");
            webView.destroy();
        } catch (Exception ignored) {}
        webView = null;
    }

    private void finish() {
        destroyWebView();
        Map<String,List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String,List<String>> e : resolved.entrySet()) out.put(e.getKey(), new ArrayList<>(e.getValue()));
        if (callback != null) callback.onComplete(out, new ArrayList<>(errors));
    }

    private static String cleanup(String s) { return s == null ? "" : s.replace("\\u0026","&").replace("&amp;","&").trim(); }
    private static String jsQuote(String s) { String v=s==null?"":s.replace("\\","\\\\").replace("\"","\\\""); return "\""+v+"\""; }
    private static boolean isWebOnly(String name) { return "VALOR ECONÔMICO".equalsIgnoreCase(name) || "THE WASHINGTON POST".equalsIgnoreCase(name); }
}
