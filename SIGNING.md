# Assinatura do APK

O workflow incluído gera `assembleDebug`. Esse APK é instalável e serve para testes internos.

Para uma distribuição definitiva, crie uma chave Android única e mantenha-a privada. Não coloque o `.jks` em um repositório público.

Recomendação de Secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Depois, adapte `app/build.gradle.kts` e o workflow para `assembleRelease`.

A mesma chave precisa ser usada em todas as futuras versões; caso contrário o Android não permitirá atualizar o aplicativo já instalado.
