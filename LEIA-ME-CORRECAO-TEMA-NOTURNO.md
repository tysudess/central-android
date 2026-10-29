# Correção do build Android — tema noturno

Repositório-alvo: `tysudess/central-android`

Commit `main` inspecionado antes desta correção: `bf7773c28f9daf0b69adb02c2c46e9fec16baa8a`.

Execução Build Android APK #136: https://github.com/tysudess/central-android/actions/runs/40920417780
Execução Validar Android #14: https://github.com/tysudess/central-android/actions/runs/40920397447

## Erro observado

O Gradle falha em `:app:processDebugResources` com:

`Android resource linking failed: resource style/Theme not found.`

A causa está em `app/src/main/res/values-night/themes.xml`: os estilos `Theme.Central` e `Theme.Central.CaptureArea` não informavam explicitamente o pai. Como o nome do primeiro contém um ponto, o Android tenta inferir um estilo-pai chamado `Theme`, que não existe. A correção informa explicitamente o mesmo pai Material Components usado pelas definições de tema principais.

## Aplicação

1. Extraia este ZIP na raiz do repositório `tysudess/central-android`, preservando os caminhos.
2. Substitua `app/src/main/res/values-night/themes.xml` pelo arquivo incluído.
3. Faça commit e execute novamente os workflows `Validar Android (testes e compilação)` e `Build Android APK`.

Este pacote não altera o GitHub, não muda código Kotlin, não altera os secrets de assinatura e não remove as alterações dos ícones dos veículos.

## Verificações feitas nesta entrega

- XML analisado com parser XML.
- Ambos os estilos qualificados têm um atributo `parent` explícito.
- ZIP testado quanto à integridade.

A compilação completa do APK não foi executada neste ambiente; a confirmação definitiva dependerá do resultado dos workflows após o commit.
