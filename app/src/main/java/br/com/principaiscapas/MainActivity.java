package br.com.principaiscapas;

import br.com.centralmidia.android.R;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newFixedThreadPool(8);
    private final List<CoverEntry> entries = new ArrayList<>();
    private LinearLayout listContainer;
    private ProgressBar progress;
    private Button refreshButton;
    private Button exportButton;
    private Button openPdfButton;
    private Button gmailButton;
    private Button dateButton;
    private TextView heroDateText;
    private TextView selectedCountText;
    private TextView lastUpdateText;
    private final Set<String> expandedCards = new HashSet<>();
    private Date selectedDate = new Date();

    private static final int REQUEST_MANUAL_COVER = 9101;
    private CoverEntry pendingManualEntry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF061321);
        getWindow().setNavigationBarColor(0xFF061321);
        // v0.7.5.9: o Android 15/16 usa edge-to-edge. Mantemos isso explícito
        // e aplicamos os insets do sistema na própria interface para que a barra
        // inferior nunca fique atrás dos botões/gestos de navegação do aparelho.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View content = buildUi();
        setContentView(content);
        ViewCompat.requestApplyInsets(content);
        loadSources();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        final int rootPadH = dp(8);
        final int rootPadTop = dp(6);
        final int rootPadBottom = dp(6);
        root.setPadding(rootPadH, rootPadTop, rootPadH, rootPadBottom);
        root.setBackgroundColor(0xFF071625);

        // Área segura do Android: status bar em cima e navigation/gesture bar embaixo.
        // Isso corrige aparelhos com 3 botões, navegação por gestos e diferentes
        // alturas de barras do sistema sem usar valores fixos por modelo.
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    rootPadH + bars.left,
                    rootPadTop + bars.top,
                    rootPadH + bars.right,
                    rootPadBottom + bars.bottom
            );
            return windowInsets;
        });

        // v0.7.5.8: topo extremamente compacto para liberar o máximo de área
        // vertical possível para os jornais. As ações principais foram movidas
        // para a barra inferior.
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(10), dp(5), dp(10), dp(5));
        toolbar.setBackground(gradientRounded(0xFF0C2F57, 0xFF071C31, 14));

        ImageView appIcon = new ImageView(this);
        appIcon.setImageResource(R.mipmap.ic_launcher);
        appIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        toolbar.addView(appIcon, new LinearLayout.LayoutParams(dp(34), dp(34)));

        TextView title = new TextView(this);
        title.setText("PRINCIPAIS CAPAS");
        title.setTextSize(17);
        title.setTextColor(Color.WHITE);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dp(9), 0, 0, 0);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        // Data em uma única faixa compacta.
        LinearLayout dateCard = new LinearLayout(this);
        dateCard.setOrientation(LinearLayout.HORIZONTAL);
        dateCard.setGravity(Gravity.CENTER_VERTICAL);
        dateCard.setPadding(dp(10), dp(3), dp(10), dp(3));
        dateCard.setBackground(rounded(0xFF0C2034, 12, 1, 0xFF29445F));
        LinearLayout.LayoutParams dateCardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        dateCardLp.setMargins(0, dp(6), 0, dp(4));

        TextView dateLabel = new TextView(this);
        dateLabel.setText("DATA");
        dateLabel.setTextColor(0xFFAFC1D5);
        dateLabel.setTextSize(10);
        dateLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        dateCard.addView(dateLabel, new LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout dateTextBox = new LinearLayout(this);
        dateTextBox.setOrientation(LinearLayout.VERTICAL);
        dateTextBox.setGravity(Gravity.CENTER_VERTICAL);

        dateButton = new Button(this);
        dateButton.setText(formatDisplayDate(selectedDate));
        dateButton.setTextColor(Color.WHITE);
        dateButton.setTextSize(20);
        dateButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        dateButton.setAllCaps(false);
        dateButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        dateButton.setPadding(0, 0, 0, 0);
        dateButton.setMinHeight(0);
        dateButton.setMinWidth(0);
        dateButton.setBackgroundColor(Color.TRANSPARENT);
        dateButton.setOnClickListener(v -> showDatePicker());
        dateTextBox.addView(dateButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

        lastUpdateText = new TextView(this);
        lastUpdateText.setText("Toque para alterar");
        lastUpdateText.setTextColor(0xFF8FA9C3);
        lastUpdateText.setTextSize(9);
        lastUpdateText.setGravity(Gravity.START);
        dateTextBox.addView(lastUpdateText);
        dateCard.addView(dateTextBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        root.addView(dateCard, dateCardLp);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setVisibility(View.GONE);
        progress.setIndeterminate(true);
        if (Build.VERSION.SDK_INT >= 21) {
            progress.setIndeterminateTintList(ColorStateList.valueOf(0xFF2D8CFF));
        }
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        progressLp.setMargins(0, 0, 0, dp(2));
        root.addView(progress, progressLp);

        // Cabeçalho da lista com seleção em um único botão, sem gastar outra linha.
        LinearLayout sectionHeader = new LinearLayout(this);
        sectionHeader.setOrientation(LinearLayout.HORIZONTAL);
        sectionHeader.setGravity(Gravity.CENTER_VERTICAL);
        sectionHeader.setPadding(dp(2), dp(2), dp(2), dp(3));

        TextView sectionTitle = new TextView(this);
        sectionTitle.setText("▤  JORNAIS");
        sectionTitle.setTextColor(0xFFB9CBE0);
        sectionTitle.setTextSize(12);
        sectionTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        sectionHeader.addView(sectionTitle);

        selectedCountText = new TextView(this);
        selectedCountText.setTextColor(0xFF53E879);
        selectedCountText.setTextSize(10);
        selectedCountText.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        countLp.setMargins(dp(8), 0, 0, 0);
        sectionHeader.addView(selectedCountText, countLp);

        Button selection = secondaryButton("SELEÇÃO");
        selection.setTextSize(9);
        selection.setOnClickListener(v -> showSelectionDialog());
        sectionHeader.addView(selection, new LinearLayout.LayoutParams(dp(78), dp(30)));
        root.addView(sectionHeader, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, 0, 0, dp(5));
        scroll.addView(listContainer);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // Barra inferior preservada e ampliada com ATUALIZAR e GERAR PDF.
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(dp(3), dp(3), dp(3), 0);
        bottom.setBackground(rounded(0xFF0A1D30, 14, 1, 0xFF203B56));

        refreshButton = bottomNavButton("↻\nATUALIZAR", false);
        refreshButton.setTextColor(0xFF72B5FF);
        refreshButton.setOnClickListener(v -> refreshAll());
        bottom.addView(refreshButton, new LinearLayout.LayoutParams(0, dp(58), 1));

        exportButton = bottomNavButton("▣\nGERAR PDF", false);
        exportButton.setTextColor(0xFF59E67F);
        exportButton.setOnClickListener(v -> exportPdf());
        bottom.addView(exportButton, new LinearLayout.LayoutParams(0, dp(58), 1));

        Button home = bottomNavButton("⌂\nINÍCIO", true);
        home.setEnabled(false);
        bottom.addView(home, new LinearLayout.LayoutParams(0, dp(58), 1));

        openPdfButton = bottomNavButton("▤\nPDFs", false);
        openPdfButton.setOnClickListener(v -> openGeneratedPdf());
        bottom.addView(openPdfButton, new LinearLayout.LayoutParams(0, dp(58), 1));

        gmailButton = bottomNavButton("⚙\nCONFIG.", false);
        gmailButton.setOnClickListener(v -> showGmailBridgeDialog());
        bottom.addView(gmailButton, new LinearLayout.LayoutParams(0, dp(58), 1));

        LinearLayout.LayoutParams bottomLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomLp.setMargins(0, dp(3), 0, 0);
        root.addView(bottom, bottomLp);
        return root;
    }

    private void showSelectionDialog() {
        final String[] options = new String[]{"Marcar todas", "Desmarcar todas"};
        new AlertDialog.Builder(this)
                .setTitle("Seleção de jornais")
                .setItems(options, (dialog, which) -> {
                    boolean value = which == 0;
                    for (CoverEntry e : entries) e.selected = value;
                    renderList();
                })
                .setNegativeButton("CANCELAR", null)
                .show();
    }

    private Button actionButton(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(rounded(color, 12, 1, lighten(color, 0.16f)));
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(0xFFE4EDF7);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setBackground(rounded(0xFF0C2034, 11, 1, 0xFF29445F));
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        return b;
    }

    private Button cardActionButton(String text, int fill, int stroke, int textColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(textColor);
        b.setTextSize(12);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setBackground(rounded(fill, 10, 1, stroke));
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        return b;
    }

    private Button bottomNavButton(String text, boolean active) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(10);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(active ? 0xFF72B5FF : 0xFFA8B9CB);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(3), dp(2), dp(3), dp(2));
        b.setBackground(active ? rounded(0xFF0D3A68, 11, 1, 0xFF1E75C9) : rounded(0x00000000, 11, 0, 0));
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        return b;
    }

    private GradientDrawable rounded(int fill, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private GradientDrawable gradientRounded(int top, int bottom, int radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), 0xFF2A4A67);
        return g;
    }

    private int lighten(int color, float factor) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        r = Math.min(255, (int) (r + (255 - r) * factor));
        g = Math.min(255, (int) (g + (255 - g) * factor));
        b = Math.min(255, (int) (b + (255 - b) * factor));
        return Color.rgb(r, g, b);
    }

    private void updateSelectedCount() {
        if (selectedCountText == null) return;
        int selected = 0;
        for (CoverEntry e : entries) if (e.selected) selected++;
        selectedCountText.setText(selected + " de " + entries.size() + " selecionados");
    }

    private void loadSources() {
        try {
            entries.clear();
            for (NewspaperSource source : NewspaperRepository.load(this)) {
                if (!source.enabled) continue;
                CoverEntry entry = new CoverEntry();
                entry.source = source;
                entries.add(entry);
            }
            if (!entries.isEmpty() && expandedCards.isEmpty()) {
                expandedCards.add(entries.get(0).source.name);
            }
            renderList();
        } catch (Exception e) {
            toast("Erro ao carregar configuração: " + e.getMessage());
        }
    }

    private void renderList() {
        listContainer.removeAllViews();
        updateSelectedCount();

        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Nenhum jornal cadastrado em newspapers.json.");
            empty.setTextSize(14);
            empty.setTextColor(0xFFA8B9CB);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(8), dp(28), dp(8), dp(28));
            listContainer.addView(empty);
            return;
        }

        for (int position = 0; position < entries.size(); position++) {
            CoverEntry entry = entries.get(position);
            boolean expanded = expandedCards.contains(entry.source.name);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(9), dp(10), dp(10));
            card.setBackground(rounded(expanded ? 0xFF0B2238 : 0xFF0A1D30, 14, 1, expanded ? 0xFF1F76C9 : 0xFF29445F));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(0, 0, 0, dp(8));

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            CheckBox check = new CheckBox(this);
            check.setChecked(entry.selected);
            check.setButtonTintList(new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{0xFF2D8CFF, 0xFF688198}));
            LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(dp(38), dp(42));
            header.addView(check, checkLp);

            TextView index = new TextView(this);
            index.setText(String.valueOf(position + 1));
            index.setTextColor(0xFFDCE8F4);
            index.setTextSize(14);
            index.setGravity(Gravity.CENTER);
            index.setBackground(rounded(0xFF10283E, 22, 1, 0xFF4C6882));
            LinearLayout.LayoutParams indexLp = new LinearLayout.LayoutParams(dp(42), dp(42));
            indexLp.setMargins(dp(2), 0, dp(9), 0);
            header.addView(index, indexLp);

            LinearLayout textBox = new LinearLayout(this);
            textBox.setOrientation(LinearLayout.VERTICAL);
            textBox.setPadding(0, dp(1), dp(6), 0);

            TextView name = new TextView(this);
            name.setText(entry.source.name);
            name.setTextSize(15);
            name.setTextColor(Color.WHITE);
            name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            textBox.addView(name);

            TextView status = new TextView(this);
            status.setText(entry.status == null ? "Aguardando" : entry.status);
            status.setTextColor(entry.status != null && entry.status.startsWith("Falha")
                    ? 0xFFFF6B6B
                    : (entry.coverBitmap != null ? 0xFF59E67F : 0xFF9CB0C4));
            status.setTextSize(11);
            status.setMaxLines(expanded ? 3 : 2);
            status.setEllipsize(TextUtils.TruncateAt.END);
            textBox.addView(status);
            header.addView(textBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            if (entry.coverBitmap != null) {
                ImageView thumb = new ImageView(this);
                thumb.setImageBitmap(entry.coverBitmap);
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumb.setBackground(rounded(0xFFF2F5F8, 5, 1, 0xFF536B82));
                LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(dp(40), dp(56));
                thumbLp.setMargins(dp(4), 0, dp(5), 0);
                header.addView(thumb, thumbLp);
            }

            Button toggle = cardActionButton(expanded ? "▲" : "▼", 0x00000000, 0x00000000, 0xFFB9CBE0);
            toggle.setTextSize(14);
            toggle.setOnClickListener(v -> {
                if (expandedCards.contains(entry.source.name)) expandedCards.remove(entry.source.name);
                else expandedCards.add(entry.source.name);
                renderList();
            });
            header.addView(toggle, new LinearLayout.LayoutParams(dp(42), dp(42)));
            card.addView(header);

            check.setOnCheckedChangeListener((buttonView, isChecked) -> {
                entry.selected = isChecked;
                updateSelectedCount();
            });

            if (expanded) {
                View divider = new View(this);
                divider.setBackgroundColor(0xFF25435E);
                LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                dividerLp.setMargins(0, dp(8), 0, dp(8));
                card.addView(divider, dividerLp);

                if (entry.coverBitmap != null) {
                    ImageView preview = new ImageView(this);
                    preview.setImageBitmap(entry.coverBitmap);
                    preview.setAdjustViewBounds(true);
                    preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    preview.setBackground(rounded(0xFFF5F7F9, 10, 0, 0));
                    LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300));
                    imageLp.setMargins(dp(2), 0, dp(2), dp(8));
                    card.addView(preview, imageLp);
                }

                if (!entry.candidates.isEmpty()) {
                    Button review = cardActionButton("◉  REVISAR CAPA", 0xFF0F56A8, 0xFF2D8CFF, Color.WHITE);
                    review.setOnClickListener(v -> showReview(entry));
                    LinearLayout.LayoutParams reviewLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
                    reviewLp.setMargins(0, 0, 0, dp(6));
                    card.addView(review, reviewLp);
                }

                Button manual = cardActionButton(
                        entry.manualOverride ? "▧  TROCAR CAPA MANUAL" : "▧  INSERIR CAPA MANUALMENTE",
                        0xFF0A1D30,
                        0xFF2D8CFF,
                        0xFF67B4FF);
                manual.setOnClickListener(v -> chooseManualCover(entry));
                LinearLayout.LayoutParams manualLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
                manualLp.setMargins(0, 0, 0, dp(6));
                card.addView(manual, manualLp);

                if (entry.manualOverride) {
                    Button automatic = cardActionButton(
                            "↻  VOLTAR PARA CAPA AUTOMÁTICA",
                            0xFF0A281D,
                            0xFF2E9A59,
                            0xFF59E67F);
                    automatic.setOnClickListener(v -> restoreAutomaticCover(entry));
                    LinearLayout.LayoutParams automaticLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
                    automaticLp.setMargins(0, 0, 0, dp(5));
                    card.addView(automatic, automaticLp);
                }

                TextView include = new TextView(this);
                include.setText(entry.selected
                        ? (entry.coverBitmap != null ? "✓  Este jornal será incluído no PDF" : "•  Selecionado, aguardando uma capa")
                        : "○  Este jornal está fora do PDF");
                include.setTextColor(entry.selected && entry.coverBitmap != null ? 0xFF59E67F : 0xFFA8B9CB);
                include.setTextSize(11);
                include.setGravity(Gravity.CENTER_VERTICAL);
                include.setPadding(dp(10), 0, dp(10), 0);
                include.setBackground(rounded(entry.selected && entry.coverBitmap != null ? 0xFF0A281D : 0xFF10283E, 9, 1,
                        entry.selected && entry.coverBitmap != null ? 0xFF2E7C4C : 0xFF29445F));
                card.addView(include, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

                check.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    entry.selected = isChecked;
                    updateSelectedCount();
                    include.setText(isChecked
                            ? (entry.coverBitmap != null ? "✓  Este jornal será incluído no PDF" : "•  Selecionado, aguardando uma capa")
                            : "○  Este jornal está fora do PDF");
                    include.setTextColor(isChecked && entry.coverBitmap != null ? 0xFF59E67F : 0xFFA8B9CB);
                    include.setBackground(rounded(isChecked && entry.coverBitmap != null ? 0xFF0A281D : 0xFF10283E, 9, 1,
                            isChecked && entry.coverBitmap != null ? 0xFF2E7C4C : 0xFF29445F));
                });
            }

            listContainer.addView(card, cardLp);
        }
    }

    private void refreshAll() {
        if (entries.isEmpty()) { toast("Primeiro vamos cadastrar os jornais."); return; }
        setBusy(true);
        for (CoverEntry e : entries) {
            deleteManualCacheFile(e);
            e.status = "Aguardando...";
            e.coverBitmap = null;
            e.originalImagePath = "";
            e.originalWidth = 0;
            e.originalHeight = 0;
            e.candidates.clear();
            clearManualState(e);
        }
        renderList();

        final long started = System.currentTimeMillis();
        final Date targetDate = new Date(selectedDate.getTime());
        final AtomicInteger branches = new AtomicInteger(2);
        final Runnable branchDone = () -> {
            if (branches.decrementAndGet() == 0) {
                runOnUiThread(() -> finishRefresh(started));
            }
        };

        // v0.7.5.3: Valor e Washington Post não ficam mais esperando o processamento
        // das páginas do Gmail. Eles voltam a iniciar imediatamente, em paralelo,
        // usando EXATAMENTE o mesmo FrontPageBrowserResolver da base v0.7.4.6/0.7.4.7.
        startWebOnlyBranch(targetDate, branchDone);
        startGmailBranch(targetDate, branchDone);
    }

    private void startWebOnlyBranch(Date targetDate, Runnable branchDone) {
        List<CoverEntry> webEntries = new ArrayList<>();
        for (CoverEntry e : entries) {
            if ("VALOR ECONÔMICO".equals(e.source.name)) {
                if (isWeekend(targetDate)) {
                    e.status = "Sem edição regular no fim de semana";
                } else {
                    e.status = "Buscando a capa exibida atualmente no link...";
                    webEntries.add(e);
                }
            } else if ("THE WASHINGTON POST".equals(e.source.name)) {
                e.status = "Buscando a capa exibida atualmente no link...";
                webEntries.add(e);
            }
        }
        renderList();

        if (webEntries.isEmpty()) {
            branchDone.run();
            return;
        }

        AtomicInteger pending = new AtomicInteger(webEntries.size());
        for (CoverEntry e : webEntries) {
            executor.execute(() -> {
                long itemStart = System.currentTimeMillis();
                try {
                    FrontPageBrowserResolver.resolve(this, e, targetDate);
                    long sec = Math.max(1, (System.currentTimeMillis() - itemStart) / 1000);
                    if (e.coverBitmap != null && !e.status.contains(" • " + sec + "s")) e.status += " • " + sec + "s";
                } catch (Exception ex) {
                    e.status = "Falha: " + ex.getMessage();
                    e.coverBitmap = null;
                    e.candidates.clear();
                }
                runOnUiThread(this::renderList);
                if (pending.decrementAndGet() == 0) branchDone.run();
            });
        }
    }

    private void startGmailBranch(Date targetDate, Runnable branchDone) {
        String feedUrl = ClippingFeedClient.getConfiguredUrl(this);
        if (feedUrl.isEmpty()) {
            for (CoverEntry e : entries) {
                if (!"VALOR ECONÔMICO".equals(e.source.name) && !"THE WASHINGTON POST".equals(e.source.name)) {
                    e.status = "Gmail automático não configurado • sem usar internet";
                }
            }
            renderList();
            branchDone.run();
            return;
        }

        for (CoverEntry e : entries) {
            if (!"VALOR ECONÔMICO".equals(e.source.name) && !"THE WASHINGTON POST".equals(e.source.name)) {
                e.status = "Localizando páginas no Gmail...";
            }
        }
        renderList();

        executor.execute(() -> {
            try {
                Map<String, List<String>> matters = ClippingFeedClient.fetchMatterUrls(this, targetDate);
                String bridgeVersion = ClippingFeedClient.getLastFeedVersion();

                runOnUiThread(() -> {
                    boolean oldBridge = bridgeVersion.isEmpty() ||
                            !(bridgeVersion.startsWith("0.7.5") || bridgeVersion.startsWith("0.7.6") || bridgeVersion.startsWith("0.7.7"));
                    if (oldBridge) {
                        toast("Ponte Gmail " + (bridgeVersion.isEmpty() ? "sem versão" : "v" + bridgeVersion) +
                                ": para consolidar vários e-mails e receber todas as candidatas, atualize o Apps Script 0.7.7.4.");
                    }

                    for (CoverEntry e : entries) {
                        if ("VALOR ECONÔMICO".equals(e.source.name) || "THE WASHINGTON POST".equals(e.source.name)) continue;
                        List<String> list = matters.get(e.source.name);
                        int sent = list == null ? 0 : list.size();
                        e.status = sent > 0
                                ? "Gmail enviou " + sent + " página(s) • abrindo Ver página..."
                                : "Não veio no Gmail • sem usar internet";
                    }
                    renderList();

                    if (matters.isEmpty()) {
                        branchDone.run();
                        return;
                    }

                    // v0.7.7.7: todas as candidatas continuam sendo analisadas, mas os jornais
                    // são resolvidos em paralelo (até 4 por vez). Dentro de cada jornal,
                    // a ordem das páginas permanece exatamente a mesma.
                    ParallelCentralClippingResolver resolver = new ParallelCentralClippingResolver(this);
                    resolver.resolve(matters, (covers, errors) -> executor.execute(() -> {
                        try {
                            if (!covers.isEmpty()) {
                                ClippingImageScanner.fillFromDirectImages(this, covers, entries, targetDate);
                            }
                        } catch (Exception ignored) {}

                        runOnUiThread(() -> {
                            for (CoverEntry e : entries) {
                                if ("VALOR ECONÔMICO".equals(e.source.name) || "THE WASHINGTON POST".equals(e.source.name)) continue;
                                int sent = ClippingFeedClient.getLastSentCount(e.source.name);
                                if (sent <= 0) continue;
                                int opened = 0;
                                for (CandidatePage c : e.candidates) if (c != null && c.available && c.imageFile != null) opened++;
                                if (e.coverBitmap != null) {
                                    e.status = e.status + " • Gmail " + sent + " recebida(s) / " + opened + " aberta(s)";
                                } else if (opened == 0) {
                                    e.status = "Gmail enviou " + sent + " página(s), mas nenhuma imagem original foi aberta • sem usar internet";
                                }
                            }
                            if (covers.isEmpty()) {
                                toast("O Gmail enviou links, mas nenhuma imagem 'Ver página' foi resolvida.");
                            }
                            renderList();
                            branchDone.run();
                        });
                    }));
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    for (CoverEntry e : entries) {
                        if (!"VALOR ECONÔMICO".equals(e.source.name) && !"THE WASHINGTON POST".equals(e.source.name)) {
                            e.status = "Gmail: " + ex.getMessage() + " • sem usar internet";
                        }
                    }
                    toast("Gmail: " + ex.getMessage());
                    renderList();
                    branchDone.run();
                });
            }
        });
    }

    private void runWebFallback(long started, Date targetDate) {
        List<CoverEntry> missing = new ArrayList<>();
        for (CoverEntry e : entries) if (e.coverBitmap == null) missing.add(e);
        if (missing.isEmpty()) {
            finishRefresh(started);
            return;
        }

        // v0.7.5.0: jornais alimentados pelo Gmail são SOMENTE Gmail.
        // Nunca buscamos uma capa alternativa na internet para substituir uma página
        // do clipping. Valor Econômico e Washington Post continuam como fontes web.
        List<CoverEntry> webAllowedOnly = new ArrayList<>();
        for (CoverEntry e : missing) {
            if ("VALOR ECONÔMICO".equals(e.source.name) || "THE WASHINGTON POST".equals(e.source.name)) {
                webAllowedOnly.add(e);
            } else {
                String current = e.status == null ? "" : e.status.trim();
                if (!current.startsWith("Gmail:") && !current.startsWith("Gmail •") && !current.startsWith("Não veio no Gmail")) {
                    e.status = "Gmail: nenhuma candidata pôde ser confirmada • sem usar internet";
                }
            }
        }
        missing = webAllowedOnly;
        renderList();
        if (missing.isEmpty()) {
            finishRefresh(started);
            return;
        }

        // Em data histórica, os jornais do Gmail continuam exclusivamente no Gmail.
        // Valor e Washington Post são as únicas exceções e usam a capa exibida atualmente
        // em seus links, conforme a regra já adotada no projeto.
        if (!isToday(targetDate)) {
            List<CoverEntry> allowedHistoricalWeb = new ArrayList<>();
            for (CoverEntry e : missing) {
                if ("VALOR ECONÔMICO".equals(e.source.name) && isWeekend(targetDate)) {
                    e.status = "Sem edição regular no fim de semana";
                } else if ("VALOR ECONÔMICO".equals(e.source.name) || "THE WASHINGTON POST".equals(e.source.name)) {
                    e.status = "Buscando a capa exibida atualmente no link...";
                    allowedHistoricalWeb.add(e);
                } else if ("FOLHA DE SÃO PAULO".equals(e.source.name) || "ESTADÃO".equals(e.source.name)
                        || "THE NEW YORK TIMES".equals(e.source.name) || "CORREIO BRAZILIENSE".equals(e.source.name)) {
                    e.status = "Gmail não confirmou; buscando alternativa de " + formatDisplayDate(targetDate) + "...";
                    allowedHistoricalWeb.add(e);
                } else {
                    e.status = "Não encontrado no Gmail em " + formatDisplayDate(targetDate) + " • mantendo sem capa para evitar troca";
                }
            }
            missing = allowedHistoricalWeb;
            renderList();
            if (missing.isEmpty()) {
                finishRefresh(started);
                return;
            }
        }

        // Valor não é buscado em sábado/domingo, independentemente de a capa
        // atual do link ser de sexta-feira. Washington Post sempre usa o que o
        // link dedicado estiver mostrando, sem filtro pela data selecionada.
        List<CoverEntry> runnableMissing = new ArrayList<>();
        for (CoverEntry e : missing) {
            if ("VALOR ECONÔMICO".equals(e.source.name) && isWeekend(targetDate)) {
                e.status = "Sem edição regular no fim de semana";
            } else {
                runnableMissing.add(e);
            }
        }
        missing = runnableMissing;
        if (missing.isEmpty()) {
            renderList();
            finishRefresh(started);
            return;
        }

        AtomicInteger pending = new AtomicInteger(missing.size());
        for (CoverEntry e : missing) {
            if ("THE WASHINGTON POST".equals(e.source.name) || "VALOR ECONÔMICO".equals(e.source.name)) {
                e.status = "Buscando a capa exibida atualmente no link...";
            } else {
                e.status = "Buscando na internet...";
            }
            executor.execute(() -> {
                long itemStart = System.currentTimeMillis();
                try {
                    if ("THE WASHINGTON POST".equals(e.source.name) || "VALOR ECONÔMICO".equals(e.source.name)) {
                        // FrontPages/PressReader são carregados em um navegador interno porque
                        // os URLs de imagem podem retornar 404 fora da sessão da página.
                        // O navegador captura a capa inteira já renderizada, sem crop.
                        FrontPageBrowserResolver.resolve(this, e, targetDate);
                    } else {
                        CoverLoader.load(this, e, targetDate);
                    }
                    long sec = Math.max(1, (System.currentTimeMillis() - itemStart) / 1000);
                    if (e.coverBitmap != null && !e.status.contains("s")) e.status += " • " + sec + "s";
                } catch (Exception ex) {
                    e.status = "Falha: " + ex.getMessage(); e.coverBitmap = null; e.candidates.clear();
                }
                runOnUiThread(this::renderList);
                if (pending.decrementAndGet() == 0) runOnUiThread(() -> finishRefresh(started));
            });
        }
        renderList();
    }

    private void finishRefresh(long started) {
        setBusy(false);
        long total = Math.max(1, (System.currentTimeMillis() - started) / 1000);
        if (lastUpdateText != null) {
            String time = new SimpleDateFormat("HH:mm", new Locale("pt", "BR")).format(new Date());
            lastUpdateText.setText("Última atualização: " + time + " • concluída em " + total + "s");
        }
        toast("Atualização concluída em " + total + "s");
    }

    private String gmailButtonText() {
        return ClippingFeedClient.getConfiguredUrl(this).isEmpty()
                ? "GMAIL AUTOMÁTICO: INATIVO\nToque para configurar o Apps Script"
                : "GMAIL AUTOMÁTICO: ATIVO\nApps Script conectado e funcionando";
    }

    private void showGmailBridgeDialog() {
        EditText input = new EditText(this);
        input.setHint("Cole uma única vez a URL do Web App");
        input.setSingleLine(true);
        input.setText(ClippingFeedClient.getConfiguredUrl(this));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(4), dp(18), 0);

        TextView info = new TextView(this);
        info.setText("Configuração única e gratuita. O Apps Script consolida todos os e-mails do dia com assunto 'Monitoramento: Capa(s) de Jornais', inclusive versões numeradas 1, 2, 3... O APK recebe todas as páginas únicas, abre cada 'Ver página', compara as imagens e escolhe automaticamente a candidata com maior confiança de ser a capa. Jornais do Gmail não usam fallback da internet. Valor Econômico e Washington Post continuam pela internet.");
        info.setTextSize(13);
        info.setTextColor(0xFF475467);
        info.setPadding(0, 0, 0, dp(12));
        box.addView(info);
        box.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Gmail automático sem cobrança")
                .setView(box)
                .setNegativeButton("CANCELAR", null)
                .setNeutralButton("REMOVER", (d,w) -> {
                    ClippingFeedClient.saveConfiguredUrl(this, "");
                    gmailButton.setText("⚙\nCONFIG.");
                    toast("Integração Gmail removida");
                })
                .setPositiveButton("SALVAR", (d,w) -> {
                    String u = input.getText().toString().trim();
                    if(!u.isEmpty() && !ClippingFeedClient.isValidWebAppUrl(u)) {
                        toast("Use a URL do Apps Script terminada em /exec. Não use a URL do Gmail.");
                        return;
                    }
                    ClippingFeedClient.saveConfiguredUrl(this, u);
                    gmailButton.setText("⚙\nCONFIG.");
                    toast(u.isEmpty() ? "URL não informada" : "Gmail automático ativado");
                })
                .show();
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(selectedDate);
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar chosen = Calendar.getInstance();
                    chosen.clear();
                    chosen.set(year, month, dayOfMonth, 12, 0, 0);
                    selectedDate = chosen.getTime();
                    updateSelectedDateUi(true);
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.setTitle("Escolha a data das capas");
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "HOJE", (d, which) -> {
            selectedDate = new Date();
            updateSelectedDateUi(true);
        });
        dialog.show();
    }

    private void updateSelectedDateUi(boolean clearResults) {
        String shown = formatDisplayDate(selectedDate);
        if (heroDateText != null) heroDateText.setText(shown);
        if (dateButton != null) dateButton.setText(shown);
        if (lastUpdateText != null && clearResults) lastUpdateText.setText("Data alterada • toque em ATUALIZAR CAPAS");
        if (clearResults) {
            for (CoverEntry e : entries) {
                deleteManualCacheFile(e);
                e.status = "Aguardando busca para " + shown;
                e.coverBitmap = null;
                e.originalImagePath = "";
                e.originalWidth = 0;
                e.originalHeight = 0;
                e.candidates.clear();
                clearManualState(e);
            }
            renderList();
        }
    }

    private String formatDisplayDate(Date date) {
        return new SimpleDateFormat("dd/MM/yyyy", new Locale("pt", "BR")).format(date);
    }

    private boolean isToday(Date date) {
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        b.setTime(date);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private boolean isWeekend(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        int dow = c.get(Calendar.DAY_OF_WEEK);
        return dow == Calendar.SATURDAY || dow == Calendar.SUNDAY;
    }

    private void showReview(CoverEntry entry) {
        if (entry.candidates.isEmpty()) return;

        // v0.7.5.1: detectedPage é a ordem original no Gmail, não necessariamente
        // o índice da lista (alguma candidata anterior pode ter sido descartada).
        // Por isso localizamos explicitamente a candidata escolhida em vez de usar
        // detectedPage - 1, que podia apontar para a imagem errada ou impedir a navegação.
        int initialIndex = 0;
        for (int i = 0; i < entry.candidates.size(); i++) {
            if (entry.candidates.get(i).pageNumber == entry.detectedPage) {
                initialIndex = i;
                break;
            }
        }
        final int[] idx = {initialIndex};
        final Bitmap[] reviewBitmap = {null};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(4));

        TextView pageLabel = new TextView(this);
        pageLabel.setGravity(Gravity.CENTER);
        pageLabel.setTextSize(16);
        box.addView(pageLabel);

        TextView help = new TextView(this);
        help.setGravity(Gravity.CENTER);
        help.setTextSize(13);
        help.setTextColor(0xFF667085);
        help.setPadding(0, dp(4), 0, dp(6));
        box.addView(help);

        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        box.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(460)));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        Button prev = new Button(this);
        prev.setText("ANTERIOR");
        Button next = new Button(this);
        next.setText("PRÓXIMA");
        nav.addView(prev, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        nextLp.setMarginStart(dp(8));
        nav.addView(next, nextLp);
        box.addView(nav);

        Runnable redraw = () -> {
            if (idx[0] < 0 || idx[0] >= entry.candidates.size()) idx[0] = 0;
            CandidatePage c = entry.candidates.get(idx[0]);
            int total = entry.candidates.size();

            if (!c.available || c.imageFile == null || !c.imageFile.exists()) {
                pageLabel.setText("Candidata " + (idx[0] + 1) + " de " + total + "  •  não aberta");
                help.setText("Esta página foi recebida pelo Gmail, mas a imagem original não pôde ser aberta" +
                        (c.errorMessage == null || c.errorMessage.isEmpty() ? "." : ": " + c.errorMessage));
                image.setImageDrawable(null);
                if (reviewBitmap[0] != null && !reviewBitmap[0].isRecycled()) {
                    reviewBitmap[0].recycle();
                    reviewBitmap[0] = null;
                }
                return;
            }

            pageLabel.setText("Candidata " + (idx[0] + 1) + " de " + total +
                    "  •  confiança " + c.score + "%");
            if (total <= 1) {
                help.setText("O Gmail disponibilizou apenas 1 página deste jornal nesta busca.");
            } else {
                help.setText("Use ANTERIOR/PRÓXIMA e toque em USAR ESTA PÁGINA quando encontrar a capa correta.");
            }

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(c.imageFile.getAbsolutePath(), bounds);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= 1200) sample *= 2;
            opts.inSampleSize = Math.max(1, sample);
            Bitmap b = BitmapFactory.decodeFile(c.imageFile.getAbsolutePath(), opts);
            if (reviewBitmap[0] != null && reviewBitmap[0] != b && !reviewBitmap[0].isRecycled()) {
                reviewBitmap[0].recycle();
            }
            reviewBitmap[0] = b;
            image.setImageBitmap(b);
        };

        boolean hasMultiple = entry.candidates.size() > 1;
        prev.setEnabled(hasMultiple);
        next.setEnabled(hasMultiple);
        prev.setAlpha(hasMultiple ? 1.0f : 0.45f);
        next.setAlpha(hasMultiple ? 1.0f : 0.45f);

        prev.setOnClickListener(v -> {
            if (entry.candidates.size() <= 1) return;
            idx[0] = (idx[0] - 1 + entry.candidates.size()) % entry.candidates.size();
            redraw.run();
        });
        next.setOnClickListener(v -> {
            if (entry.candidates.size() <= 1) return;
            idx[0] = (idx[0] + 1) % entry.candidates.size();
            redraw.run();
        });
        redraw.run();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(entry.source.name)
                .setView(box)
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("USAR ESTA PÁGINA", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            CandidatePage chosen = entry.candidates.get(idx[0]);
            if (!chosen.available || chosen.imageFile == null || !chosen.imageFile.exists()) {
                toast("Esta página foi recebida, mas não pôde ser aberta. Escolha outra candidata.");
                return;
            }
            // Ao escolher novamente uma candidata automática, a substituição manual deixa de valer.
            discardManualOverride(entry);
            entry.detectedPage = chosen.pageNumber;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(chosen.imageFile.getAbsolutePath(), bounds);
            BitmapFactory.Options previewOpts = new BitmapFactory.Options();
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= 1200) sample *= 2;
            previewOpts.inSampleSize = Math.max(1, sample);
            entry.coverBitmap = BitmapFactory.decodeFile(chosen.imageFile.getAbsolutePath(), previewOpts);
            entry.originalImagePath = chosen.imageFile.getAbsolutePath();
            entry.originalWidth = bounds.outWidth;
            entry.originalHeight = bounds.outHeight;
            entry.status = "Candidata " + (idx[0] + 1) + " de " + entry.candidates.size() +
                    " selecionada manualmente • confiança " + chosen.score + "%";
            dialog.dismiss();
            renderList();
        }));
        dialog.setOnDismissListener(d -> {
            image.setImageDrawable(null);
            if (reviewBitmap[0] != null && !reviewBitmap[0].isRecycled()) {
                reviewBitmap[0].recycle();
                reviewBitmap[0] = null;
            }
        });
        dialog.show();
    }

    /** Abre o seletor de imagens do Android sem pedir acesso amplo à galeria. */
    private void chooseManualCover(CoverEntry entry) {
        pendingManualEntry = entry;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_MANUAL_COVER);
        } catch (Exception e) {
            pendingManualEntry = null;
            toast("Não foi possível abrir a galeria/arquivos: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_MANUAL_COVER) return;

        CoverEntry entry = pendingManualEntry;
        pendingManualEntry = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null || entry == null) return;

        Uri uri = data.getData();
        setBusy(true);
        executor.execute(() -> {
            try {
                applyManualCover(entry, uri);
                runOnUiThread(() -> {
                    setBusy(false);
                    renderList();
                    toast("Capa manual aplicada em " + entry.source.name + ".");
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    setBusy(false);
                    toast("Não foi possível usar esta imagem: " + ex.getMessage());
                });
            }
        });
    }

    /**
     * Copia a imagem escolhida para o cache privado do app. Assim o PDF usa o arquivo
     * original selecionado, sem depender da permissão da galeria e sem reduzir a qualidade.
     */
    private void applyManualCover(CoverEntry entry, Uri uri) throws Exception {
        if (!entry.manualOverride) {
            entry.automaticCoverBitmap = entry.coverBitmap;
            entry.automaticOriginalImagePath = entry.originalImagePath == null ? "" : entry.originalImagePath;
            entry.automaticOriginalWidth = entry.originalWidth;
            entry.automaticOriginalHeight = entry.originalHeight;
            entry.automaticStatus = entry.status == null ? "" : entry.status;
            entry.automaticDetectedPage = entry.detectedPage;
        }

        String oldManualPath = entry.manualImagePath;
        File manualFile = File.createTempFile("capa_manual_", manualSuffix(uri), getCacheDir());
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(manualFile)) {
            if (in == null) throw new Exception("arquivo não pôde ser aberto");
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(manualFile.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            manualFile.delete();
            throw new Exception("formato de imagem não reconhecido");
        }

        BitmapFactory.Options previewOpts = new BitmapFactory.Options();
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= 1200) sample *= 2;
        previewOpts.inSampleSize = Math.max(1, sample);
        previewOpts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap preview = BitmapFactory.decodeFile(manualFile.getAbsolutePath(), previewOpts);
        if (preview == null) {
            manualFile.delete();
            throw new Exception("não foi possível criar a prévia");
        }

        entry.coverBitmap = preview;
        entry.originalImagePath = manualFile.getAbsolutePath();
        entry.originalWidth = bounds.outWidth;
        entry.originalHeight = bounds.outHeight;
        entry.detectedPage = 0;
        entry.manualOverride = true;
        entry.manualImagePath = manualFile.getAbsolutePath();
        entry.selected = true;
        entry.status = "Capa inserida manualmente • " + bounds.outWidth + "×" + bounds.outHeight + " • usada no PDF";

        if (oldManualPath != null && !oldManualPath.isEmpty() && !oldManualPath.equals(entry.manualImagePath)) {
            try { new File(oldManualPath).delete(); } catch (Exception ignored) {}
        }
    }

    private String manualSuffix(Uri uri) {
        try {
            String type = getContentResolver().getType(uri);
            if (type != null) {
                String t = type.toLowerCase(Locale.ROOT);
                if (t.contains("png")) return ".png";
                if (t.contains("webp")) return ".webp";
                if (t.contains("gif")) return ".gif";
                if (t.contains("heic") || t.contains("heif")) return ".heic";
            }
        } catch (Exception ignored) {}
        return ".jpg";
    }

    /** Restaura exatamente o resultado que estava no cartão antes da substituição manual. */
    private void restoreAutomaticCover(CoverEntry entry) {
        if (!entry.manualOverride) return;
        String manualPath = entry.manualImagePath;
        entry.coverBitmap = entry.automaticCoverBitmap;
        entry.originalImagePath = entry.automaticOriginalImagePath == null ? "" : entry.automaticOriginalImagePath;
        entry.originalWidth = entry.automaticOriginalWidth;
        entry.originalHeight = entry.automaticOriginalHeight;
        entry.status = (entry.automaticStatus == null || entry.automaticStatus.isEmpty()) ? "Aguardando" : entry.automaticStatus;
        entry.detectedPage = entry.automaticDetectedPage;
        clearManualState(entry);
        if (manualPath != null && !manualPath.isEmpty()) {
            try { new File(manualPath).delete(); } catch (Exception ignored) {}
        }
        renderList();
        toast("Capa automática restaurada para " + entry.source.name + ".");
    }

    /** Remove apenas a marca de substituição; o resultado automático atual permanece. */
    private void discardManualOverride(CoverEntry entry) {
        String manualPath = entry.manualImagePath;
        clearManualState(entry);
        if (manualPath != null && !manualPath.isEmpty()) {
            try { new File(manualPath).delete(); } catch (Exception ignored) {}
        }
    }

    private void deleteManualCacheFile(CoverEntry entry) {
        if (entry == null || entry.manualImagePath == null || entry.manualImagePath.isEmpty()) return;
        try { new File(entry.manualImagePath).delete(); } catch (Exception ignored) {}
    }

    private void clearManualState(CoverEntry entry) {
        entry.manualOverride = false;
        entry.manualImagePath = "";
        entry.automaticCoverBitmap = null;
        entry.automaticOriginalImagePath = "";
        entry.automaticOriginalWidth = 0;
        entry.automaticOriginalHeight = 0;
        entry.automaticStatus = "";
        entry.automaticDetectedPage = 0;
    }

    private void exportPdf() {
        boolean any = false;
        for (CoverEntry e : entries) {
            if (e.selected && e.coverBitmap != null) {
                any = true;
                break;
            }
        }
        if (!any) {
            toast("Atualize e selecione pelo menos uma capa.");
            return;
        }

        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 500);
            return;
        }

        setBusy(true);
        executor.execute(() -> {
            try {
                String result = PdfExporter.export(this, entries, new Date(selectedDate.getTime()));
                runOnUiThread(() -> {
                    setBusy(false);
                    new AlertDialog.Builder(this)
                            .setTitle("PDF criado")
                            .setMessage(result + "\n\nSalvo em Downloads/Principais Capas")
                            .setNegativeButton("FECHAR", null)
                            .setPositiveButton("ABRIR PDF", (d, w) -> openGeneratedPdf())
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    toast("Erro ao gerar PDF: " + e.getMessage());
                });
            }
        });
    }

    private void openGeneratedPdf() {
        try {
            Uri uri = PdfExporter.findExistingPdfUri(this, new Date(selectedDate.getTime()));
            if (uri == null) {
                toast("PDF de " + formatDisplayDate(selectedDate) + " não encontrado. Gere o PDF primeiro.");
                return;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivity(Intent.createChooser(intent, "Abrir PDF"));
        } catch (Exception e) {
            toast("Não foi possível abrir o PDF: " + e.getMessage());
        }
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        refreshButton.setEnabled(!busy);
        exportButton.setEnabled(!busy);
        if (openPdfButton != null) openPdfButton.setEnabled(!busy);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
