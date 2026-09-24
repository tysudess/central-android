CENTRAL INTELIGENTE DE MÍDIA - ANDROID
Pacote de layout mobile baseado na main verificada em:
695aa310c9f832a1c376d92cedb4be959bfd84c8

OBJETIVO
Aplicar no Android o layout aprovado para:
- Início
- Notícias
- Vídeos

ARQUIVOS COMPLETOS PARA SUBSTITUIR/ADICIONAR
1. app/src/main/java/br/com/centralmidia/android/ui/MainActivity.kt
2. app/src/main/java/br/com/centralmidia/android/ui/NewsActivity.kt
3. app/src/main/java/br/com/centralmidia/android/ui/VideosActivity.kt
4. app/src/main/java/br/com/centralmidia/android/ui/MobileScaffold.kt  (NOVO)

PONTOS IMPORTANTES
- NÃO altera Windows/Ubuntu.
- NÃO altera autenticação, usuários, senha, sessão/token ou Apps Script.
- NÃO adiciona Proxy Geral. No Android o layout mostra "Conexão direta".
- Mantém Minha Conta e troca de senha existentes.
- Mantém permissões por usuário/perfil no menu Mais.
- Parar buscas continua acessível pelo menu Mais.
- Notícias mantém pesquisa, filtro por fonte e integração com Extrator de Notícias.
- Notícias adiciona ações Abrir matéria, WhatsApp e Copiar link.
- Vídeos mantém catálogo sincronizado e abertura/pesquisa nas fontes existentes.
- As demais áreas continuam acessíveis pelo menu Mais.

WORKFLOW: NÃO PRECISA ALTERAR

Após subir estes arquivos na main, o workflow atual Build Android APK deve iniciar pelo gatilho app/**.
