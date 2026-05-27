# Smoke Checklist

This checklist covers the current MVP routes, static image resources, visit pages, and local application startup.

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
- response body is exactly `OK`.

## Cat list endpoint

Open in a browser:

```text
http://localhost:8080/cats
```

Or check from terminal.

### Windows PowerShell

```powershell
Invoke-WebRequest http://localhost:8080/cats
```

### Linux/macOS

```bash
curl http://localhost:8080/cats
```

Expected result:

- response is returned successfully;
- response is an HTML page;
- page contains the `Cats` heading;
- page contains cat names from `data/cats.json`;
- each cat is displayed as a list item;
- each cat name links to `/cats/{id}`;
- cats with missing names are displayed as `Unnamed`.

Example expected HTML content:

```html
<h1>Cats</h1>
<ul>
    <li><a href="/cats/1">Mila</a></li>
</ul>
```

The exact names and ids depend on the current contents of `data/cats.json`.

## Cat details endpoint — existing cat

Open an existing cat page, for example:

```text
http://localhost:8080/cats/1
```

Or check from terminal.

### Windows PowerShell

```powershell
Invoke-WebRequest http://localhost:8080/cats/1
```

### Linux/macOS

```bash
curl http://localhost:8080/cats/1
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
- image is loaded from `/images/...`;
- page contains a link back to `/cats`.

Acceptance criteria:

- application does not crash;
- nullable cat fields are handled through display helpers;
- image URL is rendered into the HTML page;
- broken image icon is not shown when the image file exists.

## Cat details endpoint — missing cat

Open:

```text
http://localhost:8080/cats/999
```

Or check from terminal.

### Windows PowerShell

```powershell
Invoke-WebRequest http://localhost:8080/cats/999
```

### Linux/macOS

```bash
curl http://localhost:8080/cats/999
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
http://localhost:8080/cats/abc
```

Or check from terminal.

### Windows PowerShell

```powershell
Invoke-WebRequest http://localhost:8080/cats/abc
```

### Linux/macOS

```bash
curl http://localhost:8080/cats/abc
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
http://localhost:8080/visits
```

Or check from terminal.

### Windows PowerShell

```powershell
Invoke-WebRequest http://localhost:8080/visits
```

### Linux/macOS

```bash
curl http://localhost:8080/visits
```

Expected result:

- response is returned successfully;
- response is an HTML page;
- page contains the `Volunteer visits` heading;
- page contains upcoming visits from `data/visits.json`;
- visits with `COMPLETED` status are not shown on this page;
- page displays visit title;
- page displays date, time, and timezone;
- page displays direction;
- page displays status;
- page displays availability through `displayVisitAvailability`;
- page displays capacity or fallback text;
- page displays signup instruction or fallback text;
- page contains navigation links to:
  - `/cats`
  - `/visits`
  - `/visits/archive`

Example expected content for the current test data:

```text
Volunteer visits
Cat shelter visit
2026-06-12
11:00
Europe/Belgrade
OPEN
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
http://localhost:8080/visits/archive
```

Or check from terminal.

### Windows PowerShell

```powershell
Invoke-WebRequest http://localhost:8080/visits/archive
```

### Linux/macOS

```bash
curl http://localhost:8080/visits/archive
```

Expected result:

- response is returned successfully;
- response is an HTML page;
- page contains the `Visit archive` heading;
- page contains completed visits from `data/visits.json`;
- visits without `COMPLETED` status are not shown on this page;
- page displays visit title;
- page displays date, time, and timezone;
- page displays direction;
- page displays status;
- page displays availability through `displayVisitAvailability`;
- page displays public summary if present;
- page contains navigation links to:
  - `/cats`
  - `/visits`
  - `/visits/archive`

Example expected content for the current test data:

```text
Visit archive
Cat shelter visit
2026-05-10
11:00
Europe/Belgrade
COMPLETED
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

### `/guide`

Expected:

- Page opens successfully.
- Page title or main heading contains `Volunteer guide`.
- Page contains the section `Before the visit`.
- Page contains the section `What to bring`.
- Page contains the section `During the visit`.
- Page contains the section `How signup works`.
- Navigation links are visible:
  - `Cats` -> `/cats`
  - `Volunteer visits` -> `/visits`
  - `Visit archive` -> `/visits/archive`

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
- the file is loaded from `src/main/resources/static/images/default-cat.jpg`.

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
- route tests for visit pages;
- route tests for guide page:
  - `GET /guide` returns `200 OK`;
  - response contains the main guide sections;
  - response contains navigation links to `/cats`, `/visits`, and `/visits/archive`.

Acceptance criteria:

- all automated tests pass;
- route tests confirm that `/visits` returns upcoming visits;
- route tests confirm that `/visits/archive` returns completed visits;
- visit pages do not mix upcoming and archived visit data.

## Regression checks after route, view, service, repository, static resource, or JSON changes

After changing routes, views, services, repositories, static resources, or JSON data, run a build.

- After adding `/guide`, verify that existing pages still open:
  - `/cats`
  - `/cats/1`
  - `/visits`
  - `/visits/archive`
  - `/health`
- Verify that visit pages navigation does not link to the current page unnecessarily:
  - `/visits` contains link to `/guide`
  - `/visits/archive` contains link to `/guide`

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
- `GET /cats`
- `GET /cats/1`
- `GET /cats/999`
- `GET /cats/abc`
- `GET /visits`
- `GET /visits/archive`

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

- `/health` returns plain text.
- `/cats` returns HTML.
- `/cats/{id}` returns HTML for an existing cat.
- `/cats/{id}` returns plain text for missing or invalid cat ids.
- `/visits` returns HTML with upcoming visits.
- `/visits/archive` returns HTML with completed visits.
- `/guide` — volunteer guide page
- static image resources are served from `/images/...`.

Current known limitations:

- HTML escaping is not implemented yet.
- Visit details page is not implemented yet.
- Error states for cat pages still return plain text.
- There is no shared layout yet.
- There is no CSS yet.
- There are no route tests yet.

Future improvements:

- HTML escaping for user-visible data from JSON.
- Shared layout rendering.
- CSS styling.
- Automated route tests.
- HTML pages for error states.
- Volunteer guide page.
j