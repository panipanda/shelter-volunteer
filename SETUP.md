# Project Setup

This document describes how to set up the local development environment for the Shelter Volunteer project.

The current goal is to keep the project simple, buildable, and ready for further Kotlin/Ktor development.

## Required tools

Required:

- Git
- JDK 21
- Gradle Wrapper
- VS Code or IntelliJ IDEA

Recommended:

- VS Code Kotlin extension or IntelliJ IDEA
- Gradle extension for IDE support
- JSON syntax validation in IDE

## Java / JDK

The project uses **JDK 21**.

JDK 21 is preferred because it is a stable LTS version and is suitable for Kotlin/JVM and Ktor development.

Check Java version:

```powershell
java -version
```

Expected result:

```text
openjdk version "21..."
```

Example:

```text
openjdk version "21.0.11" 2026-04-21 LTS
OpenJDK Runtime Environment Temurin-21.0.11+10
OpenJDK 64-Bit Server VM Temurin-21.0.11+10
```

Check `JAVA_HOME`:

```powershell
echo $env:JAVA_HOME
```

Expected example:

```text
C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\
```

Check which Java executable is used:

```powershell
where.exe java
```

Expected: JDK 21 should appear before any other installed Java version.

Example:

```text
C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe
```

## Gradle

The project uses **Gradle Wrapper**.

Use wrapper commands instead of global Gradle commands.

Windows:

```powershell
.\gradlew.bat tasks
.\gradlew.bat build
```

Linux/macOS:

```bash
./gradlew tasks
./gradlew build
```

Expected build result:

```text
BUILD SUCCESSFUL
```

## Why Gradle Wrapper is required

Gradle Wrapper fixes the Gradle version for the project.

This means contributors do not need to install the same global Gradle version manually. They can run the project using:

```powershell
.\gradlew.bat build
```

or:

```bash
./gradlew build
```

The wrapper files must be committed to Git:

```text
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
```

## Files that must not be committed

Do not commit local Gradle cache files:

```text
.gradle/
build/
```

These are local generated files and should stay ignored by Git.

The project `.gitignore` should include:

```gitignore
# Gradle
.gradle/
build/

# IntelliJ IDEA
.idea/
*.iml

# VS Code
.vscode/

# OS
.DS_Store
Thumbs.db
```

If `.gradle/` was accidentally tracked by Git, remove it from the Git index:

```powershell
git rm -r --cached .gradle
```

Then commit the `.gitignore` update.

## Build configuration

The project uses two Gradle configuration files in the repository root:

```text
settings.gradle.kts
build.gradle.kts
```

## `settings.gradle.kts`

The project repositories are configured in `settings.gradle.kts`.

The current setup prefers settings-level repositories:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}
```

Because of this, do not add a separate `repositories { mavenCentral() }` block inside `build.gradle.kts`.

Doing so will cause a Gradle error:

```text
Build was configured to prefer settings repositories over project repositories but repository 'MavenRepo' was added by build file 'build.gradle.kts'
```

## `build.gradle.kts`

The build file configures:

- Kotlin/JVM plugin
- Kotlin serialization plugin
- application plugin
- kotlinx.serialization JSON dependency
- test configuration

The application main class is configured as:

```kotlin
application {
    mainClass.set("org.proanima.shelter.ApplicationKt")
}
```

If the build later fails because `ApplicationKt` is missing, create:

```text
src/main/kotlin/org/proanima/shelter/Application.kt
```

with a minimal `main()` function.

## Current successful build checkpoint

The project has reached the following checkpoint:

```powershell
.\gradlew.bat build
```

Expected result:

```text
BUILD SUCCESSFUL
```

This means:

- JDK 21 works
- Gradle Wrapper works
- Kotlin compilation works
- current model/service/repository files compile
- build configuration is valid

## Project source layout

Current Kotlin source structure:

```text
src/main/kotlin/org/proanima/shelter/
  model/
  repository/
  service/
```

Expected responsibilities:

```text
model/       domain data classes and enums
repository/  data access interfaces and implementations
service/     business logic and display helpers
routes/      Ktor routes, added later
views/       HTML rendering, added later
```

Architecture (the mode is picked at startup by the `DATABASE_URL` environment variable):

```text
routes -> services -> repositories -> JSON/Markdown files   (no DATABASE_URL: the default for local work)
routes -> services -> repositories -> PostgreSQL            (DATABASE_URL set: production, with the admin panel)
```

## Data files

Without `DATABASE_URL` the site uses file-based storage; this is what you get locally unless you set it.
(With a database, the cats, dogs and photos are in it and `data/` only seeds an empty one: see
[docs/deploy.md](docs/deploy.md).)

Current data files:

```text
data/
  cats.json
  dogs.json
  images/

content/
  volunteer-guide.md
  volunteer-guide.ru.md
```

Purpose:

```text
data/cats.json                public cat catalogue data (per-field ru/en/sr text)
data/dogs.json                public dog data, the same shape
data/images/                  photos of cats and dogs (each with a -card.jpg copy), site pictures (home-*), placeholders (default-*)
content/volunteer-guide.md    volunteer instruction content, English (fallback for missing translations)
content/volunteer-guide.ru.md volunteer instruction content, Russian
content/volunteer-guide.sr.md volunteer instruction content, Serbian (Latin)
```

UI strings follow the same per-locale-file convention under
`src/main/resources/i18n/`: `messages.properties` (English, also the fallback for missing translations),
`messages_ru.properties` and `messages_sr.properties`. `messages_en.properties` is empty on purpose:
it makes `en` resolve to the base bundle instead of the JVM's default locale.

## JSON validation

For the current stage, JSON can be validated in the IDE.

If Python is available, JSON files can also be validated with:

```powershell
python -m json.tool data/cats.json
```

If Python is not available, IDE validation is enough for now.

The application also checks the content at startup (`validatePets`): duplicate ids or slugs, a birth
month outside 1..12 and photo files missing from `data/images/` stop the server with a message that lists
every problem. `DataValidationTest` runs the same check against the real `data/` files.

### Adding or replacing a photo

On the live site (database mode) photos are added in the admin panel, which makes both copies itself; the rest of
this section is for file mode, i.e. for `data/images/` in the repository.

Cards and thumbnails load a smaller copy of each photo, `name-card.jpg` next to `name.jpg`. After putting a
photo into `data/images/` and listing it in `photoUrls`, make the copy and commit both files:

```powershell
python tools/make-card-images.py
```

The script needs Pillow (`pip install pillow`), skips copies that are already up to date and takes
`--force` to redo all of them. Without the copy the server stops at startup and names the missing file.

## The admin panel and the database mode locally

The admin panel exists only in database mode. To look at it, with no database to install:

```powershell
.\gradlew.bat devAdmin
```

This starts the site with the panel at <http://localhost:8095> on a throwaway in-memory database (filled from
`data/` at each start, gone when you stop it). Log in with the password `dev-password-123`. It runs with the same
memory limits as the Render service (`-Xmx256m`), so a photo upload behaves as it will in production. The code is
in `src/test`, not in the production build.

To run against a real PostgreSQL instead, set `DATABASE_URL` (`postgresql://user:password@host/db?sslmode=require`),
`ADMIN_PASSWORD` (at least 12 characters) and `SESSION_SECRET` (at least 32) before starting the app (add
`ADMIN_COOKIE_SECURE=false` if your browser refuses the login cookie over plain `http://`); use a
separate database, never the production one. `PostgresIntegrationTest` is skipped unless `TEST_DATABASE_URL` is set;
with it set, it runs in a schema of its own and removes it afterwards.

## Scratch files

Do not put learning or experimental files into:

```text
src/main/kotlin
```

Everything inside `src/main/kotlin` is treated as production source code and must compile.

If a learning scratch file is added there, Gradle build may fail with errors like:

```text
Unresolved reference
Cannot infer type
Expecting a top level declaration
```

Use a separate folder for learning notes if needed:

```text
learning/
notes/
```

These folders are not part of production build unless explicitly configured.

## Common commands

Check Git status:

```powershell
git status
```

Run Gradle tasks:

```powershell
.\gradlew.bat tasks
```

Build project:

```powershell
.\gradlew.bat build
```

Run tests:

```powershell
.\gradlew.bat test
```

Clean build output:

```powershell
.\gradlew.bat clean
```

Build from clean state:

```powershell
.\gradlew.bat clean build
```

## Common problems

### `gradle` is not recognized

This means global Gradle is not installed or not added to `PATH`.

Use Gradle Wrapper instead:

```powershell
.\gradlew.bat build
```

If wrapper files do not exist yet, install Gradle manually once and generate wrapper:

```powershell
gradle wrapper --gradle-version 8.10.2
```

### Wrong Java version

Check:

```powershell
java -version
echo $env:JAVA_HOME
where.exe java
```

JDK 21 should be used.

### `.gradle/` appears in Git status

`.gradle/` is a local cache and must not be committed.

Make sure `.gitignore` contains:

```gitignore
.gradle/
build/
```

Then remove it from Git index if needed:

```powershell
git rm -r --cached .gradle
```
