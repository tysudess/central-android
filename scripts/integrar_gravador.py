#!/usr/bin/env python3
"""Aplica a integração do áudio interno de forma resiliente ao patch legado."""
from __future__ import annotations

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


def read(path: Path) -> str:
    if not path.is_file():
        raise SystemExit(f"::error::Arquivo obrigatório não encontrado: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def write(path: Path, content: str) -> None:
    path.write_text(content, encoding="utf-8", newline="\n")


def cleanup_rejects() -> None:
    for rejected in ROOT.rglob("*.rej"):
        rejected.unlink(missing_ok=True)


def already_integrated() -> bool:
    service = read(SERVICE)
    capture = read(CAPTURE)
    activity = read(ACTIVITY)
    return (
        "playbackAudioCapture = runCatching" in service
        and "private var playbackAudioCapture: PlaybackAudioCapture? = null" in service
        and "MediaProjectionConfig.createConfigForDefaultDisplay()" in capture
        and "O som interno do aparelho é capturado automaticamente" in activity
    )


def main() -> None:
    if already_integrated():
        cleanup_rejects()
        print("Integração do gravador já está completa; nenhuma alteração necessária.")
        return

    for path in (PATCH, AUDIO_CLASS, SERVICE, CAPTURE, ACTIVITY, MANIFEST):
        if not path.is_file():
            raise SystemExit(f"::error::Arquivo necessário ausente: {path.relative_to(ROOT)}")

    # O patch original contém todos os blocos funcionais, mas dois hunks têm
    # contexto que diverge do main atual. --reject aplica os demais hunks válidos;
    # os dois pontos residuais são reconciliados abaixo por âncoras exatas.
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

    service = read(SERVICE)
    field = "    private var playbackAudioCapture: PlaybackAudioCapture? = null"
    if field not in service:
        anchor = "    private var rawFile: File? = null\n"
        if service.count(anchor) != 1:
            raise SystemExit("::error::Não encontrei a âncora única rawFile no ScreenRecordService.kt.")
        service = service.replace(anchor, anchor + field + "\n", 1)
        write(SERVICE, service)

    activity = read(ACTIVITY)
    if NEW_HELP not in activity:
        if activity.count(OLD_HELP) != 1:
            raise SystemExit("::error::O texto de ajuda do gravador mudou; não foi possível atualizar com segurança.")
        activity = activity.replace(OLD_HELP, NEW_HELP, 1)
        write(ACTIVITY, activity)

    cleanup_rejects()

    service = read(SERVICE)
    capture = read(CAPTURE)
    activity = read(ACTIVITY)
    manifest = read(MANIFEST)
    required = {
        "campo de captura de áudio": "private var playbackAudioCapture: PlaybackAudioCapture? = null" in service,
        "inicialização de captura de áudio": "playbackAudioCapture = runCatching" in service,
        "pausa do áudio junto com o vídeo": "playbackAudioCapture?.setPaused(true)" in service,
        "finalização da faixa de áudio": "stopAndGetFile()" in service,
        "conversão das coordenadas do display": "captureDisplayBounds(): Rect" in service,
        "autorização para display padrão": "MediaProjectionConfig.createConfigForDefaultDisplay()" in capture,
        "texto atualizado da aba": "O som interno do aparelho é capturado automaticamente" in activity,
        "captura de áudio permitida no manifesto": 'android:allowAudioPlaybackCapture="true"' in manifest,
        "classe de captura presente": AUDIO_CLASS.is_file(),
    }
    missing = [name for name, ok in required.items() if not ok]
    if missing:
        raise SystemExit("::error::Integração incompleta: " + ", ".join(missing))

    print("Integração de áudio interno e correção da seleção concluídas nos fontes do build.")
    print(f"git apply retornou código {result.returncode}; os hunks divergentes foram corrigidos por âncoras verificadas.")


if __name__ == "__main__":
    main()
