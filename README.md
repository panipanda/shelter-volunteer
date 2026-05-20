# shelter-volunteer
MVP volunteer website for Pro Anima cat shelter.

## MVP scope

The MVP focuses on the cat volunteering direction.

Planned features:

- Cat catalogue
- Cat profile pages
- Volunteer guide
- Read-only volunteer visit calendar
- Manually updated visit availability
- English-first UI
- Russian localization later

## MVP architecture

The MVP uses file-based storage:

- `data/cats.json` for cat catalogue data
- `data/visits.json` for public volunteer visit data
- `data/visit-participants.json` for internal participant history, not rendered publicly in MVP
- `content/volunteer-guide.md` for volunteer instructions

The application code should access data through repository interfaces, not directly from routes.

Planned data access flow:
routes -> services -> repositories -> JSON/Markdown files

Future data access flow:
routes -> services -> repositories -> SQLite/PostgreSQL

## Visit calendar approach

The MVP does not manage volunteer registrations directly.

The current real-world signup process happens through volunteer chats and coordinators, so the website will work as a manually updated information board.

The visit calendar will show:

- visit date
- time
- free places
- visit status
- signup instructions
- last updated timestamp

Full online registration can be added later as a separate phase.

## Planned tech stack

- Kotlin
- Ktor
- Gradle
- JUnit 5
- HTML/CSS
- Docker/deploy later
