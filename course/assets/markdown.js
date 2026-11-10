/*
 * A small Markdown renderer written for this course. It knows the Markdown the lessons use plus a few "container"
 * blocks for the learning parts (callouts, tasks, quizzes, live coding). No dependencies, so the course needs no
 * build step; the same file runs in the browser and in Node (tools/course-check.mjs uses it to validate the lessons).
 *
 * Containers open with three or more colons and close with at least as many:
 *
 *   :::note Title        callouts: note, tip, warn, project, ai, idea
 *   ::::task Title       a practice task; may contain :::hint and :::solution
 *   ::::live Title       live coding; contains :::step blocks
 *   :::quiz Title        questions: "Q:", options "- [ ]" / "- [x]", explanation "E:"
 *   :::fold Title        a collapsed block
 *
 * Code fences take an info string: the language, then optional attributes such as title="...". Special languages:
 *   run      runnable Kotlin (Kotlin Playground in the browser; compiled by tools/course-snippets.mjs)
 *   starter  runnable Kotlin that is meant to be completed by the reader (compiled only; target="junit" for tests)
 *   test     Kotlin with kotlin.test tests, run by the playground's JUnit target
 *   output   the expected output of the run block above it
 *   demo     an editable HTML/CSS/JS page with a live preview in a sandboxed frame (height="300")
 * A static block can carry check="main" or check="test": the tool then compiles it, or runs it as a test.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.CourseMarkdown = factory();
  }
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var CALLOUTS = {
    note: 'Заметка',
    tip: 'Совет',
    warn: 'Осторожно',
    project: 'В проекте',
    ai: 'Спросите агента',
    idea: 'Подумайте'
  };

  var LANG_LABELS = {
    kotlin: 'Kotlin', html: 'HTML', css: 'CSS', js: 'JavaScript', javascript: 'JavaScript', sql: 'SQL',
    bash: 'Shell', json: 'JSON', yaml: 'YAML', properties: 'properties', text: 'Текст', markdown: 'Markdown',
    dockerfile: 'Dockerfile', diff: 'diff', xml: 'XML'
  };

  var DEFAULT_REPO = 'https://github.com/panipanda/shelter-volunteer/';

  function escapeHtml(text) {
    return String(text)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function slugify(text) {
    var slug = String(text)
      .toLowerCase()
      .replace(/[^\p{L}\p{N}]+/gu, '-')
      .replace(/^-+|-+$/g, '');
    return slug || 'section';
  }

  // ---------------------------------------------------------------- inline

  function linkTarget(href, env) {
    if (href.indexOf('lesson:') === 0) {
      var rest = href.slice('lesson:'.length);
      var hash = rest.indexOf('#');
      return hash === -1 ? { href: '#/' + rest } : { href: '#/' + rest.slice(0, hash) + '/' + rest.slice(hash + 1) };
    }
    if (href.indexOf('repo:') === 0) {
      var path = href.slice('repo:'.length);
      var kind = /\/$/.test(path) ? 'tree/main/' : 'blob/main/';
      return { href: (env.repoBase || DEFAULT_REPO) + kind + path, external: true, repoPath: path };
    }
    if (/^https?:\/\//.test(href)) {
      return { href: href, external: true };
    }
    return { href: href };
  }

  function inline(source, env) {
    var stash = [];
    var text = source.replace(/(`+)([\s\S]*?[^`])\1(?!`)/g, function (match, ticks, code) {
      stash.push('<code>' + escapeHtml(code.trim()) + '</code>');
      return '\u0000' + (stash.length - 1) + '\u0000';
    });

    text = escapeHtml(text);

    text = text.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, function (match, label, href) {
      var target = linkTarget(href.replace(/&amp;/g, '&'), env);
      if (target.repoPath && env.repoLinks) {
        env.repoLinks.push(target.repoPath);
      }
      if (target.external) {
        return '<a href="' + escapeHtml(target.href) + '" target="_blank" rel="noopener noreferrer">' + label + '</a>';
      }
      return '<a href="' + escapeHtml(target.href) + '">' + label + '</a>';
    });

    text = text.replace(/\*\*(?=\S)([^*\n]+?)(?<=\S)\*\*/g, '<strong>$1</strong>');
    text = text.replace(/(?<![\p{L}\p{N}*])\*(?=[^\s*])([^*\n]+?)(?<=[^\s*])\*(?![\p{L}\p{N}*])/gu, '<em>$1</em>');

    return text.replace(/\u0000(\d+)\u0000/g, function (match, index) {
      return stash[Number(index)];
    });
  }

  // ---------------------------------------------------------------- tokenizer

  var FENCE = /^(`{3,})\s*(.*)$/;
  var CONTAINER_OPEN = /^(:{3,})\s*([a-z]+)\s*(.*)$/;
  var CONTAINER_CLOSE = /^(:{3,})\s*$/;
  var HEADING = /^(#{1,4})\s+(.*?)\s*#*\s*$/;
  var LIST_ITEM = /^(\s*)([-*]|\d+[.)])\s+(.*)$/;
  var TABLE_SEPARATOR = /^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$/;

  function isBlank(line) {
    return /^\s*$/.test(line);
  }

  function startsBlock(line) {
    return FENCE.test(line) || CONTAINER_OPEN.test(line) || HEADING.test(line) || /^>/.test(line) || LIST_ITEM.test(line) || /^-{3,}\s*$/.test(line);
  }

  function findFenceEnd(lines, start, ticks) {
    for (var i = start + 1; i < lines.length; i++) {
      var match = /^(`{3,})\s*$/.exec(lines[i]);
      if (match && match[1].length >= ticks) {
        return i;
      }
    }
    return lines.length;
  }

  function findContainerEnd(lines, start, colons) {
    var fence = 0;
    for (var i = start + 1; i < lines.length; i++) {
      var open = FENCE.exec(lines[i]);
      if (fence) {
        var close = /^(`{3,})\s*$/.exec(lines[i]);
        if (close && close[1].length >= fence) {
          fence = 0;
        }
        continue;
      }
      if (open) {
        fence = open[1].length;
        continue;
      }
      var end = CONTAINER_CLOSE.exec(lines[i]);
      if (end && end[1].length >= colons) {
        return i;
      }
    }
    return lines.length;
  }

  function parseAttributes(text) {
    var attributes = {};
    var pattern = /(\w+)="([^"]*)"/g;
    var match;
    while ((match = pattern.exec(text))) {
      attributes[match[1]] = match[2];
    }
    return attributes;
  }

  function tokenize(lines) {
    var tokens = [];
    var i = 0;
    while (i < lines.length) {
      var line = lines[i];
      if (isBlank(line)) {
        i++;
        continue;
      }

      var fence = FENCE.exec(line);
      if (fence) {
        var end = findFenceEnd(lines, i, fence[1].length);
        tokens.push({ type: 'code', info: fence[2], code: lines.slice(i + 1, end).join('\n'), line: i });
        i = end + 1;
        continue;
      }

      var open = CONTAINER_OPEN.exec(line);
      if (open) {
        var closeAt = findContainerEnd(lines, i, open[1].length);
        tokens.push({ type: 'container', name: open[2], title: open[3].trim(), lines: lines.slice(i + 1, closeAt) });
        i = closeAt + 1;
        continue;
      }

      var heading = HEADING.exec(line);
      if (heading) {
        tokens.push({ type: 'heading', level: heading[1].length, text: heading[2] });
        i++;
        continue;
      }

      if (/^-{3,}\s*$/.test(line)) {
        tokens.push({ type: 'hr' });
        i++;
        continue;
      }

      if (/^>/.test(line)) {
        var quote = [];
        while (i < lines.length && /^>/.test(lines[i])) {
          quote.push(lines[i].replace(/^>\s?/, ''));
          i++;
        }
        tokens.push({ type: 'quote', children: tokenize(quote) });
        continue;
      }

      if (line.indexOf('|') !== -1 && i + 1 < lines.length && TABLE_SEPARATOR.test(lines[i + 1]) && lines[i + 1].indexOf('-') !== -1) {
        var rows = [line];
        i += 2;
        while (i < lines.length && lines[i].indexOf('|') !== -1 && !isBlank(lines[i])) {
          rows.push(lines[i]);
          i++;
        }
        tokens.push({ type: 'table', rows: rows.map(splitRow) });
        continue;
      }

      if (LIST_ITEM.test(line)) {
        var list = readList(lines, i);
        tokens.push(list.token);
        i = list.next;
        continue;
      }

      var paragraph = [];
      while (i < lines.length && !isBlank(lines[i]) && (paragraph.length === 0 || !startsBlock(lines[i]))) {
        paragraph.push(lines[i].trim());
        i++;
      }
      tokens.push({ type: 'paragraph', text: paragraph.join(' ') });
    }
    return tokens;
  }

  function splitRow(row) {
    var trimmed = row.trim().replace(/^\|/, '').replace(/\|$/, '');
    var cells = [];
    var current = '';
    var inCode = false;
    for (var i = 0; i < trimmed.length; i++) {
      var ch = trimmed[i];
      if (ch === '`') {
        inCode = !inCode;
      }
      if (ch === '|' && !inCode && trimmed[i - 1] !== '\\') {
        cells.push(current.trim());
        current = '';
      } else {
        current += ch;
      }
    }
    cells.push(current.trim());
    return cells.map(function (cell) { return cell.replace(/\\\|/g, '|'); });
  }

  function readList(lines, start) {
    var first = LIST_ITEM.exec(lines[start]);
    var baseIndent = first[1].length;
    var ordered = /\d/.test(first[2]);
    var items = [];
    var current = null;
    var i = start;

    while (i < lines.length) {
      var line = lines[i];
      if (isBlank(line)) {
        var j = i;
        while (j < lines.length && isBlank(lines[j])) {
          j++;
        }
        var follow = lines[j];
        var continues = follow !== undefined && (/^\s{2,}\S/.test(follow) || (LIST_ITEM.test(follow) && LIST_ITEM.exec(follow)[1].length === baseIndent));
        if (!continues || !current) {
          break;
        }
        for (var k = i; k < j; k++) {
          current.push('');
        }
        i = j;
        continue;
      }

      var item = LIST_ITEM.exec(line);
      if (item && item[1].length === baseIndent) {
        if (/\d/.test(item[2]) !== ordered) {
          break;
        }
        current = [item[3]];
        items.push(current);
        i++;
        continue;
      }
      if (/^\s{2,}\S/.test(line) && current) {
        current.push(line.slice(Math.min(baseIndent + 2, line.length - line.trimStart().length)));
        i++;
        continue;
      }
      break;
    }

    return {
      token: { type: 'list', ordered: ordered, items: items.map(function (itemLines) { return tokenize(itemLines); }) },
      next: i
    };
  }

  // ---------------------------------------------------------------- rendering

  function renderTokens(tokens, env) {
    return tokens.map(function (token) { return renderToken(token, env); }).join('\n');
  }

  function renderToken(token, env) {
    // An "output" block belongs to the run block right before it, with nothing in between.
    if (token.type !== 'code') {
      env.lastCodeToken = null;
    }
    switch (token.type) {
      case 'heading': return renderHeading(token, env);
      case 'paragraph': return '<p>' + inline(token.text, env) + '</p>';
      case 'hr': return '<hr>';
      case 'quote': return '<blockquote>' + renderTokens(token.children, env) + '</blockquote>';
      case 'code': return renderCode(token, env);
      case 'table': return renderTable(token, env);
      case 'list': return renderList(token, env);
      case 'container': return renderContainer(token, env);
      default: return '';
    }
  }

  function renderHeading(token, env) {
    var level = Math.min(token.level, 4);
    var id = slugify(token.text);
    var used = env.usedIds;
    var unique = id;
    var n = 2;
    while (used[unique]) {
      unique = id + '-' + n++;
    }
    used[unique] = true;
    if (level === 1) {
      env.title = env.title || token.text;
      return '<h1 id="' + unique + '">' + inline(token.text, env) + '</h1>';
    }
    if (level === 2) {
      env.headings.push({ id: unique, text: token.text.replace(/`/g, '') });
    }
    return '<h' + level + ' id="' + unique + '">' + inline(token.text, env) + '</h' + level + '>';
  }

  function renderList(token, env) {
    var tag = token.ordered ? 'ol' : 'ul';
    var items = token.items.map(function (children) {
      var simple = children.length === 1 && children[0].type === 'paragraph';
      if (simple) {
        return '<li>' + inline(children[0].text, env) + '</li>';
      }
      var first = children[0];
      if (first && first.type === 'paragraph') {
        return '<li>' + inline(first.text, env) + renderTokens(children.slice(1), env) + '</li>';
      }
      return '<li>' + renderTokens(children, env) + '</li>';
    });
    return '<' + tag + '>' + items.join('') + '</' + tag + '>';
  }

  function renderTable(token, env) {
    var head = token.rows[0];
    var body = token.rows.slice(1);
    var html = '<div class="table-wrap"><table><thead><tr>';
    head.forEach(function (cell) { html += '<th scope="col">' + inline(cell, env) + '</th>'; });
    html += '</tr></thead><tbody>';
    body.forEach(function (row) {
      html += '<tr>';
      row.forEach(function (cell) { html += '<td>' + inline(cell, env) + '</td>'; });
      html += '</tr>';
    });
    return html + '</tbody></table></div>';
  }

  function renderCode(token, env) {
    var match = /^(\S*)\s*(.*)$/.exec(token.info.trim());
    var language = match[1] || 'text';
    var attributes = parseAttributes(match[2]);
    var code = token.code;
    // check="main" or check="test" on a static block asks tools/course-snippets.mjs to compile it (and run it as a test).
    var record = { lang: language, title: attributes.title || '', code: code, expectedOutput: null, line: token.line, check: attributes.check || '' };
    env.codeBlocks.push(record);

    if (language === 'output') {
      var previous = env.codeBlocks[env.codeBlocks.length - 2];
      if (previous && env.lastCodeToken === previous && previous.lang === 'run') {
        previous.expectedOutput = code;
      }
      env.lastCodeToken = null;
      return '<figure class="code code-output"><figcaption>Вывод</figcaption><pre><code>' + escapeHtml(code) + '</code></pre></figure>';
    }
    env.lastCodeToken = record;

    var caption = escapeHtml(attributes.title || LANG_LABELS[language] || language);
    if (language === 'demo') {
      // A page fragment (HTML with its own <style> and <script>) that the reader edits and sees at once, in a sandboxed frame.
      var height = Math.min(Math.max(parseInt(attributes.height, 10) || 280, 120), 700);
      return '<figure class="code code-demo"><figcaption>' + (attributes.title ? caption : 'Живой пример: меняйте код, результат обновляется сразу') + '</figcaption>' +
        '<div class="demo" style="--demo-height:' + height + 'px"><textarea class="demo-source" spellcheck="false" aria-label="Код примера">' + escapeHtml(code) + '</textarea>' +
        '<iframe class="demo-frame" sandbox="allow-scripts" title="Результат примера"></iframe></div></figure>';
    }
    if (language === 'run' || language === 'starter' || language === 'test') {
      env.runnable = true;
      var label = attributes.title ? caption : (language === 'test' ? 'Kotlin: тесты, можно запустить' : 'Kotlin: можно запустить и править');
      var platform = language === 'test' || attributes.target === 'junit' ? 'junit' : 'java';
      return '<figure class="code code-run" data-kind="' + language + '"><figcaption>' + label + '</figcaption>' +
        '<pre><code class="pg-code" data-target-platform="' + platform + '">' + escapeHtml(code) + '</code></pre></figure>';
    }
    return '<figure class="code"><figcaption>' + caption + '</figcaption><pre><code class="language-' + escapeHtml(language) + '">' + escapeHtml(code) + '</code></pre></figure>';
  }

  function containerHtml(token, env) {
    return renderTokens(tokenize(token.lines), env);
  }

  function renderContainer(token, env) {
    var name = token.name;

    if (CALLOUTS[name]) {
      var title = token.title || CALLOUTS[name];
      return '<aside class="callout callout-' + name + '"><p class="callout-title">' + inline(title, env) + '</p>' + containerHtml(token, env) + '</aside>';
    }

    if (name === 'fold') {
      return '<details class="fold"><summary>' + inline(token.title || 'Подробнее', env) + '</summary>' + containerHtml(token, env) + '</details>';
    }

    if (name === 'hint') {
      return '<details class="hint"><summary>' + inline(token.title || 'Подсказка', env) + '</summary>' + containerHtml(token, env) + '</details>';
    }

    if (name === 'solution') {
      return '<details class="solution"><summary>' + inline(token.title || 'Показать решение', env) + '</summary>' + containerHtml(token, env) + '</details>';
    }

    if (name === 'task') {
      env.taskCount++;
      var number = env.taskCount;
      env.tasks.push({ number: number, title: token.title });
      return '<section class="task" id="task-' + number + '"><h3 class="task-title"><span class="task-label">Задание ' + number + '</span> ' +
        inline(token.title, env) + '</h3>' + containerHtml(token, env) + '</section>';
    }

    if (name === 'live') {
      return renderLive(token, env);
    }

    if (name === 'step') {
      return '<li class="live-step"><h4 class="live-step-title">' + inline(token.title, env) + '</h4>' + containerHtml(token, env) + '</li>';
    }

    if (name === 'quiz') {
      return renderQuiz(token, env);
    }

    return '<div class="container container-' + escapeHtml(name) + '">' + containerHtml(token, env) + '</div>';
  }

  function renderLive(token, env) {
    env.liveCount++;
    var children = tokenize(token.lines);
    var intro = [];
    var steps = [];
    children.forEach(function (child) {
      if (child.type === 'container' && child.name === 'step') {
        steps.push(child);
      } else if (steps.length === 0) {
        intro.push(child);
      }
    });
    env.liveSteps += steps.length;
    return '<section class="live" id="live-' + env.liveCount + '">' +
      '<header class="live-head"><span class="live-badge">Live coding</span><h3>' + inline(token.title, env) + '</h3></header>' +
      renderTokens(intro, env) +
      '<ol class="live-steps">' + steps.map(function (step) { return renderContainer(step, env); }).join('') + '</ol></section>';
  }

  // ---------------------------------------------------------------- quiz

  function parseQuiz(lines) {
    var questions = [];
    var chunk = null;
    var fence = 0;

    lines.forEach(function (line) {
      var open = FENCE.exec(line);
      if (fence) {
        var close = /^(`{3,})\s*$/.exec(line);
        if (close && close[1].length >= fence) {
          fence = 0;
        }
      } else if (open) {
        fence = open[1].length;
      }
      var start = !fence && !open && /^Q:\s?/.exec(line);
      if (start) {
        chunk = [line.replace(/^Q:\s?/, '')];
        questions.push(chunk);
      } else if (chunk) {
        chunk.push(line);
      }
    });

    return questions.map(function (chunkLines) {
      var text = [];
      var options = [];
      var explanation = [];
      var mode = 'text';
      var currentFence = 0;

      chunkLines.forEach(function (line) {
        var fenceOpen = FENCE.exec(line);
        if (currentFence) {
          var fenceClose = /^(`{3,})\s*$/.exec(line);
          if (fenceClose && fenceClose[1].length >= currentFence) {
            currentFence = 0;
          }
        } else if (fenceOpen) {
          currentFence = fenceOpen[1].length;
        }
        var option = !currentFence && !fenceOpen && /^- \[( |x|X)\]\s+(.*)$/.exec(line);
        if (option) {
          mode = 'options';
          options.push({ correct: option[1] !== ' ', text: [option[2]] });
        } else if (!currentFence && /^E:\s?/.test(line)) {
          mode = 'explanation';
          explanation.push(line.replace(/^E:\s?/, ''));
        } else if (mode === 'text') {
          text.push(line);
        } else if (mode === 'options') {
          if (!isBlank(line)) {
            options[options.length - 1].text.push(line.trim());
          }
        } else {
          explanation.push(line);
        }
      });

      return { text: text, options: options, explanation: explanation };
    });
  }

  function renderQuiz(token, env) {
    env.quizCount++;
    var quizId = env.quizCount;
    var questions = parseQuiz(token.lines);
    var html = '<section class="quiz" data-quiz="' + quizId + '"><header class="quiz-head"><span class="quiz-badge">Квиз</span><h3>' +
      inline(token.title || 'Проверьте себя', env) + '</h3></header>';

    questions.forEach(function (question, index) {
      env.questions.push({
        quiz: quizId,
        number: index + 1,
        optionCount: question.options.length,
        correctCount: question.options.filter(function (o) { return o.correct; }).length,
        hasExplanation: question.explanation.join('').trim() !== '',
        text: question.text.join(' ')
      });
      env.questionCount++;
      var id = 'q' + env.questionCount;
      var correct = [];
      question.options.forEach(function (option, optionIndex) {
        if (option.correct) {
          correct.push(optionIndex);
        }
      });
      var multi = correct.length > 1;
      html += '<div class="question" data-question="' + env.questionCount + '" data-answer="' + correct.join(',') + '" data-multi="' + (multi ? '1' : '0') + '">';
      html += '<div class="q-text" id="' + id + '-text"><span class="q-num">Вопрос ' + (index + 1) + '.</span> ' +
        renderTokens(tokenize(question.text), env).replace(/^<p>/, '<span class="q-lead">').replace(/<\/p>/, '</span>') + '</div>';
      if (multi) {
        html += '<p class="q-hint">Верных ответов несколько.</p>';
      }
      html += '<ul class="options" role="group" aria-labelledby="' + id + '-text">';
      question.options.forEach(function (option, optionIndex) {
        html += '<li><label class="option"><input type="' + (multi ? 'checkbox' : 'radio') + '" name="' + id + '" value="' + optionIndex + '">' +
          '<span class="option-text">' + inline(option.text.join(' '), env) + '</span></label></li>';
      });
      html += '</ul><div class="q-actions"><button type="button" class="btn q-check">Проверить</button></div>';
      html += '<div class="q-explain" role="status" hidden>' + renderTokens(tokenize(question.explanation), env) + '</div></div>';
    });

    return html + '</section>';
  }

  // ---------------------------------------------------------------- entry points

  function render(markdown, options) {
    var env = {
      repoBase: (options && options.repoBase) || DEFAULT_REPO,
      repoLinks: [],
      usedIds: {},
      headings: [],
      codeBlocks: [],
      lastCodeToken: null,
      title: '',
      runnable: false,
      taskCount: 0,
      tasks: [],
      liveCount: 0,
      liveSteps: 0,
      quizCount: 0,
      questionCount: 0,
      questions: []
    };
    var lines = String(markdown).replace(/\r\n?/g, '\n').split('\n');
    var html = renderTokens(tokenize(lines), env);
    var prose = String(markdown).replace(/```[\s\S]*?```/g, ' ');
    var words = (prose.match(/[\p{L}\p{N}]+/gu) || []).length;
    return {
      html: html,
      title: env.title,
      headings: env.headings,
      codeBlocks: env.codeBlocks,
      repoLinks: env.repoLinks,
      runnable: env.runnable,
      tasks: env.tasks,
      lives: env.liveCount,
      liveSteps: env.liveSteps,
      questions: env.questions,
      words: words
    };
  }

  return { render: render, inline: function (text) { return inline(text, { repoBase: DEFAULT_REPO }); }, escapeHtml: escapeHtml, slugify: slugify };
});
