package br.com.extratorvideos;

import br.com.centralmidia.android.R;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.content.ActivityNotFoundException;
import android.view.View;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class MainActivity extends AppCompatActivity {

    private EditText editUrl;
    private Spinner spinnerQuality;
    private Button buttonDownload;
    private Button buttonCancel;
    private Button buttonOpenFolder;
    private Button buttonUpdateYtdlp;
    private Button buttonSelectVideo;
    private Button buttonPreview;
    private Button buttonCutVideo;
    private Button buttonOpenEditor;
    private Button buttonMarkStart;
    private Button buttonMarkEnd;
    private Button buttonBack1s;
    private Button buttonBack100ms;
    private Button buttonBack10ms;
    private Button buttonForward10ms;
    private Button buttonForward100ms;
    private Button buttonForward1s;
    private EditText editCutStart;
    private EditText editCutEnd;
    private EditText editCutOutputName;
    private TextView textSelectedVideo;
    private TextView textCutDuration;
    private TextView textCutStatus;
    private TextureView videoPreview;
    private RangeSelectionView rangeSelector;
    private TextView textPreviewPosition;
    private TextView textPreviewHint;
    private ProgressBar progressCut;
    private ProgressBar progressBar;
    private TextView textProgress;
    private TextView textStatus;
    private ScrollView mainScroll;
    private Button buttonNavDownload;
    private Button buttonNavCut;
    private View downloadSection;
    private View cutSection;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean ready = false;
    private volatile boolean downloading = false;
    private volatile boolean cutting = false;
    private String processId = null;
    private File selectedVideo = null;
    private long selectedVideoDurationMs = 0L;
    private Transformer cutTransformer = null;
    private File cutOutputPending = null;

    private final Handler previewHandler = new Handler(Looper.getMainLooper());
    private MediaPlayer previewPlayer = null;
    private boolean previewSurfaceReady = false;
    private boolean previewPrepared = false;
    private boolean previewing = false;
    private boolean previewStartPending = false;
    private boolean previewTimelineSeeking = false;
    private long previewStartMs = 0L;
    private long previewEndMs = 0L;

    private final Runnable previewMonitor = new Runnable() {
        @Override
        public void run() {
            if (previewPlayer == null || !previewPrepared) return;
            try {
                long pos = previewPlayer.getCurrentPosition();
                if (!previewTimelineSeeking) {
                    atualizarPosicaoPreview(pos);
                }

                if (previewing && pos >= previewEndMs) {
                    pararPrevia(true);
                    return;
                }
            } catch (Exception ignored) {
            }
            if (previewPlayer != null && previewPrepared) {
                previewHandler.postDelayed(this, 100L);
            }
        }
    };

    private static final String[] QUALIDADES = {
            "360p",
            "480p",
            "720p HD",
            "1080p Full HD",
            "Melhor disponível"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View systemRoot = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(systemRoot, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(systemRoot);

        editUrl = findViewById(R.id.editUrl);
        String incomingCentralUrl = getIntent().getStringExtra("url");
        if (incomingCentralUrl != null && !incomingCentralUrl.trim().isEmpty()) editUrl.setText(incomingCentralUrl.trim());
        spinnerQuality = findViewById(R.id.spinnerQuality);
        buttonDownload = findViewById(R.id.buttonDownload);
        buttonCancel = findViewById(R.id.buttonCancel);
        buttonOpenFolder = findViewById(R.id.buttonOpenFolder);
        buttonUpdateYtdlp = findViewById(R.id.buttonUpdateYtdlp);
        buttonSelectVideo = findViewById(R.id.buttonSelectVideo);
        buttonPreview = findViewById(R.id.buttonPreview);
        buttonCutVideo = findViewById(R.id.buttonCutVideo);
        buttonOpenEditor = findViewById(R.id.buttonOpenEditor);
        buttonMarkStart = findViewById(R.id.buttonMarkStart);
        buttonMarkEnd = findViewById(R.id.buttonMarkEnd);
        buttonBack1s = findViewById(R.id.buttonBack1s);
        buttonBack100ms = findViewById(R.id.buttonBack100ms);
        buttonBack10ms = findViewById(R.id.buttonBack10ms);
        buttonForward10ms = findViewById(R.id.buttonForward10ms);
        buttonForward100ms = findViewById(R.id.buttonForward100ms);
        buttonForward1s = findViewById(R.id.buttonForward1s);
        editCutStart = findViewById(R.id.editCutStart);
        editCutEnd = findViewById(R.id.editCutEnd);
        editCutOutputName = findViewById(R.id.editCutOutputName);
        textSelectedVideo = findViewById(R.id.textSelectedVideo);
        textCutDuration = findViewById(R.id.textCutDuration);
        textCutStatus = findViewById(R.id.textCutStatus);
        videoPreview = findViewById(R.id.videoPreview);
        rangeSelector = findViewById(R.id.rangeSelector);
        textPreviewPosition = findViewById(R.id.textPreviewPosition);
        textPreviewHint = findViewById(R.id.textPreviewHint);
        progressCut = findViewById(R.id.progressCut);
        progressBar = findViewById(R.id.progressBar);
        textProgress = findViewById(R.id.textProgress);
        textStatus = findViewById(R.id.textStatus);
        mainScroll = findViewById(R.id.mainScroll);
        buttonNavDownload = findViewById(R.id.buttonNavDownload);
        buttonNavCut = findViewById(R.id.buttonNavCut);
        downloadSection = findViewById(R.id.downloadSection);
        cutSection = findViewById(R.id.cutSection);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                QUALIDADES
        );
        spinnerQuality.setAdapter(adapter);
        spinnerQuality.setSelection(1); // 480p como padrão

        buttonDownload.setEnabled(false);
        buttonDownload.setOnClickListener(v -> iniciarDownload());
        buttonCancel.setOnClickListener(v -> cancelarDownload());
        buttonOpenFolder.setOnClickListener(v -> abrirPastaVideos());
        buttonUpdateYtdlp.setOnClickListener(v -> atualizarYtDlp());
        buttonSelectVideo.setOnClickListener(v -> selecionarVideoParaCorte());
        buttonPreview.setOnClickListener(v -> alternarPreviaTrecho());
        buttonCutVideo.setOnClickListener(v -> cortarVideoSelecionado());
        buttonOpenEditor.setOnClickListener(v -> startActivity(new Intent(this, EditorActivity.class)));
        buttonNavDownload.setOnClickListener(v -> rolarParaSecao(downloadSection));
        buttonNavCut.setOnClickListener(v -> rolarParaSecao(cutSection));
        buttonMarkStart.setOnClickListener(v -> marcarInicioNaPosicaoAtual());
        buttonMarkEnd.setOnClickListener(v -> marcarFimNaPosicaoAtual());
        buttonBack1s.setOnClickListener(v -> ajustarPosicaoAtual(-1000L));
        buttonBack100ms.setOnClickListener(v -> ajustarPosicaoAtual(-100L));
        buttonBack10ms.setOnClickListener(v -> ajustarPosicaoAtual(-10L));
        buttonForward10ms.setOnClickListener(v -> ajustarPosicaoAtual(10L));
        buttonForward100ms.setOnClickListener(v -> ajustarPosicaoAtual(100L));
        buttonForward1s.setOnClickListener(v -> ajustarPosicaoAtual(1000L));

        videoPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                previewSurfaceReady = true;
                if (previewStartPending && selectedVideo != null) {
                    iniciarPlayerPreviewPendente();
                } else if (selectedVideo != null) {
                    prepararFramePreview(rangeSelector == null ? 0L : rangeSelector.getPlayheadMs());
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                previewSurfaceReady = false;
                liberarPlayerPreview();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });

        rangeSelector.setOnRangeChangeListener(new RangeSelectionView.OnRangeChangeListener() {
            @Override
            public void onRangeChanged(long startMs, long endMs, int activeThumb, boolean fromUser) {
                if (activeThumb == RangeSelectionView.THUMB_START) {
                    editCutStart.setText(formatarTempo(startMs));
                    atualizarPosicaoPreview(startMs);
                } else if (activeThumb == RangeSelectionView.THUMB_END) {
                    editCutEnd.setText(formatarTempo(endMs));
                    atualizarPosicaoPreview(endMs);
                } else {
                    atualizarPosicaoPreview(rangeSelector.getPlayheadMs());
                }

                if (fromUser) {
                    if (activeThumb == RangeSelectionView.THUMB_PLAYHEAD) {
                        if (previewing) previewTimelineSeeking = true;
                        textCutStatus.setText("Posição atual: " + formatarTempo(rangeSelector.getPlayheadMs()) +
                                (previewing ? "   •   solte para continuar daqui" : "   •   Início/Fim não alterados"));
                    } else {
                        long pos = activeThumb == RangeSelectionView.THUMB_START ? startMs : endMs;
                        String nomeCursor = activeThumb == RangeSelectionView.THUMB_START ? "Início" : "Fim";
                        textCutStatus.setText(nomeCursor + ": " + formatarTempo(pos) +
                                "   •   Trecho: " + formatarTempo(startMs) + " até " + formatarTempo(endMs));
                    }
                }
            }

            @Override
            public void onRangeChangeFinished(long startMs, long endMs, int activeThumb) {
                long pos;
                if (activeThumb == RangeSelectionView.THUMB_START) pos = startMs;
                else if (activeThumb == RangeSelectionView.THUMB_END) pos = endMs;
                else pos = rangeSelector.getPlayheadMs();

                if (activeThumb == RangeSelectionView.THUMB_PLAYHEAD && previewing) {
                    previewTimelineSeeking = false;
                    reposicionarPreviaSemPausar(pos);
                    return;
                }
                previewTimelineSeeking = false;
                if (previewing) pararPrevia(false);
                if (selectedVideo != null) prepararFramePreview(pos);
            }
        });

        editCutStart.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) sincronizarCursoresComCampos();
        });
        editCutEnd.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) sincronizarCursoresComCampos();
        });

        // Mostra o botão se a pasta já existir de downloads anteriores.
        buttonOpenFolder.setVisibility(pastaVideos().exists() ? View.VISIBLE : View.GONE);

        pedirPermissaoAndroid10SeNecessario();
        inicializarMotores();
    }

    private void rolarParaSecao(View section) {
        if (mainScroll == null || section == null) return;
        mainScroll.post(() -> mainScroll.smoothScrollTo(0, Math.max(0, section.getTop() - 12)));
    }

    private void pedirPermissaoAndroid10SeNecessario() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    90
            );
        }
    }

    private void inicializarMotores() {
        setStatus("Inicializando yt-dlp e FFmpeg...");
        executor.execute(() -> {
            try {
                YoutubeDL.getInstance().init(getApplicationContext());
                FFmpeg.getInstance().init(getApplicationContext());
                ready = true;
                runOnUiThread(() -> {
                    setStatus("Pronto. Cole um link e escolha a qualidade.");
                    buttonDownload.setEnabled(true);
                    buttonUpdateYtdlp.setEnabled(true);
                    buttonSelectVideo.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> setStatus("Erro ao inicializar: " + mensagemCurta(e)));
            }
        });
    }

    private void atualizarYtDlp() {
        if (!ready || downloading || cutting) return;

        buttonUpdateYtdlp.setEnabled(false);
        buttonDownload.setEnabled(false);
        buttonSelectVideo.setEnabled(false);
        buttonPreview.setEnabled(false);
        buttonCutVideo.setEnabled(false);
        rangeSelector.setEnabled(false);
        setStatus("Verificando atualização do yt-dlp...");

        executor.execute(() -> {
            try {
                String antes = YoutubeDL.getInstance().versionName(getApplicationContext());
                YoutubeDL.UpdateStatus resultado = YoutubeDL.getInstance().updateYoutubeDL(
                        getApplicationContext(),
                        YoutubeDL.UpdateChannel._STABLE
                );
                String depois = YoutubeDL.getInstance().versionName(getApplicationContext());

                runOnUiThread(() -> {
                    if (resultado == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE) {
                        setStatus("✓ yt-dlp já está atualizado" + versaoTexto(depois));
                        Toast.makeText(this, "yt-dlp já está na versão mais recente", Toast.LENGTH_LONG).show();
                    } else {
                        setStatus("✓ yt-dlp atualizado" + versaoTexto(depois));
                        Toast.makeText(this, "Atualização concluída", Toast.LENGTH_LONG).show();
                    }
                    buttonUpdateYtdlp.setEnabled(true);
                    buttonDownload.setEnabled(true);
                    buttonSelectVideo.setEnabled(true);
                    buttonPreview.setEnabled(selectedVideo != null && selectedVideoDurationMs > 0);
                    buttonCutVideo.setEnabled(selectedVideo != null && selectedVideoDurationMs > 0);
                    rangeSelector.setEnabled(selectedVideo != null && selectedVideoDurationMs > 0);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setStatus("Erro ao atualizar yt-dlp: " + mensagemCurta(e));
                    Toast.makeText(this, "Não foi possível atualizar. Verifique a internet.", Toast.LENGTH_LONG).show();
                    buttonUpdateYtdlp.setEnabled(true);
                    buttonDownload.setEnabled(true);
                    buttonSelectVideo.setEnabled(true);
                    buttonPreview.setEnabled(selectedVideo != null && selectedVideoDurationMs > 0);
                    buttonCutVideo.setEnabled(selectedVideo != null && selectedVideoDurationMs > 0);
                    rangeSelector.setEnabled(selectedVideo != null && selectedVideoDurationMs > 0);
                });
            }
        });
    }

    private String versaoTexto(String versao) {
        if (versao == null || versao.trim().isEmpty()) return "";
        return " • versão " + versao.trim();
    }

    private void iniciarDownload() {
        if (!ready || downloading || cutting) return;

        String url = editUrl.getText().toString().trim();
        if (!url.matches("(?i)^https?://.+")) {
            Toast.makeText(this, "Cole um link começando com http:// ou https://", Toast.LENGTH_LONG).show();
            return;
        }

        pararPrevia(false);
        downloading = true;
        buttonDownload.setEnabled(false);
        buttonSelectVideo.setEnabled(false);
        buttonPreview.setEnabled(false);
        buttonCutVideo.setEnabled(false);
        buttonCancel.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        textProgress.setText("0%");
        setStatus("Analisando página...");

        final String formato = formatoParaQualidade(spinnerQuality.getSelectedItemPosition());

        executor.execute(() -> {
            try {
                File destino = pastaVideos();
                if (!destino.exists() && !destino.mkdirs()) {
                    throw new IllegalStateException("Não foi possível criar Downloads/ExtratorVideos");
                }

                // 1) Tentativa principal: o próprio yt-dlp analisa a página.
                Exception falhaPrincipal = executarTentativa(url, formato, destino, "", "Tentativa principal...");
                if (falhaPrincipal == null) {
                    concluirDownload("✓ Download concluído");
                    return;
                }

                if (!downloading) return;

                // YouTube: mantém o modo de compatibilidade que já existia.
                if (ehYoutube(url) && pareceFalhaDeFormatoOu403(falhaPrincipal)) {
                    runOnUiThread(() -> setStatus("Tentando modo de compatibilidade do YouTube..."));
                    Exception falhaCompat = executarTentativa(
                            url,
                            formatoCompatibilidade(spinnerQuality.getSelectedItemPosition()),
                            destino,
                            "",
                            "Compatibilidade YouTube..."
                    );
                    if (falhaCompat == null) {
                        concluirDownload("✓ Download concluído pelo modo de compatibilidade");
                        return;
                    }
                    falhaPrincipal = falhaCompat;
                }

                if (!downloading) return;

                // 2) Para páginas de notícias e outros sites, replica a lógica da v1.6 do Windows:
                // baixa o HTML, encontra até 15 URLs de mídia/player e testa uma por uma.
                if (!ehYoutube(url)) {
                    runOnUiThread(() -> setStatus("Método principal falhou. Procurando vídeos dentro da página..."));
                    List<String> candidatos = extrairCandidatosDaPagina(url);

                    for (int i = 0; i < candidatos.size() && downloading; i++) {
                        final int tentativaAtual = i + 1;
                        final int total = candidatos.size();
                        final String candidato = candidatos.get(i);

                        runOnUiThread(() -> {
                            progressBar.setProgress(0);
                            textProgress.setText("0%");
                            setStatus("Tentativa alternativa " + tentativaAtual + "/" + total + "...");
                        });

                        Exception falha = executarTentativa(
                                candidato,
                                formato,
                                destino,
                                url,
                                "Tentativa alternativa " + tentativaAtual + "/" + total + "..."
                        );

                        if (falha == null) {
                            concluirDownload("✓ Download concluído na tentativa alternativa " + tentativaAtual + "/" + total);
                            return;
                        }
                        falhaPrincipal = falha;
                    }
                }

                if (!downloading) return;
                final Exception erroFinal = falhaPrincipal;
                runOnUiThread(() -> {
                    setStatus("Erro: " + mensagemCurta(erroFinal));
                    finalizarEstadoDownload();
                });

            } catch (Exception e) {
                if (!downloading) return;
                runOnUiThread(() -> {
                    setStatus("Erro: " + mensagemCurta(e));
                    finalizarEstadoDownload();
                });
            }
        });
    }

    /**
     * Executa uma tentativa de yt-dlp. Retorna null em caso de sucesso ou a exceção em caso de falha.
     */
    private Exception executarTentativa(String url, String formato, File destino, String referer, String rotulo) {
        try {
            if (!downloading) return new IllegalStateException("Download cancelado");

            processId = "download-" + UUID.randomUUID();
            YoutubeDLRequest request = criarRequest(url, formato, destino, referer);

            YoutubeDL.getInstance().execute(
                    request,
                    processId,
                    (progress, etaInSeconds, line) -> {
                        int p = Math.max(0, Math.min(100, Math.round(progress)));
                        runOnUiThread(() -> {
                            progressBar.setProgress(p);
                            String eta = etaInSeconds > 0 ? " • faltam ~" + etaInSeconds + "s" : "";
                            textProgress.setText(String.format(Locale.getDefault(), "%d%%%s", p, eta));

                            if (line != null && !line.trim().isEmpty()) {
                                String limpa = limparLinhaStatus(line);
                                // Avisos de versão não devem substituir a indicação da tentativa atual.
                                if (!limpa.toLowerCase(Locale.ROOT).startsWith("warning:")) {
                                    setStatus(limpa);
                                }
                            }
                        });
                        return kotlin.Unit.INSTANCE;
                    }
            );
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private YoutubeDLRequest criarRequest(String url, String formato, File destino, String referer) {
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        request.addOption("--no-mtime");
        request.addOption("--no-playlist");
        request.addOption("--force-ipv4");
        request.addOption("--retries", "10");
        request.addOption("--fragment-retries", "10");
        request.addOption("--socket-timeout", "30");
        request.addOption("-f", formato);
        request.addOption("--merge-output-format", "mp4");
        request.addOption("--remux-video", "mp4");
        if (referer != null && !referer.trim().isEmpty()) {
            request.addOption("--referer", referer);
        }
        request.addOption("-o", destino.getAbsolutePath() + "/%(title).100s [%(id)s].%(ext)s");
        return request;
    }

    private void concluirDownload(String mensagem) {
        runOnUiThread(() -> {
            progressBar.setProgress(100);
            textProgress.setText("100%");
            setStatus(mensagem + " em Downloads/ExtratorVideos");
            Toast.makeText(this, "Vídeo salvo com sucesso", Toast.LENGTH_LONG).show();
            finalizarEstadoDownload();
            mostrarOpcaoAbrirPasta();
        });
    }

    private boolean ehYoutube(String url) {
        return url != null && url.matches("(?i).*?(youtube\\.com|youtu\\.be).*?");
    }

    private List<String> extrairCandidatosDaPagina(String paginaUrl) {
        try {
            String html = baixarHtml(paginaUrl);
            if (html == null || html.trim().isEmpty()) return new ArrayList<>();
            return extrairCandidatosDoHtml(html, paginaUrl);
        } catch (Exception e) {
            runOnUiThread(() -> setStatus("Não foi possível analisar o HTML: " + mensagemCurta(e)));
            return new ArrayList<>();
        }
    }

    private String baixarHtml(String paginaUrl) throws Exception {
        HttpURLConnection conexao = (HttpURLConnection) new URL(paginaUrl).openConnection();
        conexao.setInstanceFollowRedirects(true);
        conexao.setConnectTimeout(20000);
        conexao.setReadTimeout(30000);
        conexao.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/149.0.0.0 Mobile Safari/537.36");
        conexao.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8");
        conexao.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8");

        int codigo = conexao.getResponseCode();
        if (codigo < 200 || codigo >= 400) {
            conexao.disconnect();
            throw new IllegalStateException("HTTP " + codigo + " ao acessar a página");
        }

        String tipo = conexao.getContentType();
        if (tipo != null && !tipo.toLowerCase(Locale.ROOT).contains("text/html")) {
            conexao.disconnect();
            return "";
        }

        InputStream input = conexao.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String linha;
        while ((linha = reader.readLine()) != null) {
            sb.append(linha).append('\n');
        }
        reader.close();
        conexao.disconnect();
        return sb.toString();
    }

    private List<String> extrairCandidatosDoHtml(String html, String paginaUrl) {
        Set<String> candidatos = new LinkedHashSet<>();
        String texto = String.valueOf(html)
                .replace("\\u0026", "&")
                .replace("\\u003d", "=")
                .replace("\\u002f", "/")
                .replace("\\/", "/")
                .replace("&amp;", "&");

        // URLs absolutas.
        coletarRegex(texto, Pattern.compile("https?://[^\\s\\\"'<>]+", Pattern.CASE_INSENSITIVE), 0, paginaUrl, candidatos, false);

        // src/content/file/href em HTML.
        coletarRegex(texto,
                Pattern.compile("\\b(?:src|content|file|href)\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']", Pattern.CASE_INSENSITIVE),
                1, paginaUrl, candidatos, false);

        // Campos comuns em JSON/JSON-LD.
        Matcher json = Pattern.compile("[\\\"'](?:contentUrl|embedUrl|videoUrl|streamUrl|file|src|url)[\\\"']\\s*:\\s*[\\\"']([^\\\"']+)[\\\"']", Pattern.CASE_INSENSITIVE).matcher(texto);
        while (json.find() && candidatos.size() < 15) {
            adicionarCandidato(json.group(1), paginaUrl, candidatos, true);
        }

        // Meta tags de vídeo.
        Matcher meta = Pattern.compile("<meta[^>]+(?:property|name)=[\\\"'](?:og:video(?::url|:secure_url)?|twitter:player(?::stream)?)[\\\"'][^>]+content=[\\\"']([^\\\"']+)[\\\"'][^>]*>", Pattern.CASE_INSENSITIVE).matcher(texto);
        while (meta.find() && candidatos.size() < 15) adicionarCandidato(meta.group(1), paginaUrl, candidatos, true);

        Matcher metaInv = Pattern.compile("<meta[^>]+content=[\\\"']([^\\\"']+)[\\\"'][^>]+(?:property|name)=[\\\"'](?:og:video(?::url|:secure_url)?|twitter:player(?::stream)?)[\\\"'][^>]*>", Pattern.CASE_INSENSITIVE).matcher(texto);
        while (metaInv.find() && candidatos.size() < 15) adicionarCandidato(metaInv.group(1), paginaUrl, candidatos, true);

        // Iframes de players incorporados.
        Matcher iframe = Pattern.compile("<iframe[^>]+src=[\\\"']([^\\\"']+)[\\\"'][^>]*>", Pattern.CASE_INSENSITIVE).matcher(texto);
        while (iframe.find() && candidatos.size() < 15) adicionarCandidato(iframe.group(1), paginaUrl, candidatos, true);

        return new ArrayList<>(candidatos).subList(0, Math.min(15, candidatos.size()));
    }

    private void coletarRegex(String texto, Pattern pattern, int grupo, String paginaUrl, Set<String> candidatos, boolean forcar) {
        Matcher matcher = pattern.matcher(texto);
        while (matcher.find() && candidatos.size() < 15) {
            adicionarCandidato(matcher.group(grupo), paginaUrl, candidatos, forcar);
        }
    }

    private void adicionarCandidato(String valor, String paginaUrl, Set<String> candidatos, boolean forcar) {
        if (valor == null || candidatos.size() >= 15) return;
        try {
            String texto = valor.trim()
                    .replace("&amp;", "&")
                    .replace("\\u0026", "&")
                    .replace("\\u003d", "=")
                    .replace("\\u002f", "/")
                    .replace("\\/", "/");

            URL base = new URL(paginaUrl);
            URL resolvida = new URL(base, texto);
            String url = resolvida.toString();
            if (!url.matches("(?i)^https?://.+")) return;
            if (!forcar && !ehCandidatoVideo(url)) return;
            candidatos.add(url);
        } catch (Exception ignored) {
        }
    }

    private boolean ehCandidatoVideo(String url) {
        String u = String.valueOf(url).toLowerCase(Locale.ROOT);
        return u.contains(".mp4") || u.contains(".m3u8") || u.contains(".mpd") ||
                u.contains("player") || u.contains("video") || u.contains("embed") || u.contains("stream");
    }

    private File pastaVideos() {
        return new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "ExtratorVideos"
        );
    }

    private void mostrarOpcaoAbrirPasta() {
        buttonOpenFolder.setVisibility(View.VISIBLE);

        new AlertDialog.Builder(this)
                .setTitle("Download concluído")
                .setMessage("O vídeo foi salvo em Downloads/ExtratorVideos.")
                .setPositiveButton("ABRIR PASTA", (dialog, which) -> abrirPastaVideos())
                .setNegativeButton("FECHAR", null)
                .show();
    }

    private void abrirPastaVideos() {
        File pasta = pastaVideos();

        if (!pasta.exists()) {
            Toast.makeText(this, "A pasta ainda não foi criada.", Toast.LENGTH_LONG).show();
            return;
        }

        Uri pastaUri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:Download/ExtratorVideos"
        );

        // Abre o navegador/gerenciador de arquivos do Android já na pasta do app.
        // ACTION_OPEN_DOCUMENT é mais compatível entre Samsung, Motorola, Xiaomi e Android puro
        // do que tentar ACTION_VIEW diretamente em um diretório.
        try {
            Intent navegador = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            navegador.addCategory(Intent.CATEGORY_OPENABLE);
            navegador.setType("video/*");
            navegador.putExtra(DocumentsContract.EXTRA_INITIAL_URI, pastaUri);
            navegador.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(navegador);
            return;
        } catch (Exception ignored) {
        }

        // Fallback: abre o seletor de pastas do sistema apontando para ExtratorVideos.
        try {
            Intent arvore = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            arvore.putExtra(DocumentsContract.EXTRA_INITIAL_URI, pastaUri);
            arvore.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(arvore);
            return;
        } catch (Exception ignored) {
        }

        // Último fallback: abre o gerenciador de arquivos sem forçar a subpasta.
        try {
            Intent arquivos = new Intent(Intent.ACTION_GET_CONTENT);
            arquivos.addCategory(Intent.CATEGORY_OPENABLE);
            arquivos.setType("video/*");
            startActivity(arquivos);
        } catch (Exception falha) {
            Toast.makeText(
                    this,
                    "Não foi possível abrir o gerenciador. A pasta é Downloads/ExtratorVideos.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void selecionarVideoParaCorte() {
        if (!ready || downloading || cutting) return;

        File pasta = pastaVideos();
        if (!pasta.exists()) {
            Toast.makeText(this, "A pasta Downloads/ExtratorVideos ainda não existe.", Toast.LENGTH_LONG).show();
            return;
        }

        File[] arquivos = pasta.listFiles(file -> {
            if (file == null || !file.isFile()) return false;
            String nome = file.getName().toLowerCase(Locale.ROOT);
            return nome.endsWith(".mp4") || nome.endsWith(".mkv") || nome.endsWith(".webm") ||
                    nome.endsWith(".mov") || nome.endsWith(".m4v");
        });

        if (arquivos == null || arquivos.length == 0) {
            Toast.makeText(this, "Nenhum vídeo encontrado em Downloads/ExtratorVideos.", Toast.LENGTH_LONG).show();
            return;
        }

        Arrays.sort(arquivos, Comparator.comparingLong(File::lastModified).reversed());
        String[] nomes = new String[arquivos.length];
        for (int i = 0; i < arquivos.length; i++) {
            nomes[i] = arquivos[i].getName();
        }

        new AlertDialog.Builder(this)
                .setTitle("Escolha o vídeo")
                .setItems(nomes, (dialog, which) -> carregarVideoParaCorte(arquivos[which]))
                .setNegativeButton("CANCELAR", null)
                .show();
    }

    private void carregarVideoParaCorte(File arquivo) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(arquivo.getAbsolutePath());
            String duracao = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            selectedVideoDurationMs = duracao == null ? 0L : Long.parseLong(duracao);
            selectedVideo = arquivo;

            pararPrevia(false);
            liberarPlayerPreview();
            textSelectedVideo.setText(arquivo.getName());
            textCutDuration.setText("Duração: " + formatarTempo(selectedVideoDurationMs));
            editCutStart.setText(formatarTempo(0L));
            editCutEnd.setText(formatarTempo(selectedVideoDurationMs));

            videoPreview.setVisibility(View.VISIBLE);
            textPreviewHint.setVisibility(View.GONE);
            rangeSelector.setVisibility(View.VISIBLE);
            textPreviewPosition.setVisibility(View.VISIBLE);

            rangeSelector.setDuration(selectedVideoDurationMs);
            rangeSelector.setSelection(0L, selectedVideoDurationMs);
            rangeSelector.setPlayhead(0L);
            rangeSelector.setEnabled(selectedVideoDurationMs > 0);
            atualizarPosicaoPreview(0L);

            buttonPreview.setEnabled(selectedVideoDurationMs > 0);
            buttonCutVideo.setEnabled(selectedVideoDurationMs > 0);
            textCutStatus.setText("Arraste as bolinhas para início/fim. Toque fora delas para mover somente a posição atual.");

            // Prepara a imagem inicial para que a área de prévia não fique apenas preta.
            prepararFramePreview(0L);
        } catch (Exception e) {
            selectedVideo = null;
            selectedVideoDurationMs = 0L;
            pararPrevia(false);
            liberarPlayerPreview();
            videoPreview.setVisibility(View.GONE);
            textPreviewHint.setVisibility(View.VISIBLE);
            rangeSelector.setVisibility(View.GONE);
            textPreviewPosition.setVisibility(View.GONE);
            buttonPreview.setEnabled(false);
            buttonCutVideo.setEnabled(false);
            textCutStatus.setText("Não foi possível ler esse vídeo: " + mensagemCurta(e));
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private long[] obterIntervaloSelecionado() {
        long inicioMs = parseTempoParaMillis(editCutStart.getText().toString());
        long fimMs = parseTempoParaMillis(editCutEnd.getText().toString());

        if (inicioMs < 0 || fimMs <= inicioMs) {
            throw new IllegalArgumentException("O tempo final precisa ser maior que o inicial.");
        }
        if (selectedVideoDurationMs > 0 && fimMs > selectedVideoDurationMs + 1000L) {
            throw new IllegalArgumentException("O tempo final ultrapassa a duração do vídeo.");
        }

        if (rangeSelector != null && selectedVideoDurationMs > 0) {
            rangeSelector.setDuration(selectedVideoDurationMs);
            rangeSelector.setSelection(inicioMs, Math.min(fimMs, selectedVideoDurationMs));
        }
        return new long[]{inicioMs, fimMs};
    }

    private void sincronizarCursoresComCampos() {
        if (!ready || downloading || cutting || selectedVideo == null || selectedVideoDurationMs <= 0 || rangeSelector == null) return;
        try {
            long inicioMs = parseTempoParaMillis(editCutStart.getText().toString());
            long fimMs = parseTempoParaMillis(editCutEnd.getText().toString());
            if (inicioMs < 0 || fimMs <= inicioMs || fimMs > selectedVideoDurationMs + 1000L) return;

            rangeSelector.setDuration(selectedVideoDurationMs);
            rangeSelector.setSelection(inicioMs, Math.min(fimMs, selectedVideoDurationMs));
        } catch (Exception ignored) {
        }
    }

    private void alternarPreviaTrecho() {
        if (previewing) {
            pararPrevia(false);
            return;
        }
        if (!ready || downloading || cutting || selectedVideo == null) return;

        try {
            long[] intervalo = obterIntervaloSelecionado();
            previewStartMs = intervalo[0];
            previewEndMs = intervalo[1];
            previewStartPending = true;

            videoPreview.setVisibility(View.VISIBLE);
            textPreviewHint.setVisibility(View.GONE);
            rangeSelector.setVisibility(View.VISIBLE);
            textPreviewPosition.setVisibility(View.VISIBLE);

            buttonPreview.setText("Carregando prévia...");
            buttonPreview.setEnabled(false);
            textCutStatus.setText("Preparando vídeo de " + formatarTempo(previewStartMs) + " até " + formatarTempo(previewEndMs) + "...");

            if (previewSurfaceReady) {
                iniciarPlayerPreviewPendente();
            }
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível reproduzir a prévia.", Toast.LENGTH_LONG).show();
            textCutStatus.setText("Erro na prévia: " + mensagemCurta(e));
            pararPrevia(false);
        }
    }

    private void iniciarPlayerPreviewPendente() {
        if (!previewStartPending || selectedVideo == null || !previewSurfaceReady) return;
        previewStartPending = false;
        liberarPlayerPreview();

        try {
            SurfaceTexture texture = videoPreview.getSurfaceTexture();
            if (texture == null) {
                previewStartPending = true;
                return;
            }

            previewPlayer = new MediaPlayer();
            Surface surface = new Surface(texture);
            previewPlayer.setSurface(surface);
            surface.release();
            previewPlayer.setDataSource(selectedVideo.getAbsolutePath());
            previewPlayer.setOnPreparedListener(mp -> {
                previewPrepared = true;
                mp.setOnSeekCompleteListener(this::iniciarReproducaoPreview);
                try {
                    mp.seekTo(previewStartMs, MediaPlayer.SEEK_CLOSEST);
                    atualizarPosicaoPreview(previewStartMs);
                    // Alguns aparelhos não chamam onSeekComplete quando o ponto é exatamente 00:00.
                    previewHandler.postDelayed(() -> iniciarReproducaoPreview(mp), 300L);
                } catch (Exception e) {
                    textCutStatus.setText("Erro ao posicionar prévia: " + mensagemCurta(e));
                    pararPrevia(false);
                }
            });
            previewPlayer.setOnCompletionListener(mp -> pararPrevia(true));
            previewPlayer.setOnErrorListener((mp, what, extra) -> {
                textCutStatus.setText("Não foi possível reproduzir este formato de vídeo.");
                pararPrevia(false);
                return true;
            });
            previewPlayer.prepareAsync();
        } catch (Exception e) {
            textCutStatus.setText("Erro na prévia: " + mensagemCurta(e));
            pararPrevia(false);
        }
    }

    private void iniciarReproducaoPreview(MediaPlayer player) {
        if (player == null || previewPlayer != player || !previewPrepared || previewing || cutting) return;
        try {
            player.setVolume(1f, 1f);
            player.start();
            previewing = true;
            buttonPreview.setText("■ Parar prévia");
            buttonPreview.setEnabled(true);
            textCutStatus.setText("Pré-visualizando de " + formatarTempo(previewStartMs) + " até " + formatarTempo(previewEndMs) + ".");
            previewHandler.removeCallbacks(previewMonitor);
            previewHandler.post(previewMonitor);
        } catch (Exception e) {
            textCutStatus.setText("Erro ao iniciar prévia: " + mensagemCurta(e));
            pararPrevia(false);
        }
    }

    private void reposicionarPreviaSemPausar(long posicaoMs) {
        if (selectedVideo == null || selectedVideoDurationMs <= 0L) return;
        long pos = Math.max(0L, Math.min(selectedVideoDurationMs, posicaoMs));

        if (previewPlayer == null || !previewPrepared) {
            atualizarPosicaoPreview(pos);
            prepararFramePreview(pos);
            return;
        }

        try {
            // Mantém o fim do trecho quando o salto fica dentro dele.
            // Se o usuário pular para depois do cursor de fim, continua até o fim do vídeo.
            long selectedEnd = rangeSelector == null ? previewEndMs : rangeSelector.getEndMs();
            previewEndMs = (selectedEnd > 0L && pos < selectedEnd) ? selectedEnd : selectedVideoDurationMs;
            previewPlayer.setOnSeekCompleteListener(mp -> {
                try {
                    if (previewing && !mp.isPlaying()) mp.start();
                    atualizarPosicaoPreview(mp.getCurrentPosition());
                } catch (Exception ignored) { }
            });
            previewPlayer.seekTo(pos, MediaPlayer.SEEK_CLOSEST);
            if (!previewPlayer.isPlaying()) previewPlayer.start();
            previewing = true;
            buttonPreview.setText("■ Parar prévia");
            buttonPreview.setEnabled(true);
            textCutStatus.setText("Prévia reposicionada para " + formatarTempo(pos) + " • reprodução contínua");
            previewHandler.removeCallbacks(previewMonitor);
            previewHandler.post(previewMonitor);
        } catch (Exception e) {
            previewTimelineSeeking = false;
            textCutStatus.setText("Não foi possível reposicionar a prévia: " + mensagemCurta(e));
        }
    }

    private void prepararFramePreview(long posicaoMs) {
        if (selectedVideo == null || !previewSurfaceReady) return;
        liberarPlayerPreview();

        try {
            SurfaceTexture texture = videoPreview.getSurfaceTexture();
            if (texture == null) return;

            previewPlayer = new MediaPlayer();
            Surface surface = new Surface(texture);
            previewPlayer.setSurface(surface);
            surface.release();
            previewPlayer.setVolume(0f, 0f);
            previewPlayer.setDataSource(selectedVideo.getAbsolutePath());
            previewPlayer.setOnPreparedListener(mp -> {
                previewPrepared = true;
                try {
                    mp.setOnSeekCompleteListener(player -> {
                        // Um toque muito curto de reprodução força a renderização do quadro no TextureView.
                        try {
                            player.start();
                            previewHandler.postDelayed(() -> {
                                try {
                                    if (previewPlayer == player && !previewing) {
                                        player.pause();
                                        player.setVolume(1f, 1f);
                                    }
                                } catch (Exception ignored) {
                                }
                            }, 90L);
                        } catch (Exception ignored) {
                        }
                    });
                    mp.seekTo(posicaoMs, MediaPlayer.SEEK_CLOSEST);
                    atualizarPosicaoPreview(posicaoMs);
                } catch (Exception ignored) {
                }
            });
            previewPlayer.setOnErrorListener((mp, what, extra) -> true);
            previewPlayer.prepareAsync();
        } catch (Exception ignored) {
        }
    }

    private void atualizarPosicaoPreview(long posicaoMs) {
        long pos = Math.max(0L, Math.min(selectedVideoDurationMs, posicaoMs));
        if (rangeSelector != null) {
            rangeSelector.setPlayhead(pos);
        }
        if (textPreviewPosition != null) {
            textPreviewPosition.setText(formatarTempo(pos) + " / " + formatarTempo(selectedVideoDurationMs));
        }
    }

    private long posicaoAtualPreview() {
        if (previewPlayer != null && previewPrepared) {
            try {
                return previewPlayer.getCurrentPosition();
            } catch (Exception ignored) {
            }
        }
        return rangeSelector == null ? 0L : rangeSelector.getPlayheadMs();
    }

    private void pararPrevia(boolean voltarAoInicio) {
        previewTimelineSeeking = false;
        previewing = false;
        previewStartPending = false;
        previewHandler.removeCallbacks(previewMonitor);

        long posDestino = voltarAoInicio ? previewStartMs : posicaoAtualPreview();
        if (previewPlayer != null && previewPrepared) {
            try {
                if (previewPlayer.isPlaying()) previewPlayer.pause();
            } catch (Exception ignored) {
            }
        }
        atualizarPosicaoPreview(posDestino);
        if (buttonPreview != null) {
            buttonPreview.setText("▶ Pré-visualizar trecho");
            buttonPreview.setEnabled(ready && !downloading && !cutting && selectedVideo != null && selectedVideoDurationMs > 0);
        }
    }

    private void liberarPlayerPreview() {
        previewTimelineSeeking = false;
        previewHandler.removeCallbacks(previewMonitor);
        previewPrepared = false;
        previewing = false;
        if (previewPlayer != null) {
            try { previewPlayer.reset(); } catch (Exception ignored) { }
            try { previewPlayer.release(); } catch (Exception ignored) { }
            previewPlayer = null;
        }
    }

    private void cortarVideoSelecionado() {
        if (!ready || downloading || cutting || selectedVideo == null) return;

        final long[] intervalo;
        try {
            intervalo = obterIntervaloSelecionado();
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        pararPrevia(false);
        cutting = true;
        buttonCutVideo.setEnabled(false);
        buttonPreview.setEnabled(false);
        rangeSelector.setEnabled(false);
        buttonSelectVideo.setEnabled(false);
        buttonDownload.setEnabled(false);
        buttonUpdateYtdlp.setEnabled(false);
        if (editCutOutputName != null) editCutOutputName.setEnabled(false);
        progressCut.setVisibility(View.VISIBLE);
        textCutStatus.setText("Cortando com sincronização precisa de áudio e vídeo...");

        final File entrada = selectedVideo;
        String requestedName = editCutOutputName == null ? "" : editCutOutputName.getText().toString();
        executarCortePreciso(entrada, intervalo[0], intervalo[1], requestedName);
    }

    private void executarCortePreciso(File entrada, long inicioMs, long fimMs, String requestedName) {
        try {
            String nome = entrada.getName();
            int ponto = nome.lastIndexOf('.');
            String base = ponto > 0 ? nome.substring(0, ponto) : nome;
            base = base.replaceAll("[\\/:*?\"<>|]", "_");

            String outputFileName = buildOutputFileName(requestedName, base + "_corte");
            File saida = uniqueOutputFile(entrada.getParentFile(), outputFileName);

            cutOutputPending = saida;

            MediaItem.ClippingConfiguration corte =
                    new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(inicioMs)
                            .setEndPositionMs(fimMs)
                            .build();

            MediaItem item = new MediaItem.Builder()
                    .setUri(Uri.fromFile(entrada))
                    .setClippingConfiguration(corte)
                    .build();

            EditedMediaItem itemEditado = new EditedMediaItem.Builder(item).build();

            // Não habilitamos MP4 edit-list nem o antigo corte por keyframe.
            // O Transformer faz a exportação normal para respeitar o instante
            // escolhido e manter áudio/vídeo alinhados desde o primeiro quadro.
            cutTransformer = new Transformer.Builder(this)
                    .addListener(new Transformer.Listener() {
                        @Override
                        public void onCompleted(Composition composition, ExportResult exportResult) {
                            File arquivoFinal = cutOutputPending;
                            cutTransformer = null;
                            cutOutputPending = null;

                            if (arquivoFinal == null || !arquivoFinal.exists() || arquivoFinal.length() == 0) {
                                textCutStatus.setText("Erro ao cortar: o arquivo final não foi criado.");
                                Toast.makeText(MainActivity.this, "Não foi possível criar o vídeo cortado.", Toast.LENGTH_LONG).show();
                                finalizarEstadoCorte();
                                return;
                            }

                            MediaScannerConnection.scanFile(
                                    MainActivity.this,
                                    new String[]{arquivoFinal.getAbsolutePath()},
                                    new String[]{"video/mp4"},
                                    null
                            );
                            concluirCorte(arquivoFinal);
                        }

                        @Override
                        public void onError(
                                Composition composition,
                                ExportResult exportResult,
                                ExportException exportException
                        ) {
                            File parcial = cutOutputPending;
                            if (parcial != null && parcial.exists()) parcial.delete();
                            cutTransformer = null;
                            cutOutputPending = null;
                            textCutStatus.setText("Erro ao cortar: " + mensagemCurta(exportException));
                            Toast.makeText(MainActivity.this, "Não foi possível cortar o vídeo.", Toast.LENGTH_LONG).show();
                            finalizarEstadoCorte();
                        }
                    })
                    .build();

            cutTransformer.start(itemEditado, saida.getAbsolutePath());

        } catch (Exception e) {
            File parcial = cutOutputPending;
            if (parcial != null && parcial.exists()) parcial.delete();
            cutTransformer = null;
            cutOutputPending = null;
            textCutStatus.setText("Erro ao cortar: " + mensagemCurta(e));
            Toast.makeText(this, "Não foi possível cortar o vídeo.", Toast.LENGTH_LONG).show();
            finalizarEstadoCorte();
        }
    }

    private void concluirCorte(File arquivoFinal) {
        Toast.makeText(this, "Vídeo cortado e salvo com sucesso", Toast.LENGTH_LONG).show();
        selectedVideo = arquivoFinal;
        carregarVideoParaCorte(arquivoFinal);
        textCutStatus.setText("✓ Corte salvo: " + arquivoFinal.getName());
        finalizarEstadoCorte();
        buttonOpenFolder.setVisibility(View.VISIBLE);

        new AlertDialog.Builder(this)
                .setTitle("Corte concluído")
                .setMessage("O novo vídeo foi salvo em Downloads/ExtratorVideos. O arquivo original foi mantido.")
                .setPositiveButton("ABRIR PASTA", (dialog, which) -> abrirPastaVideos())
                .setNegativeButton("FECHAR", null)
                .show();
    }

    private void finalizarEstadoCorte() {
        cutting = false;
        progressCut.setVisibility(View.GONE);
        buttonSelectVideo.setEnabled(ready);
        buttonPreview.setEnabled(ready && selectedVideo != null && selectedVideoDurationMs > 0);
        buttonCutVideo.setEnabled(ready && selectedVideo != null && selectedVideoDurationMs > 0);
        rangeSelector.setEnabled(ready && selectedVideo != null && selectedVideoDurationMs > 0);
        buttonDownload.setEnabled(ready && !downloading);
        buttonUpdateYtdlp.setEnabled(ready && !downloading);
        if (editCutOutputName != null) editCutOutputName.setEnabled(true);
    }

    private String buildOutputFileName(String requestedName, String fallbackPrefix) {
        String name = requestedName == null ? "" : requestedName.trim();
        if (name.toLowerCase(Locale.ROOT).endsWith(".mp4")) {
            name = name.substring(0, name.length() - 4);
        }
        name = name.replace('\\', '_')
                .replace('/', '_')
                .replace(':', '_')
                .replace('*', '_')
                .replace('?', '_')
                .replace('"', '_')
                .replace('<', '_')
                .replace('>', '_')
                .replace('|', '_')
                .trim();
        name = name.replaceAll("[. ]+$", "");
        if (name.isEmpty()) {
            name = fallbackPrefix + "_" + System.currentTimeMillis();
        }
        return name + ".mp4";
    }

    private File uniqueOutputFile(File folder, String fileName) {
        File candidate = new File(folder, fileName);
        if (!candidate.exists()) return candidate;

        int point = fileName.toLowerCase(Locale.ROOT).endsWith(".mp4")
                ? fileName.length() - 4 : fileName.length();
        String base = fileName.substring(0, point);
        String extension = point < fileName.length() ? fileName.substring(point) : ".mp4";
        int index = 2;
        while (candidate.exists()) {
            candidate = new File(folder, base + "_" + index + extension);
            index++;
        }
        return candidate;
    }

    private long parseTempoParaMillis(String texto) {
        String valor = texto == null ? "" : texto.trim().replace(',', '.');
        if (valor.isEmpty()) throw new IllegalArgumentException("Informe o tempo inicial e final.");

        String[] partes = valor.split(":");
        if (partes.length < 1 || partes.length > 3) {
            throw new IllegalArgumentException("Use HH:MM:SS.mmm.");
        }

        try {
            long horas = 0L;
            long minutos = 0L;
            double segundos;
            if (partes.length == 3) {
                horas = Long.parseLong(partes[0]);
                minutos = Long.parseLong(partes[1]);
                segundos = Double.parseDouble(partes[2]);
            } else if (partes.length == 2) {
                minutos = Long.parseLong(partes[0]);
                segundos = Double.parseDouble(partes[1]);
            } else {
                segundos = Double.parseDouble(partes[0]);
            }
            if (horas < 0 || minutos < 0 || minutos >= 60 || segundos < 0 || segundos >= 60) {
                throw new NumberFormatException();
            }
            return Math.round(((horas * 3600.0) + (minutos * 60.0) + segundos) * 1000.0);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Tempo inválido. Use HH:MM:SS.mmm.");
        }
    }

    private String formatarTempo(long millis) {
        long safe = Math.max(0L, millis);
        long horas = safe / 3_600_000L;
        long minutos = (safe % 3_600_000L) / 60_000L;
        long segundos = (safe % 60_000L) / 1000L;
        long ms = safe % 1000L;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d.%03d", horas, minutos, segundos, ms);
    }

    private void ajustarPosicaoAtual(long deltaMs) {
        if (!ready || downloading || cutting || selectedVideo == null || selectedVideoDurationMs <= 0 || rangeSelector == null) return;
        long atual = rangeSelector.getPlayheadMs();
        long novo = Math.max(0L, Math.min(selectedVideoDurationMs, atual + deltaMs));
        if (previewing) {
            reposicionarPreviaSemPausar(novo);
        } else {
            atualizarPosicaoPreview(novo);
            prepararFramePreview(novo);
            textCutStatus.setText("Posição atual: " + formatarTempo(novo));
        }
    }

    private void marcarInicioNaPosicaoAtual() {
        if (!ready || downloading || cutting || selectedVideo == null || rangeSelector == null) return;
        long atual = rangeSelector.getPlayheadMs();
        long fim = rangeSelector.getEndMs();
        if (atual >= fim) {
            Toast.makeText(this, "O início precisa ficar antes do fim.", Toast.LENGTH_LONG).show();
            return;
        }
        rangeSelector.setSelection(atual, fim);
        editCutStart.setText(formatarTempo(atual));
        textCutStatus.setText("Início marcado em " + formatarTempo(atual));
    }

    private void marcarFimNaPosicaoAtual() {
        if (!ready || downloading || cutting || selectedVideo == null || rangeSelector == null) return;
        long atual = rangeSelector.getPlayheadMs();
        long inicio = rangeSelector.getStartMs();
        if (atual <= inicio) {
            Toast.makeText(this, "O fim precisa ficar depois do início.", Toast.LENGTH_LONG).show();
            return;
        }
        rangeSelector.setSelection(inicio, atual);
        editCutEnd.setText(formatarTempo(atual));
        textCutStatus.setText("Fim marcado em " + formatarTempo(atual));
    }

    private String formatoParaQualidade(int posicao) {
        switch (posicao) {
            case 0:
                return "bv*[height<=360][ext=mp4]+ba[ext=m4a]/b[height<=360][ext=mp4]/bv*[height<=360]+ba/b[height<=360]/b";
            case 1:
                return "bv*[height<=480][ext=mp4]+ba[ext=m4a]/b[height<=480][ext=mp4]/bv*[height<=480]+ba/b[height<=480]/b";
            case 2:
                return "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4]/bv*[height<=720]+ba/b[height<=720]/b";
            case 3:
                return "bv*[height<=1080][ext=mp4]+ba[ext=m4a]/b[height<=1080][ext=mp4]/bv*[height<=1080]+ba/b[height<=1080]/b";
            default:
                return "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b";
        }
    }

    private String formatoCompatibilidade(int posicao) {
        switch (posicao) {
            case 0: return "b[height<=360][ext=mp4]/b[height<=360]/b";
            case 1: return "b[height<=480][ext=mp4]/b[height<=480]/b";
            case 2: return "b[height<=720][ext=mp4]/b[height<=720]/b";
            case 3: return "b[height<=1080][ext=mp4]/b[height<=1080]/b";
            default: return "b[ext=mp4]/b";
        }
    }

    private boolean pareceFalhaDeFormatoOu403(Exception e) {
        String m = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
        return m.contains("403") ||
                m.contains("forbidden") ||
                m.contains("requested format") ||
                m.contains("format is not available") ||
                m.contains("unable to download video data");
    }

    private void cancelarDownload() {
        if (!downloading || processId == null) return;
        try {
            YoutubeDL.getInstance().destroyProcessById(processId);
            setStatus("Download cancelado.");
        } catch (Exception e) {
            setStatus("Não foi possível cancelar: " + mensagemCurta(e));
        }
        finalizarEstadoDownload();
    }

    private void finalizarEstadoDownload() {
        downloading = false;
        processId = null;
        buttonDownload.setEnabled(ready && !cutting);
        buttonSelectVideo.setEnabled(ready && !cutting);
        buttonPreview.setEnabled(ready && !cutting && selectedVideo != null && selectedVideoDurationMs > 0);
        buttonCutVideo.setEnabled(ready && !cutting && selectedVideo != null && selectedVideoDurationMs > 0);
        rangeSelector.setEnabled(ready && !cutting && selectedVideo != null && selectedVideoDurationMs > 0);
        buttonCancel.setVisibility(View.GONE);
    }

    private void setStatus(String texto) {
        textStatus.setText(texto == null ? "" : texto);
    }

    private String limparLinhaStatus(String line) {
        String s = line.replaceAll("\\x1B\\[[;\\d]*m", "").trim();
        if (s.length() > 180) s = s.substring(0, 180) + "…";
        return s;
    }

    private String mensagemCurta(Exception e) {
        String msg = e == null ? "" : String.valueOf(e.getMessage());
        msg = msg.replace('\n', ' ').replace('\r', ' ').trim();
        if (msg.length() > 220) msg = msg.substring(0, 220) + "…";
        return msg;
    }

    @Override
    protected void onDestroy() {
        if (downloading && processId != null) {
            try {
                YoutubeDL.getInstance().destroyProcessById(processId);
            } catch (Exception ignored) {
            }
        }
        if (cutTransformer != null) {
            try {
                cutTransformer.cancel();
            } catch (Exception ignored) {
            }
            cutTransformer = null;
        }
        if (cutOutputPending != null && cutting && cutOutputPending.exists()) {
            try { cutOutputPending.delete(); } catch (Exception ignored) { }
        }
        cutOutputPending = null;
        previewing = false;
        previewHandler.removeCallbacks(previewMonitor);
        liberarPlayerPreview();
        executor.shutdownNow();
        super.onDestroy();
    }
}
