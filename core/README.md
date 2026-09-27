# Seren Core

The library every Seren app is built on, so the apps cannot drift apart. The rules it implements
are in [docs/BRAND.md](../docs/BRAND.md).

| Package | Contents |
| --- | --- |
| `ui.theme` | `SerenTheme` with the light and dark fallback schemes, `ThemeMode`, `MonoFamily`, `MonoSmall`, `MonoMedium`, `AccentColors`, `StatusColors`, `SystemBarAppearance` |
| `ui` | `SectionHeader`, `GroupedTile` and `groupedShape`, `Avatar`, `StatusDot`, `EmptyState`, `Chip`, `pillFieldColors`, `SwitchRow`, `NavRow`, `KeyCap` (extra keys), `LockScreen`, the Appearance, color scheme and text size settings, `AboutDialog`, `PasswordField` and the backup password dialogs, `relativeTime`, `copyToClipboard` |
| `content` | `ContentColorSchemes`: the named canvas color schemes (Midnight, Dracula, Nord and so on) |
| `security` | `SecretBox` (AES-256-GCM with a Keystore key) and `AppLock` (biometrics and hiding from recents) |
| `backup` | `BackupCrypto` (scrypt and AES-256-GCM) and `PasswordSeal`, the password protection every Seren backup file shares |
| `suite` | `SuiteApp` (the four apps), `Suite` (the intents the apps hand files to each other with, and `launch`) and `rememberInstalled`; see [section 19 of the brand guide](../docs/BRAND.md#19-working-together) |

Resources: the JetBrains Mono fonts and their license, `Theme.Seren` and the window background
colors, the shared icon background gradient and the backup exclusion rules. Its manifest, merged
into every app, declares the `wales.tucker.seren.permission.SUITE` signature permission and lists
the Seren packages under `<queries>`.

Apps depend on it with `implementation(project(":core"))`.
