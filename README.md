# SheetMobileBuilder

![Android](https://img.shields.io/badge/Android-7%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin&logoColor=white)
![SQLite](https://img.shields.io/badge/SQLite-Local%20DB-003B57?logo=sqlite&logoColor=white)
![Offline First](https://img.shields.io/badge/Mode-Offline%20First-2F855A)
![License](https://img.shields.io/badge/License-Apache%202.0-blue)

SheetMobileBuilder is an Android app for building local data structures, importing spreadsheet data, capturing records in the field, reviewing them offline, and exporting results without depending on a server.

It is designed as an agnostic mobile data tool. The app is not tied to a single domain model: users define their own tables and fields, then work with those records locally on the device.

## What It Does

- Creates custom tables and columns directly on the device
- Supports dynamic field types such as text, long text, number, date, boolean, and list
- Imports Excel data into previously defined structures
- Captures new records manually in offline scenarios
- Searches and updates records from a master table
- Tracks review state through `pending`, `confirmed`, and `updated`
- Exports filtered data to CSV

## Core Modules

| Code | Module | Purpose |
| --- | --- | --- |
| `P01` | Home | High-level summary of tables and records |
| `P02` | Design | Create tables and define columns |
| `P03` | Import | Read Excel and import matching data |
| `P04` | Browse | Paginated local record exploration |
| `P05` | Query | Search, inspect, confirm, and edit records |
| `P06` | Export | Export the master table to CSV |
| `P07` | New Record | Manual record capture |

## Product Direction

SheetMobileBuilder follows a few practical rules:

- Offline-first: records live on the device
- Agnostic data model: users define the structure
- Operational UI: optimized for fast capture and review
- Local integrity: schema, lookup, review state, and export stay consistent inside the app

## Technical Stack

- Android Views
- Kotlin
- SQLite
- Material 3
- Navigation Component
- RecyclerView
- Coroutines + ViewModel

## Project Structure

```text
pwa/
├─ android/
│  ├─ app/
│  │  ├─ src/main/java/com/pwa/offline/
│  │  ├─ src/main/res/
│  │  └─ build.gradle
├─ LICENSE
├─ NOTICE
└─ README.md
```

## Build Requirements

- Android Studio
- JDK 17
- Android SDK configured locally
- A connected Android device or emulator for install tasks

## Build Commands

From the project root:

```powershell
cd android
.\gradlew.bat assembleDebug
```

Install debug build:

```powershell
cd android
.\gradlew.bat installDebug
```

Create release APK:

```powershell
cd android
.\gradlew.bat assembleRelease
```

Create Play Store bundle:

```powershell
cd android
.\gradlew.bat bundleRelease
```

## Notes for Development

- Package name: `com.pwa.offline`
- Minimum Android version: `7+` (`minSdkVersion` from project config)
- Target/compile SDK are managed from the Android root project ext properties
- Release signing is read from `android/keystore.properties` when available

## Data Model

The app uses a metadata-driven local model:

- collections
- fields
- records
- record values
- lookup index
- unique index
- review log

This allows dynamic schemas without creating a new physical SQLite table for every user-defined structure.

## Current Capabilities

- Master table selection
- Exact lookup for query flows
- Unique value enforcement
- Review tracking
- Option tables for assisted capture
- Delete and share actions in browse flow
- Single-field editing in query flow

## License

This project is licensed under Apache-2.0.

See:

- [LICENSE](LICENSE)
- [NOTICE](NOTICE)
