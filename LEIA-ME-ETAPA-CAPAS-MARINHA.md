# Etapa — novo layout de Capas com tema naval

Base consultada: `tysudess/central-android`, `main` no commit `f5ef568aef1ee6f902930f2e8e6fe8615f552366`.

Builds de referência antes da mudança:
- Build Android APK #138: sucesso
- Validar Android #15: sucesso

## Arquivo incluído

Substituir o arquivo completo:
`app/src/main/java/br/com/centralmidia/android/ui/CoversActivity.kt`

## O que esta etapa muda

- Paleta azul-marinho, azul naval, branco e dourado.
- Cabeçalho da aba com identidade da Central e fundo navy.
- Ações Atualizar capas (navy) e Gerar PDF (dourado).
- Contadores compactos de jornais, capas disponíveis, pendências e seleção.
- Grade adaptável de cartões com prévias, status, revisão e inserção manual.
- Destaque dourado da aba Capas no dock compartilhado.
- Correção do padding inferior do módulo para a grade não perder espaço útil nem ficar encoberta pela navegação.
- Preserva o módulo de busca, os dados de capa, seleção, inserção manual, revisão e exportação existentes; não altera os motores de captura ou a criação do PDF.

## Aplicação

1. Descompacte o ZIP na raiz de `tysudess/central-android`.
2. Mantenha a estrutura de diretórios e substitua apenas `CoversActivity.kt`.
3. Faça commit no GitHub.
4. Confira `Validar Android (testes e compilação)` e `Build Android APK` antes de usar o APK.

## Limite da validação

O arquivo foi revisado estruturalmente e os delimitadores foram verificados. O ZIP foi validado quanto à integridade. Não foi possível executar uma compilação Android completa local nesta sessão; o build do GitHub Actions continua sendo a validação final. Se os cartões tiverem problemas de medida em algum aparelho, o passo seguinte deve ser um ajuste de layout após validar o APK, não uma mudança nos motores de captura.
