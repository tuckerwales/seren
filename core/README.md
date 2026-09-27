# Seren Core

The library every Seren app is built on, so the apps cannot drift apart. The rules it implements
are in [docs/BRAND.md](../docs/BRAND.md).

| Package | Contents |
| --- | --- |
| `ui.theme` | `SerenTheme` with the light and dark fallback schemes, `ThemeMode`, `MonoFamily`, `MonoSmall`, `MonoMedium`, `AccentColors`, `StatusColors`, `SystemBarAppearance` |
| `ui` | `SectionHeader`, `GroupedTile` and `groupedShape`, `Avatar`, `StatusDot`, `EmptyState`, `Chip`, `pillFieldColors`, `SwitchRow`, `NavRow`, `KeyCap` (extra keys), `LockScreen`, the Appearance, color scheme and text size settings, `AboutDialog`, `relativeTime`, `copyToClipboard` |
| `content` | `ContentColorSchemes`: the named canvas color schemes (Midnight, Dracula, Nord and so on) |
| `security` | `SecretBox` (AES-256-GCM with a Keystore key) and `AppLock` (biometrics and hiding from recents) |

Resources: the JetBrains Mono fonts and their license, `Theme.Seren` and the window background
colors, the shared icon background gradient and the backup exclusion rules.

Apps depend on it with `implementation(project(":core"))`.
