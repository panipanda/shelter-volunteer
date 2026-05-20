# Roadmap
## Phase 1 — MVP with JSON/Markdown storage

Goal: publish a small English-language website for Pro Anima cat shelter volunteers.

Storage:

- `cats.json`
- `visits.json`
- `visit-participants.json` for internal history, not public in MVP
- `volunteer-guide.md`

Features:

- Home page
- Cat catalogue
- Cat profile page
- Volunteer guide
- Read-only cat visit calendar
- Visit archive with public summaries
- Manual availability updates
- Basic tests
- Production deploy

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