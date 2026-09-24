package br.com.editorpdf;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * ImageView com moldura de recorte ajustavel.
 *
 * v0.2.3: mantem o mapeamento preciso da selecao para o bitmap e melhora a
 * manipulacao da moldura: tamanho minimo reduzido e escolha do manipulador
 * pelo ponto realmente mais proximo, evitando travamentos em selecoes pequenas.
 */
public class CropImageView extends AppCompatImageView {
    public interface OnCropSelectedListener {
        void onCropSelected(RectF normalizedCrop);
    }

    private enum DragMode {
        NONE,
        MOVE,
        LEFT, TOP, RIGHT, BOTTOM,
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF selection = new RectF();
    private final RectF downSelection = new RectF();

    private boolean cropMode = false;
    private float downX;
    private float downY;
    private DragMode dragMode = DragMode.NONE;
    private OnCropSelectedListener listener;

    public CropImageView(Context context) {
        super(context);
        init();
    }

    public CropImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setScaleType(ScaleType.FIT_CENTER);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(2));
        borderPaint.setColor(0xFFFFFFFF);

        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(dp(4));
        cornerPaint.setStrokeCap(Paint.Cap.SQUARE);
        cornerPaint.setColor(0xFFFFFFFF);

        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(0xFFFFFFFF);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1));
        gridPaint.setColor(0x99FFFFFF);

        shadePaint.setStyle(Paint.Style.FILL);
        shadePaint.setColor(0x88000000);
        setBackgroundColor(0xFF151922);
    }

    public void setOnCropSelectedListener(OnCropSelectedListener listener) {
        this.listener = listener;
    }

    public void setCropMode(boolean enabled) {
        cropMode = enabled;
        dragMode = DragMode.NONE;

        if (enabled) {
            createDefaultSelection();
        } else {
            selection.setEmpty();
        }
        invalidate();
    }

    public boolean isCropMode() {
        return cropMode;
    }

    public void clearSelection() {
        selection.setEmpty();
        if (cropMode) createDefaultSelection();
        invalidate();
    }

    public boolean hasValidSelection() {
        return cropMode && !selection.isEmpty()
                && selection.width() >= minSelectionSize()
                && selection.height() >= minSelectionSize();
    }

    /**
     * Retorna a selecao normalizada entre 0 e 1 na coordenada REAL do bitmap.
     *
     * O overlay vive em coordenadas da View. O bitmap, porem, e desenhado apos
     * padding + imageMatrix (FIT_CENTER). Portanto nao basta dividir pelo
     * retangulo aparente: convertemos os pontos de volta ao espaco do Drawable
     * usando a matriz inversa. Assim o corte exportado coincide com a moldura.
     */
    @Nullable
    public RectF getNormalizedSelection() {
        if (!hasValidSelection()) return null;
        Drawable drawable = getDrawable();
        if (drawable == null) return null;

        Rect bounds = drawable.getBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0) return null;

        Matrix inverse = new Matrix();
        if (!getImageMatrix().invert(inverse)) return null;

        float[] points = new float[]{
                selection.left - getPaddingLeft(),
                selection.top - getPaddingTop(),
                selection.right - getPaddingLeft(),
                selection.bottom - getPaddingTop()
        };
        inverse.mapPoints(points);

        float left = (points[0] - bounds.left) / bounds.width();
        float top = (points[1] - bounds.top) / bounds.height();
        float right = (points[2] - bounds.left) / bounds.width();
        float bottom = (points[3] - bounds.top) / bounds.height();

        left = clamp01(left);
        top = clamp01(top);
        right = clamp01(right);
        bottom = clamp01(bottom);

        if (right <= left || bottom <= top) return null;
        return new RectF(left, top, right, bottom);
    }

    private void createDefaultSelection() {
        RectF img = imageRect();
        if (img.width() <= 0 || img.height() <= 0) {
            selection.setEmpty();
            return;
        }

        float insetX = img.width() * 0.05f;
        float insetY = img.height() * 0.05f;
        selection.set(img.left + insetX, img.top + insetY,
                img.right - insetX, img.bottom - insetY);
        notifySelectionChanged();
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /** Retangulo exato em que o Drawable e pintado dentro da View. */
    private RectF imageRect() {
        Drawable drawable = getDrawable();
        if (drawable == null) return new RectF();

        RectF rect = new RectF(drawable.getBounds());
        getImageMatrix().mapRect(rect);

        // ImageView aplica o padding no Canvas antes de concatenar imageMatrix.
        // getImageMatrix() nao inclui essa translacao, portanto ela precisa ser
        // adicionada explicitamente para o overlay coincidir com o bitmap.
        rect.offset(getPaddingLeft(), getPaddingTop());
        return rect;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!cropMode || getDrawable() == null) return super.onTouchEvent(event);

        RectF img = imageRect();
        if (img.width() <= 0 || img.height() <= 0) return true;
        if (selection.isEmpty()) createDefaultSelection();

        float x = clamp(event.getX(), img.left, img.right);
        float y = clamp(event.getY(), img.top, img.bottom);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                downSelection.set(selection);
                dragMode = hitTest(event.getX(), event.getY());
                getParent().requestDisallowInterceptTouchEvent(dragMode != DragMode.NONE);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (dragMode == DragMode.NONE) return true;
                updateSelection(x, y, img);
                invalidate();
                notifySelectionChanged();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragMode != DragMode.NONE) {
                    updateSelection(x, y, img);
                    invalidate();
                    notifySelectionChanged();
                }
                dragMode = DragMode.NONE;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return true;
    }

    private DragMode hitTest(float x, float y) {
        if (selection.isEmpty()) return DragMode.NONE;

        // Uma pequena regiao central fica reservada para mover a selecao.
        // Assim, mesmo quando o retangulo esta pequeno, ele nao fica preso nos handles.
        RectF moveZone = new RectF(selection);
        float inset = Math.min(dp(10), Math.min(selection.width(), selection.height()) * 0.22f);
        moveZone.inset(inset, inset);
        if (moveZone.width() > 0 && moveZone.height() > 0 && moveZone.contains(x, y)) {
            return DragMode.MOVE;
        }

        float h = touchRadius();
        float maxD2 = h * h;
        DragMode best = DragMode.NONE;
        float bestD2 = Float.MAX_VALUE;

        float[][] points = new float[][]{
                {selection.left, selection.top},
                {selection.right, selection.top},
                {selection.left, selection.bottom},
                {selection.right, selection.bottom}
        };
        DragMode[] modes = new DragMode[]{
                DragMode.TOP_LEFT, DragMode.TOP_RIGHT,
                DragMode.BOTTOM_LEFT, DragMode.BOTTOM_RIGHT
        };

        for (int i = 0; i < points.length; i++) {
            float dx = x - points[i][0];
            float dy = y - points[i][1];
            float d2 = dx * dx + dy * dy;
            if (d2 <= maxD2 && d2 < bestD2) {
                bestD2 = d2;
                best = modes[i];
            }
        }
        if (best != DragMode.NONE) return best;

        // Depois dos cantos, escolhe a borda mais proxima. Isso evita que, em
        // selecoes estreitas, tocar do lado direito acione a borda esquerda.
        float edgeDistance = Float.MAX_VALUE;
        if (y >= selection.top - h && y <= selection.bottom + h) {
            float dLeft = Math.abs(x - selection.left);
            if (dLeft <= h && dLeft < edgeDistance) {
                edgeDistance = dLeft;
                best = DragMode.LEFT;
            }
            float dRight = Math.abs(x - selection.right);
            if (dRight <= h && dRight < edgeDistance) {
                edgeDistance = dRight;
                best = DragMode.RIGHT;
            }
        }
        if (x >= selection.left - h && x <= selection.right + h) {
            float dTop = Math.abs(y - selection.top);
            if (dTop <= h && dTop < edgeDistance) {
                edgeDistance = dTop;
                best = DragMode.TOP;
            }
            float dBottom = Math.abs(y - selection.bottom);
            if (dBottom <= h && dBottom < edgeDistance) {
                best = DragMode.BOTTOM;
            }
        }
        if (best != DragMode.NONE) return best;

        if (selection.contains(x, y)) return DragMode.MOVE;
        return DragMode.NONE;
    }

    private void updateSelection(float x, float y, RectF img) {
        float dx = x - downX;
        float dy = y - downY;
        float min = minSelectionSize();

        RectF r = new RectF(downSelection);

        switch (dragMode) {
            case MOVE:
                r.offset(dx, dy);
                if (r.left < img.left) r.offset(img.left - r.left, 0);
                if (r.right > img.right) r.offset(img.right - r.right, 0);
                if (r.top < img.top) r.offset(0, img.top - r.top);
                if (r.bottom > img.bottom) r.offset(0, img.bottom - r.bottom);
                break;

            case LEFT:
                r.left = clamp(downSelection.left + dx, img.left, r.right - min);
                break;
            case RIGHT:
                r.right = clamp(downSelection.right + dx, r.left + min, img.right);
                break;
            case TOP:
                r.top = clamp(downSelection.top + dy, img.top, r.bottom - min);
                break;
            case BOTTOM:
                r.bottom = clamp(downSelection.bottom + dy, r.top + min, img.bottom);
                break;

            case TOP_LEFT:
                r.left = clamp(downSelection.left + dx, img.left, r.right - min);
                r.top = clamp(downSelection.top + dy, img.top, r.bottom - min);
                break;
            case TOP_RIGHT:
                r.right = clamp(downSelection.right + dx, r.left + min, img.right);
                r.top = clamp(downSelection.top + dy, img.top, r.bottom - min);
                break;
            case BOTTOM_LEFT:
                r.left = clamp(downSelection.left + dx, img.left, r.right - min);
                r.bottom = clamp(downSelection.bottom + dy, r.top + min, img.bottom);
                break;
            case BOTTOM_RIGHT:
                r.right = clamp(downSelection.right + dx, r.left + min, img.right);
                r.bottom = clamp(downSelection.bottom + dy, r.top + min, img.bottom);
                break;
            case NONE:
                return;
        }

        selection.set(r);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void notifySelectionChanged() {
        RectF normalized = getNormalizedSelection();
        if (normalized != null && listener != null) {
            listener.onCropSelected(normalized);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!cropMode) return;
        if (selection.isEmpty()) createDefaultSelection();
        if (selection.isEmpty()) return;

        RectF img = imageRect();
        canvas.save();
        canvas.clipRect(img);

        canvas.drawRect(img.left, img.top, img.right, selection.top, shadePaint);
        canvas.drawRect(img.left, selection.bottom, img.right, img.bottom, shadePaint);
        canvas.drawRect(img.left, selection.top, selection.left, selection.bottom, shadePaint);
        canvas.drawRect(selection.right, selection.top, img.right, selection.bottom, shadePaint);

        drawGrid(canvas, selection);
        canvas.drawRect(selection, borderPaint);
        drawCorners(canvas, selection);
        drawHandles(canvas, selection);
        canvas.restore();
    }

    private void drawGrid(Canvas canvas, RectF r) {
        float x1 = r.left + r.width() / 3f;
        float x2 = r.left + (r.width() * 2f) / 3f;
        float y1 = r.top + r.height() / 3f;
        float y2 = r.top + (r.height() * 2f) / 3f;
        canvas.drawLine(x1, r.top, x1, r.bottom, gridPaint);
        canvas.drawLine(x2, r.top, x2, r.bottom, gridPaint);
        canvas.drawLine(r.left, y1, r.right, y1, gridPaint);
        canvas.drawLine(r.left, y2, r.right, y2, gridPaint);
    }

    private void drawCorners(Canvas canvas, RectF r) {
        float len = Math.min(dp(22), Math.min(r.width(), r.height()) / 4f);

        canvas.drawLine(r.left, r.top, r.left + len, r.top, cornerPaint);
        canvas.drawLine(r.left, r.top, r.left, r.top + len, cornerPaint);

        canvas.drawLine(r.right - len, r.top, r.right, r.top, cornerPaint);
        canvas.drawLine(r.right, r.top, r.right, r.top + len, cornerPaint);

        canvas.drawLine(r.left, r.bottom, r.left + len, r.bottom, cornerPaint);
        canvas.drawLine(r.left, r.bottom - len, r.left, r.bottom, cornerPaint);

        canvas.drawLine(r.right - len, r.bottom, r.right, r.bottom, cornerPaint);
        canvas.drawLine(r.right, r.bottom - len, r.right, r.bottom, cornerPaint);
    }

    private void drawHandles(Canvas canvas, RectF r) {
        float radius = dp(4.5f);
        float cx = r.centerX();
        float cy = r.centerY();

        canvas.drawCircle(r.left, r.top, radius, handlePaint);
        canvas.drawCircle(r.right, r.top, radius, handlePaint);
        canvas.drawCircle(r.left, r.bottom, radius, handlePaint);
        canvas.drawCircle(r.right, r.bottom, radius, handlePaint);

        canvas.drawCircle(cx, r.top, radius, handlePaint);
        canvas.drawCircle(cx, r.bottom, radius, handlePaint);
        canvas.drawCircle(r.left, cy, radius, handlePaint);
        canvas.drawCircle(r.right, cy, radius, handlePaint);
    }

    private float touchRadius() {
        return dp(22);
    }

    private float minSelectionSize() {
        return dp(10);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
