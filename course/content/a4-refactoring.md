# Качество кода и рефакторинг

Код читают гораздо чаще, чем пишут. Поэтому «хороший код» — это не тот, что хитро устроен, а тот, который через полгода можно прочитать, понять и безопасно изменить. **Рефакторинг** — это изменение устройства кода **без изменения его поведения**, чтобы следующая правка стала дешевле. В этом уроке мы не будем хвалить проект: мы найдём в нём настоящие проблемы и исправим одну из них под защитой тестов.

После урока вы сможете:

- называть запахи кода и подбирать для них приёмы рефакторинга;
- работать мелкими шагами с тестами-страховкой и не смешивать рефакторинг с новым поведением;
- написать характеризующие тесты для кода, у которого тестов нет;
- оценивать качество по измеримым признакам, а не по вкусу.

## Что такое «хороший код»

Договоримся о признаках, по которым можно спорить:

| Признак | Вопрос к коду |
|---|---|
| **Читаемость** | Можно ли понять функцию за минуту, не читая соседние файлы? |
| **Одна причина для изменения** | Если изменится правило X, правится одно место? |
| **Слабая связность** | Сколько других мест придётся менять вместе с этим? |
| **Явные зависимости** | Видно ли из сигнатуры, что нужно функции (время, база, меню страницы)? |
| **Проверяемость** | Можно ли проверить без сети, диска и часов? |
| **Честные имена** | Имя говорит правду о том, что делает вещь, сегодня? |

Проект неплох по большинству пунктов: функции короткие, имена выразительные, комментарии объясняют **почему**, а не «что». Эти комментарии надо ценить:

```kotlin
// Both sides are hashed first, so the comparison takes the same time whatever the password is and
// whatever its length.
fun passwordMatches(entered: String, expected: String): Boolean = ...
```

Комментарий «increments i» бесполезен; комментарий, объясняющий неочевидное решение, спасает будущего читателя от того, чтобы «упростить» код и сломать защиту.

## Каталог запахов на материале проекта

Запах кода — это не ошибка, а признак, что здесь может быть проблема. Ниже проблемы, которые видно в самом проекте (на момент написания курса).

| Запах | Где | Чем грозит | Приём |
|---|---|---|---|
| **Дублирование** | логика кэша трижды: `PgDocuments`, `PgSiteContentRepository`, `PgMenuRepository`; циклы по кошкам и собакам в `validatePets`; почти одинаковые `catKind` и `dogKind` | исправили в одном месте, забыли в двух других | Извлечь класс или функцию |
| **Длинный список параметров** | `configureRoutes` (10), `AdminSetup` (10), `PetKind` (около 20 полей) | сложно вызывать и расширять | Объект-параметр, разбивка |
| **Большой файл** | `AdminViews.kt` (755 строк), `AdminRoutes.kt` (700), `AdminSectionViews.kt` (656) | трудно найти нужное, конфликты при правках | Разделить по смыслу |
| **Одержимость примитивами** | формы как `Map<String, String>`; идентификаторы узлов меню `"page:cats"`, `"group:g1"` со сравнением префиксов | опечатка в строке не ловится компилятором | Типы-обёртки, `sealed` |
| **Скрытая зависимость** | `ThreadLocal` с меню страницы в `SiteMenu` | работает, пока код синхронный; в тесте нужно знать о нём | Передавать явно |
| **Неточное имя** | `CatLocation` используется в `Dog`; `displayCatAge` считает возраст и собак | читатель думает, что код только для кошек | Переименовать |
| **Цепочка условий** | `displayCatAge`: `if / else if` на пять ветвей, ключи сообщений строками | растёт с каждым случаем | Таблица, `when` |

Отметим: ни один из пунктов не мешает сайту работать. Это **долг**, а не авария. Вопрос не «исправить всё», а «что исправление окупит в ближайшие правки».

## Правила безопасного рефакторинга

1. **Тесты сначала.** Рефакторинг без тестов это переписывание наугад. Если тестов нет, сначала пишут **характеризующие**: они фиксируют то, что код делает сейчас (даже если это неидеально), чтобы заметить любое изменение.
2. **Мелкие шаги.** Один шаг: тесты зелёные. Следующий шаг: тесты зелёные. Если красный, шаг был слишком большим, возвращайтесь.
3. **Две шляпы.** В каждый момент вы либо меняете поведение (новая функция, исправление ошибки), либо рефакторите. Не оба сразу и не в одном коммите. Тогда при ошибке видно, что её породило.
4. **Не переписывать с нуля.** Переписывание теряет накопленные в коде знания о граничных случаях (каждое странное условие когда-то было чьей-то ошибкой). Улучшайте постепенно.
5. **Правило бойскаута:** уходя, оставь код чуть чище, чем нашёл. Не «давайте займёмся рефакторингом месяц», а непрерывно, рядом с задачей.
6. **Инструменты.** Переименование, извлечение функции, перемещение: в IntelliJ IDEA это команды, которые меняют код безопасно во всех местах. Ручная правка текста для этого опаснее.

## Live coding: убираем дублирование в validatePets

Функция `validatePets` ([DataValidation.kt](repo:src/main/kotlin/org/proanima/shelter/service/DataValidation.kt)) проверяет данные кошек и собак. Если читать код внимательно, видно два одинаковых блока:

```kotlin
cats.forEach { cat ->
    if (cat.birthMonth != null && cat.birthMonth !in 1..12) {
        problems += "cats.json: cat ${cat.id} has birthMonth ${cat.birthMonth}, expected 1..12"
    }
    if (cat.birthMonth != null && cat.birthYear == null) {
        problems += "cats.json: cat ${cat.id} has birthMonth but no birthYear"
    }
    problems += missingPhotos("cats.json", "cat ${cat.id}", cat.photoUrls, photosAt, hasPhoto)
}
dogs.forEach { dog ->
    // the same three checks with "dogs.json" and "dog"
}
```

Правило нового вида проверки придётся добавлять дважды, и однажды его добавят только в одно место. Рефакторим по шагам.

::::live Из двух циклов в один
Мы работаем с упрощённой копией: те же проверки и те же сообщения, но без файловой системы.

:::step Шаг 1. Страховка: характеризующие тесты
Сначала фиксируем текущее поведение. Тесты сравнивают **множество** сообщений: порядок сообщений не часть договорённости (его никто не читает как контракт; это решение мы записываем самим тестом).

```test
import kotlin.test.Test
import kotlin.test.assertEquals

data class Cat(val id: Int, val nameEn: String?, val birthYear: Int? = null, val birthMonth: Int? = null, val photoUrls: List<String> = emptyList())
data class Dog(val id: Int, val nameEn: String, val birthYear: Int? = null, val birthMonth: Int? = null, val photoUrls: List<String> = emptyList())

fun petSlug(name: String?, id: Int): String =
    name.orEmpty().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { id.toString() }

private fun duplicates(file: String, field: String, values: List<String>): List<String> =
    values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.map { "$file: duplicate $field \"$it\"" }

private fun missingPhotos(file: String, owner: String, photoUrls: List<String>, hasPhoto: (String) -> Boolean): List<String> =
    photoUrls.filter { !hasPhoto(it) }.map { "$file: $owner photo \"$it\" is missing" }

// BEFORE: the same three checks are written twice, once for cats and once for dogs.
fun validatePets(cats: List<Cat>, dogs: List<Dog>, hasPhoto: (String) -> Boolean): List<String> {
    val problems = mutableListOf<String>()

    problems += duplicates("cats.json", "id", cats.map { it.id.toString() })
    problems += duplicates("cats.json", "slug", cats.map { petSlug(it.nameEn, it.id) })
    problems += duplicates("dogs.json", "id", dogs.map { it.id.toString() })
    problems += duplicates("dogs.json", "slug", dogs.map { petSlug(it.nameEn, it.id) })

    cats.forEach { cat ->
        if (cat.birthMonth != null && cat.birthMonth !in 1..12) {
            problems += "cats.json: cat ${cat.id} has birthMonth ${cat.birthMonth}, expected 1..12"
        }
        if (cat.birthMonth != null && cat.birthYear == null) {
            problems += "cats.json: cat ${cat.id} has birthMonth but no birthYear"
        }
        problems += missingPhotos("cats.json", "cat ${cat.id}", cat.photoUrls, hasPhoto)
    }
    dogs.forEach { dog ->
        if (dog.birthMonth != null && dog.birthMonth !in 1..12) {
            problems += "dogs.json: dog ${dog.id} has birthMonth ${dog.birthMonth}, expected 1..12"
        }
        if (dog.birthMonth != null && dog.birthYear == null) {
            problems += "dogs.json: dog ${dog.id} has birthMonth but no birthYear"
        }
        problems += missingPhotos("dogs.json", "dog ${dog.id}", dog.photoUrls, hasPhoto)
    }

    return problems
}

class ValidatePetsTest {
    private val photos = setOf("/images/a.jpg")
    private val hasPhoto: (String) -> Boolean = { it in photos }

    private fun problems(cats: List<Cat> = emptyList(), dogs: List<Dog> = emptyList()) =
        validatePets(cats, dogs, hasPhoto).toSet()

    @Test
    fun `clean data has no problems`() {
        assertEquals(emptySet(), problems(listOf(Cat(1, "Felix", 2020, 5, listOf("/images/a.jpg"))), listOf(Dog(1, "Rex"))))
    }

    @Test
    fun `duplicate ids and slugs are reported per file`() {
        assertEquals(
            setOf("cats.json: duplicate id \"1\"", "cats.json: duplicate slug \"felix\""),
            problems(cats = listOf(Cat(1, "Felix"), Cat(1, "Felix")))
        )
        assertEquals(setOf("dogs.json: duplicate slug \"rex\""), problems(dogs = listOf(Dog(1, "Rex"), Dog(2, "Rex"))))
    }

    @Test
    fun `a month outside 1 to 12 is reported`() {
        assertEquals(setOf("cats.json: cat 3 has birthMonth 13, expected 1..12"), problems(cats = listOf(Cat(3, "Nami", 2020, 13))))
    }

    @Test
    fun `a month without a year is reported`() {
        assertEquals(setOf("dogs.json: dog 5 has birthMonth but no birthYear"), problems(dogs = listOf(Dog(5, "Sava", null, 4))))
    }

    @Test
    fun `a missing photo is reported`() {
        assertEquals(setOf("cats.json: cat 1 photo \"/images/x.jpg\" is missing"), problems(cats = listOf(Cat(1, "Felix", photoUrls = listOf("/images/x.jpg")))))
    }
}
```

Тесты зелёные. Теперь у нас есть сетка, под которой можно работать.
:::

:::step Шаг 2. Находим общее
Сравним два блока: отличаются **только** три вещи: имя файла (`cats.json` / `dogs.json`), слово в сообщении (`cat` / `dog`) и тип объекта. Всё остальное одинаково. Значит, нужен общий тип с тем, что проверка читает.

```kotlin
private class PetFacts(
    val file: String,
    val kind: String,
    val id: Int,
    val nameEn: String?,
    val birthYear: Int?,
    val birthMonth: Int?,
    val photoUrls: List<String>
)
```

Это приём «ввести объект-параметр» (или «адаптер»): мы не трогаем `Cat` и `Dog`, а описываем один набор фактов, нужный проверке.
:::

:::step Шаг 3. Преобразования и одна проверка
Две крошечные функции-преобразования и одна функция проверки.

```kotlin
private fun Cat.facts() = PetFacts("cats.json", "cat", id, nameEn, birthYear, birthMonth, photoUrls)
private fun Dog.facts() = PetFacts("dogs.json", "dog", id, nameEn, birthYear, birthMonth, photoUrls)

private fun checkPet(pet: PetFacts, hasPhoto: (String) -> Boolean): List<String> = buildList {
    if (pet.birthMonth != null && pet.birthMonth !in 1..12) {
        add("${pet.file}: ${pet.kind} ${pet.id} has birthMonth ${pet.birthMonth}, expected 1..12")
    }
    if (pet.birthMonth != null && pet.birthYear == null) {
        add("${pet.file}: ${pet.kind} ${pet.id} has birthMonth but no birthYear")
    }
    addAll(missingPhotos(pet.file, "${pet.kind} ${pet.id}", pet.photoUrls, hasPhoto))
}
```

После каждой такой правки запускаем тесты. Пока красный не появился, идём дальше.
:::

:::step Шаг 4. Итог и те же тесты
`validatePets` теперь состоит из двух строк: превратить в факты и проверить. Тесты **не менялись** ни на символ.

```test
import kotlin.test.Test
import kotlin.test.assertEquals

data class Cat(val id: Int, val nameEn: String?, val birthYear: Int? = null, val birthMonth: Int? = null, val photoUrls: List<String> = emptyList())
data class Dog(val id: Int, val nameEn: String, val birthYear: Int? = null, val birthMonth: Int? = null, val photoUrls: List<String> = emptyList())

fun petSlug(name: String?, id: Int): String =
    name.orEmpty().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { id.toString() }

private fun duplicates(file: String, field: String, values: List<String>): List<String> =
    values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.map { "$file: duplicate $field \"$it\"" }

private fun missingPhotos(file: String, owner: String, photoUrls: List<String>, hasPhoto: (String) -> Boolean): List<String> =
    photoUrls.filter { !hasPhoto(it) }.map { "$file: $owner photo \"$it\" is missing" }

// AFTER: one description of what a check reads, one check.
private class PetFacts(
    val file: String,
    val kind: String,
    val id: Int,
    val nameEn: String?,
    val birthYear: Int?,
    val birthMonth: Int?,
    val photoUrls: List<String>
)

private fun Cat.facts() = PetFacts("cats.json", "cat", id, nameEn, birthYear, birthMonth, photoUrls)
private fun Dog.facts() = PetFacts("dogs.json", "dog", id, nameEn, birthYear, birthMonth, photoUrls)

fun validatePets(cats: List<Cat>, dogs: List<Dog>, hasPhoto: (String) -> Boolean): List<String> =
    validateKind(cats.map { it.facts() }, hasPhoto) + validateKind(dogs.map { it.facts() }, hasPhoto)

private fun validateKind(pets: List<PetFacts>, hasPhoto: (String) -> Boolean): List<String> {
    val file = pets.firstOrNull()?.file ?: return emptyList()
    return duplicates(file, "id", pets.map { it.id.toString() }) +
        duplicates(file, "slug", pets.map { petSlug(it.nameEn, it.id) }) +
        pets.flatMap { checkPet(it, hasPhoto) }
}

private fun checkPet(pet: PetFacts, hasPhoto: (String) -> Boolean): List<String> = buildList {
    if (pet.birthMonth != null && pet.birthMonth !in 1..12) {
        add("${pet.file}: ${pet.kind} ${pet.id} has birthMonth ${pet.birthMonth}, expected 1..12")
    }
    if (pet.birthMonth != null && pet.birthYear == null) {
        add("${pet.file}: ${pet.kind} ${pet.id} has birthMonth but no birthYear")
    }
    addAll(missingPhotos(pet.file, "${pet.kind} ${pet.id}", pet.photoUrls, hasPhoto))
}

class ValidatePetsTest {
    private val photos = setOf("/images/a.jpg")
    private val hasPhoto: (String) -> Boolean = { it in photos }

    private fun problems(cats: List<Cat> = emptyList(), dogs: List<Dog> = emptyList()) =
        validatePets(cats, dogs, hasPhoto).toSet()

    @Test
    fun `clean data has no problems`() {
        assertEquals(emptySet(), problems(listOf(Cat(1, "Felix", 2020, 5, listOf("/images/a.jpg"))), listOf(Dog(1, "Rex"))))
    }

    @Test
    fun `duplicate ids and slugs are reported per file`() {
        assertEquals(
            setOf("cats.json: duplicate id \"1\"", "cats.json: duplicate slug \"felix\""),
            problems(cats = listOf(Cat(1, "Felix"), Cat(1, "Felix")))
        )
        assertEquals(setOf("dogs.json: duplicate slug \"rex\""), problems(dogs = listOf(Dog(1, "Rex"), Dog(2, "Rex"))))
    }

    @Test
    fun `a month outside 1 to 12 is reported`() {
        assertEquals(setOf("cats.json: cat 3 has birthMonth 13, expected 1..12"), problems(cats = listOf(Cat(3, "Nami", 2020, 13))))
    }

    @Test
    fun `a month without a year is reported`() {
        assertEquals(setOf("dogs.json: dog 5 has birthMonth but no birthYear"), problems(dogs = listOf(Dog(5, "Sava", null, 4))))
    }

    @Test
    fun `a missing photo is reported`() {
        assertEquals(setOf("cats.json: cat 1 photo \"/images/x.jpg\" is missing"), problems(cats = listOf(Cat(1, "Felix", photoUrls = listOf("/images/x.jpg")))))
    }
}
```

Обратите внимание, что в «после» исчез повтор, а **новую проверку** (например, год рождения не в будущем) теперь надо добавить один раз в `checkPet`, и она сразу охватит и кошек, и собак. Рефакторинг окупается при следующей правке. Побочный эффект: появился порядок сообщений, отличающийся от прежнего, и ни один тест этого не заметил, потому что порядок мы сознательно сделали не частью контракта.
:::

:::step Шаг 5. Что дальше
Следующий шаг мог бы быть глубже: общий интерфейс `Pet` в модели вместо адаптеров `facts()`. Но он затрагивает все места, где используются `Cat` и `Dog` (сервисы, представления, админка), поэтому его стоит делать отдельным, большим по охвату изменением, под наблюдением тестов этих мест. Одно из свойств хорошего рефакторинга: вы **выбираете**, где остановиться, по соотношению цены и пользы.
:::
::::

## Ещё приёмы, которые стоит узнавать

| Приём | Когда | Пример |
|---|---|---|
| **Извлечь функцию** | кусок кода объясняется комментарием | `published()` в сервисе |
| **Извлечь класс** | у класса две ответственности | общий `CachedValue<T>` из трёх репозиториев |
| **Переименовать** | имя соврёт | `CatLocation` → `AnimalLocation` |
| **Ввести объект-параметр** | длинный список параметров | зависимости в `Dependencies` |
| **Заменить условие таблицей или `when`** | цепочка `if` | ключи возрастных сообщений |
| **Заменить примитив типом** | строки с договорённостями | `NodeId` вместо `"group:g1"` |
| **Сделать зависимость явной** | скрытое состояние | меню страницы параметром функции |

Переименование `CatLocation` в `AnimalLocation` — хороший пример **переходного** рефакторинга. Если имя встречается в сотне мест, его можно менять постепенно: сначала `typealias CatLocation = AnimalLocation` для обратной совместимости, затем перевод мест по одному, затем удаление псевдонима. Тесты зелёные на каждом этапе, коммиты маленькие.

## Измеримое качество

Вкусовые споры («мне так больше нравится») заканчиваются, когда есть цифры. Полезные и дешёвые:

- **Длина функции и файла.** Файл в 700 строк это сигнал, не приговор.
- **Цикломатическая сложность:** число независимых путей через функцию; больше 10 значит, что функцию трудно полностью проверить. У `displayCatAge` после исправления возраста около десяти: каждая новая ветка добавляет путь, который нужно проверить.
- **Количество параметров:** больше четырёх-пяти запах.
- **Глубина вложенности:** больше трёх уровней запах.
- **Срабатывания статического анализа.** В проекте нет `ktlint` и `detekt`. Это очевидный и дешёвый следующий шаг: форматирование и базовые запахи проверяются автоматически в CI, и обсуждение стиля уходит из ревью.

И человеческий механизм: **ревью кода**. Хорошее ревью не ищет опечатки (это дело автоматики), а задаёт вопросы: понятно ли название? где граничные случаи? что случится, если зависимость недоступна? нужен ли тест? Просто вернитесь к списку признаков в начале урока.

## Практика

::::task Извлеките общий кэш
В коде две почти одинаковые реализации кэша. Выделите общий класс `Cached<T>` так, чтобы тесты остались прежними.

```starter
class ContentSource(private val ttlMillis: Long, private val now: () -> Long, private val read: () -> String) {
    private var value: String? = null
    private var loadedAt = 0L

    fun load(): String {
        val cached = value
        if (cached != null && now() - loadedAt < ttlMillis) return cached
        return read().also { value = it; loadedAt = now() }
    }
}

class MenuSource(private val ttlMillis: Long, private val now: () -> Long, private val read: () -> List<String>) {
    private var value: List<String>? = null
    private var loadedAt = 0L

    fun load(): List<String> {
        val cached = value
        if (cached != null && now() - loadedAt < ttlMillis) return cached
        return read().also { value = it; loadedAt = now() }
    }
}

// TODO: extract Cached<T>(ttlMillis, now, read) and make both classes use it
```

:::hint
Сделайте `class Cached<T>(...)` с тем же телом `load()`, но с типом `T`. Затем `ContentSource` и `MenuSource` станут тонкими обёртками (или обычными значениями `Cached<String>` и `Cached<List<String>>`).
:::

:::solution
```test
import kotlin.test.Test
import kotlin.test.assertEquals

class Cached<T>(private val ttlMillis: Long, private val now: () -> Long, private val read: () -> T) {
    private var value: T? = null
    private var loadedAt = 0L
    private var loaded = false

    fun load(): T {
        if (loaded && now() - loadedAt < ttlMillis) {
            @Suppress("UNCHECKED_CAST")
            return value as T
        }
        return read().also {
            value = it
            loadedAt = now()
            loaded = true
        }
    }
}

class ContentSource(ttlMillis: Long, now: () -> Long, read: () -> String) {
    private val cache = Cached(ttlMillis, now, read)
    fun load(): String = cache.load()
}

class MenuSource(ttlMillis: Long, now: () -> Long, read: () -> List<String>) {
    private val cache = Cached(ttlMillis, now, read)
    fun load(): List<String> = cache.load()
}

class CachedTest {
    private var clock = 0L
    private var reads = 0

    @Test
    fun `content is cached for its lifetime`() {
        val source = ContentSource(1000, { clock }) { reads++; "text" }
        source.load()
        clock = 999
        source.load()
        assertEquals(1, reads)
        clock = 1000
        source.load()
        assertEquals(2, reads)
    }

    @Test
    fun `the menu is cached the same way`() {
        val source = MenuSource(1000, { clock }) { reads++; listOf("cats") }
        assertEquals(listOf("cats"), source.load())
        source.load()
        assertEquals(1, reads)
    }
}
```

Что получилось: один `Cached<T>` вместо двух копий. Обратите внимание на флаг `loaded`: в одной из исходных версий проверялось `cached != null`, но для обобщённого `T` `null` может быть **законным значением** (например, «меню ни разу не сохраняли», как в `PgMenuRepository`, где «null is an answer worth caching too»). Рефакторинг заставил заметить различие между «значения нет» и «значение `null`». Такое бывает: обобщение выявляет скрытые допущения.
:::
::::

::::task Найдите запахи
Назовите в этом фрагменте не менее четырёх запахов кода и предложите приёмы.

```kotlin
fun process(a: Any?, b: String, c: Int, d: Boolean, e: Map<String, String>, f: List<String>): String {
    var r = ""
    if (d) {
        if (c > 0) {
            if (b == "cat") {
                r = "C"
            } else if (b == "dog") {
                r = "D"
            } else if (b == "bird") {
                r = "B"
            }
        }
    }
    // loop over f
    for (x in f) {
        r += x
    }
    return r
}
```

:::solution
1. **Длинный список параметров** (шесть) и один из них `Any?`: отсутствие типа. Приём: объект-параметр, конкретные типы.
2. **Непонятные имена** (`a`–`f`, `r`, `x`, `process`). Приём: переименовать.
3. **Глубокая вложенность** (три `if` подряд). Приём: ранние возвраты (guard clauses), `when`.
4. **Цепочка условий по строке** (`"cat"`, `"dog"`, `"bird"`): «магические строки». Приём: `enum` или таблица `mapOf("cat" to "C", ...)`.
5. **Бесполезный комментарий** (`// loop over f`). Приём: удалить; пусть код говорит сам.
6. **Накопление строки в цикле** (`r += x`) и изменяемая переменная. Приём: `joinToString`.
7. **Неиспользуемые параметры** (`a`, `e`). Приём: удалить.

Переписанная версия: `fun code(kind: AnimalKind): String = ...` и `fun joinNames(names: List<String>) = names.joinToString("")`.
:::
::::

::::task Решение: рефакторить или оставить
Файл `AdminViews.kt` содержит 755 строк. Команда не планирует менять админку в ближайшие три месяца. Стоит ли его делить? Напишите короткое обоснование (3–4 предложения) по критериям: цена, польза, риск.

:::solution
Возможный ответ: **не сейчас**. Цена: деление большого файла затрагивает много импортов и создаёт большой диф, который трудно ревьюить; риск: функции-представления разделяют вспомогательные (`FlowContent.fieldError`, `markInvalid`), и их раскладка требует решений. Польза проявляется только при правках, а правок не планируется. Кроме того, у файла уже есть внутренняя структура (секции по сущностям), и находить нужное функции удаётся через поиск по имени.

Но стоит **зафиксировать** это решение (коротко в `docs/decisions.md` или в комментарии наверху файла) и применить правило бойскаута: когда в файл придётся вносить правку, заодно выносить затронутую группу функций в соседний файл. Вывод: рефакторинг оправдан, когда он снижает стоимость ближайших изменений, а не просто потому, что число строк велико.
:::
::::

:::quiz Проверьте себя
Q: Что такое рефакторинг?
- [ ] Добавление новой функции в существующий код.
- [x] Изменение структуры кода без изменения его наблюдаемого поведения.
- [ ] Исправление ошибок.
- [ ] Переписывание проекта на другой язык.
E: Если поведение изменилось, это уже не рефакторинг, а изменение или исправление. Поэтому тесты, проходившие до, должны проходить и после.

Q: Что такое характеризующие тесты?
- [ ] Тесты, описывающие характер разработчика.
- [x] Тесты, фиксирующие текущее поведение существующего кода (даже неидеальное), чтобы безопасно его улучшать.
- [ ] Тесты производительности.
- [ ] Тесты интерфейса.
E: Сначала фиксируем, что есть. Решать, идеально ли оно, будем отдельно, «другой шляпой».

Q: Почему не стоит смешивать рефакторинг и новое поведение в одном коммите?
- [ ] Git так не работает.
- [x] При появлении ошибки непонятно, чем она вызвана: перестройкой или новым поведением; ревью и откат тоже сложнее.
- [ ] Это запрещено в Kotlin.
- [ ] Коммит получится слишком быстрым.
E: Принцип «две шляпы»: в каждый момент либо меняем поведение, либо структуру.

Q: Какой запах у кода, где одна и та же логика кэша записана в трёх классах?
- [x] Дублирование.
- [ ] Магические числа.
- [ ] Длинный метод.
- [ ] Мёртвый код.
E: Исправление ошибки придётся делать трижды, и однажды её исправят не везде. Приём: извлечь общий класс.

Q: Что лучше сделать, если нужно переименовать тип, используемый в сотне мест?
- [ ] Заменить текст поиском по всему проекту за один раз без тестов.
- [x] Использовать переименование IDE или переходный `typealias` и переводить места постепенно, запуская тесты.
- [ ] Оставить старое имя навсегда.
- [ ] Переписать проект.
E: Инструменты IDE и мелкие шаги дают безопасность, а ошибка находится сразу.

Q: Почему обобщение двух кэшей в `Cached<T>` потребовало флага `loaded` вместо проверки `value != null`?
- [ ] Это стиль.
- [x] Для обобщённого `T` значение `null` может быть законным ответом, который тоже надо кэшировать; «нет значения» и «значение `null`» различаются.
- [ ] Иначе не компилируется.
- [ ] Для скорости.
E: Обобщение вскрыло скрытое допущение исходных версий.

Q: Выберите верные утверждения о комментариях в коде.
- [x] Хороший комментарий объясняет, **почему** сделано именно так.
- [x] Комментарий, пересказывающий очевидный код, лучше удалить.
- [ ] Чем больше комментариев, тем лучше.
- [ ] Комментарии заменяют тесты.
E: Комментарий должен нести информацию, которой в коде нет: причины и ограничения.
:::

## Итоги

- Качество измеряется признаками: читаемость, одна причина для изменения, явные зависимости, проверяемость, честные имена.
- Рефакторинг безопасен только под тестами и малыми шагами; две шляпы не смешивают.
- Запахи в проекте реальны (дубли, длинные списки, большие файлы, скрытая зависимость), но чинить надо то, что окупится при следующей правке.
- Обобщение полезно и как способ заметить скрытые допущения.

Дальше: [Сборка, деплой и эксплуатация](lesson:a5-ops).
