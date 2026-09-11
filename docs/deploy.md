# Deployment

The MVP deploys to [Render](https://render.com) as a Docker web service, using the free plan.

## How it works

- `Dockerfile` builds the app with `./gradlew installDist` and copies `data/` and `content/`
  into the image, so cat/visit JSON, the volunteer guide, and `data/images/` all ship inside
  the container. There is no runtime write to these files, so the free plan's ephemeral
  filesystem is not a problem.
- `Application.kt` binds to `0.0.0.0` and reads the port from the `PORT` environment variable
  (falling back to `8080` if unset), matching Render's requirement that a Docker web service
  bind to the port Render assigns rather than a hardcoded one.
- `render.yaml` is a [Blueprint](https://render.com/docs/blueprint-spec) that defines the
  service: Docker runtime, free plan, `/health` as the health check path, and a `JAVA_OPTS`
  env var that caps heap/stack/metaspace size so the JVM stays within the free plan's 512MB
  RAM limit.

Because content is baked into the image, publishing a content change (new cat, updated visit,
new photo) means editing the file under `data/` or `content/`, committing it, and pushing to
`main` — the same flow as a code change. There is no separate content-update path in MVP.

## Free plan limits to know about

- **512MB RAM / 0.1 CPU** per instance.
- The service **spins down after 15 minutes without traffic** and takes about a minute to wake
  up on the next request — acceptable for a low-traffic volunteer site, but expect a slow first
  load after idle periods.
- **5GB/month outbound bandwidth.**
- 750 free instance-hours/month per workspace — enough to keep one service running continuously.

## One-time setup

1. Create a free account at [render.com](https://render.com) (GitHub sign-in is the simplest
   path since the repo needs to be connected anyway).

2. In the Render dashboard: **New → Blueprint**, then select the `shelter-volunteer` GitHub
   repo. Render reads `render.yaml` from the repo root and proposes the `shelter-volunteer`
   web service on the free plan — confirm and apply.

3. Render builds the Docker image and deploys automatically. Once done, the app is live at
   `https://shelter-volunteer.onrender.com` (or whatever subdomain Render assigns if that one
   is taken).

If Render ever asks for a card during signup, that's identity verification, not a charge — the
free plan stays $0 as long as usage stays within the limits above.

## Automatic deploys

Render's GitHub integration deploys on every push to the connected branch by default — no
GitHub Actions workflow needed (unlike the earlier Fly.io setup). Check deploy status and logs
from the service's page in the Render dashboard.

## Useful commands

```powershell
# Render is managed entirely through its dashboard/API; there's no required CLI.
# For local parity checks:
docker build -t shelter-volunteer .
docker run -p 8080:8080 -e PORT=8080 shelter-volunteer
```

## Custom domain (optional)

In the service's **Settings → Custom Domains**, add the domain and create the CNAME record
Render shows at your registrar.

## Known limitation

Photos and JSON/Markdown data live inside the Docker image rather than on a persistent volume
or database, matching the MVP's file-based storage decision (see `docs/decisions.md`, "Media
storage"). Every content update triggers a rebuild and redeploy. This is deferred until Phase 2
(SQLite) or real content-update frequency makes it worth the added complexity.
