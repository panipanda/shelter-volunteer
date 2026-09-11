# Deployment

The MVP deploys to Fly.io as a Docker container. This document describes the one-time setup
and the ongoing deploy flow.

## How it works

- `Dockerfile` builds the app with `./gradlew installDist` and copies `data/` and `content/`
  into the image, so cat/visit JSON, the volunteer guide, and `data/images/` all ship inside
  the container.
- `fly.toml` configures the Fly.io app: internal port `8080` (matches `Application.kt`), HTTPS,
  and `auto_stop_machines`/`min_machines_running = 0` so the machine can scale to zero and stop
  costing anything while idle.
- `.github/workflows/fly-deploy.yml` deploys automatically on every push to `main`.

Because content is baked into the image, publishing a content change (new cat, updated visit,
new photo) means editing the file under `data/` or `content/`, committing it, and pushing to
`main` — the same flow as a code change. There is no separate content-update path in MVP.

## One-time setup

1. Create a free account at [fly.io](https://fly.io) if you don't have one.

2. Install `flyctl`:

   ```powershell
   iwr https://fly.io/install.ps1 -useb | iex
   ```

3. Log in (opens a browser):

   ```powershell
   fly auth login
   ```

4. From the project root, create the Fly app matching the committed `fly.toml`:

   ```powershell
   fly launch --no-deploy
   ```

   If the app name `shelter-volunteer` is already taken, `fly launch` will prompt for a
   different name — update the `app` field in `fly.toml` to match what it picks.

5. First deploy (builds remotely on Fly's infrastructure, no local Docker needed):

   ```powershell
   fly deploy
   ```

   The app will be live at `https://<app-name>.fly.dev`.

## Enable automatic deploys from GitHub

1. Create a deploy token scoped to this app:

   ```powershell
   fly tokens create deploy -x 999999h
   ```

2. In the GitHub repo, go to **Settings → Secrets and variables → Actions → New repository
   secret**, and add:

   - Name: `FLY_API_TOKEN`
   - Value: the token from step 1

3. Push to `main`. The `fly-deploy` workflow builds and deploys automatically; check progress
   under the repo's **Actions** tab.

## Useful commands

```powershell
fly status          # app/machine status
fly logs            # tail live logs
fly deploy          # manual deploy from local checkout
fly apps open        # open the live URL in a browser
```

## Custom domain (optional)

```powershell
fly certs add yourdomain.com
```

Then add the DNS records Fly prints (a CNAME or A/AAAA pair) at your domain registrar.

## Known limitation

Photos and JSON/Markdown data live inside the Docker image rather than on a persistent volume,
matching the MVP's file-based storage decision (see `docs/decisions.md`, "Media storage").
Every content update triggers a rebuild and redeploy. A Fly volume mounted at `data/images`
(and `data/`) would remove that coupling, but is deferred until Phase 2 (SQLite) or real
content-update frequency makes it worth the added operational complexity.
