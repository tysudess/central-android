# Bordas douradas — Extrator de Vídeos, Editor de Vídeo e Editor PDF

Base consultada: `tysudess/central-android`, branch `main`, commit `483a200a393704df13b54b3f96f60ee38fbca8e5`.
Cor da borda: `#C6A15B` (dourado naval), espessura de `1dp`.

## Arquivos de recursos prontos

Copie estes quatro arquivos para os mesmos caminhos do repositório, substituindo os existentes:

- `app/src/main/res/drawable/bg_card.xml`
- `app/src/main/res/drawable/bg_preview.xml`
- `app/src/main/res/drawable/bg_timeline_panel.xml`
- `app/src/main/res/drawable/bg_status.xml`

Eles estilizam os painéis do Extrator de Vídeos e do Editor de Vídeo, preservando preenchimentos, raios e espaçamentos.

## Ajuste do Editor PDF

O Editor PDF constrói seus cards em Java e usa a constante local `BORDER`; por isso os drawables acima não alteram essa tela. No arquivo `app/src/main/java/br/com/editorpdf/MainActivity.java`, altere somente esta linha:

Antes:
```java
final int BORDER = 0xFFD8E4F3;
```
Depois:
```java
final int BORDER = 0xFFC6A15B;
```

O diff exato está em `PATCHES/PDF-MainActivity.java.diff`. Não substitua `MainActivity.java` por esse arquivo `.diff`; aplique a mudança de uma linha no arquivo original.

## Escopo e validação

- Não altera `CoversActivity.kt` nem o módulo Principais Capas.
- Não altera funções de download, edição, corte ou exportação de arquivos.
- Este ZIP contém arquivos drawable completos e o diff exato para a alteração Java do PDF. A compilação deve ser confirmada pelos workflows do GitHub Actions depois de aplicar as alterações.
