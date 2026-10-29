# Correção do histórico de notícias e vídeos — Central Android

## Base verificada

- Repositório: `tysudess/central-android`
- Branch: `main`
- Commit consultado antes da alteração: `8f2dcdcecb420e78f6d86bb5bb5a87fec5fd4c49`
- Arquivos de origem conferidos no GitHub:
  - `app/src/main/java/br/com/centralmidia/android/core/CentralDb.kt` — SHA `b92265c3770378ddda19f950fb4ed70e2e971df5`
  - `app/src/main/java/br/com/centralmidia/android/ui/HistoryActivity.kt` — SHA `125f0b0152086be731b689ade558ad71e47150d7`

## Arquivos completos neste pacote

1. `app/src/main/java/br/com/centralmidia/android/core/CentralDb.kt`
2. `app/src/main/java/br/com/centralmidia/android/ui/HistoryActivity.kt`

Os caminhos dentro do ZIP começam em `app/`, iguais aos caminhos do repositório. Não é um patch e não contém arquivos parciais.

## O que foi alterado

- O banco mantém o histórico por **7 dias**, excedendo o mínimo solicitado de 72 horas.
- A consulta do histórico filtra pelo período de retenção e não limita mais a lista a 1.000 registros.
- A limpeza automática de registros expirados acontece ao abrir o banco; falha nessa limpeza não impede a leitura do histórico.
- Foram adicionados índices não exclusivos para acelerar a busca por categoria/data e por URL/categoria. Nenhuma restrição nova de unicidade é aplicada aos dados antigos.
- A tela informa a janela de retenção e atualiza a contagem de registros.
- O botão **Limpar histórico** continua sendo uma ação manual explícita do usuário.
- Os registros continuam locais no banco SQLite do aplicativo. Não são enviados para um serviço externo por essa alteração.

## Como aplicar no GitHub sem PowerShell

1. Abra o repositório `tysudess/central-android` e confirme que está na branch `main`.
2. Abra cada caminho acima no GitHub e substitua o conteúdo pelo arquivo completo de mesmo caminho que está neste ZIP. Mantenha os nomes e diretórios exatamente iguais.
3. Faça o commit das alterações na branch usada para gerar o APK.
4. Abra **Actions** e confira a execução de validação e a de build Android. Só considere a correção entregue no aplicativo depois de o build terminar com sucesso e o APK resultante ser instalado e testado.

## Validação realizada neste pacote

- ZIP criado e testado para integridade.
- Verificação estática dos dois arquivos Kotlin e teste da lógica SQL de retenção/listagem, incluindo mais de 1.000 registros.
- **Não foi possível confirmar um APK compilado nesta etapa.** A compilação oficial deve ser validada no GitHub Actions após o commit.
