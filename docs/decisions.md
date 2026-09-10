# Project Decisions

## Language

MVP language: English.

Russian localization is postponed to a later phase.

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

The MVP stores public content in JSON and Markdown files.

This is a deliberate temporary choice to keep the first version simple, free, and deployable.

Application code must access data through repository interfaces, not directly through JSON or Markdown files. This should allow replacing JSON storage with SQLite later without rewriting routes and services.

Planned migration path:

- MVP: JSON and Markdown files
- Phase 2: SQLite
- Later, if needed: PostgreSQL

## Database migration readiness

JSON structures should stay close to future database tables.

Participants should be stored separately from visits to make future migration to relational tables easier.

Routes and services must not depend on JSON-specific implementation details.

## Visit data

Upcoming visits and archived visits use the same base model: `VolunteerVisit`.

The application should separate upcoming and archived visits by date and status, not by using separate data structures.

The public visit data is stored in `data/visits.json`.

Internal participant history may be stored separately in `data/visit-participants.json`.

Participant names and contacts must not be rendered publicly in MVP unless explicit consent exists.

## Admin panel

The MVP does not include an admin panel.

Data is updated manually through JSON and Markdown files.

A simple admin interface may be added later after the MVP is deployed and real content-management needs are clearer.

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

`content/volunteer-guide.md` is still not read by the application — `GuideViews.kt`'s guide page
content is hardcoded English text inside the DSL. Its `<html lang>` is intentionally left as `"en"`
regardless of the route's locale, since the content itself isn't actually translated yet; wiring
real per-locale guide files is a separate follow-up.

