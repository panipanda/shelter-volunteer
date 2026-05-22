# Shelter Volunteer

MVP volunteer website for Pro Anima cat shelter.

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
- English-first UI
- Russian localization later

## Out of scope for MVP

The following features are intentionally postponed:

- Admin panel
- Online visit registration
- Visit cancellation flow
- User accounts
- Authentication and authorization
- Public participant lists
- Telegram integration
- Google Calendar integration
- Payment or donation integration
- Database-backed storage

## MVP architecture

The MVP uses file-based storage:

- `data/cats.json` for cat catalogue data
- `data/visits.json` for public volunteer visit data
- `data/visit-participants.json` for internal participant history, not rendered publicly in MVP
- `content/volunteer-guide.md` for volunteer instructions

Application code should access data through repository interfaces, not directly from routes.

Planned MVP data access flow:

```text
routes -> services -> repositories -> JSON/Markdown files
```

Future data access flow:

```text
routes -> services -> repositories -> SQLite/PostgreSQL
```

This keeps the web layer independent from the storage implementation.

## Data storage strategy

The MVP starts with JSON and Markdown storage to keep the first version simple, free, and deployable.

Planned storage evolution:

```text
JSON/Markdown -> SQLite -> PostgreSQL if needed
```

The repository layer should make this migration possible without rewriting routes and services.

### Public data

Public website data lives in:

```text
data/cats.json
data/visits.json
content/volunteer-guide.md
```

### Internal data

Participant history may be stored in:

```text
data/visit-participants.json
```

This file is internal operational data. It must not be rendered on public pages during MVP development unless explicit consent exists.

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

The file `data/visit-participants.json` may be used later for internal history, but it must not be rendered on public pages during MVP development.

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

Static website assets such as cat photos and default images are stored under:

```text
src/main/resources/static/
```

Cat photos should use public URLs like:

```text
/images/cats/zoka.jpg
```

The default cat image should be available at:

```text
/images/default-cat.jpg
```

JSON files should store public image URLs, not internal filesystem paths.

## Planned tech stack

- Kotlin
- Ktor
- Gradle
- Gradle Wrapper
- kotlinx.serialization
- JUnit 5 / Kotlin test
- HTML/CSS
- JSON/Markdown storage for MVP
- SQLite later
- Docker/deploy later

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
  visits.json
  visit-participants.json

content/
  volunteer-guide.md

docs/
  decisions.md
  mvp-scope.md
  roadmap.md
```

## Development setup

See [SETUP.md](SETUP.md) for local setup, Java, Gradle, wrapper, build, and troubleshooting instructions.

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