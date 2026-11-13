# Integração definitiva do Gravador de Tela — Android

**Repositório:** `tysudess/central-android`  
**Base conferida:** `main` em `388674ebe6cdb9e83d04ae535772e005a2dc2b0c`  
**Escopo:** gravador de tela, captura de áudio interno, seleção da área e workflows de build/validação. O módulo Capas não é alterado.

## O que foi descoberto

O `main` continha `PlaybackAudioCapture.kt` e o patch de integração como arquivos separados, mas `ScreenRecordService.kt`, `CaptureAreaActivity.kt` e `ScreenRecorderActivity.kt` continuavam com as implementações antigas. Portanto, a classe de áudio nunca era chamada ao iniciar uma gravação pelo botão da aba.

## O que este pacote corrige

1. Atualiza os workflows para aplicar automaticamente o patch antes de compilar e validar o Android.
2. Mantém o áudio interno separado do microfone: o áudio interno é iniciado pela captura de reprodução do Android; o microfone continua opcional.
3. Ajusta a seleção de área usando a referência do display inteiro, além de pedir captura do display padrão no Android 14+.
4. O workflow `Build Android APK` gera também um artefato ZIP contendo os **arquivos-fonte completos já integrados**, com os caminhos originais do repositório.
5. O workflow de validação compila a mesma integração, evitando uma validação bem-sucedida de fontes antigos.

## Arquivos para copiar para o repositório

Copie os itens deste pacote para a raiz de `central-android`, preservando os caminhos:

- `.github/workflows/build-android.yml` — substitui o workflow atual de APK.
- `.github/workflows/validate-android.yml` — substitui o workflow atual de validação.
- `02-aplicar-correcao-audio-area.patch` — patch versionado e aplicado pelos workflows.
- `app/src/main/java/br/com/centralmidia/android/recording/PlaybackAudioCapture.kt` — classe completa de captura e codificação do áudio interno.

Os dois últimos já existem no `main` consultado, mas estão incluídos aqui para que o conjunto fique completo e coerente.

## Como aplicar

1. Baixe o ZIP e extraia seu conteúdo.
2. Envie os arquivos para o GitHub nos mesmos caminhos indicados acima, substituindo os dois workflows existentes.
3. Faça o commit na branch `main`.
4. Abra **Actions** e confira os workflows `Validar Android (testes e compilação)` e `Build Android APK`.
5. Quando `Build Android APK` terminar com sucesso, abra o artefato `Central-Android-APK-e-Fontes-Gravador`.
6. Baixe `Central-Android-Fontes-Completos-Gravador.zip`. Esse segundo ZIP contém os cinco arquivos completos que receberam a integração. Copie-os para o repositório, preservando os caminhos, e faça um novo commit. Assim o código-fonte também fica efetivamente integrado no `main`, sem depender do patch nos builds seguintes.

## Limites de captura de áudio

- Áudio interno: Android 10/API 29 ou superior, permissão `RECORD_AUDIO` e autorização da sessão `MediaProjection`.
- O aplicativo que reproduz o som precisa permitir a captura. Apps podem restringir a captura; áudio protegido por DRM ou bloqueado pelo aplicativo de origem não pode ser gravado.
- Microfone: segue opcional pela opção da aba.
- O Android 9 ou anterior não oferece a API de captura de áudio interno; nesses aparelhos permanece apenas a gravação pelo microfone.

## Validação

A verificação de sintaxe dos YAMLs, a leitura estrutural do patch e a integridade do ZIP são feitas nesta preparação. A compilação Gradle real e o teste em aparelho serão realizados pelos workflows após o upload. Não foi alegado teste físico nem sucesso de build desta nova integração.
