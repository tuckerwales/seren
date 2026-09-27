# Seren brand and design guide

**Seren** (Welsh for "star", said *SEH-ren*) is a suite of free, open source Android apps with no
ads and no tracking. This guide describes how the apps look, feel and speak. Seren SSH was the
first app, Seren Edit the second, Seren Auth the third and Seren Files the fourth; what they share lives in **Seren Core** (the `core` module),
so when in doubt, open
`core/src/main/java/wales/tucker/seren/core/ui/theme/Theme.kt` and
`core/src/main/java/wales/tucker/seren/core/ui/Components.kt`.

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
17. [Applying this to the authenticator](#17-applying-this-to-the-authenticator)
18. [Applying this to the file manager](#18-applying-this-to-the-file-manager)
19. [Working together](#19-working-together)

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

### The suite

The suite is called **Seren**, Welsh for "star". A star is a small, steady light that anyone can
use to find their way and nobody owns, which is what these apps try to be. It is also a nod to
where they are made (`tucker.wales`).

- Always written **Seren**, capital S, never all caps or all lower case in prose.
- Pronunciation, when it comes up: *SEH-ren*, with a short "e" as in "set".
- The Play Store developer name is **Seren**.
- The one-line story, used in READMEs, store listings and About dialogs: *"Seren is Welsh for star.
  Seren apps are free, open source, and have no ads and no tracking."*

### App names

- **Seren + one word**, where the word is the shortest thing someone would type into a search to
  find the app: **Seren SSH**, **Seren Edit**, **Seren Auth**, **Seren Files**, then for example Seren Notes.
- The word is always capitalised (or an acronym in caps), never a pun, never a version number.
- The **launcher label is the full name** ("Seren SSH"). Keep it to 12 characters or fewer so it
  never truncates under the icon.
- In prose the app name is a proper noun without "the": "Seren SSH keeps sessions alive", not
  "the Seren SSH app keeps...". Always use the full name, never just the app word ("Seren SSH
  is locked", not "SSH is locked").
- Never shorten an app to just "Seren"; that means the suite.

| App | Launcher label | Package id | Module |
| --- | --- | --- | --- |
| Seren SSH | Seren SSH | `wales.tucker.seren.ssh` | `ssh` |
| Seren Edit | Seren Edit | `wales.tucker.seren.edit` | `edit` |
| Seren Auth | Seren Auth | `wales.tucker.seren.auth` | `auth` |
| Seren Files | Seren Files | `wales.tucker.seren.files` | `files` |
| (shared library) | | `wales.tucker.seren.core` | `core` |

- **Package ids** (application id, namespace and Kotlin package) are `wales.tucker.seren.<word>`
  in lower case. Once an app is published its id never changes: a new id is a different app, and
  existing installs stop getting updates.
- All the apps live in one repository, `tuckerwales/seren`, each in a Gradle module named after its
  word (`ssh`, `edit`, `auth`, `files`), next to the shared `core` module.
- The Application class is `SerenApp`, the Compose theme function `SerenTheme` and the XML theme
  `Theme.Seren`.

### Wordmark

- **Seren** in Roboto Medium, followed by the app word in Roboto Regular: **Seren** SSH.
- On the icon gradient: "Seren" in white, the app word in periwinkle `#8FA8FF`.
- On light surfaces: "Seren" in `onSurface`, the app word in `primary`.
- Letter spacing default, no logotype tricks, no star glyph replacing a letter. The star lives in
  the name; the icons carry the light.

## 3. App icons

Every app uses an **adaptive icon** with three layers, all as vector drawables
(`res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`,
`ic_launcher_monochrome.xml`, wired up in `res/mipmap-anydpi/ic_launcher.xml`).

### Anatomy (108 x 108 dp viewport)

| Layer | Seren SSH | Rule for every app |
| --- | --- | --- |
| Background | Diagonal linear gradient, top left to bottom right: `#3A2F8F` at 0, `#1E2A6B` at 0.55, `#0E1434` at 1 | **Identical in every app.** This deep indigo gradient, a night sky, is the suite's signature. |
| Frame | Rounded rectangle from (24,34) to (84,74), 6 dp corners, fill `#1AFFFFFF`, 1.5 dp stroke `#66FFFFFF` | A translucent white "object" that represents the app (a window, a page, a folder). Keep it inside the 66 dp safe zone (x and y from 21 to 87). |
| Primary glyph | Prompt chevron, 4.5 dp round stroke, `#8FA8FF` | One simple stroked mark in **periwinkle `#8FA8FF`**, 4.5 dp, round caps and joins. |
| Accent | Cursor bar, 15 x 4.5 dp pill, `#7DF0C8` | One small filled mark in **mint `#7DF0C8`**. The mint cursor is the suite's recurring detail (the star in the night sky): use it in every icon. |
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
(below Android 12). They are defined once, in `SerenTheme` in Seren Core.

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

Eight user-selectable accents for tagging things (hosts in Seren SSH; documents, folders or tabs in
later apps). Same colors, same order, in every app (`AccentColors` in Seren Core's `Theme.kt`):

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
Monokai, Classic Black, Solarized Light, Paper) lives in `ContentColorSchemes` in Seren Core. Every app
reuse the same ids and names so a user's favorite scheme means the same thing everywhere. When the
canvas is on screen, the system bars follow the canvas (`SystemBarAppearance(lightBars = !scheme.isDark)`),
not the app theme.

## 5. Typography

Two families, each with a clear job.

| Family | Source | Used for |
| --- | --- | --- |
| **Roboto** (system default) | Material 3 default `Typography()` | All interface text: titles, labels, body, buttons. |
| **JetBrains Mono** | Bundled in `res/font` (regular, bold, italic, bold italic), in Seren Core, `MonoFamily` in `Theme.kt` | Anything the user would type or copy: hostnames, `user@host:port`, paths, commands, snippets, key fingerprints, file contents, the terminal itself, extra key labels. |

Rule of thumb: **if it is data, it is mono; if it is the app talking, it is Roboto.** In the hosts
list the name is Roboto, the `user@host` line under it is mono.

### Type roles

| Role | Where |
| --- | --- |
| Top app bar title, `FontWeight.SemiBold` | Screen titles on tabs ("Seren SSH" on the home tab, "Settings"). |
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
2. Optional hero card (quick connect in Seren SSH; "new file / open" in later apps).
3. Grouped sections with section headers.
4. One extended FAB for the primary create action ("New host", "Upload").
5. Bottom navigation bar with up to four tabs, the last one always **Settings**.

Detail and editor screens replace the bottom bar with a back arrow, a title (plus an optional mono
subtitle such as `user@host:port`) and a text **Save** action on the right.

## 8. Components

Use Material 3 components with the settings below. Shared composables live in
Seren Core (`core/.../ui/Components.kt` and its neighbours); use them rather than restyling
Material components in an app.

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
| **Snackbars** | Short, transient confirmations and non-blocking errors ("Wait for the current transfer to finish"). Offer an action only if it is useful ("Undo"). One per app, through `Messenger` in Seren Core, so a message survives navigating back. |
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
| File | Document (for something on storage) |
| Folder | Directory |
| Discard changes | Revert, lose edits |
| Forget (a folder) | Remove, revoke access |
| Remove from recent | Delete (the file stays where it is) |
| Account | Token, entry (for something that makes codes) |
| Service | Issuer (the site or app an account is for) |
| Code | OTP, token, PIN (the one-time number) |
| Setup key | Secret, seed (the key a site gives to set up codes) |
| Backup | Vault, archive (an exported file of accounts) |
| Storage | Drive, disk, SD (internal storage, an SD card or a USB drive) |
| Trash | Bin, recycle bin (where deleted items wait for 30 days) |
| Move to trash | Delete (when the item can still be restored) |
| Delete permanently | Erase, destroy (when it can't) |
| Restore | Undelete, recover (putting an item back from the trash) |
| Bookmark | Favorite, pin, shortcut (a folder kept on the Browse tab) |
| Compress, Extract | Zip, unzip, archive (the actions) |
| Paste | Drop (finishing a copy or move) |

New apps should add their own nouns to this table rather than reuse ambiguous ones.

## 12. Privacy and trust patterns

These are part of the brand, so every app implements them the same way:

- **Encrypted secrets.** Anything sensitive is encrypted with AES-256-GCM using a key in the
  Android Keystore (`SecretBox` in Seren Core; each app names its own key alias).
- **No cloud backup of secrets.** `res/xml/data_extraction_rules.xml` excludes the database and
  preferences that hold them.
- **App lock.** Optional, in Settings, using biometrics or the device screen lock. While it is on,
  the app is hidden in recent apps (`setRecentsScreenshotEnabled(false)` on Android 13+,
  `FLAG_SECURE` below) and the lock screen says "Seren SSH is locked" (the full app name).
- **Export and import.** A plain, documented file format so users can leave or move devices.
  Password protected backups all use the same lock (`PasswordSeal` in Seren Core: scrypt and
  AES-256-GCM), and the password dialogs come from Seren Core too (`NewBackupPasswordDialog`,
  `UnlockBackupDialog`).
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

1. `# Seren <Word>` and one sentence: "A modern, fast and easy to use <thing> for Android, built
   with Kotlin and Jetpack Compose." Then the suite story line from [section 2](#2-naming).
2. Two rows of four screenshots at `width="200"`, from the module's `docs/screenshots`.
3. **Features**, grouped under bold subheadings, as short bullet lists.
4. **Building**, **Tests**, **Architecture** (a package table), **Licenses**.

### Screenshots

Generated, not hand-taken: a Robolectric test (`AppScreenshotTest`) renders the main screens at
`w400dp-h860dp-xxhdpi` with seeded, realistic demo data and saves them to `<module>/build/screenshots`.
Files are numbered and named by screen (`02_hosts.png`, `05_settings.png`). Use plausible,
neutral demo data (`web-01`, `db-primary`, `Raspberry Pi`), never real hosts or names.

### Store listing

- Title: the full app name, optionally followed by what it does, within the store limit
  ("Seren SSH: terminal and SFTP").
- Developer name: Seren.
- Short description: the README's first sentence.
- Feature graphic: the icon background gradient full bleed, the app glyph on the left and the
  wordmark on the right.
- The first line of the full description states the promise: "Free and open source. No ads, no
  tracking, no account."

## 15. Starting a new app

Checklist for app number two and beyond:

- [ ] Package `wales.tucker.seren.<word>`, `minSdk 26`, latest `targetSdk`, Kotlin and Compose.
- [ ] Add a module named after the app word, include it in `settings.gradle.kts`, and depend on
      Seren Core with `implementation(project(":core"))`. That brings `SerenTheme`, `Theme.Seren`,
      the window colors, the fonts and their license, the shared components, `SecretBox`,
      `AppLock` and the backup exclusion rules; don't copy any of them.
- [ ] Draw the icon: the shared gradient background (`@drawable/ic_launcher_background` from Seren
      Core), translucent frame, periwinkle glyph, mint accent, monochrome and notification variants.
- [ ] Bottom navigation ending in Settings; Settings starts with `AppearanceSection`.
- [ ] App lock (`AppLock`, `LockScreen`), encrypted secrets, export and import if the app stores data.
- [ ] `AboutDialog` with the app's libraries plus `CORE_LICENSES`.
- [ ] Screenshot test and README in the structure above, and a line in the root README.
- [ ] Add the app's nouns to the word list in section 11.
- [ ] Add the app to `SuiteApp` in Seren Core, and offer what it does to the other apps (see
      section 19).

Anything a second app needs that one app already has (a component, a pattern, a helper) moves into
Seren Core rather than being copied, so the apps cannot drift apart. This guide lives at the root of
the repository, next to the modules it describes.

## 16. Applying this to the text editor

How the guide maps onto the second app, Seren Edit (the `edit` module), to show the system working:

- **Name:** Seren Edit (package `wales.tucker.seren.edit`).
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
- **Trust:** Seren Edit asks for no storage or network permission. It reaches only the files and
  folders the user picks, and "Forget" hands a folder's access back.

## 17. Applying this to the authenticator

The third app, Seren Auth (the `auth` module):

- **Name:** Seren Auth (package `wales.tucker.seren.auth`).
- **Icon:** the shared gradient; the frame becomes a shield; the periwinkle glyph is a countdown
  dial three quarters round; the mint accent is the cursor, upright in the middle of the dial.
- **Tabs:** Accounts, Settings.
- **Lists:** accounts as standalone cards (20 dp corners, 12 dp apart) rather than grouped tiles,
  because each is tall and led by a large code that needs space around it; each has an accent avatar (the same service always gets the same
  accent until the user picks another), the account name in `MonoSmall` and the code in large mono
  digits in `primary`, grouped in two halves ("123 456") so it can be read out. A countdown ring with
  the seconds left sits on the right; in the last five seconds the code and ring turn to
  `colorScheme.error`, and the seconds are always shown so color is never the only signal.
- **Primary action:** the extended FAB "Add account" opens a bottom sheet: scan a QR code, scan one
  from an image, enter a setup key, or import a file.
- **Canvas:** none. Codes are data, so they are mono, but they sit on ordinary Material surfaces.
- **Words:** Account, Service, Code, Setup key, Backup (see section 11). "Delete GitHub?" says to
  turn off two-factor authentication there first.
- **Trust:** Seren Auth has no network permission at all. Setup keys are encrypted with
  `SecretBox`; screenshots are blocked by default; with app lock on, showing a setup key or its QR
  code and exporting ask for the user's identity again. Exported plain text files get a warning,
  and encrypted backups use a documented format (scrypt and AES-256-GCM) so nobody is locked in.

## 18. Applying this to the file manager

The fourth app, Seren Files (the `files` module):

- **Name:** Seren Files (package `wales.tucker.seren.files`).
- **Icon:** the shared gradient; the frame becomes a folder with a tab; the periwinkle glyph is a
  small file tree (a folder line with two branches); the mint accent is the cursor, standing in as
  the second item in the folder.
- **Tabs:** Browse, Recent, Trash, Settings.
- **Hero card:** each storage volume is a hero card with a round icon badge, "38.4 GB free of
  128.0 GB" and a rounded bar of how full it is.
- **Lists:** files and folders as grouped tiles in two groups, Folders then Files. Each kind of file
  keeps one accent from the palette (folders indigo, images teal, video and PDF red, documents
  amber, archives violet, text and apps cyan, audio pink, anything else slate), and photos and
  videos show a thumbnail in the same squircle. The meta line is "2.4 MB · 3 d ago" or "12 items ·
  5 min ago"; paths (search results, recent files, the trash) are the mono subtitle.
- **Picking:** long press picks an item; picked tiles turn `secondaryContainer` with a tick in place
  of the avatar. The top bar says "3 selected" and the bottom bar offers Copy, Move, Share, Delete
  and More. `GroupedTile` in Seren Core takes `onLongClick` and `selected` for this.
- **Paths:** breadcrumbs under the top bar, one outlined chip per folder in mono, starting with the
  volume name.
- **Long jobs:** copies, moves, compressing and extracting show a `secondaryContainer` card with the
  name, "12.4 MB of 48.0 MB" and Cancel; they keep going while people move around the app. Files
  waiting to be pasted show a pill shaped bar with Paste.
- **Words:** Storage, Trash, Move to trash, Delete permanently, Restore, Bookmark, Compress, Extract,
  Paste (see section 11). Clashing names ask "Replace notes.txt?" with Replace, Keep both and Skip.
- **Trust:** Seren Files has no network permission at all. The one permission people see, all files
  access, is asked for only from an explained empty state ("Allow access to your files"). Deleting goes to the
  trash with Undo by default, and turning the trash off makes every delete say it's permanent.

## 19. Working together

Each app stands on its own, and each gets better when the others are installed. The code for this
is `core.suite` in Seren Core.

**What people see**

- An app offers another Seren app by its full name, only when it is installed, checked again
  each time the screen comes back: "Open in Seren Edit", "Upload with Seren SSH", "Import into
  Seren SSH", "Show in Seren Files". Nothing is ever offered to install, so nothing nags.
- The action sits next to the ordinary Android one ("Open with", "Share") rather than replacing
  it, and the receiving app still asks before it does anything: Seren SSH asks which server and
  folder before uploading, and Import key waits for the Import button.
- When a handoff fails, the message names the app: "Seren SSH couldn't open notes.txt".

**How it works**

| From | To | What | How |
| --- | --- | --- | --- |
| Seren Files | Seren Edit | Open a text file, saving back to it | `ACTION_VIEW` with read and write grants |
| Seren Files, any app | Seren SSH | Upload files over SFTP | `ACTION_SEND` / `ACTION_SEND_MULTIPLE` |
| Seren Files | Seren SSH | Import a private key | `wales.tucker.seren.action.IMPORT_KEY` |
| Seren SSH | Seren Files | Show a download in its folder | `wales.tucker.seren.action.REVEAL` |

- **Ordinary intents first.** Whatever a standard action can do (view, edit, send) uses one, so
  the apps also work with apps outside the suite. Only handoffs no standard action covers get a
  Seren action, and the activities that take them require `wales.tucker.seren.permission.SUITE`,
  a signature permission every app declares: only apps signed with the suite's key can use them.
  Every app must therefore be signed with the same release key.
- **Links, never paths or secrets.** Files travel as content links with one-off grants, and only
  the access needed (write only for editing). Apps refuse `file:` links from other apps, so none
  can point them at their own private files. Passwords, private keys and setup keys never pass from
  one app to another; each app keeps its own lock and its own data.
- **Package visibility.** Seren Core's manifest lists the four packages under `<queries>`, which
  Android 11 and newer need before an app can see whether another is installed.

