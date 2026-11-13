# Correção — controle do gravador pelo botão Volume +

Este pacote contém somente os arquivos necessários para habilitar o atalho físico Volume + no gravador de tela.

## Aplicação

Extraia o ZIP na raiz do repositório `tysudess/central-android`, mantendo os caminhos. Substitua os arquivos existentes e adicione os novos arquivos nos caminhos indicados.

## Como ativar no Android

1. Atualize/instale a versão compilada do aplicativo.
2. Abra **Mais → Gravador de Tela**.
3. Na configuração da gravação, toque em **Ativar controle pelo Volume +**.
4. Nas configurações de Acessibilidade do Android, abra os serviços instalados/baixados, selecione **Controle do gravador pelo botão Volume +** e ative.
5. Volte ao gravador. O status deverá indicar que o controle está ativado.

Durante uma gravação, pressione **Volume +** uma vez para pausar e novamente para retomar. Fora de uma gravação, Volume + mantém a função normal de aumentar o volume. A autorização de Acessibilidade é concedida pelo próprio usuário nas configurações do Android.

## Arquivos incluídos

- `app/src/main/AndroidManifest.xml` — registra o serviço de acessibilidade.
- `app/src/main/java/br/com/centralmidia/android/recording/ScreenRecordService.kt` — adiciona a ação de alternância entre pausar e retomar.
- `app/src/main/java/br/com/centralmidia/android/recording/VolumeKeyAccessibilityService.kt` — detecta Volume + somente durante a gravação.
- `app/src/main/java/br/com/centralmidia/android/ui/ScreenRecorderActivity.kt` — inclui instrução, acesso às configurações e indicador do serviço.
- `app/src/main/res/xml/volume_key_accessibility_service.xml` — configura a captura de teclas físicas.
- `app/src/main/res/values/strings.xml` — descrição exibida pelo Android.

O serviço não lê conteúdo da tela, não captura texto e não intercepta outras teclas. Ele solicita somente a filtragem de eventos de teclas para tratar Volume + enquanto a gravação estiver ativa.
