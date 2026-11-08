# Shelter Volunteer

MVP volunteer website for Pro Anima cat shelter.

Live at [shelter-volunteer.onrender.com](https://shelter-volunteer.onrender.com).

This repository is a Kotlin/Ktor learning project and a future production-ready MVP website for shelter volunteers.

## MVP goal

The MVP focuses on the cat volunteering direction.

The website should help volunteers and coordinators access structured and up-to-date information about:

- cats available for adoption;
- cat profile pages;
- volunteer instructions;
- upcoming cat shelter visits;
- visit availability;
- completed visit archive.

The first public version is intentionally simple. It does not replace existing volunteer chats or coordinator decisions.

## MVP scope

Planned MVP features:

- Home page
- Cat catalogue
- Cat profile pages
- Volunteer guide
- Read-only volunteer visit calendar
- Visit archive with public summaries
- Manually updated visit availability
- Multi-locale UI: `ru` (default), `en` and `sr` (Latin script) all have real translated content

Added after the MVP went live: an admin panel for cats, dogs and their photos, backed by a database (see
"Admin panel" below).

## Out of scope for MVP

The following features are intentionally postponed:

- Online visit registration
- Visit cancellation flow
- User accounts (the admin panel has one shared password, not accounts)
- Public participant lists
- Telegram integration
- Google Calendar integration
- Payment or donation integration

## MVP architecture

The site has two storage modes, chosen at startup by the `DATABASE_URL` environment variable (see
[docs/decisions.md](docs/decisions.md) "Data storage").

**Without `DATABASE_URL` (the default, and what local development and the tests use)** everything is files:

- `data/cats.json` for cat catalogue data
- Volunteer visits are not stored yet: the "Visits" page shows the next trip and how to join it, and there is no visit archive page, until sync with the cat volunteer chat is implemented. The next visit is computed from the regular schedule (Wednesday 9:00, Saturday 10:00, Vračar)
- `data/visit-overrides.json` (optional) for changes to that schedule, read at startup: `{"cancelled": ["2026-10-24"], "extra": [{"date": "2026-10-25", "departure": "11:00"}]}`. A moved trip is a cancellation plus an extra trip. A malformed file stops the server on start
- `data/dogs.json` for dogs, `data/images/` for the photos of cats and dogs
- `content/volunteer-guide.md` for volunteer instructions (plus a `.{locale}.md` variant per translated locale)

**With `DATABASE_URL` set** the cats, the dogs and their photos live in PostgreSQL (Neon, free plan) and the admin
panel is switched on. The guide and `data/visit-overrides.json` stay files in both modes.

Application code accesses data through repository interfaces, not directly from routes, so the web layer does not
know which mode it runs in:

```text
routes -> services -> repositories -> JSON/Markdown files   (no DATABASE_URL)
routes -> services -> repositories -> PostgreSQL            (DATABASE_URL set)
```

## Data storage strategy

The site started with JSON and Markdown files to keep the first version simple, free, and deployable. Files stay
the default. The database came with the admin panel, because the free hosting plan wipes its disk on every deploy,
so content edited while the site runs needs a home outside the host. SQLite, the earlier plan, was dropped for
that reason: a SQLite file would be wiped too.

```text
JSON/Markdown files  ->  PostgreSQL (cats, dogs, photos; when DATABASE_URL is set)
```

### Public data

Public website data lives in (file mode; in database mode the cats, dogs and photos are in the database and these
files only seed an empty one):

```text
data/cats.json
data/dogs.json
data/images/
content/volunteer-guide.md
content/volunteer-guide.ru.md
```

## Admin panel

At `/admin`, only when `DATABASE_URL` is set. Russian interface, one shared password. Edits cats and dogs (texts in
`ru`, `en` and `sr`), their photos (upload, order, cover, delete), drafts and publishing, a preview of any record
as visitors see it, and a one-button backup (a zip of the texts and photos). Nothing on the public pages links
to it. How to switch it on and what each setting means: [docs/deploy.md](docs/deploy.md) "Admin panel". To look at
it locally with no setup: `./gradlew devAdmin` (see [SETUP.md](SETUP.md)).

### Internal data

There is no internal data yet. Participant history, once it exists, is internal operational data and must not be rendered on public pages during MVP development unless explicit consent exists.

## Visit calendar approach

The MVP does not manage volunteer registrations directly.

The current real-world signup process happens through volunteer chats and coordinators, so the website works as a manually updated information board.

The visit calendar should show:

- visit date
- visit time
- timezone
- free places
- visit status
- signup instructions
- last updated timestamp

Full online registration can be added later as a separate phase.

## Visit archive

The project should keep historical visit data.

Upcoming and archived visits use the same base visit model. The application separates upcoming and archived visits by date and status.

The public archive may show:

- visit date;
- visit status;
- public summary;
- aggregated information.

Participant names and contacts must not be rendered publicly in MVP unless explicit consent exists.

## Data privacy

Participant data is treated as internal operational data.

Participant history, if it is stored later, must not be rendered on public pages during MVP development.

Public pages should show only safe, aggregated, or consent-approved information.

## Timezone

All volunteer visit times are stored and displayed in:

```text
Europe/Belgrade
```

Visit date and time are stored as local values:

```text
date: YYYY-MM-DD
time: HH:mm
timezone: Europe/Belgrade
```

Technical timestamps such as `createdAt`, `updatedAt`, and `lastUpdatedAt` should include timezone offset.

Example:

```text
2026-05-20T20:30:00+02:00
```

## Static assets

Stylesheets, fonts and scripts are bundled under `src/main/resources/static/`. Pictures are served by the site at
`/images/...` (from `data/images/` in file mode, from the database in database mode), for example:

```text
/images/felix.jpg
/images/felix-card.jpg     the smaller copy that cards and thumbnails load
```

The default images for a cat or a dog without photos are available at:

```text
/images/default-cat.svg
/images/default-dog.svg
```

JSON files and database records store public image URLs, not internal filesystem paths.

## Planned tech stack

- Kotlin
- Ktor
- Gradle
- Gradle Wrapper
- kotlinx.serialization
- JUnit 5 / Kotlin test
- HTML/CSS
- JSON/Markdown files by default; PostgreSQL (Neon) for cats, dogs and photos when `DATABASE_URL` is set
- Docker, deployed to Render (see [docs/deploy.md](docs/deploy.md))

## Current source structure

Current Kotlin source structure:

```text
src/main/kotlin/org/proanima/shelter/
  model/
  repository/
  service/
```

Current expected responsibilities:

```text
model/       domain data classes and enums
repository/  data access interfaces and implementations
service/     business logic and display helpers
routes/      Ktor routes, added later
views/       HTML rendering, added later
```

Current project data/content/docs structure:

```text
data/
  cats.json

content/
  volunteer-guide.md      English, also the fallback for missing translations
  volunteer-guide.ru.md
  volunteer-guide.sr.md

src/main/resources/i18n/
  messages.properties     English, also the fallback for missing translations
  messages_en.properties  empty on purpose: pins "en" to the base bundle (see the file)
  messages_ru.properties
  messages_sr.properties

docs/
  decisions.md
  mvp-scope.md
  roadmap.md
  smoke-checklist.md
```

## Development setup

See [SETUP.md](SETUP.md) for local setup, Java, Gradle, wrapper, build, and troubleshooting instructions.

See [docs/deploy.md](docs/deploy.md) for Render deployment and automatic deploy from GitHub.

Basic build command on Windows:

```powershell
.\gradlew.bat build
```

Basic build command on Linux/macOS:

```bash
./gradlew build
```

Expected result:

```text
BUILD SUCCESSFUL
```