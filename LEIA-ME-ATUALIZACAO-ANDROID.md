# Atualização do Central Android sem desinstalar nas versões futuras

## Base verificada

Repositório: `tysudess/central-android`
`main` verificado antes da entrega: `5c6ea770d383990256345a6be5dbb136b0b9c5c9`. O commit mais recente tocou apenas os arquivos da busca de vídeos; os caminhos de assinatura/build deste pacote foram conferidos separadamente no mesmo `main`.
O pacote não envia alterações diretamente ao GitHub.

## O que muda

- Mantém `applicationId = br.com.centralmidia.android`.
- Passa a compilar um APK `release` assinado com uma chave persistente guardada em GitHub Actions Secrets.
- Usa o número da execução de GitHub Actions como `versionCode`, de modo que os builds seguintes usem códigos crescentes.
- O workflow de validação não publica mais um APK de depuração que possa ser confundido com a versão instalável.
- O APK publicado chama-se `Central-Inteligente-de-Midia-Android-arm64.apk` e é compilado pela configuração de release.

## Arquivos completos para colocar no repositório

1. `app/build.gradle.kts`
2. `.github/workflows/build-android.yml`
3. `.github/workflows/validate-android.yml`
4. `.gitignore` (novo; impede que chaves `.p12`, `.jks` e `.keystore` sejam adicionadas por acidente)

Copie os três arquivos existentes para os mesmos caminhos e adicione o `.gitignore` na raiz. Mescle a pasta `.github/workflows`; não apague workflows não listados. O `.gitignore` é uma proteção adicional, não substitui o cuidado de manter a chave fora do repositório.

## Configuração obrigatória dos Secrets

Este pacote acompanha separadamente um arquivo de chave de assinatura (`central-android-chave-assinatura-privada.zip`). Esse ZIP é material privado; **não o coloque nem o extraia dentro do repositório**.

1. Extraia o pacote privado em uma pasta local segura.
2. Na raiz extraída, em PowerShell, gere o texto Base64 da chave:

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes(".\central-android-upload-key.p12")) | Set-Clipboard
   ```

3. No GitHub, abra `tysudess/central-android` → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**.
4. Crie os seguintes secrets:

   - `ANDROID_SIGNING_KEYSTORE_BASE64`: cole o Base64 que ficou na área de transferência.
   - `ANDROID_SIGNING_STORE_PASSWORD`: senha que consta no arquivo `LEIA-ANTES-DE-USAR.txt` do pacote privado.
   - `ANDROID_SIGNING_KEY_PASSWORD`: a senha da chave indicada no mesmo arquivo.
   - `ANDROID_SIGNING_KEY_ALIAS`: alias indicado no mesmo arquivo.

5. Só depois de configurar os quatro secrets, envie os arquivos de código ao `main` e abra **Actions → Build Android APK**. Se os secrets faltarem ou a chave não puder ser aberta, o workflow falhará explicitamente em vez de publicar um APK com assinatura diferente.

## Migração da instalação existente

O workflow antigo produzia APKs com `assembleDebug` e não configurava uma chave persistente explícita. Portanto, não é possível garantir, apenas pelo código do workflow, que a assinatura do APK já instalado seja a mesma da nova chave.

- Se a assinatura coincidir, o Android aceitará a atualização por cima.
- Se a assinatura for diferente, o Android bloqueará a instalação por segurança. Nesse caso, será necessária **uma única migração**: guarde antes qualquer dado local importante, desinstale a versão anterior e instale o primeiro APK assinado pela nova chave.
- **Depois dessa migração**, não altere nem perca o arquivo de chave. Os APKs futuros, gerados pelo workflow com os mesmos Secrets e `applicationId`, terão a mesma assinatura e poderão ser instalados por cima sem desinstalar.

A desinstalação pode apagar dados locais do aplicativo; confirme e faça backup antes de migrar. Não faça a migração antes de o primeiro APK de release assinado estar compilado e disponível.

## Como instalar atualizações futuras

1. Faça as alterações habituais no código e deixe o workflow compilar o APK assinado.
2. Baixe `Central-Inteligente-de-Midia-Android-arm64.apk` na Release gerada pelo workflow.
3. Abra o APK no telefone e confirme **Atualizar** quando o Android perguntar. Não selecione desinstalar.

## Validação e limites

- Os caminhos foram verificados em relação ao `main` consultado.
- A chave PKCS#12 é gerada separadamente; não está incluída no ZIP de código.
- Não foi possível executar o Gradle neste ambiente. A compilação assinada só poderá ser confirmada no GitHub Actions após configurar os Secrets.
- Mantenha uma cópia privada e segura da chave e das senhas. Se perder a chave, as versões assinadas por outra chave não serão atualizações válidas para a instalação atual.
