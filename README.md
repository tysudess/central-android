# Central Inteligente de Mídia — Android

Versão Android nativa da Central Inteligente de Mídia.

## Repositório recomendado

Este projeto foi preparado para ficar em **outro repositório**, por exemplo:

`tysudess/central-android`

O projeto desktop continua em:

`tysudess/noticias-python`

A separação evita que builds Android interfiram nos workflows Windows/Ubuntu e permite evoluir a interface mobile sem alterar o desktop.

## APK automático no GitHub

Ao subir todo este projeto para a raiz do novo repositório e fazer push na `main`, o workflow:

1. baixa o projeto Android;
2. baixa `tysudess/noticias-python`;
3. sincroniza automaticamente catálogos de Notícias/Vídeos;
4. sincroniza a URL de autenticação atual;
5. sincroniza a ponte Apps Script de Capas/Gmail;
6. compila somente `arm64-v8a`;
7. gera um APK instalável;
8. publica o APK em **Actions > Artifacts**;
9. cria um **prerelease** no GitHub Releases.

Arquivo final:

`Central-Inteligente-de-Midia-Android-arm64.apk`

## Proxy

**Não existe Proxy Geral no Android.**

Todas as conexões do APK são diretas pela internet do celular.

## Funcionalidades incluídas

- autenticação pelo mesmo Google Apps Script do desktop;
- usuário/senha, sessão, bloqueio, validade e limite de dispositivos;
- troca obrigatória de senha no primeiro acesso;
- Minha Conta / alteração de senha / logout;
- permissões por perfil;
- Notícias;
- Vídeos e catálogo de canais;
- Demandas;
- Fontes;
- Histórico;
- Termos;
- Extrator de Notícias com WebView;
- Capas com ponte Gmail/Apps Script e Valor Econômico;
- Editor PDF (imagens + PDFs, ordenação de entrada, rotação por item, capa e exportação);
- Extrator de Vídeos com yt-dlp Android;
- login Globoplay em WebView e cookies para o extrator;
- Editor de Vídeo com FFmpeg;
- Gravador de Tela com MediaProjection;
- Configurações/automação com WorkManager;
- notificações Android;
- parada de automações.

## Android suportado

- Android 7.0 ou superior (API 24+)
- processador ARM64 (`arm64-v8a`)

O extrator yt-dlp embute Python dentro do APK, por isso o APK será consideravelmente maior que um aplicativo Android comum.

## Observação sobre gravação de tela

O Android exige que o próprio usuário confirme a autorização de MediaProjection ao iniciar cada sessão de captura. O aplicativo não tenta contornar essa proteção. A gravação de áudio fornecida pela tela de gravação usa o microfone; captura do áudio interno de outros aplicativos depende das regras de cada app/versão Android.

## Assinatura

O workflow inicial gera APK **debug**, já instalável e adequado para testes/distribuição interna.

Para publicar na Play Store ou permitir atualizações permanentes sem reinstalação, configure uma chave de assinatura fixa em GitHub Secrets e altere o build para `release`. Consulte `SIGNING.md`.

## Sincronização com o desktop

`scripts/sync_desktop.py` lê diretamente do `noticias-python`:

- fontes de notícias;
- fontes/canais de vídeo;
- URL atual do Apps Script de autenticação;
- configuração de Capas/Gmail;
- jornais de Capas.

Assim não precisamos manter manualmente duas listas diferentes.
