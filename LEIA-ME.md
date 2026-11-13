# Correção inicial do áudio do Gravador de Tela — Android

## Base consultada
- Repositório: `tysudess/central-android`
- Branch: `main`
- Commit consultado: `59a8cddca154ac0fe3984bd9c3292c7a05ef571c`

## O que muda
1. A opção **Gravar áudio pelo microfone** passa a vir ativada por padrão.
2. Uma migração única ativa a opção também em instalações que tinham salvo o padrão antigo como desativado. Depois dessa migração, a escolha do usuário é respeitada.
3. O serviço deixa de iniciar silenciosamente quando o microfone foi solicitado, mas a permissão `RECORD_AUDIO` não está concedida. A falha passa a ser informada.
4. O texto de ajuda diferencia microfone de áudio interno de outros aplicativos.

## Limite importante
Esta correção habilita a gravação pelo **microfone**. Ela não adiciona ainda a captura do áudio interno reproduzido por outros aplicativos. No Android, áudio interno exige `AudioPlaybackCapture` (Android 10/API 29 ou superior) e só funciona quando o aplicativo de origem permite a captura; alguns conteúdos protegidos não podem ser capturados.

## Aplicação
Extraia os dois arquivos do ZIP na raiz do repositório e aplique:

```bash
git apply 01-correcao-audio-microfone.patch
```

O ZIP contém um patch, não arquivos Kotlin completos. Não renomeie o `.patch` para `.kt` nem o substitua diretamente no projeto.

## Validação manual depois do build
1. Abra **Gravador de Tela** e confirme que **Gravar áudio pelo microfone** está marcado.
2. Inicie uma gravação e conceda a permissão de microfone quando o Android solicitar.
3. Fale durante a gravação e depois reproduza o MP4 para conferir o áudio.
4. Desmarque a opção e confirme que a gravação sem microfone continua funcionando.

A alteração ainda precisa ser compilada e validada pelo GitHub Actions; não foi executado um build deste patch nesta sessão.
