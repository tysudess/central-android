# Como subir no GitHub

## Recomendado: novo repositório

Crie:

`central-android`

Depois extraia o ZIP entregue pelo ChatGPT e envie **todo o conteúdo da pasta `central-android` para a raiz do novo repositório**.

Não crie uma pasta `central-android/central-android`.

Estrutura esperada na raiz:

- `.github/`
- `app/`
- `scripts/`
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle.properties`
- `README.md`

Após o commit na `main`, abra **Actions** e acompanhe `Build Android APK`.

Ao terminar, o APK estará em:

- Actions > Artifacts > `Central-Android-APK`
- Releases > `Central Inteligente de Mídia Android - Build ...`

## Pode usar o mesmo repositório do desktop?

Tecnicamente sim, colocando tudo em uma pasta `android/` e ajustando paths/workflows. Não é a opção recomendada porque:

- mistura três plataformas no mesmo pipeline;
- aumenta chance de um commit Android cancelar/acionar build desktop;
- dificulta releases e versionamento;
- Android tem dependências e assinatura próprias.

Por isso este pacote está preparado como **novo repositório independente**, sincronizado automaticamente com `noticias-python` durante o build.
