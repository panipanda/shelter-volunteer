# Smoke Checklist

This checklist covers the current MVP routes, static image resources, visit stub pages, and local application startup.

Pages now live under a language prefix: `/ru`, `/en`, `/sr`. `ru` is the default and top-priority
locale — `/` redirects there. `/health` and `/images/...` stay unprefixed, since they are not
localized content. Examples below use `/ru` unless noted otherwise.

## Preconditions

- JDK 21 is installed and used by the project.
- Gradle Wrapper is available.
- The working tree is clean or current changes are intentional.
- The application is run from the project root.
- Test data exists in `data/cats.json`.
- Static image resources exist under `data/images/`.

## Build check

### Windows

Run:

```powershell
.\gradlew.bat build
```

### Linux/macOS

Run:

```bash
./gradlew build
```

Expected result:

```text
BUILD SUCCESSFUL
```

Acceptance criteria:

- Kotlin code compiles successfully.
- Existing automated tests pass.
- No local scratch files under `src/main/kotlin` break the build.

## Local run

### Windows

Start the application:

```powershell
.\gradlew.bat run
```

### Linux/macOS

Start the application:

```bash
./gradlew run
```

Expected result:

- the application starts without errors;
- the server listens on port `8080`;
- the process keeps running until stopped manually.

## Root redirect

Open in a browser:

```text
http://localhost:8080/
```

Expected result:

- browser is redirected to `http://localhost:8080/ru`;
- the homepage loads successfully after the redirect.

## Health endpoint

Open in a browser:

```text
http://localhost:8080/health
```

Or check from terminal.

### Windows PowerShell

```powershell
Invoke-WebRequest http://localhost:8080/health
```

### Linux/macOS

```bash
curl http://localhost:8080/health
```

Expected response body:

```text
OK
```

Acceptance criteria:

- response is returned without server error;
- response body is exactly `OK`;
- this endpoint is intentionally not language-prefixed.

## Cat list endpoint

Open in a browser:

```text
http://localhost:8080/ru/cats
```

Or check from terminal.

### Windows PowerShell

```powershell
Invoke-WebRequest http://localhost:8080/ru/cats
```

### Linux/macOS

```bash
curl http://localhost:8080/ru/cats
```

Expected result:

- response is returned successfully;
- response is an HTML page;
- page contains the `Cats` heading;
- page contains cat names from `data/cats.json`, in the page language (`name` is a
  `ru`/`en`/`sr` object like `description`, e.g. `Феликс` / `Felix` / `Feliks`);
- each cat is displayed as a preview card with a photo and its name;
- the card photo is the cat's first `photoUrls` entry, or the default cat image if
  `photoUrls` is empty (same fallback as the cat details gallery, via `displayPhotoUrls`);
- each cat card links to `/ru/cats/{slug}`, where `slug` is the cat's English name lowercased,
  the same in every language (e.g. `Nami` → `nami`; a cat without an English name falls back
  to its numeric `id`);
- cats with missing names are displayed as `Unnamed`.

Example expected HTML content:

```html
<h1>Cats</h1>
<div class="cat-list">
    <a href="/ru/cats/nami" class="cat-card">
        <img src="/images/nami.jpg" alt="Nami" class="cat-card-photo">
        <span>Nami</span>
    </a>
</div>
```

The exact names and slugs depend on the current contents of `data/cats.json`.

## Cat details endpoint — existing cat

Open an existing cat page, for example:

```text
http://localhost:8080/ru/cats/nami
```

Or check from terminal.

### Windows PowerShell

```powershell
Invoke-WebRequest http://localhost:8080/ru/cats/nami
```

### Linux/macOS

```bash
curl http://localhost:8080/ru/cats/nami
```

Expected result:

- response is returned successfully;
- response is an HTML page;
- page contains the display name of the cat with slug `nami`;
- if the cat name is missing in JSON, page contains `Unnamed`;
- page displays cat age using the configured age fallback;
- page displays cat location (shelter/foster home);
- page displays adoption stage;
- page displays cat description;
- page displays a photo gallery with all `photoUrls` entries as thumbnails;
- page displays the default cat image if `photoUrls` is empty;
- clicking a thumbnail opens that photo full-size in a lightbox (no page navigation);
- when the cat has more than one photo, prev/next arrows inside the lightbox step through the photos and wrap around at the ends, by click or by the Left/Right arrow keys;
- images are loaded from `/images/...` (unprefixed);
- page contains a link back to `/ru/cats`.

Acceptance criteria:

- application does not crash;
- nullable cat fields are handled through display helpers;
- image URL is rendered into the HTML page;
- broken image icon is not shown when the image file exists;
- dynamic text (name, description) is HTML-escaped, not inserted raw.

## Cat details endpoint — missing cat

Open:

```text
http://localhost:8080/ru/cats/no-such-cat
```

Or check from terminal.

### Windows PowerShell

```powershell
Invoke-WebRequest http://localhost:8080/ru/cats/no-such-cat
```

### Linux/macOS

```bash
curl http://localhost:8080/ru/cats/no-such-cat
```

Expected response body:

```text
Cat not found
```

Acceptance criteria:

- application does not crash;
- response body clearly explains that the cat was not found;
- any slug that doesn't match a cat is handled the same way — there is no separate
  "invalid slug" case, since any string is a syntactically valid slug.

## Home page: next visit card

Open `http://localhost:8080/ru`.

Expected result:

- the "Ближайший визит" card shows the date and weekday of the nearest departure: Wednesday 9:00
  or Saturday 10:00 (from Vračar), whichever comes first; once today's departure time has passed,
  the next one is shown;
- the card mentions the cat volunteer chat and the coordinators, and does not show free places;
- the button "Записаться в чате" scrolls to the "How to join" block;
- the same card is shown in `/en` and `/sr` with translated text.

## Upcoming visits endpoint (stub)

Visit data is not stored yet: the page is a stub until sync with the cat volunteer chat is implemented.

Open in a browser:

```text
http://localhost:8080/ru/visits
```

Or check from terminal.

### Windows PowerShell

```powershell
Invoke-WebRequest http://localhost:8080/ru/visits
```

### Linux/macOS

```bash
curl http://localhost:8080/ru/visits
```

Expected result:

- response is returned successfully;
- response is an HTML page;
- page contains the `Volunteer visits` heading;
- page contains the stub text saying the list will appear soon and pointing to the cat volunteer
  chat and the coordinators;
- page contains no visit cards, dates or free-place counts;
- page contains navigation links to:
  - `/ru/cats`
  - `/ru/visits`
  - `/ru/visits/archive`

Acceptance criteria:

- application does not crash;
- no data file is required for this page;
- HTML response is returned with `ContentType.Text.Html`.

## Visit archive endpoint (stub)

Open in a browser:

```text
http://localhost:8080/ru/visits/archive
```

Or check from terminal.

### Windows PowerShell

```powershell
Invoke-WebRequest http://localhost:8080/ru/visits/archive
```

### Linux/macOS

```bash
curl http://localhost:8080/ru/visits/archive
```

Expected result:

- response is returned successfully;
- response is an HTML page;
- page contains the `Visit archive` heading;
- page contains the stub text saying the archive will appear soon;
- page contains no visit cards;
- page contains navigation links to:
  - `/ru/cats`
  - `/ru/visits`
  - `/ru/visits/archive`

Acceptance criteria:

- application does not crash;
- no data file is required for this page;
- HTML response is returned with `ContentType.Text.Html`.

## Volunteer guide endpoint

### `/ru/guide`

Expected:

- Page opens successfully.
- Page title or main heading contains `Volunteer guide`.
- Page contains the section `Before the visit`.
- Page contains the section `What to bring`.
- Page contains the section `During the visit`.
- Page contains the section `How signup works`.
- Navigation links are visible:
  - `Cats` -> `/ru/cats`
  - `Volunteer visits` -> `/ru/visits`
  - `Visit archive` -> `/ru/visits/archive`

Note: guide body content is now read from `content/volunteer-guide.md` (or the locale-specific
`content/volunteer-guide.{locale}.md`, when present) at request time via `GuideRepository`, and
rendered as real DSL tags, not hardcoded in `GuideViews.kt`. `content/volunteer-guide.ru.md` now
exists with real Russian content — `/ru/guide` renders it instead of falling back to English.
`content/volunteer-guide.sr.md` exists with real Serbian (Latin script) content, so `/sr/guide`
renders it too.

## Other locales

Repeat the cat list, cat details, visits, visit archive, and guide checks above under `/en` and
`/sr`.

- `/ru` now has real translations for UI strings (`messages_ru.properties`), JSON content
  (`data/cats.json`) and the guide (`content/volunteer-guide.ru.md`) — expect
  Russian text there, not an English fallback.
- `/sr` has real translations too (Serbian, Latin script, ekavian) for UI strings
  (`messages_sr.properties`), JSON content and the guide (`content/volunteer-guide.sr.md`) — expect
  Serbian text there, not an English fallback. A locale/field with no translation still falls back
  to English through the same conventions as before.
- On `/ru/guide`, the page's `<html lang>` should be `ru`, on `/sr/guide` — `sr`; `lang` reflects
  which file was actually resolved, so a locale without its own guide file would report `en`.

## Language switcher

Every page shows a `RU · EN · SR` switcher in the nav, right below the main links.

Open any page, for example:

```text
http://localhost:8080/ru/cats/1
```

Expected result:

- the current locale (`RU`) is shown as plain text, not a link;
- `EN` and `SR` are links;
- clicking `EN` opens `http://localhost:8080/en/cats/nami` — same page, only the locale prefix
  changes, the rest of the path (`/cats/nami`) is preserved;
- this also holds on `/{lang}/guide`, `/{lang}/visits`, `/{lang}/visits/archive`, and the home
  page (`/{lang}`).

Acceptance criteria:

- the switcher never links to the currently active locale;
- switching locale keeps the same route (cat slug, page type) instead of always going to the
  locale's home page.

## Static default cat image

Open in a browser:

```text
http://localhost:8080/images/default-cat.jpg
```

Or check from terminal.

### Windows PowerShell

```powershell
Invoke-WebRequest http://localhost:8080/images/default-cat.jpg
```

### Linux/macOS

```bash
curl -I http://localhost:8080/images/default-cat.jpg
```

Expected result:

- image is returned successfully;
- browser displays the default cat image;
- response is not `404 Not Found`.

Acceptance criteria:

- static image resources are served by Ktor;
- `/images/default-cat.jpg` is available;
- the file is loaded from `data/images/default-cat.jpg` (a real directory on disk, not a
  classpath resource baked into the build);
- this endpoint is intentionally not language-prefixed.

## Stylesheet

Open in a browser:

```text
http://localhost:8080/styles/main.css
```

Or check from terminal.

### Windows PowerShell

```powershell
Invoke-WebRequest http://localhost:8080/styles/main.css
```

### Linux/macOS

```bash
curl -I http://localhost:8080/styles/main.css
```

Expected result:

- response is returned successfully, not `404 Not Found`;
- content type is CSS-ish (`text/css`).

Acceptance criteria:

- `src/main/resources/static/styles/main.css` is served (classpath resource, bundled with the
  build — unlike `/images/...`, CSS is application styling, not per-record content, so baking it
  into the JAR is the right call here, not a repeat of the media-storage mistake);
- every page's `<head>` links it (`<link rel="stylesheet" href="/styles/main.css">`);
- narrow/mobile viewport does not visibly break (nav wraps, content stays within viewport width).

## Automated tests

Run automated tests after changing repositories, services, routes, views, models, or Gradle dependencies.

### Windows

    .\gradlew.bat test

### Linux/macOS

    ./gradlew test

Expected result:

    BUILD SUCCESSFUL

Current automated coverage includes:

- repository tests;
- service tests;
- route tests for the visit stub pages (targeting `/ru/visits` and `/ru/visits/archive`);
- route tests for the home page next-visit card (Wednesday/Saturday choice with a fixed clock);
- route tests for guide page (targeting `/ru/guide`):
  - `GET /ru/guide` returns `200 OK`;
  - response contains the main guide sections;
  - response contains navigation links to `/ru/cats`, `/ru/visits`, and `/ru/visits/archive`.

Acceptance criteria:

- all automated tests pass;
- route tests confirm that `/ru/visits` and `/ru/visits/archive` return stub pages in every locale;
- route tests confirm that the home page shows the nearest Wednesday or Saturday, mentions the chat
  and coordinators, and shows no free-place count.

## Regression checks after route, view, service, repository, static resource, or JSON changes

After changing routes, views, services, repositories, static resources, or JSON data, run a build.

- After adding `/guide`, verify that existing pages still open:
  - `/ru/cats`
  - `/ru/cats/1`
  - `/ru/visits`
  - `/ru/visits/archive`
  - `/health`
- Verify that visit pages navigation does not link to the current page unnecessarily:
  - `/ru/visits` contains link to `/ru/guide`
  - `/ru/visits/archive` contains link to `/ru/guide`
- Verify that `/`, `/en`, and `/sr` still resolve the same set of pages as `/ru`.

### Windows

```powershell
.\gradlew.bat build
```

### Linux/macOS

```bash
./gradlew build
```

Then manually re-check:

- `GET /health`
- `GET /images/default-cat.jpg`
- `GET /styles/main.css`
- `GET /ru/cats`
- `GET /ru/cats/nami`
- `GET /ru/cats/no-such-cat`
- `GET /ru/visits`
- `GET /ru/visits/archive`

## Stop application

In the terminal where the application is running, press:

```text
Ctrl + C
```

Expected result:

- application stops;
- port `8080` is released;
- application can be started again.

### Windows

```powershell
.\gradlew.bat run
```

### Linux/macOS

```bash
./gradlew run
```

## Notes

Current implemented pages:

- `/` redirects to `/ru`.
- `/health` returns plain text, unprefixed.
- `/{lang}` (`ru`, `en`, `sr`) returns the homepage HTML.
- `/{lang}/cats` returns HTML.
- `/{lang}/cats/{slug}` returns HTML for an existing cat; the slug is derived from the cat's
  English name (see `catSlug()` in `service/DisplayHelpers.kt`), the numeric `id` is internal only.
- `/{lang}/cats/{slug}` returns plain text for a slug that matches no cat.
- `/{lang}/visits` and `/{lang}/visits/archive` return stub HTML pages (no visit data yet).
- `/{lang}/guide` — volunteer guide page; `ru`, `en` and `sr` body content is real
  (see "Other locales" above).
- static image resources are served from `/images/...`, unprefixed (real directory on disk).
- CSS is served from `/styles/main.css`, unprefixed (classpath resource, bundled with the build).
- every page shares one skeleton via `pageLayout()` in `views/Layout.kt`: `<head>`/nav/language
  switcher/`<main>`/footer.

Current known limitations:

- `data/cats.json` has `ru`, `en` and `sr` translations filled in.
- UI strings have `messages_ru.properties` and `messages_sr.properties` alongside the English
  default `messages.properties`.
- The volunteer guide has `content/volunteer-guide.ru.md` and `content/volunteer-guide.sr.md`
  alongside the English `content/volunteer-guide.md`; all three have the same sections, including
  the enclosure/cleaning one (`ru` is the original, `en` and `sr` were translated from it).
- `cat.age.years`/`cat.age.months` in the `ru` bundle use the same `choice` approach (1 год /
  2 года / 5 лет / 21 год / 22 года; 1 месяц / 2 месяца / 5 месяцев).
- The Serbian translations have not been reviewed by a native speaker yet.
- `cat.age.years`/`cat.age.months` in the `sr` bundle use `MessageFormat` `choice` to get the
  2-4 vs. 5+ plural forms right (2 godine / 5 godina / 22 godine).
- Styling is intentionally minimal (container width, nav, cat-list preview cards,
  responsive nav wrap) — no design system.
- There are no route tests for `/` or `/{lang}/cats` (only visits, home and guide routes are covered).
- Error states for cat pages (`Cat not found`, `Invalid cat id`) still return plain text, not a
  styled HTML page.

Future improvements:

- Native-speaker review of the `sr` translations.
- Route tests for `/` and `/{lang}/cats`.
- HTML pages for error states.
