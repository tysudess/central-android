package br.com.extratorvideos;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.HapticFeedbackConstants;

/**
 * Barra de seleção com três controles independentes:
 * - bolinha esquerda = início
 * - bolinha direita = fim
 * - linha fina = posição atual da prévia
 *
 * Tocar/arrastar fora das bolinhas move somente a posição atual.
 */
public class RangeSelectionView extends View {

    public static final int THUMB_START = 0;
    public static final int THUMB_END = 1;
    public static final int THUMB_PLAYHEAD = 2;

    public interface OnRangeChangeListener {
        void onRangeChanged(long startMs, long endMs, int activeThumb, boolean fromUser);
        void onRangeChangeFinished(long startMs, long endMs, int activeThumb);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playheadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playheadHeadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint deleteRegionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint deleteMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private long maxMs = 1L;
    private long startMs = 0L;
    private long endMs = 1L;
    private long playheadMs = 0L;
    private long deleteCutAMs = -1L;
    private long deleteCutBMs = -1L;

    private int activeThumb = -1;
    private OnRangeChangeListener listener;

    private final float horizontalPadding;
    private final float thumbRadius;
    private final float hitRadius;
    private final float trackHeight;

    public RangeSelectionView(Context context) {
        this(context, null);
    }

    public RangeSelectionView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RangeSelectionView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        horizontalPadding = dp(18f);
        thumbRadius = dp(15f);
        hitRadius = dp(26f);
        trackHeight = dp(7f);

        trackPaint.setColor(Color.rgb(35, 57, 82));
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        selectedPaint.setColor(Color.rgb(95, 124, 255));
        selectedPaint.setStrokeCap(Paint.Cap.ROUND);

        thumbPaint.setColor(Color.rgb(10, 19, 32));
        thumbStrokePaint.setColor(Color.rgb(53, 231, 215));
        thumbStrokePaint.setStyle(Paint.Style.STROKE);
        thumbStrokePaint.setStrokeWidth(dp(3.5f));

        playheadPaint.setColor(Color.rgb(165, 107, 255));
        playheadPaint.setStrokeWidth(dp(3f));
        playheadPaint.setStrokeCap(Paint.Cap.ROUND);
        playheadHeadPaint.setColor(Color.rgb(165, 107, 255));

        deleteRegionPaint.setColor(Color.argb(82, 255, 91, 119));
        deleteRegionPaint.setStrokeWidth(trackHeight + dp(8f));
        deleteRegionPaint.setStrokeCap(Paint.Cap.ROUND);
        deleteMarkerPaint.setColor(Color.rgb(255, 91, 119));
        deleteMarkerPaint.setStrokeWidth(dp(3f));
        deleteMarkerPaint.setStrokeCap(Paint.Cap.ROUND);

        setClickable(true);
        setFocusable(true);
        setContentDescription("Seleção do início, fim e posição atual do vídeo");
    }

    public void setOnRangeChangeListener(OnRangeChangeListener listener) {
        this.listener = listener;
    }

    public void setDuration(long durationMs) {
        maxMs = Math.max(1L, durationMs);
        startMs = clamp(startMs, 0L, maxMs);
        endMs = clamp(endMs, startMs, maxMs);
        playheadMs = clamp(playheadMs, 0L, maxMs);
        invalidate();
    }

    public void setSelection(long start, long end) {
        long safeStart = clamp(start, 0L, maxMs);
        long safeEnd = clamp(end, safeStart, maxMs);
        startMs = safeStart;
        endMs = safeEnd;
        invalidate();
    }

    public void setPlayhead(long positionMs) {
        playheadMs = clamp(positionMs, 0L, maxMs);
        invalidate();
    }

    public long getStartMs() { return startMs; }
    public long getEndMs() { return endMs; }
    public long getPlayheadMs() { return playheadMs; }

    public void setDeleteMarkers(long cutA, long cutB) {
        deleteCutAMs = cutA >= 0L ? clamp(cutA, 0L, maxMs) : -1L;
        deleteCutBMs = cutB >= 0L ? clamp(cutB, 0L, maxMs) : -1L;
        invalidate();
    }

    public void clearDeleteMarkers() {
        deleteCutAMs = -1L;
        deleteCutBMs = -1L;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float left = horizontalPadding;
        float right = Math.max(left + 1f, getWidth() - horizontalPadding);
        float centerY = getHeight() / 2f;

        float startX = xForValue(startMs, left, right);
        float endX = xForValue(endMs, left, right);
        float playheadX = xForValue(playheadMs, left, right);

        trackPaint.setStrokeWidth(trackHeight);
        selectedPaint.setStrokeWidth(trackHeight + dp(2f));

        canvas.drawLine(left, centerY, right, centerY, trackPaint);
        canvas.drawLine(startX, centerY, endX, centerY, selectedPaint);

        if (deleteCutAMs >= 0L && deleteCutBMs >= 0L && deleteCutAMs != deleteCutBMs) {
            float aX = xForValue(Math.min(deleteCutAMs, deleteCutBMs), left, right);
            float bX = xForValue(Math.max(deleteCutAMs, deleteCutBMs), left, right);
            canvas.drawLine(aX, centerY, bX, centerY, deleteRegionPaint);
        }
        if (deleteCutAMs >= 0L) drawDeleteMarker(canvas, xForValue(deleteCutAMs, left, right), centerY, "A");
        if (deleteCutBMs >= 0L) drawDeleteMarker(canvas, xForValue(deleteCutBMs, left, right), centerY, "B");

        canvas.drawLine(playheadX, centerY - dp(25f), playheadX, centerY + dp(25f), playheadPaint);
        canvas.drawCircle(playheadX, centerY - dp(25f), dp(5f), playheadHeadPaint);

        drawThumb(canvas, startX, centerY, activeThumb == THUMB_START);
        drawThumb(canvas, endX, centerY, activeThumb == THUMB_END);
    }

    private void drawDeleteMarker(Canvas canvas, float x, float centerY, String label) {
        canvas.drawLine(x, centerY - dp(26f), x, centerY + dp(26f), deleteMarkerPaint);
        canvas.drawCircle(x, centerY - dp(27f), dp(8f), deleteMarkerPaint);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(dp(9f));
        text.setFakeBoldText(true);
        canvas.drawText(label, x, centerY - dp(24f), text);
    }

    private void drawThumb(Canvas canvas, float x, float y, boolean active) {
        if (active) {
            Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
            halo.setColor(Color.argb(88, 53, 231, 215));
            canvas.drawCircle(x, y, thumbRadius + dp(7f), halo);
        }
        canvas.drawCircle(x, y, thumbRadius, thumbPaint);
        canvas.drawCircle(x, y, thumbRadius, thumbStrokePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || maxMs <= 0L) return false;

        float left = horizontalPadding;
        float right = Math.max(left + 1f, getWidth() - horizontalPadding);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                activeThumb = escolherControle(event.getX(), left, right);
                atualizarControlePeloToque(event.getX(), left, right, true);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                atualizarControlePeloToque(event.getX(), left, right, true);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                atualizarControlePeloToque(event.getX(), left, right, true);
                if (listener != null && activeThumb >= 0) {
                    listener.onRangeChangeFinished(startMs, endMs, activeThumb);
                }
                activeThumb = -1;
                getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                performClick();
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int escolherControle(float touchX, float left, float right) {
        float startX = xForValue(startMs, left, right);
        float endX = xForValue(endMs, left, right);
        float ds = Math.abs(touchX - startX);
        float de = Math.abs(touchX - endX);

        // Só pega uma bolinha se o toque estiver realmente perto dela.
        // Fora das bolinhas, movimenta exclusivamente a posição atual.
        if (ds <= hitRadius || de <= hitRadius) {
            return ds <= de ? THUMB_START : THUMB_END;
        }
        return THUMB_PLAYHEAD;
    }

    private void atualizarControlePeloToque(float touchX, float left, float right, boolean fromUser) {
        if (activeThumb < 0) return;

        long value = valueForX(touchX, left, right);
        long minGap = maxMs >= 20L ? 10L : 1L;

        if (activeThumb == THUMB_START) {
            startMs = Math.min(value, Math.max(0L, endMs - minGap));
            playheadMs = startMs;
        } else if (activeThumb == THUMB_END) {
            endMs = Math.max(value, Math.min(maxMs, startMs + minGap));
            playheadMs = endMs;
        } else {
            playheadMs = value;
        }

        invalidate();
        if (listener != null) {
            listener.onRangeChanged(startMs, endMs, activeThumb, fromUser);
        }
    }

    private long valueForX(float x, float left, float right) {
        float clampedX = Math.max(left, Math.min(right, x));
        float ratio = (clampedX - left) / Math.max(1f, right - left);
        return clamp(Math.round(ratio * maxMs), 0L, maxMs);
    }

    private float xForValue(long value, float left, float right) {
        float ratio = maxMs <= 0L ? 0f : (float) value / (float) maxMs;
        return left + ratio * (right - left);
    }

    private long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
