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
| h2 | Literata | 26 px | 24 px | 600 | 1.3 |
| h3 and smaller headings | Source Sans 3 | 18 px | 18 px | 600 | 1.35 |
| Lead paragraph | Source Sans 3 | 20 px | 18 px | 400 | 1.5 |
| Body | Source Sans 3 | 18 px | 17 px | 400 | 1.55 |
| Small / meta | Source Sans 3 | 15 px | 15 px | 400 | 1.5 |
| Caption | Source Sans 3 | 14 px | 14 px | 400 | 1.4 |

`letter-spacing: -0.01em` on h1 only. No uppercase, no letter-spaced labels. Emphasis is 600 weight, never italics.

Running text (typesetting):

- **Measure.** Paragraphs, lists, quotes and call-outs are at most `--measure: 33em` wide (about 600 px, 70 characters in ru),
  while photos, cards, rules and the next-visit block keep the full 720 px column. A 720 px line of 18 px text is 85–90
  characters, too long to read comfortably.
- **No hanging words.** Every text node goes through `typographicText` (`service/DisplayHelpers.kt`), which joins one-letter
  words and short prepositions (ru: в, к, с, на, по, из…; sr: one-letter words) to the next word with a no-break space,
  and a dash to the word before it. It runs once for the whole page in `respondPage` (`views/PageResponse.kt`), so UI strings,
  cat and dog descriptions and the guide are all covered. Attributes (alt, aria-label) and scripts are not touched.
  Headings use `text-wrap: balance`, running text `text-wrap: pretty`.
- **Quotes.** Content files use the language's own marks, never the straight `"`: «ёлочки» (ru), “…” (en), „…“ (sr).
  A test fails on a straight quote in `content/*.md`.

## Layout

- One text column: `--wrap-width: 720px` of text plus a side gutter of `24px` each (`16px` at ≤ 640 px), so the box is
  768 px wide. It is the same on
  every page, including the home page. The old 1120 px wrap and full-width coloured strips go away.
- Header: 64 px high, bottom border 1 px. Logo + name on the left, nav (Cats, Dogs, Guide, Visits, How to help), then language
  switch and theme toggle. "How to help" is the help page, the start of the part of the site for donors; it is named as a
  question, like the page's own heading, not as a verb ("Помочь") or as "Support" (which reads as technical support in
  English). The menu says "Guide", not "Volunteer guide" (`nav.guide.short`), and on a phone the items are 16 px with
  6 px of padding: five items have to fit in one row on a 360 px phone in ru, where "Как помочь" is the longest; the page
  itself keeps the full title. The one-row version needs about 700 px in ru and sr, so at ≤ 760 px (the only breakpoint besides 640 and only for
  the header) it becomes two rows: wordmark with language and theme controls, then the nav. The "Visits" drop-down
  hangs under the whole nav row there, so it cannot leave the screen. Every control is at least 44 px high.
  Until the logo exists the header shows only the wordmark as live text.
- Skip link: the first focusable element of every page ("Skip to content", `#main`). It is off screen until it gets focus,
  then it is an accent button in the top left corner. Both `nav` landmarks have an `aria-label`; the language links carry
  `lang`, `hreflang` and the language's own name as tooltip and accessible name.
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
- Dogs use the same page without the button and the status: the same markup (`cat-head`, `cat-profile`, class `cat-page`),
  the same title rule (`{name} · Pro Anima`), and the list of dogs is the same as the list of cats: the heading and the cards,
  nothing else. Sign-up for the dog trips is on the home page, who Svora is is on the help page.

### Home page order

Heading and lead (no links, no figures) → next visit → photo → cats looking for a home → how to join (four steps: the guide,
the cats' chat, the dogs' chat, picking a visit; then the founder's quote) → how volunteers help (what they do with
the cats and with the dogs, three points each) → about the shelter (the figures, nothing else: no links). Five sections after the hero; each one after the photo starts with a 1 px rule.
The photo under the next-visit block is a 16:10 file, `data/images/home-hero.jpg` (910×569): the original (910×682, in git
history at commit c845d70) cropped at its bottom edge, which removes the top 113 px of roof, because the group sits in the
lower part of the frame. The file itself is cropped, not the CSS, so the link preview (`og:image`) shows the same frame.
A different photo should be cut to 16:10 the same way, at least 1440 px wide, with the faces checked after the crop.
The guide has one photo too, between the table of contents and the first section: `data/images/home-guide.jpg` (1440×900,
three cats behind a wire fence). Both page photos are 16:10 and share one CSS rule (`.hero-photo`, `.guide-photo`): at 16:9 the
lowest cat's muzzle on the guide photo is cut off, and the two pages should look alike.
"How else to help" (donations, food, things, fostering) is for a different reader than the volunteer, so it has its own page,
`/{lang}/help`, linked from the menu and from the footer; the donation link and the description of Svora live only there.
The shelter's own news channels (Instagram, Telegram) are not on the home page; the footer has the Telegram channel.

The next visit is the one big accent: the day number in
Literata 56 px, the weekday, the departure, and the page's single primary button ("How to sign up"), which goes to
"how to join". At ≤ 640 px the block is one column: the title, then the number, the month and the weekday on one line
(44 px number), then the text across the full width. The block's own title stays 22 px so the date remains the headline. The lead explains who organises the trips (Svora.Volunteers), so the page does not use the word without
saying what it is.

Each fact is stated once: the founding year and the counts only in "about"; the coordinators' contacts only in
"how to join" (everywhere else a link to it); the dog trips' sign-up (one sentence and the link to the moderator, built like the cats' step) only in the
same section. A step with a link is one sentence with the link inside it ("Read the guide: …"), not a sentence and a second
line saying "open the guide".
The regular days (Wednesday and Saturday) are said once, in the next-visit block, together with how places and sign-up
work; "how to join" has only the steps. The departure time is the one in the block's own line, so it is never given twice.

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
  A contact in a step ("write to the coordinator: Name (@handle), Name (@handle)") is part of the sentence: it follows
  the colon in the same line and wraps with the text, never in a row of its own under it. A name stays together with its
  handle (`.nowrap`), so on a narrow screen a link that does not fit moves down whole, never split in two.
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
- **Card copies**: cards and gallery thumbnails load `name-card.jpg` (about 440×550, made by `tools/make-card-images.py`),
  the big photo and the lightbox load the original. The copy is not cropped, so the 4:5 crop is still CSS. Images after
  the first three cards, the thumbnails and the hidden lightbox photos have `loading="lazy"`; the main photo is not lazy.
  The server does not start if a photo has no copy.
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

## Page metadata

- **Title:** `{page} · Pro Anima`, so a row of tabs says where each one is from. The home page's own title already has the
  name and is used as is.
- **Link preview:** every page has `og:title`, `og:description` (when the page has a description), `og:url`, `og:image`
  and `twitter:card`. A cat or dog page uses its first photo; every other page, and anything whose first photo is an SVG
  placeholder, uses `/images/home-hero.jpg` (a link preview needs a raster picture). Addresses are absolute, from
  `PUBLIC_BASE_URL` (`views/Layout.kt`, see `docs/deploy.md`).
- **Language alternates:** `canonical` and `hreflang` (ru, en, sr, x-default = ru) on every page. Error pages are rendered for
  any URL, so they have none and carry `noindex`.

## Implementation order

1. Tokens + both fonts in `static/styles/main.css`; `preloadedFonts()` in `views/Layout.kt` (serif and sans files);
   remove the Ubuntu Mono files and its licence; add the OFL texts for Literata and Source Sans 3.
2. Shell: header, nav, language and theme controls, footer, 720 px column.
3. Cat list and cat card, then cat page.
4. Home page.
5. Guide, visits, archive, dogs, error pages.

One commit per step. After each: `./gradlew test`, screenshots at 375 / 768 / 1280 in light and dark for ru and sr.

## Open points

- Mobile header: the nav (5 items + language + theme) fits one row from 360 px in ru and from 320 px in en and sr; in ru at
  320 px it wraps to a second row. A collapsed pattern is needed if an item is added.
- Guide aside on mobile: keep the current stacking, restyled.
