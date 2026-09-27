# Seren

**Seren** (Welsh for “star”, said *SEH-ren*) is a suite of free, open source Android apps with no
ads and no tracking.

This repository is the monorepo for the whole suite: each app in its own Gradle module, plus
**Seren Core**, the shared library that keeps them looking, feeling and behaving the same.

Every app makes the same promises:

| Promise | In practice |
| --- | --- |
| **Free, always** | No paid tier, no “pro” unlock, no nag screens |
| **No ads, no tracking** | No ad SDKs, no analytics, no crash reporters, no network calls you did not ask for |
| **Open source** | Full source here; builds with `./gradlew assembleRelease` |
| **Yours, on your device** | Data stays local; secrets use a hardware-backed key; nothing sensitive goes into cloud backup |

<p>
  <img src="ssh/docs/screenshots/02_hosts.png" width="200" alt="Seren SSH hosts">
  <img src="ssh/docs/screenshots/09_terminal.png" width="200" alt="Seren SSH terminal">
  <img src="edit/docs/screenshots/01_files.png" width="200" alt="Seren Edit files">
  <img src="edit/docs/screenshots/03_editor.png" width="200" alt="Seren Edit editor">
</p>

## Apps

| App | Module | Package | Status |
| --- | --- | --- | --- |
| **[Seren SSH](ssh)** | [`ssh`](ssh) | `wales.tucker.seren.ssh` | Ready for daily use |
| **[Seren Edit](edit)** | [`edit`](edit) | `wales.tucker.seren.edit` | Early days — edits well; no syntax highlighting or search yet |
| **[Seren Core](core)** | [`core`](core) | `wales.tucker.seren.core` | Shared theme, components, fonts and security helpers |

### Seren SSH

A modern, fast and easy to use SSH client for Android. Custom xterm-compatible terminal, public
key and password auth (including 2FA prompts), jump hosts, port forwarding, SFTP, and multiple
sessions kept alive in the background. Passwords and private keys are encrypted with the Android
Keystore.

→ Full features, architecture and tests: [`ssh/README.md`](ssh/README.md)

### Seren Edit

A modern, fast and easy to use text and code editor for Android. JetBrains Mono on the same color
schemes as Seren SSH, folders via the Storage Access Framework, recent files, and careful handling
of encodings and line endings. No storage or network permission — it only reaches files you choose.

→ Full features, architecture and tests: [`edit/README.md`](edit/README.md)

## Repository layout

```
seren/
├── core/          Seren Core (shared library)
├── ssh/           Seren SSH
├── edit/          Seren Edit
├── docs/BRAND.md  Brand and design guide for the suite
└── README.md      This file
```

Each app module has its own README, screenshots under `<module>/docs/screenshots`, and builds to
its own APK. Shared UI, fonts, `SecretBox` and app lock live in `core` — a change there reaches
every app.

## Building

Requirements: JDK 17 or newer and the Android SDK (platform 36).

```sh
./gradlew assembleDebug          # every app
./gradlew :ssh:assembleDebug     # Seren SSH only  → ssh/build/outputs/apk/debug/
./gradlew :edit:assembleDebug    # Seren Edit only → edit/build/outputs/apk/debug/
./gradlew assembleRelease        # minified release APKs (debug-signed until you add signing config)
./gradlew testDebugUnitTest      # every module’s tests
```

CI builds and tests on every push and pull request; debug and release APKs are uploaded as
artifacts.

## Design

Colors, type, components, copy, icons and privacy patterns shared by every app are in
[docs/BRAND.md](docs/BRAND.md). The code that implements them is in [`core`](core).

## Licenses

Third-party licenses for each app are listed in that app’s README and in its About dialog.
JetBrains Mono (SIL OFL 1.1) ships in Seren Core; see `core/src/main/assets/licenses`.
