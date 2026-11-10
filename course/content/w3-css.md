# CSS: токены, темы, адаптивность, шрифты

Весь внешний вид сайта живёт в одном файле, [main.css](repo:src/main/resources/static/styles/main.css), примерно на тысячу триста строк. Ни препроцессора, ни фреймворка, ни сборки. Такой подход возможен благодаря нескольким идеям, которые и составляют современный CSS: переменные, сетка, относительные единицы, медиазапросы и аккуратная работа со шрифтами. В этом уроке вы разберёте их на реальных правилах сайта и сразу попробуете в редакторах на странице.

После урока вы сможете:

- организовать цвета и размеры через дизайн-токены (переменные CSS) и сделать тёмную тему без дублирования вёрстки;
- построить адаптивную сетку карточек без «магических» медиазапросов;
- объяснить, как шрифты влияют на скачки вёрстки, и как их подгружать;
- писать состояния (фокус, наведение, уменьшение движения), которые не мешают людям.

## Каскад и специфичность в двух абзацах

Если два правила задают одно свойство, побеждает то, чей селектор **специфичнее**: идентификатор сильнее класса, класс сильнее тега; при равенстве выигрывает **более позднее** правило. Ошибка новичка: усиливать селектор, пока всё не заработает (`.page .cat .card .name`), а потом бояться что-либо менять.

Проект держит специфичность низкой: почти всё стилизуется одним классом (`.cat-card`, `.cat-card-name`), без вложенных цепочек. Редкое исключение: псевдокласс `:where()`, у которого **нулевая специфичность**:

```css
/* :where() keeps these at the lowest specificity, so a component that sets its own link colour
   (button, card, nav) is not recoloured on hover. */
a:where(:hover) {
    color: var(--color-accent-hover);
    text-decoration-thickness: 2px;
}
```

Правило задаёт цвет наведения для всех ссылок, но любой компонент (кнопка, карточка) легко его переопределит одним классом. Всё общее пишется «слабо», частное «сильно».

## Дизайн-токены: переменные CSS

Цвета, шрифты и отступы вынесены в **пользовательские свойства**:

```css
:root {
    --color-bg: #ffffff;
    --color-surface: #f4f4f5;
    --color-line: #e4e4e7;
    --color-ink: #18181b;
    --color-body: #3f3f46;
    --color-muted: #67676f;
    --color-accent: #1a7f37;
    --color-on-accent: #ffffff;
    --font-sans: "Source Sans 3", "Source Sans 3 Fallback", system-ui, sans-serif;
    --wrap-width: 720px;
    --measure: 33em;
}
```

Две важные особенности. Во-первых, **имена по роли, а не по значению**: `--color-muted` («второстепенный текст»), а не `--grey-600`. Когда цвет изменится, название останется правдой. Во-вторых, переменные **наследуются** и вычисляются во время работы страницы: поменяйте их на `<html>`, и перекрасится всё.

На этом построена тёмная тема. Это **те же токены с другими значениями**, вёрстка не дублируется:

```css
:root[data-theme="dark"] {
    color-scheme: dark;
    --color-bg: #1f2125;
    --color-surface: #292c31;
    --color-ink: #f0f2f5;
    --color-accent: #56c27c;
    --color-on-accent: #05250f;
}
```

Попробуйте сами. Нажмите кнопку, затем измените цвета в блоке `:root[data-theme="dark"]`:

```demo title="Токены и тёмная тема" height="300"
<style>
  :root {
    --bg: #ffffff; --ink: #18181b; --muted: #62626b;
    --accent: #1a7f37; --on-accent: #ffffff; --surface: #f4f4f5;
  }
  :root[data-theme="dark"] {
    --bg: #1f2125; --ink: #f0f2f5; --muted: #a4a9b1;
    --accent: #56c27c; --on-accent: #05250f; --surface: #292c31;
  }
  body { margin: 0; padding: 20px; font: 17px/1.5 system-ui, sans-serif; background: var(--bg); color: var(--ink); }
  .card { background: var(--surface); padding: 14px; border-radius: 4px; }
  .meta { color: var(--muted); font-size: 15px; }
  button { font: inherit; font-weight: 600; padding: 9px 16px; border: 0; border-radius: 4px; background: var(--accent); color: var(--on-accent); cursor: pointer; }
</style>
<div class="card">
  <h3 style="margin:0 0 4px">Felix</h3>
  <p class="meta">About 1 year · In the shelter</p>
  <button id="toggle">Switch theme</button>
</div>
<script>
  // Flip the theme on the root element, the same way the shelter site does it.
  document.getElementById("toggle").addEventListener("click", function () {
    var root = document.documentElement;
    root.dataset.theme = root.dataset.theme === "dark" ? "light" : "dark";
  });
</script>
```

### Три селектора для тёмной темы

В реальном файле три блока, и в этом есть логика:

```css
:root[data-theme="dark"] { /* the main path: the head script always sets data-theme */ }

:root[data-theme="light"] { color-scheme: light; }

@media (prefers-color-scheme: dark) {
    :root:not([data-theme]) { /* the same dark tokens: only when the script did not run at all */ }
}
```

Маленький скрипт в `<head>` выставляет `data-theme` по сохранённому выбору или по системной настройке **до первой отрисовки**, поэтому тёмная тема не мигает белым. Если скрипты отключены, отрабатывает `@media (prefers-color-scheme: dark)`. Кнопка переключения темы в этом случае была бы бесполезной, поэтому она **скрыта в CSS, пока скрипт не отработал**: `:root[data-theme] .theme-toggle { display: ... }`. Это принцип «сначала работает без JS»: подробнее в уроке про [JavaScript](lesson:w4-js).

:::note Дублирование значений
Тёмные значения записаны дважды: в блоке `[data-theme="dark"]` и в `@media`. Это известное неудобство чистого CSS без препроцессора. Проект принимает его осознанно: два блока по десять строк проще, чем сборка. Если токенов станет сотни, это решение стоит пересмотреть (например, генерировать оба блока из одного списка).
:::

## Сетка карточек

Список кошек одним правилом:

```css
.cat-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, 220px);
    gap: 28px 24px;
}

.cat-card-photo {
    display: block;
    width: 100%;
    aspect-ratio: 4 / 5;
    object-fit: cover;
    background: var(--color-surface);
}
```

Разберём:

- **`repeat(auto-fill, 220px)`**: «помести столько колонок по 220 пикселей, сколько влезет». На широком экране три колонки, на узком две или одна. Ни одного медиазапроса. Список из двух кошек не растягивается на всю ширину: колонки остаются по 220 пикселей, а свободное место пустует (в проекте это решение зафиксировано: «left-aligned, never stretched»).
- **`aspect-ratio: 4 / 5`** резервирует место под фото **до его загрузки**. Без этого страница прыгала бы вниз при загрузке каждой картинки (это называется *layout shift* и портит удобство).
- **`object-fit: cover`**: фото заполняет рамку, лишнее обрезается, пропорции не искажаются.

```demo title="Сетка auto-fill (в примере карточки по 110 px)" height="300"
<style>
  body { margin: 0; padding: 16px; font: 15px/1.4 system-ui, sans-serif; color: #3f3f46; }
  .cat-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, 110px);
    gap: 20px 16px;
  }
  .photo { aspect-ratio: 4 / 5; background: #e4e4e7; border-radius: 4px; }
  .name { display: block; margin-top: 6px; font-weight: 600; color: #18181b; }
</style>
<div class="cat-list">
  <div><div class="photo"></div><span class="name">Felix</span>About 1 year</div>
  <div><div class="photo"></div><span class="name">Mila</span>About 3 years</div>
  <div><div class="photo"></div><span class="name">Nami</span>Under a year</div>
  <div><div class="photo"></div><span class="name">Fox</span>2 years</div>
  <div><div class="photo"></div><span class="name">Sava</span>About 4 years</div>
</div>
```

Поменяйте `110px` на `80px` или `160px` и посмотрите, как перестраивается сетка. Потом замените `auto-fill` на `auto-fit` и сравните (разница видна, когда карточек мало).

### Один брейкпоинт

В `docs/design.md` записано: «Breakpoint: `640px`. There is one». (Есть ещё один исключительно для шапки, на 760 пикселей.) Это дисциплина: чем больше точек перелома, тем больше сочетаний, которые нужно проверять. Резиновые единицы (`auto-fill`, `min()`, проценты, `rem`) делают остальное. Телефон, планшет и ноутбук получают подходящую вёрстку без отдельных правил.

## Читаемая строка

```css
--measure: 33em;   /* the longest line of running text: about 70 characters in ru */
```

Строки длиннее 80 знаков читаются плохо: глаз теряет начало следующей. Поэтому абзацы ограничены по ширине в единицах `em` (привязаны к размеру шрифта), а фото и карточки занимают всю колонку. Два свойства улучшают переносы: `text-wrap: balance` у заголовков (строки выравниваются по длине) и `text-wrap: pretty` у абзацев (не оставляет одно слово на последней строке).

```demo title="text-wrap: balance" height="220"
<style>
  body { margin: 0; padding: 16px; font: 700 26px/1.25 Georgia, serif; color: #18181b; }
  h1 { max-width: 12em; margin: 0 0 18px; }
  .balanced { text-wrap: balance; }
  small { display: block; font: 13px system-ui; color: #62626b; margin-bottom: 4px; }
</style>
<small>без balance</small>
<h1>Волонтёры приюта для кошек в Белграде</h1>
<small>text-wrap: balance</small>
<h1 class="balanced">Волонтёры приюта для кошек в Белграде</h1>
```

## Шрифты: где скрыты скачки вёрстки

Шрифты обычно весят десятки килобайт и грузятся позже текста. Пока шрифт грузится, браузер показывает запасной, а потом подменяет. Если у них разная ширина букв, **весь текст сдвигается**. Проект борется с этим в четыре приёма.

1. **Подмножества с `unicode-range`.** Шрифт разрезан на файлы по алфавитам (`latin`, `cyrillic`, `latin-ext` для сербских č, š, ž). Браузер скачивает только те, символы которых есть на странице. Русская страница грузит около 120 КБ шрифтов, сербская около 175 КБ.
2. **`font-display: swap`**: сразу показывать текст запасным шрифтом.
3. **`preload` только нужного.** В `<head>` для каждой страницы подгружаются латиница и алфавит её языка (`preloadedFonts` в [Layout.kt](repo:src/main/kotlin/org/proanima/shelter/views/Layout.kt)), чтобы шрифт нашёлся сразу, а не после разбора CSS.
4. **Запасной шрифт, подогнанный под основной.** Вот самое изящное:

```css
@font-face {
    font-family: "Source Sans 3 Fallback";
    font-weight: 400;
    src: local("Arial"), local("Liberation Sans"), local("Helvetica");
    size-adjust: 94.72%;
    ascent-override: 108.1%;
    descent-override: 42.23%;
    line-gap-override: 0%;
}
```

Это **локальный** шрифт Arial, у которого размер и высотные метрики подстроены так, чтобы буквы занимали ровно столько места, сколько займёт Source Sans 3. Подмена шрифта после загрузки происходит без сдвига строк. Цифры в комментарии проекта взяты из настоящих файлов шрифтов. Они подобраны один раз и вынесены в стек: `--font-sans: "Source Sans 3", "Source Sans 3 Fallback", system-ui, sans-serif`.

## Состояния: фокус, наведение, движение

```css
:focus-visible {
    outline: 2px solid var(--color-accent);
    outline-offset: 2px;
}
```

`:focus-visible` показывает рамку фокуса **для клавиатуры**, но не при щелчке мышью. Убирать рамку фокуса (`outline: none`) без замены нельзя: человек с клавиатурой перестанет видеть, где он находится. Эффекты наведения работают только там, где наведение существует:

```css
@media (hover: hover) {
    /* hover effects only on devices that can really hover: a touch screen has no hover */
}
```

А людям, которым анимации вредят (вестибулярные нарушения), уважайте `@media (prefers-reduced-motion: reduce)`. В проекте почти нет анимации вовсе: «No ... hover animations unless this document asks for them».

## Live coding: карточка кошки с нуля

::::live Токены, карточка, тёмная тема
Соберём карточку кошки так, как это сделано на сайте, но короче.

:::step Шаг 1. Токены
Сначала роли, а потом их использование.

```css
:root {
    --color-bg: #fff;
    --color-surface: #f4f4f5;
    --color-ink: #18181b;
    --color-muted: #62626b;
}
body { background: var(--color-bg); color: var(--color-ink); }
```

Ни одного «сырого» цвета вне блока токенов.
:::

:::step Шаг 2. Карточка
Фото с заранее известной пропорцией, имя, строка мелким цветом.

```css
.cat-card { display: flex; flex-direction: column; text-decoration: none; color: var(--color-ink); }
.cat-card-photo { width: 100%; aspect-ratio: 4 / 5; object-fit: cover; background: var(--color-surface); }
.cat-card-meta { color: var(--color-muted); font-size: 15px; }
```

`background` у фото служит запасным цветом: пока картинка не загрузилась, на её месте спокойный серый, а не пустота.
:::

:::step Шаг 3. Сетка и тёмная тема
Сетка из `auto-fill` и второй набор значений для токенов.

```css
.cat-list { display: grid; grid-template-columns: repeat(auto-fill, 150px); gap: 24px 16px; }
:root[data-theme="dark"] { --color-bg: #1f2125; --color-surface: #292c31; --color-ink: #f0f2f5; --color-muted: #a4a9b1; }
```
:::

:::step Шаг 4. Результат
Откройте и пощупайте: поменяйте число колонок, добавьте состояние наведения, переключите тему.

```demo title="Карточки кошек" height="340"
<style>
  :root { --color-bg: #fff; --color-surface: #f4f4f5; --color-ink: #18181b; --color-muted: #62626b; --color-accent: #1a7f37; }
  :root[data-theme="dark"] { --color-bg: #1f2125; --color-surface: #292c31; --color-ink: #f0f2f5; --color-muted: #a4a9b1; --color-accent: #56c27c; }
  body { margin: 0; padding: 16px; font: 16px/1.45 system-ui, sans-serif; background: var(--color-bg); color: var(--color-ink); }
  .cat-list { display: grid; grid-template-columns: repeat(auto-fill, 130px); gap: 20px 16px; }
  .cat-card { display: flex; flex-direction: column; color: var(--color-ink); text-decoration: none; }
  .cat-card-photo { aspect-ratio: 4 / 5; background: var(--color-surface); border-radius: 4px; }
  .cat-card-name { margin-top: 8px; font: 600 18px/1.3 Georgia, serif; }
  .cat-card:hover .cat-card-name { text-decoration: underline; text-underline-offset: 3px; }
  .cat-card-meta { color: var(--color-muted); font-size: 14px; }
  .cat-card:focus-visible { outline: 2px solid var(--color-accent); outline-offset: 4px; border-radius: 4px; }
  button { margin-bottom: 14px; font: inherit; padding: 6px 12px; }
</style>
<button id="t">Theme</button>
<div class="cat-list">
  <a class="cat-card" href="#"><div class="cat-card-photo"></div><span class="cat-card-name">Felix</span><span class="cat-card-meta">About 1 year</span></a>
  <a class="cat-card" href="#"><div class="cat-card-photo"></div><span class="cat-card-name">Mila</span><span class="cat-card-meta">About 3 years</span></a>
  <a class="cat-card" href="#"><div class="cat-card-photo"></div><span class="cat-card-name">Nami</span><span class="cat-card-meta">Under a year</span></a>
</div>
<script>
  document.getElementById("t").onclick = function () {
    var r = document.documentElement;
    r.dataset.theme = r.dataset.theme === "dark" ? "light" : "dark";
  };
</script>
```

Попробуйте также нажать Tab: карточка получает рамку фокуса (это `:focus-visible`).
:::
::::

## Практика

::::task Две колонки на телефоне
В дизайн-системе проекта на узком экране карточки идут в **две равные колонки** (`repeat(2, 1fr)`), а не фиксированной ширины. Доработайте пример: добавьте медиазапрос, который при ширине до 400 пикселей включает две равные колонки. (Рамка предпросмотра как раз около 400 px.)

```demo title="Дополните медиазапрос" height="280"
<style>
  body { margin: 0; padding: 16px; font: 15px system-ui, sans-serif; }
  .list { display: grid; grid-template-columns: repeat(auto-fill, 150px); gap: 16px; }
  .photo { aspect-ratio: 4 / 5; background: #e4e4e7; border-radius: 4px; }
  /* TODO: at 400px and narrower use two equal columns */
</style>
<div class="list">
  <div class="photo"></div><div class="photo"></div><div class="photo"></div><div class="photo"></div>
</div>
```

:::hint
`@media (max-width: 400px) { .list { grid-template-columns: repeat(2, 1fr); } }`. Блок ставится после основного правила: при равной специфичности побеждает более позднее.
:::

:::solution
```css
@media (max-width: 400px) {
    .list {
        grid-template-columns: repeat(2, 1fr);
        gap: 16px 12px;
    }
}
```

В проекте так и записано («≤ 640 px: two equal columns, `repeat(2, 1fr)`»). Заметьте комментарий из `docs/design.md`: «Do not size from `100vw` (it includes the scrollbar)». Ширина через `100vw` включает полосу прокрутки и создаёт горизонтальный скролл, а `fr` и проценты считаются от реальной ширины контейнера.
:::
::::

::::task Токены вместо «сырых» цветов
В этом коде три цвета повторяются по нескольку раз. Перепишите его, вынеся цвета в токены `--color-ink`, `--color-muted`, `--color-accent`, и добавьте вариант для `data-theme="dark"`.

```css
.title { color: #18181b; }
.meta { color: #62626b; }
.link { color: #1a7f37; }
.link:hover { color: #16692e; }
.note { border-left: 3px solid #1a7f37; color: #62626b; }
```

:::solution
```css
:root {
    --color-ink: #18181b;
    --color-muted: #62626b;
    --color-accent: #1a7f37;
    --color-accent-hover: #16692e;
}

:root[data-theme="dark"] {
    --color-ink: #f0f2f5;
    --color-muted: #a4a9b1;
    --color-accent: #56c27c;
    --color-accent-hover: #6fd092;
}

.title { color: var(--color-ink); }
.meta { color: var(--color-muted); }
.link { color: var(--color-accent); }
.link:hover { color: var(--color-accent-hover); }
.note { border-left: 3px solid var(--color-accent); color: var(--color-muted); }
```

Теперь смена акцентного цвета это одна строка, а тёмная тема появилась без правки правил компонентов. Обратите внимание, что в тёмной теме акцент **светлее**: тёмно-зелёный текст на тёмном фоне не прошёл бы проверку контраста (см. урок про [доступность](lesson:u2-a11y-perf)).
:::
::::

::::task Фокус и движение
Напишите два правила: видимую рамку фокуса только для клавиатуры (цвет акцента, отступ 2 px) и отключение плавного перехода у `.card` для тех, кто просит минимум движения.

:::solution
```css
:focus-visible {
    outline: 2px solid var(--color-accent);
    outline-offset: 2px;
}

.card { transition: transform 0.2s; }

@media (prefers-reduced-motion: reduce) {
    .card { transition: none; }
}
```

Не отключайте рамку фокуса (`outline: none`) глобально: сайт перестанет быть пригодным для клавиатуры. Если хочется свой стиль, замените рамку другим заметным индикатором, но не убирайте её.
:::
::::

:::quiz Проверьте себя
Q: Почему токены названы `--color-muted`, а не `--grey-600`?
- [ ] Так короче.
- [x] Название описывает роль цвета: значение может измениться (в тёмной теме это уже не тёмно-серый), а смысл останется.
- [ ] Серый цвет нельзя назвать.
- [ ] Это требование браузера.
E: Имена по значению становятся ложью при первой же смене темы или палитры.

Q: Что даёт `aspect-ratio: 4 / 5` у фото в карточке?
- [ ] Делает фото чётче.
- [x] Резервирует место до загрузки, поэтому страница не «прыгает» при появлении картинок.
- [ ] Ускоряет загрузку.
- [ ] Обрезает фото по кругу.
E: Браузер знает высоту блока заранее и не пересчитывает раскладку, когда пришли пиксели.

Q: Зачем запасной шрифт описан через `size-adjust` и `ascent-override`?
- [ ] Чтобы шрифт грузился быстрее.
- [x] Чтобы запасной шрифт занимал столько же места, сколько основной, и подмена не сдвигала текст.
- [ ] Чтобы поддержать старые браузеры.
- [ ] Чтобы шрифт был жирнее.
E: Это борьба со сдвигом раскладки при подмене шрифта.

Q: Что делает `grid-template-columns: repeat(auto-fill, 220px)`?
- [ ] Всегда создаёт три колонки.
- [x] Создаёт столько колонок по 220 пикселей, сколько помещается в контейнер.
- [ ] Растягивает карточки на всю ширину.
- [ ] Включает мобильную версию.
E: Число колонок зависит от доступной ширины, без медиазапросов.

Q: Какие утверждения о `:focus-visible` верны?
- [x] Рамка показывается при навигации с клавиатуры.
- [x] При обычном щелчке мышью рамка по умолчанию не показывается.
- [ ] Его можно заменить на `outline: none` без последствий.
- [ ] Он работает только на кнопках.
E: Именно поэтому правило безопасно оставить видимым: мышь оно не раздражает, а клавиатуре помогает.

Q: Почему на странице три блока для тёмной темы, включая `@media (prefers-color-scheme: dark)` с `:root:not([data-theme])`?
- [ ] Для красоты кода.
- [x] Основной путь — атрибут `data-theme`, который ставит скрипт; медиазапрос нужен как запасной вариант для случая, когда скрипты не выполнились.
- [ ] Браузеры требуют именно три.
- [ ] Это ошибка дублирования.
E: Сайт должен выглядеть разумно и без JavaScript. Цена — повтор значений в двух блоках.

Q: Почему `a:where(:hover)` удобнее обычного `a:hover`?
- [ ] Работает быстрее.
- [x] У `:where()` нулевая специфичность, поэтому любой компонент может переопределить цвет наведения простым классом.
- [ ] Работает на сенсорных экранах.
- [ ] Это единственный способ задать наведение.
E: Общие правила должны быть слабыми, чтобы частные не воевали с ними.
:::

## Итоги

- Токены по ролям и тёмная тема как тот же набор токенов с другими значениями.
- Сетка с `auto-fill` и `aspect-ratio` дают адаптивность без вороха медиазапросов и без скачков.
- Шрифты: подмножества, предзагрузка нужного, запасной шрифт с подогнанными метриками.
- Состояния: рамку фокуса не убирают, наведение только там, где оно есть, анимации уважают просьбу о минимуме движения.

Дальше: [JavaScript и прогрессивное улучшение](lesson:w4-js).
