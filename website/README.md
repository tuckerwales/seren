# Seren website

Marketing site for **Seren** (Welsh for “star”, pronounced *SEH-ren*), a suite of free, open source Android apps with no ads and no tracking.

Static HTML and CSS, with a little JS for the theme toggle, mobile menu, screenshot gallery and fade in. No build step, no cookies, no analytics. Served by nginx. Built for [Coolify](https://coolify.io/) and plain Docker.

## Design

Everything follows [`docs/BRAND.md`](../docs/BRAND.md):

- **Night sky.** Heroes and feature bands use the icon gradient (`#3A2F8F` > `#1E2A6B` > `#0E1434`) with a faint star field. The mint cursor from every app icon blinks after the home page headline.
- **Colors.** The suite's Material light and dark schemes as CSS custom properties. The site follows the system theme until the visitor picks one with the toggle (remembered in `localStorage`).
- **Type.** Roboto for the interface, JetBrains Mono for data (commands, stats, code).
- **Shape.** Tonal surfaces instead of shadows, 20 and 28 px corners, grouped tiles with 6 px inner corners, pill buttons.
- **Voice.** Sentence case, plain words, no em dashes, no exclamation marks.
- **Motion** respects `prefers-reduced-motion`.

## Pages

| Path | Page |
| --- | --- |
| `/` | Suite: promises, the apps, how they work together, source, questions |
| `/ssh/` | Seren SSH |
| `/edit/` | Seren Edit |
| `/auth/` | Seren Auth |
| `/files/` | Seren Files |

Each app page has a hero, an at a glance row, a screenshot gallery, grouped features, its permissions, how it works with the other apps, and links to the rest of the suite.

## Project layout

```
website/
├── Dockerfile           # nginx:alpine, serves public/ on port 80
├── docker-compose.yml   # local smoke test on :8080
├── nginx.conf           # gzip, asset cache, clean URLs
├── README.md
└── public/              # document root
    ├── index.html
    ├── 404.html
    ├── favicon.svg
    ├── css/style.css
    ├── js/main.js
    ├── images/icons/                  # app icons, redrawn from each app's launcher vectors
    ├── images/{ssh,edit,auth,files}/  # screenshots, 720 px wide WebP
    ├── ssh/index.html
    ├── edit/index.html
    ├── auth/index.html
    └── files/index.html
```

The header, footer and mobile menu are repeated in each page, so change them in all six HTML files.

## Local

### Python (no Docker)

```sh
cd website/public
python3 -m http.server 8080
# open http://127.0.0.1:8080/
```

Note: Python’s server does not rewrite `/ssh` to `/ssh/`; use trailing slashes.

### Docker Compose

```sh
cd website
docker compose up --build
# open http://127.0.0.1:8080/
```

### Docker only

```sh
docker build -t seren-website .
docker run --rm -p 8080:80 seren-website
```

## Coolify

1. Create a new resource, **Dockerfile**.
2. Point it at this directory (or its git repo). Build context: the folder that contains `Dockerfile`.
3. **Port:** `80` (container). Coolify will proxy HTTPS to it.
4. **Public directory:** not required for this Dockerfile, as files are baked into the image under `/usr/share/nginx/html`. If Coolify asks for a publish or static dir when using a static buildpack instead, use `public`.
5. Deploy. Confirm `/`, `/ssh/`, `/edit/`, `/auth/`, `/files/` return 200.

No environment variables are required. No database. No SPA fallback needed (multi-page).

## Content

App feature copy is condensed from each module’s README. Screenshots come from `<module>/docs/screenshots/` (resized to 720 px wide and saved as WebP). When the screenshots are regenerated, convert them again, for example:

```sh
cwebp -q 84 -resize 720 0 ssh/docs/screenshots/09_terminal.png -o website/public/images/ssh/09_terminal.webp
```

## Gaps

- **No Play Store links**: none exist yet. The site points to the APKs built on GitHub Actions and to building from source.
- **No GitHub Sponsors link**: omitted (the brand allows donation links only outside the apps; none found).
- **Domain**: not configured here; set your host in Coolify and DNS.

## License

Site content describes apps licensed under Apache 2.0. Screenshots are from the Seren monorepo.
