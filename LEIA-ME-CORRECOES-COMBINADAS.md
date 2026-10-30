# Central Android — correções combinadas

**Repositório-base consultado:** `tysudess/central-android`  
**Commit de `main` consultado:** `483a200a393704df13b54b3f96f60ee38fbca8e5`

Este pacote foi preparado para ser extraído sobre a raiz do repositório. Ele contém os arquivos completos nos mesmos caminhos do projeto; não é necessário aplicar um patch manual.

## Correções incluídas

1. **Editor de Vídeo — adicionar vídeos:** o botão `Adicionar` abre o seletor de documentos do Android (`video/*`), aceita um ou vários vídeos do aparelho e importa uma cópia temporária para o editor. Não fica limitado à pasta `Downloads/ExtratorVideos`. Os arquivos exportados continuam sendo salvos no destino já usado pelo editor.
2. **Início — alinhamento:** altura consistente nos cards de indicadores, alinhamento vertical do conteúdo, limites e reticências controladas nos títulos e descrições para reduzir desalinhamentos em telas menores.
3. **Editor PDF — contorno dourado:** cabeçalho e cards de pré-visualização, páginas e configurações usam borda dourada; as miniaturas também recebem o contorno.
4. **Histórico — remoção da navegação:** remove o item `Histórico` da navegação inferior das telas da Central e dos módulos independentes, sem depender de qual módulo está aberto. O atalho já havia sido removido do grupo `Mais` em `main`.
5. **Fontes — primeira renderização:** a borda dourada é configurada no momento da criação dos cards de filtros, modo, controles e cada fonte, em vez de depender da aplicação posterior do tema.
6. **Cards do Extrator/Editor de Vídeo:** os drawables compartilhados de cards, status, pré-visualização e painel da timeline passam a usar o contorno dourado.

A aba **Capas** e o módulo `br.com.principaiscapas` não foram modificados.

## Arquivos para substituir/adicionar

- `app/src/main/java/br/com/extratorvideos/EditorActivity.java`
- `app/src/main/java/br/com/editorpdf/MainActivity.java`
- `app/src/main/java/br/com/centralmidia/android/ui/MainActivity.kt`
- `app/src/main/java/br/com/centralmidia/android/ui/SourcesActivity.kt`
- `app/src/main/java/br/com/centralmidia/android/ui/NavalTheme.kt`
- `app/src/main/res/drawable/bg_card.xml`
- `app/src/main/res/drawable/bg_preview.xml`
- `app/src/main/res/drawable/bg_status.xml`
- `app/src/main/res/drawable/bg_timeline_panel.xml`

## Aplicação

1. Baixe e extraia o ZIP na raiz do repositório.
2. Substitua os arquivos correspondentes quando o GitHub solicitar.
3. Faça o commit.
4. Execute `Validar Android (testes e compilação)` e, depois de aprovada, `Build Android APK`.

A versão de `main` usada como base estava com os workflows de validação e build aprovados. A compilação **desta nova alteração conjunta ainda não foi executada** neste ambiente; confirme o resultado pelos workflows após o commit.
