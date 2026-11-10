# Лямбды, scope-функции и DSL

Страницы сайта написаны не в шаблонизаторе, а на Kotlin, и выглядят как вложенная разметка: `div { a { +"текст" } }`. Это не магия и не отдельный язык. Это обычные функции, которые принимают лямбду «с получателем». Освоив эту одну идею, вы поймёте kotlinx.html, scope-функции (`apply`, `let`) и половину стандартной библиотеки.

После урока вы сможете:

- писать функции, принимающие функции, и понимать типы вроде `(Int) -> String` и `Div.() -> Unit`;
- выбирать между `let`, `apply`, `also`, `run`, `with`;
- объяснить, как устроен DSL kotlinx.html, и написать свой мини-DSL;
- узнавать паттерн «выполнить вокруг» (транзакция, ресурс, повторная попытка).

## Функции как значения

У функции есть тип: `(Int) -> Int`. Её можно положить в переменную, передать и вернуть.

```run
fun <T> retryOnce(action: () -> T): T = try {
    action()
} catch (e: Exception) {
    println("first attempt failed: ${e.message}")
    action()
}

fun main() {
    var attempts = 0
    val result = retryOnce {
        attempts++
        if (attempts == 1) error("database is asleep")
        "ok after $attempts attempts"
    }
    println(result)

    val twice: (Int) -> Int = { it * 2 }
    println(listOf(1, 2, 3).map(twice))
}
```

```output
first attempt failed: database is asleep
ok after 2 attempts
[2, 4, 6]
```

Что здесь важно:

- Если последний параметр функции — лямбда, её можно вынести за скобки: `retryOnce { ... }`. Это синтаксис «trailing lambda», благодаря которому всё выглядит как встроенные конструкции языка.
- Если у лямбды один параметр, его можно не называть: он называется `it`.
- Лямбда **замыкается** на окружение: она изменяет `attempts` снаружи. Это удобно и опасно: изменяемое состояние внутри лямбд, выполняющихся неизвестно когда или на другом потоке, источник багов.
- Последнее выражение лямбды — её результат.

## Лямбда с получателем: ключ к DSL

Обычный тип: `(Div) -> Unit`: функция получает `Div` как параметр. Тип с получателем: `Div.() -> Unit`: функция выполняется **как будто она метод** `Div`, а сам объект доступен как `this`, и его члены вызываются без префикса.

```kotlin
fun buildPage(init: StringBuilder.() -> Unit): String {
    val builder = StringBuilder()
    builder.init()          // the lambda runs with the builder as `this`
    return builder.toString()
}

val text = buildPage {
    append("Hello, ")       // StringBuilder.append, no prefix needed
    append("world")
}
```

В стандартной библиотеке эта идея спрятана в `buildString { ... }`, `buildList { ... }`, `apply { ... }`. В kotlinx.html — в каждой функции-теге.

## Scope-функции

Пять функций, которые выполняют блок в «области» объекта. Различаются двумя вещами: как внутри блока называется объект (`this` или `it`) и что функция возвращает (сам объект или результат блока).

| Функция | Объект в блоке | Возвращает | Типичное применение |
|---|---|---|---|
| `apply` | `this` | сам объект | настроить объект после создания |
| `also` | `it` | сам объект | побочное действие по пути (лог, проверка) |
| `let` | `it` | результат блока | преобразовать значение, особенно `x?.let { }` |
| `run` | `this` | результат блока | вычислить что-то в контексте объекта |
| `with(x)` | `this` | результат блока | то же, что `run`, но объект передан аргументом |

```run
class Config {
    var url = ""
    var pool = 0
}

fun main() {
    val config = Config().apply {
        url = "jdbc:h2:mem:test"
        pool = 3
    }
    val shown = config.let { "${it.url} (${it.pool})" }
    println(shown)
    val length = "Mila".also { println("processing $it") }.length
    println(length)
    val text = with(config) { "pool=$pool" }
    println(text)
    val maybe: String? = "x"
    println(maybe?.let { it + "!" })
}
```

```output
jdbc:h2:mem:test (3)
processing Mila
4
pool=3
x!
```

:::project Где в проекте
- `apply`: настройка пула соединений в [Database.kt](repo:src/main/kotlin/org/proanima/shelter/repository/Database.kt): `HikariConfig().apply { jdbcUrl = config.jdbcUrl; maximumPoolSize = 3; ... }`.
- `let` с `?.`: «если админка настроена, создай её»: `adminConfigFromEnv()?.let { AdminSetup(it, ...) }` в [Application.kt](repo:src/main/kotlin/org/proanima/shelter/Application.kt).
- `also`: проверка, которая не меняет значение: `loadSeedData(...).also { seed -> failOnProblems(validatePets(...)) }`: данные загрузились, проверили, вернули те же данные.
- `use`: закрывает ресурс при выходе из блока, даже при исключении: `connection.prepareStatement(sql).use { statement -> ... }`.
:::

:::tip Не вкладывайте scope-функции друг в друга
Две вложенные `let` с `it` внутри читать невозможно: непонятно, чей `it`. Если блок сложнее одной-двух строк, дайте параметру имя (`.let { entered -> ... }`, как в `AdminForms.kt`) или вынесите функцию.
:::

## Выполнить вокруг: функция принимает действие

Часто нужно сделать «подготовка, действие, завершение», причём завершение обязательно, даже если действие упало. Вместо того чтобы просить каждого вызывающего помнить о завершении, мы пишем функцию, принимающую действие.

```run
class Connection {
    fun commit() = println("commit")
    fun rollback() = println("rollback")
}

fun <T> inTransaction(block: (Connection) -> T): T {
    val connection = Connection()
    try {
        return block(connection).also { connection.commit() }
    } catch (e: Throwable) {
        connection.rollback()
        throw e
    }
}

fun main() {
    println(inTransaction { "saved" })
    try {
        inTransaction<Unit> { error("constraint violated") }
    } catch (e: IllegalStateException) {
        println("caught: ${e.message}")
    }
}
```

```output
commit
saved
rollback
caught: constraint violated
```

Это упрощённая версия `DataSource.inTransaction` из `Database.kt`: «зафиксировать, когда блок вернулся; откатить, когда он бросил». Забыть `rollback` невозможно, потому что вызывающий его не пишет.

Тот же приём в других местах проекта:

- `SiteMenu.showing(menu) { ... }` ([SiteMenu.kt](repo:src/main/kotlin/org/proanima/shelter/views/SiteMenu.kt)): выставить меню для текущей страницы, выполнить блок, убрать меню даже при ошибке.
- `guarded("sections", emptyList()) { sections.findSections() }` ([MenuService.kt](repo:src/main/kotlin/org/proanima/shelter/service/MenuService.kt)): выполнить чтение, а при исключении залогировать и вернуть запасное значение.
- `call.respondPage(locale) { catsListPage(...) }`: блок описывает страницу, функция `respondPage` добавляет меню и типографику и отправляет ответ.

## Как устроен kotlinx.html

Теперь соберём всё вместе. Реальный код страницы кошек:

```kotlin
div(classes = "cat-list") {
    cats.forEachIndexed { index, cat ->
        val name = displayCatName(cat.name, locale)
        a(href = "$prefix/cats/${petSlug(cat.name?.en, cat.id)}", classes = "cat-card") {
            cardPhoto(displayPhotoUrls(cat.photoUrls).first(), name, index)
            span(classes = "cat-card-name") { +name }
        }
    }
}
```

Что здесь на самом деле происходит:

1. `div(classes = "cat-list") { ... }` — функция-расширение с лямбдой `DIV.() -> Unit`. Внутри лямбды `this` — это `DIV`, поэтому вызов `a(...)` — это `this.a(...)`, метод, доступный на `DIV`.
2. Внутри лямбды работает **обычный Kotlin**: `forEachIndexed`, `val`, `if`. Поэтому в шаблоне нет отдельного языка циклов и условий: HTML генерируется кодом.
3. `+name` — это `unaryPlus` для строки, определённый как оператор внутри тега: «добавить текст». Текст при этом **экранируется** (`<` превращается в `&lt;`). Вставить разметку случайно нельзя.
4. Функции вроде `cardPhoto(...)` — это расширения на `FlowContent`. Любая функция, объявленная как `fun FlowContent.something()`, может вызываться внутри любого тега, содержащего «потоковое» содержимое. Так в проекте строятся «компоненты»: `catList`, `photoGallery`, `navigation`, `pageLayout` — всё это обычные функции.

Страницы проекта не получают шаблон из файла. Они **собираются из вызовов функций**, и компилятор проверяет их: тег `<li>` не получится положить вне списка, атрибут не получится написать с опечаткой, а переименование функции найдёт все использования.

## Live coding: свой мини-DSL

Напишем игрушечную версию kotlinx.html, и вы увидите, что в ней нет ничего, кроме лямбд с получателем и одного оператора.

::::live Мини-DSL для HTML
Цель: получить код вида `div("cat-list") { a("/ru/cats/felix") { +"Felix" } }`, который возвращает строку HTML.

:::step Шаг 1. Строки руками
Первая попытка, как её написал бы любой новичок.

```kotlin
fun catLink(name: String) = "<a href=\"/ru/cats/$name\">$name</a>"
```

Проблемы: кавычки в кавычках, нет экранирования (имя `<script>` станет живым тегом, это дыра XSS), вложенность придётся собирать склейкой строк.
:::

:::step Шаг 2. Класс-тег и лямбда с получателем
Тег знает своё имя, атрибуты и детей. Блок инициализации выполняется с тегом в роли `this`.

```kotlin
class Div {
    val children = mutableListOf<String>()
    fun text(value: String) { children += value }
    fun render() = "<div>${children.joinToString("")}</div>"
}

fun div(init: Div.() -> Unit): String = Div().apply(init).render()

// usage: div { text("hello") }  ->  <div>hello</div>
```

`Div().apply(init)` создаёт объект, выполняет на нём блок и возвращает сам объект. Здесь `apply` как раз «настроить после создания».
:::

:::step Шаг 3. +"текст" и экранирование
Оператор `unaryPlus` делает запись короче, а экранирование спрятано внутри.

```kotlin
operator fun String.unaryPlus() { children += escape(this) }

private fun escape(text: String) = text
    .replace("&", "&amp;").replace("<", "&lt;")
    .replace(">", "&gt;").replace("\"", "&quot;")
```

Теперь `+"<script>"` превращается в `&lt;script&gt;`. Безопасность по умолчанию: чтобы вставить сырую разметку, нужно сделать это явно (в kotlinx.html для этого есть `unsafe { }`).
:::

:::step Шаг 4. Вложенные теги и атрибуты
Общий базовый класс `Tag`, у `Div` функции `a` и `div`. Каждая принимает свой блок.

```kotlin
class Div : Tag("div") {
    fun a(href: String, init: A.() -> Unit) =
        add(A().apply { attr("href", href) }, init)
    fun div(classes: String? = null, init: Div.() -> Unit) =
        add(Div().apply { classes?.let { attr("class", it) } }, init)
}
```

Вложенность получается сама: блок внутри `a { ... }` — лямбда с получателем `A`, блок внутри `div { ... }` — с получателем `Div`.
:::

:::step Шаг 5. @DslMarker: защита от лишнего
Без пометки внутри `a { ... }` можно было бы вызвать `a { ... }` ещё раз: Kotlin нашёл бы `a` у внешнего `Div`, и получились бы ссылки внутри ссылок. Аннотация `@DslMarker` запрещает неявный доступ к получателю внешнего блока.

```kotlin
@DslMarker
annotation class HtmlDsl

@HtmlDsl
abstract class Tag(private val name: String) { /* ... */ }
```

kotlinx.html использует такой же приём (`@HtmlTagMarker`).
:::

:::step Шаг 6. Всё вместе
```run
@DslMarker
annotation class HtmlDsl

private fun escape(text: String) = text
    .replace("&", "&amp;").replace("<", "&lt;")
    .replace(">", "&gt;").replace("\"", "&quot;")

@HtmlDsl
abstract class Tag(private val name: String) {
    private val attributes = linkedMapOf<String, String>()
    private val children = mutableListOf<String>()

    fun attr(key: String, value: String) {
        attributes[key] = value
    }

    operator fun String.unaryPlus() {
        children += escape(this)
    }

    protected fun <T : Tag> add(tag: T, init: T.() -> Unit): T {
        tag.init()
        children += tag.render()
        return tag
    }

    fun render(): String {
        val attrs = attributes.entries.joinToString("") { " ${it.key}=\"${escape(it.value)}\"" }
        return "<$name$attrs>${children.joinToString("")}</$name>"
    }
}

class A : Tag("a")

class Div : Tag("div") {
    fun a(href: String, init: A.() -> Unit) = add(A().apply { attr("href", href) }, init)
    fun div(classes: String? = null, init: Div.() -> Unit) =
        add(Div().apply { classes?.let { attr("class", it) } }, init)
}

fun div(classes: String? = null, init: Div.() -> Unit): String =
    Div().apply { classes?.let { attr("class", it) } }.apply(init).render()

fun main() {
    val html = div("cat-list") {
        a("/ru/cats/felix") { +"Felix & friends" }
        div("note") { +"<script>alert(1)</script>" }
    }
    println(html)
}
```

```output
<div class="cat-list"><a href="/ru/cats/felix">Felix &amp; friends</a><div class="note">&lt;script&gt;alert(1)&lt;/script&gt;</div></div>
```

Попробуйте внутри `a("/x") { ... }` вызвать ещё один `a("/y") { }`: из-за `@DslMarker` код не скомпилируется. Уберите аннотацию и запустите снова: компилироваться станет, но получится невалидный HTML.
:::
::::

## Практика

::::task Повтор с ограничением
Обобщите `retryOnce`: `retry(times: Int, action: () -> T): T` пробует до `times` раз, печатает `attempt N failed: сообщение` после каждой неудачи (кроме последней), а при провале последней попытки пробрасывает исключение.

```starter
fun <T> retry(times: Int, action: () -> T): T {
    // TODO: loop over attempts, catch Exception, rethrow on the last one
    TODO()
}

fun main() {
    var calls = 0
    val value = retry(3) {
        calls++
        if (calls < 3) error("not yet ($calls)")
        "done on call $calls"
    }
    println(value)
}
```

:::hint
Цикл `repeat(times - 1) { attempt -> try { return action() } catch (e: Exception) { println(...) } }` обрабатывает все попытки, кроме последней. После цикла вызовите `action()` ещё раз без перехвата.
:::

:::solution
```run
fun <T> retry(times: Int, action: () -> T): T {
    repeat(times - 1) { index ->
        try {
            return action()
        } catch (e: Exception) {
            println("attempt ${index + 1} failed: ${e.message}")
        }
    }
    return action()
}

fun main() {
    var calls = 0
    val value = retry(3) {
        calls++
        if (calls < 3) error("not yet ($calls)")
        "done on call $calls"
    }
    println(value)
}
```

```output
attempt 1 failed: not yet (1)
attempt 2 failed: not yet (2)
done on call 3
```

Обратите внимание на `return action()` внутри лямбды `repeat`. Это **нелокальный возврат**: `repeat` объявлена как `inline`, поэтому `return` выходит из окружающей функции `retry`, а не только из лямбды. Для обычных (не `inline`) лямбд такой `return` не разрешён.
:::
::::

::::task Список через buildString
Напишите `bulletList(items: List<String>): String`, возвращающую `<ul><li>a</li><li>b</li></ul>`, с помощью `buildString { ... }`. Пустой список даёт пустую строку.

```starter
fun bulletList(items: List<String>): String {
    // TODO: buildString { append(...) }
    TODO()
}

fun main() {
    println(bulletList(listOf("gloves", "water")))
    println("[" + bulletList(emptyList()) + "]")
}
```

:::solution
```run
fun bulletList(items: List<String>): String {
    if (items.isEmpty()) return ""
    return buildString {
        append("<ul>")
        items.forEach { append("<li>").append(it).append("</li>") }
        append("</ul>")
    }
}

fun main() {
    println(bulletList(listOf("gloves", "water")))
    println("[" + bulletList(emptyList()) + "]")
}
```

```output
<ul><li>gloves</li><li>water</li></ul>
[]
```

`buildString` — та же идея лямбды с получателем: внутри блока `this` — это `StringBuilder`, поэтому `append` вызывается без префикса. Подумайте, чего не хватает для настоящего кода: экранирования (`it` нужно прогнать через `escape`), иначе список с элементом `<b>` ломает страницу.
:::
::::

::::task Расширьте DSL: список
Добавьте в мини-DSL теги `ul` и `li`. Внутри `Div` должна работать запись `ul { li { +"gloves" } }`, а `li` должен быть доступен только внутри `ul`.

```starter
@DslMarker
annotation class HtmlDsl

@HtmlDsl
abstract class Tag(private val name: String) {
    private val children = mutableListOf<String>()

    operator fun String.unaryPlus() {
        children += this
    }

    protected fun <T : Tag> add(tag: T, init: T.() -> Unit): T {
        tag.init()
        children += tag.render()
        return tag
    }

    fun render(): String = "<$name>${children.joinToString("")}</$name>"
}

// TODO: class Li, class Ul with fun li(...), and fun ul(...) inside Div

class Div : Tag("div")

fun div(init: Div.() -> Unit): String = Div().apply(init).render()

fun main() {
    println(div {
        // TODO: ul { li { +"gloves" }; li { +"water" } }
    })
}
```

:::hint
`class Ul : Tag("ul") { fun li(init: Li.() -> Unit) = add(Li(), init) }`. В `Div` добавьте `fun ul(init: Ul.() -> Unit) = add(Ul(), init)`. Класс `Div` придётся переписать с телом.
:::

:::solution
```run
@DslMarker
annotation class HtmlDsl

@HtmlDsl
abstract class Tag(private val name: String) {
    private val children = mutableListOf<String>()

    operator fun String.unaryPlus() {
        children += this
    }

    protected fun <T : Tag> add(tag: T, init: T.() -> Unit): T {
        tag.init()
        children += tag.render()
        return tag
    }

    fun render(): String = "<$name>${children.joinToString("")}</$name>"
}

class Li : Tag("li")

class Ul : Tag("ul") {
    fun li(init: Li.() -> Unit) = add(Li(), init)
}

class Div : Tag("div") {
    fun ul(init: Ul.() -> Unit) = add(Ul(), init)
}

fun div(init: Div.() -> Unit): String = Div().apply(init).render()

fun main() {
    println(div {
        ul {
            li { +"gloves" }
            li { +"water" }
        }
    })
}
```

```output
<div><ul><li>gloves</li><li>water</li></ul></div>
```

Блок `li { ... }` доступен только внутри `ul { ... }`, потому что функция `li` объявлена в классе `Ul`. Это и есть ограничение «ссылка не вне абзаца» на уровне типов: невалидную вложенность невозможно записать.
:::
::::

:::quiz Проверьте себя
Q: Каков тип лямбды `{ x: Int -> x * 2 }`?
- [ ] `Int`
- [x] `(Int) -> Int`
- [ ] `Int.() -> Int`
- [ ] `() -> Int`
E: Лямбда принимает `Int` и возвращает `Int`. Запись `Int.() -> Int` означала бы лямбду с получателем.

Q: В чём разница между `let` и `apply`?
- [ ] `let` работает только с `null`.
- [x] `let` возвращает результат блока, `apply` возвращает сам объект; в `let` объект называется `it`, в `apply` это `this`.
- [ ] `apply` создаёт копию объекта.
- [ ] Разницы нет.
E: `apply` для настройки объекта (результат — он сам), `let` для преобразования (результат — то, что вернул блок).

Q: В `div { a(...) { +"текст" } }` откуда берётся функция `a`?
- [ ] Это глобальная функция.
- [x] Блок `div` — лямбда с получателем `DIV`, поэтому `a(...)` — это `this.a(...)`, функция, доступная на `DIV`.
- [ ] Это ключевое слово языка.
- [ ] Её находит рефлексия.
E: Всё устроено на обычных функциях. Лямбда с получателем делает члены получателя доступными без префикса.

Q: Зачем нужна функция вроде `inTransaction(block)`, а не просто `commit()` и `rollback()` у соединения?
- [ ] Это быстрее.
- [x] Вызывающий не может забыть откат: завершение гарантируется структурой, а не дисциплиной.
- [ ] Так требует JDBC.
- [ ] Чтобы не использовать исключения.
E: Это паттерн «выполнить вокруг»: подготовка и завершение живут в одной функции, а вызывающий пишет только полезное действие.

Q: Что делает `@DslMarker`?
- [ ] Ускоряет компиляцию.
- [ ] Запрещает использовать лямбды.
- [x] Не даёт неявно вызывать функции внешнего получателя из вложенного блока, чтобы нельзя было написать, например, ссылку внутри ссылки.
- [ ] Делает класс `sealed`.
E: Внутри вложенного блока по умолчанию доступны члены всех внешних получателей. Маркер ограничивает видимость ближайшим получателем.

Q: Что выведет код?

```kotlin
fun f() {
    listOf(1, 2, 3).forEach {
        if (it == 2) return
        println(it)
    }
    println("after")
}
f()
```
- [ ] `1`, `3`, `after`
- [x] `1`
- [ ] `1`, `after`
- [ ] Не скомпилируется.
E: `forEach` — `inline`, поэтому `return` внутри лямбды выходит из всей функции `f`. Печатается только `1`, и `after` не достигается. Чтобы выйти из одной итерации, используйте `return@forEach`.
:::

## Итоги

- Лямбда с получателем (`T.() -> Unit`) — основа DSL: блок исполняется «как метод» объекта.
- Scope-функции различаются тем, как называется объект и что возвращается. Не вкладывайте их.
- «Выполнить вокруг» (`inTransaction`, `use`, `showing`) гарантирует завершение и убирает забываемый код.
- Страницы сайта — это функции: компилятор проверяет разметку, экранирование включено по умолчанию, компоненты — просто функции-расширения.

Дальше: [Ошибки, ресурсы и конкурентность](lesson:k5-errors-concurrency).