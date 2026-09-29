# Seren website

Marketing site for **Seren** (Welsh for “star”, pronounced *SEH-ren*) — a suite of free, open-source Android apps with no ads and no tracking.

Static HTML/CSS (tiny bit of JS for theme toggle and mobile nav). Served by nginx. Built for [Coolify](https://coolify.io/) and plain Docker.

## Pages

| Path | Page |
| --- | --- |
| `/` | Suite landing |
| `/ssh/` | Seren SSH |
| `/edit/` | Seren Edit |
| `/auth/` | Seren Auth |
| `/files/` | Seren Files |

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
    ├── images/{ssh,edit,auth,files}/
    ├── ssh/index.html
    ├── edit/index.html
    ├── auth/index.html
    └── files/index.html
```

## Local

### Python (no Docker)

```sh
cd /workspace/website/public
python3 -m http.server 8080
# open http://127.0.0.1:8080/
```

Note: Python’s server does not rewrite `/ssh` → `/ssh/`; use trailing slashes or open `ssh/`.

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

1. Create a new resource → **Dockerfile**.
2. Point it at this directory (or its git repo). Build context: the folder that contains `Dockerfile`.
3. **Port:** `80` (container). Coolify will proxy HTTPS to it.
4. **Public directory:** not required for this Dockerfile — files are already baked into the image under `/usr/share/nginx/html`. If Coolify asks for a publish/static dir when using a static buildpack instead, use `public`.
5. Deploy. Confirm `/`, `/ssh/`, `/edit/`, `/auth/`, `/files/` return 200.

No environment variables are required. No database. No SPA fallback needed (multi-page).

## Brand / content

Colors, voice and promises follow `seren/docs/BRAND.md`. App feature copy is taken from each module’s README. Screenshots are copies of the PNGs under `<module>/docs/screenshots/`.

## Gaps

- **No Play Store links** — none exist in the monorepo yet.
- **No GitHub Sponsors link** — omitted (brand allows donation only outside the apps; none found).
- **Domain** — not configured here; set your host in Coolify / DNS.
- **App icons** — site uses an SVG star favicon derived from brand colors; launcher adaptive icons stay in the Android modules.

## License

Site content describes apps licensed under Apache 2.0. Screenshots are from the Seren monorepo.

## Monorepo

Lives in `website/` inside [tuckerwales/seren](https://github.com/tuckerwales/seren). Coolify build context should be the `website` directory.
