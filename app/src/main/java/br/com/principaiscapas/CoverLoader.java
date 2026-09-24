package br.com.principaiscapas;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.util.LinkedHashSet;
import java.text.Normalizer;

import org.jsoup.Jsoup;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CoverLoader {
    private static final int MAX_PAGES = 5;

    public static void load(Context context, CoverEntry entry, java.util.Date targetDate) throws Exception {
        boolean hasSingleUrl = entry.source.url != null && !entry.source.url.trim().isEmpty();
        boolean hasFallbackUrls = entry.source.urls != null && !entry.source.urls.isEmpty();
        if (!hasSingleUrl && !hasFallbackUrls) {
            entry.coverBitmap = null;
            entry.candidates.clear();
            entry.detectedPage = 0;
            entry.status = "Aguardando configuração da fonte";
            return;
        }

        // Valor Econômico não tem edição regular aos sábados e domingos.
        // Não tenta baixar uma capa antiga e não cria página vazia no PDF.
        if ("VALOR ECONÔMICO".equalsIgnoreCase(entry.source.name)) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            if (targetDate != null) cal.setTime(targetDate);
            int dow = cal.get(java.util.Calendar.DAY_OF_WEEK);
            if (dow == java.util.Calendar.SATURDAY || dow == java.util.Calendar.SUNDAY) {
                entry.coverBitmap = null;
                entry.candidates.clear();
                entry.detectedPage = 0;
                entry.status = "Sem edição regular no fim de semana";
                return;
            }
        }

        if (entry.source.type == NewspaperSource.Type.IMAGE) {
            String imageUrl = hasSingleUrl ? entry.source.url : entry.source.urls.get(0);
            entry.coverBitmap = downloadBitmap(imageUrl);
            entry.detectedPage = 1;
            entry.status = "Capa carregada";
        } else if (entry.source.type == NewspaperSource.Type.HTML) {
            loadFromHtmlWithFallbacks(entry, targetDate);
        } else {
            loadFromPdf(context, entry);
        }
    }


    private static void loadFromHtmlWithFallbacks(CoverEntry entry, java.util.Date targetDate) throws Exception {
        List<String> sources = (entry.source.urls != null && !entry.source.urls.isEmpty())
                ? new ArrayList<>(entry.source.urls) : new ArrayList<>(java.util.Collections.singletonList(entry.source.url));

        // Fontes fixas determinadas pelo projeto.
        // Washington Post: SOMENTE a página individual informada pelo usuário.
        if ("THE WASHINGTON POST".equalsIgnoreCase(entry.source.name)) {
            sources.clear();
            sources.add("https://www.frontpages.com/the-washington-post/");
        }
        // Valor Econômico: FrontPages primeiro; se não for a capa real (ex.: propaganda),
        // tenta o impresso oficial no PressReader.
        if ("VALOR ECONÔMICO".equalsIgnoreCase(entry.source.name)) {
            sources.clear();
            sources.add("https://www.frontpages.com/valor-economico/");
            sources.add("https://valoreconomico.pressreader.com/valor-economico");
        }
        Exception last = null;
        Bitmap bestOverall = null;
        String bestStatus = null;
        long bestPixels = -1;

        // Não encerra mais na primeira fonte que funciona. Percorre as fontes e mantém
        // a capa confirmada de MAIOR resolução. Isso é importante para não ficar preso
        // em miniaturas de 600x800/750x1305 quando outra fonte possui a imagem original.
        for (String sourceUrl : sources) {
            if (sourceUrl == null || sourceUrl.trim().isEmpty()) continue;
            try {
                entry.coverBitmap = null;
                loadFromHtml(entry, sourceUrl.trim(), targetDate);
                Bitmap candidate = entry.coverBitmap;
                if (candidate == null) continue;
                long pixels = (long) candidate.getWidth() * (long) candidate.getHeight();
                if (pixels > bestPixels) {
                    if (bestOverall != null && bestOverall != candidate) bestOverall.recycle();
                    bestOverall = candidate;
                    bestPixels = pixels;
                    bestStatus = entry.status;
                } else if (candidate != bestOverall) {
                    candidate.recycle();
                }

                // Já é uma capa realmente grande para leitura; evita abrir portais pesados
                // desnecessariamente depois de encontrar uma versão de alta definição.
                boolean strictWebOnly = "THE WASHINGTON POST".equalsIgnoreCase(entry.source.name)
                        || "VALOR ECONÔMICO".equalsIgnoreCase(entry.source.name);
                if (!strictWebOnly && bestOverall.getWidth() >= 1400 && bestOverall.getHeight() >= 1800) break;
                if ("VALOR ECONÔMICO".equalsIgnoreCase(entry.source.name)
                        && sourceUrl.contains("frontpages.com")
                        && bestOverall.getWidth() >= 1200 && bestOverall.getHeight() >= 1550) break;
            } catch (Exception ex) {
                last = ex;
            }
        }
        if (bestOverall != null) {
            entry.coverBitmap = bestOverall;
            entry.detectedPage = 1;
            String quality = bestOverall.getWidth() >= 1400 ? "alta qualidade" : "melhor disponível";
            entry.status = bestStatus + " • " + quality;
            return;
        }
        if (last != null) throw new Exception("Capa correta não confirmada para " + entry.source.name + " (" + last.getMessage() + ")");
        throw new Exception("Nenhuma fonte configurada para " + entry.source.name);
    }

    private static void loadFromHtml(CoverEntry entry, String sourceUrl, java.util.Date targetDate) throws Exception {
        final String browserUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
        Connection.Response pageResponse = Jsoup.connect(sourceUrl)
                .userAgent(browserUa)
                .referrer("https://www.google.com/")
                .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                .timeout(sourceUrl.contains("frontpages.com") ? 12000 : 8000)
                .followRedirects(true)
                .execute();
        Map<String,String> pageCookies = new HashMap<>(pageResponse.cookies());
        Document doc = pageResponse.parse();

        // Para Valor/Washington Post em data escolhida, só aceita páginas do FrontPages
        // quando o próprio documento declara a data solicitada. Evita usar capa atual
        // como se fosse histórica.
        if (targetDate != null && sourceUrl.contains("frontpages.com")) {
            if (!frontPagesDocumentMatchesDate(doc, targetDate)) {
                throw new Exception("FrontPages não corresponde à data selecionada");
            }
        }

        if (entry.source.followSelector != null && !entry.source.followSelector.isEmpty()) {
            Element chosen = null;
            String wanted = normalize(entry.source.followText);
            for (Element link : doc.select(entry.source.followSelector)) {
                String label = normalize(link.text() + " " + link.attr("href") + " " + link.attr("title"));
                if (wanted.isEmpty() || label.contains(wanted)) { chosen = link; break; }
            }
            if (chosen != null && !chosen.absUrl("href").isEmpty()) {
                doc = Jsoup.connect(chosen.absUrl("href"))
                        .userAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
                        .referrer(sourceUrl)
                        .timeout(6500).followRedirects(true).get();
            }
        }

        // LinkedHashSet mantém a ordem e elimina duplicatas.
        // "priority" recebe primeiro imagens cujo alt/title/contexto menciona o jornal.
        // Isso reduz muito a chance de validar capas relacionadas de outro veículo.
        LinkedHashSet<String> priority = new LinkedHashSet<>();
        LinkedHashSet<String> trustedPageMetadata = new LinkedHashSet<>();
        LinkedHashSet<String> found = new LinkedHashSet<>();
        if (entry.source.imageSelector != null && !entry.source.imageSelector.isEmpty())
            for (Element img : doc.select(entry.source.imageSelector)) {
                if (elementMatchesSource(img, entry.source)) addImageUrls(priority, img);
                addImageUrls(found, img);
            }

        // Fontes comuns de imagem principal em páginas modernas.
        boolean dedicatedWashingtonPage = "THE WASHINGTON POST".equalsIgnoreCase(entry.source.name)
                && sourceUrl.replace("http://","https://").startsWith("https://www.frontpages.com/the-washington-post/");
        boolean dedicatedValorPage = "VALOR ECONÔMICO".equalsIgnoreCase(entry.source.name)
                && sourceUrl.replace("http://","https://").startsWith("https://www.frontpages.com/valor-economico/");
        LinkedHashSet<String> blockedSportsUrls = new LinkedHashSet<>();

        // Na página dedicada do Washington Post o FrontPages nem sempre coloca o nome
        // do jornal no alt/title do <img>. A v0.7.1 exigia isso e acabava descartando
        // a própria capa principal. og:image/twitter:image pertencem ao documento atual,
        // portanto são candidatos confiáveis para a capa principal (não para o card SPORTS).
        for (Element meta : doc.select("meta[property=og:image],meta[name=twitter:image],meta[property=twitter:image],meta[itemprop=image],link[rel=image_src],link[rel=preload][as=image]")) {
            String attr = meta.tagName().equals("link") ? "href" : "content";
            String u = meta.absUrl(attr); if (u.isEmpty()) u = meta.attr(attr);
            if (dedicatedWashingtonPage) addUrlWithVariants(trustedPageMetadata, u, doc.location());
            addUrlWithVariants(found, u, doc.location());
        }
        if (!dedicatedWashingtonPage) {
            for (Element source : doc.select("source[srcset],source[data-srcset]")) {
                addSrcSet(found, source.attr("srcset"), source.baseUri());
                addSrcSet(found, source.attr("data-srcset"), source.baseUri());
            }
        }
        for (Element img : doc.select("img")) {
            boolean rejectedSports = isRejectedElementForSource(img, entry.source);
            if (rejectedSports) {
                collectImageUrls(blockedSportsUrls, img);
                continue;
            }
            if (elementMatchesSource(img, entry.source)) addImageUrls(priority, img);
            // Na página dedicada do Washington Post também precisamos coletar todos os <img>.
            // A versão anterior ignorava imagens sem alt/title e, na prática, podia deixar apenas
            // metadados/miniaturas inválidas que retornavam HTTP 404. A validação OCR abaixo
            // continua impedindo que a edição SPORTS seja aceita.
            addImageUrls(found, img);
        }
        // Muitos sites atuais guardam a imagem original em JSON/script em vez de src/srcset.
        // No WaPo, esses URLs embutidos podem incluir também a edição SPORTS; por isso
        // ficam apenas no conjunto genérico. "priority" é reservado ao <img> cujo
        // alt/title identifica explicitamente o jornal principal.
        if (dedicatedWashingtonPage) addEmbeddedImageUrls(found, found, doc, entry.source);
        else addEmbeddedImageUrls(priority, found, doc, entry.source);
        // Às vezes a capa em alta está no href que envolve uma miniatura.
        // Primeiro examina os links que efetivamente contêm uma imagem.
        for (Element a : doc.select("a[href]:has(img)")) {
            String h = a.absUrl("href");
            if (looksLikeImage(h)) addUrlWithVariants(found, h, doc.location());
            for (String attr : new String[]{"data-full", "data-image", "data-src", "data-zoom-image"}) {
                String u = a.absUrl(attr); if (u.isEmpty()) u = a.attr(attr);
                addUrlWithVariants(found, u, doc.location());
            }
        }
        for (Element a : doc.select("a[href]")) {
            String h = a.absUrl("href");
            if (looksLikeImage(h)) addUrlWithVariants(found, h, doc.location());
        }

        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        // FrontPages também lista "The Washington Post SPORTS". Na página dedicada,
        // a ordem é: elemento explicitamente identificado -> metadado da própria página
        // (og:image/twitter:image) -> demais candidatos. Não exigimos mais alt/title,
        // porque a capa principal do FrontPages pode não trazer esses atributos.
        if (dedicatedWashingtonPage) {
            ordered.addAll(priority);
            ordered.addAll(trustedPageMetadata);
            ordered.addAll(found);
            if (ordered.isEmpty()) throw new Exception("Nenhuma imagem candidata encontrada na página do Washington Post");
        } else {
            ordered.addAll(priority);
            ordered.addAll(found);
        }
        List<String> urls = new ArrayList<>(ordered);
        Bitmap best = null;
        int bestScore = Integer.MIN_VALUE;
        Exception last = null;
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            int candidateLimit = (dedicatedWashingtonPage || dedicatedValorPage) ? 30 : (sourceUrl.contains("frontpages.com") ? 18 : 16);
            for (int i = 0; i < Math.min(candidateLimit, urls.size()); i++) {
                try {
                    String candidateUrl = urls.get(i);
                    if (blockedSportsUrls.contains(candidateUrl)) continue;
                    Bitmap b = downloadBitmap(candidateUrl, doc.location(), pageCookies);
                    int base = bitmapScore(b, candidateUrl, entry.source);
                    if (base < 0) { b.recycle(); continue; }

                    // Confirma o jornal SOMENTE pelo topo (masthead). Isso evita, por exemplo,
                    // aceitar O Globo para a Folha apenas porque existe a palavra Datafolha no corpo.
                    int topH = Math.max(1, Math.min(b.getHeight(), Math.round(b.getHeight() * 0.38f)));
                    Bitmap top = Bitmap.createBitmap(b, 0, 0, b.getWidth(), topH);
                    Text topText;
                    try {
                        topText = Tasks.await(recognizer.process(InputImage.fromBitmap(top, 0)));
                    } finally {
                        top.recycle();
                    }
                    String recognizedTop = normalize(topText.getText());
                    int mastheadMatches = mastheadMatches(recognizedTop, entry.source);
                    boolean urlHint = urlStronglyMatchesSource(candidateUrl, entry.source);
                    boolean elementHint = priority.contains(candidateUrl);
                    boolean pageMetadataHint = trustedPageMetadata.contains(candidateUrl);
                    float aspect = b.getHeight() / (float)Math.max(1,b.getWidth());
                    boolean dedicatedWashington = dedicatedWashingtonPage;
                    String candidateNorm = normalize(candidateUrl);
                    boolean sportsCandidate = dedicatedWashington &&
                            (candidateNorm.contains("SPORT") || candidateNorm.contains("SPORTS"));

                    // WaPo: NUNCA aceitar a variante SPORTS. A v0.7.1 aceitava qualquer imagem
                    // grande da página dedicada e acabou escolhendo a página esportiva.
                    if (sportsCandidate) {
                        b.recycle(); continue;
                    }

                    // Na página individual do WaPo, o hint do ELEMENTO é confiável porque agora
                    // só entra em "priority" quando alt/title/data-* identifica Washington Post
                    // e NÃO contém SPORTS. URL genérica não é mais suficiente.
                    boolean trustedDedicatedCandidate = dedicatedWashington
                            && (elementHint || pageMetadataHint)
                            && b.getWidth() >= 700 && b.getHeight() >= 950
                            && aspect >= 1.20f && aspect <= 1.95f;

                    if (dedicatedWashington) {
                        // Preferimos OCR do masthead. Se a fonte gótica não for reconhecida,
                        // aceitamos somente o elemento identificado OU o og:image/twitter:image
                        // da página dedicada, nunca um card relacionado genérico.
                        if (mastheadMatches == 0 && !trustedDedicatedCandidate) {
                            b.recycle(); continue;
                        }
                    } else if (mastheadMatches == 0 && !urlHint && !(elementHint && aspect >= 1.18f)) {
                        b.recycle(); continue;
                    }

                    // Depois de confirmar o cabeçalho/contexto, usa resolução como critério principal.
                    long pixels = (long)b.getWidth() * (long)b.getHeight();
                    int resolutionBonus = (int)Math.min(1400, pixels / 4500L);
                    String cu = candidateUrl.toLowerCase(Locale.ROOT);
                    int cropPenalty = (cu.contains("crop") || cu.contains("thumb") || cu.contains("thumbnail") || cu.contains("preview")) ? 900 : 0;
                    int fullPageBonus = (aspect >= 1.25f && aspect <= 1.70f) ? 500 : 0;
                    int score = base + mastheadMatches * 1400 + (urlHint ? 500 : 0) + (elementHint ? 650 : 0)
                            + (pageMetadataHint ? 1200 : 0) + (trustedDedicatedCandidate ? 900 : 0)
                            + resolutionBonus + fullPageBonus - cropPenalty;
                    if (score > bestScore) {
                        if (best != null) best.recycle();
                        best = b; bestScore = score;
                        // Modo rápido: uma capa confirmada e já legível encerra a varredura.
                        // Evita dezenas de downloads/OCR desnecessários.
                        if (!dedicatedWashingtonPage && best.getWidth() >= 1200 && best.getHeight() >= 1550
                                && best.getHeight()/(float)best.getWidth() >= 1.18f) break;
                    } else b.recycle();
                } catch (Exception ex) { last = ex; }
            }
        } finally { recognizer.close(); }

        if (best == null) {
            String detail = last == null ? "" : " (última tentativa: " + last.getMessage() + ")";
            throw new Exception("Capa correta não confirmada para " + entry.source.name + detail);
        }
        entry.coverBitmap = best;
        entry.detectedPage = 1;
        entry.status = "Capa confirmada v0.7.3 • " + best.getWidth() + "×" + best.getHeight() + " • fonte " + hostOf(doc.location());
    }

    private static boolean isDateSensitiveWebOnly(String name) {
        return "VALOR ECONÔMICO".equalsIgnoreCase(name) || "THE WASHINGTON POST".equalsIgnoreCase(name);
    }

    private static boolean frontPagesDocumentMatchesDate(Document doc, java.util.Date targetDate) {
        java.text.SimpleDateFormat f1 = new java.text.SimpleDateFormat("MMMM d, yyyy", Locale.US);
        java.text.SimpleDateFormat f2 = new java.text.SimpleDateFormat("MMM d, yyyy", Locale.US);
        String wanted1 = normalize(f1.format(targetDate));
        String wanted2 = normalize(f2.format(targetDate));
        String text = normalize(doc.title() + " " + doc.text());
        return (!wanted1.isEmpty() && text.contains(wanted1)) || (!wanted2.isEmpty() && text.contains(wanted2));
    }

    private static boolean isRejectedElementForSource(Element e, NewspaperSource source) {
        if (!"THE WASHINGTON POST".equalsIgnoreCase(source.name)) return false;
        String direct = normalize(e.attr("alt") + " " + e.attr("title") + " " +
                e.attr("data-caption") + " " + e.attr("data-title") + " " +
                e.attr("aria-label"));
        // FrontPages possui uma publicação separada "The Washington Post SPORTS".
        return direct.contains("WASHINGTON POST") && direct.contains("SPORT");
    }

    private static boolean elementMatchesSource(Element e, NewspaperSource source) {
        if ("THE WASHINGTON POST".equalsIgnoreCase(source.name)) {
            String direct = normalize(e.attr("alt") + " " + e.attr("title") + " " +
                    e.attr("data-caption") + " " + e.attr("data-title") + " " +
                    e.attr("aria-label"));
            // Para WaPo usamos somente atributos do próprio elemento, sem texto do pai,
            // pois cards vizinhos podem conter "Washington Post SPORTS".
            return direct.contains("WASHINGTON POST") && !direct.contains("SPORT");
        }
        String info = normalize(e.attr("alt") + " " + e.attr("title") + " " + e.attr("class") + " " +
                e.attr("id") + " " + e.attr("data-caption") + " " + e.attr("data-title") + " " +
                (e.parent() == null ? "" : e.parent().text()));
        return mastheadMatches(info, source) > 0;
    }

    private static boolean urlStronglyMatchesSource(String url, NewspaperSource source) {
        String u = normalize(url);
        if (source.mastheads == null) return false;
        for (String phrase : source.mastheads) {
            String p = normalize(phrase);
            if (!p.isEmpty() && u.contains(p)) return true;
            String[] toks = p.split(" ");
            int strong = 0;
            for (String t : toks) if (t.length() >= 4 && u.contains(t)) strong++;
            if (strong >= 2) return true;
        }
        // aliases comuns nos endereços dos próprios veículos
        String name = normalize(source.name);
        if (name.contains("NEW YORK TIMES") && (u.contains("NYTIMES") || (u.contains("NEW") && u.contains("YORK") && u.contains("TIMES")))) return true;
        if (name.contains("WASHINGTON POST") && (u.contains("WASHINGTONPOST") || (u.contains("WASHINGTON") && u.contains("POST")))) return true;
        if (name.contains("ESTADAO") && (u.contains("ESTADAO") || (u.contains("ESTADO") && u.contains("PAULO")))) return true;
        return false;
    }

    private static void addEmbeddedImageUrls(java.util.Set<String> priority, java.util.Set<String> all,
                                             Document doc, NewspaperSource source) {
        String html = doc.html().replace("\\/", "/").replace("&amp;", "&");
        Pattern p = Pattern.compile("(?i)(https?:)?//[^\\\"'<>\\s]+?\\.(?:jpg|jpeg|png|webp)(?:\\?[^\\\"'<>\\s]*)?");
        Matcher m = p.matcher(html);
        int count = 0;
        while (m.find() && count < 300) {
            String u = m.group();
            if (u.startsWith("//")) u = "https:" + u;
            addUrlWithVariants(urlStronglyMatchesSource(u, source) ? priority : all, u, doc.location());
            count++;
        }
    }

    private static void collectImageUrls(java.util.Set<String> urls, Element img) {
        addSrcSet(urls, img.attr("srcset"), img.baseUri());
        addSrcSet(urls, img.attr("data-srcset"), img.baseUri());
        String[] attrs = {"data-original", "data-original-src", "data-lazy-src", "data-src", "data-src-large",
                "data-full", "data-full-src", "data-large-file", "data-large_image", "data-large-image",
                "data-zoom-image", "data-zoom", "data-image", "data-hires", "data-highres", "data-cfsrc", "src"};
        for (String a : attrs) {
            String u = img.absUrl(a); if (u.isEmpty()) u = img.attr(a);
            addUrlWithVariants(urls, u, img.baseUri());
        }
    }

    private static void addImageUrls(java.util.Set<String> urls, Element img) {
        addSrcSet(urls, img.attr("srcset"), img.baseUri());
        addSrcSet(urls, img.attr("data-srcset"), img.baseUri());
        String[] attrs = {"data-original", "data-original-src", "data-lazy-src", "data-src", "data-src-large",
                "data-full", "data-full-src", "data-large-file", "data-large_image", "data-large-image",
                "data-zoom-image", "data-zoom", "data-image", "data-hires", "data-highres", "data-cfsrc", "src"};
        for (String a : attrs) {
            String u = img.absUrl(a); if (u.isEmpty()) u = img.attr(a);
            addUrlWithVariants(urls, u, img.baseUri());
        }
        String style = img.attr("style");
        int u1 = style.indexOf("url(");
        if (u1 >= 0) {
            int start = u1 + 4, end = style.indexOf(')', start);
            if (end > start) {
                String bg = style.substring(start, end).replace("\"", "").replace("'", "").trim();
                addUrlWithVariants(urls, bg, img.baseUri());
            }
        }
    }

    private static void addSrcSet(java.util.Set<String> urls, String srcset, String baseUri) {
        if (srcset == null || srcset.trim().isEmpty()) return;
        String[] parts = srcset.split(",");
        // Primeiro as variantes maiores, normalmente listadas no fim.
        for (int i = parts.length - 1; i >= 0; i--) {
            String[] bits = parts[i].trim().split("\\s+");
            if (bits.length > 0) addUrlWithVariants(urls, bits[0], baseUri);
        }
    }

    private static void addUrlWithVariants(java.util.Set<String> urls, String raw, String baseUri) {
        if (raw == null || raw.trim().isEmpty()) return;
        String u = raw.trim().replace("&amp;", "&");
        try { if (!u.startsWith("http") && baseUri != null && !baseUri.isEmpty()) u = new URL(new URL(baseUri), u).toString(); } catch (Exception ignored) {}
        if (!u.startsWith("http")) return;

        // IMPORTANTE: mantém primeiro a URL exata. Em CDNs assinados, remover a query gera 403/404.
        urls.add(u);

        // FrontPages já entrega a URL correta da imagem/thumbnail no HTML. Inventar
        // variantes sem query/tamanho gerava muitos 404/timeouts (especialmente WaPo).
        if (baseUri != null && baseUri.toLowerCase(Locale.ROOT).contains("frontpages.com")) return;

        String noQuery = u;
        int q = noQuery.indexOf('?');
        if (q > 0) noQuery = noQuery.substring(0, q);
        if (looksLikeImage(noQuery)) urls.add(noQuery);

        // Variantes frequentes de miniaturas WordPress/CDN.
        String unsized = noQuery.replaceFirst("-(\\d{2,5})x(\\d{2,5})(?=\\.[A-Za-z]{3,5}$)", "")
                .replaceFirst("_(\\d{2,5})x(\\d{2,5})(?=\\.[A-Za-z]{3,5}$)", "")
                .replaceFirst("(?i)([-_](small|medium|thumb|thumbnail|preview))(?=\\.[A-Za-z]{3,5}$)", "");
        if (!unsized.equals(noQuery) && looksLikeImage(unsized)) urls.add(unsized);

        // Alguns catálogos usam a resolução como segmento do nome: .750.jpg / .600.jpg.
        String numeric = noQuery.replaceFirst("\\.(600|640|700|720|750|800|900|1024)(?=\\.(jpg|jpeg|png|webp)$)", "");
        if (!numeric.equals(noQuery)) {
            for (String size : new String[]{"2000","1800","1600","1400","1200"}) {
                String ext = numeric.substring(numeric.lastIndexOf('.'));
                String stem = numeric.substring(0, numeric.lastIndexOf('.'));
                urls.add(stem + "." + size + ext);
            }
            urls.add(numeric);
        }
    }

    private static boolean looksLikeImage(String u) {
        if (u == null) return false;
        String x = u.toLowerCase(Locale.ROOT);
        return x.matches(".*\\.(jpg|jpeg|png|webp|avif)(\\?.*)?$");
    }

    private static int mastheadMatches(String content, NewspaperSource source) {
        int matches = 0;
        List<String> list = (source.mastheads != null && !source.mastheads.isEmpty()) ? source.mastheads : source.keywords;
        if (list != null) for (String phrase : list) {
            String p = normalize(phrase);
            if (p.isEmpty()) continue;
            if (content.contains(p)) { matches += 2; continue; }

            // Tolerância ao OCR do logotipo: exige pelo menos dois termos fortes do nome.
            // Ex.: "O ESTADO DE S. PAULO" ainda passa se o OCR ler apenas ESTADO ... PAULO.
            String[] tokens = p.split(" ");
            int strongTotal = 0, strongFound = 0;
            for (String t : tokens) {
                if (t.length() < 4) continue;
                strongTotal++;
                if (content.contains(t)) strongFound++;
            }
            if (strongTotal >= 2 && strongFound >= Math.min(2, strongTotal)) matches++;
        }
        return matches;
    }

    private static int keywordMatches(String content, NewspaperSource source) {
        int matches = 0;
        if (source.keywords != null) for (String keyword : source.keywords) {
            String k = normalize(keyword);
            if (!k.isEmpty() && content.contains(k)) matches++;
        }
        return matches;
    }

    private static int bitmapScore(Bitmap b, String url, NewspaperSource source) {
        int w=b.getWidth(), h=b.getHeight();
        if (w < 400 || h < 550) return -1000;
        // Uma capa de jornal completa é claramente vertical. As miniaturas 4:3 que estavam
        // sendo aceitas eram previews CORTADOS do site. Rejeitamos esses candidatos.
        float heightToWidth=h/(float)w;
        // Capas completas reais podem ter proporção por volta de 1,25-1,35.
        // O limite antigo 1,38 rejeitava justamente páginas completas e favorecia recortes.
        if (heightToWidth < 1.18f) return -1000;
        int score=0; float ratio=w/(float)h;
        if (h>w) score+=100;
        if (ratio>=0.45f && ratio<=0.72f) score+=180;
        else if (ratio<=0.80f) score+=90;
        // Favorece fortemente a maior resolução real disponível.
        score += Math.min(350, (w*h)/10000);
        if (w>=1200) score+=100; if (h>=1600) score+=100;
        String info=normalize(url);
        if (info.contains("CAPA")||info.contains("COVER")||info.contains("FRONTPAGE")||info.contains("PORTADA")) score+=100;
        if (info.contains("LOGO")||info.contains("ICON")||info.contains("BANNER")||info.contains("THUMB")) score-=250;
        return score;
    }

    private static void loadFromPdf(Context context, CoverEntry entry) throws Exception {
        File pdf = new File(context.getCacheDir(), safe(entry.source.name) + ".pdf");
        downloadFile(entry.source.url, pdf);
        List<CandidatePage> candidates = new ArrayList<>();
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(pfd)) {
            int count = Math.min(MAX_PAGES, renderer.getPageCount());
            for (int i = 0; i < count; i++) {
                CandidatePage candidate = new CandidatePage();
                candidate.pageNumber = i + 1;
                try (PdfRenderer.Page page = renderer.openPage(i)) {
                    int width = 2400;
                    int height = Math.max(1, Math.round(width * (page.getHeight() / (float) page.getWidth())));
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(0xFFFFFFFF);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                    Text text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)));
                    candidate.recognizedText = text.getText() == null ? "" : text.getText();
                    candidate.score = score(entry.source, text);
                    if (keywordMatches(normalize(candidate.recognizedText), entry.source) == 0) candidate.score -= 1000;
                    candidate.imageFile = new File(context.getCacheDir(), safe(entry.source.name) + "-p" + candidate.pageNumber + ".jpg");
                    try (FileOutputStream fos = new FileOutputStream(candidate.imageFile)) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 96, fos);
                    }
                    bitmap.recycle();
                }
                candidates.add(candidate);
            }
        } finally {
            recognizer.close();
        }

        if (candidates.isEmpty()) throw new Exception("PDF sem páginas");
        CandidatePage best = candidates.stream().max(Comparator.comparingInt(c -> c.score)).orElse(candidates.get(0));
        entry.candidates = candidates;
        entry.detectedPage = best.pageNumber;
        entry.coverBitmap = BitmapFactory.decodeFile(best.imageFile.getAbsolutePath());
        entry.status = "Capa detectada na página " + best.pageNumber;
    }

    private static int score(NewspaperSource source, Text text) {
        String content = normalize(text.getText());
        int score = 0;

        if (source.keywords != null) {
            for (String keyword : source.keywords) {
                String k = normalize(keyword);
                if (!k.isEmpty() && content.contains(k)) score += 45;
            }
        }

        int blocks = text.getTextBlocks().size();
        int lines = 0;
        for (Text.TextBlock b : text.getTextBlocks()) lines += b.getLines().size();
        score += Math.min(35, blocks * 3);
        score += Math.min(35, lines / 2);

        String[] adWords = {
                "PUBLICIDADE", "ANUNCIO", "ANUNCIE", "PROMOCAO", "OFERTA",
                "COMPRE", "DESCONTO", "ASSINE", "CUPOM", "PARCELAS"
        };
        int ads = 0;
        for (String word : adWords) if (content.contains(word)) ads++;
        score -= ads * 20;

        // Capas normalmente têm bastante texto. Páginas quase vazias recebem penalidade.
        if (content.length() < 120) score -= 35;
        if (lines < 5) score -= 25;
        return score;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String n = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return n.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").replaceAll("\\s+", " ").trim();
    }

    private static Bitmap downloadBitmap(String urlString) throws Exception {
        return downloadBitmap(urlString, null, null);
    }

    private static Bitmap downloadBitmap(String urlString, String referer) throws Exception {
        return downloadBitmap(urlString, referer, null);
    }

    private static Bitmap downloadBitmap(String urlString, String referer, Map<String,String> cookies) throws Exception {
        HttpURLConnection c = open(urlString, referer, cookies);
        try (InputStream in = new BufferedInputStream(c.getInputStream())) {
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (bitmap == null) throw new Exception("Imagem inválida ou formato não suportado");
            return bitmap;
        } finally {
            c.disconnect();
        }
    }

    private static void downloadFile(String urlString, File out) throws Exception {
        HttpURLConnection c = open(urlString, null);
        try (InputStream in = new BufferedInputStream(c.getInputStream());
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) fos.write(buffer, 0, n);
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String urlString) throws Exception {
        return open(urlString, null, null);
    }

    private static HttpURLConnection open(String urlString, String referer) throws Exception {
        return open(urlString, referer, null);
    }

    private static HttpURLConnection open(String urlString, String referer, Map<String,String> cookies) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlString).openConnection();
        c.setConnectTimeout(5500);
        c.setReadTimeout(12000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        c.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
        c.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7");
        c.setRequestProperty("Cache-Control", "no-cache");
        if (referer != null && !referer.trim().isEmpty()) c.setRequestProperty("Referer", referer);
        if (cookies != null && !cookies.isEmpty()) {
            StringBuilder cookieHeader = new StringBuilder();
            for (Map.Entry<String,String> e : cookies.entrySet()) {
                if (cookieHeader.length() > 0) cookieHeader.append("; ");
                cookieHeader.append(e.getKey()).append("=").append(e.getValue());
            }
            c.setRequestProperty("Cookie", cookieHeader.toString());
        }
        int code = c.getResponseCode();
        if (code < 200 || code >= 400) { c.disconnect(); throw new Exception("HTTP " + code); }
        return c;
    }

    private static boolean isSameDay(java.util.Date a, java.util.Date b) {
        java.util.Calendar ca=java.util.Calendar.getInstance(); ca.setTime(a);
        java.util.Calendar cb=java.util.Calendar.getInstance(); cb.setTime(b);
        return ca.get(java.util.Calendar.YEAR)==cb.get(java.util.Calendar.YEAR) &&
                ca.get(java.util.Calendar.DAY_OF_YEAR)==cb.get(java.util.Calendar.DAY_OF_YEAR);
    }

    private static String hostOf(String url) {
        try { return new URL(url).getHost().replaceFirst("^www\\.", ""); }
        catch (Exception e) { return "web"; }
    }

    private static String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
