# Smoke Checklist

This checklist covers the current MVP routes, static image resources, and local application startup.

## Preconditions

- JDK 21 is installed and used by the project.
- Gradle Wrapper is available.
- The working tree is clean or current changes are intentional.
- The application is run from the project root.
- Test data exists in `data/cats.json`.
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

## Regression checks after route, view, service, repository, static resource, or JSON changes

After changing routes, views, services, repositories, static resources, or JSON data, run a build.

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
- static image resources are served from `/images/...`.

Future improvements:

- HTML escaping for user-visible data from JSON.
- Shared layout rendering.
- CSS styling.
- Automated route tests.
- HTML pages for error states.
- Visit pages and guide page.
