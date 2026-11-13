# Correção da falha no Build Android — Gravador de Tela

**Repositório:** `tysudess/central-android`  
**Base verificada:** `main` no commit `d2e820f16506918f4db436e1655fb0e15859f40b`  
**Workflows afetados:** `Build Android APK` e `Validar Android (testes e compilação)`.

## Diagnóstico do erro

Os dois workflows falharam na etapa `Integrar captura de áudio e correção da seleção de área`, antes de configurar o Java ou executar o Gradle. O comando `git apply --check 02-aplicar-correcao-audio-area.patch` rejeitou dois hunks por divergência de contexto:

- `ScreenRecordService.kt`, próximo à declaração do arquivo temporário do gravador;
- `ScreenRecorderActivity.kt`, próximo ao texto explicativo da configuração.

O build, portanto, não chegou a compilar o código Kotlin. Não era um erro do Gradle.

## Arquivos incluídos

Substitua os três arquivos pelos que estão no ZIP, preservando estes caminhos a partir da raiz do repositório:

- `.github/workflows/build-android.yml` — workflow completo, com a etapa de integração chamando o script robusto;
- `.github/workflows/validate-android.yml` — workflow completo com a mesma chamada;
- `scripts/integrar_gravador.py` — aplica os hunks válidos do patch, corrige os dois pontos divergentes por âncoras verificadas, remove arquivos `.rej` e valida se a integração ficou completa.

## Aplicação

1. Extraia o ZIP na raiz de uma cópia atualizada do repositório `central-android`, substituindo os dois workflows e adicionando o script em `scripts/`.
2. Confira os três arquivos alterados e faça o commit na `main`.
3. Execute novamente `Validar Android` e `Build Android APK` em GitHub Actions.

O script é idempotente: se detectar que os fontes já estão totalmente integrados, não aplica o patch novamente.

## Escopo e cuidados

- Não altera o módulo Capas.
- Não altera diretamente o GitHub; o ZIP contém somente os arquivos a serem substituídos/adicionados.
- A mudança corrige o passo de integração que impedia o build de começar. Ela não comprova, por si só, que o áudio será capturado em todos os aplicativos; isso ainda depende de teste em aparelho e das restrições de captura do Android.

## Validação nesta entrega

O script passou pela verificação sintática de Python e os workflows passaram pela leitura/validação estrutural YAML. O build real deve ser reexecutado no GitHub depois que esses três arquivos forem enviados.
