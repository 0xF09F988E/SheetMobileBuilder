# Workflow

## Repo Root

Use this directory for all Git operations:

```powershell
cd C:\Users\LEVI\Documents\pwa
```

From here you should run:

- `git status`
- `git add .`
- `git commit -m "Message"`
- `git pull --rebase origin main`
- `git push origin main`

## Android Build Directory

Use this directory for Gradle commands:

```powershell
cd C:\Users\LEVI\Documents\pwa\android
```

Examples:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
.\gradlew.bat assembleRelease
.\gradlew.bat bundleRelease
```

## Safe Git Setup

If Git complains about `dubious ownership`, register the repo once:

```powershell
git config --global --add safe.directory C:/Users/LEVI/Documents/pwa
```

## Protected Local Files

The project ignores machine-specific and sensitive files such as:

- `android/keystore.properties`
- `android/local.properties`
- `android/backups/`
- local `.db` snapshots
- signing files (`.jks`, `.keystore`, `.pem`, `.p12`, `.key`)

Do not commit local backups, keystores, or device-specific config.
