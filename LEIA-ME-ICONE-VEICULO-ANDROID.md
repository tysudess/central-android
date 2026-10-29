# Ícones dos veículos nas Fontes e nos resultados — Central Android

## Base conferida antes de preparar os arquivos

- Repositório Android: `tysudess/central-android`
- Commit atual do `main` Android consultado: `cae5ce7cd9fcd6588e17e0c831e37ceb03b8ca5a`
- Repositório Windows: `tysudess/noticias-python`
- Commit atual do `main` Windows consultado: `9058443b879225ffe254340beb0ce7535b7a3b0b`
- A implementação Windows foi conferida em `news_site_icons_patch.py`, `source_list_logos_patch.py` e `networking/source_sites.py`.

## Arquivos completos incluídos

1. `app/src/main/java/br/com/centralmidia/android/ui/VehicleIconView.kt` — novo componente que busca e exibe favicons de forma assíncrona, limita o tamanho das respostas, restringe a concorrência, mantém cache em memória e usa as iniciais como fallback.
2. `app/src/main/java/br/com/centralmidia/android/ui/NewsCards.kt` — cartões de notícias passam a exibir o logo do veículo.
3. `app/src/main/java/br/com/centralmidia/android/ui/SourcesActivity.kt` — a lista de Fontes passa a exibir os logos; fontes de vídeo usam a URL de destino do respectivo catálogo.
4. `app/src/main/java/br/com/centralmidia/android/core/Models.kt` — `NewsItem` recebe `sourceUrl` opcional, preservando compatibilidade dos construtores existentes.
5. `app/src/main/java/br/com/centralmidia/android/core/GoogleNewsClient.kt` — lê o atributo oficial `url` do elemento RSS `<source>`.
6. `app/src/main/java/br/com/centralmidia/android/core/ResultStore.kt` — persiste e restaura `sourceUrl`, para os logos continuarem disponíveis ao reabrir os últimos resultados.
7. `app/src/main/assets/source_site_fallbacks.json` — catálogo de fallback baseado nos domínios de veículos já usados pelo Windows.

## Funcionamento e segurança

- Os favicons são baixados em segundo plano; a busca de notícias não espera pela imagem.
- O app usa a URL oficial publicada no RSS quando disponível. Para links do Google Notícias, aplica o mapa de fallback pelo nome do veículo; links diretos podem revelar o domínio oficial.
- Só são aceitos URLs HTTP(S) públicos. Links intermediários do Google não são apresentados como se fossem o site do veículo.
- Respostas precisam ter tipo de conteúdo de imagem e não podem superar 256 KiB; as conexões têm timeouts curtos e até três requisições são executadas simultaneamente.
- Se o site não estiver mapeado ou o ícone não carregar, as iniciais continuam visíveis. As buscas e ações não dependem do serviço de favicon.
- O cache de imagens é de memória. Após reiniciar o app, ícones podem ser baixados novamente.

## Aplicação no GitHub

Copie os arquivos deste pacote para a raiz do repositório `tysudess/central-android`, preservando exatamente os caminhos. Não é necessário alterar o workflow, a assinatura, as credenciais ou o repositório Windows. Faça o commit e confira os workflows de validação e geração do APK.

## Validação realizada

- JSON dos domínios validado (49 entradas e URLs absolutas).
- Integridade do ZIP verificada.
- Executada verificação estática de parsing Kotlin; o compilador local não encontrou erros específicos de sintaxe, mas não conseguiu compilar as classes porque o ambiente não possui Android SDK nem dependências Android/OkHttp/XML Pull Parser.
- A compilação real precisa ser confirmada pelo GitHub Actions após o commit.
