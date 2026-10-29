# Detalhe dourado nos cards — Central Android

Repositório: `tysudess/central-android`

Base consultada: `main` no commit `29740b5a8ae5841f40667c5dd5af1c6f90864857`.
Arquivo de origem: `app/src/main/java/br/com/centralmidia/android/ui/NavalTheme.kt`.

## O que muda
- Adiciona um filete dourado discreto na parte inferior dos cards `MaterialCardView` das abas que usam `NavalTheme`.
- Mantém o fundo branco, a borda clara, os cantos arredondados e o foreground de toque existente.
- Insere o detalhe apenas uma vez por card, evitando acúmulo quando o tema é reaplicado.
- Mantém a exclusão da `CoversActivity` e de `br.com.principaiscapas`; a aba Capas não é alterada.

## Como aplicar
Extraia este ZIP na raiz do repositório e substitua o arquivo existente pelo arquivo no mesmo caminho:
`app/src/main/java/br/com/centralmidia/android/ui/NavalTheme.kt`

Faça o commit e rode os workflows `Validar Android (testes e compilação)` e `Build Android APK`.

O pacote contém o arquivo Kotlin completo, não um patch. A compilação da alteração precisa ser confirmada pelo GitHub Actions após o commit.
