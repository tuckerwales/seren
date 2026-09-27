# Seren

Seren is Welsh for star. Seren apps are free, open source, and have no ads and no tracking.

This repository holds the whole suite: the apps, and the library they share so they look, feel and
behave the same.

| Module | What it is | Package |
| --- | --- | --- |
| [`ssh`](ssh) | **Seren SSH**, a modern, fast and easy to use SSH client | `wales.tucker.seren.ssh` |
| [`edit`](edit) | **Seren Edit**, a text and code editor (early days) | `wales.tucker.seren.edit` |
| [`auth`](auth) | **Seren Auth**, an authenticator for two-factor sign in codes | `wales.tucker.seren.auth` |
| [`core`](core) | **Seren Core**, the shared theme, components, fonts and security helpers | `wales.tucker.seren.core` |

<p>
  <img src="ssh/docs/screenshots/02_hosts.png" width="200" alt="Seren SSH hosts">
  <img src="ssh/docs/screenshots/09_terminal.png" width="200" alt="Seren SSH terminal">
  <img src="edit/docs/screenshots/01_files.png" width="200" alt="Seren Edit files">
  <img src="edit/docs/screenshots/03_editor.png" width="200" alt="Seren Edit editor">
</p>
<p>
  <img src="auth/docs/screenshots/01_accounts.png" width="200" alt="Seren Auth accounts">
  <img src="auth/docs/screenshots/03_editor.png" width="200" alt="Seren Auth account editor">
</p>

## Building

Requirements: JDK 17 or newer and the Android SDK (platform 36).

```sh
./gradlew assembleDebug        # every app
./gradlew :edit:assembleDebug  # just one
./gradlew testDebugUnitTest    # every module's tests
```

## Design

Colors, type, components, copy and icon rules shared by every app are in
[docs/BRAND.md](docs/BRAND.md). Shared code lives in `core`, so a change there reaches every app.
