# JavaScript и прогрессивное улучшение

Весь JavaScript сайта умещается в четыре файла общим объёмом меньше двухсот строк. Это не потому, что авторы не умеют писать скрипты, а потому, что страница почти ничего не требует от них. Принцип называется **прогрессивным улучшением**: сначала страница работает на одном HTML, затем CSS делает её красивой, и только потом скрипт добавляет удобство. Если скрипт не загрузился, заблокирован или упал, пользователь всё равно получает сайт.

После урока вы сможете:

- объяснить принцип прогрессивного улучшения и найти его в коде проекта;
- читать и писать небольшие скрипты: поиск элементов, события, делегирование, атрибуты `aria-*`, управление фокусом;
- защищаться от недоступного хранилища и отсутствующих элементов;
- решать, что делать на CSS, а что только на JS.

## Слои сайта

| Слой | Что даёт | Что если слоя нет |
|---|---|---|
| HTML | содержание и ссылки | ничего не работает |
| CSS | вид и раскладка | текст читается, как в 1995 году |
| JS | удобство (тема, меню по клику, клавиши в галерее) | всё остаётся доступным, чуть менее удобным |

Посмотрим, как этот принцип выглядит в четырёх скриптах проекта.

### 1. Галерея: сначала CSS

Увеличение фото кошки **полностью работает без JavaScript**. Фото — это ссылка `#photo-0`; блок с таким `id` становится видимым, когда адрес страницы указывает на него (CSS-псевдокласс `:target`), а ссылка `#` закрывает его. Прямо в [CatViews.kt](repo:src/main/kotlin/org/proanima/shelter/views/CatViews.kt) комментарий: «Works without JS; cat-gallery.js layers Esc, the arrow keys and focus handling on top».

Скрипт `cat-gallery.js` добавляет то, что CSS не может:

```javascript
document.addEventListener("keydown", (event) => {
    const lightbox = openLightbox();
    if (!lightbox) {
        return;
    }

    const selectors = {
        ArrowLeft: ".cat-gallery-lightbox-prev",
        ArrowRight: ".cat-gallery-lightbox-next",
        Escape: ".cat-gallery-lightbox-close"
    };
    const selector = selectors[event.key];
    const link = selector && lightbox.querySelector(selector);
    if (link) {
        event.preventDefault();
        link.click();
    }
});
```

Скрипт **не реализует** переключение фото сам. Он нажимает те же ссылки (`link.click()`), которыми пользуется мышь. Логика осталась в HTML, а JS лишь передаёт клавиши. Если скрипт сломается, галерея продолжит работать мышью.

Обратите внимание на деталь из комментария в файле: «getElementById, not querySelector: the hash can be any string, and an invalid CSS selector (e.g. "#1abc") would throw on every key press». Данные из адресной строки приходят от кого угодно. `querySelector("#1abc")` выбросит исключение, а `getElementById("1abc")` просто вернёт `null`. Выбирайте функцию так, чтобы **чужой ввод не мог её сломать**.

### 2. Меню: кнопка, скрытая до появления скрипта

Выпадающее меню в HTML содержит кнопку-стрелку, которая работает только со скриптом. Чтобы не показывать мёртвую кнопку, она скрыта атрибутом `hidden`, а скрипт её показывает:

```javascript
groups.forEach(function (group) {
    var button = buttonOf(group);
    // The small button beside a label that is a link is hidden until there is a script to work it.
    button.hidden = false;

    button.addEventListener("click", function () {
        var open = !group.classList.contains("is-open");
        closeAll(group);
        setOpen(group, open);
    });
});
```

Тот же приём в других местах: кнопка смены темы скрыта в CSS, пока скрипт в `<head>` не поставит `data-theme`; кнопка «Показать пароль» в админке скрыта до загрузки `admin-password.js` (комментарий: «without JavaScript the button could do nothing»). А на `<html>` скрипт ставит класс `nav-js`, по которому CSS переключается между режимами «меню по наведению и фокусу» и «меню по клику»:

```css
.nav-group.is-open > .nav-sub,
html:not(.nav-js) .nav-group:hover > .nav-sub,
html:not(.nav-js) .nav-group:focus-within > .nav-sub {
    display: block;
}
```

### 3. Тема и недоступное хранилище

```javascript
button.addEventListener("click", function () {
    var root = document.documentElement;
    var next = root.dataset.theme === "dark" ? "light" : "dark";
    root.dataset.theme = next;
    try {
        localStorage.setItem("theme", next);
    } catch (e) {
        // Storage is blocked: the choice just won't survive a reload.
    }
});
```

`localStorage` может быть недоступен: блокировка сайтовых данных, приватный режим, встраивание в iframe. Обращение тогда выбрасывает исключение. Рабочая часть (смена темы на странице) выполняется **до** записи, и сбой хранилища её не ломает. Ровно это можно попробовать прямо сейчас: рамка ниже изолирована так, что хранилище в ней заблокировано.

```demo title="localStorage в изолированной рамке" height="200"
<pre id="out" style="font: 15px/1.5 ui-monospace, monospace; margin: 16px"></pre>
<script>
  var out = document.getElementById("out");
  function log(line) { out.textContent += line + "\n"; }

  try {
    localStorage.setItem("theme", "dark");
    log("saved");
  } catch (e) {
    log("storage is blocked: " + e.name);
  }
  log("the page still works");
</script>
```

Уберите `try/catch` (оставьте только `localStorage.setItem(...)`) и обновите пример: следующая строка уже не выполнится. Так выглядит страница, сломанная чужими настройками браузера.

## Основы DOM на примерах проекта

| Задача | Код |
|---|---|
| найти элемент | `document.querySelector(".theme-toggle")`, `getElementById(id)` |
| найти несколько | `document.querySelectorAll(".main-nav .nav-group")` |
| класс | `el.classList.toggle("is-open", open)` |
| атрибут | `button.setAttribute("aria-expanded", "true")` |
| данные | `link.dataset.id` (из `data-id="..."`) |
| событие | `el.addEventListener("click", handler)` |
| ближайший предок | `event.target.closest(".nav-group")` |

### Делегирование событий

Вместо того чтобы вешать обработчик на каждый элемент, можно повесить **один** на документ и смотреть, куда кликнули:

```javascript
document.addEventListener("click", function (event) {
    if (!event.target.closest(".main-nav .nav-group")) {
        closeAll(null);
    }
});
```

Это обработчик «клик вне меню закрывает меню». Он один на всю страницу. Подход работает и для элементов, которые появятся позже: обработчик уже ждёт.

### Скрипты и загрузка

```kotlin
script(src = "/scripts/theme-toggle.js") { attributes["defer"] = "defer" }
```

`defer` говорит браузеру скачать скрипт параллельно и выполнить **после** разбора HTML, но до события загрузки. Страница не ждёт скрипт, и в момент выполнения все элементы уже существуют. Единственное исключение в проекте: маленький скрипт темы в `<head>` без `defer`. Он намеренно **блокирующий**: тема должна быть выставлена до первой отрисовки, иначе пользователь тёмной темы увидит белую вспышку.

:::note Область видимости
Три файла из четырёх обёрнуты в «немедленно вызываемую функцию» `(function () { ... })();`, чтобы переменные не попали в глобальную область. `cat-gallery.js` использует `let` и `const` на верхнем уровне. Современные браузеры поддерживают модули (`<script type="module">`), которые решают ту же задачу; для четырёх маленьких файлов это не критично.
:::

## Доступность в JS

Скрипт, который открывает и закрывает элементы, обязан **говорить об этом скринридеру**:

- `aria-expanded="true|false"` на кнопке: «раскрыто» или «свёрнуто»;
- `aria-controls="id"`: какой элемент кнопка управляет;
- по клавише `Esc` закрывать и **возвращать фокус на кнопку**, иначе клавиатурный пользователь теряется;
- при открытии модального окна переносить фокус внутрь, при закрытии возвращать к тому, что его открыло (в `cat-gallery.js` запоминается `opener`).

## Live coding: выпадающее меню шаг за шагом

::::live Меню, которое работает с JS и без него
Соберём упрощённую копию меню сайта. Откройте каждый шаг и повторите.

:::step Шаг 1. Только HTML и CSS
Группа со ссылками и CSS-правилом: при наведении или фокусе внутри показать список. Здесь нет ни одной строки JavaScript.

```html
<div class="nav-group">
  <button class="nav-toggle" type="button">Animals</button>
  <div class="nav-sub"><a href="#cats">Cats</a><a href="#dogs">Dogs</a></div>
</div>
```

```css
.nav-sub { display: none; }
.nav-group:hover .nav-sub,
.nav-group:focus-within .nav-sub { display: block; }
```

Клавиатура работает: Tab на кнопку открывает список (`:focus-within`), Tab дальше идёт по ссылкам.
:::

:::step Шаг 2. Скрипт добавляет клик и состояние
Мы хотим, чтобы меню открывалось и **закрывалось** кликом, и чтобы скринридер знал состояние. Скрипт ставит на `<html>` класс `nav-js`, чтобы CSS перешёл на управление классом `is-open`.

```javascript
document.documentElement.classList.add("nav-js");

function setOpen(open) {
  group.classList.toggle("is-open", open);
  button.setAttribute("aria-expanded", open ? "true" : "false");
}
button.addEventListener("click", function () {
  setOpen(!group.classList.contains("is-open"));
});
```

CSS меняется так: правило с `:hover` и `:focus-within` ограничено `html:not(.nav-js)`, а открывает меню класс `.is-open`.
:::

:::step Шаг 3. Esc и щелчок вне меню
Ожидаемое поведение любого раскрывающегося блока.

```javascript
document.addEventListener("keydown", function (event) {
  if (event.key === "Escape" && group.classList.contains("is-open")) {
    setOpen(false);
    button.focus();
  }
});
document.addEventListener("click", function (event) {
  if (!event.target.closest(".nav-group")) setOpen(false);
});
```

`button.focus()` возвращает фокус туда, откуда пользователь пришёл.
:::

:::step Шаг 4. Целиком
```demo title="Выпадающее меню: с клавиатуры и мышью" height="260"
<style>
  body { margin: 0; padding: 16px; font: 16px system-ui, sans-serif; }
  .nav-group { position: relative; display: inline-block; }
  .nav-toggle { font: inherit; padding: 8px 14px; min-height: 44px; cursor: pointer; }
  .nav-sub { display: none; position: absolute; left: 0; top: 100%; min-width: 180px; background: #fff; border: 1px solid #ccc; padding: 4px; }
  .nav-sub a { display: block; padding: 10px 12px; color: #18181b; text-decoration: none; }
  .nav-sub a:hover, .nav-sub a:focus { background: #f4f4f5; }
  /* Without the script: open on hover and on keyboard focus. */
  html:not(.nav-js) .nav-group:hover > .nav-sub,
  html:not(.nav-js) .nav-group:focus-within > .nav-sub { display: block; }
  /* With the script: the is-open class decides. */
  .nav-group.is-open > .nav-sub { display: block; }
</style>
<div class="nav-group">
  <button class="nav-toggle" type="button" aria-expanded="false" aria-controls="sub">Animals ▾</button>
  <div class="nav-sub" id="sub"><a href="#cats">Cats</a><a href="#dogs">Dogs</a></div>
</div>
<script>
  (function () {
    document.documentElement.classList.add("nav-js");
    var group = document.querySelector(".nav-group");
    var button = group.querySelector(".nav-toggle");

    function setOpen(open) {
      group.classList.toggle("is-open", open);
      button.setAttribute("aria-expanded", open ? "true" : "false");
    }

    button.addEventListener("click", function () { setOpen(!group.classList.contains("is-open")); });
    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape" && group.classList.contains("is-open")) {
        setOpen(false);
        button.focus();
      }
    });
    document.addEventListener("click", function (event) {
      if (!event.target.closest(".nav-group")) setOpen(false);
    });
  })();
</script>
```

Проверьте поведение: клик по кнопке, Esc, клик вне меню, Tab по ссылкам. Затем **удалите** строку `document.documentElement.classList.add("nav-js");` и посмотрите, как меню переходит на режим «по наведению и фокусу». Это та самая страховка «на случай, если скрипт не заработал».
:::
::::

## Практика

::::task Стрелки в меню
Добавьте в меню из live coding клавиши: `ArrowDown` на кнопке открывает меню и переводит фокус на первую ссылку; `ArrowDown` и `ArrowUp` на ссылке переходят к следующей и предыдущей.

```demo title="Добавьте ArrowDown и ArrowUp" height="260"
<style>
  body { margin: 0; padding: 16px; font: 16px system-ui, sans-serif; }
  .nav-sub { display: none; margin-top: 4px; border: 1px solid #ccc; width: 180px; }
  .nav-sub.is-open { display: block; }
  .nav-sub a { display: block; padding: 10px 12px; color: #18181b; text-decoration: none; }
  .nav-sub a:focus { background: #e8f3ec; outline: 2px solid #1a7f37; }
  button { font: inherit; padding: 8px 14px; min-height: 44px; }
</style>
<button id="b" type="button" aria-expanded="false">Animals ▾</button>
<div class="nav-sub" id="sub"><a href="#cats">Cats</a><a href="#dogs">Dogs</a><a href="#help">Help</a></div>
<script>
  var button = document.getElementById("b");
  var sub = document.getElementById("sub");
  var links = Array.prototype.slice.call(sub.querySelectorAll("a"));

  function setOpen(open) {
    sub.classList.toggle("is-open", open);
    button.setAttribute("aria-expanded", open ? "true" : "false");
  }
  button.addEventListener("click", function () { setOpen(!sub.classList.contains("is-open")); });

  // TODO: ArrowDown on the button opens the list and focuses the first link
  // TODO: ArrowDown / ArrowUp on a link move to the next / previous one
</script>
```

:::hint
Для кнопки `button.addEventListener("keydown", ...)`; для ссылок один обработчик на `sub` (делегирование): текущий индекс — `links.indexOf(document.activeElement)`. Не забудьте `event.preventDefault()`, иначе страница прокрутится.
:::

:::solution
```javascript
button.addEventListener("keydown", function (event) {
  if (event.key === "ArrowDown") {
    event.preventDefault();
    setOpen(true);
    links[0].focus();
  }
});

sub.addEventListener("keydown", function (event) {
  var index = links.indexOf(document.activeElement);
  if (index === -1) return;
  if (event.key === "ArrowDown") {
    event.preventDefault();
    links[(index + 1) % links.length].focus();
  } else if (event.key === "ArrowUp") {
    event.preventDefault();
    links[(index - 1 + links.length) % links.length].focus();
  }
});
```

Обратите внимание на `(index - 1 + links.length) % links.length`: в JavaScript остаток от деления отрицательного числа отрицателен, поэтому для перехода «от первого к последнему» нужно прибавить длину. Тот же приём в проекте у кнопок «предыдущее/следующее фото» (в Kotlin: `(index - 1 + photoUrls.size) % photoUrls.size`).
:::
::::

::::task Безопасное хранилище
Напишите две функции: `readSetting(key, fallback)` и `writeSetting(key, value)`, которые работают с `localStorage`, но **никогда не выбрасывают исключение**. Если хранилище недоступно, `readSetting` возвращает `fallback`, а `writeSetting` возвращает `false`.

```demo title="Оберните обращения к хранилищу" height="220"
<pre id="out" style="font: 15px/1.5 ui-monospace, monospace; margin: 16px"></pre>
<script>
  function readSetting(key, fallback) {
    // TODO
  }
  function writeSetting(key, value) {
    // TODO
  }

  var out = document.getElementById("out");
  out.textContent = "theme = " + readSetting("theme", "light") + "\nsaved: " + writeSetting("theme", "dark");
</script>
```

:::solution
```javascript
function readSetting(key, fallback) {
  try {
    var value = localStorage.getItem(key);
    return value === null ? fallback : value;
  } catch (e) {
    return fallback;
  }
}

function writeSetting(key, value) {
  try {
    localStorage.setItem(key, value);
    return true;
  } catch (e) {
    return false;
  }
}
```

В рамке предпросмотра хранилище заблокировано, поэтому вы увидите `theme = light` и `saved: false`: страница работает, а настройка просто не запомнилась. На обычном сайте результат будет `saved: true`. Заметьте, что обе функции не бросают исключений даже при заблокированном хранилище. Это и есть смысл защитного кода.
:::
::::

::::task Что отдать CSS
Для каждого поведения скажите, можно ли сделать его **без JavaScript**, и как: (а) подсветка карточки при наведении; (б) открытие фото на весь экран; (в) сворачивание блока «Подробнее»; (г) запоминание выбранной темы между визитами; (д) закрытие меню по Esc.

:::solution
- (а) Да, CSS `:hover` (с `@media (hover: hover)`).
- (б) Да, через якорь и `:target`, как в галерее проекта; клавиши Esc и стрелки уже добавка скрипта.
- (в) Да, элемент `<details><summary>`: открывается и закрывается нативно, с клавиатуры тоже. Эти же `details` используются в самом курсе.
- (г) Нет: нужно хранилище (`localStorage` или cookie, которую сервер читает и подставляет сразу).
- (д) Нет: реакция на клавишу это событие, для него нужен скрипт. Но можно не делать меню, зависящее от скрипта, как в проекте: без JS оно открывается по фокусу и закрывается, когда фокус уходит.

Общий вывод: сначала спросите, нет ли нативного элемента или псевдокласса, и только потом пишите скрипт.
:::
::::

:::quiz Проверьте себя
Q: Что означает «прогрессивное улучшение»?
- [ ] Сайт грузится постепенно, по частям.
- [x] Сайт работает на одном HTML, CSS улучшает вид, а JS добавляет удобство, не будучи обязательным.
- [ ] Каждый год в сайт добавляют новые функции.
- [ ] Сайт показывает новую версию только современным браузерам.
E: Базовая функциональность не зависит от скрипта. Если скрипт не загрузился, пользователь не теряет доступ.

Q: Почему скрипт темы в `<head>` не имеет `defer`, а остальные имеют?
- [ ] Автор забыл.
- [x] Тема должна быть выставлена до первой отрисовки, иначе пользователь тёмной темы увидит белую вспышку.
- [ ] `defer` не работает в `<head>`.
- [ ] Этот скрипт самый большой.
E: Блокирующий скрипт здесь — намеренное исключение, оправданное кратким размером и целью.

Q: Что делает `lightbox.querySelector(selector)` внутри обработчика клавиши и почему в других местах используется `getElementById`?
- [ ] Это одно и то же.
- [x] `getElementById` безопасен для произвольной строки из адресной строки, а `querySelector` с недопустимым селектором (`#1abc`) бросает исключение.
- [ ] `querySelector` работает быстрее.
- [ ] `getElementById` не находит скрытые элементы.
E: Данные из адреса — недоверенный ввод; функцию выбирают так, чтобы ввод не мог её сломать.

Q: Какие из этих действий корректно делает скрипт раскрывающегося меню?
- [x] Обновляет `aria-expanded` у кнопки.
- [x] По `Esc` закрывает меню и возвращает фокус на кнопку.
- [ ] Убирает `outline` у кнопки, чтобы выглядело аккуратнее.
- [ ] Блокирует `Tab` внутри меню.
E: Состояние нужно сообщать скринридеру, а фокус не должен теряться. Рамку фокуса убирать нельзя, `Tab` блокировать тоже.

Q: Зачем обращения к `localStorage` оборачивают в `try/catch`?
- [ ] Потому что `localStorage` медленный.
- [x] Он может быть недоступен (блокировка данных, приватный режим, iframe) и тогда бросает исключение, которое не должно ломать страницу.
- [ ] Потому что он хранит только строки.
- [ ] Это требование стандарта.
E: Не хранить настройку — мелкая неприятность, а сломать весь скрипт — серьёзная.

Q: В чём плюс делегирования событий (`document.addEventListener("click", ...)` + `closest`)?
- [ ] Выполняется быстрее любого другого способа.
- [x] Один обработчик обслуживает все элементы, в том числе добавленные позже.
- [ ] Не нужно проверять `event.target`.
- [ ] Работает только для ссылок.
E: Обработчик на уровне документа ждёт события «всплытия» от любых потомков.
:::

## Итоги

- Сначала HTML, затем CSS, затем JS: каждый слой работает без следующего.
- Скрипт добавляет удобство: нажимает те же ссылки, переключает классы, обновляет `aria-*`, возвращает фокус.
- Все обращения к ненадёжным вещам (хранилище, адрес, отсутствующие элементы) защищены.
- Перед тем как писать скрипт, проверьте, нет ли нативного элемента (`details`, `dialog`, `:target`) для той же задачи.

Дальше: [Многоязычность: ru, en, sr](lesson:w5-i18n).
