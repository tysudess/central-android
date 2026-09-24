package br.com.editorpdf;

import br.com.centralmidia.android.ui.MobileScaffold;
import br.com.centralmidia.android.ui.PdfEditorActivity;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int PAGE_WIDTH_PT = 595;
    private static final int MARGIN_PT = 2;
    private static final String PREFS = "editor_pdf_prefs";
    private static final String PREF_COVER_URI = "cover_uri";

    private final ArrayList<PageItem> pages = new ArrayList<>();
    private PageAdapter adapter;
    private CropImageView preview;
    private TextView status;
    private CheckBox includeCover;
    private Spinner qualitySpinner;
    private int selectedIndex = -1;
    private Uri customCoverUri = null;
    private Bitmap currentPreviewBitmap = null;

    private ActivityResultLauncher<Intent> imagePicker;
    private ActivityResultLauncher<Intent> pdfPicker;
    private ActivityResultLauncher<Intent> coverPicker;
    private ActivityResultLauncher<Intent> pdfCreator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(246, 249, 253));
        getWindow().setNavigationBarColor(Color.WHITE);
        registerLaunchers();
        loadCoverPreference();
        buildUi();
        MobileScaffold.attachModule(this, PdfEditorActivity.class);
    }

    private void registerLaunchers() {
        imagePicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                consumeImageResult(result.getData());
            }
        });
        pdfPicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                consumePdfResult(result.getData());
            }
        });
        coverPicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                Uri uri = result.getData().getData();
                takeReadPermission(uri);
                customCoverUri = uri;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_COVER_URI, uri.toString()).apply();
                toast("Capa padrão personalizada salva.");
            }
        });
        pdfCreator = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                Uri out = result.getData().getData();
                exportPdf(out);
            }
        });
    }

    private void loadCoverPreference() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String uri = prefs.getString(PREF_COVER_URI, null);
        if (uri != null) customCoverUri = Uri.parse(uri);
    }

    private void buildUi() {
        final int BG = 0xFFF6F9FD;
        final int PANEL = 0xFFFFFFFF;
        final int CARD = 0xFFFAFCFF;
        final int BORDER = 0xFFD8E4F3;
        final int MUTED = 0xFF5B749E;
        final int PRIMARY = 0xFF147EF6;

        // A tela passa a ser rolavel para a pre-visualizacao poder ocupar uma area
        // realmente grande, sem espremer Páginas e Configuracoes no mesmo viewport.
        ScrollView screenScroll = new ScrollView(this);
        screenScroll.setFillViewport(true);
        screenScroll.setClipToPadding(false);
        screenScroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(14));
        root.setBackgroundColor(BG);
        screenScroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        setContentView(screenScroll);

        // Cabeçalho — mesma identidade visual do Windows, adaptada ao celular.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(8), dp(10), dp(10));
        header.setBackground(rounded(PANEL, BORDER, 14));

        ImageView logo = new ImageView(this);
        try (InputStream in = getAssets().open("editor_pdf_icon.png")) {
            logo.setImageBitmap(BitmapFactory.decodeStream(in));
        } catch (Exception ignored) {}
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(52), dp(52));
        logoLp.rightMargin = dp(10);
        header.addView(logo, logoLp);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Editor de PDF", 22, 0xFF0A2B63);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        TextView sub = text("Edite, organize e gere PDFs", 12, MUTED);
        titleBox.addView(title);
        titleBox.addView(sub);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header);

        // Ações principais.
        HorizontalScrollView quickScroll = new HorizontalScrollView(this);
        quickScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setPadding(0, dp(10), 0, dp(8));
        quickScroll.addView(quick);
        root.addView(quickScroll);

        addModernButton(quick, "+  Imagem", PRIMARY, v -> pickImages());
        addModernButton(quick, "▣  PDF", CARD, v -> pickPdfs());
        addModernButton(quick, "▤  Trocar capa", CARD, v -> pickCover());

        // Pré-visualização em card central.
        LinearLayout previewCard = new LinearLayout(this);
        previewCard.setOrientation(LinearLayout.VERTICAL);
        previewCard.setPadding(dp(10), dp(10), dp(10), dp(10));
        previewCard.setBackground(rounded(PANEL, BORDER, 14));

        LinearLayout previewHeader = new LinearLayout(this);
        previewHeader.setOrientation(LinearLayout.HORIZONTAL);
        previewHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView previewTitle = text("PRÉ-VISUALIZAÇÃO", 12, 0xFF0A2B63);
        previewTitle.setTypeface(previewTitle.getTypeface(), android.graphics.Typeface.BOLD);
        previewHeader.addView(previewTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        status = text("Pronto", 11, MUTED);
        // O status precisa permanecer em uma unica linha. Antes, a mensagem longa
        // do modo de recorte aumentava a altura do cabecalho e empurrava a
        // imagem/moldura para fora da area visivel.
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        statusLp.leftMargin = dp(8);
        previewHeader.addView(status, statusLp);
        previewCard.addView(previewHeader);

        preview = new CropImageView(this);
        preview.setAdjustViewBounds(false);
        preview.setBackground(rounded(0xFFF8FBFF, 0xFFD8E4F3, 10));
        preview.setPadding(dp(6), dp(6), dp(6), dp(6));
        preview.setOnCropSelectedListener(crop -> {
            setStatus("Modo de corte ativo");
        });
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        previewLp.topMargin = dp(8);
        previewLp.bottomMargin = dp(8);
        previewCard.addView(preview, previewLp);

        // Cerca de 58% da altura util da tela, respeitando limites para celulares
        // menores e maiores. Como a tela e rolavel, os demais controles ficam abaixo.
        int screenHeightDp = getResources().getConfiguration().screenHeightDp;
        int previewHeightDp = Math.max(380, Math.min(520, Math.round(screenHeightDp * 0.58f)));
        root.addView(previewCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(previewHeightDp)));

        // Barra de edição horizontal: botões grandes e fáceis de tocar.
        HorizontalScrollView toolsScroll = new HorizontalScrollView(this);
        toolsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(0, dp(8), 0, dp(8));
        toolsScroll.addView(tools);
        root.addView(toolsScroll);

        addModernButton(tools, "⌗  Selecionar corte", PRIMARY, v -> startCrop());
        addModernButton(tools, "✓  Aplicar", CARD, v -> applyCrop());
        addModernButton(tools, "Cancelar", CARD, v -> cancelCrop());
        addModernButton(tools, "↻  Girar", CARD, v -> rotateSelected());
        addModernButton(tools, "Remover recorte", CARD, v -> clearCrop());
        addModernButton(tools, "↑", CARD, v -> moveSelected(-1));
        addModernButton(tools, "↓", CARD, v -> moveSelected(1));
        addModernButton(tools, "Excluir", 0xFFFFF4F8, v -> deleteSelected());

        // Faixa de páginas.
        LinearLayout pagesCard = new LinearLayout(this);
        pagesCard.setOrientation(LinearLayout.VERTICAL);
        pagesCard.setPadding(dp(8), dp(7), dp(8), dp(7));
        pagesCard.setBackground(rounded(PANEL, BORDER, 12));
        TextView pagesTitle = text("PÁGINAS  •  toque para selecionar", 11, MUTED);
        pagesCard.addView(pagesTitle);
        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        adapter = new PageAdapter();
        list.setAdapter(adapter);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(96));
        listLp.topMargin = dp(4);
        pagesCard.addView(list, listLp);
        root.addView(pagesCard);

        // Configurações rápidas.
        LinearLayout settingsCard = new LinearLayout(this);
        settingsCard.setOrientation(LinearLayout.VERTICAL);
        settingsCard.setPadding(dp(10), dp(8), dp(10), dp(8));
        settingsCard.setBackground(rounded(CARD, BORDER, 12));
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        settingsLp.topMargin = dp(8);
        root.addView(settingsCard, settingsLp);

        LinearLayout coverRow = new LinearLayout(this);
        coverRow.setOrientation(LinearLayout.HORIZONTAL);
        coverRow.setGravity(Gravity.CENTER_VERTICAL);
        includeCover = new CheckBox(this);
        includeCover.setText("Incluir capa padrão");
        includeCover.setTextColor(0xFF0A2B63);
        includeCover.setChecked(true);
        coverRow.addView(includeCover, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button restore = modernButton("Restaurar", CARD);
        restore.setOnClickListener(v -> restoreCover());
        coverRow.addView(restore);
        settingsCard.addView(coverRow);

        LinearLayout qualityRow = new LinearLayout(this);
        qualityRow.setOrientation(LinearLayout.HORIZONTAL);
        qualityRow.setGravity(Gravity.CENTER_VERTICAL);
        qualityRow.setPadding(0, dp(4), 0, 0);
        TextView q = text("Qualidade", 12, MUTED);
        qualityRow.addView(q);
        qualitySpinner = new Spinner(this);
        ArrayAdapter<String> qa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Alta (300 dpi)", "Média (220 dpi)", "Compacta (160 dpi)"});
        qualitySpinner.setAdapter(qa);
        LinearLayout.LayoutParams qLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        qLp.leftMargin = dp(10);
        qualityRow.addView(qualitySpinner, qLp);
        settingsCard.addView(qualityRow);

        Button generate = modernButton("GERAR PDF", PRIMARY);
        generate.setTextSize(15);
        generate.setTypeface(generate.getTypeface(), android.graphics.Typeface.BOLD);
        generate.setOnClickListener(v -> createPdf());
        LinearLayout.LayoutParams genLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        genLp.topMargin = dp(8);
        settingsCard.addView(generate, genLp);
    }

    private void pickImages() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        imagePicker.launch(i);
    }

    private void pickPdfs() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        pdfPicker.launch(i);
    }

    private void pickCover() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        coverPicker.launch(i);
    }

    private void consumeImageResult(Intent data) {
        List<Uri> uris = resultUris(data);
        for (Uri uri : uris) {
            takeReadPermission(uri);
            pages.add(PageItem.image(uri, displayName(uri)));
        }
        afterImport();
    }

    private void consumePdfResult(Intent data) {
        List<Uri> uris = resultUris(data);
        int added = 0;
        for (Uri uri : uris) {
            takeReadPermission(uri);
            try (ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(uri, "r");
                 PdfRenderer renderer = fd == null ? null : new PdfRenderer(fd)) {
                if (renderer == null) continue;
                String name = displayName(uri);
                for (int p = 0; p < renderer.getPageCount(); p++) {
                    pages.add(PageItem.pdf(uri, p, name));
                    added++;
                }
            } catch (Exception e) {
                toast("Não foi possível abrir um PDF: " + e.getMessage());
            }
        }
        afterImport();
        if (added > 0) setStatus(added + " página(s) de PDF adicionada(s).");
    }

    private void afterImport() {
        adapter.notifyDataSetChanged();
        if (selectedIndex < 0 && !pages.isEmpty()) select(0);
        else if (!pages.isEmpty()) select(pages.size() - 1);
        setStatus(pages.size() + " página(s) no projeto.");
    }

    private List<Uri> resultUris(Intent data) {
        ArrayList<Uri> out = new ArrayList<>();
        if (data.getData() != null) out.add(data.getData());
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri u = clip.getItemAt(i).getUri();
                if (u != null && !out.contains(u)) out.add(u);
            }
        }
        return out;
    }

    private void takeReadPermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    private void select(int index) {
        if (index < 0 || index >= pages.size()) return;
        int old = selectedIndex;
        selectedIndex = index;
        if (old >= 0) adapter.notifyItemChanged(old);
        adapter.notifyItemChanged(index);
        showSelected(false);
    }

    private PageItem selectedItem() {
        if (selectedIndex < 0 || selectedIndex >= pages.size()) return null;
        return pages.get(selectedIndex);
    }

    private void moveSelected(int direction) {
        if (selectedItem() == null) return;
        int target = selectedIndex + direction;
        if (target < 0 || target >= pages.size()) return;
        Collections.swap(pages, selectedIndex, target);
        int old = selectedIndex;
        selectedIndex = target;
        adapter.notifyItemMoved(old, target);
        adapter.notifyItemChanged(old);
        adapter.notifyItemChanged(target);
    }

    private void deleteSelected() {
        if (selectedItem() == null) return;
        int old = selectedIndex;
        pages.remove(old);
        adapter.notifyItemRemoved(old);
        if (pages.isEmpty()) {
            selectedIndex = -1;
            preview.setImageDrawable(null);
            recycleCurrentPreview();
        } else {
            selectedIndex = Math.min(old, pages.size() - 1);
            adapter.notifyItemChanged(selectedIndex);
            showSelected(false);
        }
        setStatus(pages.size() + " página(s) no projeto.");
    }

    private void rotateSelected() {
        PageItem item = selectedItem();
        if (item == null) return;
        item.rotation = (item.rotation + 90) % 360;
        if (item.crop != null) {
            item.crop = null;
            toast("O recorte foi removido para manter a rotação correta.");
        }
        item.thumbnail = null;
        showSelected(false);
        adapter.notifyItemChanged(selectedIndex);
    }

    private void startCrop() {
        PageItem item = selectedItem();
        if (item == null) return;
        try {
            // Mostra a página inteira, sem aplicar eventual recorte antigo,
            // para que a nova área possa ser escolhida com precisão.
            Bitmap source = renderPage(item, 1500, false);
            setPreviewBitmap(source);
            // Aguarda a ImageView concluir layout/matriz antes de criar a moldura.
            // Isso garante que o retangulo seja calculado sobre a imagem visivel.
            preview.post(() -> {
                preview.setCropMode(true);
                preview.invalidate();
                preview.requestFocus();
            });
            setStatus("Modo de corte ativo");
            toast("Ajuste a moldura na imagem e toque em Aplicar quando estiver correto.");
        } catch (Exception e) {
            toast("Erro ao preparar recorte: " + e.getMessage());
        }
    }

    private void applyCrop() {
        PageItem item = selectedItem();
        if (item == null) return;

        RectF crop = preview.getNormalizedSelection();
        if (crop == null) {
            toast("Primeiro selecione uma área válida na imagem.");
            setStatus("Ajuste a moldura de seleção e depois toque em Aplicar corte.");
            return;
        }

        item.crop = new RectF(crop);
        item.thumbnail = null;
        preview.setCropMode(false);
        showSelected(false);
        adapter.notifyItemChanged(selectedIndex);
        setStatus("Recorte aplicado. O arquivo original permanece intacto.");
    }

    private void cancelCrop() {
        if (!preview.isCropMode()) return;
        preview.setCropMode(false);
        showSelected(false);
        setStatus("Seleção de corte cancelada. Nenhuma alteração foi aplicada.");
    }

    private void clearCrop() {
        PageItem item = selectedItem();
        if (item == null) return;
        item.crop = null;
        item.thumbnail = null;
        preview.setCropMode(false);
        showSelected(false);
        adapter.notifyItemChanged(selectedIndex);
        setStatus("Recorte removido.");
    }

    private void showSelected(boolean originalForCrop) {
        PageItem item = selectedItem();
        if (item == null) return;
        try {
            Bitmap b = renderPage(item, 1500, !originalForCrop);
            setPreviewBitmap(b);
            preview.setCropMode(false);
            setStatus(String.format(Locale.getDefault(), "Página %d de %d", selectedIndex + 1, pages.size()));
        } catch (Exception e) {
            toast("Erro na prévia: " + e.getMessage());
        }
    }

    private void setPreviewBitmap(Bitmap b) {
        recycleCurrentPreview();
        currentPreviewBitmap = b;
        preview.setImageBitmap(b);
    }

    private void recycleCurrentPreview() {
        if (currentPreviewBitmap != null && !currentPreviewBitmap.isRecycled()) {
            currentPreviewBitmap.recycle();
        }
        currentPreviewBitmap = null;
    }

    private void createPdf() {
        if (pages.isEmpty() && !includeCover.isChecked()) {
            toast("Adicione pelo menos uma página ou ative a capa.");
            return;
        }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/pdf");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE, "EditorPDF.pdf");
        pdfCreator.launch(i);
    }

    private void exportPdf(Uri outputUri) {
        setStatus("Gerando PDF...");
        new Thread(() -> {
            PdfDocument document = new PdfDocument();
            try {
                if (includeCover.isChecked()) addCoverPage(document);
                int targetWidth = exportPixelWidth();
                for (int i = 0; i < pages.size(); i++) {
                    PageItem item = pages.get(i);
                    Bitmap bitmap = renderPage(item, targetWidth, true);
                    addContentPage(document, bitmap);
                    bitmap.recycle();
                    int done = i + 1;
                    runOnUiThread(() -> setStatus("Gerando: " + done + "/" + pages.size()));
                }
                try (OutputStream os = getContentResolver().openOutputStream(outputUri, "w")) {
                    if (os == null) throw new IOException("Não foi possível abrir o arquivo de saída.");
                    document.writeTo(os);
                }
                runOnUiThread(() -> {
                    setStatus("PDF gerado com sucesso.");
                    toast("PDF salvo.");
                    openGeneratedPdf(outputUri);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setStatus("Erro ao gerar PDF.");
                    toast("Erro: " + e.getMessage());
                });
            } finally {
                document.close();
            }
        }).start();
    }

    private int exportPixelWidth() {
        int position = qualitySpinner.getSelectedItemPosition();
        int dpi = position == 1 ? 220 : (position == 2 ? 160 : 300);
        return Math.round(PAGE_WIDTH_PT / 72f * dpi);
    }

    private void addCoverPage(PdfDocument document) throws Exception {
        Bitmap cover = loadCoverBitmap(exportPixelWidth());
        if (cover == null) throw new IOException("Capa padrão não encontrada.");
        int heightPt = Math.max(1, Math.round(PAGE_WIDTH_PT * (cover.getHeight() / (float) cover.getWidth())));
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, heightPt, document.getPages().size() + 1).create();
        PdfDocument.Page page = document.startPage(info);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        page.getCanvas().drawBitmap(cover, null, new RectF(0, 0, PAGE_WIDTH_PT, heightPt), paint);
        document.finishPage(page);
        cover.recycle();
    }

    private void addContentPage(PdfDocument document, Bitmap bitmap) {
        int contentWidth = PAGE_WIDTH_PT - 2 * MARGIN_PT;
        int contentHeight = Math.max(1, Math.round(contentWidth * bitmap.getHeight() / (float) bitmap.getWidth()));
        int pageHeight = contentHeight + 2 * MARGIN_PT;
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, pageHeight, document.getPages().size() + 1).create();
        PdfDocument.Page page = document.startPage(info);
        Canvas c = page.getCanvas();
        c.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        RectF dst = new RectF(MARGIN_PT, MARGIN_PT, MARGIN_PT + contentWidth, MARGIN_PT + contentHeight);
        c.drawBitmap(bitmap, null, dst, paint);
        document.finishPage(page);
    }

    private Bitmap loadCoverBitmap(int targetWidth) throws Exception {
        if (customCoverUri != null) {
            try {
                return decodeImage(customCoverUri, targetWidth);
            } catch (Exception ignored) {
                customCoverUri = null;
            }
        }
        try (InputStream is = getAssets().open("capa_padrao.png")) {
            Bitmap full = BitmapFactory.decodeStream(is);
            if (full == null) return null;
            if (full.getWidth() <= targetWidth) return full;
            int h = Math.round(full.getHeight() * targetWidth / (float) full.getWidth());
            Bitmap scaled = Bitmap.createScaledBitmap(full, targetWidth, h, true);
            if (scaled != full) full.recycle();
            return scaled;
        }
    }

    private Bitmap renderPage(PageItem item, int targetWidth, boolean applyCrop) throws Exception {
        Bitmap base = item.pdf ? renderPdfPage(item.uri, item.pageIndex, targetWidth) : decodeImage(item.uri, targetWidth);
        Bitmap rotated = rotateBitmap(base, item.rotation);
        if (rotated != base) base.recycle();
        if (applyCrop && item.crop != null) {
            Bitmap cropped = cropBitmap(rotated, item.crop);
            if (cropped != rotated) rotated.recycle();
            return cropped;
        }
        return rotated;
    }

    private Bitmap decodeImage(Uri uri, int targetWidth) throws Exception {
        ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
        return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            int w = info.getSize().getWidth();
            int h = info.getSize().getHeight();
            if (w > targetWidth && targetWidth > 0) {
                decoder.setTargetSize(targetWidth, Math.max(1, Math.round(h * targetWidth / (float) w)));
            }
        });
    }

    private Bitmap renderPdfPage(Uri uri, int pageIndex, int targetWidth) throws Exception {
        ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(uri, "r");
        if (fd == null) throw new FileNotFoundException("PDF não encontrado.");
        try (ParcelFileDescriptor safeFd = fd; PdfRenderer renderer = new PdfRenderer(safeFd); PdfRenderer.Page page = renderer.openPage(pageIndex)) {
            float scale = targetWidth / (float) page.getWidth();
            int height = Math.max(1, Math.round(page.getHeight() * scale));
            Bitmap bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
            return bitmap;
        }
    }

    private Bitmap rotateBitmap(Bitmap source, int degrees) {
        if (degrees % 360 == 0) return source;
        Matrix m = new Matrix();
        m.postRotate(degrees);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), m, true);
    }

    private Bitmap cropBitmap(Bitmap source, RectF n) {
        int left = clamp(Math.round(n.left * source.getWidth()), 0, source.getWidth() - 1);
        int top = clamp(Math.round(n.top * source.getHeight()), 0, source.getHeight() - 1);
        int right = clamp(Math.round(n.right * source.getWidth()), left + 1, source.getWidth());
        int bottom = clamp(Math.round(n.bottom * source.getHeight()), top + 1, source.getHeight());
        if (left == 0 && top == 0 && right == source.getWidth() && bottom == source.getHeight()) return source;
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top);
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }

    private void restoreCover() {
        customCoverUri = null;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(PREF_COVER_URI).apply();
        toast("Capa original restaurada.");
    }

    private void openGeneratedPdf(Uri uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/pdf");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private String displayName(Uri uri) {
        String name = "arquivo";
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignored) {
        }
        return name;
    }

    private TextView text(String value, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private GradientDrawable rounded(int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), strokeColor);
        return d;
    }

    private Button modernButton(String label, int fillColor) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setTextColor(fillColor == 0xFF147EF6 ? Color.WHITE : 0xFF0A2B63);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setBackground(rounded(fillColor, fillColor == 0xFF147EF6 ? 0xFF0F66CB : 0xFFD8E4F3, 9));
        return b;
    }

    private void addModernButton(LinearLayout parent, String label, int fillColor, View.OnClickListener listener) {
        Button b = modernButton(label, fillColor);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(46));
        lp.rightMargin = dp(6);
        parent.addView(b, lp);
    }

    private Button button(String label) {
        return modernButton(label, 0xFFFFFFFF);
    }

    private void addButton(LinearLayout parent, String label, View.OnClickListener listener) {
        addModernButton(parent, label, 0xFFFFFFFF, listener);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setStatus(String s) {
        if (status != null) status.setText(s);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        recycleCurrentPreview();
        for (PageItem p : pages) {
            if (p.thumbnail != null && !p.thumbnail.isRecycled()) p.thumbnail.recycle();
        }
        super.onDestroy();
    }

    private class PageAdapter extends RecyclerView.Adapter<PageHolder> {
        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            LinearLayout box = new LinearLayout(MainActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setPadding(dp(5), dp(5), dp(5), dp(5));
            android.widget.ImageView img = new android.widget.ImageView(MainActivity.this);
            img.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            box.addView(img, new LinearLayout.LayoutParams(dp(72), dp(72)));
            TextView label = text("", 10, 0xFF0A2B63);
            label.setGravity(Gravity.CENTER);
            box.addView(label, new LinearLayout.LayoutParams(dp(92), dp(28)));
            return new PageHolder(box, img, label);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            PageItem item = pages.get(position);
            holder.label.setText((position + 1) + (item.pdf ? " • PDF" : " • IMG"));
            holder.itemView.setBackground(rounded(position == selectedIndex ? 0xFFE5F1FF : 0xFFFFFFFF, position == selectedIndex ? 0xFF147EF6 : 0xFFD8E4F3, 9));
            if (item.thumbnail == null || item.thumbnail.isRecycled()) {
                try {
                    item.thumbnail = renderPage(item, 260, true);
                } catch (Exception ignored) {
                    item.thumbnail = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);
                }
            }
            holder.image.setImageBitmap(item.thumbnail);
            holder.itemView.setOnClickListener(v -> select(holder.getBindingAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }
    }

    private static class PageHolder extends RecyclerView.ViewHolder {
        final android.widget.ImageView image;
        final TextView label;
        PageHolder(@NonNull View itemView, android.widget.ImageView image, TextView label) {
            super(itemView);
            this.image = image;
            this.label = label;
        }
    }

    private static class PageItem {
        Uri uri;
        boolean pdf;
        int pageIndex;
        String name;
        int rotation = 0;
        RectF crop = null;
        Bitmap thumbnail = null;

        static PageItem image(Uri uri, String name) {
            PageItem p = new PageItem();
            p.uri = uri;
            p.pdf = false;
            p.pageIndex = 0;
            p.name = name;
            return p;
        }

        static PageItem pdf(Uri uri, int page, String name) {
            PageItem p = new PageItem();
            p.uri = uri;
            p.pdf = true;
            p.pageIndex = page;
            p.name = name;
            return p;
        }
    }
}
