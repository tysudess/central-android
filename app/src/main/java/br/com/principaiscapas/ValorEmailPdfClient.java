package br.com.principaiscapas;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * v0.7.7.5 — prioridade especial do Valor Econômico.
 *
 * Fluxo isolado, sem alterar os demais jornais:
 * 1) consulta a ponte Gmail já configurada no app;
 * 2) procura o e-mail "Monitoramento: CAPAS DE JORNAIS" enviado por
 *    diguinhodess@gmail.com;
 * 3) baixa os PDFs Valor-Economico-AAAA-MM-DD-Pagina-1/2/3.pdf;
 * 4) mantém os três PDFs dentro do armazenamento privado do aplicativo;
 * 5) também salva os anexos em Downloads/Principais Capas/Valor Economico;
 * 6) renderiza os três PDFs e coloca as três páginas em REVISAR CAPA;
 * 7) a Página 1 continua selecionada automaticamente como capa;
 * 8) se a Página 1 não vier do Gmail, o fallback web da v0.7.7.2 permanece.
 */
public final class ValorEmailPdfClient {
    private ValorEmailPdfClient() {}

    private static final String ACCESS_KEY = "PC26-8F2D4A7B-31C9E6F0-5A1D";
    private static final int PREVIEW_WIDTH = 1200;
    private static final int RENDER_WIDTH = 2400;

    private static final class PdfMeta {
        int page;
        String filename;
        long size;
    }

    /** Retorna true somente quando a Página 1 do Valor foi carregada com sucesso. */
    public static boolean loadValorFromGmail(Context context, CoverEntry entry, Date targetDate) throws Exception {
        String base = ClippingFeedClient.getConfiguredUrl(context);
        if (base == null || base.trim().isEmpty() || !ClippingFeedClient.isValidWebAppUrl(base)) return false;

        Date safeDate = targetDate == null ? new Date() : targetDate;
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(safeDate);

        JSONObject manifest = requestJson(buildEndpoint(base, "valor_manifest", date, 0));
        if (!manifest.optBoolean("ok", false)) {
            throw new Exception(manifest.optString("error", "Falha ao consultar PDFs do Valor no Gmail"));
        }

        // Ponte antiga: action desconhecida cai no feed normal e não possui o campo pages.
        // Nesse caso não quebramos nada; apenas deixamos o fallback web da v0.7.7.2 assumir.
        if (!manifest.has("pages")) return false;

        JSONArray arr = manifest.optJSONArray("pages");
        if (arr == null || arr.length() == 0) return false;

        List<PdfMeta> metas = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            PdfMeta m = new PdfMeta();
            m.page = o.optInt("page", 0);
            m.filename = sanitizeFilename(o.optString("filename", ""));
            m.size = o.optLong("size", 0L);
            // O e-mail autorizado do Valor entrega as três primeiras páginas.
            if (m.page >= 1 && m.page <= 3 && !m.filename.isEmpty()) metas.add(m);
        }
        Collections.sort(metas, Comparator.comparingInt(m -> m.page));

        PdfMeta coverMeta = null;
        for (PdfMeta m : metas) if (m.page == 1) { coverMeta = m; break; }
        if (coverMeta == null) return false;

        entry.candidates.clear();
        CandidatePage coverCandidate = null;
        int downloaded = 0;
        int savedToFiles = 0;
        int storedInApp = 0;
        int renderedInApp = 0;

        // Página 1 vem primeiro pela ordenação. Ela precisa existir para o fluxo Gmail
        // ser considerado válido; páginas 2/3 podem aparecer como indisponíveis sem
        // derrubar a capa que já foi confirmada.
        for (PdfMeta m : metas) {
            CandidatePage candidate = new CandidatePage();
            candidate.pageNumber = m.page;
            candidate.score = 100;
            candidate.sourceFilename = m.filename;
            candidate.recognizedText = "VALOR ECONÔMICO — PDF recebido por e-mail — Página " + m.page;

            try {
                byte[] bytes = fetchPdfBytes(base, date, m.page);
                downloaded++;

                File appPdf = writeAppPdf(context, date, m.filename, bytes);
                candidate.sourcePdfFile = appPdf;
                storedInApp++;

                if (saveToDownloads(context, m.filename, bytes)) savedToFiles++;

                CandidatePage rendered = renderPdfCandidate(context, appPdf, date, m.page, m.filename);
                candidate.imageFile = rendered.imageFile;
                candidate.available = true;
                candidate.errorMessage = "";
                renderedInApp++;

                if (m.page == 1) coverCandidate = candidate;
            } catch (Exception ex) {
                candidate.available = false;
                candidate.errorMessage = ex.getMessage() == null ? "não foi possível abrir este PDF" : ex.getMessage();
                if (m.page == 1) {
                    entry.candidates.clear();
                    throw ex;
                }
            }

            entry.candidates.add(candidate);
        }

        if (coverCandidate == null || !coverCandidate.available || coverCandidate.imageFile == null) {
            entry.candidates.clear();
            return false;
        }

        applyPrimaryCover(entry, coverCandidate);

        String downloads = savedToFiles > 0
                ? " • " + savedToFiles + "/" + metas.size() + " também em Arquivos/Downloads"
                : " • cópia em Downloads indisponível";
        entry.status = "Gmail • " + storedInApp + "/" + metas.size() + " PDF(s) do Valor no aplicativo"
                + " • " + renderedInApp + " página(s) em REVISAR CAPA"
                + " • Página 1 selecionada" + downloads;
        return true;
    }

    private static byte[] fetchPdfBytes(String base, String date, int page) throws Exception {
        JSONObject root = requestJson(buildEndpoint(base, "valor_pdf", date, page));
        if (!root.optBoolean("ok", false)) {
            throw new Exception(root.optString("error", "PDF do Valor não disponível"));
        }
        String b64 = root.optString("dataBase64", "");
        if (b64.isEmpty()) throw new Exception("Anexo PDF vazio");
        byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F') {
            throw new Exception("Anexo recebido não é um PDF válido");
        }
        return bytes;
    }

    private static String buildEndpoint(String base, String action, String date, int page) throws Exception {
        String sep = base.contains("?") ? "&" : "?";
        StringBuilder sb = new StringBuilder(base)
                .append(sep).append("key=").append(URLEncoder.encode(ACCESS_KEY, "UTF-8"))
                .append("&action=").append(URLEncoder.encode(action, "UTF-8"))
                .append("&date=").append(URLEncoder.encode(date, "UTF-8"));
        if (page > 0) sb.append("&page=").append(page);
        return sb.toString();
    }

    private static JSONObject requestJson(String endpoint) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(90000); // PDFs de 3–6 MB chegam em base64 pela ponte.
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "PrincipaisCapas/0.7.7.5 Android");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new Exception("Ponte Gmail HTTP " + code);
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        } finally {
            c.disconnect();
        }
        String body = sb.toString().trim();
        if (!body.startsWith("{")) throw new Exception("Resposta inválida da ponte Gmail para o Valor");
        return new JSONObject(body);
    }

    /** Mantém o PDF original dentro do armazenamento privado do aplicativo. */
    private static File writeAppPdf(Context context, String date, String filename, byte[] bytes) throws Exception {
        File dir = new File(context.getFilesDir(), "valor-pdfs/" + date);
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Não foi possível criar a pasta interna do Valor");
        File out = new File(dir, sanitizeFilename(filename));
        try (FileOutputStream fos = new FileOutputStream(out, false)) {
            fos.write(bytes);
            fos.flush();
        }
        return out;
    }

    /** Salva/atualiza o anexo em Downloads/Principais Capas/Valor Economico. */
    private static boolean saveToDownloads(Context context, String filename, byte[] bytes) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = context.getContentResolver();
                Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                String relative = Environment.DIRECTORY_DOWNLOADS + "/Principais Capas/Valor Economico/";
                Uri target = null;

                String[] projection = { MediaStore.MediaColumns._ID };
                String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
                String[] args = { filename, relative };
                try (Cursor cursor = resolver.query(collection, projection, selection, args, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        long id = cursor.getLong(0);
                        target = ContentUris.withAppendedId(collection, id);
                    }
                }

                boolean newlyCreated = false;
                if (target == null) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, relative);
                    values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    target = resolver.insert(collection, values);
                    newlyCreated = true;
                }
                if (target == null) return false;

                try (OutputStream out = resolver.openOutputStream(target, "w")) {
                    if (out == null) return false;
                    out.write(bytes);
                    out.flush();
                }
                if (newlyCreated) {
                    ContentValues ready = new ContentValues();
                    ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    resolver.update(target, ready, null, null);
                }
                return true;
            }

            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Principais Capas/Valor Economico");
            if (!dir.exists() && !dir.mkdirs()) return false;
            File out = new File(dir, filename);
            try (FileOutputStream fos = new FileOutputStream(out, false)) {
                fos.write(bytes);
                fos.flush();
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Renderiza a primeira página de cada anexo PDF para a revisão visual já existente no app. */
    private static CandidatePage renderPdfCandidate(Context context, File pdf, String date, int pageNumber, String filename) throws Exception {
        File image = new File(context.getCacheDir(), "valor-email-" + date + "-p" + pageNumber + ".jpg");
        Bitmap full = null;
        int width;
        int height;

        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(pfd)) {
            if (renderer.getPageCount() <= 0) throw new Exception("PDF Página " + pageNumber + " do Valor sem páginas");
            try (PdfRenderer.Page page = renderer.openPage(0)) {
                width = RENDER_WIDTH;
                height = Math.max(1, Math.round(width * (page.getHeight() / (float) page.getWidth())));
                full = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                full.eraseColor(0xFFFFFFFF);
                page.render(full, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            }
        }

        try (FileOutputStream fos = new FileOutputStream(image, false)) {
            if (full == null || !full.compress(Bitmap.CompressFormat.JPEG, 97, fos)) {
                throw new Exception("Não foi possível preparar a Página " + pageNumber + " do Valor");
            }
        } finally {
            if (full != null && !full.isRecycled()) full.recycle();
        }

        CandidatePage candidate = new CandidatePage();
        candidate.pageNumber = pageNumber;
        candidate.score = 100;
        candidate.available = true;
        candidate.imageFile = image;
        candidate.sourcePdfFile = pdf;
        candidate.sourceFilename = filename;
        candidate.recognizedText = "VALOR ECONÔMICO — PDF recebido por e-mail — Página " + pageNumber;
        return candidate;
    }

    /** Mantém Página 1 como capa automática, mas preserva todas as candidatas 1/2/3. */
    private static void applyPrimaryCover(CoverEntry entry, CandidatePage coverCandidate) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(coverCandidate.imageFile.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new Exception("Prévia da Página 1 do Valor inválida");

        BitmapFactory.Options opts = new BitmapFactory.Options();
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= PREVIEW_WIDTH) sample *= 2;
        opts.inSampleSize = Math.max(1, sample);
        Bitmap preview = BitmapFactory.decodeFile(coverCandidate.imageFile.getAbsolutePath(), opts);
        if (preview == null) throw new Exception("Não foi possível abrir a Página 1 do Valor no aplicativo");

        entry.coverBitmap = preview;
        entry.originalImagePath = coverCandidate.imageFile.getAbsolutePath();
        entry.originalWidth = bounds.outWidth;
        entry.originalHeight = bounds.outHeight;
        entry.detectedPage = 1;
        entry.selected = true;
    }

    private static String sanitizeFilename(String value) {
        if (value == null) return "";
        String s = value.replace('\\', '_').replace('/', '_').trim();
        return s.replaceAll("[^A-Za-z0-9._ -]", "_");
    }
}
