# Основы Kotlin и null-safety

Kotlin — язык, в котором многие ошибки Java и JavaScript просто не компилируются. Самая важная из них — обращение к `null`. В этом уроке разберём основы языка на коротких функциях сайта: показ имени кошки, адрес страницы из имени, возраст, выбор текста по языку.

После урока вы сможете:

- объявлять значения (`val`, `var`) и функции, пользоваться шаблонами строк и значениями по умолчанию;
- писать `if` и `when` как выражения и понимать, почему это удобно;
- работать с типами, допускающими `null`: `?.`, `?:`, `let`, `takeIf`, умные приведения;
- объяснить, чем `!!` опасен и когда его появление в проекте оправдано.

## val и var

`val` — значение, которое нельзя переприсвоить. `var` — переменная. По умолчанию пишите `val`: код, где значения не меняются, проще читать и проверять.

```run
fun main() {
    val shelter = "Pro Anima"          // val: cannot be reassigned
    var visitors = 3                   // var: can be reassigned
    visitors += 2
    println("$shelter has $visitors volunteers today")
    println("Half of them: ${visitors / 2}")
}
```

```output
Pro Anima has 5 volunteers today
Half of them: 2
```

Обратите внимание:

- Типы выводятся сами: `shelter` — `String`, `visitors` — `Int`. Писать `val shelter: String` можно, но обычно не нужно.
- `"$name"` и `"${выражение}"` — шаблоны строк вместо склейки через `+`.
- `5 / 2` — целочисленное деление и даёт `2`. Для дробного нужно `5 / 2.0`.

:::warn val не значит «неизменяемый объект»
`val list = mutableListOf(1)` запрещает присвоить `list` другой список, но `list.add(2)` работать будет: изменяемый сам объект. Неизменяемость значения и неизменяемость переменной — разные вещи. В проекте модели (`Cat`, `Dog`) сделаны из `val`-полей и `List`, поэтому «изменить» кошку можно только создав копию (`copy`), и об этом поговорим в уроке про [моделирование](lesson:k3-modeling).
:::

## Всё, что можно, — выражение

В Kotlin `if` и `when` возвращают значение. Поэтому часто не нужны временные переменные и `return` в каждой ветке.

```run
enum class AdoptionStage { NONE, FOR_ADOPTION, RESERVED, ADOPTED }

fun label(stage: AdoptionStage): String = when (stage) {
    AdoptionStage.NONE -> "-"
    AdoptionStage.FOR_ADOPTION -> "Looking for a home"
    AdoptionStage.RESERVED -> "Reserved"
    AdoptionStage.ADOPTED -> "Found a home"
}

fun main() {
    val stage = AdoptionStage.RESERVED
    val text = if (stage == AdoptionStage.NONE) "no status" else label(stage)
    println(text)
    println(AdoptionStage.entries.map(::label))
}
```

```output
Reserved
[-, Looking for a home, Reserved, Found a home]
```

Три вещи, за которые стоит полюбить `when`:

1. **Исчерпывающий `when` по `enum` и `sealed`.** Если вы добавите в `AdoptionStage` значение и забудете обработать его, код не скомпилируется. Никакого «забыли ветку `default`» в продакшене.
2. `when` заменяет цепочки `if / else if`, не нагромождая скобки.
3. Функция из одного выражения записывается через `=`, без `{ return ... }`.

:::project В проекте
Так устроен `displayAdoptionStage` в [DisplayHelpers.kt](repo:src/main/kotlin/org/proanima/shelter/service/DisplayHelpers.kt): `when` по `AdoptionStage` выбирает ключ сообщения. Добавите стадию, например `QUARANTINE`, и компилятор сам покажет все места, которые нужно обновить.

```kotlin
val key = when (stage) {
    AdoptionStage.NONE -> "$animal.adoptionStage.none"
    AdoptionStage.FOR_ADOPTION -> "$animal.adoptionStage.forAdoption"
    AdoptionStage.RESERVED -> "$animal.adoptionStage.reserved"
    AdoptionStage.ADOPTED -> "$animal.adoptionStage.adopted"
}
```
:::

## Функции: значения по умолчанию и именованные аргументы

```kotlin
fun displayCatAge(
    birthYear: Int?,
    birthMonth: Int?,
    locale: AppLocale,
    today: LocalDate = LocalDate.now()
): String
```

Параметр `today` имеет значение по умолчанию. Обычный код вызывает `displayCatAge(2020, null, locale)`, а тест подставляет фиксированную дату: `displayCatAge(2020, null, locale, today = LocalDate.of(2026, 6, 1))`. Так «сегодняшняя дата» перестаёт быть скрытой зависимостью, и результат можно проверить завтра, через год, в любой день. Эта идея будет ключевой в уроке про [тестирование](lesson:a3-testing).

Именованные аргументы (`today = ...`) делают вызов читаемым, особенно с флагами: `displayAdoptionStage(stage, locale, forDog = true)` понятнее, чем `(stage, locale, true)`.

Функции в Kotlin можно объявлять на верхнем уровне файла, без класса. Большинство вспомогательных функций проекта (`petSlug`, `cardPhotoUrl`, `shortened`) именно такие. Константы пишутся как `const val`:

```kotlin
private const val ONE_HOUR_SECONDS = 3600
private const val ONE_DAY_SECONDS = 24 * 3600
```

## Null-safety: главная причина любить Kotlin

В Kotlin тип `String` не может быть `null`. Если значение может отсутствовать, это видно в типе: `String?`. Компилятор не даст обратиться к такому значению «как есть».

Посмотрите на реальное поле: у кошки может не быть имени, поэтому `Cat.name` имеет тип `LocalizedText?`, а у собаки имя обязательно (`Dog.name: LocalizedText`). Различие записано в типах, и не нужно искать в документации.

Операторы для работы с `null`:

| Запись | Что делает |
|---|---|
| `x?.length` | безопасный вызов: если `x == null`, результат `null`, иначе длина |
| `x ?: "по умолчанию"` | «элвис»: если слева `null`, берётся правая часть |
| `x!!` | утверждение «здесь точно не `null`»; если ошибся, будет исключение |
| `x?.let { ... }` | выполнить блок, только если значение есть |
| `x?.takeIf { условие }` | вернуть `x`, если условие истинно, иначе `null` |
| `x.orEmpty()` | для `String?`: пустая строка вместо `null` |

Соберём настоящую функцию проекта по частям:

```run
class LocalizedText(val ru: String? = null, val en: String? = null, val sr: String? = null)

fun displayName(name: LocalizedText?, language: String): String {
    val text = when (language) {
        "ru" -> name?.ru
        "en" -> name?.en
        else -> name?.sr
    }
    return text?.ifBlank { null } ?: "Unnamed"
}

fun main() {
    val felix = LocalizedText(ru = "Феликс", en = "Felix")
    println(displayName(felix, "ru"))
    println(displayName(felix, "sr"))
    println(displayName(LocalizedText(en = "  "), "en"))
    println(displayName(null, "ru"))
}
```

```output
Феликс
Unnamed
Unnamed
Unnamed
```

Разберите последнюю строку функции слева направо: `text?.ifBlank { null }` превращает пустую или состоящую из пробелов строку в `null`; `?: "Unnamed"` подставляет запасное значение. Пустая строка и `null` для пользователя одно и то же («имени нет»), и в одном выражении мы обработали оба случая.

### Умные приведения и let

```run
fun main() {
    val raw: String? = "  Mila  "
    if (raw != null) {
        println(raw.trim().length)       // smart cast: raw is a plain String here
    }
    raw?.let { println("Hello, ${it.trim()}") }

    println("12a".toIntOrNull())
    println("2026".toIntOrNull()?.takeIf { it in 2000..2100 })
    println("1999".toIntOrNull()?.takeIf { it in 2000..2100 })
}
```

```output
4
Hello, Mila
null
2026
null
```

После проверки `raw != null` компилятор сам считает `raw` непустым внутри блока (умное приведение, smart cast). Функции `toIntOrNull`, `firstOrNull`, `getOrNull` — стандартный способ получить «значение или `null`» вместо исключения.

:::project В проекте
Проверка года рождения в форме админки ([AdminForms.kt](repo:src/main/kotlin/org/proanima/shelter/admin/AdminForms.kt), функция `birth`) использует ровно эту цепочку:

```kotlin
val year = text("birthYear").takeIf { it.isNotEmpty() }?.let { entered ->
    entered.toIntOrNull()?.takeIf { it in EARLIEST_BIRTH_YEAR..today.year }
        ?: null.also { fail("birthYear", "Укажите число от $EARLIEST_BIRTH_YEAR до ${today.year}.") }
}
```

Читайте так: если поле не пустое, пробуем превратить текст в число; если число попало в допустимый диапазон, оставляем его; иначе записываем ошибку и возвращаем `null`. Ни одного `if`, и при этом все случаи обработаны.
:::

### Когда `!!` допустим

`!!` превращает «возможно `null`» в «точно не `null`» ценой исключения, если вы ошиблись. В коде проекта он встречается, например, в маршруте:

```kotlin
get("/cats/{slug}") {
    val slug = call.parameters["slug"]!!
    ...
}
```

Параметр `slug` есть в шаблоне маршрута, поэтому Ktor не вызовет этот обработчик без него. Тут `!!` — осознанное утверждение, подкреплённое самим маршрутом. В остальных случаях лучше `?:` с понятной ошибкой или значением по умолчанию: `?: error("slug is required")` объясняет сбой, а `NullPointerException` нет.

## Диапазоны и проверки

```kotlin
if (cat.birthMonth != null && cat.birthMonth !in 1..12) { ... }
```

`1..12` — диапазон, включающий оба края; `in` и `!in` проверяют вхождение. Этот же синтаксис работает со строками (`'a'..'z'`), датами и любыми сравнимыми типами. Для «до, не включая» есть `1 until 12`.

## Live coding: функция petSlug

Адрес кошки строится из её английского имени: `Felix` → `/cats/felix`. Напишем функцию, как это делали в проекте: сначала просто, потом исправляя ошибки.

::::live Адрес страницы из имени
Правила: только строчные латинские буквы и цифры, остальное превращается в дефис, дефисов по краям нет, а если ничего не осталось, адресом будет номер.

:::step Шаг 1. Самая простая версия
Заменим пробелы дефисами и приведём к нижнему регистру.

```kotlin
fun petSlug(name: String): String = name.lowercase().replace(" ", "-")
```

Для `"Felix"` работает. Для `"Mila's Kitten!"` получится `mila's-kitten!`: апостроф и восклицательный знак в адресе нам не нужны.
:::

:::step Шаг 2. Регулярное выражение вместо пробела
Любая последовательность символов, не являющихся латинской буквой или цифрой, становится одним дефисом.

```kotlin
fun petSlug(name: String): String =
    name.lowercase().replace(Regex("[^a-z0-9]+"), "-")
```

Теперь `"Mila's Kitten!"` даёт `mila-s-kitten-`. Дефис на конце лишний.
:::

:::step Шаг 3. Обрезаем дефисы по краям
`trim('-')` убирает указанный символ с начала и конца.

```kotlin
fun petSlug(name: String): String =
    name.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
```

`mila-s-kitten`. Теперь подумаем о том, чего ещё мы не учли: имени может не быть, а у кошки «Феликс» без латинского имени весь результат окажется пустым.
:::

:::step Шаг 4. Имя может отсутствовать, а результат пустовать
Принимаем `String?`, используем `orEmpty()`, а пустой результат заменяем номером записи.

```run
fun petSlug(name: String?, id: Int): String {
    val slug = name.orEmpty()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    return slug.ifBlank { id.toString() }
}

fun main() {
    println(petSlug("Felix", 1))
    println(petSlug("Mila's Kitten!", 2))
    println(petSlug("Феликс", 3))
    println(petSlug(null, 4))
}
```

```output
felix
mila-s-kitten
3
4
```

Это почти буква в букву функция `petSlug` из [DisplayHelpers.kt](repo:src/main/kotlin/org/proanima/shelter/service/DisplayHelpers.kt). Обратите внимание: каждая «заплатка» появилась потому, что мы подумали о реальном вводе. Этот навык (искать граничные случаи) важнее знания синтаксиса, и мы потренируем его в уроке про [тестирование](lesson:a3-testing).
:::

:::step Шаг 5. Что мы выиграли от Kotlin
Посмотрите, чего нет в итоговой функции: проверок `if (name == null)`, временных переменных, циклов по символам, `StringBuilder`. Каждая строка — одно преобразование, и код читается сверху вниз как рецепт. Это и есть «выразительность» Kotlin, о которой говорят: не меньше кода любой ценой, а меньше посторонних деталей.
:::
::::

## Практика

::::task Возраст по году рождения
Напишите `displayAge(birthYear: Int?, currentYear: Int): String`. Для `null` верните `"Age unknown"`, для разницы в 0 лет `"Under a year"`, для 1 года `"1 year"`, для остальных `"N years"`. Используйте `when` как выражение.

```starter
fun displayAge(birthYear: Int?, currentYear: Int): String {
    // TODO: return the text according to the rules above
    TODO()
}

fun main() {
    println(displayAge(null, 2026))
    println(displayAge(2026, 2026))
    println(displayAge(2025, 2026))
    println(displayAge(2020, 2026))
}
```

:::hint
Сначала отсеките `null` через `?: return ...` или обычный `if`. Затем `when (val years = currentYear - birthYear) { ... }`: в `when` можно объявить значение прямо в скобках.
:::

:::solution
```run
fun displayAge(birthYear: Int?, currentYear: Int): String {
    if (birthYear == null) return "Age unknown"
    return when (val years = currentYear - birthYear) {
        0 -> "Under a year"
        1 -> "1 year"
        else -> "$years years"
    }
}

fun main() {
    println(displayAge(null, 2026))
    println(displayAge(2026, 2026))
    println(displayAge(2025, 2026))
    println(displayAge(2020, 2026))
}
```

```output
Age unknown
Under a year
1 year
6 years
```

В настоящей `displayCatAge` то же самое, только тексты берутся из файлов сообщений и учитывается месяц рождения.
:::
::::

::::task Безопасный разбор месяца
Напишите `parseMonth(text: String?): Int?`, которая возвращает число от 1 до 12 или `null`, если текст пустой, не число или вне диапазона. Допускаются пробелы вокруг числа. Тело функции должно быть одним выражением.

```starter
fun parseMonth(text: String?): Int? {
    // TODO: one expression with ?. toIntOrNull and takeIf
    TODO()
}

fun main() {
    println(parseMonth("5"))
    println(parseMonth(" 12 "))
    println(parseMonth("13"))
    println(parseMonth("abc"))
    println(parseMonth(null))
}
```

:::solution
```run
fun parseMonth(text: String?): Int? =
    text?.trim()?.toIntOrNull()?.takeIf { it in 1..12 }

fun main() {
    println(parseMonth("5"))
    println(parseMonth(" 12 "))
    println(parseMonth("13"))
    println(parseMonth("abc"))
    println(parseMonth(null))
}
```

```output
5
12
null
null
null
```

Каждый `?.` пропускает `null` дальше по цепочке, и ни одна ветка не падает.
:::
::::

::::task Контакт координатора
В описании «как забрать кошку» координатор указан как `@Nata_Rogava`. Напишите `telegramHandle(instruction: String?): String?`, возвращающую имя без `@` (от 5 до 32 символов: латинские буквы, цифры, `_`) или `null`. Используйте `Regex` и `find`.

```starter
fun telegramHandle(instruction: String?): String? {
    // TODO: Regex("@([A-Za-z0-9_]{5,32})"), find, groupValues[1]
    TODO()
}

fun main() {
    println(telegramHandle("Write to @Nata_Rogava, please"))
    println(telegramHandle("Write to @ab"))
    println(telegramHandle("No contact here"))
    println(telegramHandle(null))
}
```

:::hint
`regex.find(text)` возвращает `MatchResult?`. Первая группа в скобках лежит в `groupValues[1]` (элемент `0` — всё совпадение целиком).
:::

:::solution
```run
private val TELEGRAM_HANDLE = Regex("@([A-Za-z0-9_]{5,32})")

fun telegramHandle(instruction: String?): String? =
    instruction?.let { TELEGRAM_HANDLE.find(it)?.groupValues?.get(1) }

fun main() {
    println(telegramHandle("Write to @Nata_Rogava, please"))
    println(telegramHandle("Write to @ab"))
    println(telegramHandle("No contact here"))
    println(telegramHandle(null))
}
```

```output
Nata_Rogava
null
null
null
```

Это почти точная копия `telegramHandle` из [CatViews.kt](repo:src/main/kotlin/org/proanima/shelter/views/CatViews.kt). Регулярное выражение вынесено в константу уровня файла, чтобы не компилироваться при каждом вызове.
:::
::::

:::quiz Проверьте себя
Q: Что выведет программа?

```kotlin
val a = 7
println(a / 2)
println(a / 2.0)
```
- [ ] `3.5` и `3.5`
- [x] `3` и `3.5`
- [ ] `4` и `3.5`
- [ ] Не скомпилируется
E: Если оба операнда `Int`, деление целочисленное и отбрасывает дробную часть. Если хотя бы один операнд `Double`, результат дробный.

Q: `name` имеет тип `LocalizedText?` и равен `null`. Чему равно значение выражения `name?.forLocale(locale)?.ifBlank { null } ?: "Unnamed"`?
- [ ] Выбросит `NullPointerException`.
- [ ] Пустой строке.
- [x] `"Unnamed"`
- [ ] `null`
E: Первый `?.` видит `null` и возвращает `null` для всей цепочки; оператор `?:` подставляет запасное значение.

Q: Что верно про `val`?
- [ ] Объект, на который указывает `val`, нельзя менять.
- [x] Нельзя присвоить переменной другое значение, но изменяемый объект внутри остаётся изменяемым.
- [ ] `val` работает только для чисел и строк.
- [ ] `val` — то же, что `const val`.
E: `val` запрещает переприсваивание. `const val` — это константа времени компиляции, она бывает только у примитивов и строк, объявленных на верхнем уровне или в `object`.

Q: Вы добавили в `enum class AdoptionStage` значение `QUARANTINE`. В коде есть `val key = when (stage) { ... }` без ветки `else`, которая не знает о новом значении. Что произойдёт?
- [ ] Во время работы будет исключение для новой стадии.
- [ ] Ничего: для неизвестных значений вернётся `null`.
- [x] Код не скомпилируется: `when`-выражение должно быть исчерпывающим.
- [ ] Компилятор молча добавит ветку `else`.
E: Для `when` как выражения по `enum` или `sealed` компилятор требует покрыть все значения. Это защищает от «забытых» мест при расширении модели.

Q: Выберите все выражения, которые превращают пустую строку или `null` в `null`.

```kotlin
val a: String? = ...
```
- [x] `a?.ifBlank { null }`
- [x] `a?.takeIf { it.isNotBlank() }`
- [ ] `a.orEmpty()`
- [ ] `a ?: ""`
E: `ifBlank { null }` и `takeIf { ... }` дают `null` для пустого (и состоящего из пробелов) значения. `orEmpty()` и `?: ""` наоборот превращают `null` в пустую строку.

Q: Когда в проекте уместен `call.parameters["slug"]!!`?
- [ ] Всегда: так короче.
- [ ] Никогда: `!!` запрещён в Kotlin.
- [x] Когда имя параметра входит в шаблон маршрута `/cats/{slug}`, поэтому обработчик не вызовется без него.
- [ ] Когда нужно скрыть возможную ошибку от пользователя.
E: `!!` — утверждение. Оно оправданно, когда инвариант гарантируется чем-то другим (здесь маршрутом) и когда падение означало бы программную ошибку, а не плохой ввод.
:::

## Итоги

- Предпочитайте `val`, пишите выражения вместо операторов, давайте функциям значения по умолчанию.
- Допустимость `null` — часть типа. `?.`, `?:`, `let`, `takeIf` позволяют обработать отсутствие значения без вложенных `if`.
- Исчерпывающий `when` превращает расширение модели в подсказки компилятора.
- Граничные случаи ввода (пусто, пробелы, не латиница, `null`) надо придумывать самому. Компилятор их не найдёт.

Дальше: [Коллекции и функциональный стиль](lesson:k2-collections).
