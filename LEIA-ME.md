# Correção — widget reaparecendo dentro da gravação

Base analisada: `tysudess/central-android`, `main` no commit `903c8be3c4772b14bd2ae0ed9a7f3282ac1e4bfe` (Build Android APK #164, sucesso).

## Causa identificada

Ao voltar da autorização do MediaProjection, a `ScreenRecorderActivity.onResume()` chama `enableWidgetService()`. Isso envia `ACTION_ENABLE_WIDGET` enquanto a captura já está ativa. O método `enableWidget()` recriava a janela flutuante sem testar `isRecording`/`isProcessing`, e o overlay voltava a aparecer na tela capturada.

## Correção

- `enableWidget()` mantém a preferência habilitada, mas não mostra o overlay enquanto grava ou finaliza.
- `showFloatingWidget()` recebeu uma proteção central; qualquer chamada durante captura/finalização remove ou mantém o widget oculto.
- A rotina `integrar_gravador.py` aplica e valida essas duas proteções nos builds, além de continuar validando as quatro coordenadas da área.

## Aplicação

Extraia na raiz do repositório `tysudess/central-android`, preservando os caminhos, substitua os dois arquivos e faça o commit na `main`. O widget voltará a aparecer depois que o vídeo terminar de ser salvo. Durante a gravação, os controles continuam disponíveis na notificação do Android.

A sintaxe Python foi verificada e o script de integração foi executado com sucesso em uma cópia de validação. A compilação deve ser confirmada pelo GitHub Actions após o commit. Não houve alteração direta no GitHub.
