package br.com.extratorvideos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Linha do tempo horizontal de múltiplos clipes.
 *
 * - Cada bloco representa um vídeo/trecho.
 * - Miniaturas são extraídas em segundo plano.
 * - Toque seleciona o clipe e posiciona o playhead.
 * - Arraste o playhead para navegar sem interromper a reprodução.
 * - Pressione e segure um clipe e arraste para reordenar.
 */
public class TimelineEditorView extends View {

    public static final class ClipData {
        public final File file;
        public final long startMs;
        public final long endMs;
        public final String title;
        public final boolean cutBefore;

        public ClipData(File file, long startMs, long endMs, String title) {
            this(file, startMs, endMs, title, false);
        }

        public ClipData(File file, long startMs, long endMs, String title, boolean cutBefore) {
            this.file = file;
            this.startMs = Math.max(0L, startMs);
            this.endMs = Math.max(this.startMs + 1L, endMs);
            this.title = title == null ? file.getName() : title;
            this.cutBefore = cutBefore;
        }

        public long durationMs() {
            return Math.max(1L, endMs - startMs);
        }

        String cacheKey() {
            return file.getAbsolutePath() + "|" + startMs + "|" + endMs;
        }
    }

    public interface Listener {
        void onClipSelected(int index);
        void onTimelineSeek(long globalMs, boolean finished);
        void onClipMoveRequested(int fromIndex, int toIndex);
    }

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rulerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rulerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clipBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint separatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playheadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playheadHeadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dragOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cutMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cutMarkerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final ArrayList<ClipData> clips = new ArrayList<>();
    private final Map<String, List<Bitmap>> thumbnailCache = new HashMap<>();
    private final ExecutorService thumbnailExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final GestureDetector gestureDetector;

    private Listener listener;
    private int selectedIndex = -1;
    private int draggingClipIndex = -1;
    private long playheadMs = 0L;

    private float pixelsPerSecond;
    private final float minClipWidth;
    private final float leftPadding;
    private final float rightPadding;
    private final float rulerHeight;
    private final float clipTop;
    private final float clipHeight;
    private final float cornerRadius;
    private final float playheadHitRadius;

    private boolean draggingPlayhead = false;

    public TimelineEditorView(Context context) {
        this(context, null);
    }

    public TimelineEditorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TimelineEditorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        pixelsPerSecond = dp(4.8f);
        minClipWidth = dp(92f);
        leftPadding = dp(16f);
        rightPadding = dp(20f);
        rulerHeight = dp(34f);
        clipTop = dp(40f);
        clipHeight = dp(92f);
        cornerRadius = dp(12f);
        playheadHitRadius = dp(24f);

        backgroundPaint.setColor(Color.rgb(7, 11, 20));
        rulerPaint.setColor(Color.rgb(49, 87, 126));
        rulerPaint.setStrokeWidth(dp(1f));
        rulerTextPaint.setColor(Color.rgb(114, 135, 159));
        rulerTextPaint.setTextSize(sp(10f));

        clipPaint.setColor(Color.rgb(17, 29, 46));
        clipBorderPaint.setColor(Color.rgb(32, 53, 82));
        clipBorderPaint.setStyle(Paint.Style.STROKE);
        clipBorderPaint.setStrokeWidth(dp(1.5f));

        selectedBorderPaint.setColor(Color.rgb(255, 199, 102));
        selectedBorderPaint.setStyle(Paint.Style.STROKE);
        selectedBorderPaint.setStrokeWidth(dp(3f));

        labelBgPaint.setColor(Color.argb(205, 8, 14, 24));
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(sp(10f));
        labelPaint.setFakeBoldText(true);

        separatorPaint.setColor(Color.rgb(95, 124, 255));
        separatorPaint.setStrokeWidth(dp(2f));

        playheadPaint.setColor(Color.rgb(53, 231, 215));
        playheadPaint.setStrokeWidth(dp(2.5f));
        playheadHeadPaint.setColor(Color.rgb(53, 231, 215));

        dragOverlayPaint.setColor(Color.argb(72, 255, 199, 102));

        cutMarkerPaint.setColor(Color.rgb(255, 93, 122));
        cutMarkerPaint.setStrokeWidth(dp(2.4f));
        cutMarkerTextPaint.setColor(Color.rgb(255, 93, 122));
        cutMarkerTextPaint.setTextSize(sp(17f));
        cutMarkerTextPaint.setFakeBoldText(true);

        setBackgroundColor(Color.TRANSPARENT);
        setClickable(true);
        setFocusable(true);
        setContentDescription("Linha do tempo dos vídeos unidos");

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                int index = clipIndexForX(e.getX());
                if (index >= 0) {
                    selectedIndex = index;
                    if (listener != null) listener.onClipSelected(index);
                }
                long global = globalMsForX(e.getX());
                playheadMs = global;
                invalidate();
                if (listener != null) listener.onTimelineSeek(global, true);
                performClick();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                int index = clipIndexForX(e.getX());
                if (index >= 0) {
                    draggingClipIndex = index;
                    selectedIndex = index;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    if (listener != null) listener.onClipSelected(index);
                    invalidate();
                }
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setClips(List<ClipData> newClips, int selectedIndex) {
        clips.clear();
        if (newClips != null) clips.addAll(newClips);
        this.selectedIndex = selectedIndex >= 0 && selectedIndex < clips.size() ? selectedIndex : -1;
        playheadMs = clamp(playheadMs, 0L, totalDurationMs());
        requestLayout();
        invalidate();
        ensureThumbnails();
    }

    public void setSelectedIndex(int index) {
        selectedIndex = index >= 0 && index < clips.size() ? index : -1;
        invalidate();
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setPlayheadMs(long positionMs) {
        playheadMs = clamp(positionMs, 0L, totalDurationMs());
        invalidate();
    }

    public long getPlayheadMs() {
        return playheadMs;
    }

    public long getTotalDurationMs() {
        return totalDurationMs();
    }

    public void setPixelsPerSecondDp(float dpPerSecond) {
        pixelsPerSecond = dp(Math.max(1.7f, Math.min(22f, dpPerSecond)));
        requestLayout();
        invalidate();
    }

    public float getPixelsPerSecondDp() {
        return pixelsPerSecond / getResources().getDisplayMetrics().density;
    }

    public float xForGlobalMs(long globalMs) {
        long target = clamp(globalMs, 0L, totalDurationMs());
        float x = leftPadding;
        long accumulated = 0L;
        for (ClipData clip : clips) {
            long d = clip.durationMs();
            float w = clipWidth(clip);
            if (target <= accumulated + d) {
                float ratio = d <= 0L ? 0f : (float) (target - accumulated) / (float) d;
                return x + ratio * w;
            }
            accumulated += d;
            x += w;
        }
        return Math.max(leftPadding, x);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float content = leftPadding + rightPadding;
        for (ClipData clip : clips) content += clipWidth(clip);
        int minimum = Math.max(getResources().getDisplayMetrics().widthPixels - (int) dp(32f), (int) dp(320f));
        int desiredWidth = Math.max(minimum, Math.round(content));
        int desiredHeight = Math.round(dp(148f));
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRoundRect(new RectF(0, 0, getWidth(), getHeight()), dp(14f), dp(14f), backgroundPaint);

        drawRuler(canvas);

        float x = leftPadding;
        for (int i = 0; i < clips.size(); i++) {
            ClipData clip = clips.get(i);
            float width = clipWidth(clip);
            RectF rect = new RectF(x, clipTop, x + width, clipTop + clipHeight);
            drawClip(canvas, clip, i, rect);
            if (i > 0 && clip.cutBefore) drawCutMarker(canvas, rect.left);
            x += width;
        }

        float px = xForGlobalMs(playheadMs);
        canvas.drawLine(px, dp(24f), px, clipTop + clipHeight + dp(9f), playheadPaint);
        RectF head = new RectF(px - dp(7f), dp(18f), px + dp(7f), dp(31f));
        canvas.drawRoundRect(head, dp(4f), dp(4f), playheadHeadPaint);
    }

    private void drawRuler(Canvas canvas) {
        long total = totalDurationMs();
        if (total <= 0L) return;

        long stepMs;
        float ppsDp = getPixelsPerSecondDp();
        if (ppsDp >= 14f) stepMs = 5_000L;
        else if (ppsDp >= 7f) stepMs = 10_000L;
        else if (ppsDp >= 3.5f) stepMs = 30_000L;
        else stepMs = 60_000L;

        for (long t = 0L; t <= total; t += stepMs) {
            float x = xForGlobalMs(t);
            canvas.drawLine(x, rulerHeight - dp(8f), x, rulerHeight, rulerPaint);
            canvas.drawText(formatRulerTime(t), x + dp(3f), rulerHeight - dp(11f), rulerTextPaint);
        }
    }

    private void drawClip(Canvas canvas, ClipData clip, int index, RectF rect) {
        canvas.save();
        Path clipPath = new Path();
        clipPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        canvas.drawRect(rect, clipPaint);

        List<Bitmap> frames = thumbnailCache.get(clip.cacheKey());
        if (frames != null && !frames.isEmpty()) {
            float tileWidth = Math.max(dp(58f), rect.width() / frames.size());
            int tileCount = Math.max(1, (int) Math.ceil(rect.width() / tileWidth));
            tileWidth = rect.width() / tileCount;
            for (int i = 0; i < tileCount; i++) {
                Bitmap bitmap = frames.get(i % frames.size());
                if (bitmap == null || bitmap.isRecycled()) continue;
                Rect src = centerCropSource(bitmap, tileWidth / clipHeight);
                RectF dst = new RectF(rect.left + (i * tileWidth), rect.top, rect.left + ((i + 1) * tileWidth), rect.bottom);
                canvas.drawBitmap(bitmap, src, dst, null);
            }
        }

        RectF labelRect = new RectF(rect.left, rect.top, rect.right, rect.top + dp(25f));
        canvas.drawRect(labelRect, labelBgPaint);
        String title = ellipsize(clip.title, Math.max(8, (int) (rect.width() / dp(7f))));
        canvas.drawText(title, rect.left + dp(8f), rect.top + dp(17f), labelPaint);
        canvas.restore();

        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, clipBorderPaint);
        if (index == selectedIndex) canvas.drawRoundRect(rect, cornerRadius, cornerRadius, selectedBorderPaint);
        if (index == draggingClipIndex) canvas.drawRoundRect(rect, cornerRadius, cornerRadius, dragOverlayPaint);

        if (index > 0) {
            canvas.drawLine(rect.left, rect.top + dp(7f), rect.left, rect.bottom - dp(7f), separatorPaint);
        }
    }

    private void drawCutMarker(Canvas canvas, float x) {
        float top = rulerHeight + dp(1f);
        float bottom = clipTop + clipHeight + dp(7f);
        canvas.drawLine(x, top, x, bottom, cutMarkerPaint);
        canvas.drawCircle(x, clipTop + dp(7f), dp(3.6f), cutMarkerPaint);
        canvas.drawText("✂", x - dp(8f), dp(30f), cutMarkerTextPaint);
    }

    private Rect centerCropSource(Bitmap bitmap, float targetRatio) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float ratio = h == 0 ? 1f : (float) w / (float) h;
        if (ratio > targetRatio) {
            int cropW = Math.max(1, Math.round(h * targetRatio));
            int left = Math.max(0, (w - cropW) / 2);
            return new Rect(left, 0, Math.min(w, left + cropW), h);
        }
        int cropH = Math.max(1, Math.round(w / Math.max(0.01f, targetRatio)));
        int top = Math.max(0, (h - cropH) / 2);
        return new Rect(0, top, w, Math.min(h, top + cropH));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;

        float playheadX = xForGlobalMs(playheadMs);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && Math.abs(event.getX() - playheadX) <= playheadHitRadius) {
            draggingPlayhead = true;
            getParent().requestDisallowInterceptTouchEvent(true);
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            updatePlayheadFromTouch(event.getX(), false);
            return true;
        }

        if (draggingPlayhead) {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                updatePlayheadFromTouch(event.getX(), false);
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                updatePlayheadFromTouch(event.getX(), true);
                draggingPlayhead = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                return true;
            }
        }

        gestureDetector.onTouchEvent(event);

        if (draggingClipIndex >= 0) {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                int target = clipIndexForX(event.getX());
                if (target >= 0 && target != draggingClipIndex) {
                    int from = draggingClipIndex;
                    draggingClipIndex = target;
                    selectedIndex = target;
                    if (listener != null) listener.onClipMoveRequested(from, target);
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    invalidate();
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                draggingClipIndex = -1;
                getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                performClick();
                return true;
            }
        }

        return true;
    }

    private void updatePlayheadFromTouch(float x, boolean finished) {
        long global = globalMsForX(x);
        playheadMs = global;
        int index = clipIndexForX(x);
        if (index >= 0 && index != selectedIndex) {
            selectedIndex = index;
            if (listener != null) listener.onClipSelected(index);
        }
        invalidate();
        if (listener != null) listener.onTimelineSeek(global, finished);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int clipIndexForX(float touchX) {
        if (clips.isEmpty()) return -1;
        float x = leftPadding;
        for (int i = 0; i < clips.size(); i++) {
            float width = clipWidth(clips.get(i));
            if (touchX >= x && touchX <= x + width) return i;
            x += width;
        }
        if (touchX < leftPadding) return 0;
        return clips.size() - 1;
    }

    private long globalMsForX(float touchX) {
        if (clips.isEmpty()) return 0L;
        float x = leftPadding;
        long accumulated = 0L;
        for (ClipData clip : clips) {
            float width = clipWidth(clip);
            if (touchX <= x + width) {
                float ratio = (touchX - x) / Math.max(1f, width);
                ratio = Math.max(0f, Math.min(1f, ratio));
                return clamp(accumulated + Math.round(ratio * clip.durationMs()), 0L, totalDurationMs());
            }
            x += width;
            accumulated += clip.durationMs();
        }
        return totalDurationMs();
    }

    private float clipWidth(ClipData clip) {
        float scaled = (clip.durationMs() / 1000f) * pixelsPerSecond;
        return Math.max(minClipWidth, scaled);
    }

    private long totalDurationMs() {
        long total = 0L;
        for (ClipData clip : clips) total += clip.durationMs();
        return Math.max(0L, total);
    }

    private void ensureThumbnails() {
        for (ClipData clip : clips) {
            final String key = clip.cacheKey();
            if (thumbnailCache.containsKey(key)) continue;
            thumbnailCache.put(key, new ArrayList<>());
            thumbnailExecutor.submit(() -> loadThumbnails(clip, key));
        }
    }

    private void loadThumbnails(ClipData clip, String key) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        ArrayList<Bitmap> frames = new ArrayList<>();
        try {
            retriever.setDataSource(clip.file.getAbsolutePath());
            final int count = 6;
            long duration = clip.durationMs();
            for (int i = 0; i < count; i++) {
                long local = count == 1 ? 0L : Math.round((duration - 1L) * (i / (double) (count - 1)));
                long sourceMs = clip.startMs + local;
                Bitmap bitmap;
                try {
                    bitmap = retriever.getScaledFrameAtTime(
                            sourceMs * 1000L,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            180,
                            102
                    );
                } catch (Throwable ignored) {
                    bitmap = retriever.getFrameAtTime(sourceMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                }
                if (bitmap != null) frames.add(bitmap);
            }
        } catch (Exception ignored) {
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
        }

        mainHandler.post(() -> {
            List<Bitmap> old = thumbnailCache.put(key, frames);
            if (old != null && old != frames) recycleBitmaps(old);
            invalidate();
        });
    }

    public void releaseResources() {
        thumbnailExecutor.shutdownNow();
        for (List<Bitmap> bitmaps : thumbnailCache.values()) recycleBitmaps(bitmaps);
        thumbnailCache.clear();
    }

    private void recycleBitmaps(List<Bitmap> bitmaps) {
        if (bitmaps == null) return;
        for (Bitmap bitmap : bitmaps) {
            if (bitmap != null && !bitmap.isRecycled()) {
                try { bitmap.recycle(); } catch (Exception ignored) { }
            }
        }
    }

    private String ellipsize(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(1, maxChars - 1)) + "…";
    }

    private String formatRulerTime(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
