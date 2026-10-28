# Diagnóstico e correção da busca de vídeos — Central Android

## Repositórios e base comparada

- Android: `tysudess/central-android`, `main` consultado em `c543a5e91b82a5f9b5da4c380b96906d866c418f`.
- Desktop/Windows: `tysudess/noticias-python`, `main` consultado em `9058443b879225ffe254340beb0ce7535b7a3b0b`.
- O arquivo Android original `VideoSearchClient.kt` foi conferido por Git blob SHA (`0b42dd75f352273e9ec86839cb9acc8af29fed1e`) antes de modificá-lo.
- Nenhuma alteração foi enviada diretamente ao GitHub.

## Causa identificada no código

As capturas mostram 1 vídeo no Android contra 28 no Windows, embora ambos completem a busca. O Android faz 3.509 etapas; o Windows faz 160. A comparação dos implementadores mostra diferenças estruturais, não apenas um limite pequeno:

1. **Explosão de consultas no Android:** `buildJobs()` criava consultas separadas para os 25 termos em quase todas as fontes Globoplay, Band e R7. O Windows (`repositories/video_search.py`) faz varredura local por fonte e restringe consultas de fallback aos oito IDs de portais nacionais indicados no código desktop, limitando o fallback a seis consultas por portal. O volume anterior de 3.509 etapas corresponde a essa multiplicação.
2. **Vídeos embutidos não eram descobertos:** o parser genérico Android lia apenas `a[href]`. O coletor Windows (`collectors/video/website.py`) também procura IDs `/v/12345` em HTML/JSON/script embutidos. No Globoplay, parte dos cartões e metadados está no estado inicial/JSON, não necessariamente em âncoras HTML.
3. **Faltava a estratégia especializada do Globoplay:** o desktop combina coletores de edições e trechos (`globoplay_editions.py`, `globoplay_trechos.py`) e consulta o endpoint GraphQL Jarvis para cinco programas nacionais (`globoplay_jarvis.py`). O Android não tinha essa descoberta combinada.
4. **Orçamento de enriquecimento mal priorizado:** o Android podia gastar as primeiras resoluções de páginas com vídeos sem relação com termos monitorados. Como candidatos sem data são corretamente excluídos do período, os vídeos relevantes eram descartados se não houvesse orçamento para abrir suas páginas e descobrir data/descrição.
5. **Contagem de falhas não comparável:** alguns erros individuais são absorvidos pelos fallbacks Android; por isso “0 falhas” não significa que a coleta das fontes tenha sido integralmente equivalente ao Windows.

Esses pontos explicam a discrepância estrutural observada. A causa não era o conjunto de termos ou o número de fontes selecionadas.

## Arquivos completos neste pacote

- `app/src/main/java/br/com/centralmidia/android/core/VideoSearchClient.kt` — corrige o planejamento das consultas, prioriza candidatos relevantes e aumenta/orienta o orçamento de resolução por tipo de fonte.
- `app/src/main/java/br/com/centralmidia/android/core/GloboplayDiscoveryClient.kt` — novo coletor de descoberta de Globoplay: links `/v/` em HTML/script, páginas de programas e `/cenas/`, e consulta Jarvis agrupada por programa nacional.

## Mudanças técnicas

- Alinha a classificação das fontes em modo de varredura ao desktop, sem tratar CNN Brasil como uma varredura especial que o Windows não faz.
- Restringe a busca de fallback por termos aos mesmos IDs nacionais usados no desktop; limita esse fallback a seis consultas e prioriza demandas cadastradas.
- Une resultados do Jarvis aos resultados da fonte pelo URL canônico, preservando título, descrição e data quando disponíveis.
- Descobre candidatos do Globoplay em HTML/JSON embutido e páginas de programas/trechos; não inventa a data de publicação.
- Evita fazer novamente as mesmas requisições do parser genérico quando o coletor especializado já encontrou candidatos para uma fonte Globoplay.
- Prioriza candidatos que já correspondem a termos/demandas antes de usar o orçamento de abertura das páginas individuais.
- Adota limites por tipo de fonte mais próximos do desktop, sem aumentar indiscriminadamente a resolução de cada item de todas as 134 fontes.

## Validação feita nesta etapa

- ZIP conferido e testado quanto à integridade.
- O `VideoSearchClient.kt` original foi comparado com o blob SHA da versão Android consultada.
- Os dois arquivos Kotlin passaram por compilação de sintaxe com `kotlinc` usando stubs mínimos de Jsoup/JSON/Models. Isso valida a sintaxe Kotlin e os fluxos básicos, mas os stubs não substituem as dependências reais do Android.
- **Não** foi possível executar Gradle/SDK Android neste ambiente.
- **Não** foi feita uma busca de rede ao vivo nem validada ainda a contagem real de 28 vídeos. Isso só pode ser confirmado após aplicar o pacote e executar o workflow Android e uma busca com o mesmo período, termos e fontes.

## Como aplicar

1. Baixe e extraia o ZIP na raiz local do repositório `central-android`, mantendo os caminhos relativos.
2. Confira o diff dos dois arquivos Kotlin.
3. Envie os arquivos pelo seu processo normal para o GitHub; não precisa substituir o repositório nem reaplicar o patch incremental anterior de limites.
4. Execute **Actions → Validar Android (testes e APK)** e depois compare a busca com a versão Windows, mantendo os mesmos termos, fontes e período.

Se a busca ainda não se aproximar dos resultados do Windows, o próximo diagnóstico deverá usar o cartão de diagnósticos por fonte e comparar candidato por candidato; não será seguro afirmar paridade sem esse teste real.
