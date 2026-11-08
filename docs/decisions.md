# Project Decisions

## Language

The site supports three locales via a `/{locale}` path prefix: `ru` (default), `en`, `sr`.

This supersedes the original plan (English-first MVP, Russian localization postponed to a later
phase): locale-prefixed routing and a `ru`-default content model were added early, and real `ru`
translations for JSON content (`data/cats.json`), UI strings
(`messages_ru.properties`), and the volunteer guide (`content/volunteer-guide.ru.md`) were filled
in soon after, since `ru` was the locale most volunteers would actually see by default.

`sr` now has real translations too, written in Latin script (ekavian) — the everyday script in
Serbia: `messages_sr.properties`, `sr` fields in the JSON content and
`content/volunteer-guide.sr.md`. A missing translation still falls back to `en` through the same
convention as `LocalizedText.forLocale()`, the JVM `ResourceBundle` lookup for
`messages.properties`, and `MarkdownGuideRepository`'s file-existence check. See `docs/smoke-checklist.md` for the current
per-locale coverage, and a RU/EN/SR switcher in the page nav lets visitors change locale without
editing the URL by hand.

## Visit archive

The project should keep historical visit data.

Upcoming visits and archived visits use the same base visit model.

Participant information must not be publicly displayed in MVP unless explicit consent exists.

Internal participant records may be stored separately from public visit data.

## Volunteer direction

The MVP supports cat volunteering only in the UI.

The architecture should allow future support for dog volunteering without major rewriting.

## Visit calendar

The MVP visit calendar is read-only.

Availability is updated manually by an admin or maintainer.

The website is not the source of truth for volunteer registration during MVP.

Final signup confirmation happens in existing volunteer chats or through a coordinator.

## Timezone

All volunteer visit times are stored and displayed in Europe/Belgrade timezone.

For MVP, visit date and time are stored separately as local date and local time, with explicit timezone field:

- date: YYYY-MM-DD
- time: HH:mm
- timezone: Europe/Belgrade

Technical timestamps such as lastUpdatedAt should include timezone offset.

## Registration

Online registration, cancellation flow, waitlist, notifications and Telegram integration are out of MVP scope.

These features may be added in later phases.

## Data storage

There are two storage modes, chosen at startup by the `DATABASE_URL` environment variable.

- **Files (the default, and what runs without `DATABASE_URL`):** cats and dogs in `data/cats.json` and
  `data/dogs.json`, photos in `data/images/`, the guide in `content/*.md`. Nothing to set up; this is also what
  every automated test of the public site runs on.
- **Database (when `DATABASE_URL` is set):** cats, dogs and their photos live in PostgreSQL (Neon free plan in
  production). This is what the admin panel needs: the free Render plan wipes the disk on every deploy, so files
  cannot be the source of truth for something people edit while the site is running. The first start on an empty
  database copies `data/` into it once (a mark in `app_meta` records that, so a removed last dog does not come back
  after a restart); from then on `data/cats.json` and `data/dogs.json` are no longer read.

SQLite, the earlier plan for this step, was dropped for the same reason: a SQLite file on Render's free disk
would be wiped too. A database outside the host, with a free plan, is the cheapest thing that survives a deploy.

Application code accesses data through repository interfaces (`CatRepository`, `DogRepository` and their mutable
versions), never directly through files or SQL, so routes and services are the same in both modes. The guide
(`content/*.md`) and the next-visit overrides (`data/visit-overrides.json`) are still files in both modes: the admin
panel does not edit them yet.

Not an accident, but worth knowing: in database mode a content mistake made in the admin must not take the site
down, so the startup validation of the data only logs a warning there (the strict check still runs on `data/` for
the first seeding).

## Database migration readiness

A cat or a dog is one row holding the same JSON document that the files hold (a `text` column, not `jsonb`, so
the same SQL runs on H2 in the tests). A new field in the model therefore needs no migration: documents saved
earlier simply lack it and get the default. `encodeDefaults` is on, so a default that changes in the code later
does not silently change a record saved before.

Participants should be stored separately from visits to make future migration to relational tables easier.

Routes and services must not depend on JSON-specific implementation details.

## Visit data

Visit data is not stored yet. Visits are organised in the cat volunteer chat, and sync with it is not
implemented, so the test data (`data/visits.json`, `data/visit-participants.json`) and the whole visit
data layer (`VolunteerVisit`, `VisitService`, `VisitRepository` and related code) were removed.
The "Upcoming visits" page shows the next trip and how to join it. The "Visit archive" page was removed (2026-11-03): an empty public stub works against trust; bring it back when there is something to show.
The data model will be designed again together with the sync.

The home page computes the next visit from the regular schedule (Wednesday 9:00 and Saturday 10:00,
departure from Vračar, Belgrade time): whichever departure is nearest is shown, with no information
about free places — only a pointer to the chat and the coordinators.

Participant names and contacts must not be rendered publicly in MVP unless explicit consent exists.

## Admin panel

The MVP shipped without one, and content changed through a commit and a deploy. That was painful for the people
who actually add cats and photos, so an admin panel was built after the site went live (2026-11). It exists
only in database mode (see "Data storage"): without `DATABASE_URL` there is no `/admin` at all.

What it does: edits cats and dogs with their photos. Every text (name, description, health, adoption instruction,
a dog's age) has its own `ru`, `en` and `sr` field, so one save updates the site in all three languages; `ru` is
required and the list shows which translations are missing (the site then falls back as it always did). Each
record is a draft or published; a new record starts as a draft, and a draft is visible only in the admin, where
a preview shows the public page in each language. Deleting asks first, and so does deleting a single photo.

Decisions behind it:

- **One shared password** (`ADMIN_PASSWORD`, at least 12 characters), no accounts. It is known to a few senior
  volunteers. The price is accepted on purpose: no record of who changed what, and two people editing the same
  record at once means the last save wins. When someone leaves, the password and `SESSION_SECRET` are changed in
  Render, which also ends every session.
- **The panel is in Russian only**; the site's languages are content, not the admin's interface.
- **A published record is edited in place.** A hidden second copy of "the draft of a published record" was judged
  too costly for the value; to rework a published record at leisure, set it back to a draft first.
- **Photos have a page of their own**, not a field in the form: every photo step reloads the page and would throw
  away text typed but not saved.
- **Nothing links to `/admin` from the public pages**; its pages are `noindex`, never cached and cannot be framed.
  Every change needs the form's own token. Wrong passwords go through one gate, a second each, with no lockout (a
  lockout would let anyone lock the volunteers out).
- **A backup is one button** (`/admin/export`): a zip with `cats.json`, `dogs.json` and the photos, in the layout
  of the file mode, so it can be unpacked into `data/`. Neon keeps its own history for a short time only; the
  volunteers are asked to keep a copy of their own.

Still files, and still changed through the repository: the volunteer guide, the next-visit overrides, the UI
strings. They can move into the admin later in the same pattern.

## Google Calendar

Google Calendar integration is not part of MVP.

It may be researched later for displaying or managing volunteer visits, but the MVP should not depend on it because the current real-world signup process happens through volunteer chats and coordinators.

## HTML rendering

All pages render through the Ktor HTML DSL (`kotlinx.html`), not hand-written HTML strings and not a
separate template engine.

Reasoning: the project briefly ran a mixed approach (homepage on the DSL, every other page as raw
multiline strings with a manual `escapeHtml()` call per dynamic field). A real instance of a
forgotten `escapeHtml()` call reached `main` before it was caught in review — the DSL closes that
whole bug class structurally, since `kotlinx.html` escapes text nodes and attribute values by
construction, not by each call site remembering to do it.

A separate template engine (Thymeleaf/FreeMarker/Mustache) was considered and rejected again: it
would add a dependency with no capability the DSL doesn't already provide for this project's scale,
and some engines (FreeMarker in particular) don't auto-escape by default, which is a real footgun
for whoever picks one expecting "the engine handles it for me."

`content/volunteer-guide.md` is read at request time through `GuideRepository` /
`MarkdownGuideRepository` (matching the "access data through repository interfaces" rule above,
extended to Markdown), parsed by a small hand-rolled subset parser (`parseGuideMarkdown` —
headings, paragraphs, `- ` bullet lists only) into `GuideBlock` values that `GuideViews.kt` renders
as real DSL tags. No markdown library was added: the content is small and fully author-controlled,
and the subset actually used is tiny enough that a full parser would be more dependency than value.

`MarkdownGuideRepository` looks for `content/volunteer-guide.{locale}.md` first and falls back to
the unsuffixed `content/volunteer-guide.md` (English) — the same fallback convention as
`messages.properties` and `LocalizedText`. `GuideViews.kt`'s `<html lang>` reflects the file that was
actually resolved (`ru`, `sr`, or `en` on fallback), not the route's locale.

## Client-side JavaScript

The MVP originally avoided JavaScript entirely as a blanket rule — the cat photo lightbox
(`CatViews.kt`) was built as a pure CSS `:target` mechanism specifically to prove pages didn't
need it, and code comments said so explicitly.

That blanket rule is dropped: plain HTML/CSS stays the default for anything it can do — it's
simpler to read and debug, and keeps working for visitors with JS disabled — but a small,
dependency-free script is fine where it adds real value a static page can't provide on its own.
First case: keyboard arrow-key navigation in the photo lightbox
(`static/scripts/cat-gallery.js`), added because `:target` reacts to clicks but not to keydown
events, and clicking was the only way to page through photos before this.

Scripts should stay small and framework-free (plain `document.addEventListener`, no bundler, no
new dependency) and act as progressive enhancement: the underlying CSS-only mechanism must keep
working with JS disabled, and a script should only add a convenience layer on top, never be the
only way a feature works.

## Media storage

**In database mode the photos of cats and dogs are rows in the same database** (`bytea`), served by the site at
`/images/...` with an ETag (the content hash) and a one-day cache. A separate image store (S3 and similar) was
considered and rejected: one more account, one more secret and one more thing to break, for a few dozen photos
that fit in the free 1 GB (the admin shows how much is used and warns above 80%).

The admin turns every upload into two JPEGs on the server, replacing what `tools/make-card-images.py` did by hand:
the picture itself (at most 1600 px on the long side, the camera's rotation applied, all metadata including the
shooting place removed) and the card copy (440x550, never scaled up). Only JPEG and PNG are accepted, checked by
their content; the limits (8 MB a file, 30 megapixels, 5 files an upload, 12 photos a record) are there because the
free plan has 512 MB of memory. Every photo gets a new random name (`/images/cat-12-3fa9c1d2.jpg`), so a browser
cache never shows an old picture in the place of a new one.

The pictures of the site's own pages (`home-*`) and the placeholders (`default-*`) are part of the code and are
not owned by the admin: they stay in `data/images/` and are refreshed in the database on every start.

**In file mode** (below) nothing changed.

Cat/visit photos are served from a real directory on disk (`data/images/`, via Ktor's
`staticFiles`), not from `src/main/resources/static/images` on the classpath (`staticResources`).

Reasoning: this project's whole "MVP data storage" decision above is built around content living
in plain files that get edited without touching code or triggering a rebuild — that's explicitly
why `data/cats.json` isn't compiled into the JAR. Serving images from classpath
resources broke that same promise for exactly one content type: adding or replacing a cat photo
would have meant committing a binary into `src/main/resources`, rebuilding, and redeploying — the
same heavyweight path as a code change, for what should be a content update. `data/images/` fixes
that, and is also the natural mount point for a Docker volume once deployment is set up, so photos
don't need to ship inside the image.

Not addressed by this change: `Cat.photoUrls` entries are still unvalidated free-form strings with no
constraint on scheme or path — nothing stops one from pointing outside `/images/` entirely. That's a
data-validation gap, separate from where the bytes live on disk, and still open.

