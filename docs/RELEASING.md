# Releasing Flint

CI builds one APK per architecture (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) plus a
`universal` one. Which one do users need?

| Phone | APK |
|---|---|
| Almost every phone from 2017 on | `arm64-v8a` |
| Old 32-bit phones | `armeabi-v7a` |
| Emulators, Chromebooks, x86 tablets | `x86_64` or `x86` |
| Not sure | `universal` (largest, works everywhere) |

## One-time: create a signing key

```bash
keytool -genkeypair -v -keystore flint-release.jks -alias flint \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 flint-release.jks > flint-release.jks.b64     # macOS: base64 -i flint-release.jks
```

Add these in GitHub: Settings, Secrets and variables, Actions, New repository secret.

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the content of `flint-release.jks.b64` |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `flint` |
| `KEY_PASSWORD` | the key password |

Back up `flint-release.jks` somewhere safe. If you lose it, you can't update installed apps.
Never commit it.

## Each release

1. Raise `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Commit, then tag and push:
   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```
3. The workflow builds, tests, signs, and attaches every APK plus `SHA256SUMS.txt` to a GitHub
   Release. Builds without the secrets are uploaded only as a workflow artifact named
   `...DEBUG-SIGNED-do-not-distribute`, never to a Release.
