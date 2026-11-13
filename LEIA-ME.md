# Gravador de Tela — ocultar o flutuador durante a captura

## Base consultada
- Repositório: `tysudess/central-android`
- Branch: `main`
- Commit-base conferido: `a04fb79e657f3b2a28992a677784a4b27803b7d1`
- Arquivo principal: `app/src/main/java/br/com/centralmidia/android/recording/ScreenRecordService.kt`
- Texto de orientação: `app/src/main/java/br/com/centralmidia/android/ui/ScreenRecorderActivity.kt`

## O que muda
1. O serviço remove o painel flutuante **antes de criar a MediaProjection**, evitando que os comandos apareçam até nos primeiros quadros.
2. O painel permanece oculto durante gravação em tela inteira e em área personalizada.
3. Os controles **Pausar/Retomar** e **Parar** continuam disponíveis na notificação persistente do Android.
4. Ao terminar de salvar o vídeo, o método já existente `finishAfterCapture()` restaura o flutuador automaticamente, caso ele esteja habilitado.
5. O texto de orientação do Gravador de Tela passa a explicar o novo comportamento.

## Aplicação
O ZIP contém `01-correcao-gravador.patch`, com os dois ajustes exatos e seus caminhos no repositório. Ele é um patch, não um arquivo Kotlin completo: aplique-o aos arquivos da versão-base indicada. Não substitua um `.kt` pelo arquivo `.patch`.

Com Git disponível, na raiz do clone do repositório:

```bash
git apply 01-correcao-gravador.patch
```

Depois confira o diff, faça o commit e execute os workflows `Validar Android (testes e compilação)` e `Build Android APK`.

## Escopo preservado
- Não altera o fluxo de autorização da MediaProjection.
- Não altera a seleção/recorte de área.
- Não altera a gravação de áudio, qualidade ou FPS.
- Não desativa a permissão de sobreposição; ela continua sendo usada para mostrar o flutuador antes e depois da gravação.
