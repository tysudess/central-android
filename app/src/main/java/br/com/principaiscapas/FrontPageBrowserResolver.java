package br.com.principaiscapas;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONTokener;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Navegador interno dedicado ao Valor Econômico e The Washington Post.
 *
 * Motivo: o FrontPages renderiza a capa corretamente no navegador, mas alguns URLs
 * de imagem retornam HTTP 404 quando baixados fora da sessão da página. Em vez de
 * tentar adivinhar/reescrever o URL da imagem, carregamos a página em WebView,
 * selecionamos a imagem principal já renderizada pelo próprio site e capturamos
 * somente a imagem, inteira e sem crop.
 */
public final class FrontPageBrowserResolver {
    private FrontPageBrowserResolver() {}

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int PAGE_TIMEOUT_SECONDS = 18;

    public static void resolve(Activity activity, CoverEntry entry, Date targetDate) throws Exception {
        String name = entry.source.name == null ? "" : entry.source.name;
        List<String> urls = new ArrayList<>();

        if ("THE WASHINGTON POST".equalsIgnoreCase(name)) {
            urls.add("https://www.frontpages.com/the-washington-post/");
        } else if ("VALOR ECONÔMICO".equalsIgnoreCase(name)) {
            // v0.7.7.4 — alteração EXCLUSIVA do Valor. Primeiro tenta os PDFs
            // autorizados recebidos no Gmail. Se Pagina-1.pdf não estiver disponível,
            // volta exatamente ao fluxo web aprovado na v0.7.7.2.
            try {
                entry.status = "Valor: procurando PDFs no Gmail...";
                if (ValorEmailPdfClient.loadValorFromGmail(activity, entry, targetDate)) return;
            } catch (Exception gmailEx) {
                // Falha do Gmail não remove o fallback já aprovado.
                entry.status = "Valor: Gmail indisponível • usando fonte web...";
            }

            urls.add("https://www.frontpages.com/valor-economico/");
            // A data selecionada não limita mais Valor/Post: usamos o que a página
            // estiver exibindo naquele momento. O PressReader permanece como fallback.
            urls.add("https://valoreconomico.pressreader.com/valor-economico");
        } else {
            throw new Exception("Resolver do navegador não se aplica a " + name);
        }

        Exception last = null;
        for (String url : urls) {
            try {
                BrowserResult r = resolveOne(activity, name, url, targetDate);
                if (r == null || r.bitmap == null) continue;

                // v0.7.7.2: o FrontPages às vezes entrega ao Valor uma imagem
                // vertical válida, porém recortada na parte inferior. Não aceitar
                // essa versão como capa final: cair para o PressReader, que fornece
                // a página inteira. O limite é exclusivo do Valor/FrontPages.
                if ("VALOR ECONÔMICO".equalsIgnoreCase(name) && url.contains("frontpages.com")
                        && !isCompleteValorCover(r.bitmap)) {
                    int rw = r.bitmap.getWidth();
                    int rh = r.bitmap.getHeight();
                    r.bitmap.recycle();
                    throw new Exception("FrontPages retornou capa recortada do Valor (" + rw + "×" + rh + "); tentando PressReader");
                }

                if ("THE WASHINGTON POST".equalsIgnoreCase(name)) {
                    // Segurança extra: nunca aceitar a edição SPORTS.
                    String top = readTop(r.bitmap);
                    if (top.contains("WASHINGTON POST SPORTS")) {
                        r.bitmap.recycle();
                        throw new Exception("edição SPORTS rejeitada");
                    }
                }

                entry.coverBitmap = r.bitmap;
                entry.detectedPage = 1;
                entry.status = "Capa exibida no link v0.7.4.6 • " + r.bitmap.getWidth() + "×" + r.bitmap.getHeight()
                        + " • fonte " + (url.contains("frontpages.com") ? "frontpages.com" : "PressReader")
                        + " • data do link";
                return;
            } catch (Exception ex) {
                last = ex;
            }
        }

        throw new Exception("Capa correta não confirmada para " + name
                + (last == null ? "" : " (" + last.getMessage() + ")"));
    }

    private static BrowserResult resolveOne(Activity activity, String newspaperName, String url, Date targetDate) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final BrowserResult[] holder = new BrowserResult[1];
        final Exception[] error = new Exception[1];

        MAIN.post(() -> {
            final ViewGroup root = activity.findViewById(android.R.id.content);
            final WebView web = new WebView(activity);
            web.setBackgroundColor(Color.WHITE);
            web.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            web.setTranslationX(-5000f);
            web.setTranslationY(0f);
            web.setAlpha(0.02f);

            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setLoadsImagesAutomatically(true);
            s.setBlockNetworkImage(false);
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(false);
            s.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
            web.setWebChromeClient(new WebChromeClient());

            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(2, 2);
            root.addView(web, lp);

            final boolean frontPages = url.contains("frontpages.com");
            final String fullDate = new SimpleDateFormat("MMMM d, yyyy", Locale.US).format(targetDate).toUpperCase(Locale.ROOT);
            final String shortDate = new SimpleDateFormat("MMM d, yyyy", Locale.US).format(targetDate).toUpperCase(Locale.ROOT);
            final String datePath = new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(targetDate);

            final Runnable cleanup = () -> {
                try { root.removeView(web); } catch (Exception ignored) {}
                try { web.stopLoading(); web.destroy(); } catch (Exception ignored) {}
            };

            final int[] scans = {0};
            web.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String finishedUrl) {
                    // Algumas páginas continuam carregando a capa por JS/lazy-load após onPageFinished.
                    MAIN.postDelayed(() -> scanPage(), 1200);
                }

                private void scanPage() {
                    if (latch.getCount() == 0) return;
                    scans[0]++;

                    // Valor e Washington Post: usar a capa que a página dedicada estiver
                    // exibindo AGORA, sem exigir que a data do arquivo coincida com a data
                    // selecionada no app. É o equivalente a abrir a imagem da capa em nova guia.
                    if (frontPages && ("VALOR ECONÔMICO".equalsIgnoreCase(newspaperName)
                            || "THE WASHINGTON POST".equalsIgnoreCase(newspaperName))) {
                        final String slug = "THE WASHINGTON POST".equalsIgnoreCase(newspaperName)
                                ? "the-washington-post" : "valor-economico";
                        web.evaluateJavascript(buildCurrentDirectProbeScript(slug), probeRaw -> {
                            try {
                                String direct = decodeJsString(probeRaw);
                                String low = direct == null ? "" : direct.toLowerCase(Locale.ROOT);
                                boolean expected = direct != null
                                        && direct.startsWith("https://www.frontpages.com/g/")
                                        && low.contains("/" + slug + "-")
                                        && low.contains(".webp")
                                        && !("the-washington-post".equals(slug) && low.contains("sports"));
                                if (expected) {
                                    final String cookieHeader = CookieManager.getInstance().getCookie(direct);
                                    new Thread(() -> {
                                        try {
                                            Bitmap bmp = downloadDirectBitmap(direct, url, cookieHeader);
                                            if (bmp == null || bmp.getWidth() < 700 || bmp.getHeight() < 950)
                                                throw new Exception("arquivo direto não é uma capa completa");
                                            holder[0] = new BrowserResult(bmp);
                                            MAIN.post(cleanup);
                                            latch.countDown();
                                        } catch (Exception directEx) {
                                            MAIN.post(this::scanStandard);
                                        }
                                    }, "pc-current-direct-cover").start();
                                    return;
                                }
                            } catch (Exception ignored) {
                                // Cai no motor padrão, também sem filtro de data.
                            }
                            scanStandard();
                        });
                        return;
                    }

                    scanStandard();
                }

                private void scanStandard() {
                    if (latch.getCount() == 0) return;
                    String script = buildScanScript(newspaperName, fullDate, shortDate, datePath, false);
                    web.evaluateJavascript(script, raw -> {
                        try {
                            String json = decodeJsString(raw);
                            JSONObject o = new JSONObject(json);
                            if (!o.optBoolean("dateOk", true)) {
                                throw new Exception("FrontPages não corresponde à data selecionada");
                            }
                            int index = o.optInt("index", -1);
                            int nw = o.optInt("w", 0);
                            int nh = o.optInt("h", 0);
                            String directUrl = o.optString("url", "");
                            boolean exactDirect = o.optBoolean("exact", false);
                            if (index < 0 || nw < 300 || nh < 450) {
                                if (scans[0] < 4) {
                                    MAIN.postDelayed(this::scanPage, 900);
                                    return;
                                }
                                throw new Exception("capa principal não localizada na página");
                            }

                            // FrontPages: quando o navegador já revelou o arquivo real da capa
                            // (/g/AAAA/MM/DD/jornal-...webp), baixa EXATAMENTE esse arquivo.
                            // É o equivalente automático a clicar com o botão direito na capa e
                            // escolher "abrir imagem em nova guia". Assim preservamos a resolução
                            // original e evitamos screenshots/crops.
                            if (frontPages && exactDirect && directUrl.startsWith("https://www.frontpages.com/g/")) {
                                final String cookieHeader = CookieManager.getInstance().getCookie(directUrl);
                                new Thread(() -> {
                                    try {
                                        Bitmap bmp = downloadDirectBitmap(directUrl, url, cookieHeader);
                                        if (bmp == null || bmp.getWidth() < 700 || bmp.getHeight() < 950)
                                            throw new Exception("arquivo direto não é uma capa completa");
                                        holder[0] = new BrowserResult(bmp);
                                        MAIN.post(cleanup);
                                        latch.countDown();
                                    } catch (Exception directEx) {
                                        // Se o CDN recusar a requisição direta, ainda temos a imagem
                                        // renderizada no mesmo WebView e fazemos o fallback abaixo.
                                        MAIN.post(() -> captureRendered(web, root, cleanup, latch, holder, error, index, nw, nh));
                                    }
                                }, "pc-direct-cover").start();
                                return;
                            }

                            captureRendered(web, root, cleanup, latch, holder, error, index, nw, nh);
                            /* legacy isolate path replaced */
                            if (false) web.evaluateJavascript(buildIsolateScript(index), ignored -> MAIN.postDelayed(() -> {
                                try {
                                    int captureW = 1800;
                                    int captureH = Math.max(1100, Math.round(captureW * (nh / (float)Math.max(1, nw))));
                                    if (captureH > 3400) {
                                        captureH = 3400;
                                        captureW = Math.max(900, Math.round(captureH * (nw / (float)Math.max(1, nh))));
                                    }
                                    ViewGroup.LayoutParams p = web.getLayoutParams();
                                    p.width = captureW;
                                    p.height = captureH;
                                    web.setLayoutParams(p);
                                    int ws = View.MeasureSpec.makeMeasureSpec(captureW, View.MeasureSpec.EXACTLY);
                                    int hs = View.MeasureSpec.makeMeasureSpec(captureH, View.MeasureSpec.EXACTLY);
                                    web.measure(ws, hs);
                                    web.layout(0, 0, captureW, captureH);

                                    Bitmap bmp = Bitmap.createBitmap(captureW, captureH, Bitmap.Config.ARGB_8888);
                                    Canvas canvas = new Canvas(bmp);
                                    canvas.drawColor(Color.WHITE);
                                    web.draw(canvas);
                                    holder[0] = new BrowserResult(bmp);
                                } catch (Exception ex) {
                                    error[0] = ex;
                                } finally {
                                    cleanup.run();
                                    latch.countDown();
                                }
                            }, 1000));
                        } catch (Exception ex) {
                            error[0] = ex;
                            cleanup.run();
                            latch.countDown();
                        }
                    });
                }
            });

            try {
                web.loadUrl(url);
            } catch (Exception ex) {
                error[0] = ex;
                cleanup.run();
                latch.countDown();
            }
        });

        if (!latch.await(PAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new Exception("timeout do navegador interno");
        }
        if (error[0] != null) throw error[0];
        if (holder[0] == null || holder[0].bitmap == null) throw new Exception("imagem não capturada");
        return holder[0];
    }

    /**
     * Sondagem exclusiva do Valor Econômico.
     *
     * Procura o mesmo arquivo WEBP original que o navegador usa, mas sem tocar
     * na lógica do Washington Post. Verifica recursos já carregados pelo WebView,
     * imagens, srcset e atributos lazy-load. Se não achar, o código volta para o
     * motor padrão da v0.7.4.1.
     */
    /**
     * Procura o arquivo WEBP atualmente exibido na página dedicada do FrontPages.
     * Não filtra por data. A própria URL do recurso contém /g/AAAA/MM/DD/, mas
     * aceitamos qualquer data que a página esteja mostrando no momento.
     */
    private static String buildCurrentDirectProbeScript(String slug) {
        return "(function(){" +
                "var slug=" + jsQuote(slug) + ";" +
                "function pick(u){try{u=String(u||'');var l=u.toLowerCase();" +
                "if(l.indexOf('/g/')<0||l.indexOf('/'+slug+'-')<0||l.indexOf('.webp')<0)return '';" +
                "if(slug==='the-washington-post'&&l.indexOf('sports')>=0)return '';" +
                "var p=l.indexOf('/g/');var e=l.indexOf('.webp',p);if(p<0||e<0)return '';" +
                "var x=u.substring(p,e+5);if(x.indexOf('/g/')===0)x='https://www.frontpages.com'+x;return x;}catch(e){return '';}}" +
                "try{var rr=performance.getEntriesByType('resource')||[];for(var i=0;i<rr.length;i++){var v=pick(rr[i].name);if(v)return v;}}catch(e){}" +
                "try{var imgs=document.images||[];for(var j=0;j<imgs.length;j++){var im=imgs[j];var v=pick(im.currentSrc||im.src);if(v)return v;" +
                "var attrs=['src','data-src','data-lazy-src','data-original','data-image','data-url','data-full'];" +
                "for(var a=0;a<attrs.length;a++){v=pick(im.getAttribute(attrs[a]));if(v)return v;}" +
                "var ss=(im.getAttribute('srcset')||im.getAttribute('data-srcset')||'').split(',');" +
                "for(var k=0;k<ss.length;k++){v=pick(ss[k].trim().split(/\\s+/)[0]);if(v)return v;}}}catch(e){}" +
                "try{var els=document.querySelectorAll('source,a,[data-src],[data-lazy-src],[data-original],[data-image],[data-url],[data-full]');" +
                "for(var z=0;z<els.length;z++){var el=els[z];var aa=['src','href','srcset','data-src','data-lazy-src','data-original','data-image','data-url','data-full'];" +
                "for(var q=0;q<aa.length;q++){var raw=el.getAttribute(aa[q])||'';var parts=raw.split(',');for(var b=0;b<parts.length;b++){var v=pick(parts[b].trim().split(/\\s+/)[0]);if(v)return v;}}}}catch(e){}" +
                "try{var html=document.documentElement?document.documentElement.innerHTML:'';var low=html.toLowerCase();var key='/'+slug+'-';var pos=low.indexOf(key);" +
                "while(pos>=0){var gp=low.lastIndexOf('/g/',pos);var we=low.indexOf('.webp',pos);if(gp>=0&&we>pos){var x=html.substring(gp,we+5).replace(/&amp;/g,'&').replace(/\\\\//g,'/');" +
                "if(x.indexOf('/g/')===0)x='https://www.frontpages.com'+x;if(!(slug==='the-washington-post'&&x.toLowerCase().indexOf('sports')>=0))return x;}pos=low.indexOf(key,pos+key.length);}}catch(e){}" +
                "return '';})()";
    }

    private static String buildScanScript(String name, String fullDate, String shortDate, String datePath, boolean requireDate) {
        String expected = jsQuote(normalize(name));
        String d1 = jsQuote(normalize(fullDate));
        String d2 = jsQuote(normalize(shortDate));
        String dateRequirement = requireDate ? "true" : "false";
        String slug = "THE WASHINGTON POST".equalsIgnoreCase(name) ? "the-washington-post"
                : ("VALOR ECONÔMICO".equalsIgnoreCase(name) ? "valor-economico" : "");
        return "(function(){" +
                "function n(s){return (s||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toUpperCase().replace(/[^A-Z0-9]+/g,' ').trim();}" +
                "var expected=" + expected + ";var d1=" + d1 + ";var d2=" + d2 + ";" +
                "var slug=" + jsQuote(slug) + ";var datePath=" + jsQuote(datePath) + ";" +
                "var page=n(document.title+' '+(document.body?document.body.innerText:''));" +
                "var dateOk=!(" + dateRequirement + ")||page.indexOf(d1)>=0||page.indexOf(d2)>=0;" +
                "var imgs=Array.prototype.slice.call(document.images||[]);var best=null;" +
                "for(var i=0;i<imgs.length;i++){var im=imgs[i];var w=im.naturalWidth||0,h=im.naturalHeight||0;if(w<300||h<450)continue;" +
                "var raw=(im.currentSrc||im.src||'');var low=raw.toLowerCase();" +
                "var exact=!!slug && low.indexOf('/g/'+datePath+'/'+slug+'-')>=0 && low.indexOf('.webp')>=0;" +
                "if(expected.indexOf('WASHINGTON POST')>=0 && low.indexOf('sports')>=0)continue;" +
                "var ar=h/Math.max(1,w);if(ar<1.10||ar>2.20)continue;" +
                "var a=im.closest?im.closest('a'):null;var par=im.parentElement;" +
                "var info=n((im.alt||'')+' '+(im.title||'')+' '+(im.getAttribute('data-title')||'')+' '+(im.getAttribute('data-caption')||'')+' '+(a?(a.title||''):'')+' '+(par?(par.innerText||'').slice(0,450):''));" +
                "var y=0;try{y=im.getBoundingClientRect().top+(window.scrollY||0);}catch(e){}" +
                "var score=Math.min(w*h,12000000);score+=Math.max(0,3500-Math.min(3500,y))*3500;" +
                "if(exact)score+=1000000000;" +
                "if(expected.indexOf('WASHINGTON POST')>=0&&info.indexOf('WASHINGTON POST')>=0)score+=25000000;" +
                "if(expected.indexOf('VALOR ECONOMICO')>=0&&info.indexOf('VALOR ECONOMICO')>=0)score+=25000000;" +
                "if(low.indexOf('logo')>=0||low.indexOf('icon')>=0||low.indexOf('avatar')>=0)score-=15000000;" +
                "if(!best||score>best.score)best={index:i,w:w,h:h,score:score,info:info,url:raw,exact:exact};}" +
                "return JSON.stringify({dateOk:dateOk,index:best?best.index:-1,w:best?best.w:0,h:best?best.h:0,info:best?best.info:'',url:best?best.url:'',exact:best?best.exact:false});" +
                "})()";
    }

    private static String buildIsolateScript(int index) {
        return "(function(){var imgs=Array.prototype.slice.call(document.images||[]);var im=imgs[" + index + "];if(!im)return 'missing';" +
                "var src=im.currentSrc||im.src;var nw=im.naturalWidth||1000,nh=im.naturalHeight||1500;" +
                "document.documentElement.innerHTML='<head><meta name=\\\"viewport\\\" content=\\\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\\\"><style>html,body{margin:0!important;padding:0!important;background:#fff!important;width:100%!important;min-height:100%!important;overflow:hidden!important}#pcCover{display:block!important;margin:0!important;padding:0!important;width:100%!important;height:auto!important;object-fit:contain!important;background:#fff!important}</style></head><body><img id=\\\"pcCover\\\"></body>';" +
                "var c=document.getElementById('pcCover');c.src=src;c.width=nw;c.height=nh;window.scrollTo(0,0);return src;})()";
    }

    private static void captureRendered(WebView web, ViewGroup root, Runnable cleanup,
                                        CountDownLatch latch, BrowserResult[] holder, Exception[] error,
                                        int index, int nw, int nh) {
        String isolate = buildIsolateScript(index);
        web.evaluateJavascript(isolate, ignored -> MAIN.postDelayed(() -> {
            try {
                int captureW = Math.min(2200, Math.max(1400, nw));
                int captureH = Math.max(1100, Math.round(captureW * (nh / (float)Math.max(1, nw))));
                if (captureH > 4200) {
                    captureH = 4200;
                    captureW = Math.max(900, Math.round(captureH * (nw / (float)Math.max(1, nh))));
                }
                ViewGroup.LayoutParams p = web.getLayoutParams();
                p.width = captureW; p.height = captureH; web.setLayoutParams(p);
                int ws = View.MeasureSpec.makeMeasureSpec(captureW, View.MeasureSpec.EXACTLY);
                int hs = View.MeasureSpec.makeMeasureSpec(captureH, View.MeasureSpec.EXACTLY);
                web.measure(ws, hs); web.layout(0, 0, captureW, captureH);
                Bitmap bmp = Bitmap.createBitmap(captureW, captureH, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bmp); canvas.drawColor(Color.WHITE); web.draw(canvas);
                holder[0] = new BrowserResult(bmp);
            } catch (Exception ex) { error[0] = ex; }
            finally { cleanup.run(); latch.countDown(); }
        }, 900));
    }

    private static Bitmap downloadDirectBitmap(String directUrl, String referer, String cookieHeader) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(directUrl).openConnection();
        c.setConnectTimeout(7000); c.setReadTimeout(15000); c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
        c.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        c.setRequestProperty("Referer", referer);
        if (cookieHeader != null && !cookieHeader.isEmpty()) c.setRequestProperty("Cookie", cookieHeader);
        int code = c.getResponseCode();
        if (code < 200 || code >= 400) { c.disconnect(); throw new Exception("imagem direta HTTP " + code); }
        try (InputStream in = new BufferedInputStream(c.getInputStream())) {
            Bitmap b = BitmapFactory.decodeStream(in);
            if (b == null) throw new Exception("imagem direta inválida");
            return b;
        } finally { c.disconnect(); }
    }

    private static boolean isCompleteValorCover(Bitmap b) {
        if (b == null || b.getWidth() <= 0 || b.getHeight() <= 0) return false;
        float ratio = b.getHeight() / (float) b.getWidth();
        // Capas completas recentes do Valor são claramente mais altas que
        // a miniatura/crop que o FrontPages passou a expor (~1,28).
        // Mantemos margem conservadora e, em dúvida, preferimos o PressReader.
        return b.getWidth() >= 700 && b.getHeight() >= 950 && ratio >= 1.34f;
    }

    private static String readTop(Bitmap b) throws Exception {
        int h = Math.max(1, Math.min(b.getHeight(), Math.round(b.getHeight() * 0.34f)));
        Bitmap top = Bitmap.createBitmap(b, 0, 0, b.getWidth(), h);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            Text t = Tasks.await(recognizer.process(InputImage.fromBitmap(top, 0)));
            return normalize(t.getText());
        } finally {
            top.recycle();
            recognizer.close();
        }
    }

    private static boolean topContains(Bitmap b, String phrase) throws Exception {
        return readTop(b).contains(normalize(phrase));
    }

    private static boolean isLatestBusinessEdition(Date target) {
        Calendar latest = Calendar.getInstance();
        latest.set(Calendar.HOUR_OF_DAY, 12); latest.set(Calendar.MINUTE, 0); latest.set(Calendar.SECOND, 0); latest.set(Calendar.MILLISECOND, 0);
        while (latest.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || latest.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            latest.add(Calendar.DAY_OF_MONTH, -1);
        }
        Calendar t = Calendar.getInstance();
        t.setTime(target == null ? new Date() : target);
        return t.get(Calendar.YEAR) == latest.get(Calendar.YEAR)
                && t.get(Calendar.DAY_OF_YEAR) == latest.get(Calendar.DAY_OF_YEAR);
    }

    private static String decodeJsString(String raw) throws Exception {
        Object v = new JSONTokener(raw == null ? "null" : raw).nextValue();
        return v == null || v == JSONObject.NULL ? "{}" : String.valueOf(v);
    }

    private static String jsQuote(String s) {
        if (s == null) s = "";
        return JSONObject.quote(s);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
    }

    private static final class BrowserResult {
        final Bitmap bitmap;
        BrowserResult(Bitmap bitmap) { this.bitmap = bitmap; }
    }
}
