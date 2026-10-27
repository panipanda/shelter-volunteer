# Design system

Source of truth for how the site looks. Content, routes and i18n keys are not part of it.
If a page needs something that is not described here, add it here first, then build it.

## Principles

1. **Calm and plain.** The page is text first. Photos are few (a handful of cats), so the
   layout must not depend on big photography.
2. **One accent.** Green is the only colour on the page. Photos and the logo bring the rest.
3. **Every decoration needs a reason.** No shadows, gradients, decorative icons, emoji, pills,
   coloured section backgrounds or hover animations unless this document asks for them.
4. **Two themes, one system.** Dark theme uses the same tokens with different values, never its own layout.
5. **Longest language wins.** ru and sr strings are longer than en. Nothing may rely on a fixed width.
6. **Say it once.** A fact appears once per page: where a cat lives, when the next visit leaves, who to write to.
   Repeat it only as a link to the place that owns it. A label that is true for every item in a list is the list's
   heading, not each item's caption.

## Typography

Two families, each with one job. Both are SIL Open Font License, self-hosted as woff2 (keep each
licence file next to its fonts, as is done for the current fonts).

- **Literata** (serif), weight 600 only. Used for: `h1`, `h2`, the "Pro Anima" wordmark in the header, cat names
  on cards and cat pages, and the day number of the next visit. Nothing else is serif.
- **Source Sans 3** (sans), variable 400–600. Used for everything else: body, lead, `h3` and below, nav, buttons,
  links, meta, numbers (the "2010 / 40+ / 2" facts stay sans).

Files and weight (woff2, per subset):

| Subset | Needed for | Source Sans 3 | Literata (opsz + wght) |
|---|---|---|---|
| `latin` | all locales | ~28 KB | ~49 KB |
| `latin-ext` | sr (č, ć, đ, š, ž) | ~59 KB | ~39 KB |
| `cyrillic` | ru | ~18 KB | ~26 KB |
| `cyrillic-ext` | not preloaded, loaded on demand only | ~11 KB | ~9 KB |

A ru page therefore needs about 120 KB of fonts, an sr page about 175 KB. If that turns out to matter, drop Literata's
optical-size axis (a weight-only 600 file is ~21 / 18 / 11 KB for latin / latin-ext / cyrillic, roughly 45 KB less
per page); headings then use the text cut at display size and look slightly sturdier. Decide with the real pages in hand.

- Preload only `latin` and the active locale's subset, for both families, as `preloadedFonts()` in
  `views/Layout.kt` does today (extend it to the serif files). Source Sans 3 is one variable file per subset, not one per weight.
- `font-display: swap`. Replace the Ubuntu Mono metric-matched fallbacks with ones computed for both families
  (a serif fallback for Literata, a sans fallback for Source Sans 3) so the swap does not move the layout.
- `font-optical-sizing: auto` on Literata. No italics are loaded: do not use `<em>` styling that needs one.

| Role | Family | Desktop | ≤ 640 px | Weight | Line height |
|---|---|---|---|---|---|
| h1 | Literata | 36 px | 30 px | 600 | 1.2 |
| h2 | Literata | 22 px | 22 px | 600 | 1.3 |
| h3 and smaller headings | Source Sans 3 | 18 px | 18 px | 600 | 1.35 |
| Lead paragraph | Source Sans 3 | 20 px | 18 px | 400 | 1.5 |
| Body | Source Sans 3 | 18 px | 17 px | 400 | 1.55 |
| Small / meta | Source Sans 3 | 15 px | 15 px | 400 | 1.5 |
| Caption | Source Sans 3 | 14 px | 14 px | 400 | 1.4 |

`letter-spacing: -0.01em` on h1 only. No uppercase, no letter-spaced labels. Emphasis is 600 weight, never italics.

## Layout

- One text column: `--wrap-width: 720px` of text plus a side gutter of `24px` each (`16px` at ≤ 640 px), so the box is
  768 px wide. It is the same on
  every page, including the home page. The old 1120 px wrap and full-width coloured strips go away.
- Header: 64 px high, bottom border 1 px. Logo + name on the left, nav, then language switch and theme toggle.
  The one-row version needs about 700 px in ru and sr, so at ≤ 760 px (the only breakpoint besides 640 and only for
  the header) it becomes two rows: wordmark with language and theme controls, then the nav. The "Visits" drop-down
  hangs under the whole nav row there, so it cannot leave the screen. Every control is at least 44 px high.
  Until the logo exists the header shows only the wordmark as live text.
- Sections: `56px` apart, each starts with a 1 px `--color-line` rule and `32px` padding above the heading.
- Breakpoint: `640px`. There is one.
- Radius: `4px` for photos, buttons and call-outs. Nothing else is rounded.
- No shadows anywhere. Separation comes from 1 px lines and spacing.

### Cat cards

- Fixed width `220px`, `grid-template-columns: repeat(auto-fill, 220px)`, gap `28px 24px`, left-aligned,
  never stretched. Three fit in the column.
- ≤ 640 px: two equal columns, `repeat(2, 1fr)`, gap `24px 16px`. Do not size from `100vw` (it includes the scrollbar).
- Photo `aspect-ratio: 4 / 5`, `object-fit: cover`. Under it: name (Literata, 20 px, 600), then one meta line (15 px, muted): the age.
  The location is added only when it is not the shelter (`IN_FOSTERHOME`, `AT_HOME`). No status line on cards.
- The cats list and the home page use the same card.

### Cat page

- Title row: the name (h1) on the left and, for a cat that is `FOR_ADOPTION`, the primary button "Message the coordinator on
  Telegram" on the right (it wraps under the name on a narrow screen). The link is the `@handle` found in the cat's
  adoption instruction, so the contact stays in the data. If the instruction has no handle there is no button and the
  instruction is shown as a call-out instead (the contact is stated once). Under the title: one status line (marker shape + text).
- Then photo `360px` (4:5) with 56 px thumbnails under it, and the description and facts list next to it. It is a wrapping
  flex row, not a breakpoint: the text keeps at least 280 px, otherwise it drops below the photo.
- Facts are a `dl`: `110px` label column, 1 px line between rows, labels without the colon. Rows: age, where now, health.
  The adoption stage is not a row: it is the status line under the title.
- Dogs use the same page without the button and the status.

### Home page order

Heading and lead (no links, no figures) → next visit → photo → cats looking for a home → how to join → how volunteers help
(cats, dogs) → about the shelter (the figures, news links, how else to help, the founder's quote). Five sections after
the hero; each one after the photo starts with a 1 px rule. The next visit is the one big accent: the day number in
Literata 56 px, the weekday, the departure, and the page's single primary button ("How to sign up"), which goes to
"how to join". The lead explains who organises the trips (Svora.Volunteers), so the page does not use the word without
saying what it is.

Each fact is stated once: the founding year and the counts only in "about"; the coordinators' contacts only in
"how to join" (everywhere else a link to it); the dogs' signup only on the dogs page (the home page points to it).
A short "next visit" date and the regular schedule in "how to join" are different facts, so both stay.

The next visit is computed from the regular schedule (`NextVisit.kt`) and can be changed by hand in
`data/visit-overrides.json`; the block says the trips run on schedule and that cancellations are announced in the chat,
so it never presents the date as a booking. `/visits` shows the same two blocks (next visit, how to join).

## Colour tokens

Existing mechanism stays: `:root` holds the light values, `:root[data-theme="dark"]` the dark ones, and the
`prefers-color-scheme` block is the no-JS fallback. Keep the current `--color-*` names where they exist.

| Token | Light | Dark | Use |
|---|---|---|---|
| `--color-bg` | `#ffffff` | `#1f2125` | page |
| `--color-surface` | `#f4f4f5` | `#292c31` | call-outs, next-visit block |
| `--color-line` | `#e4e4e7` | `#3e4249` | all 1 px rules |
| `--color-ink` | `#18181b` | `#f0f2f5` | headings, strong text |
| `--color-body` | `#3f3f46` | `#cfd3d9` | running text, lead |
| `--color-muted` | `#67676f` | `#a4a9b1` | meta, captions |
| `--color-accent` | `#1a7f37` | `#56c27c` | primary button, links, status dot, current-nav underline, focus ring |
| `--color-accent-hover` | `#16692e` | `#6fd092` | hover |
| `--color-accent-active` | `#115524` | `#8adca8` | pressed |
| `--color-on-accent` | `#ffffff` | `#05250f` | text on the accent button |

Removed: `--color-mint`, `--color-teal`, `--color-on-teal`, `--color-sun`, `--color-sun-hover`, `--color-on-sun`,
`--color-coral`, `--color-link` (links use the accent), `--color-placeholder` (use `--color-surface`), `--shadow`.
The orange of the logo is not part of the palette.

Contrast (WCAG): all text pairs are at least 4.5:1. Weakest light pairs: accent on surface 4.6, muted on surface 5.1.
Dark: accent on surface 6.3, muted on surface 5.9. Re-check when changing any value.

## Components

- **Primary button**: accent background, `on-accent` text, 600, `9px 16px`, radius 4. Hover/pressed use the
  hover/active tokens. One primary button per view.
- **Secondary action**: a plain link. No outlined or ghost buttons.
- **Links**: accent colour, always underlined, `text-underline-offset: 3px`, 1 px; 2 px on hover.
- **Nav**: ink text, no background on the current item, current item has a 2 px accent underline.
- **Focus**: `outline: 2px solid var(--color-accent); outline-offset: 2px` on every interactive element. Never removed.
- **Status** (cat): shape carries the meaning, so no extra colours are needed. It is shown only where it tells the
  reader something the context does not. Lists are grouped under a heading by stage ("Ждут свою семью", "Нашли дом"),
  so a card never repeats its group's status; only a status that differs from the group is marked (in practice
  `RESERVED`). On the cat page the stage is one line under the title. Shapes: `FOR_ADOPTION` = filled accent dot;
  `RESERVED` = hollow ring in ink; `ADOPTED` = muted dot at 55% opacity; `NONE` = nothing. A dot always comes with its text label.
- **Call-out**: `--color-surface` background, 14 px padding, radius 4, no border. The guide aside is the same with
  a 3 px accent line on the left and no radius on that edge. No red frame.
- **Cover photo**: the first `photoUrls` entry is the card photo and the first image on the page, so put the best frame
  first: the face visible and in focus, nothing between the lens and the animal (no fence or grille: Mila's cover is
  the known bad example), and the face in the middle of the frame, because the 4:5 crop cuts the sides and the edge.
  CSS cannot fix a bad cover, so the rule lives in the data.
- **Placeholder**: an animal with no photos shows `data/images/default-cat.svg` or `default-dog.svg`: a neutral grey
  head (`#9a9ea6`, eyes and nose cut out) on a transparent ground, so the `--color-surface` under the image shows through
  in both themes. Cats and dogs have separate files and separate constants; do not reuse one for the other. The face is
  deliberately wide: a tall head with eyes reads as a person.
- **Language switch**: text `RU EN SR`, current one in ink 600, others muted. No pill.

## Do / don't

| Do | Don't |
|---|---|
| Separate with a line or space | Add a shadow or a tinted card |
| Colour a link or the primary button | Colour a section background |
| Mark a status only when it differs from its group | Caption every card with the same label |
| State each fact once per page | Restate it in another section (link to it instead) |
| Let a long ru/sr label wrap | Fix button or nav item widths |
| Keep photos at card size | Stretch photos to the column or the viewport |
| Use the tokens | Hard-code a hex value in a component |
| Keep serif to h1, h2, wordmark and cat names | Use Literata for body, nav, buttons or meta |

## Accessibility

- Text contrast ≥ 4.5:1 in both themes; focus indicator ≥ 3:1.
- Tap targets ≥ 44 px high on touch (links in the nav get vertical padding).
- No horizontal scroll at 375 px. Respect `prefers-reduced-motion`: there are no transitions to begin with.
- Theme: saved choice, otherwise the system one, set before first paint (existing head script).

## Implementation order

1. Tokens + both fonts in `static/styles/main.css`; `preloadedFonts()` in `views/Layout.kt` (serif and sans files);
   remove the Ubuntu Mono files and its licence; add the OFL texts for Literata and Source Sans 3.
2. Shell: header, nav, language and theme controls, footer, 720 px column.
3. Cat list and cat card, then cat page.
4. Home page.
5. Guide, visits, archive, dogs, error pages.

One commit per step. After each: `./gradlew test`, screenshots at 375 / 768 / 1280 in light and dark for ru and sr.

## Open points

- Mobile header: the nav (4 items + language + theme) needs a collapsed pattern; `nav-menu.js` already exists, keep it
  unless it fights the new header.
- Guide aside on mobile: keep the current stacking, restyled.
- Dogs page: text only for now; reuse the page template, no cards.
