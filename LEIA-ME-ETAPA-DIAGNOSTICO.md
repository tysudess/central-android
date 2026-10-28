# Central Inteligente de Mídia Android — etapa de diagnóstico visual da busca de vídeos

## Base e segurança da alteração

A base consultada é o `main` atual do repositório `tysudess/central-android`. O arquivo `VideosActivity.kt` tinha SHA `16a73e2534a792178df3205147fad98d0abfd7fd` quando foi lido. `VideoSearchClient.Outcome` já fornece `diagnostics`; esta etapa acrescenta um componente visual novo para apresentar esses dados por fonte/consulta.

Nenhuma alteração foi enviada ao GitHub.

## Conteúdo deste pacote

- `app/src/main/java/br/com/centralmidia/android/ui/VideoDiagnosticsCard.kt`: arquivo Kotlin completo novo, com resumo e detalhes expansíveis dos diagnósticos.
- `INTEGRACAO-VideosActivity.patch`: patch mínimo para inserir o cartão em `VideosActivity.kt` e preenchê-lo quando a busca terminar.

## Integração

O patch é aplicado sobre o `VideosActivity.kt` do `main` que corresponde ao SHA informado acima. Não substitua esse arquivo por cópia antiga. Aplique o patch e confira o diff antes de compilar.

## O que aparece

- Total de consultas, candidatos, correspondências, itens sem data, falhas ao resolver páginas e erros de coleta.
- Detalhes expansíveis por fonte/consulta, com candidatos, páginas resolvidas, data ausente, fora do período, termos/demandas não encontrados, duplicatas e erro de coleta.

## Validação

- Conferida a presença dos campos usados no modelo `VideoSearchClient.SourceDiagnostic` e do método `summary()` no `main` consultado.
- Conferidos os caminhos relativos e a estrutura do ZIP.
- **Não foi possível executar o build Gradle/APK neste ambiente**: não há Gradle/`gradlew` e SDK Android configurados. A compilação e a inspeção visual precisam ser confirmadas pelo workflow Android ou no ambiente de desenvolvimento antes de considerar a etapa validada.
