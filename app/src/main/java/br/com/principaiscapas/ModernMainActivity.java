package br.com.principaiscapas;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Camada visual do Principais Capas.
 *
 * IMPORTANTE: esta Activity herda integralmente toda a logica da MainActivity.
 * Nenhuma rotina de busca, download, selecao, revisao, insercao manual,
 * Apps Script/Gmail ou geracao/abertura de PDF e substituida aqui.
 * Esta classe apenas aplica o novo tema visual sobre a interface existente.
 */
public class ModernMainActivity extends MainActivity {

    private static final int BG = 0xFF090E17;
    private static final int SURFACE = 0xFF111827;
    private static final int SURFACE_2 = 0xFF161E2A;
    private static final int BORDER = 0xFF263244;
    private static final int PURPLE = 0xFF8B5CF6;
    private static final int PURPLE_DARK = 0xFF6D4AE6;
    private static final int PURPLE_SOFT = 0xFF241B45;
    private static final int TEXT = 0xFFF8FAFC;
    private static final int MUTED = 0xFF9CA3AF;
    private static final int GREEN = 0xFF34D399;
    private static final int RED = 0xFFF87171;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySystemChrome();
        restyleWholeScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        View content = findViewById(android.R.id.content);
        if (content != null) content.post(this::restyleWholeScreen);
    }

    private void applySystemChrome() {
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = window.getDecorView().getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            window.getDecorView().setSystemUiVisibility(flags);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int flags = window.getDecorView().getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void restyleWholeScreen() {
        View content = findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;

        ViewGroup frame = (ViewGroup) content;
        frame.setBackgroundColor(BG);
        if (frame.getChildCount() > 0) {
            View root = frame.getChildAt(0);
            root.setBackgroundColor(BG);
            styleTree(root);
        }
    }

    private void styleTree(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            styleGroup(group);

            group.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                @Override
                public void onChildViewAdded(View parent, View child) {
                    if (child != null) child.post(() -> styleTree(child));
                }

                @Override
                public void onChildViewRemoved(View parent, View child) {
                    // Nada a fazer. A logica da tela continua sob responsabilidade da MainActivity.
                }
            });

            for (int i = 0; i < group.getChildCount(); i++) {
                styleTree(group.getChildAt(i));
            }
        }

        // CheckBox tambem herda de Button; por isso precisa ser tratado primeiro.
        if (view instanceof CheckBox) {
            styleCheckBox((CheckBox) view);
        } else if (view instanceof Button) {
            styleButton((Button) view);
        } else if (view instanceof TextView) {
            styleText((TextView) view);
        } else if (view instanceof ProgressBar) {
            styleProgress((ProgressBar) view);
        } else if (view instanceof ImageView) {
            styleImage((ImageView) view);
        }
    }

    private void styleGroup(ViewGroup group) {
        if (isHero(group)) {
            group.setBackground(gradientCard(
                    new int[]{0xFF181429, 0xFF111827},
                    22,
                    1,
                    0xFF3D2C66
            ));
            group.setElevation(dp(4));
            return;
        }

        if (isCoverCard(group)) {
            group.setBackground(rounded(SURFACE, 18, 1, BORDER));
            group.setElevation(dp(2));
            return;
        }

        if (group instanceof ScrollView) {
            group.setBackgroundColor(BG);
        }
    }

    private boolean isHero(ViewGroup group) {
        if (group.getChildCount() < 2) return false;
        boolean hasIcon = false;
        boolean hasTitle = false;

        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ImageView) hasIcon = true;
            if (containsText(child, "PRINCIPAIS CAPAS")) hasTitle = true;
        }
        return hasIcon && hasTitle;
    }

    private boolean isCoverCard(ViewGroup group) {
        return group.getChildCount() > 0 && group.getChildAt(0) instanceof CheckBox;
    }

    private boolean containsText(View view, String wanted) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            return text != null && wanted.equalsIgnoreCase(text.toString().trim());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsText(group.getChildAt(i), wanted)) return true;
            }
        }
        return false;
    }

    private void styleText(TextView textView) {
        String text = value(textView);

        if ("PRINCIPAIS CAPAS".equalsIgnoreCase(text)) {
            textView.setTextColor(TEXT);
            textView.setTextSize(24);
            textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            return;
        }

        if (text.startsWith("Localiza a capa correta")) {
            textView.setTextColor(MUTED);
            textView.setTextSize(13);
            return;
        }

        View parent = (View) textView.getParent();
        if (parent instanceof ViewGroup && isHero((ViewGroup) parent)) {
            textView.setTextColor(text.matches(".*\\d{2}.*") ? 0xFFC4B5FD : MUTED);
            return;
        }

        if (parent instanceof ViewGroup && isCoverCard((ViewGroup) parent)) {
            if (text.startsWith("Falha")) {
                textView.setTextColor(RED);
            } else if (text.contains("Sem edição") || text.contains("Aguardando") || text.contains("Pendente")) {
                textView.setTextColor(MUTED);
            } else if (text.contains("Encontr") || text.contains("Concl") || text.contains("OK") || text.contains("capa")) {
                textView.setTextColor(GREEN);
            } else {
                textView.setTextColor(MUTED);
            }
            return;
        }

        if (text.startsWith("A base do aplicativo")) {
            textView.setTextColor(MUTED);
        }
    }

    private void styleCheckBox(CheckBox checkBox) {
        checkBox.setTextColor(TEXT);
        checkBox.setTextSize(16);
        checkBox.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int[][] states = new int[][]{
                    new int[]{android.R.attr.state_checked},
                    new int[]{-android.R.attr.state_checked}
            };
            int[] colors = new int[]{PURPLE, 0xFF64748B};
            checkBox.setButtonTintList(new ColorStateList(states, colors));
        }
    }

    private void styleButton(Button button) {
        String text = value(button).toUpperCase();
        button.setAllCaps(false);
        button.setElevation(0);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        if (text.contains("ATUALIZAR CAPAS") || text.equals("GERAR PDF")) {
            button.setTextColor(Color.WHITE);
            button.setBackground(gradientCard(
                    new int[]{PURPLE_DARK, PURPLE},
                    14,
                    0,
                    0
            ));
            return;
        }

        if (text.contains("REVISAR CAPA")) {
            button.setTextColor(0xFFD8CCFF);
            button.setBackground(rounded(PURPLE_SOFT, 12, 1, 0xFF5B4B92));
            return;
        }

        if (text.contains("INSERIR CAPA MANUALMENTE") || text.contains("TROCAR CAPA MANUAL")) {
            button.setTextColor(0xFFFFE8A3);
            button.setBackground(rounded(0xFF2A2319, 12, 1, 0xFF6B5420));
            return;
        }

        if (text.contains("VOLTAR PARA CAPA AUTOMÁTICA")) {
            button.setTextColor(MUTED);
            button.setBackground(rounded(SURFACE_2, 12, 1, BORDER));
            return;
        }

        if (text.contains("ABRIR PDF GERADO")) {
            button.setTextColor(0xFFD8CCFF);
            button.setBackground(rounded(SURFACE_2, 12, 1, 0xFF5B4B92));
            return;
        }

        if (text.contains("DATA DA BUSCA") ||
                text.contains("MARCAR TODAS") ||
                text.contains("DESMARCAR") ||
                text.contains("GMAIL") ||
                text.contains("APPS SCRIPT")) {
            button.setTextColor(TEXT);
            button.setBackground(rounded(SURFACE_2, 12, 1, BORDER));
            return;
        }

        button.setTextColor(TEXT);
        button.setBackground(rounded(SURFACE_2, 12, 1, BORDER));
    }

    private void styleProgress(ProgressBar progressBar) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progressBar.setProgressTintList(ColorStateList.valueOf(PURPLE));
            progressBar.setIndeterminateTintList(ColorStateList.valueOf(PURPLE));
        }
    }

    private void styleImage(ImageView imageView) {
        View parent = (View) imageView.getParent();
        if (parent instanceof ViewGroup && isCoverCard((ViewGroup) parent)) {
            imageView.setBackground(rounded(0xFF0D1420, 12, 1, BORDER));
            imageView.setPadding(dp(6), dp(6), dp(6), dp(6));
        }
    }

    private GradientDrawable rounded(int fill, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private GradientDrawable gradientCard(int[] colors, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private String value(TextView view) {
        CharSequence text = view.getText();
        return text == null ? "" : text.toString().trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
