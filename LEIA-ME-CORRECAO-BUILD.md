# Correção do build — tema naval

Repositório: `tysudess/central-android`
Base consultada: `main` no commit `658e93a05b87383f9a9772b258bb9328f7576489`.

## Erro identificado

O GitHub Actions #144 falhou em `:app:compileDebugKotlin`:

`NavalTheme.kt:282:48 Inline function 'visit' cannot be recursive`

## Aplicar

Arquivo: `app/src/main/java/br/com/centralmidia/android/ui/NavalTheme.kt`

Na declaração da função `visit`, altere somente:

`private inline fun visit(root: View, action: (View) -> Unit) {`

para:

`private fun visit(root: View, action: (View) -> Unit) {`

Não altere o corpo da função. A palavra `inline` precisa ser removida porque a função é recursiva.

## Escopo

A correção não modifica `CoversActivity.kt`, os arquivos de Principais Capas nem os secrets de assinatura.

Depois de salvar o arquivo, faça o commit e execute novamente `Validar Android (testes e compilação)` e `Build Android APK`.
