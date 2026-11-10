# Коллекции и функциональный стиль

Почти вся логика сайта сводится к работе со списками: «опубликованные кошки», «кошки, которые ищут дом», «сгруппировать по статусу», «найти дубликаты адресов», «отсортировать пункты меню». В Kotlin для этого есть богатая стандартная библиотека, и циклы `for` с временными списками нужны редко.

После урока вы сможете:

- выбирать между `List`, `Set` и `Map` и понимать, что «неизменяемый» в Kotlin значит «только для чтения»;
- читать и писать цепочки `filter`, `map`, `partition`, `groupBy`, `sortedWith`;
- использовать `Sequence` там, где ленивость действительно помогает;
- замечать неочевидные ловушки: повторяющиеся ключи, сортировку, сложность поиска.

## Три основные коллекции

| Тип | Что это | Пример из проекта |
|---|---|---|
| `List<T>` | упорядоченный набор, элементы могут повторяться | `Cat.photoUrls: List<String>` |
| `Set<T>` | набор без повторов, быстрая проверка «есть ли» | `VisitOverrides.cancelled: Set<LocalDate>` |
| `Map<K, V>` | ключ → значение | `texts: Map<String, LocalizedText>` в записи раздела |

Интерфейсы `List`, `Set`, `Map` **только читают**. Чтобы изменять, нужны `MutableList`, `MutableSet`, `MutableMap`. Функции `listOf(...)`, `setOf(...)`, `mapOf(...)` создают коллекции для чтения; `mutableListOf(...)` и подобные для записи. Пустые значения по умолчанию в моделях проекта пишутся как `emptyList()`:

```kotlin
val photoUrls: List<String> = emptyList()
```

:::note Только для чтения не значит «замороженный»
`List` — это *вид*, через который нельзя менять. Если за ним спрятан `MutableList`, кто-то другой может изменить содержимое. Модели проекта держат в полях именно `List`, а изменения делают через `copy(...)` с новым списком, поэтому в коде нет неожиданных мутаций.
:::

## Преобразования: основа функционального стиля

Идея: вместо цикла с накоплением результата описываем, какой результат хотим. Каждая функция принимает коллекцию и лямбду и возвращает **новую** коллекцию.

```run
enum class Stage { NONE, FOR_ADOPTION, RESERVED, ADOPTED }
data class Cat(val name: String, val stage: Stage, val birthYear: Int?)

fun main() {
    val cats = listOf(
        Cat("Felix", Stage.FOR_ADOPTION, 2020),
        Cat("Mila", Stage.ADOPTED, 2019),
        Cat("Nami", Stage.FOR_ADOPTION, null),
        Cat("Fox", Stage.RESERVED, 2022)
    )

    val available = cats.filter { it.stage == Stage.FOR_ADOPTION }
    println(available.map { it.name })

    val (adopted, others) = cats.partition { it.stage == Stage.ADOPTED }
    println("adopted=${adopted.map { it.name }} others=${others.map { it.name }}")

    println(cats.mapNotNull { it.birthYear })
    println(cats.groupBy { it.stage }.mapValues { (_, group) -> group.size })
    println(cats.any { it.birthYear == null })
    println(cats.sortedBy { it.name }.joinToString { it.name })
}
```

```output
[Felix, Nami]
adopted=[Mila] others=[Felix, Nami, Fox]
[2020, 2019, 2022]
{FOR_ADOPTION=2, ADOPTED=1, RESERVED=1}
true
Felix, Fox, Mila, Nami
```

Основные операции, которые стоит помнить:

| Задача | Функция |
|---|---|
| оставить подходящие | `filter`, `filterNot`, `filterValues` (для `Map`) |
| преобразовать каждый | `map`, `mapNotNull` (выбрасывает `null`), `flatMap` |
| разделить на две части | `partition` |
| сгруппировать | `groupBy` (даёт списки), `groupingBy { }.eachCount()` (даёт счётчики) |
| превратить в словарь | `associateBy`, `associateWith`, `associate` |
| найти | `firstOrNull`, `any`, `all`, `none`, `minBy`, `maxByOrNull` |
| порядок | `sortedBy`, `sortedByDescending`, `sortedWith(compareBy(...))` |
| взять часть | `take`, `drop`, `takeWhile` |
| склеить | `joinToString`, `plus`, `zip` |

:::project В проекте
`CatService` ([CatService.kt](repo:src/main/kotlin/org/proanima/shelter/service/CatService.kt)) целиком состоит из этих операций. Обратите внимание, как маленькая приватная функция `published()` задаёт правило «черновики публике не показываем» **в одном месте**:

```kotlin
class CatService(private val repository: CatRepository) {
    fun getAllCats(): List<Cat> = published()

    fun getAvailableCats(): List<Cat> =
        published().filter { it.adoptionStage == AdoptionStage.FOR_ADOPTION }

    fun getCatBySlug(slug: String): Cat? =
        published().firstOrNull { petSlug(it.name?.en, it.id) == slug }

    private fun published(): List<Cat> = repository.findAll().filter { it.published }
}
```

(Для краткости тело дано через `=`; в файле те же операции записаны с `return`.) `firstOrNull` возвращает `null`, если кошки нет: маршрут превращает это в ответ 404. Метод `first { ... }` вместо него бросил бы исключение, и посетитель увидел бы страницу ошибки 500 вместо «не найдено».

Страница списка использует `partition`, чтобы отделить «нашедших дом» в отдельный раздел ([CatViews.kt](repo:src/main/kotlin/org/proanima/shelter/views/CatViews.kt)):

```kotlin
val (adopted, others) = cats.partition { it.adoptionStage == AdoptionStage.ADOPTED }
```
:::

## Словари и их ловушки

```run
fun main() {
    val codes = listOf("ru", "en", "sr")
    println(codes.associateWith { it.uppercase() })

    val byLength = listOf("cat", "dog", "bird").associateBy { it.length }
    println(byLength)

    val words = listOf("a", "bb", "cc", "ddd")
    println(words.groupingBy { it.length }.eachCount())
}
```

```output
{ru=RU, en=EN, sr=SR}
{3=dog, 4=bird}
{1=1, 2=2, 3=1}
```

Обратите внимание на второй результат: `associateBy` при совпадении ключей **молча оставляет последний** элемент (`cat` потерян). Если ключи должны быть уникальными, это нужно проверять отдельно, и именно такую проверку делает `validatePets`: два животных с одинаковым адресом сделали бы второго недоступным.

## Сортировка

```run
data class Node(val id: String, val position: Int)

fun main() {
    val nodes = listOf(Node("b", 20), Node("a", 20), Node("c", 10))

    val sorted = nodes.sortedWith(compareBy({ it.position }, { it.id }))
    println(sorted.map { it.id })

    println(nodes.sortedByDescending { it.position }.map { it.id })
}
```

```output
[c, a, b]
[b, a, c]
```

`compareBy({ первый }, { второй })` сортирует по нескольким ключам: по позиции, а при равенстве по `id`. Так в проекте упорядочено меню. Вторая строка показывает полезное свойство: `sortedBy` **устойчива**: равные по ключу элементы (`b` и `a`) остаются в исходном порядке.

:::warn Не сортируйте «по ощущениям»
Если порядок важен пользователю (меню, список кошек), задавайте его явно и полностью. Сортировка только по `position` при равных значениях даёт порядок, зависящий от того, в каком виде пришли данные. В `MenuTrees.kt` вторым ключом всегда идёт `id`, чтобы порядок не «плавал» между запусками.
:::

## Sequence: ленивые вычисления

Цепочка `filter { ... }.map { ... }` на `List` выполняет каждый шаг над всей коллекцией и создаёт промежуточные списки. `Sequence` обрабатывает элементы по одному и вычисляет ровно столько, сколько нужно:

```run
fun main() {
    val result = listOf(1, 2, 3, 4)
        .asSequence()
        .filter { println("filter $it"); it % 2 == 0 }
        .map { println("map $it"); it * 10 }
        .first()
    println(result)
}
```

```output
filter 1
filter 2
map 2
20
```

Элементы 3 и 4 даже не проверялись: `first()` получил ответ и остановил процесс. Ленивость также позволяет описывать **бесконечные** последовательности через `generateSequence`:

```run
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

fun main() {
    val firstSaturday = LocalDate.of(2026, 11, 10).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
    val saturdays = generateSequence(firstSaturday) { it.plusWeeks(1) }
    println(saturdays.take(3).toList())
    println(saturdays.first { it.dayOfMonth == 1 })
}
```

```output
[2026-11-14, 2026-11-21, 2026-11-28]
2027-05-01
```

Именно так проект ищет ближайший выезд: «возьми ближайшую среду и идти по неделям, пока не найдётся дата, которая не отменена и ещё не прошла». Разбор алгоритма целиком в уроке про [алгоритмы](lesson:a2-algorithms).

:::tip Когда брать Sequence
Для маленьких списков (десятки элементов) разницы нет, и `List` проще отлаживать. `Sequence` стоит выбирать для бесконечных или очень больших источников и для цепочек, которые обычно останавливаются рано (`first`, `take`, `any`).
:::

## Сложность: Set быстрее List для «есть ли»

Проверка `x in list` перебирает список: O(n). Проверка `x in set` для `HashSet` почти мгновенная: O(1). В цикле это разница между O(n²) и O(n). В `settleMenuTree` идентификаторы разделов собраны в `HashSet` именно поэтому:

```kotlin
val sectionIds = sections.mapTo(HashSet()) { sectionNodeId(it.id) }
```

`mapTo(HashSet()) { ... }` преобразует и сразу кладёт результат в нужную коллекцию без промежуточного списка. Данных на сайте мало, и разница не видна; привычка нужна, чтобы код не сломался, когда данных станет в сто раз больше.

## Live coding: ищем дубликаты адресов

Задача из `DataValidation.kt`: если у двух кошек одинаковый адрес-слаг, вторая станет недоступной. Нужно найти повторы и сказать о них человеку.

::::live Функция duplicates
Исходные данные: список слагов. Результат: список понятных сообщений.

:::step Шаг 1. Наивный перебор
Две вложенные петли сравнивают каждую пару. Работает, но это O(n²) и много кода.

```kotlin
val result = mutableListOf<String>()
for (i in values.indices) {
    for (j in i + 1 until values.size) {
        if (values[i] == values[j] && values[i] !in result) result += values[i]
    }
}
```
:::

:::step Шаг 2. groupBy
Сгруппируем значения и оставим группы из двух и более.

```kotlin
values.groupBy { it }.filterValues { it.size > 1 }.keys
```

Короче и понятнее, но `groupBy` строит списки равных значений, которые нам не нужны: нужен только счётчик.
:::

:::step Шаг 3. groupingBy + eachCount
`groupingBy { it }.eachCount()` сразу считает, сколько раз встретилось каждое значение.

```kotlin
values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
```
:::

:::step Шаг 4. Сообщения для человека
Превращаем ключи в строки, которые указывают файл и поле. Это итоговая функция проекта.

```run
fun duplicates(file: String, field: String, values: List<String>): List<String> =
    values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        .map { "$file: duplicate $field \"$it\"" }

fun main() {
    val slugs = listOf("felix", "mila", "felix", "nami", "mila", "mila")
    duplicates("cats.json", "slug", slugs).forEach(::println)
    println(duplicates("cats.json", "id", listOf("1", "2", "3")))
}
```

```output
cats.json: duplicate slug "felix"
cats.json: duplicate slug "mila"
[]
```
:::

:::step Шаг 5. Что получилось
Четыре строки вместо цикла, одна проверка на значение, понятные сообщения. Функция возвращает **данные** (список сообщений), а не печатает и не бросает исключение: вызывающий код сам решает, что делать с проблемами. При запуске на файлах приложение останавливается с перечнем ошибок, при запуске на базе пишет предупреждения в журнал, и сайт продолжает работать. Это пример того, как возврат значений делает логику гибкой.
:::
::::

## Практика

::::task Кошки для главной страницы
Напишите `homeCats`: имена кошек, которые ищут дом, сначала самые молодые (больший год рождения), кошки без года в конце. Не больше `limit` штук.

```starter
data class Cat(val name: String, val forAdoption: Boolean, val birthYear: Int?)

fun homeCats(cats: List<Cat>, limit: Int): List<String> {
    // TODO: filter, sort (null birth year goes last), take, map to names
    TODO()
}

fun main() {
    val cats = listOf(
        Cat("Felix", true, 2020),
        Cat("Mila", false, 2019),
        Cat("Nami", true, null),
        Cat("Fox", true, 2022),
        Cat("Sava", true, 2018)
    )
    println(homeCats(cats, 3))
}
```

:::hint
`compareByDescending<Cat> { it.birthYear ?: Int.MIN_VALUE }` превращает отсутствующий год в «самое старое» значение, и при сортировке по убыванию такие кошки оказываются в конце.
:::

:::solution
```run
data class Cat(val name: String, val forAdoption: Boolean, val birthYear: Int?)

fun homeCats(cats: List<Cat>, limit: Int): List<String> =
    cats.filter { it.forAdoption }
        .sortedWith(compareByDescending { it.birthYear ?: Int.MIN_VALUE })
        .take(limit)
        .map { it.name }

fun main() {
    val cats = listOf(
        Cat("Felix", true, 2020),
        Cat("Mila", false, 2019),
        Cat("Nami", true, null),
        Cat("Fox", true, 2022),
        Cat("Sava", true, 2018)
    )
    println(homeCats(cats, 3))
}
```

```output
[Fox, Felix, Sava]
```

Сайт сейчас берёт первые три доступные кошки без сортировки (`getAvailableCats().take(3)`). Сортировка по возрасту была бы продуктовым решением: стоит ли показывать на главной котят? Это вопрос к команде, а не к программисту.
:::
::::

::::task Отчёт по расположению
Дан список расположений животных. Верните строки вида `IN_SHELTER: 3`, от самого частого расположения к самому редкому.

```starter
fun locationReport(locations: List<String>): List<String> {
    // TODO: count with groupingBy, sort by count descending, format
    TODO()
}

fun main() {
    val data = listOf("IN_SHELTER", "AT_HOME", "IN_SHELTER", "IN_FOSTERHOME", "IN_SHELTER", "AT_HOME")
    locationReport(data).forEach(::println)
}
```

:::solution
```run
fun locationReport(locations: List<String>): List<String> =
    locations.groupingBy { it }.eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { "${it.key}: ${it.value}" }

fun main() {
    val data = listOf("IN_SHELTER", "AT_HOME", "IN_SHELTER", "IN_FOSTERHOME", "IN_SHELTER", "AT_HOME")
    locationReport(data).forEach(::println)
}
```

```output
IN_SHELTER: 3
AT_HOME: 2
IN_FOSTERHOME: 1
```

Подумайте: что произойдёт при равных счётчиках? Благодаря устойчивой сортировке порядок будет таким, каким группы появились в данных. Если нужен предсказуемый результат, добавьте второй ключ: `sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })`.
:::
::::

::::task Следующие среды без отменённых
Верните `count` ближайших дат выезда по средам, начиная с даты `from` (включительно, если она среда), пропуская отменённые. Используйте `generateSequence`.

```starter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

fun nextWednesdays(from: LocalDate, cancelled: Set<LocalDate>, count: Int): List<LocalDate> {
    // TODO: a lazy sequence of Wednesdays, week by week, without the cancelled ones
    TODO()
}

fun main() {
    val cancelled = setOf(LocalDate.of(2026, 9, 16))
    println(nextWednesdays(LocalDate.of(2026, 9, 14), cancelled, 3))
}
```

:::hint
`TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY)` находит ближайшую среду. Дальше `generateSequence(start) { it.plusWeeks(1) }`, затем `filter`, `take`, `toList`.
:::

:::solution
```run
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

fun nextWednesdays(from: LocalDate, cancelled: Set<LocalDate>, count: Int): List<LocalDate> =
    generateSequence(from.with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))) { it.plusWeeks(1) }
        .filter { it !in cancelled }
        .take(count)
        .toList()

fun main() {
    val cancelled = setOf(LocalDate.of(2026, 9, 16))
    println(nextWednesdays(LocalDate.of(2026, 9, 14), cancelled, 3))
}
```

```output
[2026-09-23, 2026-09-30, 2026-10-07]
```

Среда 16 сентября отменена, поэтому список начинается с 23-го. Обратите внимание, что `cancelled` — это `Set`: проверка `!in` быстрая, и это тот же приём, что в `VisitOverrides`.
:::
::::

:::quiz Проверьте себя
Q: Что произойдёт?

```kotlin
val xs = listOf(1, 2, 3)
xs.add(4)
```
- [ ] Список станет `[1, 2, 3, 4]`.
- [ ] Бросится исключение во время работы.
- [x] Код не скомпилируется: у `List` нет метода `add`.
- [ ] `add` вернёт `false`.
E: Интерфейс `List` в Kotlin только для чтения. Для изменяемого списка нужен `mutableListOf` или `ArrayList`.

Q: Что вернёт `listOf("cat", "dog", "bird").associateBy { it.length }`?
- [ ] `{3=cat, 4=bird}`
- [x] `{3=dog, 4=bird}`
- [ ] `{3=[cat, dog], 4=[bird]}`
- [ ] Исключение: ключи совпали.
E: `associateBy` при совпадении ключей оставляет последний элемент. Чтобы сохранить все, нужен `groupBy`.

Q: `CatService.getCatBySlug` использует `firstOrNull`, а не `first`. Почему это важно?
- [ ] `firstOrNull` быстрее.
- [x] Отсутствие кошки — обычная ситуация (неверный адрес). `firstOrNull` даёт `null`, и маршрут вернёт 404, а `first` бросил бы исключение и привёл к ошибке 500.
- [ ] `first` не работает со списками.
- [ ] Это просто стиль.
E: Исключения предназначены для неожиданного. «Такого адреса нет» ожидаемо, поэтому оно выражено типом `Cat?`.

Q: Какие задачи хорошо решает `Sequence`?
- [x] Бесконечная последовательность дат выездов, из которой берут первую подходящую.
- [x] Длинная цепочка преобразований большого списка, из которой нужен только первый результат.
- [ ] Список из пяти пунктов меню, к которому применяют один `map`.
- [ ] Любая сортировка.
E: Ленивость окупается, когда можно остановиться раньше или когда данных очень много. Для пяти элементов она только усложняет отладку. Сортировка всё равно требует всех элементов.

Q: В `settleMenuTree` идентификаторы разделов лежат в `HashSet`, а не в `List`. Что это даёт?
- [ ] Порядок элементов сохраняется.
- [ ] Элементы становятся неизменяемыми.
- [x] Проверка `in` выполняется за постоянное время, а не перебором всего списка.
- [ ] Ничего: это одно и то же.
E: Для списка `in` перебирает элементы (O(n)); для `HashSet` ищет по хэшу (O(1) в среднем). В цикле по n элементам это разница между O(n²) и O(n).

Q: Верно ли, что `sortedBy { it.position }` сохраняет исходный порядок элементов с равной позицией?
- [x] Да, сортировка устойчивая.
- [ ] Нет, порядок равных элементов случайный.
- [ ] Только для `sortedByDescending`.
- [ ] Только если элементы `Comparable`.
E: Сортировки в стандартной библиотеке Kotlin устойчивы. Тем не менее, если порядок важен, лучше явно задать второй ключ, чтобы не зависеть от порядка входных данных.
:::

## Итоги

- Описывайте результат цепочкой преобразований, а не циклом с накоплением.
- Выбирайте `Set` для «есть ли», `Map` для «по ключу», `List` для порядка.
- Функции возвращают данные, а решения (ошибка, предупреждение, 404) принимает вызывающий код.
- Ловушки: молчаливая потеря в `associateBy`, `first` вместо `firstOrNull`, неполная сортировка.

Дальше: [Моделирование предметной области](lesson:k3-modeling).
