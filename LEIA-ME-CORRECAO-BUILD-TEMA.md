# Correção do build Android — tema e recursos

## Diagnóstico confirmado

Repositório: `tysudess/central-android`

`main` consultado para diagnosticar: `dd52bcd0e8d961cfafc5144124fd4451f587bd9b`

Execuções que falharam:
- Build Android APK #134: https://github.com/tysudess/central-android/actions/runs/40919357132
- Validar Android #13: https://github.com/tysudess/central-android/actions/runs/40919299381

As duas falham em `:app:processDebugResources`, com os erros:
- `attr/colorBackground ... not found`
- `resource style/Theme ... not found`

Isso acontece antes da compilação Kotlin. A mudança que introduziu os novos temas adicionou `colorBackground` sem o namespace Android e definiu estilos globais de widgets que não são necessários para aplicar a paleta.

## Arquivos completos incluídos

1. `app/src/main/res/values/themes.xml` — remove o atributo não namespaced, usa `android:colorBackground` e restaura uma definição de tema mínima, compatível com a estrutura anterior.
2. `app/src/main/res/values/styles.xml` — remove temporariamente os estilos globais de widgets que tinham sido acrescentados junto ao tema. A paleta nova permanece nos arquivos `colors.xml` e os módulos de capas/PDF/vídeo não são modificados.

As alterações de ícones dos veículos (Kotlin e JSON) não são removidas por este pacote.

## Como aplicar

1. Descompacte na raiz de `tysudess/central-android` e mantenha os caminhos.
2. Substitua exatamente os dois arquivos deste pacote.
3. Faça commit e aguarde as execuções `Validar Android (testes e compilação)` e `Build Android APK`.

Não altere os secrets de assinatura. Este erro é de recursos Android, não de assinatura.

## Validação nesta entrega

- Os dois XML foram analisados com parser XML.
- Foi verificado que o tema não contém mais `name="colorBackground"` sem namespace nem referências aos estilos `Widget.Central.*` temporariamente removidos.
- A compilação com Android SDK não pode ser executada neste ambiente; a confirmação final é o GitHub Actions após aplicar os arquivos.
