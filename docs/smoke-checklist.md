# Smoke Checklist

This checklist covers the current MVP routes and local application startup.

## Preconditions

- JDK 21 is installed and used by the project.
- Gradle Wrapper is available.
- The working tree is clean or current changes are intentional.
- The application is run from the project root.

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
- response contains cat names from `data/cats.json`;
- each cat is displayed on a separate line;
- cats with missing names are displayed as `Unnamed`.

Example expected format:

```text
Zoka
Marta
Unnamed
```

The exact names depend on the current contents of `data/cats.json`.

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
- response contains the display name of the cat with `id = 1`;
- if the cat name is missing in JSON, response contains `Unnamed`.

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

## Regression checks after route changes

After changing routes, services, repositories, or JSON data, run a build.

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

Current `/cats` and `/cats/{id}` responses are plain text.

HTML rendering will be added later.
