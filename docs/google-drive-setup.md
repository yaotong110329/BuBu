# Google Drive Cloud Backup setup

BuBu stores existing `.bubu` Data Backup files only in the signed-in user's Google Drive `appDataFolder`. The app requests `https://www.googleapis.com/auth/drive.appdata` plus the narrow `https://www.googleapis.com/auth/drive.file` scope required by the current Android `AuthorizationClient` write flow. Do not add broad Drive scopes such as `drive`, and do not create Firebase, a BuBu account system, or a backend for this feature.

## Google Cloud project

1. Create or select the Google Cloud project used for BuBu.
2. Enable **Google Drive API**.
3. Configure the OAuth consent screen / branding information, including the published app name, support email, privacy-policy URL, and authorised test users while the app is in testing.
4. Declare `drive.appdata` and `drive.file` on the consent screen. Both are non-sensitive; `drive.appdata` confines storage to BuBu's hidden app-data folder, while `drive.file` permits the Android authorization flow to create and modify only files BuBu creates or the user explicitly opens with BuBu.

## OAuth clients

Create Android OAuth clients for package name `com.kumo.bubu`:

- Debug: obtain the debug signing SHA-1 with `./gradlew.bat signingReport` and register it.
- Release: register the release signing SHA-1. If Google Play App Signing is used, also register the SHA-1 from Play Console **App integrity**.

Credential Manager needs a Web OAuth client ID as its `serverClientId` even though BuBu has no backend. This client ID is public configuration, not a secret. Keep the actual value out of Git by adding `BUBU_GOOGLE_WEB_CLIENT_ID=your-web-client-id.apps.googleusercontent.com` to the ignored root `local.properties`, or supplying the same Gradle property in the release build environment.

```powershell
./gradlew.bat :app:signingReport
./gradlew.bat :app:assembleDebug -PBUBU_GOOGLE_WEB_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
```

Do not commit an OAuth client secret, private key, keystore password, access token, refresh token, or a populated machine-specific `local.properties`. BuBu must not persist access tokens. Google Play services obtains short-lived tokens as needed; it must re-authorize when those tokens are no longer valid.

## Signed V1.1.0 release build

Copy `signing.properties.example` to the ignored root `signing.properties`, fill in the path and passwords for the release keystore, then register that keystore's SHA-1 in the Android OAuth client before installing the release build. The build refuses the release verification step when either the Google Web client ID or all four signing values are missing.

```powershell
Copy-Item signing.properties.example signing.properties
./gradlew.bat :app:signingReport :app:verifyReleaseConfiguration :app:bundleRelease
```

Do not publish until the OAuth consent screen is configured for the intended production audience and the release (and, when applicable, Play App Signing) SHA-1 values are registered.

## Test and release checklist

1. Add each test Google account to the OAuth consent screen while the project is in testing.
2. Install the app on a device or Google APIs emulator with Google Play services.
3. Link a first account, upload a backup, modify local data, download the backup, verify the preview, and restore it.
4. Upload more than five backups and verify only the five newest remain in `appDataFolder`.
5. Link a second Google account and verify it cannot see the first account's backups.
6. Revoke / unlink and reconnect, verifying the first account's backups remain available after consent is granted again.

`appDataFolder` is hidden from the regular Drive UI. Retention therefore uses permanent `files.delete`, not trash; it only runs after a new upload has succeeded.
