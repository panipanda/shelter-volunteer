# Deployment

The MVP deploys to [Render](https://render.com) as a Docker web service, using the free plan.

## How it works

- `Dockerfile` builds the app with `./gradlew test installDist` (a failing test fails the build, so
  the previous version stays live) and runs it as a non-root user. It copies `data/` and `content/`
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

3. Render builds the Docker image and deploys automatically. The app is live at
   [shelter-volunteer.onrender.com](https://shelter-volunteer.onrender.com).

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

Then set the environment variable `PUBLIC_BASE_URL` (for example `https://volunteers.example.org`, no trailing
slash) on the service. Pages put it in the canonical link, the language alternates and the link preview
(`og:url`, `og:image`); without it they point at `https://shelter-volunteer.onrender.com`.

## Database for cats, dogs and their photos (optional)

Without a database, the cats, the dogs and their photos are the files in `data/`, baked into the image
(see "Known limitation" below). Setting the environment variable `DATABASE_URL` moves them into a PostgreSQL
database; nothing else about the site changes, and every page and picture comes out the same
(`DatabaseModeTest` checks this against the file-based site).

**Setup.** Create a free project on [neon.tech](https://neon.tech), copy its connection string
(`postgresql://user:password@host/dbname?sslmode=require`) and add it in Render as the `DATABASE_URL`
environment variable of the service. Never commit it: it contains the password. Removing the variable
brings the file-based site back (the files in `data/` are untouched).

**First start.** The server creates the tables and, once, copies `data/cats.json`, `data/dogs.json` and the
photos from `data/images/` into the database. A mark in the `app_meta` table records that this happened, so
a later restart never copies again, even if every dog has been removed. From then on **the database is the
source of truth**: editing `data/cats.json` or adding a photo to `data/images/` no longer changes the site.

**Exception: pictures that belong to the site.** Files whose names start with `home-` or `default-` (the
photos on the home page and the guide, the placeholders for pets without a photo) are part of the code. On
every start the server compares them with the database and replaces the ones that differ, so a new hero
photo still goes live with a commit and a deploy.

**Failures.** The list of cats and dogs is cached in memory for 30 seconds. If the database cannot be reached
while an older copy exists, the older copy is served; with no copy, a visitor gets the 500 page. A free Neon
database sleeps after about five minutes without queries and wakes up in a second or two.

**Tests.** `./gradlew test` needs no database (it uses an in-memory H2). To also run the checks against a real
PostgreSQL, set `TEST_DATABASE_URL` to a connection string; the test works in a schema of its own and removes it.

## Admin panel (needs the database)

With `DATABASE_URL` set, the cats and dogs can be added, edited and removed at `/admin` instead of editing files
and redeploying. The panel is in Russian; every text of a cat or dog has its own Russian, English and Serbian field.
It is **off unless `ADMIN_PASSWORD` and `SESSION_SECRET` are set as well**, and then `/admin` is a plain 404.

**Setup.** Add two more environment variables in Render:

- `ADMIN_PASSWORD`: the one shared password, at least 12 characters (a few random words is fine and easy to type).
  Whoever knows it can change everything on the site's cats and dogs. See "Who knows the password" below for what to
  do when someone must be locked out.
- `SESSION_SECRET`: at least 32 random characters, used to sign the login cookie. Never typed by anyone. To make one
  in PowerShell: `[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))`.

If only one of the two is set, or one is too short, the start fails with a message (the previous version keeps
running), so a half-configured panel never goes live.

**What it does.** A cat's and a dog's form have the same fields: name, birth year and month, description, health, where it
lives, adoption stage and how to take it (a dog also has "Возраст словами", for a dog whose birth year nobody knows; the
birth year wins when both are given). The site shows each field only when it is filled in. Every cat and dog is a *draft* or *published*. Drafts are in no list, no page and not on the
home page; only the admin sees them, and "Как на сайте" shows a draft the way visitors will see it, in each
language. That preview is the public page under a banner ("Предпросмотр", with a link back to editing); its
language links stay inside the preview, because a draft has no public address. A new record starts as a draft. Editing a published record changes the site at once. Deleting asks
first and removes the record together with its photos (unless another cat or dog uses the same photo). The list
marks records that lack a translation (`нет en`, `нет sr`): the site then shows another language for them.

**Page texts and photos ("Страницы сайта").** The second item of the panel's menu edits the home page, the visits
page, the volunteer guide and the help page: their texts in Russian, English and Serbian, and the home and guide
photos. Whatever was not edited stays the text that ships with the code, and an emptied field (or one set back to the
shipped text) goes back to it; edited fields have a "Вернуть исходный текст" box. Edits are on the site at once, with
no draft step. The guide is one markdown text per language; the form explains the five marks it understands. The
Telegram names of the coordinators and the donation address are here too. Nothing needs setting up: the table is
created at start, and until something is edited the pages are what they were. The visits form says what it does not
change (the dates and times of trips, which are still `data/visit-overrides.json` and the schedule in the code).
- A page form is bigger than a cat's (three copies of the whole guide), so the body limit for it is 900 KB.
- A page photo is one resized JPEG (long side at most 1600 px) with the same checks as the cat photos; the new
  one is stored first, the page points at it, and the old uploaded one is deleted. The committed `home-*.jpg` stay.

**Security notes.**
- One session lasts 12 hours. The cookie is signed, `HttpOnly`, `SameSite=Lax`, `Secure` and limited to `/admin`.
  A server restart does not log anyone out (the cookie is self-contained).
- Every change needs the form's own token (CSRF) and a body below 200 KB.
- A wrong password costs a second, and all wrong attempts go through one gate, so guessing is limited to about one a
  second however many requests arrive; the right password never waits.
- No public page links to `/admin`; its pages say `noindex`, are never cached and cannot be framed.
- Uploaded files are checked by their content, not by their name: only JPEG and PNG, up to 8 MB and 30 megapixels each,
  at least 300 px on the short side, at most 5 files per upload and 12 photos per cat or dog. Everything else (HEIC,
  WebP, GIF, a renamed text file) is refused with a message that names the file.

**Photos.** Each cat and dog has a page of its own for photos (`Фотографии` on the record's form; it is a separate page
because every step there reloads it, and unsaved text in the form would be lost). A photo can be added (several at
once), moved earlier or later, made the cover (the first photo is the card in the list and the first picture on the
page) and deleted (after a question that shows the photo and warns if it is the cover or the only one). Behind the scenes the server turns every upload into two JPEGs, the same way
`tools/make-card-images.py` did by hand: the picture itself, at most 1600 px on the long side and the right way up
(the camera's rotation tag is applied), and the card copy that covers 440×550 (never scaled up). Nothing from the
original file's metadata is kept, the shooting place included. Names look like `/images/cat-12-3fa9c1d2.jpg`: the random
part makes every photo a new address, so a browser never shows an old picture in the place of a new one. The page shows
how much of the database's 1 GB the photos take.
- One picture is processed at a time and large pictures are read in a reduced size, so memory stays within the free plan
  (checked with the same JVM limits as `render.yaml`: a 24-megapixel photo takes about a second here). The free plan has
  a tenth of a CPU, so expect it to be several times slower on Render; upload three or four photos at a time at first.

**Backup.** The button "Скачать копию (zip)" at the bottom of the list page downloads one archive: `cats.json` and
`dogs.json` (all three languages, drafts included, marked `"published": false`), `site-content.json` (the edited texts
of the pages, only what differs from the code), `images/` (every photo, original and card copy, and the page photos
that were uploaded, `page-...`) and a `README.txt` that says what is inside, how many of each, and names any photo
that a record or a page points at but the database lacks. The layout is the one the site uses without a database, so
the archive can be unpacked into `data/` (and `data/images/`) of the repository, or the same files can seed a new
empty database (the page texts are read from `data/site-content.json` in both modes). The site's own pictures
(`home-*`, `default-*`) are not in it: they are in the repository. Neon keeps a history of its own for a
short time on the free plan, but a copy kept by the people does not depend on the account, so ask the volunteers to
download one about once a month and after big changes (new cats, many photos).
- It is a POST with the form's token, not a plain link, so a page on another site cannot start it. One backup runs at
  a time; a second press meanwhile gets a "Копия уже собирается" page.
- The archive is streamed photo by photo (about the size of one photo in memory, whatever the total), and the JPEGs
  are stored as they are rather than compressed again, because the free plan has a tenth of a CPU. A zip's
  index is written last, so an archive cut short by a broken connection is reported as damaged by zip programs
  instead of quietly opening with photos missing. If that happens, download it again.
- Each backup reads every photo from Neon, so it counts against the free plan's 5 GB of transfer a month: with the
  photos near the 1 GB limit that is a few backups a month, with a few hundred MB of photos it is many more.
  The list page shows how much the photos take.

**Who knows the password.** The password is shared by a few senior volunteers, and the panel has no accounts. This
is a decision, not an oversight (see `docs/decisions.md`), with consequences to keep in mind:
- Nothing records who changed what. If something is lost, the backup is the way back, not a history.
- Two people editing the same record at once: the last save wins and the first person's changes are gone. Agree who
  edits what, or look at the list for a change before starting.
- When someone should lose access, change **both** variables in Render: a new `ADMIN_PASSWORD` stops new logins, and
  a new `SESSION_SECRET` ends the sessions that are open (the password alone does not: a session lasts 12 hours and a
  restart does not end it). Then tell the others the new password in person, not in a group chat.

**Trying it locally.** `./gradlew devAdmin` starts the site with the panel on a throwaway in-memory database
(it starts with the content of `data/`) at <http://localhost:8095>, password `dev-password-123`. Nothing real is
connected. Set `ADMIN_COOKIE_SECURE=false` when running over plain http with your own variables.

## Known limitation

Without `DATABASE_URL`, photos and JSON/Markdown data live inside the Docker image rather than on a persistent
volume or database, matching the MVP's file-based storage decision (see `docs/decisions.md`, "Media
storage"). Every content update triggers a rebuild and redeploy. The database mode above is the way out of
this; the admin panel (above) edits its content.
