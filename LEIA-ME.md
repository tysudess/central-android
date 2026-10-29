# Contorno dourado completo dos cards — Android

**Base conferida:** `tysudess/central-android`, branch `main`, commit `620c9ed81946f9207e5b443a52a749b856d61ebd`.

## Arquivo a substituir

`app/src/main/java/br/com/centralmidia/android/ui/NavalTheme.kt`

Este pacote contém o arquivo Kotlin completo, baseado no `main` conferido. Extraia o ZIP na raiz do repositório e permita a substituição desse caminho.

## Correção aplicada

- Os cards agora usam o contorno nativo `MaterialCardView` em dourado (`#C6A15B`) nos quatro lados, com espessura de 1 dp e preservação dos cantos arredondados.
- Foi removido o filete separado que aparecia apenas na parte inferior.
- A alteração fica concentrada em `NavalTheme.kt`; mantém a exclusão de `CoversActivity` e do pacote `br.com.principaiscapas.`. A aba Capas e o módulo Principais Capas não são alterados.
- O arquivo foi baseado no `main` mais recente que foi consultado, sem reaproveitar versões antigas.

Depois do commit, execute `Validar Android (testes e compilação)` e `Build Android APK`. A compilação deste novo ajuste só pode ser confirmada pelo GitHub Actions após o envio.
