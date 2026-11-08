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

## Phase 2 — Database storage (done for cats, dogs and photos)

Goal: replace JSON storage without rewriting routes and services. The plan said SQLite; it became PostgreSQL
(Neon, free plan) because the free Render plan wipes the disk on every deploy, which would wipe a SQLite file too
(see `docs/decisions.md` "Data storage").

Done:

- Cats, dogs and their photos in PostgreSQL when `DATABASE_URL` is set; files stay the default
- First start copies `data/` into an empty database, once
- Same repository interfaces, same routes and services
- Backup: one button in the admin (a zip with the texts and the photos)

Left: visits and visit participants have no storage at all yet (visits are organised in the volunteer chat, see
`docs/decisions.md` "Visit data").

## Phase 3 — Simple content management

Done (2026-11): a password-protected admin area at `/admin` (database mode only) that edits cats and dogs with
their photos, with drafts, preview, validation of required fields and a backup. See `docs/decisions.md` "Admin
panel" and `docs/deploy.md`.

Done (2026-11, second part): the texts and photos of the home, visits, guide and help pages are edited in the same
panel ("Страницы сайта"), over the texts that ship with the code. See `docs/decisions.md` "Admin panel".

Done (2026-11, third part): sections. The admin builds new parts of the site (settings, fields, tags, entries with
drafts, preview and photos) and starts from presets: useful articles, and the visit archive. They are in the menu only
when they have something published. See `docs/decisions.md` "Sections".

Done (2026-11, fourth part): the menu tree. The admin chooses where the pages, the sections and new groups stand in
the menu (two levels, drop-downs), hides a page from the menu, and picks the level in the form of a section. See
`docs/decisions.md` "Menu tree".

Left:

- Edit the trip schedule, cancelled and extra trips (still `data/visit-overrides.json` and the schedule in the code);
  separate from the visit archive, which only records what happened
- Reordering the entries of a section by hand, and images inside a long text
- Hiding or reordering a block on a page (the page forms change what blocks say, not which blocks exist)
- Prevent accidental public exposure of participant data (relevant once participants are stored)
- Possibly: personal logins and a "who changed what" record, if one shared password stops being enough

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