# Brand and design guide

This guide describes how the apps in the suite look, feel and speak. Terminal is the first app and
the reference implementation: every value here is taken from its code, so when in doubt, open
`app/src/main/java/wales/tucker/terminal/ui/theme/Theme.kt` and
`app/src/main/java/wales/tucker/terminal/ui/common/Components.kt`.

The goal is that someone who uses one of the apps feels at home in the next one: same colors, same
shapes, same words for the same things, same respect for their device and their data.

Contents

1. [What the suite stands for](#1-what-the-suite-stands-for)
2. [Naming](#2-naming)
3. [App icons](#3-app-icons)
4. [Color](#4-color)
5. [Typography](#5-typography)
6. [Shape](#6-shape)
7. [Spacing and layout](#7-spacing-and-layout)
8. [Components](#8-components)
9. [Iconography](#9-iconography)
10. [Motion and haptics](#10-motion-and-haptics)
11. [Voice and copy](#11-voice-and-copy)
12. [Privacy and trust patterns](#12-privacy-and-trust-patterns)
13. [Accessibility](#13-accessibility)
14. [Repository, README and store listing](#14-repository-readme-and-store-listing)
15. [Starting a new app](#15-starting-a-new-app)
16. [Applying this to the text editor](#16-applying-this-to-the-text-editor)

---

## 1. What the suite stands for

Every app in the suite makes the same four promises. They are the brand; everything else in this
guide exists to express them.

| Promise | What it means in practice |
| --- | --- |
| **Free, always** | No paid tier, no "pro" unlock, no nag screens, no donation prompts inside the app. A donation link may live in the README and the store listing only. |
| **No ads, no tracking** | No ad SDKs, no analytics, no crash reporting services, no network calls the user did not ask for. The `INTERNET` permission is only requested when the app's job needs it. |
| **Open source** | The full source is public and builds with `./gradlew assembleRelease`. Third party licenses are listed in the README and in the app's About dialog. |
| **Yours, on your device** | Data stays on the device. Secrets are encrypted with a hardware-backed key. Nothing sensitive goes into cloud backup. Export is always available so nobody is locked in. |

Design principles that follow from those promises:

- **Quiet and fast.** The app opens straight into the thing you came for. No splash screens,
  onboarding carousels or "what's new" popups.
- **Native first.** Material 3 and Jetpack Compose, following platform conventions (back gesture,
  dynamic color, edge-to-edge, system font) rather than inventing new ones.
- **Power without clutter.** Common actions are one tap away; advanced options live in overflow
  menus, long presses and settings, never removed.
- **Honest.** The app says what it is doing, what went wrong and what will happen when you tap a
  button. Destructive actions always confirm and say exactly what will be lost.

## 2. Naming

- **App names are plain, capitalised nouns** that say what the app is: `Terminal`, then `Editor`
  (or `Text`, `Notes`, `Files`...). One word where possible. No prefixes, puns or version numbers.
- **The suite name** is not decided yet. When it is, use it only in the README, the store
  developer name and the About dialog, never in the launcher label. Keep launcher labels short so
  they never truncate under the icon.
- **Package ids** follow `wales.tucker.<app>` in lower case (`wales.tucker.terminal`,
  `wales.tucker.editor`). The Compose theme function is `<App>Theme` (`TerminalTheme`).
- **Repository names** match the package suffix (`tuckerwales/terminal`, `tuckerwales/editor`).
- In prose the app name is written as a proper noun without "the": "Terminal keeps sessions alive",
  not "the Terminal app keeps...".

## 3. App icons

Every app uses an **adaptive icon** with three layers, all as vector drawables
(`res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`,
`ic_launcher_monochrome.xml`, wired up in `res/mipmap-anydpi/ic_launcher.xml`).

### Anatomy (108 x 108 dp viewport)

| Layer | Terminal | Rule for every app |
| --- | --- | --- |
| Background | Diagonal linear gradient, top left to bottom right: `#3A2F8F` at 0, `#1E2A6B` at 0.55, `#0E1434` at 1 | **Identical in every app.** This deep indigo gradient is the suite's signature. |
| Frame | Rounded rectangle from (24,34) to (84,74), 6 dp corners, fill `#1AFFFFFF`, 1.5 dp stroke `#66FFFFFF` | A translucent white "object" that represents the app (a window, a page, a folder). Keep it inside the 66 dp safe zone (x and y from 21 to 87). |
| Primary glyph | Prompt chevron, 4.5 dp round stroke, `#8FA8FF` | One simple stroked mark in **periwinkle `#8FA8FF`**, 4.5 dp, round caps and joins. |
| Accent | Cursor bar, 15 x 4.5 dp pill, `#7DF0C8` | One small filled mark in **mint `#7DF0C8`**. The mint cursor is the suite's recurring detail: use it in every icon. |
| Monochrome | Chevron and cursor only, pure white | Glyph and accent only, no frame or background, for themed icons on Android 13+. |

### Notification icon

`res/drawable/ic_notification.xml`: 24 x 24 dp, white only, the same glyph and accent redrawn at
2.2 dp stroke. No frame, no color, no gradient.

### Do and don't

- Do keep glyphs geometric, stroked, with round caps.
- Do test the icon under circle, squircle and teardrop masks and at 48 dp.
- Don't add text, letters or gradients to the glyph.
- Don't change the background gradient per app; the glyph is what tells apps apart.

## 4. Color

### Brand colors

| Name | Hex | Use |
| --- | --- | --- |
| **Indigo** (brand) | `#5B6CF9` | `@color/brand`, `android:colorAccent`, the first accent in the palette, marketing. |
| Primary, light | `#4B55C8` | `primary` in the light scheme. |
| Primary, dark | `#BFC2FF` | `primary` in the dark scheme. |
| Icon indigo | `#3A2F8F` > `#1E2A6B` > `#0E1434` | Icon background gradient, feature graphic. |
| Periwinkle | `#8FA8FF` | Icon glyph. |
| Mint | `#7DF0C8` | Icon accent (cursor). |

### Material color schemes

Every app ships the same two fallback schemes, used when dynamic color is off or unavailable
(below Android 12). Copy them verbatim from `Theme.kt`.

| Role | Light | Dark |
| --- | --- | --- |
| primary / onPrimary | `#4B55C8` / `#FFFFFF` | `#BFC2FF` / `#151E97` |
| primaryContainer / on | `#E0E0FF` / `#00006E` | `#323BAE` / `#E0E0FF` |
| secondary / onSecondary | `#006B5E` / `#FFFFFF` | `#82D5C4` / `#003730` |
| secondaryContainer / on | `#9EF2E0` / `#00201B` | `#005046` / `#9EF2E0` |
| tertiary | `#7A5260` | `#EAB8C8` |
| tertiaryContainer | `#FFD9E3` | `#603B48` |
| background, surface | `#F8F8FC` | `#111318` |
| onBackground, onSurface | `#1A1B21` | `#E3E1E9` |
| surfaceVariant / on | `#E3E1EC` / `#46464F` | `#46464F` / `#C7C5D0` |
| surfaceContainerLowest | `#FFFFFF` | `#0C0E13` |
| surfaceContainerLow | `#F2F2F8` | `#1A1B21` |
| surfaceContainer | `#ECECF3` | `#1E1F25` |
| surfaceContainerHigh | `#E6E6EE` | `#282A2F` |
| surfaceContainerHighest | `#E1E1E9` | `#33343A` |
| outline / outlineVariant | `#777680` / `#C7C5D0` | `#91909A` / `#46464F` |
| error / errorContainer | `#BA1A1A` / `#FFDAD6` | `#FFB4AB` / `#93000A` |

The indigo primary with a teal secondary is the suite's personality. The window background
(`@color/window_background`, `#F8F8FC` light and `#111318` dark) matches `background` so there is
no flash between the launch window and the first frame.

### Theme settings

Every app offers the same two appearance settings, in this order, under an **Appearance** section:

- **Theme:** System, Light, Dark (a segmented button row). Default: System.
- **Dynamic color:** "Use colors from your wallpaper". Default: on (Android 12+ only).

Because dynamic color is on by default, never rely on a specific hue for meaning. Always use theme
roles (`MaterialTheme.colorScheme.*`) in UI code; hard-coded colors are only allowed for the cases
below.

### Surface hierarchy

Screens use `background`. Content sits on tonal containers, never on shadows:

| Surface | Used for |
| --- | --- |
| `surfaceContainerLow` | Large secondary panels (key cards, port forward panel in the host editor). |
| `surfaceContainer` | List tiles (hosts, snippets), bottom navigation. |
| `surfaceContainerHigh` | Hero cards (quick connect, active session cards), search pill field. |
| `surfaceContainerHighest` | Input pills inside hero cards, detail blocks inside dialogs (fingerprints). |
| `primaryContainer` | Empty state icon tile. |
| `secondaryContainer` | Small chips and tags. |

### Accent palette

Eight user-selectable accents for tagging things (hosts in Terminal; documents, folders or tabs in
later apps). Same colors, same order, in every app (`HostColors` in `Theme.kt`):

| # | Name | Hex |
| --- | --- | --- |
| 0 | Indigo | `#5B6CF9` |
| 1 | Teal | `#00A58E` |
| 2 | Red | `#E5484D` |
| 3 | Amber | `#F5A524` |
| 4 | Violet | `#9B5DE5` |
| 5 | Cyan | `#00B4D8` |
| 6 | Pink | `#EF6FA5` |
| 7 | Slate | `#7C8B9C` |

They are stored as an index, not a hex value, so the palette can be tuned without migrating data.
They are shown as the text or icon color on a 16% alpha tint of themselves (see Avatar).

### Status colors

Fixed, not themed, because they carry meaning:

| State | Hex |
| --- | --- |
| Connected, success, running | `#2FBF71` |
| Connecting, pending, waiting | `#F5A524` |
| Failed (on a dark, non-Material surface) | `#FF6B6B` |
| Error in Material UI | `colorScheme.error` |

Always pair a status color with text or an icon; color alone is never the only signal.

### Content color schemes

Where an app shows user content in a styled canvas (terminal output, and later code in the
editor), it offers named color schemes independent of the app theme. The default is **Midnight**:

| Role | Hex |
| --- | --- |
| Background | `#0F1117` |
| Foreground | `#E6E6EF` |
| Cursor | `#7AA2F7` |
| Red / bright | `#F7768E` / `#FF7A93` |
| Green / bright | `#9ECE6A` / `#B9F27C` |
| Yellow / bright | `#E0AF68` / `#FFC777` |
| Blue / bright | `#7AA2F7` / `#82AAFF` |
| Magenta / bright | `#BB9AF7` / `#C099FF` |
| Cyan / bright | `#7DCFFF` / `#86E1FC` |
| Black / bright black | `#1A1D26` / `#444B6A` |
| White / bright white | `#C0CAF5` / `#FFFFFF` |

The full set (Midnight, Dracula, Catppuccin Mocha, Nord, Gruvbox Dark, One Dark, Solarized Dark,
Monokai, Classic Black, Solarized Light, Paper) lives in `emulator/ColorSchemes.kt`. Later apps
reuse the same ids and names so a user's favorite scheme means the same thing everywhere. When the
canvas is on screen, the system bars follow the canvas (`SystemBarAppearance(lightBars = !scheme.isDark)`),
not the app theme.

## 5. Typography

Two families, each with a clear job.

| Family | Source | Used for |
| --- | --- | --- |
| **Roboto** (system default) | Material 3 default `Typography()` | All interface text: titles, labels, body, buttons. |
| **JetBrains Mono** | Bundled in `res/font` (regular, bold, italic, bold italic), `MonoFamily` in `Theme.kt` | Anything the user would type or copy: hostnames, `user@host:port`, paths, commands, snippets, key fingerprints, file contents, the terminal itself, extra key labels. |

Rule of thumb: **if it is data, it is mono; if it is the app talking, it is Roboto.** In the hosts
list the name is Roboto, the `user@host` line under it is mono.

### Type roles

| Role | Where |
| --- | --- |
| Top app bar title, `FontWeight.SemiBold` | Screen titles on tabs ("Terminal", "Settings"). |
| `headlineSmall` | Lock screen title. |
| `titleLarge` | Empty state titles, error overlays. |
| `titleMedium` | List item headlines, avatar letters (Bold). |
| `bodyMedium` | Supporting text, dialog bodies, empty state messages (`onSurfaceVariant`). |
| `labelLarge` in `primary` | Section headers. |
| `labelMedium` in `outline` | Trailing counts in section headers. |
| `labelSmall` | Chips. |
| `MonoMedium` (14/20 sp) | Primary mono text: quick connect field, previews. |
| `MonoSmall` (12/16 sp) | Secondary mono text: subtitles, fingerprints, paths in lists. |

Do not introduce other font families, weights heavier than Bold, or letter-spacing tweaks. Do not
use all caps except on hardware key labels (ESC, TAB, CTRL).

## 6. Shape

Soft, generous corners. The same scale everywhere:

| Radius | Used for |
| --- | --- |
| 3 dp | Tiny swatches (color scheme preview dots). |
| 6 dp | Chips; inner corners of grouped tiles. |
| 10 dp | Extra keys, small buttons inside dark canvases. |
| 12 dp | Detail blocks, previews inside settings. |
| 16 dp | Medium cards, color scheme cards. |
| 20 dp | Outer corners of grouped tiles, standalone cards. |
| 28 dp | Hero cards, empty state icon tile, dialogs (Material default). |
| 32% of size | Avatars (a squircle, not a circle). |
| Full (pill) | Buttons, search field, quick connect field, FABs, segmented buttons. |

### Grouped tiles

Lists of related items are drawn as separate tiles with a 2 dp gap (1 dp vertical padding each), **20 dp outside corners
and 6 dp inside corners** so the group reads as one block (`groupedShape(index, count)`). A single
item gets 20 dp on all corners. Use this for every list of user objects (hosts, snippets, and later
documents and recent files).

## 7. Spacing and layout

- **4 dp grid.** Common steps: 4, 8, 12, 16, 20, 24, 32, 48.
- **Screen gutter: 20 dp** horizontally for section headers and text content; **16 dp** for tiles
  and cards, so their contents line up with the 20 dp text.
- **Vertical rhythm:** 2 dp between tiles in the same group, 12 dp between cards
  (`Arrangement.spacedBy(12.dp)` is the most common spacing in the codebase), 20 dp above a section
  header and 8 dp below it.
- **Edge to edge.** Status and navigation bars are transparent (`themes.xml`); content handles
  insets with `WindowInsets` and scaffold padding.
- **Bottom padding** under scrolling lists leaves room for the FAB (at least 88 dp).
- Minimum touch target 48 x 48 dp, even when the visual is smaller (status dots, chips).

### Screen structure

1. Top app bar: tab title in SemiBold on the left, at most two icon actions on the right, the rest
   in an overflow menu.
2. Optional hero card (quick connect in Terminal; "new file / open" in later apps).
3. Grouped sections with section headers.
4. One extended FAB for the primary create action ("New host", "Upload").
5. Bottom navigation bar with up to four tabs, the last one always **Settings**.

Detail and editor screens replace the bottom bar with a back arrow, a title (plus an optional mono
subtitle such as `user@host:port`) and a text **Save** action on the right.

## 8. Components

Use Material 3 components with the settings below. Shared composables live in
`ui/common/Components.kt`; copy that file into each new app (or a shared module, see
[section 15](#15-starting-a-new-app)).

| Component | Spec |
| --- | --- |
| **SectionHeader** | `labelLarge`, `primary`, padding 20/20/20/8 dp, optional trailing count in `labelMedium` `outline`. Sentence case ("Port forwarding"). |
| **Avatar** | 44 dp squircle (32% radius), background is the accent at 16% alpha, first letter in the accent color, `titleMedium` Bold, or an icon at 50% size. |
| **StatusDot** | 8 dp circle in a status color, placed before a title. |
| **Chip** | 6 dp corners, `secondaryContainer`, `labelSmall`, padding 6 x 2 dp. For tags like "Ed25519" or a group name. |
| **EmptyState** | 88 dp `primaryContainer` tile with 28 dp corners and a 40 dp icon, 20 dp gap, `titleLarge` title, 8 dp gap, `bodyMedium` message, optional button 24 dp below. Always tell the user what to do next. |
| **Hero card** | `surfaceContainerHigh`, 28 dp corners, a round icon badge, title plus one line of supporting text, a pill field inside on `surfaceContainerHighest`. |
| **Pill text field** | `TextField` with `CircleShape`, filled container, no indicator line (`pillFieldColors`). For search and quick input only. |
| **Form fields** | `OutlinedTextField` for all forms. Label in sentence case, supporting text under it for reassurance ("Stored encrypted with a hardware-backed key on this device"). Password fields get a show/hide eye. |
| **Segmented buttons** | `SingleChoiceSegmentedButtonRow` for 2 to 4 mutually exclusive options (Theme, Cursor, Authentication). |
| **Switch rows** | Title plus one line of supporting text, switch on the right. The whole row is clickable. |
| **Extended FAB** | Icon plus verb label ("New host", "Upload"). One per screen. |
| **Dialogs** | `AlertDialog`, optional top icon, title as a question for confirmations ("Delete web-01?"), body explaining the consequence, confirm button names the action ("Delete", "Trust and connect"), dismiss is "Cancel". Technical detail (fingerprints) goes in a `surfaceContainerHighest` block with mono text. |
| **Bottom sheets** | `ModalBottomSheet` for pickers and lists of actions tied to the current screen (snippets over the terminal). |
| **Snackbars** | Short, transient confirmations and non-blocking errors ("Wait for the current transfer to finish"). Offer an action only if it is useful ("Undo"). |
| **Menus** | `DropdownMenu` from a three dot overflow on tiles and top bars; icon plus label per item; destructive item last. |
| **Cards over dark canvases** | Keep the canvas color; use a 10 dp rounded key or button with a subtle lighter tint, labels in mono caps (extra keys bar: 46 dp tall, keys 36 dp tall, min 42 dp wide, 4 dp gaps). |

## 9. Iconography

- **Material Symbols, Rounded style** (`Icons.Rounded.*`) everywhere. Use `Icons.AutoMirrored.Rounded.*`
  for anything directional (back, forward, send) so RTL works.
- `Icons.Outlined.*` only when a filled rounded icon would read as "selected" by mistake.
- Sizes: 24 dp default, 20 dp in dense rows, 18 dp inline with text, 40 dp in empty states.
- Icons in lists take `onSurfaceVariant`; icons inside an accent avatar take the accent.
- The same concept always uses the same icon across apps: Settings is `Settings`, delete is
  `Delete`, copy is `ContentCopy`, keys are `Key`, share is `Share`, search is `Search`.

## 10. Motion and haptics

Motion is short and functional. Nothing bounces or loops.

| Transition | Spec |
| --- | --- |
| Push to a new screen | Slide in from the end plus fade in, `tween(280)`. The old screen fades out, `tween(200)`. |
| Pop back | New screen fades in `tween(200)`; old one slides to the end plus fades, `tween(280)`. |
| Overlays, banners, progress bars appearing | `AnimatedVisibility` with `fadeIn()` / `fadeOut()`. |
| Scrolling a list to an item | `animateScrollTo`. |

Haptics only confirm keyboard-like or physical-feeling actions: a `TextHandleMove` tick on extra
key presses and `LONG_PRESS` when a long press starts a selection. No haptics on ordinary buttons,
list taps or navigation.

## 11. Voice and copy

The apps sound like a calm, competent friend: plain words, short sentences, no hype.

### Rules

- **Sentence case** for everything: titles, buttons, menu items, section headers, settings
  ("Blinking cursor", not "Blinking Cursor").
- **No em dashes.** Use a comma, a colon, parentheses or a new sentence.
- **No exclamation marks**, no "Oops", no emoji in UI copy.
- **Contractions are fine** ("Couldn't connect", "You'll be asked...").
- **Use the real ellipsis character** `…` for in-progress states ("Connecting…").
- **Buttons are verbs** that name the result: "Trust and connect", "Delete", "Replace",
  "Keep going". Avoid "OK" and "Yes".
- **Confirmations ask a question** with the object's name in it ("Delete web-01?",
  "Replace notes.txt?", "Forget 10.0.0.4?") and the body says what will happen.
- **Errors say what failed and, if known, what to do**: "Couldn't connect to web-01", then the
  reason, then "Retry" and "Close".
- **Empty states** give a title that names the situation ("No hosts yet", "Empty folder",
  "No matches") and a message that points to the next action.
- **Reassure about privacy where the user makes a trust decision**: "Stored encrypted with a
  hardware-backed key on this device."
- **Numbers and time:** use `relativeTime()`: "Just now", "5 min ago", "3 h ago", "2 d ago", then a
  medium date. Mid-sentence it is lower case ("Added just now"). Use real plurals
  (`<plurals>` resources), never "item(s)".
- **Units** have a space: "13 sp", "22 KB".

### Words we use

| Use | Not |
| --- | --- |
| Host | Server, connection (for a saved entry) |
| Key | SSH key, identity |
| Snippet | Macro, shortcut |
| Session | Tab, connection (for a live connection) |
| Disconnect | Close, kill, terminate |
| Forget | Remove, clear (for trusted fingerprints) |
| Delete | Remove, erase (for user data) |
| Settings | Preferences, options |

New apps should add their own nouns to this table rather than reuse ambiguous ones.

## 12. Privacy and trust patterns

These are part of the brand, so every app implements them the same way:

- **Encrypted secrets.** Anything sensitive is encrypted with AES-256-GCM using a key in the
  Android Keystore (`security/SecretBox.kt`).
- **No cloud backup of secrets.** `res/xml/data_extraction_rules.xml` excludes the database and
  preferences that hold them.
- **App lock.** Optional, in Settings, using biometrics or the device screen lock. While it is on,
  the app is hidden in recent apps (`setRecentsScreenshotEnabled(false)` on Android 13+,
  `FLAG_SECURE` below) and the lock screen says "<App> is locked".
- **Export and import.** A plain, documented file format so users can leave or move devices.
- **Trust on first use.** When the app meets something it cannot verify (a host key, a file from
  outside), it shows the facts in mono and asks, with the safe option as the dismiss button.
- **About dialog** lists the version, one sentence describing the app and every bundled library
  with its license.

## 13. Accessibility

- Every icon-only button has a `contentDescription` that says the action ("Search",
  "Close search"); decorative icons pass `null`.
- Status is never color only: pair dots with text ("Connecting…", "Connected to web-01").
- Text uses `sp` and respects the system font scale. Content canvases have their own text size
  setting with a live preview and pinch to zoom.
- Test every screen in light, dark, dynamic color and at 200% font scale.
- Respect the system "Remove animations" setting (Compose does this for standard transitions).
- Hardware keyboard support is expected in apps where typing is the main job.

## 14. Repository, README and store listing

### README structure

1. `# <App>` and one sentence: "A modern, fast and easy to use <thing> for Android, built with
   Kotlin and Jetpack Compose."
2. Two rows of four screenshots at `width="200"`, from `docs/screenshots`.
3. **Features**, grouped under bold subheadings, as short bullet lists.
4. **Building**, **Tests**, **Architecture** (a package table), **Licenses**.

### Screenshots

Generated, not hand-taken: a Robolectric test (`AppScreenshotTest`) renders the main screens at
`w400dp-h860dp-xxhdpi` with seeded, realistic demo data and saves them to `app/build/screenshots`.
Files are numbered and named by screen (`02_hosts.png`, `05_settings.png`). Use plausible,
neutral demo data (`web-01`, `db-primary`, `Raspberry Pi`), never real hosts or names.

### Store listing

- Title: the app name only.
- Short description: the README's first sentence.
- Feature graphic: the icon background gradient full bleed, the app glyph on the left and the app
  name in white Roboto Medium on the right.
- The first line of the full description states the promise: "Free and open source. No ads, no
  tracking, no account."

## 15. Starting a new app

Checklist for app number two and beyond:

- [ ] Package `wales.tucker.<app>`, `minSdk 26`, latest `targetSdk`, Kotlin and Compose.
- [ ] Copy `ui/theme/Theme.kt` (rename `TerminalTheme` to `<App>Theme`, keep every color),
      `ui/common/Components.kt`, `res/values*/colors.xml` and `themes.xml`, and the JetBrains Mono
      fonts with their license in `assets/licenses`.
- [ ] Draw the icon: shared gradient background, translucent frame, periwinkle glyph, mint accent,
      monochrome and notification variants.
- [ ] Bottom navigation ending in Settings; Settings starts with Appearance (Theme, Dynamic color).
- [ ] App lock, encrypted secrets, backup exclusions, export and import if the app stores data.
- [ ] About dialog with licenses.
- [ ] Screenshot test and README in the structure above.
- [ ] Add the app's nouns to the word list in section 11.

Once there are two apps, move `Theme.kt`, `Components.kt`, the fonts and `SecretBox` into a shared
library (a Gradle module published from its own repository, or a git submodule) so the apps cannot
drift apart. This guide should move with it.

## 16. Applying this to the text editor

A sketch of how the guide maps onto the second app, to show the system working:

- **Name:** `Editor` (package `wales.tucker.editor`).
- **Icon:** the shared gradient; the frame becomes a portrait page (a rounded rectangle about
  40 x 52 dp with a folded corner); the periwinkle glyph is three stroked text lines of different
  lengths; the mint accent is a vertical text cursor at the end of the last line.
- **Tabs:** Files, Recent, Settings.
- **Hero card:** "New file" with a pill field for a file name, like quick connect.
- **Lists:** recent files as grouped tiles with an accent avatar (from the eight accent colors)
  and the path in `MonoSmall`.
- **Canvas:** JetBrains Mono, the same content color schemes (Midnight default), the same text size
  setting with a live preview and pinch to zoom, system bars following the canvas.
- **Extra keys row** above the keyboard reusing the terminal's styling (Tab, arrows, Home/End,
  brackets, undo, redo).
- **Words:** File, Folder, Save, Discard changes ("Discard changes to notes.txt?").
