# Roadmap
## Phase 1 — MVP with JSON/Markdown storage

Goal: publish a small volunteer website for Pro Anima cat shelter volunteers. `ru` is the
default locale and has real content alongside `en` and `sr` (Latin script; `sr` awaits a
native-speaker review; see `docs/decisions.md` "Language" and `docs/smoke-checklist.md`). The site is live, deployed to
Render (see `docs/deploy.md`).

Storage:

- `cats.json`
- `volunteer-guide.md`

Features:

- Home page
- Cat catalogue
- Cat profile page
- Volunteer guide
- Read-only cat visit calendar
- Visit archive with public summaries — high priority: show cancelled/no-show visits
  honestly instead of only successful ones (a content decision; may need a visit status field
  in the model)
- FAQ as a separate page (`content/faq.md`) — high priority: follow the `volunteer-guide.md`
  pattern, the repository and markdown parser already exist for it
- Manual availability updates
- Language switcher (ru/en/sr)
- Basic tests
- Production deploy — done, see `docs/deploy.md`
- Cat adoption instructions (`adoptionInstruction`) for cats with `adoptionStage == FOR_ADOPTION`,
  shown on the cat profile page — done
- Cat age: store birth date and compute age (years/months) instead of a hardcoded `age`/`ageMonths`
  number that goes stale — done. Shelter cats rarely have a known day of birth, so `Cat` stores
  `birthYear`/`birthMonth` (both nullable; month often unknown) instead of a full date, and
  `displayCatAge` computes age from whichever precision is available
- Cat profile: split free-text description into character/behavior (`description`) and a
  separate `medicalStatus` field (vaccination, spaying/neutering, treatment) — done. Practical
  info a volunteer needs before a visit, kept apart from the adoption-facing narrative
- Cat URLs use a name-based slug (`/cats/mila`) instead of the numeric `id` — done. `id` stays
  internal; a cat without a name (or whose name has no usable characters) falls back to the
  numeric id, same as before this change

## Phase 2 — SQLite storage

Goal: replace JSON storage with SQLite without rewriting routes and services.

Tasks:

- Add SQLite database
- Create tables for cats, visits and visit participants
- Write migration script from JSON to SQLite
- Implement SQLite repositories
- Keep existing service and route contracts
- Add backup process

## Phase 3 — Simple content management

- Add password-protected admin area
- Edit cats
- Edit visit availability and status
- Edit volunteer guide content
- Validate required fields
- Prevent accidental public exposure of participant data

## Phase 4 — Dog volunteering support

- Add dog volunteering direction to visit data
- Add UI filter or tabs for cats and dogs
- Add dog-specific volunteer guide
- Add dog visit calendar entries

## Phase 5 — Online registration research

- Research whether signup should stay in volunteer chats or move to the website
- Evaluate Google Calendar / Google Sheets / custom registration
- Define source of truth
- Design cancellation and waitlist flow

## Phase 6 — Telegram or calendar integration

- Research Telegram integration
- Research Google Calendar display or sync
- Add notifications only if the real volunteer process supports it