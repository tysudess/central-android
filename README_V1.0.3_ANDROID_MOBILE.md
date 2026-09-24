# Central Android v1.0.3 — UI mobile + sincronização com o portable

## Base usada

- Android `tysudess/central-android`
- `main`: `f0d96cbdc79fa0c309dcad9c0180b358db9d680e`
- Último workflow verificado antes das alterações: Build Android APK #6 — SUCCESS
- Referência desktop: `tysudess/noticias-python`
- `main` desktop verificada: `2ce2a51e5e33724f8929054878d5c6ae18057d14`

## O que foi alterado

### Layout Android
- Início refinado com ícones vetoriais.
- Notícias refinada.
- Vídeos refinada.
- Demandas redesenhada para o padrão mobile.
- Fontes redesenhada para o padrão mobile.
- Histórico redesenhado para o padrão mobile.
- Termos redesenhada para o padrão mobile.
- Barra inferior: Início / Notícias / Vídeos / Mais.
- Safe area do Android aplicada com `WindowInsetsCompat`, inclusive barra gestual/navegação inferior.

### Notícias
- O campo de busca da tela funciona como filtro local, como no portable.
- A busca principal usa os termos cadastrados, como no portable.
- Usa a seleção de fontes configurada em Fontes.
- Modo "Pesquisar todos" mantido.
- Filtro por período: Hoje, 24 horas, 7 dias, 30 dias e personalizado.
- Filtro "Só demandas".
- Matching de fonte, termo e demanda portado para Android.
- Cards com Abrir matéria, WhatsApp, Copiar link e Extrair matéria.

### Vídeos
- Termos de vídeo independentes dos termos de notícias.
- Fontes de vídeo independentes e selecionáveis.
- Catálogo sincronizado do portable em cada build.
- `aliases`, `linkHints`, `youtubeHandle`, `searchUrlTemplate` e `searchPrefix` passam a ser sincronizados.
- A UI usa os termos e as fontes selecionadas para montar a pesquisa Android por fonte e integrar com o Extrator de Vídeos.
- O Android continua usando implementação própria (Web/yt-dlp Android), sem Proxy Geral.

### Termos
- Os 25 termos padrão do portable são sincronizados no build.
- Notícias e Vídeos possuem listas independentes.
- Adicionar, selecionar vários e excluir selecionados.

### Fontes
- Notícias / Vídeos / Mídia especializada.
- Filtro por nome, região, estado, grupo e aliases.
- Selecionar visíveis.
- Limpar visíveis.
- Selecionar todas.
- Limpar todas.
- "Pesquisar todos" para Notícias/Mídia especializada.

### Demandas
- Cadastro por veículo + assunto.
- Busca individual.
- Buscar todas.
- Matching por veículo e assunto.
- Resultados usam os mesmos cards/ações de Notícias.

### Histórico
- Notícias e Vídeos separados.
- Limpeza por categoria.
- Abertura e cópia dos links.
- Notícias mantêm compartilhamento por WhatsApp.

## Segurança e autenticação

Não foi alterado:
- Apps Script / Google Sheets.
- login;
- token;
- Android Keystore/armazenamento seguro;
- validade;
- status ATIVO/BLOQUEADO;
- limite de dispositivos;
- permissões;
- TROCAR_SENHA;
- troca obrigatória;
- Minha Conta;
- logout;
- revogação de sessão.

No Android continua **sem Proxy Geral** e com conexão direta.

## Banco local

`CentralDb` passa da versão 1 para a versão 2:
- dados existentes são preservados;
- é criada a tabela `video_terms`;
- notícias e vídeos passam a ter termos independentes;
- os termos padrão são inseridos apenas quando a lista correspondente ainda está vazia.

## Workflow

**WORKFLOW: NÃO PRECISA ALTERAR**

O arquivo `.github/workflows/build-android.yml` não faz parte deste pacote porque não precisa de mudança.
O workflow existente já executa `scripts/sync_desktop.py` antes do Gradle.

## Como subir

Envie os arquivos deste ZIP preservando exatamente os caminhos das pastas e faça commit na `main`.
Depois acompanhe o workflow `Build Android APK`.

Se o workflow falhar, não faça correções por tentativa: use o log real da etapa que falhou.
