# Central Android v1.0.1 — Correção do Build APK ARM64

## Repositório

`tysudess/central-android`

## Erro confirmado no GitHub Actions

O build falhou em:

`app/src/main/java/br/com/centralmidia/android/ui/PasswordDialogs.kt`

com erros:

- `Cannot access 'toast': it is protected in BaseActivity`
- `Cannot access 'io': it is protected in BaseActivity`
- `Cannot access 'auth': it is protected in BaseActivity`

## Causa

`PasswordDialogs` é um `object` auxiliar e não herda de `BaseActivity`.

Em Kotlin, membros `protected` só podem ser acessados pela própria classe ou
por subclasses. Portanto o helper não podia chamar:

- `activity.auth`
- `activity.toast(...)`
- `activity.io(...)`

## Correção

No `BaseActivity.kt`:

- `auth` passou de `protected` para `internal`;
- `toast()` passou de `protected` para `internal`;
- `io()` passou de `protected` para `internal`.

`openUrl()` e `externalUrl()` permanecem `protected`, pois são usados pelas
Activities derivadas.

`internal` mantém os membros restritos ao módulo Android `app`, sem torná-los
API pública externa.

## Arquivo para substituir

`app/src/main/java/br/com/centralmidia/android/ui/BaseActivity.kt`

## Workflow

NÃO PRECISA ALTERAR.

Depois de substituir o arquivo e fazer commit em `main`, o workflow
`Build Android APK` será executado novamente.
