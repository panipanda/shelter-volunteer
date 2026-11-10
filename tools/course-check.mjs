// Validates the course in course/: the lesson list, every lesson's Markdown, its quizzes, its links into the
// repository, and the rule that code comments are written in English.
//
// Usage: node tools/course-check.mjs [--allow-missing]
//   --allow-missing   a lesson listed in the manifest may still lack its file (while the course is being written)

import fs from "node:fs";
import path from "node:path";
import { execSync } from "node:child_process";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const courseDir = path.join(root, "course");
const markdown = require(path.join(courseDir, "assets", "markdown.js"));
const allowMissing = process.argv.includes("--allow-missing");

const errors = [];
const warnings = [];
const fail = (where, message) => errors.push(`${where}: ${message}`);
const warn = (where, message) => warnings.push(`${where}: ${message}`);

const manifest = JSON.parse(fs.readFileSync(path.join(courseDir, "content", "manifest.json"), "utf8"));
const lessons = manifest.parts.flatMap((part) => part.lessons.map((lesson) => ({ ...lesson, part: part.title })));
const ids = new Set();
for (const lesson of lessons) {
  if (ids.has(lesson.id)) fail("manifest", `duplicate lesson id ${lesson.id}`);
  ids.add(lesson.id);
  for (const key of ["title", "minutes", "summary"]) {
    if (!lesson[key]) fail(`manifest/${lesson.id}`, `missing ${key}`);
  }
}

// A comment in code must not contain Cyrillic: the project keeps its code comments in English.
const COMMENT_CHECKED = new Set(["kotlin", "run", "starter", "test", "demo", "js", "javascript", "css", "sql", "bash", "yaml", "properties", "dockerfile"]);
const CYRILLIC = /[А-Яа-яЁё]/;

function commentsWithCyrillic(lang, code) {
  const found = [];
  const lines = code.split("\n");
  let inBlock = false;
  lines.forEach((line, index) => {
    let comment = null;
    if (lang === "css") {
      if (inBlock || line.includes("/*")) comment = line;
      if (line.includes("/*")) inBlock = !line.includes("*/");
      else if (inBlock && line.includes("*/")) inBlock = false;
    } else if (lang === "bash" || lang === "yaml" || lang === "properties" || lang === "dockerfile") {
      const match = /(^|\s)#(.*)$/.exec(line);
      if (match) comment = match[2];
    } else if (lang === "sql") {
      const match = /--(.*)$/.exec(line);
      if (match) comment = match[1];
    } else {
      // Kotlin and JavaScript: a // comment that is not inside a string, or a /* ... */ block.
      const withoutStrings = line.replace(/"(?:[^"\\]|\\.)*"/g, '""');
      const match = /\/\/(.*)$/.exec(withoutStrings);
      if (match) comment = match[1];
      if (inBlock || /\/\*/.test(withoutStrings)) {
        comment = (comment ?? "") + line;
        if (/\/\*/.test(withoutStrings)) inBlock = !/\*\//.test(withoutStrings);
        else if (/\*\//.test(withoutStrings)) inBlock = false;
      }
    }
    if (comment && CYRILLIC.test(comment)) found.push(index + 1);
  });
  return found;
}

// A link into the repository must lead to a file that is committed: a file that only exists on this computer
// (or is git-ignored) would be a dead link on GitHub. Outside a git checkout the check falls back to the disk.
let tracked = null;
try {
  tracked = new Set(execSync("git ls-files", { cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] }).split("\n").filter(Boolean));
} catch (error) {
  tracked = null;
}

const repoFileCache = new Map();
function repoPathExists(repoPath) {
  if (!repoFileCache.has(repoPath)) {
    const clean = repoPath.replace(/\/$/, "");
    const exists = tracked
      ? tracked.has(clean) || [...tracked].some((file) => file.startsWith(clean + "/"))
      : fs.existsSync(path.join(root, clean));
    repoFileCache.set(repoPath, exists);
  }
  return repoFileCache.get(repoPath);
}

const allLinks = [];
const stats = { lessons: 0, words: 0, tasks: 0, questions: 0, lives: 0, runBlocks: 0, testBlocks: 0 };

for (const lesson of lessons) {
  const file = path.join(courseDir, "content", `${lesson.id}.md`);
  const where = `content/${lesson.id}.md`;
  if (!fs.existsSync(file)) {
    (allowMissing ? warn : fail)(where, "the lesson file is missing");
    continue;
  }
  const source = fs.readFileSync(file, "utf8");
  let result;
  try {
    result = markdown.render(source);
  } catch (error) {
    fail(where, `the renderer crashed: ${error.message}`);
    continue;
  }
  stats.lessons++;
  stats.words += result.words;
  stats.tasks += result.tasks.length;
  stats.questions += result.questions.length;
  stats.lives += result.lives;

  if (!result.title) fail(where, "no level-1 heading");
  if ((result.html.match(/<h1[ >]/g) || []).length !== 1) fail(where, "a lesson needs exactly one level-1 heading");
  if (result.questions.length < 4 && lesson.id !== "cheatsheet" && lesson.id !== "intro") {
    fail(where, `only ${result.questions.length} quiz questions, at least 4 expected`);
  }
  if (result.tasks.length < 1 && !["cheatsheet", "exam", "intro"].includes(lesson.id)) {
    fail(where, "no practice task");
  }
  if (result.lives < 1 && !["cheatsheet", "exam", "intro", "capstone"].includes(lesson.id)) {
    fail(where, "no live coding section");
  }
  if (result.words < 700 && lesson.id !== "cheatsheet") warn(where, `short lesson: ${result.words} words`);

  result.questions.forEach((question) => {
    const label = `${where} quiz ${question.quiz} question ${question.number}`;
    if (question.optionCount < 2) fail(label, "fewer than two options");
    if (question.correctCount < 1) fail(label, "no correct option marked");
    if (question.correctCount === question.optionCount) fail(label, "every option is marked correct");
    if (!question.hasExplanation) fail(label, "no explanation (E:)");
  });

  // A task must come with a solution; check by looking at the rendered HTML around it.
  const taskBlocks = result.html.split('<section class="task"').slice(1);
  taskBlocks.forEach((block, index) => {
    if (!block.includes('class="solution"')) fail(`${where} task ${index + 1}`, "no :::solution block");
  });

  result.codeBlocks.forEach((block) => {
    if (COMMENT_CHECKED.has(block.lang)) {
      commentsWithCyrillic(block.lang, block.code).forEach((line) =>
        fail(`${where} (${block.lang} block at line ${block.line + 1}, code line ${line})`, "a code comment contains Cyrillic; comments in code are written in English"));
    }
    if (block.lang === "run") {
      stats.runBlocks++;
      if (!/fun\s+main\s*\(/.test(block.code)) fail(`${where} (run block at line ${block.line + 1})`, "a run block needs fun main()");
      if (block.expectedOutput === null) warn(`${where} (run block at line ${block.line + 1})`, "no output block after it, so the output is not verified");
    }
    if (block.lang === "test") {
      stats.testBlocks++;
      if (!/@Test/.test(block.code)) fail(`${where} (test block at line ${block.line + 1})`, "a test block needs @Test");
    }
  });

  result.repoLinks.forEach((repoPath) => {
    if (!repoPathExists(repoPath)) fail(where, `link to a file that is not in the repository: ${repoPath}`);
  });

  for (const match of source.matchAll(/\]\(lesson:([a-z0-9-]+)(#[^)]*)?\)/g)) {
    if (!ids.has(match[1])) fail(where, `link to an unknown lesson: ${match[1]}`);
  }
  const headingIds = new Set(result.headings.map((h) => h.id));
  allLinks.push({ where, source, headingIds, lessonId: lesson.id });
}

const orphans = fs.readdirSync(path.join(courseDir, "content"))
  .filter((name) => name.endsWith(".md") && name !== "home.md" && !ids.has(name.replace(/\.md$/, "")));
orphans.forEach((name) => warn(`content/${name}`, "not listed in the manifest"));

for (const message of warnings) console.warn("warning:", message);
for (const message of errors) console.error("error:", message);
console.log(`\n${stats.lessons} lessons, ${stats.words} words, ${stats.tasks} tasks, ${stats.questions} quiz questions, ` +
  `${stats.lives} live-coding sections, ${stats.runBlocks} run blocks, ${stats.testBlocks} test blocks.`);
if (errors.length > 0) {
  console.error(`${errors.length} error(s).`);
  process.exit(1);
}
console.log("The course is consistent.");
