# Matriz de funcionalidades — Desktop x Android

| Desktop | Android |
|---|---|
| Login / usuários | Mesmo Apps Script, direto pela internet |
| Troca de senha | Implementado |
| Minha Conta | Implementado |
| Proxy Geral | Removido propositalmente no Android |
| Notícias | Busca Google News + fontes sincronizadas |
| Vídeos | Catálogo completo sincronizado + navegação |
| Demandas | Cadastro e busca no aparelho |
| Fontes | Catálogo de notícias e vídeos sincronizado |
| Histórico | SQLite local |
| Termos | SQLite local + automação |
| Parar | Cancela WorkManager e gravação |
| Extrator de Notícias | WebView + extração DOM + TXT/compartilhamento |
| Capas | Ponte Gmail/Apps Script, Valor PDF e fontes web |
| Editor PDF | Imagens/PDFs, rotação, capa e exportação de alta resolução |
| Extrator de Vídeos | yt-dlp Android ARM64 |
| Login Globoplay | WebView + cookies Netscape para yt-dlp |
| Editor de Vídeo | FFmpeg Android: corte, compressão, rotação, áudio |
| Gravador de Tela | MediaProjection + microfone |
| Configurações | Automação Android; sem proxy |
| Automação | WorkManager + notificações |

## Diferenças impostas pelo Android

Algumas implementações não podem ser literalmente iguais às do Windows/Linux:

- não existe `Qt WebEngine`/Electron dentro deste APK; usa Android WebView;
- não existe `DPAPI`; o token usa Android Keystore;
- não há `.exe` de FFmpeg/yt-dlp; são bibliotecas Android ARM64;
- gravação de tela usa MediaProjection e sempre solicita consentimento do Android;
- trabalho em segundo plano usa WorkManager, com intervalo mínimo de 15 minutos;
- arquivos são gravados usando MediaStore/armazenamento do Android.

Essas diferenças mantêm a função para o usuário usando mecanismos suportados pelo sistema operacional móvel.
