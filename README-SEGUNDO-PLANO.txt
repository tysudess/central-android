CENTRAL INTELIGENTE DE MIDIA — MODO DE SEGUNDO PLANO

Arquivos incluídos (caminhos completos no projeto):
1. app/src/main/AndroidManifest.xml
2. app/src/main/java/br/com/centralmidia/android/ui/BaseActivity.kt
3. app/src/main/java/br/com/centralmidia/android/automation/CentralBackgroundService.kt (novo)

O que muda:
- Ao entrar na tela principal após o login, o aplicativo inicia um serviço Android em primeiro plano.
- O serviço exibe uma notificação persistente e mantém um WakeLock parcial enquanto estiver ativo, para reduzir a chance de o CPU suspender com a tela apagada.
- O serviço continua ativo ao alternar entre abas. A ação “Desativar” na notificação encerra o serviço.
- Ao voltar à tela de login, o serviço é encerrado.
- O monitoramento automático existente via WorkManager e o serviço de gravação de tela foram mantidos.

Importante:
- O modo contínuo aumenta o consumo da bateria. A notificação fica visível para deixar claro que o serviço está ativo.
- O Android e fabricantes podem continuar impondo restrições a rede, Doze e bateria, e a parada forçada do aplicativo encerra sua execução. Para maior confiabilidade, deixe a bateria do app como “Sem restrições/Não otimizar” nas configurações do aparelho.
- O serviço usa o tipo foreground “specialUse” para evitar a limitação de seis horas por dia aplicada a alguns serviços do tipo dataSync. Para publicação pela Google Play, esse tipo exige justificativa e análise de conformidade; para distribuição por APK, o manifesto declara a finalidade específica.
- Este pacote não altera o GitHub nem o workflow de build.
