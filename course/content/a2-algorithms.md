# Алгоритмы и структуры данных

На сайте приюта нет сортировки миллиона записей, но алгоритмы в нём есть, и они решают настоящие задачи: найти ближайший выезд с учётом отмен, посчитать возраст кошки по году и месяцу, превратить текст в блоки, не дать пунктам меню зациклиться, не показать устаревший кэш. Все они маленькие. Поэтому на них удобно учиться: можно удержать в голове весь алгоритм, нарисовать граничные случаи и доказать, что он работает.

После урока вы сможете:

- разбирать задачу на граничные случаи и писать к ним тесты раньше кода;
- оценивать сложность по Big-O и понимать, когда она не важна;
- читать и писать обходы деревьев и поиск циклов;
- писать построчные разборщики (конечные автоматы) и проверять свойства вроде идемпотентности;
- находить дефекты в целочисленной арифметике по границам.

## Big-O без страха

Нотация O(...) отвечает на вопрос «как растёт время, когда данных становится больше». Для небольших `n` она почти не важна, а для больших решает всё.

| Сложность | Название | Пример из проекта |
|---|---|---|
| O(1) | константа | проверка `x in hashSet`; поле из `Map` |
| O(log n) | логарифм | двоичный поиск (в проекте не нужен) |
| O(n) | линейная | `filter`, `groupingBy(...).eachCount()`, один проход разборщика гайда |
| O(n log n) | как сортировка | `sortedWith(compareBy(...))` |
| O(n²) | квадрат | вложенные циклы: сравнить каждый с каждым |

Применим к реальному коду.

- `CatService.getCatBySlug` делает `firstOrNull` по списку: **O(n)** на запрос. Для 30 кошек это тридцать сравнений. Индекс по слагу (`Map<String, Cat>`) дал бы O(1), но усложнил бы код и сброс кэша. **Не нужен**, пока кошек сотни, а не сотни тысяч.
- `validatePets` ищет дубликаты через `groupingBy`: **O(n)**. Наивные два цикла дали бы O(n²), и при тысяче записей это миллион сравнений на каждом запуске.
- `MenuTree.depthOf` обходит родителей, каждый раз ища узел `node(id)` линейным перебором: это **O(глубина × n)** на вызов, а для всех узлов O(n²). Меню состоит из десятков узлов и глубины не больше двух, поэтому цифры ничтожны.

:::note Оптимизируйте после измерения
Быстрый код, которого никто не может прочитать, дороже медленного, который понятен. Правило: сначала ясность и тесты, затем измерение, затем оптимизация там, где измерение показало проблему. Но привычка **знать** сложность своих операций (не как экзамен, а как чутьё) пригодится, когда данных станет больше.
:::

## Алгоритм 1: ближайший выезд

Выезды по расписанию: среда 9:00 и суббота 10:00. Часть выездов отменяют, иногда добавляют лишние. Функция должна вернуть ближайший выезд, который ещё не уехал. Разберём [NextVisit.kt](repo:src/main/kotlin/org/proanima/shelter/service/NextVisit.kt) как алгоритм.

Идея:

1. Для **каждого** дня недели в расписании построить бесконечную последовательность дат: ближайший такой день (`nextOrSame`), затем каждую неделю.
2. В каждой последовательности взять **первую** дату, которая не отменена и ещё не прошла.
3. Добавить дополнительные выезды из переопределений, которые ещё не прошли.
4. Выбрать **минимальный** по времени отправления.

Сложность: для каждой из двух последовательностей придётся пройти не больше `|отмены| + 2` шагов, и итоговый O(c), где c число отмен. Последовательность бесконечна, но `first` остановит её, потому что множество отмен **конечно**.

### Live coding: ближайший выезд по тестам

Решим задачу, как её решали при разработке: тест, минимальный код, новый тест.

::::live NextVisit через TDD
Договорённость: «2026-09-16 среда, 2026-09-19 суббота». Тест берёт текущее время параметром, поэтому проверять можно любую дату.

:::step Шаг 1. Только среда
Самый простой тест: в понедельник ближайший выезд в среду в 9:00.

```kotlin
@Test
fun `monday picks wednesday`() {
    assertEquals(
        NextVisit(LocalDate.of(2026, 9, 16), LocalTime.of(9, 0)),
        nextScheduledVisit(LocalDateTime.parse("2026-09-14T10:00:00"))
    )
}
```

```kotlin
fun nextScheduledVisit(now: LocalDateTime): NextVisit =
    NextVisit(now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY)), LocalTime.of(9, 0))
```
:::

:::step Шаг 2. Два дня и минимум
Четверг должен давать субботу. Строим кандидатов для каждого дня расписания и берём ближайшего.

```kotlin
private val DEPARTURES = listOf(
    DayOfWeek.WEDNESDAY to LocalTime.of(9, 0),
    DayOfWeek.SATURDAY to LocalTime.of(10, 0)
)

fun nextScheduledVisit(now: LocalDateTime): NextVisit =
    DEPARTURES.map { (day, time) -> NextVisit(now.toLocalDate().with(TemporalAdjusters.nextOrSame(day)), time) }
        .minBy { it.date.atTime(it.departure) }
```

Новое расписание это новая строка в списке, а не новая ветка `if`.
:::

:::step Шаг 3. Уже уехавший выезд
Тест: в среду в 9:01 выезд уже ушёл, ближайший это суббота. Граница важна: «`nextOrSame` вернёт сегодня, но сегодня в 9:00 уже в прошлом». Значит, нужно идти по неделям, пока выезд не окажется в будущем.

```kotlin
generateSequence(first) { it.next() }.first { !it.startsBefore(now) }
```

Здесь пригодилась ленивая последовательность из урока про [коллекции](lesson:k2-collections).
:::

:::step Шаг 4. Отмены и дополнительные выезды
Отменённая дата пропускается тем же `first`. Перенос выезда — это отмена плюс дополнительный выезд, поэтому отдельного «переноса» нет.

```kotlin
.first { it.date !in overrides.cancelled && !it.startsBefore(now) }
// extra trips join the candidates:
(regular + overrides.extra.filter { !it.startsBefore(now) }).minBy { ... }
```

Подумайте: что случится, если отменены три выезда подряд? Тест «cancelling several trips in a row moves on to the following week» отвечает: цикл просто идёт дальше.
:::

:::step Шаг 5. Финальная версия и все тесты
Запустите набор. Он повторяет `NextVisitTest` проекта. Попробуйте «сломать» код (замените `<` на `<=` в `startsBefore`) и посмотрите, какой тест упадёт.

```test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import kotlin.test.Test
import kotlin.test.assertEquals

private val DEPARTURES = listOf(
    DayOfWeek.WEDNESDAY to LocalTime.of(9, 0),
    DayOfWeek.SATURDAY to LocalTime.of(10, 0)
)

data class NextVisit(val date: LocalDate, val departure: LocalTime)

data class VisitOverrides(
    val cancelled: Set<LocalDate> = emptySet(),
    val extra: List<NextVisit> = emptyList()
)

fun nextScheduledVisit(now: LocalDateTime, overrides: VisitOverrides = VisitOverrides()): NextVisit {
    val regular = DEPARTURES.map { (day, time) ->
        generateSequence(NextVisit(now.toLocalDate().with(TemporalAdjusters.nextOrSame(day)), time)) { it.next() }
            .first { it.date !in overrides.cancelled && !it.startsBefore(now) }
    }
    val extra = overrides.extra.filter { !it.startsBefore(now) }

    return (regular + extra).minBy { it.date.atTime(it.departure) }
}

private fun NextVisit.startsBefore(now: LocalDateTime) = date.atTime(departure) < now

private fun NextVisit.next() = NextVisit(date.plusWeeks(1), departure)

class NextVisitTest {
    // 2026-09-16 is a Wednesday, 2026-09-19 a Saturday.
    private fun at(dateTime: String, overrides: VisitOverrides = VisitOverrides()) =
        nextScheduledVisit(LocalDateTime.parse(dateTime), overrides)

    @Test
    fun `the regular schedule picks the nearest Wednesday or Saturday`() {
        assertEquals(NextVisit(LocalDate.of(2026, 9, 16), LocalTime.of(9, 0)), at("2026-09-14T10:00:00"))
        assertEquals(NextVisit(LocalDate.of(2026, 9, 19), LocalTime.of(10, 0)), at("2026-09-17T12:00:00"))
    }

    @Test
    fun `a trip that has already left is not the next one`() {
        assertEquals(LocalDate.of(2026, 9, 19), at("2026-09-16T09:01:00").date)
        assertEquals(LocalDate.of(2026, 9, 23), at("2026-09-19T10:01:00").date)
    }

    @Test
    fun `a cancelled trip is skipped`() {
        val overrides = VisitOverrides(cancelled = setOf(LocalDate.of(2026, 9, 16)))

        assertEquals(LocalDate.of(2026, 9, 19), at("2026-09-14T10:00:00", overrides).date)
    }

    @Test
    fun `cancelling several trips in a row moves on to the following week`() {
        val overrides = VisitOverrides(
            cancelled = setOf(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 23))
        )

        assertEquals(LocalDate.of(2026, 9, 26), at("2026-09-14T10:00:00", overrides).date)
    }

    @Test
    fun `an extra trip is chosen when it is earlier than the regular ones`() {
        val extra = NextVisit(LocalDate.of(2026, 9, 15), LocalTime.of(11, 0))

        assertEquals(extra, at("2026-09-14T10:00:00", VisitOverrides(extra = listOf(extra))))
    }

    @Test
    fun `an extra trip in the past is ignored`() {
        val extra = NextVisit(LocalDate.of(2026, 9, 13), LocalTime.of(11, 0))

        assertEquals(LocalDate.of(2026, 9, 16), at("2026-09-14T10:00:00", VisitOverrides(extra = listOf(extra))).date)
    }

    @Test
    fun `a moved trip is a cancellation plus an extra trip`() {
        val moved = NextVisit(LocalDate.of(2026, 9, 17), LocalTime.of(9, 0))
        val overrides = VisitOverrides(cancelled = setOf(LocalDate.of(2026, 9, 16)), extra = listOf(moved))

        assertEquals(moved, at("2026-09-14T10:00:00", overrides))
    }
}
```

Какие свойства у алгоритма: **чистая функция** (время передано), **расширяемая** (новый день расписания — строка в списке), **тестируемая** граничными датами. Именно такие тесты стоит писать для любого алгоритма с датами: «в самый момент отправления», «минуту спустя», «через границу недели».
:::
::::

## Алгоритм 2: возраст по году и месяцу

У кошки из приюта обычно известен год рождения, иногда месяц. Функция `displayCatAge` считает возраст с той точностью, какая есть. Основа, целочисленная арифметика:

```kotlin
val totalMonths = (today.year - birthYear) * 12 + (today.monthValue - birthMonth)
val years = totalMonths / 12
val months = totalMonths % 12
```

Идея: перевести «год и месяц» в общее число месяцев и разделить с остатком. Подход «год минус год» без перевода в месяцы дал бы ошибку для кошки, родившейся в декабре, когда сейчас январь: разница в годах 1, но ей месяц.

Границы, которые стоит проверять руками:

| Родилась | Сегодня | Верный ответ |
|---|---|---|
| 2026-01 | 2026-11 | 10 месяцев |
| 2025-12 | 2026-11 | 11 месяцев |
| 2025-11 | 2026-11 | 1 год |
| 2026-11 | 2026-11 | меньше месяца |
| 2026-12 | 2026-11 | будущее: ошибка данных |

Когда мы писали этот курс, чтение кода без запуска показало два неприятных случая: «родилась в этом месяце» давало `months == 0` и текст «0 месяцев», а «родилась в будущем» давало отрицательное число. Админка запрещает будущие даты, поэтому второй случай недостижим через неё, но данные могут прийти и из файла. **Оба случая исправлены в проекте**: теперь показывается «Младше месяца» (ключ `cat.age.underMonth` на трёх языках) и «Возраст неизвестен», а в `DisplayHelpersTest` добавлены три теста. Само упражнение ниже сделано на копии **прежней** версии функции: так вы можете найти дефекты сами.

## Алгоритм 3: разбор гайда построчно

`parseGuideMarkdown` превращает текст в список блоков за **один проход**, O(n) по строкам. Устроен как **конечный автомат с накопителями**: три буфера (абзац, список, выноска) и правило «пустая строка или смена вида сбрасывает накопленное».

```run
sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class BulletList(val items: List<String>) : Block
    data class Callout(val text: String) : Block
}

fun parse(markdown: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val paragraph = mutableListOf<String>()
    val bullets = mutableListOf<String>()
    val callout = mutableListOf<String>()

    fun flush() {
        if (paragraph.isNotEmpty()) { blocks += Block.Paragraph(paragraph.joinToString(" ")); paragraph.clear() }
        if (bullets.isNotEmpty()) { blocks += Block.BulletList(bullets.toList()); bullets.clear() }
        if (callout.isNotEmpty()) { blocks += Block.Callout(callout.joinToString(" ")); callout.clear() }
    }

    markdown.lines().forEach { raw ->
        val line = raw.trim()
        when {
            line.isEmpty() -> flush()
            line.startsWith("## ") -> { flush(); blocks += Block.Heading(2, line.removePrefix("## ")) }
            line.startsWith("# ") -> { flush(); blocks += Block.Heading(1, line.removePrefix("# ")) }
            line.startsWith("> ") -> callout += line.removePrefix("> ")
            line.startsWith("- ") -> bullets += line.removePrefix("- ")
            else -> paragraph += line
        }
    }
    flush()
    return blocks
}

fun main() {
    val text = """
        # Guide

        Come on Wednesday
        or Saturday.

        - gloves
        - water

        > Wash your hands.
    """.trimIndent()
    parse(text).forEach(::println)
}
```

```output
Heading(level=1, text=Guide)
Paragraph(text=Come on Wednesday or Saturday.)
BulletList(items=[gloves, water])
Callout(text=Wash your hands.)
```

Что стоит увидеть:

- **Накопители.** Строки абзаца собираются в список и склеиваются пробелом: два соседних строки текста дают один абзац (как в Markdown).
- **Сброс.** Заголовок и пустая строка закрывают всё накопленное. Последний `flush()` после цикла обязателен: без него терялся бы последний блок файла. Это самая частая ошибка в таких разборщиках, и её ловит тест «файл не заканчивается пустой строкой».
- **Порядок веток `when`.** Первой проверяется пустая строка, затем частные случаи (заголовок, выноска, пункт списка), и только `else` принимает обычный текст. Каждая строка попадает ровно в одну ветку.
- Реальная версия в проекте дополнительно умеет `### `, якоря `{#id}` и защищается от смешивания списка и выноски (переход от одного вида к другому сбрасывает предыдущий накопитель).

## Алгоритм 4: дерево меню

Меню сайта — дерево: страницы, разделы, группы, на каждом узле `parent` и `position`. Данные приходят из базы или резервной копии, значит, они могут быть **любыми**: ссылаться на несуществующих родителей, содержать циклы, быть слишком глубокими. Функция `settleMenuTree` ([MenuTrees.kt](repo:src/main/kotlin/org/proanima/shelter/service/MenuTrees.kt)) превращает что угодно в безопасное дерево. Это приём **нормализации**: на границе системы любые данные приводятся к инвариантам, и дальше код может на них полагаться.

Инварианты результата: каждый узел один раз; нет ссылок на несуществующее; нет циклов; глубина не больше `MAX_MENU_DEPTH = 2`.

::::live Дерево: цикл, глубина, обход
Построим ядро алгоритма по шагам и запустим на «грязных» данных.

:::step Шаг 1. Дубликаты и словарь
Первый узел с данным `id` побеждает. `LinkedHashMap` сохраняет порядок и даёт поиск за O(1).

```kotlin
val known = LinkedHashMap<String, Node>()
stored.forEach { if (it.id !in known) known[it.id] = it }
```
:::

:::step Шаг 2. Глубина и поиск цикла
Идём от узла вверх по родителям, считая уровни. Если встретили узел, который уже видели, то это цикл. Неизвестный родитель обрывает цепочку.

```kotlin
fun depth(node: Node, known: Map<String, Node>): Int? {
    val seen = hashSetOf(node.id)
    var depth = 1
    var current = node
    while (true) {
        val parent = current.parent?.let { known[it] } ?: return depth
        if (!seen.add(parent.id)) return null   // came back to a node already seen: a cycle
        depth++
        current = parent
    }
}
```

Цикл всегда завершается: либо цепочка кончилась, либо мы повторились; длина ограничена числом узлов. `HashSet.add` возвращает `false`, если элемент уже был: компактный способ обнаружить повтор.
:::

:::step Шаг 3. Что делаем с плохими узлами
Цикл, слишком глубокая цепочка или неизвестный родитель переводят узел на верхний уровень: лучше показать пункт не на месте, чем потерять его.

```kotlin
known.values.map { node ->
    val d = depth(node, known)
    if (d == null || d > MAX_DEPTH || (node.parent != null && node.parent !in known)) node.copy(parent = null) else node
}
```
:::

:::step Шаг 4. Обход в глубину для порядка показа
Узлы группируются по родителю и выводятся рекурсивным обходом: узел, затем его дети. Дети сортируются по `position`, затем по `id` (чтобы порядок был полностью определён).

```kotlin
val byParent = nodes.groupBy { it.parent }
fun add(parent: String?, level: Int) {
    byParent[parent].orEmpty().sortedWith(compareBy({ it.position }, { it.id })).forEach { node ->
        out += "-".repeat(level) + node.id
        add(node.id, level + 1)
    }
}
```

Это обход «в глубину, предпорядок». Каждый узел посещается ровно раз, а сортировка у каждого родителя, всего O(n log n).
:::

:::step Шаг 5. Запускаем на «грязных» данных
В данных есть дубликат `cats`, цикл `a ↔ b` и слишком глубокая цепочка `cats → kittens → deep`.

```run
data class Node(val id: String, val parent: String? = null, val position: Int = 0)

const val MAX_DEPTH = 2

fun depth(node: Node, known: Map<String, Node>): Int? {
    val seen = hashSetOf(node.id)
    var depth = 1
    var current = node
    while (true) {
        val parent = current.parent?.let { known[it] } ?: return depth
        if (!seen.add(parent.id)) return null
        depth++
        current = parent
    }
}

fun settle(stored: List<Node>): List<Node> {
    val known = LinkedHashMap<String, Node>()
    stored.forEach { if (it.id !in known) known[it.id] = it }
    return known.values.map { node ->
        val d = depth(node, known)
        if (d == null || d > MAX_DEPTH || (node.parent != null && node.parent !in known)) node.copy(parent = null) else node
    }
}

fun layOut(nodes: List<Node>): List<String> {
    val byParent = nodes.groupBy { it.parent }
    val out = mutableListOf<String>()
    fun add(parent: String?, level: Int) {
        byParent[parent].orEmpty().sortedWith(compareBy({ it.position }, { it.id })).forEach { node ->
            out += "-".repeat(level) + node.id
            add(node.id, level + 1)
        }
    }
    add(null, 0)
    return out
}

fun main() {
    val stored = listOf(
        Node("cats", null, 10), Node("dogs", null, 20),
        Node("kittens", "cats", 10), Node("deep", "kittens", 10),
        Node("a", "b", 30), Node("b", "a", 40),
        Node("cats", "dogs", 99)
    )
    layOut(settle(stored)).forEach(::println)
}
```

```output
cats
-kittens
deep
dogs
a
b
```

Прочитайте результат: дубликат `cats` (с родителем `dogs`) отброшен; `deep` оказался слишком глубоким и поднят на верхний уровень; `a` и `b` (цикл) тоже поднялись, и ни одна запись не потерялась. Ни исключений, ни бесконечных циклов.
:::
::::

## Алгоритм 5: «слипание» слов и идемпотентность

Русский текст не должен заканчивать строку предлогом: «в» и следующее слово склеиваются неразрывным пробелом. Функция `typographicText` применяет регулярное выражение к каждому текстовому узлу страницы. Заметьте комментарий проекта: «Only plain spaces are replaced, so a second pass changes nothing». Это свойство называется **идемпотентностью**: `f(f(x)) == f(x)`. Для таких преобразований его стоит проверять отдельно: двойное применение не должно портить результат.

```run
private const val NBSP = " "
private val GLUED_WORDS = listOf("а", "в", "и", "к", "о", "с", "у", "я", "во", "до", "за", "из", "ко", "на", "не", "но", "ни", "об", "от", "по", "со")
private val GLUE_AFTER_WORD = Regex("(?<![\\p{L}\\p{N}])(${GLUED_WORDS.joinToString("|")}) ", RegexOption.IGNORE_CASE)

fun typographic(text: String): String =
    GLUE_AFTER_WORD.replace(text) { "${it.groupValues[1]}$NBSP" }.replace(" —", "$NBSP—")

fun main() {
    val text = "Кошка живёт в приюте — и ждёт семью"
    val once = typographic(text)
    println(once.replace(NBSP, "_"))
    println(typographic(once) == once)
}
```

```output
Кошка живёт в_приюте_— и_ждёт семью
true
```

Две тонкости. Во-первых, `(?<![\p{L}\p{N}])` (отрицательный просмотр назад) гарантирует, что «в» берётся только как **целое слово**: иначе «ав » внутри слова тоже склеилось бы. Во-вторых, регулярное выражение не откатывается экспоненциально: для него нет вложенных повторений, и оно линейно по длине текста. Это важно, потому что оно работает над всей страницей на каждый запрос (проверка «безопасности регулярного выражения» похожа на проверку сложности).

## Практика

::::task Возраст: найдите дефекты и исправьте
Функция ниже копирует логику `displayCatAge` **до исправления**. Напишите тесты на границы из таблицы выше. Найдите два дефекта: кошка, родившаяся в текущем месяце, и родившаяся «в будущем». Исправьте: для «меньше месяца» верните `"under a month"`, для будущего `"unknown"`.

```starter
import java.time.LocalDate

fun displayAge(birthYear: Int?, birthMonth: Int?, today: LocalDate): String {
    if (birthYear == null) return "unknown"
    if (birthMonth == null) {
        return when (val years = today.year - birthYear) {
            0 -> "under a year"
            1 -> "1 year"
            else -> "$years years"
        }
    }
    val totalMonths = (today.year - birthYear) * 12 + (today.monthValue - birthMonth)
    val years = totalMonths / 12
    val months = totalMonths % 12
    return if (years == 1) "1 year"
    else if (years > 1) "$years years"
    else if (months == 1) "1 month"
    else "$months months"
}

fun main() {
    val today = LocalDate.of(2026, 11, 8)
    println(displayAge(2026, 11, today))
    println(displayAge(2026, 12, today))
}
```

:::hint
Дефекты рядом с последней веткой: при `totalMonths == 0` получается `0 months`, при `totalMonths < 0` отрицательное число. Добавьте проверки **до** деления.
:::

:::solution
```test
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

fun displayAge(birthYear: Int?, birthMonth: Int?, today: LocalDate): String {
    if (birthYear == null) return "unknown"
    if (birthMonth == null) {
        return when (val years = today.year - birthYear) {
            0 -> "under a year"
            1 -> "1 year"
            else -> if (years < 0) "unknown" else "$years years"
        }
    }
    val totalMonths = (today.year - birthYear) * 12 + (today.monthValue - birthMonth)
    if (totalMonths < 0) return "unknown"
    if (totalMonths == 0) return "under a month"
    val years = totalMonths / 12
    val months = totalMonths % 12
    return if (years == 1) "1 year"
    else if (years > 1) "$years years"
    else if (months == 1) "1 month"
    else "$months months"
}

class AgeTest {
    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun `months are counted when the month is known`() {
        assertEquals("10 months", displayAge(2026, 1, today))
        assertEquals("11 months", displayAge(2025, 12, today))
        assertEquals("1 month", displayAge(2026, 10, today))
    }

    @Test
    fun `a full year or more is counted in years`() {
        assertEquals("1 year", displayAge(2025, 11, today))
        assertEquals("1 year", displayAge(2025, 1, today))
        assertEquals("3 years", displayAge(2023, 6, today))
    }

    @Test
    fun `born this month is under a month, not zero months`() {
        assertEquals("under a month", displayAge(2026, 11, today))
    }

    @Test
    fun `a birth date in the future is unknown, not a negative number`() {
        assertEquals("unknown", displayAge(2026, 12, today))
        assertEquals("unknown", displayAge(2027, null, today))
    }

    @Test
    fun `without a month only the year counts`() {
        assertEquals("under a year", displayAge(2026, null, today))
        assertEquals("1 year", displayAge(2025, null, today))
        assertEquals("unknown", displayAge(null, null, today))
    }
}
```

Алгоритм поиска дефектов: выписать **границы** (ноль, один, переход через год, будущее, «нет данных»), для каждой написать ожидаемое значение и сравнить с кодом. Это называют анализом граничных значений, и именно он находит ошибки «на единицу». Обратите внимание на `1 year` для `2025-01`: разница в 22 месяца даёт `years == 1` (целочисленное деление), и это верно: возраст «1 год и 10 месяцев» кошке показывается как «1 год». Допустимо ли это для продукта, вопрос к людям (возможно, лучше «1 год 10 мес.»).
:::
::::

::::task Дерево: потомки и допустимые родители
Для интерфейса «куда можно переместить пункт» нужно знать множество **потомков** узла (нельзя переместить пункт под самого себя или под своего потомка). Напишите `descendants(id, nodes)`, возвращающую множество идентификаторов всех потомков, используя обход в глубину.

```starter
data class Node(val id: String, val parent: String?)

fun descendants(id: String, nodes: List<Node>): Set<String> {
    // TODO: children = nodes whose parent is id; recurse
    TODO()
}

fun main() {
    val nodes = listOf(Node("cats", null), Node("kittens", "cats"), Node("tiny", "kittens"), Node("dogs", null))
    println(descendants("cats", nodes))
    println(descendants("dogs", nodes))
}
```

:::solution
```run
data class Node(val id: String, val parent: String?)

fun descendants(id: String, nodes: List<Node>): Set<String> =
    nodes.filter { it.parent == id }.flatMap { setOf(it.id) + descendants(it.id, nodes) }.toSet()

fun main() {
    val nodes = listOf(Node("cats", null), Node("kittens", "cats"), Node("tiny", "kittens"), Node("dogs", null))
    println(descendants("cats", nodes))
    println(descendants("dogs", nodes))
}
```

```output
[kittens, tiny]
[]
```

Это точная идея `descendantIds` из проекта. Сложность: каждый вызов фильтрует весь список, значит, O(n) на узел и O(n²) для обхода всего дерева. Подумайте: как сделать O(n)? Ответ: один раз сгруппировать узлы по родителю (`groupBy { it.parent }`) и дальше брать детей из словаря за O(1). Для меню из тридцати узлов выигрыш не заметен, но принцип «сначала индекс, потом обход» стоит запомнить. Заметьте и защиту: функция безопасна, только если в дереве нет циклов; в проекте это гарантирует `settleMenuTree`, поэтому рекурсия всегда завершается.
:::
::::

::::task Оцените сложность
Для каждого фрагмента назовите сложность по `n` (размер списка) и скажите, можно ли улучшить.

```kotlin
// (a)
cats.firstOrNull { it.id == wanted }

// (b)
for (i in slugs.indices) for (j in i + 1 until slugs.size) if (slugs[i] == slugs[j]) println("dup")

// (c)
cats.sortedBy { it.name }.take(3)

// (d)
val set = slugs.toSet()
wanted in set
```

:::solution
- (a) **O(n)**: перебор. Для редких запросов нормально; при частых и большом `n` строят `Map<id, Cat>`.
- (b) **O(n²)**: каждая пара сравнивается. Дубликаты ищут через `groupingBy(...).eachCount()` за O(n), как это делает `validatePets`.
- (c) **O(n log n)** из-за сортировки, хотя нужны лишь три наименьших. Можно `minByOrNull` три раза или `sortedBy` оставить: для небольших списков читаемость важнее. Для больших `n` используют структуру «куча» и частичную выборку.
- (d) Построение множества **O(n)**, проверка потом **O(1)**. Выигрыш, только если проверок много; для одной проверки `wanted in slugs` дешевле.
:::
::::

:::quiz Проверьте себя
Q: Почему последовательность дат выезда в `nextScheduledVisit` бесконечна, а программа всё равно завершается?
- [ ] Потому что дат всего конечное число.
- [x] `first { ... }` останавливает генерацию, как только найдена подходящая дата; множество отмен конечно, поэтому подходящая дата всегда находится.
- [ ] Потому что Kotlin прерывает циклы по таймеру.
- [ ] Это ошибка в проекте.
E: Ленивость плюс конечное условие отказа дают гарантированную остановку.

Q: Что даёт перевод «год и месяц» в общее число месяцев при расчёте возраста?
- [ ] Ускорение работы.
- [x] Корректный учёт переходов через год: кошка, родившаяся в декабре, в январе имеет месяц, а не год.
- [ ] Меньше кода.
- [ ] Возможность не обрабатывать `null`.
E: Разность лет без месяцев даёт ошибку на границе года. Общее число месяцев устраняет её.

Q: Для чего нужен последний `flush()` после цикла в `parseGuideMarkdown`?
- [ ] Для очистки памяти.
- [x] Иначе последний блок файла, не закрытый пустой строкой, остался бы в буфере и потерялся.
- [ ] Чтобы вернуть пустой список.
- [ ] Это стиль.
E: Накопители сбрасываются на разделителях. Конец файла тоже разделитель, и о нём нужно позаботиться явно.

Q: Как в `settleMenuTree` обнаруживается цикл в родителях?
- [ ] Подсчётом числа узлов.
- [x] Идём от узла вверх, запоминая виденные узлы; если снова встречаем уже виденный, значит, цепочка зациклилась.
- [ ] Сортировкой.
- [ ] Рекурсией без ограничений.
E: `HashSet.add` возвращает `false` при повторе, что даёт короткую проверку.

Q: Что такое идемпотентность преобразования `typographicText`?
- [ ] Преобразование работает быстро.
- [x] Повторное применение к уже обработанному тексту ничего не меняет: `f(f(x)) == f(x)`.
- [ ] Преобразование не использует регулярные выражения.
- [ ] Преобразование обратимо.
E: Идемпотентные преобразования безопасно применять в нескольких местах конвейера: двойная обработка не портит результат.

Q: Какую сложность имеет проверка `x in list` для `List` из n элементов и `x in set` для `HashSet`?
- [ ] Обе O(1).
- [x] O(n) и O(1) соответственно.
- [ ] O(1) и O(n).
- [ ] Обе O(n).
E: Список перебирается, множество ищет по хэшу.

Q: Когда оправдано **не** оптимизировать линейный поиск `firstOrNull` по списку кошек?
- [x] Когда данных десятки или сотни и запрос редкий; ясность кода дороже микровыигрыша.
- [ ] Никогда, оптимизировать надо всегда.
- [ ] Когда кошек миллион.
- [ ] Когда список отсортирован по случайному ключу.
E: Оптимизация имеет цену в усложнении кода. Её делают, когда измерение показывает проблему.
:::

## Итоги

- Алгоритм начинается с граничных случаев: напишите их тестами до кода.
- Для сложности достаточно чутья: перебор O(n), вложенный перебор O(n²), индекс O(1). Но не оптимизируйте без измерения.
- На границе системы данные **нормализуют** к инвариантам: дальше код на них полагается (дерево меню).
- Разборщики строятся на накопителях и обязательном финальном сбросе.
- Проверяйте свойства (идемпотентность), а не только примеры.

Дальше: [Тестирование и TDD](lesson:a3-testing).
