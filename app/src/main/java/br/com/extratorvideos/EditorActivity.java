package br.com.extratorvideos;

import br.com.centralmidia.android.R;
import br.com.centralmidia.android.ui.MobileScaffold;
import br.com.centralmidia.android.ui.VideoEditorActivity;

import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.AudioEncoderSettings;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.VideoEncoderSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@UnstableApi
public class EditorActivity extends AppCompatActivity {

    private Button buttonBack;
    private Button buttonAdd;
    private Button buttonUp;
    private Button buttonDown;
    private Button buttonRemove;
    private Button buttonDuplicate;
    private Button buttonCut;
    private Button buttonApplyTimes;
    private Button buttonPreview;
    private Button buttonJoin;
    private Button buttonBack5s;
    private Button buttonForward5s;
    private Button buttonZoomOut;
    private Button buttonZoomIn;
    private Button back1s;
    private Button back100ms;
    private Button back10ms;
    private Button forward10ms;
    private Button forward100ms;
    private Button forward1s;
    private Button markStart;
    private Button markEnd;

    private EditText editStart;
    private EditText editEnd;
    private EditText editOutputName;
    private Spinner spinnerResolution;
    private Spinner spinnerCompression;
    private CheckBox checkTargetSize;
    private SeekBar seekTargetSize;
    private LinearLayout panelTargetSize;
    private TextView textTargetSize;
    private TextView textTargetMin;
    private TextView textTargetMax;
    private TextView textExportEstimate;
    private TextView textSelected;
    private TextView textInfo;
    private TextView textPosition;
    private TextView textStatus;
    private TextView textJoinProgress;
    private TextView textSequenceCurrent;
    private TextView textSequenceTotal;
    private TextView textTimelineZoom;
    private TextView textClipCount;

    private TextureView preview;
    private RangeSelectionView range;
    private TimelineEditorView timeline;
    private HorizontalScrollView timelineScroll;
    private ProgressBar progressJoin;

    private final ArrayList<ClipItem> clips = new ArrayList<>();
    private int selectedIndex = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private boolean surfaceReady = false;
    private boolean prepared = false;
    private boolean sequencePlaying = false;
    private boolean timelineSeeking = false;
    private boolean localRangeSeeking = false;
    private boolean advancingClip = false;
    private int previewClipIndex = -1;
    private long globalPlayheadMs = 0L;
    private long previewClipEndSourceMs = 0L;

    private final float[] zoomLevels = new float[]{0.45f, 0.65f, 0.9f, 1.2f, 1.8f, 2.8f, 4.8f};
    private int zoomIndex = 2;

    private static final int AUDIO_BITRATE_BPS = 128_000;
    private int targetSizeMinMb = 1;
    private int targetSizeMaxMb = 100;
    private int targetSizeMb = 30;
    private boolean updatingExportUi = false;
    private String lastExportCodecLabel = "H.264 / AVC";

    private Transformer joinTransformer;
    private File joinOutput;
    private boolean joining = false;
    private final ProgressHolder progressHolder = new ProgressHolder();

    private final Runnable previewMonitor = new Runnable() {
        @Override
        public void run() {
            if (player == null || !prepared) return;
            try {
                long sourcePos = player.getCurrentPosition();
                ClipItem clip = clipAt(previewClipIndex);
                if (clip != null && !timelineSeeking && !localRangeSeeking) {
                    long local = Math.max(0L, Math.min(clip.selectedDurationMs(), sourcePos - clip.startMs));
                    long global = clipGlobalStart(previewClipIndex) + local;
                    updateGlobalPlayhead(global, true);
                    if (selectedIndex == previewClipIndex) {
                        range.setPlayhead(Math.max(clip.startMs, Math.min(clip.endMs, sourcePos)));
                        updateLocalPosition(sourcePos);
                    }
                }

                if (sequencePlaying && clip != null && !advancingClip && sourcePos >= previewClipEndSourceMs - 35L) {
                    advanceToNextClip();
                    return;
                }
            } catch (Exception ignored) {
            }
            if (player != null && prepared) handler.postDelayed(this, 55L);
        }
    };

    private final Runnable joinProgressMonitor = new Runnable() {
        @Override
        public void run() {
            if (!joining || joinTransformer == null) return;
            try {
                int state = joinTransformer.getProgress(progressHolder);
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    int p = Math.max(0, Math.min(100, progressHolder.progress));
                    progressJoin.setProgress(p);
                    textJoinProgress.setText(p + "%");
                    textStatus.setText(p < 95 ? "Renderizando sequência..." : "Finalizando arquivo...");
                } else if (state == Transformer.PROGRESS_STATE_UNAVAILABLE) {
                    textStatus.setText("Renderizando sequência...");
                }
            } catch (Exception ignored) {
            }
            if (joining) handler.postDelayed(this, 400L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        bindViews();
        bindActions();
        configurePreview();
        configureTimeline();
        configureRangeEditor();
        configureExportSettings();

        clearSelectionUi();
        refreshTimeline(false);
        MobileScaffold.attachModule(this, VideoEditorActivity.class);
    }

    private void bindViews() {
        buttonBack = findViewById(R.id.buttonEditorBack);
        buttonAdd = findViewById(R.id.buttonEditorAdd);
        buttonUp = findViewById(R.id.buttonEditorUp);
        buttonDown = findViewById(R.id.buttonEditorDown);
        buttonRemove = findViewById(R.id.buttonEditorRemove);
        buttonDuplicate = findViewById(R.id.buttonEditorDuplicate);
        buttonCut = findViewById(R.id.buttonEditorCut);
        buttonApplyTimes = findViewById(R.id.buttonEditorApplyTimes);
        buttonPreview = findViewById(R.id.buttonEditorPreview);
        buttonJoin = findViewById(R.id.buttonEditorJoin);
        buttonBack5s = findViewById(R.id.editorBack5s);
        buttonForward5s = findViewById(R.id.editorForward5s);
        buttonZoomOut = findViewById(R.id.editorZoomOut);
        buttonZoomIn = findViewById(R.id.editorZoomIn);
        back1s = findViewById(R.id.editorBack1s);
        back100ms = findViewById(R.id.editorBack100ms);
        back10ms = findViewById(R.id.editorBack10ms);
        forward10ms = findViewById(R.id.editorForward10ms);
        forward100ms = findViewById(R.id.editorForward100ms);
        forward1s = findViewById(R.id.editorForward1s);
        markStart = findViewById(R.id.editorMarkStart);
        markEnd = findViewById(R.id.editorMarkEnd);

        editStart = findViewById(R.id.editEditorStart);
        editEnd = findViewById(R.id.editEditorEnd);
        editOutputName = findViewById(R.id.editEditorOutputName);
        spinnerResolution = findViewById(R.id.spinnerEditorResolution);
        spinnerCompression = findViewById(R.id.spinnerEditorCompression);
        checkTargetSize = findViewById(R.id.checkEditorTargetSize);
        seekTargetSize = findViewById(R.id.seekEditorTargetSize);
        panelTargetSize = findViewById(R.id.panelEditorTargetSize);
        textTargetSize = findViewById(R.id.textEditorTargetSize);
        textTargetMin = findViewById(R.id.textEditorTargetMin);
        textTargetMax = findViewById(R.id.textEditorTargetMax);
        textExportEstimate = findViewById(R.id.textEditorExportEstimate);
        textSelected = findViewById(R.id.textEditorSelected);
        textInfo = findViewById(R.id.textEditorInfo);
        textPosition = findViewById(R.id.textEditorPosition);
        textStatus = findViewById(R.id.textEditorStatus);
        textJoinProgress = findViewById(R.id.textEditorJoinProgress);
        textSequenceCurrent = findViewById(R.id.textEditorSequenceCurrent);
        textSequenceTotal = findViewById(R.id.textEditorSequenceTotal);
        textTimelineZoom = findViewById(R.id.textEditorTimelineZoom);
        textClipCount = findViewById(R.id.textEditorClipCount);

        preview = findViewById(R.id.editorPreview);
        range = findViewById(R.id.editorRangeSelector);
        timeline = findViewById(R.id.editorTimeline);
        timelineScroll = findViewById(R.id.editorTimelineScroll);
        progressJoin = findViewById(R.id.progressEditorJoin);
    }

    private void bindActions() {
        buttonBack.setOnClickListener(v -> finish());
        buttonAdd.setOnClickListener(v -> showAddVideosDialog());
        buttonUp.setOnClickListener(v -> moveSelected(-1));
        buttonDown.setOnClickListener(v -> moveSelected(1));
        buttonRemove.setOnClickListener(v -> removeSelected());
        buttonDuplicate.setOnClickListener(v -> duplicateSelected());
        buttonCut.setOnClickListener(v -> cutAtPlayhead());
        buttonApplyTimes.setOnClickListener(v -> applyTimesFromFields(true));
        buttonPreview.setOnClickListener(v -> toggleSequencePlayback());
        buttonJoin.setOnClickListener(v -> joinVideos());

        buttonBack5s.setOnClickListener(v -> seekGlobalBy(-5000L));
        buttonForward5s.setOnClickListener(v -> seekGlobalBy(5000L));
        buttonZoomOut.setOnClickListener(v -> changeZoom(-1));
        buttonZoomIn.setOnClickListener(v -> changeZoom(1));

        back1s.setOnClickListener(v -> nudgeLocal(-1000L));
        back100ms.setOnClickListener(v -> nudgeLocal(-100L));
        back10ms.setOnClickListener(v -> nudgeLocal(-10L));
        forward10ms.setOnClickListener(v -> nudgeLocal(10L));
        forward100ms.setOnClickListener(v -> nudgeLocal(100L));
        forward1s.setOnClickListener(v -> nudgeLocal(1000L));
        markStart.setOnClickListener(v -> markStartHere());
        markEnd.setOnClickListener(v -> markEndHere());

        editStart.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) applyTimesFromFields(false);
        });
        editEnd.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) applyTimesFromFields(false);
        });
    }

    private void configureExportSettings() {
        String[] options = new String[]{"Original", "360p", "480p", "720p", "1080p"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_export_item, options);
        adapter.setDropDownViewResource(R.layout.spinner_export_dropdown_item);
        spinnerResolution.setAdapter(adapter);
        spinnerResolution.setSelection(0);

        String[] compressionOptions = new String[]{
                "H.264 / AVC • maior compatibilidade",
                "H.265 / HEVC • alta compressão"
        };
        ArrayAdapter<String> compressionAdapter = new ArrayAdapter<>(this, R.layout.spinner_export_item, compressionOptions);
        compressionAdapter.setDropDownViewResource(R.layout.spinner_export_dropdown_item);
        spinnerCompression.setAdapter(compressionAdapter);
        spinnerCompression.setSelection(1);
        spinnerCompression.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateExportSizeControls(true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        spinnerResolution.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateExportSizeControls(true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        checkTargetSize.setOnCheckedChangeListener((buttonView, isChecked) -> {
            panelTargetSize.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            seekTargetSize.setEnabled(!joining && isChecked);
            updateExportSizeControls(false);
        });

        seekTargetSize.setMax(1000);
        seekTargetSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (updatingExportUi) return;
                targetSizeMb = mapProgressToTargetSize(progress);
                updateTargetSizeLabels();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        panelTargetSize.setVisibility(View.GONE);
        updateExportSizeControls(true);
    }

    private void configurePreview() {
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                surfaceReady = true;
                if (!clips.isEmpty()) seekSequence(globalPlayheadMs, sequencePlaying);
            }

            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                surfaceReady = false;
                releaseMediaPlayer();
                return true;
            }

            @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
        });
    }

    private void configureTimeline() {
        timeline.setPixelsPerSecondDp(zoomLevels[zoomIndex]);
        updateZoomLabel();
        timeline.setListener(new TimelineEditorView.Listener() {
            @Override
            public void onClipSelected(int index) {
                selectClip(index, false);
            }

            @Override
            public void onTimelineSeek(long globalMs, boolean finished) {
                timelineSeeking = !finished;
                updateGlobalPlayhead(globalMs, false);
                if (finished) {
                    boolean keepPlaying = sequencePlaying;
                    timelineSeeking = false;
                    seekSequence(globalMs, keepPlaying);
                }
            }

            @Override
            public void onClipMoveRequested(int fromIndex, int toIndex) {
                if (joining || fromIndex < 0 || toIndex < 0 || fromIndex >= clips.size() || toIndex >= clips.size()) return;
                pauseSequence();
                ClipItem moving = clips.remove(fromIndex);
                clips.add(toIndex, moving);
                selectedIndex = toIndex;
                refreshTimeline(true);
                loadSelectedClipUi(false);
            }
        });
    }

    private void configureRangeEditor() {
        range.setOnRangeChangeListener(new RangeSelectionView.OnRangeChangeListener() {
            @Override
            public void onRangeChanged(long startMs, long endMs, int activeThumb, boolean fromUser) {
                ClipItem clip = currentClip();
                if (clip == null) return;

                if (activeThumb == RangeSelectionView.THUMB_START) {
                    clip.startMs = startMs;
                    editStart.setText(formatTime(startMs));
                    updateLocalPosition(startMs);
                } else if (activeThumb == RangeSelectionView.THUMB_END) {
                    clip.endMs = endMs;
                    editEnd.setText(formatTime(endMs));
                    updateLocalPosition(endMs);
                } else {
                    updateLocalPosition(range.getPlayheadMs());
                    localRangeSeeking = fromUser;
                }
            }

            @Override
            public void onRangeChangeFinished(long startMs, long endMs, int activeThumb) {
                ClipItem clip = currentClip();
                if (clip == null) return;

                if (activeThumb == RangeSelectionView.THUMB_START || activeThumb == RangeSelectionView.THUMB_END) {
                    pauseSequence();
                    clip.startMs = startMs;
                    clip.endMs = endMs;
                    
                    refreshTimeline(false);
                    long source = activeThumb == RangeSelectionView.THUMB_START ? startMs : endMs;
                    long global = clipGlobalStart(selectedIndex) + Math.max(0L, Math.min(clip.selectedDurationMs(), source - clip.startMs));
                    range.setPlayhead(source);
                    updateLocalPosition(source);
                    seekSequence(global, false);
                } else {
                    localRangeSeeking = false;
                    long source = range.getPlayheadMs();
                    long global = clipGlobalStart(selectedIndex) + Math.max(0L, Math.min(clip.selectedDurationMs(), source - clip.startMs));
                    seekSequence(global, sequencePlaying);
                }
                localRangeSeeking = false;
            }
        });
    }

    private void showAddVideosDialog() {
        if (joining) return;
        File folder = videoFolder();
        File[] files = folder.listFiles(file -> {
            if (file == null || !file.isFile()) return false;
            String n = file.getName().toLowerCase(Locale.ROOT);
            return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") ||
                    n.endsWith(".mov") || n.endsWith(".m4v");
        });

        if (files == null || files.length == 0) {
            Toast.makeText(this, "Nenhum vídeo encontrado em Downloads/ExtratorVideos.", Toast.LENGTH_LONG).show();
            return;
        }

        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        String[] names = new String[files.length];
        boolean[] checked = new boolean[files.length];
        for (int i = 0; i < files.length; i++) names[i] = files[i].getName();

        new AlertDialog.Builder(this)
                .setTitle("Adicionar vídeos à timeline")
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("ADICIONAR", (dialog, which) -> {
                    int firstNew = clips.size();
                    int added = 0;
                    for (int i = 0; i < files.length; i++) {
                        if (!checked[i]) continue;
                        ClipItem clip = readClip(files[i]);
                        if (clip != null) {
                            clips.add(clip);
                            added++;
                        }
                    }
                    if (added > 0) {
                        
                        selectedIndex = firstNew;
                        globalPlayheadMs = clipGlobalStart(selectedIndex);
                        refreshTimeline(false);
                        loadSelectedClipUi(false);
                        seekSequence(globalPlayheadMs, false);
                    }
                })
                .setNegativeButton("CANCELAR", null)
                .show();
    }

    private ClipItem readClip(File file) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(file.getAbsolutePath());
            long duration = parseLongSafe(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            int width = (int) parseLongSafe(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int height = (int) parseLongSafe(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            float fps = parseFloatSafe(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE));
            String audio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO);
            if (duration <= 0L) throw new IllegalStateException("Duração inválida");
            return new ClipItem(file, duration, width, height, fps, "yes".equalsIgnoreCase(audio));
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível ler: " + file.getName(), Toast.LENGTH_LONG).show();
            return null;
        } finally {
            try { r.release(); } catch (Exception ignored) { }
        }
    }

    private void refreshTimeline(boolean preservePlayhead) {
        long old = globalPlayheadMs;
        ArrayList<TimelineEditorView.ClipData> data = new ArrayList<>();
        for (ClipItem clip : clips) {
            data.add(new TimelineEditorView.ClipData(clip.file, clip.startMs, clip.endMs, clip.file.getName(), clip.cutBefore));
        }
        timeline.setClips(data, selectedIndex);
        long total = totalEditedDurationMs();
        globalPlayheadMs = preservePlayhead ? Math.max(0L, Math.min(total, old)) : Math.max(0L, Math.min(total, globalPlayheadMs));
        timeline.setPlayheadMs(globalPlayheadMs);
        textSequenceTotal.setText(formatSequenceTime(total));
        textSequenceCurrent.setText(formatSequenceTime(globalPlayheadMs));
        textClipCount.setText(clips.size() + (clips.size() == 1 ? " clipe" : " clipes") + " • " + formatSequenceTime(total));
        updateExportSizeControls(false);
        updateControls();
    }

    private void selectClip(int index, boolean movePlayhead) {
        if (joining || index < 0 || index >= clips.size()) return;
        selectedIndex = index;
        timeline.setSelectedIndex(index);
        loadSelectedClipUi(movePlayhead);
    }

    private void loadSelectedClipUi(boolean movePlayhead) {
        ClipItem clip = currentClip();
        if (clip == null) {
            clearSelectionUi();
            return;
        }

        textSelected.setText(clip.file.getName());
        String fpsText = clip.fps > 0f ? String.format(Locale.getDefault(), "%.2f fps", clip.fps) : "FPS --";
        String resolution = clip.width > 0 && clip.height > 0 ? clip.width + "x" + clip.height : "resolução --";
        textInfo.setText("Original " + formatTime(clip.durationMs) + "  •  trecho " + formatTime(clip.selectedDurationMs()) +
                "  •  " + resolution + "  •  " + fpsText + (clip.hasAudio ? "  •  áudio" : "  •  sem áudio"));
        editStart.setText(formatTime(clip.startMs));
        editEnd.setText(formatTime(clip.endMs));
        range.setDuration(clip.durationMs);
        range.setSelection(clip.startMs, clip.endMs);
        range.setPlayhead(clip.startMs);
        range.setVisibility(View.VISIBLE);
        updateLocalPosition(clip.startMs);

        if (movePlayhead) {
            long global = clipGlobalStart(selectedIndex);
            updateGlobalPlayhead(global, true);
            seekSequence(global, false);
        }
        updateControls();
    }

    private void clearSelectionUi() {
        
        selectedIndex = clips.isEmpty() ? -1 : selectedIndex;
        textSelected.setText("Selecione um clipe na timeline");
        textInfo.setText("Toque em um bloco para ajustar o início e o fim.");
        editStart.setText("");
        editEnd.setText("");
        textPosition.setText("--:--");
        range.setVisibility(View.GONE);
        updateControls();
    }

    private void moveSelected(int delta) {
        if (joining || selectedIndex < 0) return;
        
        int target = selectedIndex + delta;
        if (target < 0 || target >= clips.size()) return;
        pauseSequence();
        Collections.swap(clips, selectedIndex, target);
        selectedIndex = target;
        globalPlayheadMs = clipGlobalStart(selectedIndex);
        refreshTimeline(false);
        loadSelectedClipUi(false);
        seekSequence(globalPlayheadMs, false);
    }

    private void duplicateSelected() {
        if (joining || selectedIndex < 0 || selectedIndex >= clips.size()) return;
        
        pauseSequence();
        ClipItem copy = clips.get(selectedIndex).copy();
        copy.cutBefore = false;
        int insert = selectedIndex + 1;
        clips.add(insert, copy);
        selectedIndex = insert;
        globalPlayheadMs = clipGlobalStart(insert);
        refreshTimeline(false);
        loadSelectedClipUi(false);
        seekSequence(globalPlayheadMs, false);
    }

    private void cutAtPlayhead() {
        if (joining || clips.isEmpty()) return;

        SequencePosition position = mapGlobalPosition(globalPlayheadMs);
        if (position == null || position.clipIndex < 0 || position.clipIndex >= clips.size()) return;

        selectedIndex = position.clipIndex;
        ClipItem original = clips.get(selectedIndex);
        long source = Math.max(original.startMs, Math.min(original.endMs, position.sourceMs));
        long edgeGuardMs = 40L;

        if (source <= original.startMs + edgeGuardMs || source >= original.endMs - edgeGuardMs) {
            Toast.makeText(this, "Mova a linha de reprodução para dentro do trecho antes de cortar.", Toast.LENGTH_LONG).show();
            return;
        }

        boolean keepPlaying = sequencePlaying;
        long cutGlobal = globalPlayheadMs;

        ClipItem left = original.copy();
        left.endMs = source;

        ClipItem right = original.copy();
        right.startMs = source;
        right.cutBefore = true;

        clips.set(selectedIndex, left);
        clips.add(selectedIndex + 1, right);
        selectedIndex = selectedIndex + 1;
        globalPlayheadMs = cutGlobal;

        refreshTimeline(true);
        loadSelectedClipUi(false);
        seekSequence(globalPlayheadMs, keepPlaying);
        Toast.makeText(this, "Corte criado em " + formatTime(source), Toast.LENGTH_SHORT).show();
    }

    private void removeSelected() {
        if (joining || selectedIndex < 0 || selectedIndex >= clips.size()) return;
        
        pauseSequence();
        clips.remove(selectedIndex);
        releaseMediaPlayer();
        if (clips.isEmpty()) {
            selectedIndex = -1;
            globalPlayheadMs = 0L;
            refreshTimeline(false);
            clearSelectionUi();
            return;
        }
        if (selectedIndex >= clips.size()) selectedIndex = clips.size() - 1;
        globalPlayheadMs = clipGlobalStart(selectedIndex);
        refreshTimeline(false);
        loadSelectedClipUi(false);
        seekSequence(globalPlayheadMs, false);
    }

    private boolean applyTimesFromFields(boolean showMessage) {
        ClipItem clip = currentClip();
        if (clip == null) return false;
        try {
            long start = parseTime(editStart.getText().toString());
            long end = parseTime(editEnd.getText().toString());
            if (start < 0L || end <= start || end > clip.durationMs + 5L) {
                throw new IllegalArgumentException("O fim precisa ser maior que o início e não pode ultrapassar o vídeo.");
            }
            pauseSequence();
            
            clip.startMs = Math.max(0L, start);
            clip.endMs = Math.min(clip.durationMs, end);
            range.setSelection(clip.startMs, clip.endMs);
            range.setPlayhead(clip.startMs);
            globalPlayheadMs = clipGlobalStart(selectedIndex);
            refreshTimeline(false);
            updateLocalPosition(clip.startMs);
            seekSequence(globalPlayheadMs, false);
            if (showMessage) Toast.makeText(this, "Trecho atualizado na timeline", Toast.LENGTH_SHORT).show();
            return true;
        } catch (IllegalArgumentException e) {
            if (showMessage) Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void nudgeLocal(long deltaMs) {
        ClipItem clip = currentClip();
        if (clip == null) return;
        long next = Math.max(0L, Math.min(clip.durationMs, range.getPlayheadMs() + deltaMs));
        range.setPlayhead(next);
        updateLocalPosition(next);
        long global = clipGlobalStart(selectedIndex) + Math.max(0L, Math.min(clip.selectedDurationMs(), next - clip.startMs));
        seekSequence(global, sequencePlaying);
    }

    private void markStartHere() {
        ClipItem clip = currentClip();
        if (clip == null) return;
        long pos = range.getPlayheadMs();
        if (pos >= clip.endMs) {
            Toast.makeText(this, "O início precisa ficar antes do fim.", Toast.LENGTH_LONG).show();
            return;
        }
        pauseSequence();
        
        clip.startMs = pos;
        range.setSelection(clip.startMs, clip.endMs);
        editStart.setText(formatTime(pos));
        globalPlayheadMs = clipGlobalStart(selectedIndex);
        refreshTimeline(false);
        updateLocalPosition(pos);
        seekSequence(globalPlayheadMs, false);
    }

    private void markEndHere() {
        ClipItem clip = currentClip();
        if (clip == null) return;
        long pos = range.getPlayheadMs();
        if (pos <= clip.startMs) {
            Toast.makeText(this, "O fim precisa ficar depois do início.", Toast.LENGTH_LONG).show();
            return;
        }
        pauseSequence();
        
        clip.endMs = pos;
        range.setSelection(clip.startMs, clip.endMs);
        editEnd.setText(formatTime(pos));
        globalPlayheadMs = clipGlobalStart(selectedIndex) + clip.selectedDurationMs();
        refreshTimeline(false);
        updateLocalPosition(pos);
        seekSequence(globalPlayheadMs, false);
    }

    private void toggleSequencePlayback() {
        if (clips.isEmpty() || joining) return;
        if (sequencePlaying) {
            pauseSequence();
            return;
        }
        if (globalPlayheadMs >= totalEditedDurationMs()) globalPlayheadMs = 0L;
        sequencePlaying = true;
        buttonPreview.setText("❚❚  Pausar");
        seekSequence(globalPlayheadMs, true);
    }

    private void pauseSequence() {
        sequencePlaying = false;
        handler.removeCallbacks(previewMonitor);
        if (player != null && prepared) {
            try { if (player.isPlaying()) player.pause(); } catch (Exception ignored) { }
        }
        buttonPreview.setText("▶  Reproduzir sequência");
    }

    private void stopSequenceAtEnd() {
        sequencePlaying = false;
        advancingClip = false;
        handler.removeCallbacks(previewMonitor);
        long total = totalEditedDurationMs();
        updateGlobalPlayhead(total, true);
        buttonPreview.setText("▶  Reproduzir sequência");
        if (player != null && prepared) {
            try { if (player.isPlaying()) player.pause(); } catch (Exception ignored) { }
        }
    }

    private void seekGlobalBy(long deltaMs) {
        if (clips.isEmpty()) return;
        long next = Math.max(0L, Math.min(totalEditedDurationMs(), globalPlayheadMs + deltaMs));
        seekSequence(next, sequencePlaying);
    }

    private void seekSequence(long globalMs, boolean keepPlaying) {
        if (clips.isEmpty()) return;
        long total = totalEditedDurationMs();
        long safe = Math.max(0L, Math.min(total, globalMs));
        if (safe >= total && total > 0L) safe = Math.max(0L, total - 1L);
        updateGlobalPlayhead(safe, true);

        SequencePosition position = mapGlobalPosition(safe);
        if (position == null) return;

        if (!surfaceReady) return;
        if (player != null && prepared && previewClipIndex == position.clipIndex) {
            try {
                sequencePlaying = keepPlaying;
                previewClipEndSourceMs = clips.get(position.clipIndex).endMs;
                player.setOnSeekCompleteListener(mp -> {
                    try {
                        if (keepPlaying) {
                            mp.setVolume(1f, 1f);
                            if (!mp.isPlaying()) mp.start();
                            sequencePlaying = true;
                            buttonPreview.setText("❚❚  Pausar");
                            handler.removeCallbacks(previewMonitor);
                            handler.post(previewMonitor);
                        } else {
                            if (mp.isPlaying()) mp.pause();
                            sequencePlaying = false;
                            buttonPreview.setText("▶  Reproduzir sequência");
                        }
                    } catch (Exception ignored) { }
                });
                player.seekTo(position.sourceMs, MediaPlayer.SEEK_CLOSEST);
                return;
            } catch (Exception ignored) {
            }
        }
        prepareSequencePosition(position, keepPlaying);
    }

    private void prepareSequencePosition(SequencePosition position, boolean autoPlay) {
        ClipItem clip = clipAt(position.clipIndex);
        if (clip == null || !surfaceReady) return;

        releaseMediaPlayer();
        previewClipIndex = position.clipIndex;
        previewClipEndSourceMs = clip.endMs;
        sequencePlaying = autoPlay;
        advancingClip = true;

        try {
            SurfaceTexture texture = preview.getSurfaceTexture();
            if (texture == null) return;
            player = new MediaPlayer();
            Surface surface = new Surface(texture);
            player.setSurface(surface);
            surface.release();
            player.setDataSource(clip.file.getAbsolutePath());
            player.setOnPreparedListener(mp -> {
                prepared = true;
                try {
                    mp.setVolume(1f, 1f);
                    mp.setOnSeekCompleteListener(p -> {
                        try {
                            advancingClip = false;
                            if (sequencePlaying) {
                                p.start();
                                buttonPreview.setText("❚❚  Pausar");
                                handler.removeCallbacks(previewMonitor);
                                handler.post(previewMonitor);
                            } else {
                                if (p.isPlaying()) p.pause();
                                buttonPreview.setText("▶  Reproduzir sequência");
                            }
                        } catch (Exception ignored) { }
                    });
                    mp.seekTo(position.sourceMs, MediaPlayer.SEEK_CLOSEST);
                } catch (Exception e) {
                    advancingClip = false;
                }
            });
            player.setOnCompletionListener(mp -> {
                if (sequencePlaying) advanceToNextClip();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                advancingClip = false;
                textStatus.setText("Não foi possível reproduzir um dos clipes na prévia.");
                pauseSequence();
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            advancingClip = false;
            textStatus.setText("Erro na prévia da sequência: " + shortMessage(e));
        }
    }

    private void advanceToNextClip() {
        if (advancingClip) return;
        int next = previewClipIndex + 1;
        if (next >= clips.size()) {
            stopSequenceAtEnd();
            return;
        }
        advancingClip = true;
        long global = clipGlobalStart(next);
        updateGlobalPlayhead(global, true);
        SequencePosition position = new SequencePosition(next, clips.get(next).startMs);
        prepareSequencePosition(position, true);
    }

    private void releaseMediaPlayer() {
        handler.removeCallbacks(previewMonitor);
        prepared = false;
        advancingClip = false;
        if (player != null) {
            try { player.reset(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
        previewClipIndex = -1;
    }

    private void updateGlobalPlayhead(long globalMs, boolean autoScroll) {
        long safe = Math.max(0L, Math.min(totalEditedDurationMs(), globalMs));
        globalPlayheadMs = safe;
        timeline.setPlayheadMs(safe);
        textSequenceCurrent.setText(formatSequenceTime(safe));
        textSequenceTotal.setText(formatSequenceTime(totalEditedDurationMs()));

        if (autoScroll) {
            timeline.post(() -> {
                int x = Math.round(timeline.xForGlobalMs(globalPlayheadMs));
                int left = timelineScroll.getScrollX();
                int width = timelineScroll.getWidth();
                int safeMargin = Math.max(60, width / 6);
                if (x < left + safeMargin || x > left + width - safeMargin) {
                    timelineScroll.smoothScrollTo(Math.max(0, x - width / 2), 0);
                }
            });
        }
    }

    private void updateLocalPosition(long sourceMs) {
        ClipItem clip = currentClip();
        if (clip == null) return;
        long safe = Math.max(0L, Math.min(clip.durationMs, sourceMs));
        textPosition.setText(formatTime(safe) + " / " + formatTime(clip.durationMs));
    }

    private void changeZoom(int delta) {
        int next = Math.max(0, Math.min(zoomLevels.length - 1, zoomIndex + delta));
        if (next == zoomIndex) return;
        float oldX = timeline.xForGlobalMs(globalPlayheadMs);
        zoomIndex = next;
        timeline.setPixelsPerSecondDp(zoomLevels[zoomIndex]);
        updateZoomLabel();
        timeline.post(() -> {
            int newX = Math.round(timeline.xForGlobalMs(globalPlayheadMs));
            int centerOffset = timelineScroll.getWidth() / 2;
            timelineScroll.scrollTo(Math.max(0, newX - centerOffset), 0);
        });
    }

    private void updateZoomLabel() {
        textTimelineZoom.setText("ZOOM " + (zoomIndex + 1) + "/" + zoomLevels.length);
        buttonZoomOut.setEnabled(zoomIndex > 0);
        buttonZoomIn.setEnabled(zoomIndex < zoomLevels.length - 1);
    }

    private SequencePosition mapGlobalPosition(long globalMs) {
        if (clips.isEmpty()) return null;
        long safe = Math.max(0L, Math.min(totalEditedDurationMs(), globalMs));
        long accumulated = 0L;
        for (int i = 0; i < clips.size(); i++) {
            ClipItem clip = clips.get(i);
            long duration = clip.selectedDurationMs();
            if (safe < accumulated + duration || i == clips.size() - 1) {
                long inside = Math.max(0L, Math.min(duration, safe - accumulated));
                long source = Math.max(clip.startMs, Math.min(clip.endMs, clip.startMs + inside));
                if (source >= clip.endMs && clip.endMs > clip.startMs) source = clip.endMs - 1L;
                return new SequencePosition(i, source);
            }
            accumulated += duration;
        }
        ClipItem last = clips.get(clips.size() - 1);
        return new SequencePosition(clips.size() - 1, Math.max(last.startMs, last.endMs - 1L));
    }

    private long clipGlobalStart(int index) {
        long total = 0L;
        for (int i = 0; i < index && i < clips.size(); i++) total += clips.get(i).selectedDurationMs();
        return total;
    }

    private long totalEditedDurationMs() {
        long total = 0L;
        for (ClipItem clip : clips) total += clip.selectedDurationMs();
        return total;
    }

    private void updateControls() {
        boolean selected = selectedIndex >= 0 && selectedIndex < clips.size();
        boolean editable = selected && !joining;
        buttonUp.setEnabled(editable && selectedIndex > 0);
        buttonDown.setEnabled(editable && selectedIndex < clips.size() - 1);
        buttonRemove.setEnabled(editable);
        buttonDuplicate.setEnabled(editable);
        buttonCut.setEnabled(editable);
        buttonApplyTimes.setEnabled(editable);
        back1s.setEnabled(editable);
        back100ms.setEnabled(editable);
        back10ms.setEnabled(editable);
        forward10ms.setEnabled(editable);
        forward100ms.setEnabled(editable);
        forward1s.setEnabled(editable);
        markStart.setEnabled(editable);
        markEnd.setEnabled(editable);
        range.setEnabled(editable);
        editStart.setEnabled(editable);
        editEnd.setEnabled(editable);
        buttonPreview.setEnabled(!joining && !clips.isEmpty());
        buttonBack5s.setEnabled(!joining && !clips.isEmpty());
        buttonForward5s.setEnabled(!joining && !clips.isEmpty());
        buttonJoin.setEnabled(!joining && !clips.isEmpty());
        buttonAdd.setEnabled(!joining);
        if (editOutputName != null) editOutputName.setEnabled(!joining);
        if (spinnerResolution != null) spinnerResolution.setEnabled(!joining);
        if (spinnerCompression != null) spinnerCompression.setEnabled(!joining);
        if (checkTargetSize != null) checkTargetSize.setEnabled(!joining);
        if (seekTargetSize != null) seekTargetSize.setEnabled(!joining && checkTargetSize != null && checkTargetSize.isChecked());
        timeline.setEnabled(!joining);

        if (!joining) {
            if (clips.isEmpty()) textStatus.setText("Adicione um vídeo à timeline para editar e exportar.");
            else textStatus.setText("Timeline pronta: " + clips.size() + (clips.size() == 1 ? " clipe" : " clipes") + " • " + formatSequenceTime(totalEditedDurationMs()));
        }
    }

    private void joinVideos() {
        if (joining) return;
        if (clips.isEmpty()) {
            Toast.makeText(this, "Adicione pelo menos um vídeo.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!applyTimesFromFields(false) && currentClip() != null) {
            Toast.makeText(this, "Confira os tempos do clipe selecionado.", Toast.LENGTH_LONG).show();
            return;
        }

        pauseSequence();
        releaseMediaPlayer();
        setJoiningUi(true);
        progressJoin.setProgress(0);
        textJoinProgress.setText("0%");
        textStatus.setText("Preparando a timeline para exportação...");

        try {
            File folder = videoFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                throw new IllegalStateException("Não foi possível criar Downloads/ExtratorVideos");
            }
            String requestedName = editOutputName == null ? "" : editOutputName.getText().toString();
            joinOutput = uniqueOutputFile(folder, buildOutputFileName(requestedName, "video_final"));

            ClipItem first = clips.get(0);
            int[] targetDimensions = resolveTargetDimensions(first);
            int targetWidth = targetDimensions[0];
            int targetHeight = targetDimensions[1];
            boolean useTargetSize = checkTargetSize != null && checkTargetSize.isChecked();
            String requestedVideoMimeType = requestedVideoMimeType();
            String exportVideoMimeType = effectiveVideoMimeType();
            boolean hevcFallback = MimeTypes.VIDEO_H265.equals(requestedVideoMimeType)
                    && !MimeTypes.VIDEO_H265.equals(exportVideoMimeType);
            lastExportCodecLabel = codecLabel(exportVideoMimeType);
            int requestedVideoBitrate = useTargetSize
                    ? calculateVideoBitrateForTargetSize(targetSizeMb, totalEditedDurationMs(), exportVideoMimeType)
                    : bitrateProfileForResolution(exportVideoMimeType)[1];
            if (hevcFallback) {
                Toast.makeText(this, "HEVC não está disponível neste aparelho. A exportação usará H.264.", Toast.LENGTH_LONG).show();
            }

            ArrayList<EditedMediaItem> items = new ArrayList<>();
            for (ClipItem clip : clips) {
                MediaItem.ClippingConfiguration clipping = new MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startMs)
                        .setEndPositionMs(clip.endMs)
                        .build();
                MediaItem mediaItem = new MediaItem.Builder()
                        .setUri(Uri.fromFile(clip.file))
                        .setClippingConfiguration(clipping)
                        .build();

                List<Effect> videoEffects = new ArrayList<>();
                videoEffects.add(Presentation.createForWidthAndHeight(
                        targetWidth,
                        targetHeight,
                        Presentation.LAYOUT_SCALE_TO_FIT
                ));
                Effects effects = new Effects(Collections.emptyList(), videoEffects);
                items.add(new EditedMediaItem.Builder(mediaItem).setEffects(effects).build());
            }

            EditedMediaItemSequence sequence = EditedMediaItemSequence.withAudioAndVideoFrom(items);
            Composition composition = new Composition.Builder(sequence)
                    .setTransmuxAudio(false)
                    .setTransmuxVideo(false)
                    .build();

            Transformer.Builder transformerBuilder = new Transformer.Builder(this)
                    .setVideoMimeType(exportVideoMimeType)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC);

            // Define bitrate também no modo automático. Em HEVC usamos um perfil mais eficiente,
            // reduzindo bastante o tamanho sem precisar diminuir a resolução.
            if (requestedVideoBitrate > 0) {
                DefaultEncoderFactory encoderFactory = new DefaultEncoderFactory.Builder(this)
                        .setEnableFallback(true)
                        .setRequestedVideoEncoderSettings(
                                new VideoEncoderSettings.Builder()
                                        .setBitrate(requestedVideoBitrate)
                                        .build())
                        .setRequestedAudioEncoderSettings(
                                new AudioEncoderSettings.Builder()
                                        .setBitrate(AUDIO_BITRATE_BPS)
                                        .build())
                        .build();
                transformerBuilder.setEncoderFactory(encoderFactory);
            }

            joinTransformer = transformerBuilder
                    .addListener(new Transformer.Listener() {
                        @Override
                        public void onCompleted(Composition composition, ExportResult exportResult) {
                            File output = joinOutput;
                            joining = false;
                            handler.removeCallbacks(joinProgressMonitor);
                            joinTransformer = null;

                            if (output == null || !output.exists() || output.length() == 0L) {
                                textStatus.setText("Erro: o arquivo final não foi criado.");
                                setJoiningUi(false);
                                return;
                            }
                            progressJoin.setProgress(100);
                            textJoinProgress.setText("100%");
                            textStatus.setText("✓ Sequência exportada: " + output.getName() + " • " + formatFileSize(output.length()));
                            MediaScannerConnection.scanFile(
                                    EditorActivity.this,
                                    new String[]{output.getAbsolutePath()},
                                    new String[]{"video/mp4"},
                                    null
                            );
                            setJoiningUi(false);
                            new AlertDialog.Builder(EditorActivity.this)
                                    .setTitle("Exportação concluída")
                                    .setMessage(buildExportCompletedMessage(output))
                                    .setPositiveButton("ABRIR PASTA", (dialog, which) -> openVideoFolder())
                                    .setNegativeButton("FECHAR", null)
                                    .show();
                        }

                        @Override
                        public void onError(Composition composition, ExportResult exportResult, ExportException exportException) {
                            joining = false;
                            handler.removeCallbacks(joinProgressMonitor);
                            if (joinOutput != null && joinOutput.exists() && joinOutput.length() < 1024L * 1024L) {
                                joinOutput.delete();
                            }
                            joinTransformer = null;
                            textStatus.setText("Erro ao exportar: " + shortMessage(exportException));
                            Toast.makeText(EditorActivity.this, "Não foi possível exportar a sequência.", Toast.LENGTH_LONG).show();
                            setJoiningUi(false);
                        }
                    })
                    .build();

            joining = true;
            joinTransformer.start(composition, joinOutput.getAbsolutePath());
            handler.removeCallbacks(joinProgressMonitor);
            handler.post(joinProgressMonitor);
        } catch (Exception e) {
            joining = false;
            if (joinOutput != null && joinOutput.exists() && joinOutput.length() < 1024L * 1024L) joinOutput.delete();
            joinTransformer = null;
            textStatus.setText("Erro ao preparar exportação: " + shortMessage(e));
            setJoiningUi(false);
        }
    }

    private int[] resolveTargetDimensions(ClipItem first) {
        int sourceWidth = first != null && first.width > 0 ? first.width : 1280;
        int sourceHeight = first != null && first.height > 0 ? first.height : 720;
        int choice = spinnerResolution == null ? 0 : spinnerResolution.getSelectedItemPosition();

        if (choice <= 0) {
            int w = even(sourceWidth);
            int h = even(sourceHeight);
            if (w > 1920 || h > 1920) {
                float scale = Math.min(1920f / w, 1920f / h);
                w = even(Math.max(2, Math.round(w * scale)));
                h = even(Math.max(2, Math.round(h * scale)));
            }
            return new int[]{w, h};
        }

        int shortSide;
        switch (choice) {
            case 1: shortSide = 360; break;
            case 2: shortSide = 480; break;
            case 3: shortSide = 720; break;
            case 4: shortSide = 1080; break;
            default: shortSide = 720; break;
        }

        float aspect = sourceHeight > 0 ? (sourceWidth / (float) sourceHeight) : (16f / 9f);
        int w;
        int h;
        if (sourceWidth >= sourceHeight) {
            h = shortSide;
            w = even(Math.max(2, Math.round(h * aspect)));
        } else {
            w = shortSide;
            h = even(Math.max(2, Math.round(w / Math.max(0.1f, aspect))));
        }
        w = even(w);
        h = even(h);
        if (w > 1920 || h > 1920) {
            float scale = Math.min(1920f / w, 1920f / h);
            w = even(Math.max(2, Math.round(w * scale)));
            h = even(Math.max(2, Math.round(h * scale)));
        }
        return new int[]{w, h};
    }

    private void updateExportSizeControls(boolean resetToRecommended) {
        if (seekTargetSize == null || textTargetSize == null || textTargetMin == null || textTargetMax == null || textExportEstimate == null) return;
        long durationMs = totalEditedDurationMs();
        if (durationMs <= 0L || clips.isEmpty()) {
            targetSizeMinMb = 1;
            targetSizeMaxMb = 100;
            if (resetToRecommended) targetSizeMb = 30;
            updatingExportUi = true;
            seekTargetSize.setProgress(mapTargetSizeToProgress(targetSizeMb));
            updatingExportUi = false;
            textTargetMin.setText("1 MB");
            textTargetMax.setText("100 MB");
            textTargetSize.setText("≈ " + targetSizeMb + " MB");
            textExportEstimate.setText("Adicione um vídeo para calcular resolução, bitrate e tamanho estimado.");
            return;
        }

        String exportMime = effectiveVideoMimeType();
        int[] bitrates = bitrateProfileForResolution(exportMime);
        int minVideo = bitrates[0];
        int recommendedVideo = bitrates[1];
        int maxVideo = bitrates[2];
        targetSizeMinMb = Math.max(1, (int) Math.ceil(estimatedSizeMb(durationMs, minVideo, AUDIO_BITRATE_BPS)));
        targetSizeMaxMb = Math.max(targetSizeMinMb + 1, (int) Math.ceil(estimatedSizeMb(durationMs, maxVideo, AUDIO_BITRATE_BPS)));
        int recommendedMb = clampInt((int) Math.round(estimatedSizeMb(durationMs, recommendedVideo, AUDIO_BITRATE_BPS)), targetSizeMinMb, targetSizeMaxMb);

        if (resetToRecommended || targetSizeMb < targetSizeMinMb || targetSizeMb > targetSizeMaxMb) {
            targetSizeMb = recommendedMb;
        }

        updatingExportUi = true;
        seekTargetSize.setProgress(mapTargetSizeToProgress(targetSizeMb));
        updatingExportUi = false;
        updateTargetSizeLabels();
    }

    private void updateTargetSizeLabels() {
        if (textTargetSize == null) return;
        targetSizeMb = clampInt(targetSizeMb, targetSizeMinMb, targetSizeMaxMb);
        textTargetMin.setText(targetSizeMinMb + " MB");
        textTargetMax.setText(targetSizeMaxMb + " MB");
        textTargetSize.setText("≈ " + targetSizeMb + " MB");

        ClipItem first = clips.isEmpty() ? null : clips.get(0);
        int[] dims = resolveTargetDimensions(first);
        String resText = dims[0] + "×" + dims[1];
        boolean custom = checkTargetSize != null && checkTargetSize.isChecked();
        String exportMime = effectiveVideoMimeType();
        String codec = codecLabel(exportMime);
        boolean hevcRequestedButUnavailable = MimeTypes.VIDEO_H265.equals(requestedVideoMimeType())
                && !MimeTypes.VIDEO_H265.equals(exportMime);
        String fallbackText = hevcRequestedButUnavailable ? "  •  HEVC indisponível → H.264" : "";
        if (custom) {
            int videoBitrate = calculateVideoBitrateForTargetSize(targetSizeMb, totalEditedDurationMs(), exportMime);
            textExportEstimate.setText("Alvo aproximado: " + targetSizeMb + " MB  •  " + resText + "  •  " + codec + "/AAC  •  vídeo ~" + formatBitrate(videoBitrate) + fallbackText);
        } else {
            int recommended = bitrateProfileForResolution(exportMime)[1];
            double estimate = estimatedSizeMb(totalEditedDurationMs(), recommended, AUDIO_BITRATE_BPS);
            textExportEstimate.setText("Compressão automática  •  " + resText + "  •  " + codec + "  •  estimativa ~" + Math.max(1, Math.round(estimate)) + " MB" + fallbackText);
        }
    }

    private String requestedVideoMimeType() {
        int choice = spinnerCompression == null ? 1 : spinnerCompression.getSelectedItemPosition();
        return choice == 1 ? MimeTypes.VIDEO_H265 : MimeTypes.VIDEO_H264;
    }

    private String effectiveVideoMimeType() {
        String requested = requestedVideoMimeType();
        if (MimeTypes.VIDEO_H265.equals(requested) && !isVideoEncoderSupported(MimeTypes.VIDEO_H265)) {
            return MimeTypes.VIDEO_H264;
        }
        return requested;
    }

    private boolean isVideoEncoderSupported(String mimeType) {
        try {
            MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
            for (MediaCodecInfo info : codecList.getCodecInfos()) {
                if (!info.isEncoder()) continue;
                for (String type : info.getSupportedTypes()) {
                    if (mimeType.equalsIgnoreCase(type)) return true;
                }
            }
        } catch (Exception ignored) { }
        return false;
    }

    private String codecLabel(String mimeType) {
        return MimeTypes.VIDEO_H265.equals(mimeType) ? "H.265 / HEVC" : "H.264 / AVC";
    }

    private int[] bitrateProfileForResolution(String mimeType) {
        int choice = spinnerResolution == null ? 0 : spinnerResolution.getSelectedItemPosition();
        boolean hevc = MimeTypes.VIDEO_H265.equals(mimeType);

        // HEVC trabalha com bitrates menores para manter qualidade visual semelhante ao H.264.
        if (hevc) {
            if (choice == 1) return new int[]{300_000, 650_000, 1_600_000};
            if (choice == 2) return new int[]{450_000, 950_000, 2_400_000};
            if (choice == 3) return new int[]{750_000, 1_800_000, 4_200_000};
            if (choice == 4) return new int[]{1_200_000, 3_200_000, 7_000_000};
        } else {
            if (choice == 1) return new int[]{450_000, 1_000_000, 2_500_000};
            if (choice == 2) return new int[]{700_000, 1_600_000, 4_000_000};
            if (choice == 3) return new int[]{1_200_000, 3_000_000, 7_000_000};
            if (choice == 4) return new int[]{2_000_000, 5_500_000, 12_000_000};
        }

        ClipItem first = clips.isEmpty() ? null : clips.get(0);
        int[] dims = first == null ? new int[]{1280, 720} : resolveTargetDimensions(first);
        int longSide = Math.max(dims[0], dims[1]);
        int shortSide = Math.min(dims[0], dims[1]);
        if (hevc) {
            if (shortSide <= 480) return new int[]{450_000, 1_050_000, 2_500_000};
            if (shortSide <= 720) return new int[]{750_000, 2_000_000, 4_800_000};
            if (shortSide <= 1080 && longSide <= 1920) return new int[]{1_200_000, 3_500_000, 7_500_000};
            return new int[]{1_800_000, 4_800_000, 10_000_000};
        }
        if (shortSide <= 480) return new int[]{700_000, 1_800_000, 4_000_000};
        if (shortSide <= 720) return new int[]{1_200_000, 3_500_000, 8_000_000};
        if (shortSide <= 1080 && longSide <= 1920) return new int[]{2_000_000, 6_000_000, 12_000_000};
        return new int[]{3_000_000, 8_000_000, 16_000_000};
    }

    private int calculateVideoBitrateForTargetSize(int sizeMb, long durationMs, String mimeType) {
        double seconds = Math.max(0.25d, durationMs / 1000d);
        double usableBits = Math.max(1d, sizeMb) * 1024d * 1024d * 8d * 0.97d;
        long totalBitrate = Math.round(usableBits / seconds);
        int video = (int) Math.max(250_000L, Math.min(20_000_000L, totalBitrate - AUDIO_BITRATE_BPS));
        int[] profile = bitrateProfileForResolution(mimeType);
        return clampInt(video, profile[0], profile[2]);
    }

    private double estimatedSizeMb(long durationMs, int videoBitrate, int audioBitrate) {
        double seconds = Math.max(0d, durationMs / 1000d);
        double bytes = seconds * Math.max(1, videoBitrate + audioBitrate) / 8d;
        return bytes / (1024d * 1024d);
    }

    private int mapProgressToTargetSize(int progress) {
        if (targetSizeMaxMb <= targetSizeMinMb) return targetSizeMinMb;
        double ratio = Math.max(0d, Math.min(1d, progress / 1000d));
        return clampInt((int) Math.round(targetSizeMinMb + ratio * (targetSizeMaxMb - targetSizeMinMb)), targetSizeMinMb, targetSizeMaxMb);
    }

    private int mapTargetSizeToProgress(int sizeMb) {
        if (targetSizeMaxMb <= targetSizeMinMb) return 0;
        double ratio = (clampInt(sizeMb, targetSizeMinMb, targetSizeMaxMb) - targetSizeMinMb) / (double) (targetSizeMaxMb - targetSizeMinMb);
        return clampInt((int) Math.round(ratio * 1000d), 0, 1000);
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String formatBitrate(int bps) {
        if (bps >= 1_000_000) return String.format(Locale.getDefault(), "%.1f Mbps", bps / 1_000_000d);
        return Math.round(bps / 1000d) + " kbps";
    }

    private String formatFileSize(long bytes) {
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024d * 1024d));
    }

    private String buildExportCompletedMessage(File output) {
        StringBuilder message = new StringBuilder();
        message.append("O vídeo foi salvo em Downloads/ExtratorVideos.\n\n");
        message.append("Tamanho gerado: ").append(formatFileSize(output.length()));
        message.append("\nCodec: ").append(lastExportCodecLabel);
        if (checkTargetSize != null && checkTargetSize.isChecked()) {
            message.append("\nTamanho solicitado: ~").append(targetSizeMb).append(" MB");
        }
        ClipItem first = clips.isEmpty() ? null : clips.get(0);
        if (first != null) {
            int[] dims = resolveTargetDimensions(first);
            message.append("\nResolução: ").append(dims[0]).append("×").append(dims[1]);
        }
        message.append("\n\nOs vídeos originais foram mantidos.");
        return message.toString();
    }

    private void setJoiningUi(boolean busy) {
        joining = busy;
        updateControls();
        buttonZoomOut.setEnabled(!busy && zoomIndex > 0);
        buttonZoomIn.setEnabled(!busy && zoomIndex < zoomLevels.length - 1);
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
        if (name.isEmpty()) name = fallbackPrefix + "_" + System.currentTimeMillis();
        return name + ".mp4";
    }

    private File uniqueOutputFile(File folder, String fileName) {
        File candidate = new File(folder, fileName);
        if (!candidate.exists()) return candidate;
        int point = fileName.toLowerCase(Locale.ROOT).endsWith(".mp4") ? fileName.length() - 4 : fileName.length();
        String base = fileName.substring(0, point);
        String extension = point < fileName.length() ? fileName.substring(point) : ".mp4";
        int index = 2;
        while (candidate.exists()) {
            candidate = new File(folder, base + "_" + index + extension);
            index++;
        }
        return candidate;
    }

    private ClipItem currentClip() {
        return clipAt(selectedIndex);
    }

    private ClipItem clipAt(int index) {
        if (index < 0 || index >= clips.size()) return null;
        return clips.get(index);
    }

    private File videoFolder() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ExtratorVideos");
    }

    private void openVideoFolder() {
        Uri folderUri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:Download/ExtratorVideos"
        );
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "A pasta é Downloads/ExtratorVideos.", Toast.LENGTH_LONG).show();
        }
    }

    private long parseTime(String text) {
        String value = text == null ? "" : text.trim().replace(',', '.');
        if (value.isEmpty()) throw new IllegalArgumentException("Informe início e fim.");
        String[] parts = value.split(":");
        if (parts.length < 1 || parts.length > 3) throw new IllegalArgumentException("Use HH:MM:SS.mmm.");
        try {
            long hours = 0L;
            long minutes = 0L;
            double seconds;
            if (parts.length == 3) {
                hours = Long.parseLong(parts[0]);
                minutes = Long.parseLong(parts[1]);
                seconds = Double.parseDouble(parts[2]);
            } else if (parts.length == 2) {
                minutes = Long.parseLong(parts[0]);
                seconds = Double.parseDouble(parts[1]);
            } else {
                seconds = Double.parseDouble(parts[0]);
            }
            if (hours < 0 || minutes < 0 || minutes >= 60 || seconds < 0 || seconds >= 60) throw new NumberFormatException();
            return Math.round(((hours * 3600.0) + (minutes * 60.0) + seconds) * 1000.0);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Tempo inválido. Use HH:MM:SS.mmm.");
        }
    }

    private String formatTime(long millis) {
        long safe = Math.max(0L, millis);
        long hours = safe / 3_600_000L;
        long minutes = (safe % 3_600_000L) / 60_000L;
        long seconds = (safe % 60_000L) / 1000L;
        long ms = safe % 1000L;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d.%03d", hours, minutes, seconds, ms);
    }

    private String formatSequenceTime(long millis) {
        long safe = Math.max(0L, millis);
        long hours = safe / 3_600_000L;
        long minutes = (safe % 3_600_000L) / 60_000L;
        long seconds = (safe % 60_000L) / 1000L;
        long ms = safe % 1000L;
        if (hours > 0L) return String.format(Locale.getDefault(), "%02d:%02d:%02d.%03d", hours, minutes, seconds, ms);
        return String.format(Locale.getDefault(), "%02d:%02d.%03d", minutes, seconds, ms);
    }

    private long parseLongSafe(String value) {
        try { return value == null ? 0L : Long.parseLong(value); }
        catch (Exception e) { return 0L; }
    }

    private float parseFloatSafe(String value) {
        try { return value == null ? 0f : Float.parseFloat(value); }
        catch (Exception e) { return 0f; }
    }

    private int even(int value) {
        int safe = Math.max(2, value);
        return safe % 2 == 0 ? safe : safe - 1;
    }

    private String shortMessage(Throwable t) {
        if (t == null) return "erro desconhecido";
        String m = t.getMessage();
        if (m == null || m.trim().isEmpty()) m = t.getClass().getSimpleName();
        m = m.replace('\n', ' ').replace('\r', ' ').trim();
        return m.length() > 220 ? m.substring(0, 220) : m;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(previewMonitor);
        handler.removeCallbacks(joinProgressMonitor);
        releaseMediaPlayer();
        if (timeline != null) timeline.releaseResources();
        if (joinTransformer != null && joining) {
            try { joinTransformer.cancel(); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    private static final class SequencePosition {
        final int clipIndex;
        final long sourceMs;

        SequencePosition(int clipIndex, long sourceMs) {
            this.clipIndex = clipIndex;
            this.sourceMs = sourceMs;
        }
    }

    private static final class ClipItem {
        final File file;
        final long durationMs;
        final int width;
        final int height;
        final float fps;
        final boolean hasAudio;
        long startMs;
        long endMs;
        boolean cutBefore;

        ClipItem(File file, long durationMs, int width, int height, float fps, boolean hasAudio) {
            this.file = file;
            this.durationMs = durationMs;
            this.width = width;
            this.height = height;
            this.fps = fps;
            this.hasAudio = hasAudio;
            this.startMs = 0L;
            this.endMs = durationMs;
            this.cutBefore = false;
        }

        ClipItem copy() {
            ClipItem c = new ClipItem(file, durationMs, width, height, fps, hasAudio);
            c.startMs = startMs;
            c.endMs = endMs;
            c.cutBefore = cutBefore;
            return c;
        }

        long selectedDurationMs() {
            return Math.max(1L, endMs - startMs);
        }
    }
}
