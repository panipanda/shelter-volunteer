# Smoke Checklist

This checklist covers the current MVP routes, static image resources, visit pages, and local application startup.

Pages now live under a language prefix: `/ru`, `/en`, `/sr`. `ru` is the default and top-priority
locale — `/` redirects there. `/health` and `/images/...` stay unprefixed, since they are not
localized content. Examples below use `/ru` unless noted otherwise.

## Preconditions

- JDK 21 is installed and used by the project.
- Gradle Wrapper is available.
- The working tree is clean or current changes are intentional.
- The application is run from the project root.
- Test data exists in `data/cats.json`.
- Test data exists in `data/visits.json`.
- Static image resources exist under `src/main/resources/static/images/`.

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
- page contains cat names from `data/cats.json`;
- each cat is displayed as a list item;
- each cat name links to `/ru/cats/{id}`;
- cats with missing names are displayed as `Unnamed`.

Example expected HTML content:

```html
<h1>Cats</h1>
<ul>
    <li><a href="/ru/cats/1">Mila</a></li>
</ul>
```

The exact names and ids depend on the current contents of `data/cats.json`.

## Cat details endpoint — existing cat

Open an existing cat page, for example:

```text
http://localhost:8080/ru/cats/1
```

Or check from terminal.

### Windows PowerShell

```powershell
Invoke-WebRequest http://localhost:8080/ru/cats/1
```

### Linux/macOS

```bash
curl http://localhost:8080/ru/cats/1
```

Expected result:

- response is returned successfully;
- response is an HTML page;
- page contains the display name of the cat with `id = 1`;
- if the cat name is missing in JSON, page contains `Unnamed`;
- page displays cat age using the configured age fallback;
- page displays adoption availability;
- page displays cat description;
- page displays a cat image if `photoUrl` is present;
- page displays the default cat image if `photoUrl` is missing or `null`;
- image is loaded from `/images/...` (unprefixed);
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
http://localhost:8080/ru/cats/999
```

Or check from terminal.

### Windows PowerShell

```powershell
Invoke-WebRequest http://localhost:8080/ru/cats/999
```

### Linux/macOS

```bash
curl http://localhost:8080/ru/cats/999
```

Expected response body:

```text
Cat not found
```

Acceptance criteria:

- application does not crash;
- response body clearly explains that the cat was not found.

## Cat details endpoint — invalid id

Open:

```text
http://localhost:8080/ru/cats/abc
```

Or check from terminal.

### Windows PowerShell

```powershell
Invoke-WebRequest http://localhost:8080/ru/cats/abc
```

### Linux/macOS

```bash
curl http://localhost:8080/ru/cats/abc
```

Expected response body:

```text
Invalid cat id
```

Acceptance criteria:

- application does not crash;
- invalid non-numeric path parameter is handled safely;
- response body clearly explains that the id is invalid.

## Upcoming visits endpoint

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
- page contains upcoming visits from `data/visits.json`;
- visits with `COMPLETED` status are not shown on this page;
- page displays visit title;
- page displays date, time, and timezone;
- page displays direction and status as translated labels, not raw enum names;
- page displays availability through `displayVisitAvailability`;
- page displays capacity or fallback text;
- page displays signup instruction or fallback text;
- page contains navigation links to:
  - `/ru/cats`
  - `/ru/visits`
  - `/ru/visits/archive`

Example expected content for the current test data:

```text
Volunteer visits
Cat shelter visit
2026-06-12
11:00
Europe/Belgrade
Open
Free places: 2
```

Acceptance criteria:

- application does not crash;
- upcoming visits are loaded from `data/visits.json`;
- completed visits are excluded;
- nullable visit fields are handled safely;
- HTML response is returned with `ContentType.Text.Html`.

## Visit archive endpoint

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
- page contains completed visits from `data/visits.json`;
- visits without `COMPLETED` status are not shown on this page;
- page displays visit title;
- page displays date, time, and timezone;
- page displays direction and status as translated labels, not raw enum names;
- page displays availability through `displayVisitAvailability`;
- page displays public summary if present;
- page contains navigation links to:
  - `/ru/cats`
  - `/ru/visits`
  - `/ru/visits/archive`

Example expected content for the current test data:

```text
Visit archive
Cat shelter visit
2026-05-10
11:00
Europe/Belgrade
Completed
Visit completed
Visit completed. Volunteers helped with cleaning, feeding and cat socialization.
```

Acceptance criteria:

- application does not crash;
- archived visits are loaded from `data/visits.json`;
- only completed visits are shown;
- nullable visit fields are handled safely;
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

Note: guide body content is now read from `content/volunteer-guide.md` at request time (via
`GuideRepository`) and rendered as real DSL tags, not hardcoded in `GuideViews.kt` anymore. It is
still English-only regardless of locale — no `content/volunteer-guide.ru.md` /
`content/volunteer-guide.sr.md` exist yet, so every locale falls back to the English file.

## Other locales

Repeat the cat list, cat details, visits, visit archive, and guide checks above under `/en` and
`/sr`. Since `ru`/`sr` translations for JSON content (`data/cats.json`, `data/visits.json`) and for
UI strings (`src/main/resources/i18n/messages.properties`) are not filled in yet, expect the same
English text to appear under all three prefixes — this is the intended fallback behavior, not a bug.

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
- the file is loaded from `src/main/resources/static/images/default-cat.jpg`;
- this endpoint is intentionally not language-prefixed.

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
- route tests for visit pages (targeting `/ru/visits` and `/ru/visits/archive`);
- route tests for guide page (targeting `/ru/guide`):
  - `GET /ru/guide` returns `200 OK`;
  - response contains the main guide sections;
  - response contains navigation links to `/ru/cats`, `/ru/visits`, and `/ru/visits/archive`.

Acceptance criteria:

- all automated tests pass;
- route tests confirm that `/ru/visits` returns upcoming visits;
- route tests confirm that `/ru/visits/archive` returns completed visits;
- visit pages do not mix upcoming and archived visit data.

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
- `GET /ru/cats`
- `GET /ru/cats/1`
- `GET /ru/cats/999`
- `GET /ru/cats/abc`
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
- `/{lang}/cats/{id}` returns HTML for an existing cat.
- `/{lang}/cats/{id}` returns plain text for missing or invalid cat ids.
- `/{lang}/visits` returns HTML with upcoming visits.
- `/{lang}/visits/archive` returns HTML with completed visits.
- `/{lang}/guide` — volunteer guide page (body content is not actually localized yet, see below).
- static image resources are served from `/images/...`, unprefixed.

Current known limitations:

- `data/cats.json` and `data/visits.json` only have `en` translations filled in — `ru` and `sr`
  fall back to `en` through `LocalizedText.forLocale()`.
- UI strings in `src/main/resources/i18n/messages.properties` are English-only (no locale suffix) —
  `ru` and `sr` fall back to this file through the JVM's default `ResourceBundle` lookup.
- `content/volunteer-guide.md` only exists in English — `ru`/`sr` fall back to it through
  `MarkdownGuideRepository`, same convention as `LocalizedText` and `messages.properties`.
- There is no shared layout yet (each page repeats its own `<!DOCTYPE>`/`<head>` boilerplate).
- There is no CSS yet.
- There are no route tests for `/` or `/{lang}/cats` (only visits and guide routes are covered).
- Error states for cat pages (`Cat not found`, `Invalid cat id`) still return plain text, not a
  styled HTML page.

Future improvements:

- Real `ru` (priority) and `sr` translations for JSON content, UI strings, and
  `content/volunteer-guide.md`.
- Shared layout rendering.
- CSS styling.
- Route tests for `/` and `/{lang}/cats`.
- HTML pages for error states.
