# Project Decisions

## Language

The site supports three locales via a `/{locale}` path prefix: `ru` (default), `en`, `sr`.

This supersedes the original plan (English-first MVP, Russian localization postponed to a later
phase): locale-prefixed routing and a `ru`-default content model were added early, and real `ru`
translations for JSON content (`data/cats.json`, `data/visits.json`), UI strings
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

Cat/visit photos are served from a real directory on disk (`data/images/`, via Ktor's
`staticFiles`), not from `src/main/resources/static/images` on the classpath (`staticResources`).

Reasoning: this project's whole "MVP data storage" decision above is built around content living
in plain files that get edited without touching code or triggering a rebuild — that's explicitly
why `data/cats.json`/`data/visits.json` aren't compiled into the JAR. Serving images from classpath
resources broke that same promise for exactly one content type: adding or replacing a cat photo
would have meant committing a binary into `src/main/resources`, rebuilding, and redeploying — the
same heavyweight path as a code change, for what should be a content update. `data/images/` fixes
that, and is also the natural mount point for a Docker volume once deployment is set up, so photos
don't need to ship inside the image.

Not addressed by this change: `Cat.photoUrls` entries are still unvalidated free-form strings with no
constraint on scheme or path — nothing stops one from pointing outside `/images/` entirely. That's a
data-validation gap, separate from where the bytes live on disk, and still open.

