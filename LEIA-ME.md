# Remover a aba Histórico — Central Android

Repositório-base consultado: `tysudess/central-android`
Commit de referência: `6b62c6f9b61420d2aff5e2d9a9769808d1178c0b`

## Arquivo a substituir

`app/src/main/java/br/com/centralmidia/android/ui/MoreHubActivity.kt`

O ZIP contém o arquivo Kotlin completo, com o atalho **Histórico** removido do grupo **Mais > Monitoramento** e a descrição atualizada para “Busca, fontes e termos”. Os outros atalhos, permissões, a aba Capas e o código de gravação local do histórico são mantidos. A tela `HistoryActivity.kt` continua no repositório, mas deixa de ser apresentada como opção na interface; isso mantém a alteração cirúrgica e reduz o risco para outras funcionalidades.

## Aplicação

1. Extraia o ZIP na raiz do repositório.
2. Substitua o arquivo existente no caminho indicado.
3. Faça o commit pelo GitHub.
4. Execute os workflows `Validar Android (testes e compilação)` e `Build Android APK`.

A validação do commit-base estava aprovada quando o pacote foi preparado. Esta alteração ainda precisa ser validada após o commit.
