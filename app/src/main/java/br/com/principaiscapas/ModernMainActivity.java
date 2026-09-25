package br.com.principaiscapas;

import br.com.centralmidia.android.ui.MobileScaffold;
import br.com.centralmidia.android.ui.CoversActivity;

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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Camada visual clara do módulo Principais Capas.
 *
 * A lógica de busca, Gmail/Apps Script, seleção, revisão e geração do PDF
 * continua integralmente em MainActivity. Esta classe somente transforma a
 * interface original para o mesmo padrão claro/azul da Central.
 */
public class ModernMainActivity extends MainActivity {

    private static final int BG = 0xFFF5F9FE;
    private static final int SURFACE = 0xFFFFFFFF;
    private static final int SURFACE_2 = 0xFFF8FBFF;

    private static final int BLUE = 0xFF147EF6;
    private static final int BLUE_DARK = 0xFF0D5FB9;
    private static final int BLUE_LIGHT = 0xFFEAF4FF;
    private static final int BLUE_LIGHT_2 = 0xFFF2F8FF;

    private static final int BORDER = 0xFFC9DDF2;
    private static final int BORDER_STRONG = 0xFF9EC6EF;

    private static final int TEXT = 0xFF0A2B63;
    private static final int MUTED = 0xFF6079A5;

    private static final int GREEN = 0xFF00975F;
    private static final int GREEN_LIGHT = 0xFFE8F9F0;

    private static final int RED = 0xFFD93462;
    private static final int RED_LIGHT = 0xFFFFEDF2;

    private static final int GOLD = 0xFF9A6500;
    private static final int GOLD_LIGHT = 0xFFFFF5DA;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySystemChrome();
        restyleWholeScreen();
        MobileScaffold.attachModule(this, CoversActivity.class);
    }

    @Override
    protected void onResume() {
        super.onResume();

        View content =
                findViewById(
                        android.R.id.content
                );

        if (content != null) {
            content.post(
                    this::restyleWholeScreen
            );
        }
    }

    private void applySystemChrome() {
        Window window =
                getWindow();

        window.setStatusBarColor(
                BG
        );

        window.setNavigationBarColor(
                Color.WHITE
        );

        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.M
        ) {
            int flags =
                    window.getDecorView()
                            .getSystemUiVisibility();

            flags |=
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;

            window.getDecorView()
                    .setSystemUiVisibility(
                            flags
                    );
        }

        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.O
        ) {
            int flags =
                    window.getDecorView()
                            .getSystemUiVisibility();

            flags |=
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;

            window.getDecorView()
                    .setSystemUiVisibility(
                            flags
                    );
        }
    }

    private void restyleWholeScreen() {
        View content =
                findViewById(
                        android.R.id.content
                );

        if (
                !(content instanceof ViewGroup)
        ) {
            return;
        }

        ViewGroup frame =
                (ViewGroup) content;

        frame.setBackgroundColor(
                BG
        );

        if (
                frame.getChildCount() >
                        0
        ) {
            View root =
                    frame.getChildAt(
                            0
                    );

            root.setBackgroundColor(
                    BG
            );

            styleTree(
                    root
            );
        }
    }

    private void styleTree(
            View view
    ) {
        if (
                view instanceof ViewGroup
        ) {
            ViewGroup group =
                    (ViewGroup) view;

            styleGroup(
                    group
            );

            group.setOnHierarchyChangeListener(
                    new ViewGroup.OnHierarchyChangeListener() {
                        @Override
                        public void onChildViewAdded(
                                View parent,
                                View child
                        ) {
                            if (
                                    child != null
                            ) {
                                child.post(
                                        () -> styleTree(
                                                child
                                        )
                                );
                            }
                        }

                        @Override
                        public void onChildViewRemoved(
                                View parent,
                                View child
                        ) {
                        }
                    }
            );

            for (
                    int i = 0;
                    i < group.getChildCount();
                    i++
            ) {
                styleTree(
                        group.getChildAt(
                                i
                        )
                );
            }
        }

        /*
         * CheckBox e Button também são TextView.
         * A ordem evita que o estilo genérico apague os botões.
         */
        if (
                view instanceof CheckBox
        ) {
            styleCheckBox(
                    (CheckBox) view
            );
        } else if (
                view instanceof Button
        ) {
            styleButton(
                    (Button) view
            );
        } else if (
                view instanceof TextView
        ) {
            styleText(
                    (TextView) view
            );
        } else if (
                view instanceof ProgressBar
        ) {
            styleProgress(
                    (ProgressBar) view
            );
        } else if (
                view instanceof ImageView
        ) {
            styleImage(
                    (ImageView) view
            );
        }
    }

    private void styleGroup(
            ViewGroup group
    ) {
        if (
                group instanceof ScrollView
        ) {
            group.setBackgroundColor(
                    BG
            );
            return;
        }

        if (
                isHero(
                        group
                )
        ) {
            group.setBackground(
                    gradientCard(
                            new int[]{
                                    0xFFFFFFFF,
                                    BLUE_LIGHT
                            },
                            20,
                            1,
                            BORDER
                    )
            );

            group.setElevation(
                    dp(2)
            );

            return;
        }

        if (
                isDateCard(
                        group
                )
        ) {
            group.setBackground(
                    rounded(
                            BLUE_LIGHT,
                            14,
                            1,
                            BORDER_STRONG
                    )
            );

            group.setElevation(
                    0
            );

            return;
        }

        if (
                isCoverCard(
                        group
                )
        ) {
            group.setBackground(
                    rounded(
                            SURFACE,
                            16,
                            1,
                            BORDER
                    )
            );

            group.setElevation(
                    dp(1)
            );

            return;
        }

        if (
                isBottomActions(
                        group
                )
        ) {
            group.setBackground(
                    rounded(
                            BLUE_LIGHT_2,
                            14,
                            1,
                            BORDER
                    )
            );

            return;
        }

        /*
         * O módulo original desenha vários LinearLayouts em azul-marinho.
         * Nos grupos auxiliares removemos esse fundo para que apareça o
         * fundo claro da Central.
         */
        if (
                group instanceof LinearLayout
        ) {
            group.setBackgroundColor(
                    Color.TRANSPARENT
            );
        }
    }

    private boolean isHero(
            ViewGroup group
    ) {
        return containsText(
                group,
                "PRINCIPAIS CAPAS"
        );
    }

    private boolean isDateCard(
            ViewGroup group
    ) {
        return containsText(
                group,
                "DATA"
        ) &&
                !containsText(
                        group,
                        "PRINCIPAIS CAPAS"
                );
    }

    private boolean isBottomActions(
            ViewGroup group
    ) {
        return containsText(
                group,
                "ATUALIZAR"
        ) &&
                containsText(
                        group,
                        "GERAR PDF"
                );
    }

    private boolean isCoverCard(
            ViewGroup group
    ) {
        if (
                group.getChildCount() <=
                        0
        ) {
            return false;
        }

        View first =
                group.getChildAt(
                        0
                );

        if (
                first instanceof CheckBox
        ) {
            return true;
        }

        if (
                first instanceof ViewGroup
        ) {
            ViewGroup firstGroup =
                    (ViewGroup) first;

            return firstGroup.getChildCount() >
                    0 &&
                    firstGroup.getChildAt(
                            0
                    ) instanceof CheckBox;
        }

        return false;
    }

    private boolean containsText(
            View view,
            String wanted
    ) {
        if (
                view instanceof TextView
        ) {
            CharSequence text =
                    ((TextView) view)
                            .getText();

            if (
                    text != null &&
                    text.toString()
                            .toUpperCase()
                            .contains(
                                    wanted.toUpperCase()
                            )
            ) {
                return true;
            }
        }

        if (
                view instanceof ViewGroup
        ) {
            ViewGroup group =
                    (ViewGroup) view;

            for (
                    int i = 0;
                    i < group.getChildCount();
                    i++
            ) {
                if (
                        containsText(
                                group.getChildAt(
                                        i
                                ),
                                wanted
                        )
                ) {
                    return true;
                }
            }
        }

        return false;
    }

    private void styleText(
            TextView textView
    ) {
        String text =
                value(
                        textView
                );

        String upper =
                text.toUpperCase();

        textView.setTextColor(
                TEXT
        );

        if (
                "PRINCIPAIS CAPAS"
                        .equalsIgnoreCase(
                                text
                        )
        ) {
            textView.setTextColor(
                    TEXT
            );

            textView.setTextSize(
                    24
            );

            textView.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );

            return;
        }

        if (
                upper.equals(
                        "DATA"
                )
        ) {
            textView.setTextColor(
                    MUTED
            );
            return;
        }

        if (
                upper.contains(
                        "TOQUE PARA ALTERAR"
                ) ||
                upper.contains(
                        "AGUARDANDO"
                ) ||
                upper.contains(
                        "PENDENTE"
                ) ||
                upper.contains(
                        "SEM EDIÇÃO"
                ) ||
                upper.contains(
                        "LOCALIZANDO"
                ) ||
                upper.contains(
                        "PROCURANDO"
                ) ||
                upper.contains(
                        "ABRINDO"
                )
        ) {
            textView.setTextColor(
                    MUTED
            );
        }

        if (
                upper.startsWith(
                        "FALHA"
                ) ||
                upper.contains(
                        "ERRO"
                )
        ) {
            textView.setTextColor(
                    RED
            );
        }

        if (
                upper.contains(
                        "ENCONTR"
                ) ||
                upper.contains(
                        "CONCLU"
                ) ||
                upper.contains(
                        "INCLUÍDO NO PDF"
                )
        ) {
            textView.setTextColor(
                    GREEN
            );
        }

        if (
                text.matches(
                        "\\d+"
                )
        ) {
            textView.setTextColor(
                    BLUE_DARK
            );

            textView.setBackground(
                    rounded(
                            BLUE_LIGHT,
                            22,
                            1,
                            BORDER_STRONG
                    )
            );
        }

        if (
                upper.contains(
                        "SELECIONADO, AGUARDANDO"
                ) ||
                upper.contains(
                        "FORA DO PDF"
                )
        ) {
            textView.setTextColor(
                    MUTED
            );

            textView.setBackground(
                    rounded(
                            BLUE_LIGHT_2,
                            10,
                            1,
                            BORDER
                    )
            );
        }

        if (
                upper.contains(
                        "SERÁ INCLUÍDO NO PDF"
                )
        ) {
            textView.setTextColor(
                    GREEN
            );

            textView.setBackground(
                    rounded(
                            GREEN_LIGHT,
                            10,
                            1,
                            0xFFBCE7D2
                    )
            );
        }
    }

    private void styleCheckBox(
            CheckBox checkBox
    ) {
        checkBox.setTextColor(
                TEXT
        );

        checkBox.setTextSize(
                15
        );

        checkBox.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.LOLLIPOP
        ) {
            int[][] states =
                    new int[][]{
                            new int[]{
                                    android.R.attr.state_checked
                            },
                            new int[]{
                                    -android.R.attr.state_checked
                            }
                    };

            int[] colors =
                    new int[]{
                            BLUE,
                            0xFF9DB1C7
                    };

            checkBox.setButtonTintList(
                    new ColorStateList(
                            states,
                            colors
                    )
            );
        }
    }

    private void styleButton(
            Button button
    ) {
        String text =
                value(
                        button
                )
                        .toUpperCase();

        button.setAllCaps(
                false
        );

        button.setElevation(
                0
        );

        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        if (
                text.contains(
                        "ATUALIZAR"
                ) ||
                text.equals(
                        "GERAR PDF"
                )
        ) {
            button.setTextColor(
                    BLUE_DARK
            );

            button.setBackground(
                    rounded(
                            BLUE_LIGHT,
                            13,
                            1,
                            BORDER_STRONG
                    )
            );

            return;
        }

        if (
                text.contains(
                        "REVISAR CAPA"
                )
        ) {
            button.setTextColor(
                    BLUE_DARK
            );

            button.setBackground(
                    rounded(
                            BLUE_LIGHT,
                            12,
                            1,
                            BORDER_STRONG
                    )
            );

            return;
        }

        if (
                text.contains(
                        "INSERIR CAPA MANUALMENTE"
                ) ||
                text.contains(
                        "TROCAR CAPA MANUAL"
                )
        ) {
            button.setTextColor(
                    GOLD
            );

            button.setBackground(
                    rounded(
                            GOLD_LIGHT,
                            12,
                            1,
                            0xFFF1D58C
                    )
            );

            return;
        }

        if (
                text.contains(
                        "VOLTAR PARA CAPA AUTOMÁTICA"
                )
        ) {
            button.setTextColor(
                    GREEN
            );

            button.setBackground(
                    rounded(
                            GREEN_LIGHT,
                            12,
                            1,
                            0xFFBCE7D2
                    )
            );

            return;
        }

        if (
                text.contains(
                        "ABRIR PDF"
                ) ||
                text.contains(
                        "PDFS"
                )
        ) {
            button.setTextColor(
                    BLUE_DARK
            );

            button.setBackground(
                    rounded(
                            SURFACE,
                            12,
                            1,
                            BORDER
                    )
            );

            return;
        }

        if (
                text.contains(
                        "DATA"
                ) ||
                text.contains(
                        "SELEÇÃO"
                ) ||
                text.contains(
                        "MARCAR"
                ) ||
                text.contains(
                        "DESMARCAR"
                ) ||
                text.contains(
                        "GMAIL"
                ) ||
                text.contains(
                        "APPS SCRIPT"
                ) ||
                text.contains(
                        "CONFIG"
                )
        ) {
            button.setTextColor(
                    TEXT
            );

            button.setBackground(
                    rounded(
                            SURFACE,
                            12,
                            1,
                            BORDER
                    )
            );

            return;
        }

        button.setTextColor(
                TEXT
        );

        button.setBackground(
                rounded(
                        SURFACE,
                        12,
                        1,
                        BORDER
                )
        );
    }

    private void styleProgress(
            ProgressBar progressBar
    ) {
        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.LOLLIPOP
        ) {
            progressBar.setProgressTintList(
                    ColorStateList.valueOf(
                            BLUE
                    )
            );

            progressBar.setIndeterminateTintList(
                    ColorStateList.valueOf(
                            BLUE
                    )
            );
        }
    }

    private void styleImage(
            ImageView imageView
    ) {
        View parent =
                (View) imageView.getParent();

        if (
                parent instanceof ViewGroup &&
                isCoverCard(
                        (ViewGroup) parent
                )
        ) {
            imageView.setBackground(
                    rounded(
                            0xFFF8FBFF,
                            12,
                            1,
                            BORDER
                    )
            );

            imageView.setPadding(
                    dp(6),
                    dp(6),
                    dp(6),
                    dp(6)
            );
        }
    }

    private GradientDrawable rounded(
            int fill,
            int radiusDp,
            int strokeDp,
            int strokeColor
    ) {
        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                fill
        );

        drawable.setCornerRadius(
                dp(
                        radiusDp
                )
        );

        if (
                strokeDp >
                        0
        ) {
            drawable.setStroke(
                    dp(
                            strokeDp
                    ),
                    strokeColor
            );
        }

        return drawable;
    }

    private GradientDrawable gradientCard(
            int[] colors,
            int radiusDp,
            int strokeDp,
            int strokeColor
    ) {
        GradientDrawable drawable =
                new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        colors
                );

        drawable.setCornerRadius(
                dp(
                        radiusDp
                )
        );

        if (
                strokeDp >
                        0
        ) {
            drawable.setStroke(
                    dp(
                            strokeDp
                    ),
                    strokeColor
            );
        }

        return drawable;
    }

    private String value(
            TextView view
    ) {
        CharSequence text =
                view.getText();

        return text == null
                ? ""
                : text.toString()
                .trim();
    }

    private int dp(
            int value
    ) {
        return Math.round(
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }
}
