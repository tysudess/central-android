#!/usr/bin/env python3
"""Integra e valida o gravador sem aceitar uma correção parcial de coordenadas."""
from __future__ import annotations

import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / "02-aplicar-correcao-audio-area.patch"
SERVICE = ROOT / "app/src/main/java/br/com/centralmidia/android/recording/ScreenRecordService.kt"
CAPTURE = ROOT / "app/src/main/java/br/com/centralmidia/android/ui/CaptureAreaActivity.kt"
ACTIVITY = ROOT / "app/src/main/java/br/com/centralmidia/android/ui/ScreenRecorderActivity.kt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
AUDIO_CLASS = ROOT / "app/src/main/java/br/com/centralmidia/android/recording/PlaybackAudioCapture.kt"

OLD_HELP = (
    '"No widget, toque em ÁREA para marcar ou redefinir a região que será gravada. '
    'Depois toque em REC. O widget ficará fora da área selecionada para continuar '
    'disponível sem aparecer no vídeo final."'
)
NEW_HELP = (
    '"O som interno do aparelho é capturado automaticamente no Android 10+ quando '
    'o aplicativo permite. Marque a opção para adicionar também o microfone. Na '
    'seleção de área, a moldura agora é convertida usando a posição real na tela inteira."'
)

NORMALIZED_REGION = '''        fun normalizedRegion(): AreaRegion {
            val screenLocation = IntArray(2)
            getLocationOnScreen(screenLocation)
            val safeW = max(1, displayBounds.width()).toFloat()
            val safeH = max(1, displayBounds.height()).toFloat()
            val originX = screenLocation[0].toFloat() - displayBounds.left.toFloat()
            val originY = screenLocation[1].toFloat() - displayBounds.top.toFloat()

            return AreaRegion(
                left = ((originX + rect.left) / safeW).coerceIn(0f, 1f),
                top = ((originY + rect.top) / safeH).coerceIn(0f, 1f),
                right = ((originX + rect.right) / safeW).coerceIn(0f, 1f),
                bottom = ((originY + rect.bottom) / safeH).coerceIn(0f, 1f),
            )
        }
'''


def read(path: Path) -> str:
    if not path.is_file():
        raise SystemExit(f"::error::Arquivo obrigatório não encontrado: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def write(path: Path, value: str) -> None:
    path.write_text(value, encoding="utf-8", newline="\n")


def add_import(source: str, import_line: str, anchor: str) -> str:
    if import_line not in source:
        if anchor not in source:
            raise SystemExit(f"::error::Não encontrei a âncora para inserir {import_line}.")
        source = source.replace(anchor, anchor + import_line + "\n", 1)
    return source


def apply_legacy_patch_if_needed(service: str) -> None:
    if "playbackAudioCapture = runCatching" in service:
        return
    if not PATCH.is_file():
        raise SystemExit("::error::A integração de áudio está ausente e o patch original não foi encontrado.")
    result = subprocess.run(
        ["git", "apply", "--reject", str(PATCH)],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if result.stdout.strip():
        print(result.stdout.strip())
    # O retorno 1 pode ocorrer em arquivos cujo contexto mudou. A rotina abaixo
    # repara os trechos por âncoras e a validação final impede sucesso parcial.


def repair_widget_visibility(service: str) -> str:
    # Impede que ACTION_ENABLE_WIDGET recrie o overlay durante a captura.
    old_enable = '''    private fun enableWidget() {
        prefs.edit().putBoolean(KEY_ENABLED, true).apply()
        isWidgetEnabled = true
        if (Settings.canDrawOverlays(this)) {
            showFloatingWidget()
        }
    }
'''
    new_enable = '''    private fun enableWidget() {
        prefs.edit().putBoolean(KEY_ENABLED, true).apply()
        isWidgetEnabled = true

        // A tela de configuração volta ao primeiro plano depois da autorização
        // da MediaProjection e pode solicitar ACTION_ENABLE_WIDGET novamente.
        // Nunca recrie a janela flutuante durante a captura ou a finalização.
        if (isRecording || isProcessing) {
            hideFloatingWidget()
            return
        }

        if (Settings.canDrawOverlays(this)) {
            showFloatingWidget()
        }
    }
'''
    if new_enable not in service:
        if service.count(old_enable) != 1:
            raise SystemExit("::error::Não encontrei a implementação única de enableWidget() para impedir a reexibição do widget.")
        service = service.replace(old_enable, new_enable, 1)

    show_marker = "    private fun showFloatingWidget("
    show_start = service.index(show_marker)
    body_anchor = "    ): Boolean {\n"
    body_start = service.index(body_anchor, show_start) + len(body_anchor)
    guard = '''        // Defesa central: qualquer caminho que tente exibir o widget durante
        // a gravação/finalização é bloqueado, inclusive callbacks de onResume.
        if (isRecording || isProcessing) {
            hideFloatingWidget()
            return false
        }

'''
    if guard not in service[body_start:body_start + 700]:
        permission_anchor = "        if (!Settings.canDrawOverlays(this)) {\n"
        perm_pos = service.index(permission_anchor, body_start)
        service = service[:perm_pos] + guard + service[perm_pos:]
    return service


def repair_service() -> None:
    service = read(SERVICE)
    service = add_import(service, "import android.graphics.Point", "import android.graphics.PixelFormat\n")
    service = add_import(service, "import android.graphics.Rect", "import android.graphics.Point\n")

    field = "    private var playbackAudioCapture: PlaybackAudioCapture? = null\n"
    if field not in service:
        anchor = "    private var rawFile: File? = null\n"
        if service.count(anchor) != 1:
            raise SystemExit("::error::Não encontrei a âncora única rawFile no ScreenRecordService.kt.")
        service = service.replace(anchor, anchor + field, 1)

    # O gravador deve receber exatamente o mesmo retângulo físico usado pelo seletor.
    start = service.index("private fun startRecording(")
    width_line = service.index("            var width = bounds.width()", start)
    bounds_start = service.rfind("            val bounds =", start, width_line)
    if bounds_start < 0:
        raise SystemExit("::error::Não encontrei o bloco de dimensões de startRecording().")
    before_bounds = service.rfind("            val screenWindowManager =", start, bounds_start + 1)
    if before_bounds >= start:
        bounds_start = before_bounds
    service = service[:bounds_start] + (
        "            // Usa a mesma referência física do display do seletor e do widget.\n"
        "            val bounds = captureDisplayBounds()\n\n"
    ) + service[width_line:]

    # O posicionamento do widget deve compartilhar a mesma referência de display.
    widget_start = service.index("private fun calculateWidgetPositionOutsideRegion(")
    screen_w = service.index("        val screenW =", widget_start)
    bounds_start = service.rfind("        val bounds =", widget_start, screen_w)
    if bounds_start < 0:
        raise SystemExit("::error::Não encontrei o bloco de dimensões do widget.")
    wm_start = service.rfind("        val wm =", widget_start, bounds_start + 1)
    if wm_start >= widget_start:
        bounds_start = wm_start
    service = service[:bounds_start] + "        val bounds = captureDisplayBounds()\n\n" + service[screen_w:]

    # Garante o helper comum para API 30+ e versões anteriores.
    if "private fun captureDisplayBounds(): Rect" not in service:
        helper = '''    private fun captureDisplayBounds(): Rect {
        val screenWindowManager = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect(screenWindowManager.maximumWindowMetrics.bounds)
        } else {
            @Suppress("DEPRECATION")
            val realSize = Point().also { screenWindowManager.defaultDisplay.getRealSize(it) }
            Rect(0, 0, realSize.x, realSize.y)
        }
    }

'''
        anchor = "    private fun needsCrop(): Boolean ="
        if service.count(anchor) != 1:
            raise SystemExit("::error::Não encontrei a âncora para inserir captureDisplayBounds().")
        service = service.replace(anchor, helper + anchor, 1)

    # O overlay de seleção mede a sua própria origem e recebe os limites físicos do display.
    selector_start = service.index("private fun showAreaSelectorOverlay(")
    wm_line = service.index("        val wm =", selector_start)
    selector_prefix = service[selector_start:wm_line]
    if "val captureBounds = captureDisplayBounds()" not in selector_prefix:
        hide_line = service.index("        hideAreaSelectorOverlay()", selector_start) + len("        hideAreaSelectorOverlay()")
        service = service[:hide_line] + "\n        val captureBounds = captureDisplayBounds()" + service[hide_line:]

    view_call = service.index("        val area =", selector_start)
    view_call_end = service.index("        selectorAreaView =", view_call)
    call_text = service[view_call:view_call_end]
    if "captureBounds" not in call_text:
        call_text_new = re.sub(
            r"(AreaSelectionView\(\s*this,)(\s*\))",
            r"\1\n                captureBounds,\2",
            call_text,
            count=1,
        )
        if call_text_new == call_text:
            raise SystemExit("::error::Não foi possível ligar AreaSelectionView aos limites físicos do display.")
        service = service[:view_call] + call_text_new + service[view_call_end:]

    # Atualiza construtor e cópia dos limites se ainda estiverem na forma antiga.
    class_pos = service.index("private class AreaSelectionView(")
    class_head_end = service.index(") : View(context) {", class_pos) + len(") : View(context) {")
    class_head = service[class_pos:class_head_end]
    if "screenBounds: Rect" not in class_head:
        if "android.content.Context," not in class_head:
            raise SystemExit("::error::Não reconheci o construtor atual de AreaSelectionView.")
        class_head = class_head.replace(
            "android.content.Context,",
            "android.content.Context,\n        screenBounds: Rect,",
            1,
        )
        service = service[:class_pos] + class_head + service[class_head_end:]
    class_pos = service.index("private class AreaSelectionView(")
    class_body = service.index("\n", service.index(") : View(context) {", class_pos)) + 1
    if "private val displayBounds = Rect(screenBounds)" not in service[class_body:class_body + 300]:
        service = service[:class_body] + "        private val displayBounds = Rect(screenBounds)\n" + service[class_body:]

    # Substitui o normalizador inteiro para impedir regressões em qualquer borda.
    class_pos = service.index("private class AreaSelectionView(")
    norm_start = service.index("        fun normalizedRegion()", class_pos)
    norm_end = service.index("        companion object {", norm_start)
    service = service[:norm_start] + NORMALIZED_REGION + "\n" + service[norm_end:]

    # Bloqueia reexibição do widget ao retornar da tela de permissão.
    service = repair_widget_visibility(service)
    write(SERVICE, service)


def repair_capture_activity() -> None:
    source = read(CAPTURE)
    source = add_import(
        source,
        "import android.media.projection.MediaProjectionConfig",
        "import android.media.projection.MediaProjectionManager\n",
    )
    if "MediaProjectionConfig.createConfigForDefaultDisplay()" not in source:
        old = """        projectionPermission.launch(
            manager.createScreenCaptureIntent(),
        )"""
        new = """        val captureIntent = if (Build.VERSION.SDK_INT >= 34) {
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            manager.createScreenCaptureIntent()
        }
        projectionPermission.launch(
            captureIntent,
        )"""
        if source.count(old) != 1:
            raise SystemExit("::error::Não encontrei a chamada de permissão de captura para configurar o display inteiro.")
        source = source.replace(old, new, 1)
    write(CAPTURE, source)


def repair_ui_and_manifest() -> None:
    activity = read(ACTIVITY)
    activity = activity.replace('text = "Gravar microfone"', 'text = "Gravar também microfone"')
    if NEW_HELP not in activity:
        if OLD_HELP in activity:
            activity = activity.replace(OLD_HELP, NEW_HELP, 1)
        else:
            raise SystemExit("::error::O texto de ajuda do gravador mudou; não foi possível atualizar com segurança.")
    write(ACTIVITY, activity)

    manifest = read(MANIFEST)
    if 'android:allowAudioPlaybackCapture="true"' not in manifest:
        anchor = '        android:allowBackup="false"\n'
        if manifest.count(anchor) != 1:
            raise SystemExit("::error::Não encontrei a âncora da tag application no Manifest.")
        manifest = manifest.replace(anchor, anchor + '        android:allowAudioPlaybackCapture="true"\n', 1)
    write(MANIFEST, manifest)


def validate() -> None:
    service = read(SERVICE)
    capture = read(CAPTURE)
    activity = read(ACTIVITY)
    manifest = read(MANIFEST)
    read(AUDIO_CLASS)

    markers = {
        "captura de áudio interno inicializada": "playbackAudioCapture = runCatching" in service,
        "campo da captura de áudio": "private var playbackAudioCapture: PlaybackAudioCapture? = null" in service,
        "pausa sincronizada do áudio": "playbackAudioCapture?.setPaused(true)" in service,
        "retomada sincronizada do áudio": "playbackAudioCapture?.setPaused(false)" in service,
        "finalização da trilha de áudio": "stopAndGetFile()" in service,
        "limites comuns do display": "private fun captureDisplayBounds(): Rect" in service,
        "dimensões do vídeo pelo display comum": "val bounds = captureDisplayBounds()" in service[service.index("private fun startRecording("):service.index("private fun startForegroundRecorder(")],
        "widget pelo display comum": "val bounds = captureDisplayBounds()" in service[service.index("private fun calculateWidgetPositionOutsideRegion("):service.index("private fun beginCaptureFlow(")],
        "seletor pelo display comum": "val captureBounds = captureDisplayBounds()" in service,
        "AreaSelectionView recebe limites físicos": "screenBounds: Rect" in service and "AreaSelectionView(\n                this,\n                captureBounds," in service,
        "origem horizontal esquerda": "(originX + rect.left) / safeW" in service,
        "origem vertical superior": "(originY + rect.top) / safeH" in service,
        "origem horizontal direita": "(originX + rect.right) / safeW" in service,
        "origem vertical inferior": "(originY + rect.bottom) / safeH" in service,
        "enableWidget bloqueado durante captura": "if (isRecording || isProcessing)" in service[service.index("private fun enableWidget()"):service.index("private fun disableWidget()")],
        "showFloatingWidget bloqueado durante captura": "if (isRecording || isProcessing)" in service[service.index("private fun showFloatingWidget("):service.index("private fun calculateWidgetPositionOutsideCapture(")],
        "captura do display inteiro no Android 14+": "MediaProjectionConfig.createConfigForDefaultDisplay()" in capture,
        "texto de ajuda atualizado": "O som interno do aparelho é capturado automaticamente" in activity,
        "captura de áudio permitida no Manifest": 'android:allowAudioPlaybackCapture="true"' in manifest,
        "classe de captura de áudio disponível": AUDIO_CLASS.is_file(),
    }
    missing = [name for name, ok in markers.items() if not ok]
    if missing:
        raise SystemExit("::error::Gravador incompleto: " + "; ".join(missing))

    if list(ROOT.rglob("*.rej")):
        raise SystemExit("::error::Há arquivos .rej pendentes; a integração não foi concluída.")

    print("Gravador integrado e validado: as quatro bordas usam a mesma origem e escala do display.")


def main() -> None:
    for path in (SERVICE, CAPTURE, ACTIVITY, MANIFEST, AUDIO_CLASS):
        read(path)
    apply_legacy_patch_if_needed(read(SERVICE))
    repair_service()
    repair_capture_activity()
    repair_ui_and_manifest()
    for reject in ROOT.rglob("*.rej"):
        reject.unlink(missing_ok=True)
    validate()


if __name__ == "__main__":
    main()
