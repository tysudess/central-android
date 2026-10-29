# Tema naval nas demais abas da Central Android

## Base consultada
- Repositório: `tysudess/central-android`
- `main` antes desta entrega: `5f221875def2a29d5a3765080d3b1b95ee5a9532`
- Validação Android #17: sucesso
- Build Android APK #142: sucesso

## Escopo
Esta alteração aplica a identidade visual azul-marinho, branco e dourado às telas que herdam de `BaseActivity`, incluindo cabeçalho, botões, cartões e navegação compartilhada.

**A aba Capas foi deliberadamente excluída.** Não substituir nem editar `app/src/main/java/br/com/centralmidia/android/ui/CoversActivity.kt`, `br/com/principaiscapas/ModernMainActivity.java` ou `br/com/principaiscapas/MainActivity.java` com este pacote. A lógica de Capas e o layout naval que já foi aprovado permanecem fora desta mudança.

## Arquivos incluídos
1. `app/src/main/java/br/com/centralmidia/android/ui/BaseActivity.kt` — arquivo completo, com instalação/limpeza do tema naval no ciclo de vida.
2. `app/src/main/java/br/com/centralmidia/android/ui/NavalTheme.kt` — novo aplicador compartilhado do tema, com paleta naval, estados semânticos preservados e reaplicação quando a árvore da tela ganha componentes.

## Como aplicar
1. Descompacte na raiz de `tysudess/central-android`.
2. Substitua apenas o `BaseActivity.kt` e adicione `NavalTheme.kt` nos caminhos acima.
3. Não copie nem substitua nenhum arquivo do módulo Capas.
4. Faça commit e confira `Validar Android (testes e compilação)` e `Build Android APK`.

## Validação desta entrega
Os caminhos do ZIP foram conferidos. A compilação Android completa deve ser confirmada no GitHub Actions depois do commit; este ambiente não tem Android SDK/Gradle configurados.
