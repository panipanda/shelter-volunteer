# Доступность и скорость как часть UX

Доступность и скорость часто считают «технической полировкой на потом». На самом деле это условия, при которых сайт вообще работает для части людей: для слепого волонтёра с экранным диктором, для человека с дрожащими руками и кнопкой на сенсорном экране, для того, кто открыл страницу в метро на слабой сети. Правило проекта: это **требования к каждой странице**, а не пожелания, и они записаны в `docs/design.md` числами.

После урока вы сможете:

- посчитать контраст цветов по формуле WCAG и исправить недостаточный;
- проверить страницу с клавиатуры и понять, что сообщает скринридер;
- назвать причины скачков вёрстки и медленной загрузки и способы их убрать;
- сочетать автоматические и ручные проверки.

## Четыре принципа доступности (POUR)

| Принцип | Вопрос | Пример в проекте |
|---|---|---|
| **Воспринимаемость** | Можно ли воспринять содержимое? | `alt` у фото; контраст не ниже 4,5 : 1; смысл не только цветом |
| **Управляемость** | Можно ли пользоваться без мыши? | ссылка-пропуск, видимый фокус, Esc закрывает меню и галерею, цели нажатия не меньше 44 px |
| **Понятность** | Предсказуемо ли поведение? | единые стили, понятные ошибки, `lang` у страницы |
| **Надёжность** | Работает ли с разными программами? | семантическая разметка, `aria-*` только там, где нет нативного элемента |

Это соответствует стандарту **WCAG 2.2**, уровню AA. Несколько критериев, на которые стоит обращать внимание всегда:

| Критерий | Требование | Как в проекте |
|---|---|---|
| 1.1.1 Текст для не-текста | у содержательных картинок есть `alt` | имя кошки; у декоративных `alt=""` |
| 1.4.3 Контраст текста | не меньше 4,5 : 1 (крупный текст 3 : 1) | токены проверены, см. ниже |
| 1.4.4 Масштаб текста | страница работает при увеличении до 200% | шрифты в `px`/`em`, резиновая сетка |
| 1.4.10 Перестроение | на ширине 320 px нет горизонтальной прокрутки | один брейкпоинт, `auto-fill` и `1fr` |
| 1.4.11 Контраст не-текста | рамки, значки, фокус не меньше 3 : 1 | рамка фокуса цветом акцента |
| 2.1.1 С клавиатуры | всё доступно без мыши | нативные элементы; скрипты дополняют, а не заменяют |
| 2.4.1 Обход блоков | можно пропустить повторяющееся | ссылка «К содержанию» |
| 2.4.7 Видимый фокус | видно, где фокус | `:focus-visible` |
| 2.5.8 Размер цели | не меньше 24 px (рекомендуется 44) | «Every control is at least 44 px high» |
| 3.1.1 Язык страницы | `lang` задан и верен | `lang` из реального языка текста |

## Контраст: формула и проверка

Контраст двух цветов считается через **относительную яркость** (0 для чёрного, 1 для белого):

1. Каждый канал цвета (R, G, B) переводится из диапазона 0..255 в 0..1 и «линеаризуется»: для малых значений делится на 12,92, для остальных возводится по формуле `((v + 0,055) / 1,055)^2,4`.
2. Яркость `L = 0,2126·R + 0,7152·G + 0,0722·B` (глаз сильнее всего чувствителен к зелёному).
3. Контраст `(L_светлого + 0,05) / (L_тёмного + 0,05)`. Он от 1 (одинаковые цвета) до 21 (чёрное на белом).

Реализуем это и проверим токены дизайн-системы проекта:

```run
import java.util.Locale
import kotlin.math.pow

fun channel(value: Int): Double {
    val v = value / 255.0
    return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}

fun luminance(hex: String): Double {
    val r = hex.substring(1, 3).toInt(16)
    val g = hex.substring(3, 5).toInt(16)
    val b = hex.substring(5, 7).toInt(16)
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
}

fun contrast(foreground: String, background: String): Double {
    val a = luminance(foreground)
    val b = luminance(background)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

fun main() {
    val pairs = listOf(
        Triple("ink on bg (light)", "#18181b", "#ffffff"),
        Triple("body on bg (light)", "#3f3f46", "#ffffff"),
        Triple("muted on surface (light)", "#67676f", "#f4f4f5"),
        Triple("accent on surface (light)", "#1a7f37", "#f4f4f5"),
        Triple("on-accent on accent (light)", "#ffffff", "#1a7f37"),
        Triple("muted on surface (dark)", "#a4a9b1", "#292c31"),
        Triple("accent on surface (dark)", "#56c27c", "#292c31"),
        Triple("on-accent on accent (dark)", "#05250f", "#56c27c"),
        Triple("hypothetical gray on surface", "#8a8a93", "#f4f4f5")
    )
    pairs.forEach { (name, fg, bg) ->
        val ratio = contrast(fg, bg)
        println("$name: ${"%.2f".format(Locale.ROOT, ratio)} ${if (ratio >= 4.5) "ok" else "FAIL"}")
    }
}
```

```output
ink on bg (light): 17.72 ok
body on bg (light): 10.44 ok
muted on surface (light): 5.10 ok
accent on surface (light): 4.62 ok
on-accent on accent (light): 5.08 ok
muted on surface (dark): 5.93 ok
accent on surface (dark): 6.27 ok
on-accent on accent (dark): 7.36 ok
hypothetical gray on surface: 3.11 FAIL
```

Сверьте с `docs/design.md`: «Weakest light pairs: accent on surface 4.6, muted on surface 5.1. Dark: accent on surface 6.3, muted on surface 5.9». Это те же числа, которые вы только что вычислили. Проект записал **самые слабые пары** и требует: «Re-check when changing any value». Именно поэтому у зелёного акцента в светлой теме тёмный оттенок (`#1a7f37`), а в тёмной светлый (`#56c27c`): один и тот же цвет не годится для обеих тем.

Попробуйте с собственными цветами:

```demo title="Калькулятор контраста" height="330"
<style>
  body { margin: 0; padding: 16px; font: 16px/1.5 system-ui, sans-serif; color: #18181b; }
  label { display: inline-block; margin: 0 16px 10px 0; }
  input[type=color] { vertical-align: middle; width: 48px; height: 32px; }
  #sample { padding: 14px 16px; border-radius: 4px; font-size: 18px; margin: 6px 0 12px; }
  .ok { color: #0a6b2c; font-weight: 600; }
  .bad { color: #b42318; font-weight: 600; }
  table { border-collapse: collapse; font-size: 15px; }
  td, th { padding: 4px 12px 4px 0; text-align: left; }
</style>
<label>Text <input type="color" id="fg" value="#67676f"></label>
<label>Background <input type="color" id="bg" value="#f4f4f5"></label>
<div id="sample">The shelter needs volunteers on Wednesday and Saturday.</div>
<p>Contrast ratio: <strong id="ratio"></strong></p>
<table>
  <tr><th>Level</th><th>Normal text</th><th>Large text</th></tr>
  <tr><td>AA</td><td id="aa"></td><td id="aaLarge"></td></tr>
  <tr><td>AAA</td><td id="aaa"></td><td id="aaaLarge"></td></tr>
</table>
<script>
  function channel(v) { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }
  function luminance(hex) {
    var r = parseInt(hex.slice(1, 3), 16), g = parseInt(hex.slice(3, 5), 16), b = parseInt(hex.slice(5, 7), 16);
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
  }
  function mark(id, pass) {
    var el = document.getElementById(id);
    el.textContent = pass ? "pass" : "fail";
    el.className = pass ? "ok" : "bad";
  }
  function update() {
    var fg = document.getElementById("fg").value, bg = document.getElementById("bg").value;
    var a = luminance(fg), b = luminance(bg);
    var ratio = (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    document.getElementById("ratio").textContent = ratio.toFixed(2) + " : 1";
    var sample = document.getElementById("sample");
    sample.style.color = fg;
    sample.style.background = bg;
    mark("aa", ratio >= 4.5); mark("aaLarge", ratio >= 3);
    mark("aaa", ratio >= 7); mark("aaaLarge", ratio >= 4.5);
  }
  document.getElementById("fg").addEventListener("input", update);
  document.getElementById("bg").addEventListener("input", update);
  update();
</script>
```

## Клавиатура и скринридер

Пройдите свою страницу **без мыши**: нажимайте `Tab`, `Shift+Tab`, `Enter`, пробел, `Esc`, стрелки. Проверяйте:

1. **Порядок фокуса** следует за визуальным порядком, и фокус нигде не пропадает.
2. **Фокус виден** на каждом элементе (проект: `:focus-visible` с рамкой цвета акцента).
3. **Первая остановка ссылка «К содержанию»**: она позволяет пропустить меню.
4. **Нет ловушек**: из любого места можно выйти клавиатурой. Окно-лайтбокс закрывается `Esc`, а фокус возвращается на миниатюру, с которой окно было открыто.
5. **Элементы честно называются.** Скринридер читает роль и имя: «кнопка, Сменить тему», «ссылка, Английский». Поэтому у кнопки без текста (иконки темы) есть `aria-label`, а у ссылок на языки `lang`/`hreflang` и полное название на самом языке.

Что сообщает скринридер о карточке кошки, зависит от разметки: `a` с `img alt="Феликс"` и именем даёт «ссылка, Феликс, изображение Феликс». Если `alt` повторяет соседний текст, получается «Феликс Феликс»: поэтому в живых проектах бывает уместен пустой `alt=""` у картинки, дублирующей подпись.

:::note Как попробовать скринридер
Включите его хотя бы раз: NVDA (Windows, бесплатный), VoiceOver (macOS и iOS, встроен, `Cmd+F5`), TalkBack (Android). Закройте глаза или отключите экран и попробуйте пройти по одному из сценариев сайта: найти ближайший выезд. Двадцать минут такого опыта меняют представление о разметке лучше любого справочника.
:::

### Состояния для вспомогательных технологий

| Задача | Атрибут | Пример |
|---|---|---|
| Раскрывающийся блок | `aria-expanded`, `aria-controls` | кнопка меню |
| Текущая страница | `aria-current="page"` | пункт меню |
| Неверное поле | `aria-invalid`, `aria-describedby` | форма админки |
| Важное сообщение | `role="alert"` | «Не сохранено» |
| Диалог | `role="dialog"`, `aria-modal`, `aria-label` | лайтбокс фото |
| Скрытое от скринридера | `aria-hidden="true"` | декоративный значок-стрелка |

Первое правило ARIA: **не используйте ARIA, если есть нативный элемент**. `<button>` лучше, чем `<div role="button" tabindex="0">` с тремя обработчиками. ARIA нужна там, где нативного нет (диалог, раскрывающееся меню).

## Скорость как UX

Три показателя Core Web Vitals описывают то, что чувствует человек:

| Показатель | Что измеряет | Цель | Что делает проект |
|---|---|---|---|
| **LCP** (крупнейшая отрисовка) | когда появилось главное содержимое | до 2,5 с | серверный HTML, шрифты с `preload`, фото первого ряда без `lazy` |
| **CLS** (сдвиг раскладки) | насколько страница «прыгает» | до 0,1 | `aspect-ratio` у фото, запасные шрифты с подогнанными метриками, `scrollbar-gutter: stable` |
| **INP** (отзывчивость) | как быстро страница реагирует на действия | до 200 мс | почти нет JavaScript |

### Картинки

Самая тяжёлая часть сайта это фотографии. Приёмы проекта, видимые в [CatViews.kt](repo:src/main/kotlin/org/proanima/shelter/views/CatViews.kt):

```kotlin
// The first row of a list is on the first screen and loads at once; the rest waits until it is near the viewport.
private const val CARDS_IN_FIRST_ROW = 3

internal fun FlowContent.cardPhoto(photoUrl: String, alt: String, position: Int) {
    img(src = cardPhotoUrl(photoUrl), alt = alt, classes = "cat-card-photo") {
        attributes["decoding"] = "async"
        if (position >= CARDS_IN_FIRST_ROW) {
            attributes["loading"] = "lazy"
        }
    }
}
```

- **Уменьшенные копии для карточек.** Карточка показывает фото шириной 220 пикселей, а оригинал до 1600 по длинной стороне. Рядом с каждым фото лежит копия `-card.jpg` (440×550). Реальные размеры файлов репозитория:

| Фото | Оригинал | Карточка | Меньше в |
|---|---|---|---|
| Феликс | 135 КБ | 69 КБ | 2 раза |
| Мила | 133 КБ | 58 КБ | 2,3 раза |
| Нами | 116 КБ | 36 КБ | 3,2 раза |

На странице с двенадцатью кошками это экономит мегабайт и заметно на слабой сети. Наличие копии проверяется **при старте**: нет `-card.jpg`, значит, приложение сообщит об ошибке.

- **`loading="lazy"`** для всего, что ниже первого экрана: браузер скачивает картинку, когда она приближается к области просмотра. Первые три (первый ряд) грузятся сразу: иначе главное содержимое появилось бы позже.
- **`decoding="async"`**: декодирование не блокирует отрисовку текста.
- **Резерв места** (`aspect-ratio`): без него загрузка картинки сдвинула бы текст.
- **Единый формат JPEG** при загрузке (перекодирование в админке), с разумным качеством 0,85 и 0,82.

### Что ещё влияет

Страница почти без JavaScript, текст сжимается gzip, статические файлы кэшируются, шрифты разрезаны по алфавитам и подгружается только нужное (см. урок про [CSS](lesson:w3-css)). Суммарно русская главная страница это HTML, один CSS, четыре небольших шрифтовых файла и несколько фото.

## Live coding: чиним контраст

Допустим, дизайнер предложил для второстепенного текста серый `#8a8a93` на поверхности `#f4f4f5`. Мы вычислили: контраст 3,11, меньше 4,5. Автоматизируем исправление: затемняем цвет шагами, пока контраст не станет достаточным.

::::live Подбор цвета алгоритмом
Мы используем функции `luminance` и `contrast` из урока и пишем только то, чего не хватает.

:::step Шаг 1. Масштабирование цвета
Умножаем каждый канал на коэффициент меньше единицы (чем меньше, тем темнее) и собираем строку обратно.

```kotlin
fun scale(hex: String, factor: Double): String {
    val channels = listOf(1, 3, 5).map { hex.substring(it, it + 2).toInt(16) }
    val scaled = channels.map { (it * factor).roundToInt().coerceIn(0, 255) }
    return "#%02x%02x%02x".format(scaled[0], scaled[1], scaled[2])
}
```
:::

:::step Шаг 2. Цикл с остановкой
Повторяем, пока контраст меньше цели. Обязательное ограничение числа шагов (`guard`) защищает от бесконечного цикла: например, цель, недостижимая на этом фоне.

```kotlin
fun fixForeground(hex: String, background: String, target: Double = 4.5): String {
    var color = hex
    var steps = 0
    while (contrast(color, background) < target && steps < 100) {
        color = scale(color, 0.96)
        steps++
    }
    return color
}
```
:::

:::step Шаг 3. Запуск
```run
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

fun channel(value: Int): Double {
    val v = value / 255.0
    return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}

fun luminance(hex: String): Double {
    val c = listOf(1, 3, 5).map { hex.substring(it, it + 2).toInt(16) }
    return 0.2126 * channel(c[0]) + 0.7152 * channel(c[1]) + 0.0722 * channel(c[2])
}

fun contrast(foreground: String, background: String): Double {
    val a = luminance(foreground)
    val b = luminance(background)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

fun scale(hex: String, factor: Double): String {
    val channels = listOf(1, 3, 5).map { hex.substring(it, it + 2).toInt(16) }
    val scaled = channels.map { (it * factor).roundToInt().coerceIn(0, 255) }
    return "#%02x%02x%02x".format(scaled[0], scaled[1], scaled[2])
}

fun fixForeground(hex: String, background: String, target: Double = 4.5): Pair<String, Int> {
    var color = hex
    var steps = 0
    while (contrast(color, background) < target && steps < 100) {
        color = scale(color, 0.96)
        steps++
    }
    return color to steps
}

fun main() {
    val (fixed, steps) = fixForeground("#8a8a93", "#f4f4f5")
    println("$fixed after $steps steps, contrast ${"%.2f".format(Locale.ROOT, contrast(fixed, "#f4f4f5"))}")
}
```

```output
#6c6c73 after 6 steps, contrast 4.74
```

Алгоритм нашёл `#6c6c73`, близкий к исходному оттенок, который проходит проверку. Заметьте, что он **не заменяет** дизайнера: он предложил число, а решение о том, подходит ли оттенок по замыслу, принимает человек. Реальный токен проекта, `#67676f`, чуть темнее найденного и даёт запас 5,10.
:::
::::

## Автоматическая проверка и ручная

Автоматика (Lighthouse, axe DevTools, WAVE, тесты в коде) находит примерно треть проблем: отсутствие `alt`, контраст, незаданный `lang`, пустые кнопки. Остальное ручное: осмысленность `alt`, понятность порядка фокуса, работа со скринридером. В проекте есть и то и другое:

- **Автоматика в тестах** (`AccessibilityTest`): ссылка-пропуск на месте, у обеих навигаций есть имена, у языковых ссылок есть `lang`, лайтбокс объявлен диалогом.
- **Ручной чек-лист** (`docs/smoke-checklist.md`) и проверки скриншотами на разной ширине и в обеих темах.

## Практика

::::task Проверьте контраст нового цвета
В дизайн предлагают добавить цвет предупреждений `#b45309` (оранжевый) на белом фоне и `#fbbf24` на тёмном фоне `#292c31`. Используйте калькулятор выше (подставьте цвета) и ответьте: проходят ли пары AA для обычного текста? Если нет, подберите ближайшие варианты.

:::solution
Вычисление по формуле: `#b45309` на `#ffffff` даёт контраст примерно 5,0, а значит, проходит AA (хотя запас небольшой); `#fbbf24` на `#292c31` даёт около 8,4, проходит с запасом. Вывод: пары допустимы. Но урок в другом: **проверять нужно каждую пару в каждой теме**. Если подставить `#fbbf24` на белый, получится около 1,7, то есть красивый на тёмном фоне цвет **непригоден** на светлом. Поэтому в проекте у токенов два набора значений, а не один.

(Заметьте также, что оранжевого в палитре проекта нет по дизайн-решению: «Green is the only colour on the page». Цвет предупреждений нужно было бы согласовать с принципом «один акцент».)
:::
::::

::::task Тест на заголовки
Напишите функцию `headingLevels(html: String): List<Int>` и тест, который проверяет два правила: на странице **ровно один** `h1`, и уровни заголовков **не перескакивают** (после `h1` не может идти `h3`). Протестируйте на хорошей и плохой разметке.

```starter target="junit"
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun headingLevels(html: String): List<Int> {
    // TODO: find <h1>..<h6> opening tags in order and return their levels
    TODO()
}

fun headingProblems(html: String): List<String> {
    // TODO: "expected one h1" and "level jumps from X to Y"
    TODO()
}

class HeadingsTest {
    @Test
    fun `a good page has no problems`() {
        // TODO
    }
}
```

:::hint
`Regex("<h([1-6])[ >]").findAll(html).map { it.groupValues[1].toInt() }.toList()`; затем `zipWithNext()` для пар соседних уровней: скачок, если следующий больше предыдущего больше чем на 1.
:::

:::solution
```test
import kotlin.test.Test
import kotlin.test.assertEquals

fun headingLevels(html: String): List<Int> =
    Regex("<h([1-6])[ >]").findAll(html).map { it.groupValues[1].toInt() }.toList()

fun headingProblems(html: String): List<String> {
    val levels = headingLevels(html)
    val problems = mutableListOf<String>()
    if (levels.count { it == 1 } != 1) {
        problems += "expected one h1, found ${levels.count { it == 1 }}"
    }
    levels.zipWithNext().forEach { (before, after) ->
        if (after > before + 1) problems += "level jumps from $before to $after"
    }
    return problems
}

class HeadingsTest {
    @Test
    fun `a good page has no problems`() {
        assertEquals(emptyList(), headingProblems("<h1>Cats</h1><h2>Adopted</h2><h3>Felix</h3><h2>Help</h2>"))
    }

    @Test
    fun `two h1 are reported`() {
        assertEquals(listOf("expected one h1, found 2"), headingProblems("<h1>A</h1><h1>B</h1>"))
    }

    @Test
    fun `a skipped level is reported`() {
        assertEquals(listOf("level jumps from 1 to 3"), headingProblems("<h1>Cats</h1><h3>Felix</h3>"))
    }

    @Test
    fun `going back up is fine`() {
        assertEquals(emptyList(), headingProblems("<h1>A</h1><h2>B</h2><h3>C</h3><h2>D</h2>"))
    }
}
```

Тест можно применить **ко всем страницам проекта** через `testApplication`: пройти по списку адресов на трёх языках и проверить `headingProblems(body)`. Тогда любая новая страница автоматически проходит проверку структуры. Это пример теста, охраняющего **договорённость**, а не функцию (см. урок про [тестирование](lesson:a3-testing)).
:::
::::

::::task Аудит страницы
В этой разметке не менее семи проблем доступности. Назовите их и как исправить.

```demo title="Страница с проблемами" height="260"
<style>
  body { font: 15px system-ui, sans-serif; margin: 12px; }
  .nav div { display: inline-block; padding: 4px; color: #aaa; cursor: pointer; }
  .tag { background: #fbe9d0; color: #d9a45b; padding: 2px 6px; }
  img { width: 90px; }
</style>
<div class="nav"><div onclick="alert(1)">Cats</div><div onclick="alert(2)">Help</div></div>
<h3>Felix</h3>
<img src="felix.jpg">
<p>Status: <span class="tag">available</span></p>
<p>To adopt, <a href="#">click here</a>.</p>
<input type="text" placeholder="Your email">
<div onclick="alert(3)" style="border:1px solid #ccc;display:inline-block;padding:4px">Send</div>
```

:::solution
1. В `<html>` нет **`lang`** (страница в демо без него).
2. Меню из `div` с `onclick`: **не фокусируется с клавиатуры**, нет роли и имени. Заменить на `nav` со ссылками `a href`.
3. Серый `#aaa` на белом: **контраст около 2,3 : 1**. Поднять до 4,5 : 1 и больше.
4. Заголовок **`h3` без `h1`** и пропущены уровни. Страница должна начинаться с `h1`.
5. `img` **без `alt`**: скринридер прочитает имя файла. Нужен `alt="Felix"` (или пустой, если картинка декоративная).
6. Статус `available` показан **цветом на цвете** (`#d9a45b` на `#fbe9d0`, контраст около 1,9 : 1); смысл должен идти текстом и значком, контраст должен проходить.
7. Ссылка **«click here»** не говорит, куда ведёт. Заменить на содержательное: «Как забрать Феликса».
8. Поле **без `label`** (placeholder не подпись). Добавить `<label for>`.
9. «Send» это `div` с `onclick`: не кнопка. Заменить на `<button type="submit">`.
10. Нет ориентиров (`header`, `main`) и ссылки-пропуска.
:::
::::

:::quiz Проверьте себя
Q: Почему у зелёного акцента в светлой теме тёмный оттенок, а в тёмной светлый?
- [ ] Для разнообразия.
- [x] Один и тот же цвет не может одновременно дать достаточный контраст на белом и на тёмном фоне.
- [ ] Светлые цвета лучше видны на белом.
- [ ] Так проще писать CSS.
E: Контраст зависит от пары цветов. Для каждой темы подбираются свои значения и проверяются по WCAG.

Q: Какой минимальный контраст обычного текста требует уровень WCAG AA?
- [ ] 2 : 1
- [ ] 3 : 1
- [x] 4,5 : 1
- [ ] 21 : 1
E: Для крупного текста достаточно 3 : 1, для обычного 4,5 : 1; AAA требует 7 : 1.

Q: Что даёт `aspect-ratio` у фотографий в карточках, помимо красоты?
- [ ] Меньше размер файла.
- [x] Место резервируется до загрузки: нет сдвига раскладки (улучшается CLS).
- [ ] Лучше контраст.
- [ ] Фото становятся доступнее для скринридера.
E: Браузер знает высоту блока заранее и не перестраивает страницу при загрузке картинки.

Q: Почему фото первого ряда карточек не имеют `loading="lazy"`?
- [ ] Это ошибка.
- [x] Они на первом экране; ленивая загрузка задержала бы главное содержимое (LCP).
- [ ] Первые три фото меньше остальных.
- [ ] Браузер не поддерживает `lazy` для них.
E: `lazy` нужен для того, что ниже первого экрана.

Q: Выберите верные утверждения о ARIA.
- [x] Нативный элемент (`button`, `a`, `label`) лучше, чем `div` с атрибутами ARIA.
- [x] `aria-expanded` сообщает скринридеру, раскрыт ли блок.
- [ ] ARIA добавляет поведение: фокус и обработчики клавиш.
- [ ] Чем больше атрибутов ARIA, тем доступнее страница.
E: ARIA только описывает смысл для вспомогательных технологий. Поведение нужно писать самим, а лишний ARIA вводит в заблуждение.

Q: Что находит автоматическая проверка (Lighthouse, axe) хуже всего?
- [ ] Отсутствие `alt`.
- [ ] Недостаточный контраст.
- [x] Осмысленность подписей и удобство порядка фокуса при работе со скринридером.
- [ ] Пустую кнопку.
E: Машина видит, что атрибут есть, но не понимает, помогает ли он человеку. Это проверяется вручную.

Q: Почему уменьшенные копии фото (`-card.jpg`) проверяются при старте приложения?
- [ ] Чтобы ускорить запуск.
- [x] Нет копии значит страница загрузит тяжёлый оригинал на слабой сети; лучше увидеть проблему при запуске, чем на странице.
- [ ] Копии нужны для лайтбокса.
- [ ] Так требует JPEG.
E: Это та же идея «упасть рано»: ошибку контента ловят до того, как её увидят посетители.
:::

## Итоги

- Доступность — набор проверяемых требований: POUR, WCAG AA, 44 px, видимый фокус, смысл не только цветом.
- Контраст считается формулой; проверяйте каждую пару цветов в каждой теме и записывайте самые слабые.
- Клавиатура и скринридер: вручную хотя бы раз; нативные элементы лучше ARIA.
- Скорость (LCP, CLS, INP) строится на мелочах: копии фото, `lazy`, `aspect-ratio`, шрифты, мало JS.
- Автоматика ловит треть, остальное делают люди.

Часть про UX пройдена. Дальше новая часть: [ИИ-агенты в разработке](lesson:ai1-agents).
