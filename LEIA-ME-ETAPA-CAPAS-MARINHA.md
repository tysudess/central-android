# Ajuste fino do layout Capas — tema naval

## Base consultada

Repositório: `tysudess/central-android`

`main` consultado antes desta alteração: `68dd937f2d9ba51fbabd41f6565148bbee2c6b1b`.

Validação anterior da base:
- Build Android APK #140: sucesso
- Validar Android #16: sucesso

## Arquivo completo incluído

Substitua este arquivo, mantendo exatamente o caminho:

`app/src/main/java/br/com/centralmidia/android/ui/CoversActivity.kt`

## Correções desta versão

- Aplica o layout na primeira abertura e na reentrada da atividade, com tentativas limitadas quando a hierarquia visual ainda não está pronta.
- Reaplica a identidade naval após o scaffold da Central terminar de ajustar a tela.
- Corrige contraste dos rótulos e ícones da navegação inferior; os itens não selecionados ficam claros sobre o fundo azul-marinho e Capas fica dourado.
- Centraliza o item Capas no menu inferior depois que a largura da barra é medida, evitando que a primeira abertura deixe Capas fora da área visível.
- Reduz a altura do cabeçalho, da data, dos botões, dos indicadores e do título da lista para dar mais espaço às capas.
- Não expande todos os jornais automaticamente. O primeiro continua aberto por padrão; jornais pendentes ficam compactos.
- Quando existe uma capa, a grade mostra uma prévia maior mesmo no cartão recolhido. A inserção manual permanece disponível ao expandir o cartão.
- Mantém os motores existentes de busca, Gmail, revisão, seleção, substituição manual e exportação PDF em `MainActivity`/`ModernMainActivity`; este pacote não modifica essas rotinas.

## Como aplicar

1. Descompacte este ZIP na raiz do repositório `tysudess/central-android`.
2. Substitua o arquivo indicado acima.
3. Faça o commit no GitHub.
4. Confirme os workflows `Validar Android (testes e compilação)` e `Build Android APK`.

## Limite da validação local

A integridade do ZIP e a estrutura das pastas foram verificadas. Este ambiente não possui Android SDK/Gradle configurados para compilar o APK completo; o build final deve ser confirmado no GitHub Actions depois do commit. Não altere os secrets de assinatura por causa desta mudança visual.
