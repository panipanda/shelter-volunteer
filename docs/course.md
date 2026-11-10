# The course

`course/` holds a course in Russian, "Kotlin and web development on a live project": Kotlin, Ktor, HTML/CSS/JS,
architecture, algorithms, testing, refactoring, UX, and working with AI agents. It uses this repository as its
example. It is a static site (no build step) published on GitHub Pages:
https://panipanda.github.io/shelter-volunteer/

## Publishing

`.github/workflows/course.yml` checks the lessons and publishes `course/` on every push to `main` that touches it.
One-time setup in the repository: **Settings → Pages → Build and deployment → Source: GitHub Actions** (or
`gh api -X POST repos/panipanda/shelter-volunteer/pages -f build_type=workflow`).

The course does not go into the Docker image of the site (`.dockerignore`), but a push to `main` still starts a
deploy on Render. Put `[skip render]` in the commit message of a course-only change to avoid a pointless rebuild
(check Render's documentation for the exact wording it supports).

## Running it locally

```bash
cd course
python -m http.server 8000     # or any other static server; opening index.html from the disk does not work
# open http://localhost:8000
```

## Layout

```text
course/
  index.html              the shell: header, sidebar, content area
  assets/app.js           routing (#/lesson-id), quizzes, live-coding stepper, runnable code, progress
  assets/markdown.js      the Markdown renderer written for the course (also used by the tools)
  assets/app.css          styles (the same tokens and fonts as the site)
  content/manifest.json   parts and lessons: id, title, minutes, summary
  content/<id>.md         one lesson
  content/home.md         the text of the start page
tools/course-check.mjs    validates the lessons
tools/course-snippets.mjs compiles and runs the Kotlin examples
```

## Writing a lesson

A lesson is Markdown plus a few blocks. Containers open with three or more colons and close with at least as
many; a longer outer container can hold shorter inner ones.

| Block | Syntax |
|---|---|
| Callout | `:::note`, `:::tip`, `:::warn`, `:::project`, `:::ai`, `:::idea` with an optional title |
| Practice task | `::::task Title` containing text, `:::hint` and `:::solution` |
| Live coding | `::::live Title` containing `:::step Title` blocks (shown one at a time) |
| Quiz | `:::quiz Title`; each question starts with `Q:`, options are `- [ ]` / `- [x]`, the explanation starts with `E:` |
| Collapsible | `:::fold Title` |
| Link into the repository | `[text](repo:path/in/the/repo)`; to another lesson `[text](lesson:k1-basics#anchor)` |

Code fences:

| Fence | Meaning |
|---|---|
| ```` ```kotlin ````, ```` ```html ````, ... | static, highlighted |
| ```` ```run ```` | runnable Kotlin (Kotlin Playground in the browser); the next ```` ```output ```` block is the expected output |
| ```` ```starter ```` | runnable exercise that still contains `TODO()`; add `target="junit"` when it holds tests |
| ```` ```test ```` | Kotlin with `kotlin.test` tests, run by the playground's JUnit target |
| ```` ```kotlin check="test" ```` | a static block that the tool still compiles and runs as a test (used for Ktor and kotlinx.html examples) |
| ```` ```demo height="300" ```` | an editable HTML/CSS/JS page with a live preview in a sandboxed frame |

## Rules the tools enforce

`node tools/course-check.mjs` fails when a lesson has fewer than four quiz questions, a quiz option set without a
correct answer or without an explanation (`E:`), a task without a `:::solution`, no live-coding section, a link to a
lesson that does not exist, a `repo:` link to a file that is not committed, or a **code comment that contains
Cyrillic**: comments in code are written in English, as in the rest of the repository. The prose around the code is
Russian.

`node tools/course-snippets.mjs` (needs JDK 21; Gradle is the wrapper of this repository) writes every `run`,
`starter`, `test` and `check` block into a scratch Gradle project with the Kotlin version of the site, compiles it,
runs the programs and compares their output with the `output` blocks, and runs the tests. Options: `--only <lesson>`,
`--work <dir>`, `--offline`.

The Kotlin Playground (JetBrains) is loaded from unpkg at a pinned version and needs a network; without it the code
stays readable and can be copied. The playground uses a newer Kotlin than the site (the tool uses the site's), so
examples must compile on both.

## Adding a lesson

1. Write `course/content/<id>.md` with exactly one `#` heading.
2. Add it to `course/content/manifest.json` (id, title, minutes, summary).
3. Run both tools. Fix what they report.
