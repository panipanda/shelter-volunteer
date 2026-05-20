# MVP Scope

## Must have

### Cat catalogue

- List of cats
- Cat profile page
- Name fallback if name is missing
- Default photo if photo is missing
- Availability status

### Volunteer guide

- How to prepare for a visit
- What to bring
- Basic safety rules
- What to do during a visit
- What to do after a visit

### Visit calendar

- Upcoming cat volunteer visits
- Date and time
- Free places
- Status
- Signup instruction
- Last updated timestamp

### Technical

- Kotlin backend
- Ktor routes
- Server-rendered HTML
- Basic unit tests
- Basic route tests
- README
- Production deploy

### Data files

- Store cat catalogue data in `data/cats.json`
- Store public visit data in `data/visits.json`
- Store volunteer guide content in `content/volunteer-guide.md`
- Keep data structures close to future database tables

### Visit archive

- Store upcoming and past visits in the same public visit data file
- Show upcoming visits on `/visits`
- Optionally show completed visit summaries on `/visits/archive`
- Keep participant data out of public pages in MVP

### Architecture

- Use repository interfaces for data access
- Keep routes independent from JSON parsing
- Prepare for future SQLite migration

## Out of scope for MVP

- Dog volunteering UI
- Telegram integration
- User accounts
- Roles and permissions
- Payment/donation integration
- Admin panel
- Online registration
- Cancellation flow
- Google Calendar integration
- Google Calendar booking
- Public participant lists
- Authentication
- Database-backed storage