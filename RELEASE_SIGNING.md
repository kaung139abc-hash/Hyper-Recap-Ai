# Android release signing

The Android workflow supports a private release keystore. It never stores the keystore or passwords in the repository.

Add these four **repository Actions secrets** under Settings → Secrets and variables → Actions:

- `ANDROID_KEYSTORE_BASE64`: Base64-encoded contents of your private `.jks` keystore.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: key alias.
- `ANDROID_KEY_PASSWORD`: private-key password.

When all four are present, GitHub Actions builds `app-release.apk` signed with that key and publishes it beside the debug APK. Without all four secrets, the workflow only validates an unsigned release build.

## Important

- Keep the original `.jks` file and passwords backed up in a secure offline location.
- Never commit the keystore or passwords to GitHub or paste them into chat.
- Every future update for existing customers must be signed with the exact same private key. Losing it prevents in-place app updates.
- The signing identity created for this repository is intended for Hyper Recap AI. Treat it as a production credential.
