// Compiles and runs the Kotlin code of the course with a real Kotlin compiler (the version the project uses), so that
// a lesson never shows code that does not compile or prints something else than the lesson says.
//
//   run      blocks are compiled and executed; the output must equal the following "output" block (when there is one)
//   starter  blocks are compiled only (they are exercises that still contain TODO())
//   test     blocks are compiled and run as kotlin.test tests, all of which must pass
//
// Usage: node tools/course-snippets.mjs [--only <lesson-id>] [--work <directory>] [--offline]

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const require = createRequire(import.meta.url);
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const courseDir = path.join(root, "course");
const markdown = require(path.join(courseDir, "assets", "markdown.js"));

const args = process.argv.slice(2);
const option = (name) => (args.includes(name) ? args[args.indexOf(name) + 1] : null);
const only = option("--only");
const workDir = path.resolve(option("--work") ?? path.join(os.tmpdir(), "course-snippets"));
const offline = args.includes("--offline");

const manifest = JSON.parse(fs.readFileSync(path.join(courseDir, "content", "manifest.json"), "utf8"));
const lessons = manifest.parts.flatMap((part) => part.lessons).filter((lesson) => !only || lesson.id === only);

const KOTLIN_VERSION = "2.0.21";
const packageOf = (lessonId, index) => `s_${lessonId.replace(/-/g, "_")}_${index}`;

// ---- collect the snippets

const snippets = [];
for (const lesson of lessons) {
  const file = path.join(courseDir, "content", `${lesson.id}.md`);
  if (!fs.existsSync(file)) continue;
  const result = markdown.render(fs.readFileSync(file, "utf8"));
  result.codeBlocks.forEach((block, index) => {
    // A static block can ask to be checked too: kotlin check="main" is compiled, kotlin check="test" is run as a test.
    let kind = block.lang;
    if (block.lang === "kotlin" && block.check) kind = block.check === "test" ? "test" : "compile";
    if (!["run", "starter", "test", "compile"].includes(kind)) return;
    // A starter with tests is meant to fail until the reader completes it; its solution is a "test" block.
    if (kind === "starter" && /@Test|import kotlin\.test/.test(block.code)) return;
    snippets.push({ lesson: lesson.id, kind, code: block.code, expected: block.expectedOutput, line: block.line + 1, pkg: packageOf(lesson.id, index) });
  });
}
console.log(`${snippets.length} snippets: ${snippets.filter((s) => s.kind === "run").length} run, ${snippets.filter((s) => s.kind === "starter").length} starter, ${snippets.filter((s) => s.kind === "compile").length} compile-only, ${snippets.filter((s) => s.kind === "test").length} test`);

// ---- write the scratch Gradle project

fs.rmSync(workDir, { recursive: true, force: true });
fs.mkdirSync(path.join(workDir, "src", "main", "kotlin"), { recursive: true });
fs.mkdirSync(path.join(workDir, "src", "test", "kotlin"), { recursive: true });

fs.writeFileSync(path.join(workDir, "settings.gradle.kts"), 'rootProject.name = "course-snippets"\n');
fs.writeFileSync(path.join(workDir, "build.gradle.kts"), `
plugins {
    kotlin("jvm") version "${KOTLIN_VERSION}"
    kotlin("plugin.serialization") version "${KOTLIN_VERSION}"
}
repositories { mavenCentral() }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // The same server libraries as the project, so that lesson code about Ktor is checked too.
    listOf("core", "netty", "html-builder", "status-pages", "caching-headers", "compression", "sessions", "auto-head-response").forEach {
        implementation("io.ktor:ktor-server-$it-jvm:2.3.12")
    }
    implementation("ch.qos.logback:logback-classic:1.5.6")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-tests-jvm:2.3.12")
}
tasks.test {
    useJUnitPlatform()
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
tasks.register("printClasspath") {
    doLast { println("CLASSPATH=" + sourceSets["main"].runtimeClasspath.asPath) }
}
`);

function withPackage(snippet) {
  const body = snippet.code.replace(/^\s*package\s+\S+\s*\n/, "");
  return `package ${snippet.pkg}\n\n${body}\n`;
}

for (const snippet of snippets) {
  const isTest = snippet.kind === "test";
  const dir = path.join(workDir, "src", isTest ? "test" : "main", "kotlin", snippet.pkg);
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, isTest ? "SnippetTest.kt" : "Snippet.kt"), withPackage(snippet));
}

// ---- build

const gradle = path.join(root, process.platform === "win32" ? "gradlew.bat" : "gradlew");
function runGradle(tasks) {
  const gradleArgs = ["-p", workDir, "--console=plain", "-q", ...(offline ? ["--offline"] : []), ...tasks];
  return spawnSync(gradle, gradleArgs, { encoding: "utf8", shell: process.platform === "win32", maxBuffer: 64 * 1024 * 1024 });
}

const problems = [];
const describe = (snippet) => `${snippet.lesson} (${snippet.kind} block at line ${snippet.line})`;

console.log("compiling…");
const build = runGradle(["compileKotlin", "compileTestKotlin", "printClasspath"]);
if (build.status !== 0) {
  console.error(build.stdout.split("\n").filter((line) => !line.startsWith("CLASSPATH=")).join("\n"));
  console.error(build.stderr);
  console.error("The snippets do not compile. File names are s_<lesson>_<block index>/Snippet.kt under", path.join(workDir, "src"));
  process.exit(1);
}
const classpath = /CLASSPATH=(.*)/.exec(build.stdout)?.[1]?.trim();
if (!classpath) {
  console.error("Could not read the classpath from Gradle:", build.stdout);
  process.exit(1);
}
const fullClasspath = [path.join(workDir, "build", "classes", "kotlin", "main"), classpath].join(path.delimiter);

// ---- run the main() blocks

const normalize = (text) => text.replace(/\r\n/g, "\n").split("\n").map((line) => line.replace(/\s+$/, "")).join("\n").trim();

for (const snippet of snippets.filter((s) => s.kind === "run")) {
  const run = spawnSync("java", ["-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-cp", fullClasspath, `${snippet.pkg}.SnippetKt`], {
    encoding: "utf8", timeout: 30000, maxBuffer: 16 * 1024 * 1024
  });
  if (run.status !== 0) {
    problems.push(`${describe(snippet)}: exited with ${run.status}\n${run.stderr}`);
    continue;
  }
  if (snippet.expected !== null && normalize(run.stdout) !== normalize(snippet.expected)) {
    problems.push(`${describe(snippet)}: the output differs\n--- the lesson says\n${normalize(snippet.expected)}\n--- the program prints\n${normalize(run.stdout)}`);
  }
}

// ---- run the tests

if (snippets.some((s) => s.kind === "test")) {
  console.log("running tests…");
  const tests = runGradle(["test"]);
  if (tests.status !== 0) {
    problems.push(`tests failed\n${tests.stdout}\n${tests.stderr}`);
  }
}

if (problems.length > 0) {
  problems.forEach((problem) => console.error("\nerror:", problem));
  console.error(`\n${problems.length} problem(s).`);
  process.exit(1);
}
console.log("All snippets compile, run and print what the lessons say.");
