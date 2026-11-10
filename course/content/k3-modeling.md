# Моделирование предметной области

Хорошая модель данных делает неверные состояния невозможными: компилятор не даст создать то, чего в мире не бывает. Этот урок о том, как Kotlin помогает это делать: `data class`, `enum`, `sealed`, `object`, интерфейсы, делегирование и расширения. Все приёмы взяты из папки `model/` и соседних слоёв.

После урока вы сможете:

- проектировать модели из `data class` с разумными значениями по умолчанию и копировать их через `copy`;
- выбирать между `enum`, `sealed` и обычным классом;
- применять `object` для паттерна «Null Object» и `by` для делегирования;
- писать функции и свойства-расширения, не превращая код в свалку.

## data class: значение, а не идентичность

```run
data class Cat(
    val id: Int,
    val name: String,
    val photoUrls: List<String> = emptyList(),
    val published: Boolean = true
)

fun main() {
    val felix = Cat(1, "Felix")
    val renamed = felix.copy(name = "Felix II", photoUrls = listOf("/images/felix.jpg"))
    println(felix)
    println(renamed)
    println(felix == Cat(1, "Felix"))
    println(felix === Cat(1, "Felix"))
    val (id, name) = felix
    println("$id $name")
    println(felix.hashCode() == Cat(1, "Felix").hashCode())
}
```

```output
Cat(id=1, name=Felix, photoUrls=[], published=true)
Cat(id=1, name=Felix II, photoUrls=[/images/felix.jpg], published=true)
true
false
1 Felix
true
```

Компилятор сам создаёт для `data class`: `toString`, `equals`, `hashCode`, `copy` и функции для деструктуризации (`val (id, name) = felix`). Главное: `==` сравнивает **содержимое**, а `===` проверяет, один ли это объект в памяти. Две кошки с одинаковыми полями равны.

`copy` создаёт новый объект с изменением нужных полей, исходный не меняется. Поэтому админка «меняет» кошку так:

```kotlin
withPhotoUrls = { cat, urls -> cat.copy(photoUrls = urls, updatedAt = admin.nowText()) }
```

Неизменяемые объекты безопаснее при параллельной работе: один запрос не может испортить данные, которые читает другой (подробнее в уроке про [конкурентность](lesson:k5-errors-concurrency)).

### Значения по умолчанию как способ развивать модель

Посмотрите на `Dog` из [Dog.kt](repo:src/main/kotlin/org/proanima/shelter/model/Dog.kt): большинство полей имеют значения по умолчанию.

```kotlin
@Serializable
data class Dog(
    val id: Int,
    val name: LocalizedText,
    val birthYear: Int? = null,
    val description: LocalizedText,
    val photoUrls: List<String> = emptyList(),
    val location: CatLocation = CatLocation.IN_SHELTER,
    val adoptionStage: AdoptionStage = AdoptionStage.NONE,
    // A draft is seen only in the admin panel: the public pages leave it out.
    val published: Boolean = true
)
```

(Здесь показана часть полей.) Когда у собак появились возраст, здоровье и стадия усыновления, **старые записи в базе** не пришлось переписывать: в них этих полей нет, и они читаются со значениями по умолчанию. Это самый дешёвый вид «миграции данных»: добавлять поле с безопасным значением по умолчанию. В уроке про [данные](lesson:k6-data) разберём, как это связано с сериализацией.

:::note Nullable или значение по умолчанию?
`birthYear: Int? = null` говорит «год может быть неизвестен», `published: Boolean = true` говорит «если не указано, считай опубликованным». Первое — отсутствие информации, второе — разумное умолчание. Не смешивайте: `null` в булевом поле означает третье состояние, которого обычно не существует.
:::

## enum: фиксированный набор значений

```run
enum class AppLocale(val code: String, val nativeName: String) {
    RU("ru", "Русский"),
    EN("en", "English"),
    SR("sr", "Srpski");

    companion object {
        val default = RU
        fun byCode(code: String): AppLocale? = entries.firstOrNull { it.code == code }
    }
}

fun main() {
    println(AppLocale.default)
    println(AppLocale.entries.map { it.code })
    println(AppLocale.byCode("sr")?.nativeName)
    println(AppLocale.byCode("de"))
    println(AppLocale.valueOf("EN").ordinal)
}
```

```output
RU
[ru, en, sr]
Srpski
null
1
```

`enum` может иметь свойства и методы, а `companion object` хранит то, что относится к классу в целом (аналог статических членов). Реальный `AppLocale` почти такой же, и от него зависит многое: цикл `AppLocale.entries.forEach { ... route("/${locale.code}") ... }` в `Application.kt` создаёт набор маршрутов для каждого языка. Добавьте новое значение, и сайт получит новый язык (при условии, что есть переводы).

Ещё один пример: `SystemPage` в [Menu.kt](repo:src/main/kotlin/org/proanima/shelter/model/Menu.kt) описывает пять страниц, которыми владеет код. В нём есть вычисляемое свойство:

```kotlin
val nodeId: String get() = "$PAGE_PREFIX$key"
```

Свойство с `get()` не хранит значение, а вычисляет его при каждом обращении.

## sealed: «один из нескольких видов», у каждого свои данные

`enum` хорош, когда у вариантов нет собственных данных. Если у вариантов разные поля, нужен `sealed`: закрытая иерархия, все наследники которой известны компилятору.

Гайд волонтёра разбирается в блоки: заголовок, абзац, список, выноска.

```run
sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class BulletList(val items: List<String>) : Block
    data class Callout(val text: String) : Block
}

fun render(block: Block): String = when (block) {
    is Block.Heading -> "<h${block.level}>${block.text}</h${block.level}>"
    is Block.Paragraph -> "<p>${block.text}</p>"
    is Block.BulletList -> block.items.joinToString("", "<ul>", "</ul>") { "<li>$it</li>" }
    is Block.Callout -> "<aside>${block.text}</aside>"
}

fun main() {
    val page = listOf(
        Block.Heading(2, "Visits"),
        Block.Paragraph("Come on Wednesday."),
        Block.BulletList(listOf("gloves", "water"))
    )
    page.forEach { println(render(it)) }
}
```

```output
<h2>Visits</h2>
<p>Come on Wednesday.</p>
<ul><li>gloves</li><li>water</li></ul>
```

У `Heading` есть `level`, у `BulletList` есть `items`, а у `Paragraph` нет ни того, ни другого. Нельзя создать «заголовок без уровня» или «список без элементов», и нет полей, которые заполнены только у некоторых видов. Кроме того, `when` без ветки `else` проверяется на полноту.

:::project В проекте
Это настоящая модель из [GuideContent.kt](repo:src/main/kotlin/org/proanima/shelter/service/GuideContent.kt): `sealed interface GuideBlock` с четырьмя наследниками. Функция `parseGuideMarkdown` превращает текст гайда в список блоков, а представление (`GuideViews.kt`) рисует каждый блок через `when`. Парсер и вёрстка не знают друг о друге, они договариваются только через эту модель. Разбор парсера как алгоритма: [урок про алгоритмы](lesson:a2-algorithms).
:::

## object и паттерн Null Object

`object` — класс с единственным экземпляром, создаваемым при первом обращении. В проекте он используется для «нулевых» реализаций:

```run
interface SiteContentRepository {
    fun load(): Map<String, String>
}

object NoSiteContent : SiteContentRepository {
    override fun load(): Map<String, String> = emptyMap()
}

class InMemory(private val data: Map<String, String>) : SiteContentRepository {
    override fun load() = data
}

fun title(content: SiteContentRepository): String = content.load()["home.title"] ?: "Default title"

fun main() {
    println(title(NoSiteContent))
    println(title(InMemory(mapOf("home.title" to "Edited title"))))
}
```

```output
Default title
Edited title
```

Когда админка выключена (нет базы данных), страницам всё равно нужен «репозиторий текстов». Вместо `SiteContentRepository?` с проверками `if (content != null)` в десятке мест у проекта есть `object NoSiteContent`, который просто отвечает «ничего не изменено». Это **Null Object**: объект, ведущий себя как «ничего», чтобы остальной код не знал о существовании этого случая. В проекте таких три: `NoSiteContent`, `NoSections`, `NoMenu`.

## Интерфейсы: читать отдельно, писать отдельно

```kotlin
interface CatRepository {
    fun findAll(): List<Cat>
    fun findById(id: Int): Cat?
}

interface MutableCatRepository : CatRepository {
    fun save(cat: Cat): Cat
    fun delete(id: Int): Boolean
}
```

Публичные страницы получают `CatRepository` и физически не могут записать. Админка получает `MutableCatRepository`. Это принцип разделения интерфейсов: клиент зависит только от того, что использует. Побочный эффект: тест публичной части не нужно знакомить с записью.

## Делегирование: by

Иногда нужно «взять объект и изменить одно поведение». Наследование для этого тяжеловесно, а Kotlin позволяет делегировать интерфейс другому объекту:

```run
interface Consumer {
    fun onText(text: String)
    fun onTag(name: String)
}

class Printer : Consumer {
    override fun onText(text: String) = println("text: $text")
    override fun onTag(name: String) = println("tag: $name")
}

// Only onText is changed; onTag is forwarded to the wrapped consumer.
class Shouting(private val downstream: Consumer) : Consumer by downstream {
    override fun onText(text: String) = downstream.onText(text.uppercase())
}

fun main() {
    val consumer = Shouting(Printer())
    consumer.onTag("p")
    consumer.onText("hello")
}
```

```output
tag: p
text: HELLO
```

Именно так в проекте сделана типографика. `TypographicConsumer` в [PageResponse.kt](repo:src/main/kotlin/org/proanima/shelter/views/PageResponse.kt) оборачивает потребитель HTML-событий из kotlinx.html, пропускает все вызовы дальше (`TagConsumer<R> by downstream`) и переопределяет единственный метод `onTagContent`, чтобы привязать предлоги к следующему слову (неразрывный пробел). Десятки методов интерфейса пишутся одной фразой.

## Функции и свойства-расширения

Расширение добавляет функцию «к чужому типу» без наследования. Внутри оно видит только публичный интерфейс типа.

```run
class LocalizedText(val ru: String? = null, val en: String? = null)

val LocalizedText.isEmpty: Boolean get() = ru.isNullOrBlank() && en.isNullOrBlank()

fun LocalizedText?.orUnnamed(): String = this?.en ?: this?.ru ?: "Unnamed"

fun main() {
    val none: LocalizedText? = null
    println(LocalizedText().isEmpty)
    println(LocalizedText(en = "Felix").isEmpty)
    println(none.orUnnamed())
    println(LocalizedText(ru = "Феликс").orUnnamed())
}
```

```output
true
false
Unnamed
Феликс
```

Расширение может быть определено даже для типа, допускающего `null` (`LocalizedText?`): внутри `this` может оказаться `null`, поэтому `this?.en`.

В проекте расширения используются повсюду:

- `fun ResourceBundle.t(key: String, vararg args: Any): String` в `i18n/Messages.kt`: перевод ключа с подстановкой аргументов: `messages.t("cat.page.title")`.
- `fun SectionItem.isShownIn(locale: AppLocale): Boolean` в `SectionService.kt`: правило «запись видна, если опубликована и заголовок есть на этом языке».
- `fun MenuTree.movedUnder(id: String, parent: String?): MenuTree?` в `MenuTrees.kt`: операции над деревом меню лежат рядом, но не в классе данных.

:::warn Расширения не должны превращаться в «ящик с инструментами»
Расширение уместно, когда оно читается как естественное свойство типа (`item.isShownIn(locale)`). Если вы пишете `fun String.doEverythingForAdminForm()`, то прячете обычную функцию за точкой. Бизнес-правила лучше держать в сервисах или рядом с моделью в том же файле.
:::

## Видимость: private и internal

В проекте почти все вспомогательные функции приватные для файла (`private fun duplicates(...)`), а классы вроде `OverlayBundle` объявлены `internal`: они видны внутри модуля, но не снаружи. Правило простое: **делайте видимым минимум**. Каждая публичная функция становится обещанием, которое придётся поддерживать.

## Live coding: от строк к типам

Проследим, как модель гайда могла бы появиться и улучшаться.

::::live Блоки гайда
Мы разбираем текст гайда на блоки и хотим безопасно их показывать.

:::step Шаг 1. Всё строками и null
Самый первый вариант: один класс на все случаи.

```kotlin
data class Block(
    val type: String,
    val text: String? = null,
    val items: List<String>? = null,
    val level: Int? = null
)
```

Что тут плохо? `Block("headin")` с опечаткой компилируется; у заголовка можно забыть `level`; при показе придётся писать `block.items!!` и надеяться.
:::

:::step Шаг 2. Типы вместо строк
Делаем закрытую иерархию. У каждого вида ровно те поля, которые нужны.

```kotlin
sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class BulletList(val items: List<String>) : Block
}
```

Опечатки в названии вида больше невозможны, лишних `null` нет.
:::

:::step Шаг 3. Показ через исчерпывающий when
Функция показа должна знать обо всех видах.

```kotlin
fun render(block: Block): String = when (block) {
    is Block.Heading -> "<h${block.level}>${block.text}</h${block.level}>"
    is Block.Paragraph -> "<p>${block.text}</p>"
    is Block.BulletList -> block.items.joinToString("", "<ul>", "</ul>") { "<li>$it</li>" }
}
```

Внутри ветки `is Block.Heading` компилятор сам знает, что `block` — это `Heading`, и `block.level` доступен (умное приведение).
:::

:::step Шаг 4. Добавляем новый вид и слушаем компилятор
Добавим `Callout` (выноска) в иерархию и не тронем `render`.

```kotlin
data class Callout(val text: String) : Block   // inside sealed interface Block
```

Теперь `render` не компилируется: `'when' expression must be exhaustive`. Компилятор показывает единственное место, которое нужно дополнить. В динамическом коде с `type: String` вы нашли бы его только на странице, где выноска встретилась впервые.
:::

:::step Шаг 5. Итог
```run
sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class BulletList(val items: List<String>) : Block
    data class Callout(val text: String) : Block
}

fun render(block: Block): String = when (block) {
    is Block.Heading -> "<h${block.level}>${block.text}</h${block.level}>"
    is Block.Paragraph -> "<p>${block.text}</p>"
    is Block.BulletList -> block.items.joinToString("", "<ul>", "</ul>") { "<li>$it</li>" }
    is Block.Callout -> "<aside>${block.text}</aside>"
}

fun main() {
    listOf(Block.Callout("Wash your hands."), Block.Heading(3, "Rules")).forEach { println(render(it)) }
}
```

```output
<aside>Wash your hands.</aside>
<h3>Rules</h3>
```

Попробуйте в редакторе удалить ветку `Callout` из `render` и запустить: увидите ошибку компиляции прямо на странице.
:::
::::

## Практика

::::task Статусы визита
В будущем у раздела «Архив визитов» будут записи трёх видов: состоявшийся визит (число волонтёров может быть неизвестно), отменённый (с причиной), дополнительный (с временем выезда). Опишите `sealed interface VisitStatus` и функцию `describe`, которая возвращает текст. Используйте `when` без `else`.

```starter
sealed interface VisitStatus {
    // TODO: Held(volunteers: Int?), Cancelled(reason: String), Extra(departure: String)
}

fun describe(status: VisitStatus): String {
    // TODO: exhaustive when
    TODO()
}

fun main() {
    // TODO: print describe(...) for one status of each kind
}
```

:::solution
```run
sealed interface VisitStatus {
    data class Held(val volunteers: Int?) : VisitStatus
    data class Cancelled(val reason: String) : VisitStatus
    data class Extra(val departure: String) : VisitStatus
}

fun describe(status: VisitStatus): String = when (status) {
    is VisitStatus.Held -> status.volunteers?.let { "Held, $it volunteers" } ?: "Held"
    is VisitStatus.Cancelled -> "Cancelled: ${status.reason}"
    is VisitStatus.Extra -> "Extra trip at ${status.departure}"
}

fun main() {
    println(describe(VisitStatus.Held(5)))
    println(describe(VisitStatus.Held(null)))
    println(describe(VisitStatus.Cancelled("rain")))
    println(describe(VisitStatus.Extra("11:00")))
}
```

```output
Held, 5 volunteers
Held
Cancelled: rain
Extra trip at 11:00
```

Сравните с проектом: сейчас статус визита задан обычным полем-выбором в разделе (`CHOICE` с вариантами «состоялся»/«отменён»), потому что администратор должен уметь добавить свой вариант без программиста. Выбор между `sealed` и данными-в-базе зависит от того, кто меняет набор вариантов: код или человек.
:::
::::

::::task Функция shortened как расширение
На карточке длинный текст обрезается по границе слова и получает многоточие. Напишите `fun String.shortened(limit: Int): String`: если текст не длиннее лимита, вернуть его как есть; иначе взять первые `limit` символов, отрезать незаконченное слово (`substringBeforeLast(' ')`), убрать в конце запятые, точки с запятой, двоеточия, дефисы и пробелы и добавить `…`.

```starter
fun String.shortened(limit: Int): String {
    // TODO
    TODO()
}

fun main() {
    println("Short text".shortened(50))
    println("Cats like to sleep in the sun".shortened(15))
    println("one two, three".shortened(9))
}
```

:::hint
`substringBeforeLast(' ', missingDelimiterValue)` вторым аргументом принимает значение на случай, когда пробела нет (слово длиннее лимита). Передайте туда сам обрезанный текст, чтобы не потерять всё.
:::

:::solution
```run
fun String.shortened(limit: Int): String {
    if (length <= limit) {
        return this
    }
    val cut = take(limit)
    return cut.substringBeforeLast(' ', cut).trimEnd(',', ';', ':', '-', ' ') + "…"
}

fun main() {
    println("Short text".shortened(50))
    println("Cats like to sleep in the sun".shortened(15))
    println("one two, three".shortened(9))
}
```

```output
Short text
Cats like to…
one two…
```

Внутри расширения `this` — строка, и `length`, `take(...)` вызываются без префикса. В проекте функция `shortened(text, limit)` ([SectionService.kt](repo:src/main/kotlin/org/proanima/shelter/service/SectionService.kt)) написана обычной, а не расширением: это вкусовое решение, и оба варианта допустимы.
:::
::::

::::task LocalizedText целиком
Реализуйте `LocalizedText` с методами `forLocale` и `isFilledIn`. Для запрошенного языка берётся его текст; если его нет, то английский, затем русский, затем сербский, а если нет ничего, то пустая строка. `isFilledIn` проверяет, что текст есть именно **для этого языка** (не пустой и не из пробелов).

```starter
enum class AppLocale { RU, EN, SR }

data class LocalizedText(val ru: String? = null, val en: String? = null, val sr: String? = null) {
    fun forLocale(locale: AppLocale): String {
        // TODO
        TODO()
    }

    fun isFilledIn(locale: AppLocale): Boolean {
        // TODO
        TODO()
    }
}

fun main() {
    println(LocalizedText(ru = "Феликс", en = "Felix").forLocale(AppLocale.EN))
    println(LocalizedText(en = "Felix").forLocale(AppLocale.RU))
    println(LocalizedText(sr = "Feliks").forLocale(AppLocale.RU))
    println("[" + LocalizedText().forLocale(AppLocale.SR) + "]")
    println(LocalizedText(ru = "  ").isFilledIn(AppLocale.RU))
}
```

:::hint
Вынесите `when (locale)` в приватную функцию `valueFor(locale): String?`. Тогда обе публичные функции станут короткими.
:::

:::solution
```run
enum class AppLocale { RU, EN, SR }

data class LocalizedText(val ru: String? = null, val en: String? = null, val sr: String? = null) {
    fun forLocale(locale: AppLocale): String = valueFor(locale) ?: en ?: ru ?: sr ?: ""

    fun isFilledIn(locale: AppLocale): Boolean = !valueFor(locale).isNullOrBlank()

    private fun valueFor(locale: AppLocale): String? = when (locale) {
        AppLocale.RU -> ru
        AppLocale.EN -> en
        AppLocale.SR -> sr
    }
}

fun main() {
    println(LocalizedText(ru = "Феликс", en = "Felix").forLocale(AppLocale.EN))
    println(LocalizedText(en = "Felix").forLocale(AppLocale.RU))
    println(LocalizedText(sr = "Feliks").forLocale(AppLocale.RU))
    println("[" + LocalizedText().forLocale(AppLocale.SR) + "]")
    println(LocalizedText(ru = "  ").isFilledIn(AppLocale.RU))
}
```

```output
Felix
Felix
Feliks
[]
false
```

Это почти в точности `LocalizedText` из проекта. Заметьте, что одна цепочка `?:` выражает всю политику запасных языков. Если политика изменится (например, «сначала русский»), правится одна строка, а все страницы подхватят изменение.
:::
::::

:::quiz Проверьте себя
Q: Что выведет код?

```kotlin
data class P(val name: String)
val a = P("x")
val b = P("x")
println(a == b)
println(a === b)
```
- [ ] `false` и `false`
- [ ] `true` и `true`
- [x] `true` и `false`
- [ ] `false` и `true`
E: `==` для `data class` сравнивает поля, а `===` сравнивает ссылки. Это два разных объекта с одинаковым содержимым.

Q: В `Dog` появилось новое поле `val published: Boolean = true`. Что произойдёт с записями, сохранёнными до этого?
- [ ] Их нужно вручную обновить.
- [ ] Чтение упадёт с ошибкой.
- [x] Они прочитаются со значением по умолчанию (`true`).
- [ ] Они станут черновиками.
E: Отсутствующее поле при чтении подменяется значением по умолчанию. Именно поэтому добавление поля с умолчанием не требует миграции.

Q: Когда `sealed` предпочтительнее `enum`?
- [ ] Когда значений больше десяти.
- [x] Когда у разных вариантов разные данные.
- [ ] Когда нужно перебирать все значения.
- [ ] Когда нужен `when`.
E: `enum` — набор одинаково устроенных констант. `sealed` — набор типов, каждый со своими полями. `when` с проверкой полноты работает у обоих.

Q: Зачем в проекте объект `NoSiteContent`, а не `null`?
- [ ] Чтобы сэкономить память.
- [x] Чтобы остальной код не проверял «а есть ли репозиторий текстов» и мог всегда вызывать `load()`.
- [ ] Потому что Kotlin запрещает `null` в интерфейсах.
- [ ] Это требование Ktor.
E: Null Object убирает условные проверки из клиентского кода: он ведёт себя как репозиторий, в котором ничего не изменено.

Q: Что делает запись `class TypographicConsumer<R>(...) : TagConsumer<R> by downstream`?
- [ ] Наследует реализацию от `downstream`.
- [x] Автоматически передаёт вызовы всех методов интерфейса объекту `downstream`, а переопределённые методы заменяет своими.
- [ ] Копирует поля `downstream`.
- [ ] Запрещает переопределять методы.
E: Это делегирование интерфейса. Оно заменяет десятки методов-пробросов и оставляет в классе только то, что он меняет.

Q: Выберите верные утверждения о расширениях.
- [x] Расширение не получает доступа к `private`-членам типа.
- [x] Если у типа есть метод с той же сигнатурой, побеждает метод класса.
- [ ] Расширение изменяет исходный класс.
- [ ] Расширение можно объявить только для классов своего проекта.
E: Расширение — это обычная функция, у которой «получатель» оформлен как параметр. Класс она не меняет, закрытое не видит, а расширять можно и чужие типы (`String`, `ResourceBundle`).
:::

## Итоги

- Описывайте модель так, чтобы неверное состояние нельзя было создать: `sealed`, `enum`, типы без лишних `null`.
- Новое поле со значением по умолчанию — самый дешёвый способ развивать модель с уже сохранёнными данными.
- `object` как Null Object, `by` для делегирования, расширения для естественных операций над типом.
- Минимальная видимость: наружу только то, что нужно.

Дальше: [Лямбды, scope-функции и DSL](lesson:k4-lambdas-dsl).
