# Etapa 1 — Fundação da identidade visual

## Base conferida

- Repositório: `tysudess/central-android`
- Branch: `main`
- Commit conferido antes da preparação: `28b076ae71515f081daae0b7c1a04fe2c1adedeb`
- Nenhuma alteração foi enviada diretamente ao GitHub.

## Arquivos incluídos

- `app/src/main/res/values/colors.xml` — paleta oficial clara, mantendo nomes legados `central_blue`, `central_bg`, `central_surface`, `central_text`, `central_muted` e `central_border`.
- `app/src/main/res/values-night/colors.xml` — cores equivalentes para o tema escuro.
- `app/src/main/res/values/styles.xml` — estilos base para botões, botões contornados, campos Material e cartões.
- `app/src/main/res/values/themes.xml` — tema principal integrado aos tokens de cor e aos estilos compartilhados.
- `app/src/main/res/values-night/themes.xml` — contraste adequado para ícones das barras do sistema no tema escuro.

## Direção visual

- Azul-marinho institucional e azul principal para ações.
- Fundo cinza-azulado muito claro no tema diurno e superfícies escuras no tema noturno.
- Estados semânticos consistentes para sucesso, atenção, erro e informação.
- Componentes de Material com altura de toque, raios e tipografia padronizados.

## Importante sobre o escopo

Esta entrega é a base de tema/recursos compartilhados; ela não migra, ainda, as cores que algumas telas aplicam diretamente via `Color.rgb()` ou propriedades Kotlin. Isso precisa ser feito na próxima fase em `MobileScaffold.kt` e `Ui.kt`, centralizando esses tokens e atualizando o cabeçalho, cartões, navegação e controles visuais sem reescrever os fluxos funcionais.

## Validação

- XML criado com encoding UTF-8 e estrutura de recursos Android.
- Recomenda-se executar `Validar Android (testes e compilação)` e `Build Android APK` após enviar estes arquivos.
- A compilação do Android não foi executada localmente nesta entrega porque o ambiente de trabalho não tem Android SDK/dependências Gradle resolvidas.
