# HTML на сервере: семантика, формы, экранирование

HTML — это не «то, что рисует браузер», а **структура и смысл** страницы. Браузер, поисковая система, скринридер и расширение для перевода читают одну и ту же разметку и делают из неё выводы. Правильная разметка бесплатно даёт доступность, поиск и работоспособность без JavaScript. Неправильная даёт дыры в безопасности.

После урока вы сможете:

- объяснить, почему проект рендерит HTML на сервере, и когда этот выбор не подходит;
- размечать страницу семантическими элементами и ориентирами;
- строить формы с привязанными подписями и ошибками, не теряющие введённое;
- защищать страницу от XSS: экранированием и белым списком адресов.

## Серверный рендеринг

| | Сервер собирает HTML (проект) | Одностраничное приложение (SPA) |
|---|---|---|
| Первый показ | сразу готовая страница | пустая страница, затем скрипт строит содержимое |
| Работает без JS | да | нет |
| Поисковики, предпросмотр ссылок | читают текст сразу | зависят от выполнения скриптов |
| Слабый телефон и сеть | быстро | тяжело: нужно скачать и выполнить много кода |
| Сложная интерактивность | труднее | естественно |

Сайт приюта — это по большей части тексты и фотографии, читаемые с телефонов по слабой связи. Серверный HTML и немного скриптов (меню, тема, галерея) — разумный выбор. Если бы нужен был редактор схем или чат, выбор мог бы быть другим.

## Семантический каркас

В проекте общий каркас всех страниц собирает `pageLayout` ([Layout.kt](repo:src/main/kotlin/org/proanima/shelter/views/Layout.kt)). Если убрать детали, получится вот такая страница:

```html
<html lang="ru">
  <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Кошки · Pro Anima</title>
    <meta name="description" content="...">
  </head>
  <body>
    <a href="#main" class="skip-link">К содержанию</a>
    <header class="site-header">
      <nav class="main-nav" aria-label="Главное меню">...</nav>
      <nav class="lang-switch" aria-label="Язык">...</nav>
    </header>
    <main id="main" class="page">
      <h1>Кошки</h1>
      ...
    </main>
    <footer>...</footer>
  </body>
</html>
```

Что здесь делает каждый элемент и зачем:

- **`lang`**: язык документа. Скринридер выбирает голос и произношение; браузер предлагает перевод; поисковик знает язык. В проекте есть тонкость: для гайда `lang` берётся не из адреса, а из **реального языка текста** (`contentLang`). Пока нет сербского перевода гайда, страница `/sr/guide` показывает английский текст и честно объявляет `lang="en"`.
- **`<title>`**: первое, что видно во вкладке, истории и результатах поиска. В проекте формат единый: «название страницы · Pro Anima».
- **Ориентиры (`header`, `nav`, `main`, `footer`)**: скринридер умеет прыгать между ними. Две навигации (меню и язык) различаются подписью `aria-label`, иначе пользователь услышит «навигация, навигация».
- **Ссылка «К содержанию»** — первая на странице. Она позволяет пользователю клавиатуры пропустить меню. Скрыта, пока не получит фокус.
- **Один `h1`** на страницу, подзаголовки по порядку (`h2`, `h3`) без пропуска уровней: по ним тоже перемещаются.

:::project В проекте
Семантику проверяют тесты. В `AccessibilityTest` ([AccessibilityTest.kt](repo:src/test/kotlin/org/proanima/shelter/routes/AccessibilityTest.kt)) записано, что «каждая страница начинается со ссылки на основное содержимое» и что «обе навигации имеют имена». Доступность перестала быть «кто-то когда-нибудь проверит» и стала автоматической проверкой.
:::

### Подбирайте элемент по смыслу

| Задача | Правильно | Часто делают |
|---|---|---|
| Переход на другую страницу | `<a href>` | `<div onclick>` |
| Действие на странице (открыть меню, сменить тему) | `<button type="button">` | `<a href="#">` или `<span>` |
| Пары «название: значение» (возраст, здоровье) | `<dl><dt><dd>` | таблица или набор `div` |
| Выноска, отделённая от основного текста | `<aside>` | `<div class="box">` |
| Подпись к полю | `<label for>` | текст рядом |

Правильный элемент даёт поведение бесплатно: `button` доступен с клавиатуры (Tab, Enter, пробел), имеет роль для скринридера, работает в формах. `div` с обработчиком клика не даёт ничего из этого, и всё придётся строить руками.

Тексты `alt` у изображений описывают содержимое: у фото кошки это её имя. Если картинка чисто декоративная, `alt=""` (пустой, но присутствующий) говорит скринридеру её пропустить.

## Экранирование и XSS

Представьте имя кошки `<script>stealCookies()</script>`. Если вставить его в страницу как есть, браузер выполнит скрипт у каждого посетителя. Это **XSS** (межсайтовый скриптинг), одна из самых частых уязвимостей.

Защита: любые данные, попадающие в HTML, **экранируются**: `<` становится `&lt;`, `&` становится `&amp;` и так далее. В kotlinx.html это делается по умолчанию:

```kotlin check="test"
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.html.unsafe
import kotlin.test.Test
import kotlin.test.assertEquals

class EscapingTest {
    @Test
    fun `text is escaped and unsafe is not`() {
        val escaped = createHTML(prettyPrint = false).div {
            p { +"<script>alert(1)</script>" }
        }
        assertEquals("<div><p>&lt;script&gt;alert(1)&lt;/script&gt;</p></div>", escaped)

        val raw = createHTML(prettyPrint = false).div {
            unsafe { +"<b>trusted</b>" }
        }
        assertEquals("<div><b>trusted</b></div>", raw)
    }
}
```

Чтобы вставить сырую разметку, нужно **явно** написать `unsafe { ... }`. В проекте `unsafe` встречается считанные разы и только для констант, написанных разработчиком (например, маленький скрипт выбора темы `THEME_INIT_SCRIPT`). Правило: в `unsafe` никогда не попадает ничего, что пришло от пользователя, из базы или из файла с текстами.

### Ссылки: белый список вместо чёрного

Есть и более хитрая угроза. Если текст в админке позволяет писать `[ссылка](адрес)`, то адрес `javascript:alert(1)` при клике выполнит скрипт, хотя ни одного символа `<` в нём нет. Экранирование здесь не поможет.

Проект решает это **белым списком** в [GuideContent.kt](repo:src/main/kotlin/org/proanima/shelter/service/GuideContent.kt):

```kotlin
private val SAFE_LINK_STARTS = listOf("https://", "http://", "mailto:", "/", "#")

fun isSafeGuideLink(href: String): Boolean = SAFE_LINK_STARTS.any { href.startsWith(it) }
```

Разрешено только то, что явно перечислено. Чёрный список («запретить `javascript:`») обходится: `JavaScript:`, ` javascript:` с пробелом, `java&#9;script:` и десятком других способов. С белым списком всё неизвестное отвергается само.

## Форма: HTML + сервер

Форма входа в админку ([AdminViews.kt](repo:src/main/kotlin/org/proanima/shelter/admin/AdminViews.kt)) почти учебная:

```kotlin
form(action = "/admin/login", method = FormMethod.post, classes = "admin-form") {
    div(classes = "field") {
        label { htmlFor = "f-password"; +"Пароль" }
        input(type = InputType.password, name = "password") {
            id = "f-password"
            attributes["autocomplete"] = "current-password"
            attributes["required"] = "required"
            attributes["autofocus"] = "autofocus"
        }
    }
    div(classes = "form-actions") {
        button(type = ButtonType.submit, classes = "button") { +"Войти" }
    }
}
```

Разберём:

- **`label for` + `id`**: подпись привязана к полю. Клик по подписи ставит фокус в поле, скринридер читает подпись при входе в поле. Плейсхолдер **не заменяет** подпись: он исчезает при вводе.
- **`type="password"`** и **`autocomplete="current-password"`**: менеджер паролей понимает, что это за поле, и подставит пароль.
- **`required`** и **`autofocus`**: удобство, а не защита. Браузерную проверку можно обойти, поэтому настоящая проверка всегда на сервере.
- **`method="post"`**: форма меняет состояние (создаёт сессию).

### Ошибки: рядом с полем и без потери ввода

Хорошая форма при ошибке возвращает человека на то же место со всем, что он набрал, и показывает **какое** поле неверно и **почему**. В проекте для этого три маленьких функции ([AdminViews.kt](repo:src/main/kotlin/org/proanima/shelter/admin/AdminViews.kt)):

```kotlin
internal fun markInvalid(input: CommonAttributeGroupFacade, errors: Map<String, String>, name: String) {
    if (name in errors) {
        input.attributes["aria-invalid"] = "true"
        input.attributes["aria-describedby"] = "err-$name"
    }
}

internal fun FlowContent.fieldError(errors: Map<String, String>, name: String) {
    errors[name]?.let { message ->
        p(classes = "field-error") {
            id = "err-$name"
            +message
        }
    }
}
```

`aria-invalid` сообщает скринридеру «поле заполнено неверно», `aria-describedby` связывает поле с текстом ошибки, который будет прочитан вслед за подписью. А над формой выводится сводка с `role="alert"`: список ошибок со **ссылками на поля** (`#f-name`), потому что длинная форма может уйти за экран. Данные для этого уже знакомы вам из урока про [ошибки](lesson:k5-errors-concurrency): `FormResult` хранит ошибки по именам полей.

Соберём это сами и проверим тестом.

```kotlin check="test"
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertTrue

fun FlowContent.textField(name: String, caption: String, values: Map<String, String>, errors: Map<String, String>) {
    div(classes = "field") {
        label { htmlFor = "f-$name"; +caption }
        input(type = InputType.text, name = name) {
            id = "f-$name"
            value = values[name].orEmpty()
            if (name in errors) {
                attributes["aria-invalid"] = "true"
                attributes["aria-describedby"] = "err-$name"
            }
        }
        errors[name]?.let { message ->
            p(classes = "field-error") {
                id = "err-$name"
                +message
            }
        }
    }
}

class FieldTest {
    @Test
    fun `an error is tied to its field and the typed value is kept and escaped`() {
        val html = createHTML(prettyPrint = false).div {
            textField("name", "Name", mapOf("name" to "<b>"), mapOf("name" to "Fill in the name"))
        }

        assertTrue("aria-invalid=\"true\"" in html)
        assertTrue("aria-describedby=\"err-name\"" in html)
        assertTrue("value=\"&lt;b&gt;\"" in html)
        assertTrue("<p class=\"field-error\" id=\"err-name\">Fill in the name</p>" in html)
    }
}
```

### POST, Redirect, GET и фото на отдельной странице

После успешного сохранения админка отвечает `303` и переводит на список (мы разбирали это в уроке про [HTTP](lesson:w1-http-ktor)). А в `docs/decisions.md` есть интересное решение о фотографиях: «Photos have a page of their own, not a field in the form: every photo step reloads the page and would throw away text typed but not saved». Если бы загрузка фото была внутри длинной формы, то каждая загрузка перезагружала бы страницу и стирала несохранённый текст. Дизайн формы определяется тем, что происходит при её отправке.

## Live coding: безопасный текст со ссылками

Админ пишет текст гайда с разметкой `**жирный**` и `[ссылка](адрес)`. Нужно превратить его в безопасный HTML. Повторим `inlineText` из [MarkdownViews.kt](repo:src/main/kotlin/org/proanima/shelter/views/MarkdownViews.kt).

::::live От дырявой функции к безопасной
Мы идём от наивной версии, находим уязвимости и закрываем их по одной.

:::step Шаг 1. Наивные замены
Заменим разметку регулярными выражениями прямо в строке.

```kotlin
fun inlineText(text: String): String = text
    .replace(Regex("\\*\\*([^*]+)\\*\\*"), "<strong>$1</strong>")
    .replace(Regex("\\[([^\\]]+)]\\(([^)\\s]+)\\)"), "<a href=\"$2\">$1</a>")
```

Для `Read the **guide**` работает. Но на входе `<img src=x onerror=alert(1)>` получим живой тег, а `[click](javascript:alert(1))` превратится в ссылку, которая выполняет скрипт. Две дыры.
:::

:::step Шаг 2. Экранируем всё, что не разметка
Идея: идти по тексту, находить только **наши** метки, а остальное экранировать. Теги создаём мы сами из найденных частей.

```kotlin
private val INLINE_MARK = Regex("""\*\*([^*]+)\*\*|\[([^\]]+)]\(([^)\s]+)\)""")

fun escape(text: String) = text
    .replace("&", "&amp;").replace("<", "&lt;")
    .replace(">", "&gt;").replace("\"", "&quot;")
```

Одно регулярное выражение с двумя вариантами (`**…**` или `[…](…)`) находит метки по порядку; `match.range` говорит, где метка начинается и кончается, и текст **между** метками экранируется целиком.
:::

:::step Шаг 3. Белый список для адресов
Адрес из ссылки проверяется до того, как станет `href`.

```kotlin
private val SAFE_LINK_STARTS = listOf("https://", "http://", "mailto:", "/", "#")

fun isSafeLink(href: String) = SAFE_LINK_STARTS.any { href.startsWith(it) }
```

Небезопасная ссылка не создаётся, остаётся только подпись как обычный текст.
:::

:::step Шаг 4. Собираем
В адресе тоже есть символы, которые надо экранировать (`&` в `?a=1&b=2`), поэтому и `href` проходит через `escape`.

```run
private val INLINE_MARK = Regex("""\*\*([^*]+)\*\*|\[([^\]]+)]\(([^)\s]+)\)""")
private val SAFE_LINK_STARTS = listOf("https://", "http://", "mailto:", "/", "#")

fun escape(text: String) = text
    .replace("&", "&amp;").replace("<", "&lt;")
    .replace(">", "&gt;").replace("\"", "&quot;")

fun isSafeLink(href: String) = SAFE_LINK_STARTS.any { href.startsWith(it) }

fun inlineText(text: String): String = buildString {
    var position = 0
    INLINE_MARK.findAll(text).forEach { match ->
        append(escape(text.substring(position, match.range.first)))
        val bold = match.groups[1]?.value
        if (bold != null) {
            append("<strong>").append(escape(bold)).append("</strong>")
        } else {
            val label = escape(match.groupValues[2])
            val href = match.groupValues[3]
            if (isSafeLink(href)) {
                append("<a href=\"").append(escape(href)).append("\">").append(label).append("</a>")
            } else {
                append(label)
            }
        }
        position = match.range.last + 1
    }
    append(escape(text.substring(position)))
}

fun main() {
    println(inlineText("Read the **guide** at [our page](/ru/guide) first."))
    println(inlineText("Click [here](javascript:alert(1)) <b>now</b>"))
    println(inlineText("Write to [Nata](https://t.me/Nata_Rogava?a=1&b=2)"))
}
```

```output
Read the <strong>guide</strong> at <a href="/ru/guide">our page</a> first.
Click here) &lt;b&gt;now&lt;/b&gt;
Write to <a href="https://t.me/Nata_Rogava?a=1&amp;b=2">Nata</a>
```

Разберите вторую строку внимательно:

- ссылка с `javascript:` не стала ссылкой: осталась подпись «here»;
- `<b>now</b>` превращён в безобидный текст;
- лишняя `)` осталась: регулярное выражение `[^)\s]+` останавливается на первой закрывающей скобке, поэтому адреса со скобками внутри в этом диалекте не поддерживаются. Это **осознанное ограничение маленького разбора**. Оно безопасно (мы видим лишний символ, а не дыру), и разработчикам стоит о нём знать.
:::

:::step Шаг 5. Что мы приобрели
Три принципа безопасной генерации HTML, на которых стоит этот код: (1) данные экранируются по умолчанию, (2) теги создаёт только код, а не данные, (3) для адресов используется белый список. Заметьте, что сам парсер короткий, и именно из-за этого его можно прочитать и убедиться в безопасности. Такие тесты в проекте есть, например, на ссылку `javascript:`.
:::
::::

## Практика

::::task Чинить «суп из div»
Перепишите разметку страницы кошки так, чтобы она была семантической: заголовок, пары «название: значение», выноска, кнопка-действие и ссылка-переход.

```html
<div class="top">
  <div class="title">Феликс</div>
  <div class="row"><span>Возраст</span><span>1 год</span></div>
  <div class="row"><span>Где живёт</span><span>В приюте</span></div>
  <div class="note">Пишите координатору @Nata_Rogava</div>
  <div class="link" onclick="location='/ru/cats'">Все кошки</div>
  <div class="button" onclick="share()">Поделиться</div>
</div>
```

:::solution
```html
<main id="main" class="page">
  <h1>Феликс</h1>
  <dl class="facts-list">
    <dt>Возраст</dt><dd>1 год</dd>
    <dt>Где живёт</dt><dd>В приюте</dd>
  </dl>
  <aside class="callout"><p>Пишите координатору @Nata_Rogava</p></aside>
  <p class="back"><a href="/ru/cats">Все кошки</a></p>
  <button type="button" id="share">Поделиться</button>
</main>
```

Что и почему изменилось:

- заголовок стал `h1` (по нему навигируют);
- пары «название: значение» стали списком определений `dl`, как в проекте;
- выноска стала `aside`;
- переход на другую страницу стал настоящей ссылкой `a href` (работает без JavaScript, открывается в новой вкладке по средней кнопке, виден адрес);
- действие стало `button type="button"` (фокусируется, срабатывает от Enter и пробела). Обработчик навешивается скриптом, а не атрибутом `onclick`.
:::
::::

::::task Поле формы с ошибкой
Напишите функцию `FlowContent.numberField(name, caption, values, errors)` для поля «Год рождения» с `type="number"`, привязанной подписью и ошибкой по образцу `textField`. Если в `errors` есть сообщение для поля, оно выводится в `<p class="field-error" id="err-NAME">`, а у поля есть `aria-invalid` и `aria-describedby`.

```kotlin
fun FlowContent.numberField(name: String, caption: String, values: Map<String, String>, errors: Map<String, String>) {
    // TODO: div.field > label[for] + input[type=number] + optional error paragraph
}
```

:::hint
Скопируйте `textField` из урока и поменяйте `InputType.text` на `InputType.number`. Подумайте, нужен ли ещё `min` и `max`? Они помогут браузеру, но проверка на сервере остаётся обязательной.
:::

:::solution
```kotlin check="test"
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertTrue

fun FlowContent.numberField(name: String, caption: String, values: Map<String, String>, errors: Map<String, String>) {
    div(classes = "field") {
        label { htmlFor = "f-$name"; +caption }
        input(type = InputType.number, name = name) {
            id = "f-$name"
            value = values[name].orEmpty()
            if (name in errors) {
                attributes["aria-invalid"] = "true"
                attributes["aria-describedby"] = "err-$name"
            }
        }
        errors[name]?.let { message ->
            p(classes = "field-error") {
                id = "err-$name"
                +message
            }
        }
    }
}

class NumberFieldTest {
    @Test
    fun `the field is a number input tied to its error`() {
        val html = createHTML(prettyPrint = false).div {
            numberField("birthYear", "Year of birth", mapOf("birthYear" to "1999"), mapOf("birthYear" to "Enter a year from 2000"))
        }

        assertTrue("type=\"number\"" in html)
        assertTrue("value=\"1999\"" in html)
        assertTrue("id=\"err-birthYear\"" in html)
        assertTrue("aria-describedby=\"err-birthYear\"" in html)
    }
}
```

Обратите внимание: введённое значение (`1999`) **остаётся** в поле, даже когда оно неверно. Человек увидит, что именно он ввёл, и исправит это, а не начнёт заново.
:::
::::

::::task Ужесточите проверку ссылок
Проверка `isSafeGuideLink` принимает всё, что начинается с `/`. Но `//evil.example` («протокольно-относительный адрес») ведёт на **другой сайт**, а некоторые браузеры воспринимают `/\evil.example` как `//evil.example`. Исправьте функцию: `/` разрешается только как начало пути внутри сайта.

```starter
private val SAFE_LINK_STARTS = listOf("https://", "http://", "mailto:", "/", "#")

fun isSafeLink(href: String): Boolean = SAFE_LINK_STARTS.any { href.startsWith(it) }

fun main() {
    listOf("https://t.me/x", "/ru/guide", "#help", "mailto:a@b.rs", "javascript:alert(1)", "//evil.example", "/\\evil.example", " javascript:alert(1)", "JAVASCRIPT:alert(1)").forEach {
        println("[$it] -> ${isSafeLink(it)}")
    }
}
```

:::solution
```run
private val SAFE_LINK_STARTS = listOf("https://", "http://", "mailto:", "#")

fun isSafeLink(href: String): Boolean =
    SAFE_LINK_STARTS.any { href.startsWith(it) } ||
        (href.startsWith("/") && !href.startsWith("//") && !href.startsWith("/\\"))

fun main() {
    listOf("https://t.me/x", "/ru/guide", "#help", "mailto:a@b.rs", "javascript:alert(1)", "//evil.example", "/\\evil.example", " javascript:alert(1)", "JAVASCRIPT:alert(1)").forEach {
        println("[$it] -> ${isSafeLink(it)}")
    }
}
```

```output
[https://t.me/x] -> true
[/ru/guide] -> true
[#help] -> true
[mailto:a@b.rs] -> true
[javascript:alert(1)] -> false
[//evil.example] -> false
[/\evil.example] -> false
[ javascript:alert(1)] -> false
[JAVASCRIPT:alert(1)] -> false
```

Это не уязвимость выполнения скриптов (чужой сайт не получает права вашего), а риск подмены: редактор гайда может невольно поставить ссылку, выглядящую как внутренняя. В проекте тексты правят доверенные люди, так что риск мал, но привычка закрывать такие щели дешёвая. Заодно видно силу белого списка: пробел в начале и заглавные буквы отвергаются «сами», без специальных правил.
:::
::::

:::quiz Проверьте себя
Q: Чем `<button type="button">` лучше `<div onclick="...">`?
- [ ] Ничем, это одно и то же.
- [x] Кнопка получает фокус, срабатывает от Enter и пробела и имеет роль для скринридера без дополнительного кода.
- [ ] Кнопка быстрее исполняется.
- [ ] `div` нельзя стилизовать.
E: Нативные элементы несут поведение и смысл. Для `div` нужно вручную добавлять `tabindex`, роль, обработчики клавиш.

Q: Для чего у второй навигации на странице нужен `aria-label`?
- [ ] Для красоты.
- [x] Чтобы скринридер отличал «Главное меню» от «Язык» в списке ориентиров.
- [ ] Чтобы поисковик нашёл меню.
- [ ] Чтобы работал CSS.
E: Без подписи оба ориентира называются просто «навигация».

Q: Админ вставил в имя кошки `<script>alert(1)</script>`. Что произойдёт на странице проекта?
- [ ] Скрипт выполнится.
- [ ] Страница упадёт с ошибкой.
- [x] Текст отобразится как есть, без выполнения: kotlinx.html экранирует содержимое по умолчанию.
- [ ] Имя будет удалено.
E: Экранирование `<` и `>` делает из «кода» обычный текст. Для обхода нужно явно написать `unsafe { }`.

Q: Почему для адресов ссылок используется белый список (`SAFE_LINK_STARTS`), а не запрет `javascript:`?
- [ ] Белый список короче.
- [x] Чёрный список обходится вариантами (`JavaScript:`, пробел, кодирование), а всё, чего нет в белом списке, отвергается автоматически.
- [ ] Браузеры не понимают `javascript:`.
- [ ] Белый список быстрее.
E: Безопаснее перечислять разрешённое, чем пытаться предугадать все запрещённое.

Q: Какие утверждения о `required` в поле формы верны?
- [x] Это подсказка браузеру и удобство для человека.
- [x] Сервер всё равно должен проверять значение.
- [ ] Благодаря `required` пустое значение никогда не дойдёт до сервера.
- [ ] `required` заменяет метку `label`.
E: Запрос на сервер можно отправить без браузера (`curl`), поэтому только серверная проверка гарантирует правила.

Q: Зачем в форме при ошибке сохранять введённые значения?
- [ ] Чтобы экономить память сервера.
- [x] Чтобы человек не набирал заново, а видел и правил то, что ввёл.
- [ ] Это требование HTML.
- [ ] Чтобы ускорить загрузку.
E: Потеря введённого — частая причина брошенных форм. В проекте значения форм хранятся «как набрано» именно для этого.

Q: Почему на странице `/sr/guide` стоит `lang="en"`, пока гайда на сербском нет?
- [ ] Это ошибка.
- [x] Атрибут `lang` описывает язык текста, а не адреса; скринридер должен читать английский текст английским голосом.
- [ ] Так быстрее.
- [ ] `lang` ни на что не влияет.
E: `contentLang` отделён от `locale` именно ради такой честности.
:::

## Итоги

- Правильный элемент даёт доступность и поведение бесплатно: `a` для переходов, `button` для действий, `dl`, `aside`, `label`.
- Экранируйте всё, что приходит извне, создавайте теги только кодом, адреса проверяйте белым списком.
- Форма возвращает человека к полю с ошибкой и сохраняет введённое; серверная проверка обязательна.
- Дизайн формы зависит от того, что происходит при отправке: сохранение, редирект, потеря состояния.

Дальше: [CSS: токены, темы, адаптивность, шрифты](lesson:w3-css).
