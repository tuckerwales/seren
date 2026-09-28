# Seren Files — as built

This note replaces the early Sep 2026 scope sketch (`seren-files-scope.md` on planning boxes).
Brand rules for the file manager live in [BRAND.md §18](BRAND.md#18-applying-this-to-the-file-manager).

## Tabs

| Tab | Role |
| --- | --- |
| **Browse** | Volumes, bookmarks, and the folder tree. Bookmarks are pinned from Browse (not a separate Favourites tab). |
| **Recent** | Files and folders opened recently. |
| **Trash** | 30-day restore with Undo. |
| **Settings** | Appearance, browsing toggles, bookmark export/import, storage access, app lock, About. |

## Implemented

- All-files access with a plain explanation; no network permission
- Copy / move with conflict choices; long jobs with notifications
- Archives: zip, 7z, tar.*
- Categories and photo thumbnails (grid)
- Share-in (“Save to Seren Files”) and SAF picker for other apps
- Suite handoffs: open in Edit, upload / import key with SSH, reveal downloads from SSH
- Multi-select where the UI needs it
- Bookmark export/import as plain JSON (paths are device-local)

## Explicitly out (brand)

- Dual-pane, cloud backends, in-app SFTP (Seren SSH’s job), Files-by-Google-style cleanup tone
