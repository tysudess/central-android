package br.com.principaiscapas;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;

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
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * v0.7.7.0 — seleção automática da capa entre todas as candidatas consolidadas do Gmail.
 *
 * Regras principais:
 * - analisa TODAS as páginas válidas recebidas para o mesmo jornal;
 * - não exige que a primeira página seja a capa;
 * - dá peso alto ao masthead correto no topo e à sua proeminência;
 * - reduz a pontuação de páginas com sinais claros de publicidade;
 * - usa a data selecionada apenas como evidência adicional, nunca como único critério;
 * - sempre escolhe a candidata de MAIOR confiança entre as páginas válidas;
 * - preserva os arquivos candidatos para o botão REVISAR CAPA.
 *
 * Jornais do Gmail não usam fallback da internet. Valor Econômico e Washington Post
 * são tratados fora desta classe pelas fontes web já existentes no projeto.
 */
public class ClippingImageScanner {
    private static final int PREVIEW_WIDTH = 1200;
    private static final int MIN_WIDTH = 700;
    private static final int MIN_HEIGHT = 950;

    public static Set<String> fillFromDirectImages(Context context,
                                                   Map<String, List<String>> coverUrls,
                                                   List<CoverEntry> entries,
                                                   Date targetDate) throws Exception {
        Set<String> found = new HashSet<>();
        if (coverUrls == null || coverUrls.isEmpty()) return found;
        int globalIndex = 0;

        for (CoverEntry e : entries) {
            if (e.coverBitmap != null || isWebOnly(e.source.name)) continue;
            List<String> urls = findUrls(coverUrls, e.source.name);
            if (urls == null || urls.isEmpty()) continue;

            e.candidates.clear();

            File bestFile = null;
            Bitmap bestPreview = null;
            int bestW = 0, bestH = 0;
            long bestPixels = -1;
            int bestScore = -1;
            int bestOrdinal = 0;
            String lastReason = "nenhum candidato válido";

            // v0.7.6.0: o total da revisão é o total de POSIÇÕES recebidas do Gmail,
            // não apenas o total de imagens que o resolver conseguiu abrir.
            int totalConsidered = urls.size();

            for (int slot = 0; slot < totalConsidered; slot++) {
                String url = urls.get(slot);
                int pageNumber = slot + 1;

                CandidatePage cp = new CandidatePage();
                cp.pageNumber = pageNumber;
                cp.score = 0;
                cp.available = false;
                cp.errorMessage = "Ver página não pôde ser aberta";

                if (url == null || url.trim().isEmpty()) {
                    e.candidates.add(cp);
                    lastReason = cp.errorMessage;
                    continue;
                }

                File out = new File(context.getCacheDir(),
                        "clipping-v076-" + (globalIndex++) + "-" + safe(e.source.name) + "-c" + pageNumber + ".img");
                Bitmap preview = null;
                boolean keepFile = false;
                try {
                    download(url, out);

                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(out.getAbsolutePath(), bounds);
                    if (bounds.outWidth < MIN_WIDTH || bounds.outHeight < MIN_HEIGHT) {
                        throw new Exception("arquivo pequeno/placeholder (" + bounds.outWidth + "×" + bounds.outHeight + ")");
                    }
                    float aspect = bounds.outHeight / (float) Math.max(1, bounds.outWidth);
                    if (aspect < 1.12f) throw new Exception("formato não parece página inteira");

                    BitmapFactory.Options previewOpts = new BitmapFactory.Options();
                    previewOpts.inSampleSize = sampleForWidth(bounds.outWidth, PREVIEW_WIDTH);
                    previewOpts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    preview = BitmapFactory.decodeFile(out.getAbsolutePath(), previewOpts);
                    if (preview == null) throw new Exception("não foi possível abrir a imagem original");

                    CandidateScore analysis = scoreCandidate(preview, e.source.name, targetDate);

                    cp.score = analysis.score;
                    cp.imageFile = out;
                    cp.recognizedText = analysis.recognizedText;
                    cp.available = true;
                    cp.errorMessage = "";
                    keepFile = true;

                    long pixels = (long) bounds.outWidth * (long) bounds.outHeight;
                    boolean better = analysis.score > bestScore ||
                            (analysis.score == bestScore && pixels > bestPixels);
                    if (better) {
                        if (bestPreview != null && bestPreview != preview && !bestPreview.isRecycled()) bestPreview.recycle();
                        bestPreview = preview;
                        bestFile = out;
                        bestW = bounds.outWidth;
                        bestH = bounds.outHeight;
                        bestPixels = pixels;
                        bestScore = analysis.score;
                        bestOrdinal = pageNumber;
                        preview = null;
                    }
                } catch (Exception ex) {
                    lastReason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    cp.available = false;
                    cp.errorMessage = lastReason;
                    cp.imageFile = null;
                    cp.score = 0;
                } finally {
                    e.candidates.add(cp); // sempre 1 slot visível para cada página recebida
                    if (preview != null && !preview.isRecycled()) preview.recycle();
                    if (!keepFile && out.exists()) out.delete();
                }
            }

            if (bestPreview != null && bestFile != null) {
                e.coverBitmap = bestPreview;
                e.originalImagePath = bestFile.getAbsolutePath();
                e.originalWidth = bestW;
                e.originalHeight = bestH;
                e.detectedPage = bestOrdinal;
                e.status = "Gmail • candidata " + bestOrdinal + "/" + Math.max(1, totalConsidered) +
                        " escolhida • confiança " + bestScore + "% • " + bestW + "×" + bestH;
                found.add(e.source.name);
            } else {
                e.coverBitmap = null;
                e.originalImagePath = "";
                e.originalWidth = 0;
                e.originalHeight = 0;
                e.detectedPage = 0;
                e.status = "Gmail: não foi possível analisar as páginas (" + lastReason + ") • sem usar internet";
            }
        }
        return found;
    }

    private static class CandidateScore {
        final int score;
        final String recognizedText;
        CandidateScore(int score, String recognizedText) {
            this.score = score;
            this.recognizedText = recognizedText == null ? "" : recognizedText;
        }
    }

    /**
     * Produz uma confiança de 0..100. Não é uma probabilidade estatística; é uma
     * pontuação comparativa criada para distinguir capa, página interna e publicidade.
     */
    private static CandidateScore scoreCandidate(Bitmap bitmap, String name, Date targetDate) {
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            Text result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)));
            String raw = result.getText() == null ? "" : result.getText();
            String full = normalize(raw);

            StringBuilder top20 = new StringBuilder();
            StringBuilder top35 = new StringBuilder();
            StringBuilder top50 = new StringBuilder();
            int lineCount = 0;
            int mastheadStrength = 0;
            boolean internalPageMarkerTop = false;

            final int h = Math.max(1, bitmap.getHeight());
            final int w = Math.max(1, bitmap.getWidth());

            for (Text.TextBlock block : result.getTextBlocks()) {
                for (Text.Line line : block.getLines()) {
                    lineCount++;
                    Rect box = line.getBoundingBox();
                    String lineText = normalize(line.getText());
                    if (box == null) continue;

                    float centerY = (box.top + box.bottom) / 2f / h;
                    if (centerY <= 0.20f) top20.append(' ').append(lineText);
                    if (centerY <= 0.35f) top35.append(' ').append(lineText);
                    if (centerY <= 0.50f) top50.append(' ').append(lineText);

                    if (centerY <= 0.20f && isInternalPageHeaderLine(lineText)) {
                        internalPageMarkerTop = true;
                    }

                    if (matchesExpected(lineText, name) && centerY <= 0.32f) {
                        float heightFrac = box.height() / (float) h;
                        float widthFrac = box.width() / (float) w;
                        int strength = 1;
                        if (heightFrac >= 0.018f || widthFrac >= 0.24f) strength = 2;
                        if (heightFrac >= 0.030f || widthFrac >= 0.40f) strength = 3;
                        if (heightFrac >= 0.045f || widthFrac >= 0.55f) strength = 4;
                        mastheadStrength = Math.max(mastheadStrength, strength);
                    }
                }
            }

            String t20 = normalize(top20.toString());
            String t35 = normalize(top35.toString());
            String t50 = normalize(top50.toString());

            boolean expected20 = matchesExpected(t20, name);
            boolean expected35 = matchesExpected(t35, name);
            boolean expected50 = matchesExpected(t50, name);
            boolean expectedFull = matchesExpected(full, name);

            // Se o OCR dividiu o masthead em várias linhas, a zona superior ainda o reconhece.
            if (expected20 && mastheadStrength == 0) mastheadStrength = 1;

            int score = 8;
            if (mastheadStrength >= 4) score += 72;
            else if (mastheadStrength == 3) score += 64;
            else if (mastheadStrength == 2) score += 54;
            else if (expected20) score += 42;
            else if (expected35) score += 32;
            else if (expected50) score += 22;
            else if (expectedFull) score += 10;

            // Capa costuma ter bastante conteúdo editorial distribuído pela página.
            if (lineCount >= 24) score += 10;
            else if (lineCount >= 14) score += 7;
            else if (lineCount >= 8) score += 3;
            else if (lineCount <= 3) score -= 8;

            if (dateMatches(full, targetDate)) score += 8;

            String other = detectOtherNewspaper(t35, normalize(name));
            if (!other.isEmpty()) score -= 70;

            boolean ad = containsAdSignal(full);
            if (ad) score -= expected20 ? 10 : 35;

            // v0.7.7.6 — PERFIS DE CAPA.
            // Esta camada NÃO descarta candidatas. Ela apenas melhora a ordem de confiança
            // usando características que já conhecemos das capas principais: masthead forte
            // no topo, data correta, página inteira e ausência de sinais de página interna.
            boolean profileDate = dateMatches(full, targetDate);
            boolean profileExactTop = matchesCoverProfileTop(t20, t35, name);
            boolean profileStrongTop = mastheadStrength >= 3 ||
                    (profileExactTop && mastheadStrength >= 2);
            boolean profileMediumTop = mastheadStrength >= 2 || expected20 || profileExactTop;
            boolean profileEstadao = normalize(name).contains("ESTADAO");

            if (profileExactTop) score += 10;
            if (profileStrongTop) score += 8;
            if (profileStrongTop && profileDate) score += 12;

            // Marcadores A2/B4/C3 etc. no alto costumam indicar página interna.
            // No Estadão não aplicamos isso, pois as chamadas da própria capa podem trazer A2/A3 etc.
            if (internalPageMarkerTop && !profileEstadao && !profileStrongTop) {
                score -= 28;
                score = Math.min(score, 48);
            }

            // Se o nome do jornal só apareceu no corpo da página, sem presença convincente
            // no topo, não deixamos essa página interna subir demais no ranking.
            if (!profileMediumTop && expectedFull) score = Math.min(score, 62);

            // v0.7.6.2 — ESTADÃO:
            // A decisão é feita pela imagem final. Uma chamada de capa pode conter
            // A2/A3/A12/B4 etc. indicando a página da matéria; isso NÃO transforma a
            // imagem em página interna. Por isso, marcador Axx não recebe penalidade.
            // "FUNDADO EM 1875" sozinho continua insuficiente para identificar a capa.
            boolean estadao = normalize(name).contains("ESTADAO");
            if (estadao) {
                boolean exactEstadaoTop = containsExactEstadaoMasthead(t35);
                boolean targetDatePresent = dateMatches(full, targetDate);
                boolean founderOnly = full.contains("FUNDADO EM 1875") && !exactEstadaoTop;

                if (exactEstadaoTop && targetDatePresent) score += 28;
                else if (exactEstadaoTop) score += 14;

                if (founderOnly) score = Math.min(score, 62);
                // Não penalizar A2/A3/A12/B4 no Estadão: pode ser uma chamada da capa.
            }

            // NYT: a linha de copyright "The New York Times Company" aparece em
            // páginas internas e já gerou falso positivo de 80%. Se ela existir sem
            // um masthead forte na parte superior, derrubamos a confiança.
            boolean nyt = normalize(name).contains("NEW YORK TIMES");
            boolean nytCompanyOnly = nyt && full.contains("THE NEW YORK TIMES COMPANY") && !isStrongNytMasthead(t20);
            if (nytCompanyOnly) {
                score -= 45;
                score = Math.min(score, 35);
            }

            // Quando o perfil da capa está muito forte, elevamos a confiança. Ainda assim,
            // todas as candidatas continuam disponíveis no REVISAR CAPA.
            if (profileExactTop && profileStrongTop && profileDate && other.isEmpty() && !ad && lineCount >= 8) {
                score = Math.max(score, 92);
            } else if (profileExactTop && profileStrongTop && other.isEmpty() && !ad) {
                score = Math.max(score, 84);
            }

            // Sem o nome/masthead do jornal, a página ainda pode vencer se todas as outras
            // forem piores, mas nunca recebe uma confiança artificialmente alta.
            if (!expectedFull) score = Math.min(score, 55);
            if (!other.isEmpty()) score = Math.min(score, 20);
            if (ad && !expected20) score = Math.min(score, 35);

            score = Math.max(0, Math.min(100, score));
            return new CandidateScore(score, raw);
        } catch (Exception ex) {
            return new CandidateScore(0, "");
        } finally {
            recognizer.close();
        }
    }

    private static boolean matchesExpected(String text, String name) {
        String t = normalize(text);
        String expected = normalize(name);
        if (expected.equals("O GLOBO")) {
            return t.contains("O GLOBO") || (t.contains("GLOBO") && t.contains("IRINEU MARINHO"));
        }
        if (expected.contains("FOLHA")) {
            return t.contains("FOLHA") && (t.contains("PAULO") || t.contains("S PAULO"));
        }
        if (expected.contains("ESTADAO")) {
            // "FUNDADO EM 1875" sozinho não é suficiente: a frase também aparece
            // no cabeçalho de páginas internas. Exigimos o nome do jornal.
            return t.contains("ESTADAO") || (t.contains("ESTADO") && t.contains("S PAULO")) ||
                    (t.contains("ESTADO") && t.contains("SAO PAULO"));
        }
        if (expected.contains("CORREIO BRAZILIENSE")) {
            return t.contains("CORREIO") && t.contains("BRAZILIENSE");
        }
        if (expected.contains("ESTADO DE MINAS")) {
            return t.contains("ESTADO") && t.contains("MINAS");
        }
        if (expected.contains("NEW YORK TIMES")) {
            // A capa verdadeira traz o masthead "THE NEW YORK TIMES".
            // Páginas internas podem trazer apenas o copyright
            // "The New York Times Company", que não deve valer como masthead.
            return isStrongNytMasthead(t);
        }
        return t.contains(expected);
    }


    /**
     * Assinatura textual do topo da capa por jornal. É deliberadamente conservadora:
     * serve para pontuar, nunca para eliminar uma candidata.
     */
    private static boolean matchesCoverProfileTop(String top20, String top35, String name) {
        String t20 = normalize(top20);
        String t35 = normalize(top35);
        String n = normalize(name);

        if (n.equals("O GLOBO")) {
            return t20.contains("O GLOBO") ||
                    (t20.contains("GLOBO") && t35.contains("IRINEU MARINHO"));
        }
        if (n.contains("FOLHA")) {
            return t20.contains("FOLHA DE S PAULO") ||
                    t20.contains("FOLHA DE SAO PAULO") ||
                    (t20.contains("FOLHA") && t20.contains("PAULO"));
        }
        if (n.contains("ESTADAO")) {
            return containsExactEstadaoMasthead(t35);
        }
        if (n.contains("CORREIO BRAZILIENSE")) {
            return t20.contains("CORREIO BRAZILIENSE") ||
                    (t20.contains("CORREIO") && t20.contains("BRAZILIENSE"));
        }
        if (n.contains("ESTADO DE MINAS")) {
            return t20.contains("ESTADO DE MINAS") ||
                    (t20.contains("ESTADO") && t20.contains("MINAS"));
        }
        if (n.contains("NEW YORK TIMES")) {
            return isStrongNytMasthead(t20);
        }
        return matchesExpected(t20, name);
    }

    private static boolean containsExactEstadaoMasthead(String text) {
        String t = normalize(text);
        return t.contains("O ESTADO DE S PAULO") || t.contains("O ESTADO DE SAO PAULO") ||
                t.contains("ESTADAO");
    }

    private static boolean isInternalPageHeaderLine(String text) {
        String t = normalize(text);
        if (t.isEmpty()) return false;
        // Marcadores típicos de páginas/seções internas: A2, A3, A12, B4 etc.
        return t.matches("^(A|B|C|D)\\s?\\d{1,2}(\\s.*)?$");
    }

    private static boolean isStrongNytMasthead(String text) {
        String t = normalize(text);
        if (t.contains("ALL THE NEWS THAT S FIT TO PRINT") ||
                t.contains("ALL THE NEWS THAT IS FIT TO PRINT")) return true;
        if (!t.contains("THE NEW YORK TIMES")) return false;
        // Evita aceitar como capa a linha de copyright das páginas internas.
        if (t.contains("THE NEW YORK TIMES COMPANY") &&
                !t.matches(".*THE NEW YORK TIMES (MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY).*")) {
            return false;
        }
        return true;
    }

    private static boolean containsAdSignal(String text) {
        String t = normalize(text);
        return t.contains("PUBLICIDADE") ||
                t.contains("INFORME PUBLICITARIO") ||
                t.contains("CONTEUDO PUBLICITARIO") ||
                t.contains("ADVERTISEMENT") ||
                t.contains("ADVERTORIAL") ||
                t.contains("PAID CONTENT") ||
                t.contains("SPONSORED CONTENT");
    }

    private static boolean dateMatches(String normalizedText, Date targetDate) {
        if (targetDate == null || normalizedText == null || normalizedText.isEmpty()) return false;
        try {
            String day = new SimpleDateFormat("d", Locale.ROOT).format(targetDate);
            String year = new SimpleDateFormat("yyyy", Locale.ROOT).format(targetDate);
            String monthPt = normalize(new SimpleDateFormat("MMMM", new Locale("pt", "BR")).format(targetDate));
            String monthEn = normalize(new SimpleDateFormat("MMMM", Locale.ENGLISH).format(targetDate));
            boolean hasDay = containsNumberToken(normalizedText, day);
            boolean hasYear = normalizedText.contains(year);
            boolean hasMonth = (!monthPt.isEmpty() && normalizedText.contains(monthPt)) ||
                    (!monthEn.isEmpty() && normalizedText.contains(monthEn));
            return hasDay && hasYear && hasMonth;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean containsNumberToken(String text, String number) {
        if (text == null || number == null || number.isEmpty()) return false;
        String padded = " " + text + " ";
        return padded.contains(" " + number + " ");
    }

    private static String detectOtherNewspaper(String text, String expected) {
        if (text.contains("O GLOBO") && !expected.equals("O GLOBO")) return "O GLOBO";
        if (text.contains("FOLHA") && text.contains("PAULO") && !expected.contains("FOLHA")) return "FOLHA DE SÃO PAULO";
        if (text.contains("CORREIO") && text.contains("BRAZILIENSE") && !expected.contains("CORREIO BRAZILIENSE")) return "CORREIO BRAZILIENSE";
        if (text.contains("ESTADO") && text.contains("MINAS") && !expected.contains("ESTADO DE MINAS")) return "ESTADO DE MINAS";
        if (((text.contains("ESTADO") && text.contains("PAULO")) || text.contains("ESTADAO")) && !expected.contains("ESTADAO")) return "ESTADÃO";
        if (text.contains("NEW YORK") && text.contains("TIMES") && !expected.contains("NEW YORK TIMES")) return "THE NEW YORK TIMES";
        return "";
    }

    private static List<String> findUrls(Map<String, List<String>> urls, String name) {
        List<String> exact = urls.get(name);
        if (exact != null) return exact;
        String n = normalize(name);
        for (Map.Entry<String, List<String>> x : urls.entrySet()) {
            if (normalize(x.getKey()).equals(n)) return x.getValue();
        }
        return new ArrayList<>();
    }

    private static boolean isWebOnly(String name) {
        return "VALOR ECONÔMICO".equalsIgnoreCase(name) || "THE WASHINGTON POST".equalsIgnoreCase(name);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ").replaceAll("\\s+", " ").trim();
    }

    private static int sampleForWidth(int width, int target) {
        int s = 1;
        while (width / (s * 2) >= target) s *= 2;
        return Math.max(1, s);
    }

    private static void download(String url, File out) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6500);
        c.setReadTimeout(18000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) PrincipaisCapas/0.7.5.0");
        c.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) { c.disconnect(); throw new Exception("imagem HTTP " + code); }
        try (InputStream in = new BufferedInputStream(c.getInputStream()); FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[32768]; int n; long total = 0;
            while ((n = in.read(buf)) != -1) {
                fos.write(buf, 0, n); total += n;
                if (total > 60L * 1024L * 1024L) throw new Exception("imagem maior que 60 MB");
            }
        } finally { c.disconnect(); }
        if (out.length() < 8192) throw new Exception("arquivo de imagem vazio/placeholder");
    }

    private static String safe(String s) {
        return normalize(s).replace(' ', '_');
    }
}
