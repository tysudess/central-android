# Correção do build — tema naval

Repositório: `tysudess/central-android`
Base consultada: `main` no commit `d2124cf98a711c02a745330de63c7815fc920b87`.

## Erro confirmado no GitHub Actions #145

`NavalTheme.kt:282:48 Inline function 'visit' cannot be recursive`

## Arquivo a corrigir

`app/src/main/java/br/com/centralmidia/android/ui/NavalTheme.kt`

Localize a declaração:

```kotlin
private inline fun visit(root: View, action: (View) -> Unit) {
```

Substitua por:

```kotlin
private fun visit(root: View, action: (View) -> Unit) {
```

Remova somente a palavra `inline`. Não altere o corpo da função nem os outros arquivos.

## Por que a correção anterior não resolveu

O log da execução #145 ainda encontrou `private inline fun visit`, portanto o arquivo-fonte usado no build não tinha recebido a alteração. Este pacote documenta exatamente a mudança necessária; não inclui uma cópia incompleta do arquivo Kotlin.

## O que preservar

- Não alterar `CoversActivity.kt` nem os arquivos do módulo Principais Capas.
- Não alterar secrets de assinatura.
- Depois de salvar a alteração, faça commit e execute novamente `Validar Android (testes e compilação)` e `Build Android APK`.
