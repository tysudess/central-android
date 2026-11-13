# Correção do Gravador de Tela — áudio interno e seleção de área

**Repositório:** `tysudess/central-android`  
**Base obrigatória:** `main` no commit `bf22bff8973ada2296eff7215c3a37192c247b9f`  
**Escopo:** somente gravador de tela, fluxo de autorização e manifesto do app. O módulo Capas não é alterado.

## O que esta correção faz

1. Acrescenta `PlaybackAudioCapture.kt`, que captura o áudio reproduzido por outros aplicativos no Android 10 (API 29) ou superior, codifica em AAC e combina essa faixa com o vídeo gravado.
2. Mantém o microfone como opção adicional, sem confundi-lo com o áudio interno.
3. Solicita a permissão `RECORD_AUDIO` também quando o microfone está desmarcado, pois o Android exige essa permissão para a captura de reprodução.
4. Pausa e retoma a captura de áudio junto com o vídeo.
5. Mantém o flutuador oculto durante a gravação; os controles permanecem na notificação.
6. Corrige a conversão da moldura selecionada para coordenadas relativas ao display, incluindo a posição real do overlay na tela.
7. No Android 14 ou superior, solicita a captura do display padrão inteiro. Isso evita que a autorização escolha somente uma janela de aplicativo, enquanto a seleção de área foi medida sobre a tela inteira.
8. Declara que a própria Central permite captura do seu áudio reproduzido. Isso não altera a política de captura de outros aplicativos.

## Arquivos incluídos

- `app/src/main/java/br/com/centralmidia/android/recording/PlaybackAudioCapture.kt` — arquivo Kotlin novo, completo.
- `02-aplicar-correcao-audio-area.patch` — alterações nos arquivos existentes:
  - `ScreenRecordService.kt`
  - `CaptureAreaActivity.kt`
  - `ScreenRecorderActivity.kt`
  - `AndroidManifest.xml`

## Aplicação

Este pacote contém o novo arquivo completo e um patch para os arquivos existentes. **Não copie o `.patch` por cima de um arquivo Kotlin.**

Na raiz de uma cópia atualizada do repositório, faça:

1. Copie o arquivo `PlaybackAudioCapture.kt` para o caminho indicado acima, preservando as pastas.
2. Aplique `02-aplicar-correcao-audio-area.patch` na raiz do repositório com `git apply 02-aplicar-correcao-audio-area.patch`.
3. Confira as alterações e envie o commit para o GitHub. Não substitua o repositório por uma versão antiga.
4. Aguarde a execução dos workflows de validação e geração do APK antes de instalar a versão no aparelho.

## Limitações reais do Android

- A captura do áudio interno exige Android 10 ou superior e a permissão `RECORD_AUDIO`, além da autorização de captura de tela do Android.
- O aplicativo que está reproduzindo o som precisa permitir a captura. Aplicativos podem bloquear o áudio por política própria; conteúdo protegido/DRM também pode não ser capturável. A Central não tenta contornar essas proteções.
- No Android 9 ou anterior, permanece disponível apenas a gravação pelo microfone; a API de captura de reprodução não existe nessas versões.
- Se o microfone for habilitado junto com o áudio interno, as faixas podem permanecer separadas no MP4. O áudio interno é colocado primeiro para ser a faixa principal na maioria dos players.

## Validação realizada nesta preparação

- A estrutura do patch foi lida pelo parser do Git (`git apply --numstat`).
- `PlaybackAudioCapture.kt` passou por verificação de sintaxe Kotlin usando stubs locais das APIs Android utilizadas.
- **Não foi executado um build Gradle completo nem teste físico em aparelho Android nesta etapa.** A validação definitiva depende dos workflows após a aplicação do patch no repositório.
