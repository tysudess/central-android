# Estado do pacote entregue

## Validado neste ambiente

- estrutura do projeto Android;
- sintaxe XML de todos os manifests/resources;
- JSON dos assets;
- sintaxe Python de `scripts/sync_desktop.py`;
- varredura de sintaxe Kotlin com o compilador Kotlin local (as referências Android ficam sem resolução fora do Android SDK, mas não foram encontrados erros de parsing Kotlin);
- projeto desktop atual conferido antes da criação do Android;
- `noticias-python` confirmado como repositório público, então o workflow Android consegue fazer o checkout para sincronização sem token adicional.

## Validação final automática

O próprio GitHub Actions executa:

1. sincronização com `noticias-python`;
2. resolução real das dependências Android;
3. Android Gradle Plugin;
4. compilação do APK ARM64;
5. geração de SHA-256;
6. publicação de Artifact e prerelease.

A primeira execução do workflow é, portanto, também o teste completo em Android SDK real.

## Importante

O APK inicial é `debug`, assinado automaticamente para instalação/testes. Para uma distribuição permanente/Play Store, configurar assinatura release fixa conforme `SIGNING.md`.
