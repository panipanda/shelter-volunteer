/*
 * The course shell: loads the lesson list, renders a lesson from Markdown, and makes the learning parts work
 * (quizzes, step-by-step live coding, runnable Kotlin, progress saved in the browser). No build step.
 */
(function () {
  'use strict';

  var PLAYGROUND_SRC = 'https://unpkg.com/kotlin-playground@1.34.0/dist/playground.min.js';
  var STORE_KEY = 'kotlin-web-course:v1';
  var LANGUAGE_ALIASES = { js: 'javascript', properties: 'ini', dockerfile: 'bash', text: 'plaintext' };

  var contentEl = document.getElementById('content');
  var sidebarEl = document.getElementById('sidebar');
  var progressEl = document.getElementById('topbar-progress');
  var menuButton = document.getElementById('menu-button');
  var themeButton = document.getElementById('theme-button');
  var scrim = document.getElementById('scrim');

  var manifest = null;
  var lessons = [];
  var byId = {};
  var cache = {};
  var currentId = null;
  var renderToken = 0;
  var progress = loadProgress();

  // ------------------------------------------------------------ storage (may be blocked: always wrapped)

  function loadProgress() {
    var fallback = { done: {}, quiz: {} };
    try {
      var stored = JSON.parse(localStorage.getItem(STORE_KEY));
      if (stored && typeof stored === 'object') {
        return { done: stored.done || {}, quiz: stored.quiz || {} };
      }
    } catch (e) { /* storage is blocked or the value is broken */ }
    return fallback;
  }

  function saveProgress() {
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify(progress));
    } catch (e) { /* the progress just will not survive a reload */ }
  }

  // ------------------------------------------------------------ small helpers

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) { node.className = className; }
    if (text !== undefined) { node.textContent = text; }
    return node;
  }

  function all(root, selector) {
    return Array.prototype.slice.call(root.querySelectorAll(selector));
  }

  function plural(n, one, few, many) {
    var mod10 = n % 10;
    var mod100 = n % 100;
    if (mod10 === 1 && mod100 !== 11) { return one; }
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) { return few; }
    return many;
  }

  function fetchText(url) {
    if (cache[url]) { return Promise.resolve(cache[url]); }
    return fetch(url).then(function (response) {
      if (!response.ok) { throw new Error(url + ': ' + response.status); }
      return response.text();
    }).then(function (text) {
      cache[url] = text;
      return text;
    });
  }

  function isDark() {
    return document.documentElement.dataset.theme === 'dark';
  }

  // ------------------------------------------------------------ course structure

  function loadManifest() {
    return fetchText('content/manifest.json').then(function (text) {
      manifest = JSON.parse(text);
      manifest.parts.forEach(function (part) {
        part.lessons.forEach(function (lesson) {
          lesson.part = part.title;
          lessons.push(lesson);
          byId[lesson.id] = lesson;
        });
      });
    });
  }

  function doneCount() {
    return lessons.filter(function (lesson) { return progress.done[lesson.id]; }).length;
  }

  function renderSidebar() {
    var html = '';
    manifest.parts.forEach(function (part) {
      html += '<h2>' + CourseMarkdown.escapeHtml(part.title) + '</h2><ul>';
      part.lessons.forEach(function (lesson) {
        var done = progress.done[lesson.id];
        html += '<li><a href="#/' + lesson.id + '" data-id="' + lesson.id + '"' + (done ? ' class="is-done"' : '') + '>' +
          '<span class="tick" aria-hidden="true"></span><span>' + CourseMarkdown.escapeHtml(lesson.title) +
          (done ? '<span class="visually-hidden"> (пройден)</span>' : '') + '</span>' +
          '<span class="minutes">' + lesson.minutes + ' мин</span></a></li>';
      });
      html += '</ul>';
    });
    sidebarEl.innerHTML = html;
    markCurrentInSidebar();
    renderTopProgress();
  }

  function markCurrentInSidebar() {
    all(sidebarEl, 'a').forEach(function (link) {
      if (link.dataset.id === currentId) {
        link.setAttribute('aria-current', 'page');
      } else {
        link.removeAttribute('aria-current');
      }
    });
  }

  function renderTopProgress() {
    progressEl.textContent = 'Пройдено ' + doneCount() + ' из ' + lessons.length;
  }

  function refreshProgressViews() {
    renderSidebar();
    renderTopProgress();
  }

  // ------------------------------------------------------------ routing

  function parseRoute() {
    var hash = location.hash;
    if (hash && hash.indexOf('#/') !== 0) {
      return null;
    }
    var parts = hash.replace(/^#\/?/, '').split('/');
    return { id: parts[0] || '', anchor: parts[1] ? decodeURIComponent(parts[1]) : '' };
  }

  function route() {
    var target = parseRoute();
    if (!target) { return; }
    closeMenu();
    if (target.id && target.id === currentId && target.anchor) {
      scrollToAnchor(target.anchor);
      return;
    }
    if (!target.id) {
      showHome();
    } else if (byId[target.id]) {
      showLesson(target.id, target.anchor);
    } else {
      showMessage('Такого урока нет', 'Проверьте адрес или откройте <a href="#/">оглавление</a>.');
    }
  }

  function scrollToAnchor(anchor) {
    var node = document.getElementById(anchor);
    if (node) {
      node.scrollIntoView();
    }
  }

  function showMessage(title, html) {
    currentId = null;
    markCurrentInSidebar();
    contentEl.innerHTML = '<h1>' + CourseMarkdown.escapeHtml(title) + '</h1><p>' + html + '</p>';
    document.title = title + ' · Курс';
  }

  function showError(error) {
    currentId = null;
    contentEl.innerHTML = '<div class="error-box"><h1>Не удалось загрузить страницу</h1><p>' + CourseMarkdown.escapeHtml(error.message) +
      '</p><p>Если вы открыли файл прямо с диска, запустите маленький сервер: <code>python -m http.server</code> в папке <code>course</code> и откройте <code>http://localhost:8000</code>.</p></div>';
  }

  // ------------------------------------------------------------ pages

  function showHome() {
    var token = ++renderToken;
    currentId = null;
    markCurrentInSidebar();
    fetchText('content/home.md').then(function (markdown) {
      if (token !== renderToken) { return; }
      var result = CourseMarkdown.render(markdown, { repoBase: manifest.repo });
      var first = lessons.filter(function (lesson) { return !progress.done[lesson.id]; })[0] || lessons[0];
      var done = doneCount();
      var percent = Math.round(done * 100 / lessons.length);
      var html = '<div class="hero">' + result.html + '<div class="hero-actions"><a class="btn" href="#/' + first.id + '">' +
        (done === 0 ? 'Начать курс' : 'Продолжить: ' + CourseMarkdown.escapeHtml(first.title)) + '</a>' +
        '<a class="btn btn-secondary" href="' + manifest.repo + '">Репозиторий проекта</a></div>' +
        '<div class="progress-track" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="' + percent + '" aria-label="Прогресс по курсу"><div class="progress-fill" style="width:' + percent + '%"></div></div>' +
        '<p>Пройдено уроков: ' + done + ' из ' + lessons.length + '. Прогресс хранится только в вашем браузере.</p></div>';
      html += '<div class="curriculum">';
      manifest.parts.forEach(function (part) {
        html += '<h2 id="' + CourseMarkdown.slugify(part.title) + '">' + CourseMarkdown.escapeHtml(part.title) + '</h2>';
        if (part.about) { html += '<p>' + CourseMarkdown.inline(part.about) + '</p>'; }
        html += '<ul>';
        part.lessons.forEach(function (lesson) {
          html += '<li><a href="#/' + lesson.id + '"' + (progress.done[lesson.id] ? ' class="is-done"' : '') + '><span class="tick" aria-hidden="true"></span><span><span class="title">' +
            CourseMarkdown.escapeHtml(lesson.title) + '</span><span class="summary">' + CourseMarkdown.escapeHtml(lesson.summary || '') + '</span></span>' +
            '<span class="minutes">' + lesson.minutes + ' мин</span></a></li>';
        });
        html += '</ul>';
      });
      html += '</div>';
      contentEl.innerHTML = html;
      document.title = 'Kotlin и веб-разработка на живом проекте';
      afterRender(contentEl, null);
      focusHeading();
      window.scrollTo(0, 0);
    }).catch(showError);
  }

  function showLesson(id, anchor) {
    var token = ++renderToken;
    var lesson = byId[id];
    fetchText('content/' + id + '.md').then(function (markdown) {
      if (token !== renderToken) { return; }
      currentId = id;
      var result = CourseMarkdown.render(markdown, { repoBase: manifest.repo });
      var index = lessons.indexOf(lesson);
      var meta = '<p class="lesson-meta"><span class="part">' + CourseMarkdown.escapeHtml(lesson.part) + '</span><span>≈ ' + lesson.minutes + ' мин</span>' +
        (result.tasks.length ? '<span>' + result.tasks.length + ' ' + plural(result.tasks.length, 'задание', 'задания', 'заданий') + '</span>' : '') +
        (result.questions.length ? '<span>' + result.questions.length + ' ' + plural(result.questions.length, 'вопрос', 'вопроса', 'вопросов') + ' в квизе</span>' : '') +
        (result.lives ? '<span>live coding: ' + result.lives + '</span>' : '') + '</p>';
      var toc = '';
      if (result.headings.length > 2) {
        toc = '<nav class="toc" aria-label="Разделы урока"><p>В этом уроке</p><ol>' + result.headings.map(function (h) {
          return '<li><a href="#/' + id + '/' + h.id + '">' + CourseMarkdown.escapeHtml(h.text) + '</a></li>';
        }).join('') + '</ol></nav>';
      }
      var html = result.html.replace(/<\/h1>/, '</h1>' + toc);
      html = meta + html;
      html += '<div class="done-bar"><button type="button" class="btn btn-secondary" id="done-button"></button></div>';
      html += '<nav class="lesson-nav" aria-label="Соседние уроки">' +
        (index > 0 ? '<a class="prev" href="#/' + lessons[index - 1].id + '"><span class="dir">← Назад</span>' + CourseMarkdown.escapeHtml(lessons[index - 1].title) + '</a>' : '<span></span>') +
        (index < lessons.length - 1 ? '<a class="next" href="#/' + lessons[index + 1].id + '"><span class="dir">Дальше →</span>' + CourseMarkdown.escapeHtml(lessons[index + 1].title) + '</a>' : '') +
        '</nav>';
      contentEl.innerHTML = html;
      document.title = (result.title || lesson.title) + ' · Курс';
      markCurrentInSidebar();
      afterRender(contentEl, lesson);
      if (anchor) {
        scrollToAnchor(anchor);
      } else {
        window.scrollTo(0, 0);
        focusHeading();
      }
    }).catch(showError);
  }

  function focusHeading() {
    var heading = contentEl.querySelector('h1');
    if (heading) {
      heading.setAttribute('tabindex', '-1');
      heading.focus({ preventScroll: true });
    }
  }

  // ------------------------------------------------------------ enhancing a rendered page

  function afterRender(root, lesson) {
    addAnchors(root);
    highlight(root);
    prepareRunnable(root);
    initDemos(root);
    all(root, '.live').forEach(initLive);
    all(root, '.quiz').forEach(function (quiz) { quiz.appendChild(el('p', 'quiz-score')); });
    if (lesson) {
      updateScores(lesson);
      initDoneButton(lesson);
    }
    addCopyButtons(root);
    initPlaygrounds(root);
  }

  function addAnchors(root) {
    if (!currentId) { return; }
    all(root, 'h2[id], h3[id]').forEach(function (heading) {
      var link = el('a', 'anchor', '#');
      link.href = '#/' + currentId + '/' + heading.id;
      link.setAttribute('aria-label', 'Ссылка на раздел «' + heading.textContent + '»');
      heading.appendChild(link);
    });
  }

  function highlight(root) {
    if (!window.hljs) { return; }
    all(root, 'code[class^="language-"]').forEach(function (code) {
      var language = code.className.replace('language-', '');
      language = LANGUAGE_ALIASES[language] || language;
      if (window.hljs.getLanguage(language)) {
        code.className = 'language-' + language;
        window.hljs.highlightElement(code);
      }
    });
  }

  // The playground takes a <code> element and builds its own editor in its place, so the <pre> around it goes.
  function prepareRunnable(root) {
    all(root, 'code.pg-code').forEach(function (code) {
      var pre = code.parentNode;
      if (pre && pre.tagName === 'PRE') {
        var wrap = el('div', 'pg-wrap');
        wrap.appendChild(code);
        pre.parentNode.replaceChild(wrap, pre);
      }
    });
  }

  function addCopyButtons(root) {
    all(root, 'figure.code').forEach(function (figure) {
      if (figure.classList.contains('code-output')) { return; }
      var caption = figure.querySelector('figcaption');
      var button = el('button', 'copy-button', 'Копировать');
      button.type = 'button';
      button.addEventListener('click', function () {
        var mirror = figure.querySelector('.CodeMirror');
        var text = mirror && mirror.CodeMirror ? mirror.CodeMirror.getValue() : (figure.querySelector('code') || {}).textContent || '';
        var done = function () {
          button.textContent = 'Скопировано';
          setTimeout(function () { button.textContent = 'Копировать'; }, 1500);
        };
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(text).then(done, function () { button.textContent = 'Не вышло'; });
        } else {
          button.textContent = 'Не вышло';
        }
      });
      caption.appendChild(button);
    });
  }

  // ------------------------------------------------------------ live HTML/CSS/JS demos

  // The frame has no allow-same-origin, so whatever the reader types runs in an isolated origin without access to the page.
  function initDemos(root) {
    all(root, '.demo').forEach(function (demo) {
      var source = demo.querySelector('.demo-source');
      var frame = demo.querySelector('.demo-frame');
      var timer = null;
      function paint() { frame.srcdoc = source.value; }
      source.addEventListener('input', function () {
        clearTimeout(timer);
        timer = setTimeout(paint, 250);
      });
      paint();
    });
  }

  // ------------------------------------------------------------ Kotlin Playground

  var playgroundLoading = null;

  function loadPlayground() {
    if (window.KotlinPlayground) { return Promise.resolve(); }
    if (!playgroundLoading) {
      playgroundLoading = new Promise(function (resolve, reject) {
        var script = document.createElement('script');
        script.src = PLAYGROUND_SRC;
        script.onload = resolve;
        script.onerror = function () { reject(new Error('playground')); };
        document.head.appendChild(script);
      });
    }
    return playgroundLoading;
  }

  function isVisible(node) {
    return !!(node.offsetWidth || node.offsetHeight || node.getClientRects().length);
  }

  // Starts the editor on the runnable blocks that can be seen. Blocks in a hidden step or a closed <details> wait
  // until they are shown (an editor built in a hidden box measures itself wrong).
  function initPlaygrounds(root) {
    var pending = all(root, 'code.pg-code').filter(function (code) {
      return !code.dataset.started && isVisible(code);
    });
    if (!pending.length) { return; }
    pending.forEach(function (code) { code.dataset.started = '1'; });
    loadPlayground().then(function () {
      pending.forEach(function (code) {
        code.setAttribute('theme', isDark() ? 'darcula' : 'idea');
        code.setAttribute('data-autocomplete', 'true');
        code.setAttribute('match-brackets', 'true');
        code.setAttribute('highlight-on-fly', 'true');
        window.KotlinPlayground(code);
      });
    }).catch(function () {
      pending.forEach(function (code) {
        var figure = code.closest('figure');
        if (figure && !figure.querySelector('.pg-offline')) {
          figure.appendChild(el('p', 'q-message pg-offline', 'Редактор Kotlin Playground не загрузился (нет сети?). Код можно скопировать и запустить у себя.'));
        }
      });
    });
  }

  document.addEventListener('toggle', function (event) {
    if (event.target.tagName === 'DETAILS' && event.target.open) {
      initPlaygrounds(event.target);
    }
  }, true);

  // ------------------------------------------------------------ live coding stepper

  function initLive(section) {
    var steps = all(section, ':scope > .live-steps > .live-step');
    if (steps.length < 2) { return; }
    var index = 0;
    var showAll = false;

    var controls = el('div', 'live-controls');
    var previous = el('button', 'btn btn-secondary', '← Назад');
    var counter = el('span', 'live-counter');
    var next = el('button', 'btn', 'Дальше →');
    var toggle = el('button', 'btn btn-secondary', 'Показать все шаги');
    [previous, next, toggle].forEach(function (button) { button.type = 'button'; });
    counter.setAttribute('role', 'status');
    controls.appendChild(counter);
    controls.appendChild(previous);
    controls.appendChild(next);
    controls.appendChild(toggle);
    section.appendChild(controls);

    function show() {
      steps.forEach(function (step, i) { step.hidden = !showAll && i !== index; });
      var title = steps[index].querySelector('.live-step-title');
      counter.textContent = showAll ? 'Все шаги (' + steps.length + ')' : 'Шаг ' + (index + 1) + ' из ' + steps.length + (title ? ': ' + title.textContent : '');
      previous.disabled = showAll || index === 0;
      next.disabled = showAll || index === steps.length - 1;
      toggle.textContent = showAll ? 'Идти по шагам' : 'Показать все шаги';
      steps.forEach(function (step) { initPlaygrounds(step); });
    }

    previous.addEventListener('click', function () { index = Math.max(0, index - 1); show(); });
    next.addEventListener('click', function () { index = Math.min(steps.length - 1, index + 1); show(); });
    toggle.addEventListener('click', function () { showAll = !showAll; show(); });
    show();
  }

  // ------------------------------------------------------------ quizzes

  function sameSet(a, b) {
    return a.length === b.length && a.every(function (value) { return b.indexOf(value) !== -1; });
  }

  function lessonQuiz(id) {
    if (!progress.quiz[id]) { progress.quiz[id] = {}; }
    return progress.quiz[id];
  }

  function checkQuestion(question) {
    var inputs = all(question, 'input');
    var selected = inputs.filter(function (input) { return input.checked; }).map(function (input) { return Number(input.value); });
    var message = question.querySelector('.q-message');
    if (!message) {
      message = el('p', 'q-message');
      message.setAttribute('role', 'status');
      question.querySelector('.q-actions').appendChild(message);
    }
    if (!selected.length) {
      message.textContent = 'Сначала выберите ответ.';
      return;
    }
    var answer = question.dataset.answer.split(',').map(Number);
    var correct = sameSet(selected, answer);

    inputs.forEach(function (input) {
      var option = input.closest('.option');
      var index = Number(input.value);
      var isAnswer = answer.indexOf(index) !== -1;
      var verdict = el('span', 'verdict');
      if (input.checked && isAnswer) {
        option.classList.add('is-correct');
        verdict.textContent = '✓ верно';
      } else if (input.checked) {
        option.classList.add('is-wrong');
        verdict.textContent = '✗ неверно';
      } else if (isAnswer) {
        option.classList.add('is-missed');
        verdict.textContent = 'нужно было выбрать';
      }
      if (verdict.textContent) { option.appendChild(verdict); }
      input.disabled = true;
    });

    question.querySelector('.q-explain').hidden = false;
    message.textContent = correct ? 'Верно!' : 'Не совсем. Разбор ниже.';
    var button = question.querySelector('.q-check, .q-retry');
    button.textContent = 'Ещё раз';
    button.className = 'btn btn-secondary q-retry';
    lessonQuiz(currentId)[question.dataset.question] = correct;
    saveProgress();
    updateScores(byId[currentId]);
  }

  function resetQuestion(question) {
    all(question, 'input').forEach(function (input) { input.checked = false; input.disabled = false; });
    all(question, '.option').forEach(function (option) {
      option.classList.remove('is-correct', 'is-wrong', 'is-missed');
      var verdict = option.querySelector('.verdict');
      if (verdict) { verdict.remove(); }
    });
    question.querySelector('.q-explain').hidden = true;
    var button = question.querySelector('.q-retry');
    button.textContent = 'Проверить';
    button.className = 'btn q-check';
    var message = question.querySelector('.q-message');
    if (message) { message.textContent = ''; }
    delete lessonQuiz(currentId)[question.dataset.question];
    saveProgress();
    updateScores(byId[currentId]);
  }

  function updateScores(lesson) {
    if (!lesson) { return; }
    var stored = lessonQuiz(lesson.id);
    var totalAnswered = 0;
    var totalCorrect = 0;
    var totalQuestions = 0;
    all(contentEl, '.quiz').forEach(function (quiz) {
      var questions = all(quiz, '.question');
      var answered = 0;
      var correct = 0;
      questions.forEach(function (question) {
        var value = stored[question.dataset.question];
        if (value !== undefined) {
          answered++;
          if (value) { correct++; }
        }
      });
      totalAnswered += answered;
      totalCorrect += correct;
      totalQuestions += questions.length;
      var score = quiz.querySelector('.quiz-score');
      if (score) {
        score.textContent = answered === 0 ? '' : 'Результат: ' + correct + ' из ' + questions.length + (answered < questions.length ? ' (отвечено ' + answered + ')' : '');
      }
    });
    if (lesson.exam) {
      var total = contentEl.querySelector('.exam-total');
      if (!total) {
        total = el('p', 'quiz-score exam-total');
        var bar = contentEl.querySelector('.done-bar');
        bar.parentNode.insertBefore(total, bar);
      }
      var percent = totalQuestions ? Math.round(totalCorrect * 100 / totalQuestions) : 0;
      total.textContent = totalAnswered === 0 ? '' : 'Итого: ' + totalCorrect + ' из ' + totalQuestions + ' (' + percent + '%), отвечено ' + totalAnswered +
        (totalAnswered === totalQuestions ? '. ' + (percent >= 85 ? 'Отличный результат.' : percent >= 65 ? 'Хорошо: вернитесь к урокам, где были ошибки.' : 'Стоит повторить материал и пройти ещё раз.') : '');
    }
  }

  contentEl.addEventListener('click', function (event) {
    var check = event.target.closest('.q-check');
    if (check) { checkQuestion(check.closest('.question')); return; }
    var retry = event.target.closest('.q-retry');
    if (retry) { resetQuestion(retry.closest('.question')); }
  });

  // ------------------------------------------------------------ "lesson done" button

  function initDoneButton(lesson) {
    var button = document.getElementById('done-button');
    function paint() {
      var done = !!progress.done[lesson.id];
      button.textContent = done ? '✓ Урок пройден (снять отметку)' : 'Отметить урок пройденным';
      button.setAttribute('aria-pressed', done ? 'true' : 'false');
    }
    button.addEventListener('click', function () {
      if (progress.done[lesson.id]) { delete progress.done[lesson.id]; } else { progress.done[lesson.id] = true; }
      saveProgress();
      paint();
      refreshProgressViews();
    });
    paint();
  }

  // ------------------------------------------------------------ menu and theme

  function closeMenu() {
    sidebarEl.classList.remove('is-open');
    menuButton.setAttribute('aria-expanded', 'false');
    scrim.hidden = true;
  }

  menuButton.addEventListener('click', function () {
    var open = !sidebarEl.classList.contains('is-open');
    sidebarEl.classList.toggle('is-open', open);
    menuButton.setAttribute('aria-expanded', open ? 'true' : 'false');
    scrim.hidden = !open;
    if (open) {
      var current = sidebarEl.querySelector('[aria-current="page"]') || sidebarEl.querySelector('a');
      if (current) { current.focus(); }
    }
  });
  scrim.addEventListener('click', closeMenu);
  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && sidebarEl.classList.contains('is-open')) {
      closeMenu();
      menuButton.focus();
    }
  });

  themeButton.addEventListener('click', function () {
    var next = isDark() ? 'light' : 'dark';
    document.documentElement.dataset.theme = next;
    try { localStorage.setItem('course-theme', next); } catch (e) { /* storage is blocked */ }
  });

  document.querySelector('.skip-link').addEventListener('click', function (event) {
    event.preventDefault();
    contentEl.focus();
  });

  // ------------------------------------------------------------ start

  document.documentElement.classList.add('js');
  window.addEventListener('hashchange', route);
  loadManifest().then(function () {
    renderSidebar();
    route();
  }).catch(showError);
})();
