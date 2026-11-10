# Тестирование и TDD

В проекте больше девяти тысяч строк тестов, почти столько же, сколько самого кода. Это не перфекционизм: у сайта нет отдела тестирования, а правки вносят волонтёры через админку и разработчики через код. Тесты стоят вместо коллеги, который сказал бы «осторожно, ты сломаешь русскую версию». А в `Dockerfile` есть строка, которая делает их обязательными: **упавший тест не даёт собрать образ**, и сломанный коммит не доедет до продакшена.

После урока вы сможете:

- выбирать вид теста под задачу и объяснять «пирамиду»;
- писать понятные тесты (имена-предложения, структура «дано, когда, тогда»);
- управлять временем и внешними зависимостями через подмены (`Clock`, фейки, H2);
- вести разработку через тесты (TDD) и работать с регрессиями.

## Пирамида тестов на примере проекта

```text
          /\          HTTP + браузер (вручную / Playwright): снимки экрана, чек-лист
         /  \
        /----\        route и интеграционные тесты: testApplication, H2 вместо PostgreSQL
       /      \
      /--------\      юнит-тесты сервисов и алгоритмов: быстрые, без сети и диска
```

| Уровень | Примеры файлов | Что проверяет | Скорость |
|---|---|---|---|
| Юнит | `NextVisitTest`, `MenuTreeTest`, `DisplayHelpersTest` | чистая логика | миллисекунды |
| Сервис с подменой | `CatServiceTest`, `DogServiceTest` | правила поверх интерфейса репозитория | миллисекунды |
| Репозиторий на базе | `PgRepositoriesTest`, `SeederTest` | настоящий SQL на H2 в режиме PostgreSQL | десятки миллисекунд |
| HTTP | `HomeRoutesTest`, `AccessibilityTest`, `AdminAuthTest` | страницы целиком, заголовки, статусы, куки | десятки миллисекунд |
| Сквозные проверки содержимого | `DatabaseModeTest`, `TypographyTest` | «сайт на базе байт в байт как на файлах», стиль кавычек в тексте | секунды |
| Вручную | `docs/smoke-checklist.md`, скриншоты Playwright | то, что тестами не поймать: вид и удобство | минуты |

Правило пирамиды: **много быстрых нижних тестов, мало медленных верхних.** Нижние ловят ошибку и показывают, где она; верхние подтверждают, что части работают вместе. Перевёрнутая пирамида (всё через браузер) медленна, хрупка и плохо объясняет, что сломалось.

## Тест как документ

Названия тестов проекта читаются как требования:

```kotlin
@Test
fun `a trip that has already left is not the next one`() { ... }

@Test
fun `cancelling several trips in a row moves on to the following week`() { ... }

@Test
fun `HEAD request to a missing page is a 404 as before`() { ... }
```

Имя в обратных кавычках позволяет писать **предложение**, а не `testNextVisit2`. Когда тест падает, имя говорит, какое ожидание нарушено, ещё до чтения кода.

Хорошая структура теста: **дано** (подготовка), **когда** (одно действие), **тогда** (проверки). Один тест проверяет **одно поведение**, и если нужно проверить пять, это пять тестов или один с понятной общей мыслью.

## Подмены: фейки вместо моков

Сервис не должен знать, откуда данные, поэтому в тесте мы можем подставить **что угодно**, реализующее интерфейс. Вот `CatServiceTest` проекта:

```kotlin
private val repository = object : CatRepository {
    override fun findAll(): List<Cat> = cats
    override fun findById(id: Int): Cat? = cats.firstOrNull { it.id == id }
}

private val service = CatService(repository)
```

Это **фейк**: упрощённая рабочая реализация (список в памяти). Альтернатива, **мок**, запоминает вызовы и проверяет «был ли вызван `findAll` один раз». В проекте нет ни одной библиотеки моков, и это сознательно:

| | Фейк (проект) | Мок |
|---|---|---|
| Что проверяем | результат (**состояние**) | факт вызова (**взаимодействие**) |
| Устойчивость к рефакторингу | высокая: внутренности можно менять | низкая: тест привязан к тому, как вызывается |
| Усилия | написать маленький класс | настроить ожидания |
| Когда нужен | почти всегда | когда важен сам побочный эффект (отправка письма) |

Тесты, проверяющие «как» (какие методы в каком порядке вызваны), ломаются при каждом рефакторинге, хотя поведение не изменилось. Тесты, проверяющие «что» (какой результат), переживают перестройки кода. Это одна из главных причин, почему рефакторинг в проекте идёт спокойно.

### Время как зависимость

Код, который берёт «сейчас» из системы (`LocalDate.now()`), нельзя проверить детерминированно: завтра тест будет другим. Решение: **передавать время**.

- Функции принимают `today: LocalDate = LocalDate.now()` (мы видели в `displayCatAge`).
- Объекты принимают `Clock`: кэш, админка, расписание.
- В тестах есть `TestClock`, часами которого управляют вручную ([TestDatabase.kt](repo:src/test/kotlin/org/proanima/shelter/repository/TestDatabase.kt)):

```kotlin
class TestClock(var now: Instant = Instant.parse("2026-11-08T10:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now
}
```

Тест кэша «через 30 секунд данные перечитываются» занимает миллисекунды: часы просто переводятся вперёд. Тест без управления временем либо спит 30 секунд, либо нестабилен.

### База для тестов: H2 в режиме PostgreSQL

Тесты репозиториев запускают **настоящий SQL**, но на встроенной базе:

```kotlin
fun newTestDataSource(): DataSource {
    val dataSource = JdbcDataSource()
    dataSource.setURL("jdbc:h2:mem:shelter-test-${counter.incrementAndGet()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
    createSchema(dataSource)
    return dataSource
}
```

Каждый вызов даёт **свою** пустую базу (счётчик в имени), поэтому тесты не видят строк друг друга. Общее состояние между тестами главный источник «плавающих» падений. Цена: H2 это не PostgreSQL, поэтому SQL в проекте ограничен общим подмножеством (см. урок про [данные](lesson:k6-data): `TEXT`, а не `jsonb`). Это же причина, по которой в CI важен прогон на настоящем окружении (раз в какое-то время).

## Тесты через HTTP

```kotlin
@Test
fun `unknown cat returns a localized HTML 404`() = testApplication {
    application { module() }

    val ru = client.get("/ru/cats/nobody")
    assertEquals(HttpStatusCode.NotFound, ru.status)
    assertTrue(ru.plainText().contains("Страница не найдена"))
    assertTrue(client.get("/en/cats/nobody").plainText().contains("Page not found"))
}
```

Две вспомогательные функции проекта показывают зрелость тестов ([TestSupport.kt](repo:src/test/kotlin/org/proanima/shelter/routes/TestSupport.kt)):

```kotlin
// Pages join short words to the next one with a no-break space; the route tests compare the words,
// so they read the body with those spaces turned back into plain ones.
suspend fun HttpResponse.plainText(): String = bodyAsText().replace(' ', ' ')

// The order of attributes in the output is the builder's business, so a tag is found by what it contains.
fun hasTag(body: String, name: String, vararg attributes: String): Boolean =
    Regex("<$name [^>]*>").findAll(body).any { tag -> attributes.all { it in tag.value } }
```

Обе решают одну задачу: **не привязывать тест к несущественному**. Неразрывный пробел и порядок атрибутов это детали вывода; если тест сравнивает строки целиком, он падает от каждой типографской правки. Хороший тест проверяет то, ради чего существует поведение.

Проверим идею на настоящем коде. Этот тест компилируется и проходит:

```kotlin check="test"
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.main
import kotlinx.html.title
import kotlin.test.Test
import kotlin.test.assertTrue

fun hasTag(body: String, name: String, vararg attributes: String): Boolean =
    Regex("<$name [^>]*>").findAll(body).any { tag -> attributes.all { it in tag.value } }

class SkipLinkTest {
    @Test
    fun `a page starts with a skip link and has a main landmark`() = testApplication {
        application {
            routing {
                get("/") {
                    call.respondHtml {
                        head { title { +"Cats" } }
                        body {
                            a(href = "#main", classes = "skip-link") { +"Skip to content" }
                            main { id = "main" }
                        }
                    }
                }
            }
        }

        val body = client.get("/").bodyAsText()

        assertTrue(hasTag(body, "a", "href=\"#main\"", "class=\"skip-link\""))
        assertTrue(hasTag(body, "main", "id=\"main\""))
        assertTrue(body.indexOf("skip-link") < body.indexOf("<main"), "the skip link comes first")
    }
}
```

## Что тестировать

Не «каждую функцию», а **поведение и риски**:

1. **Правила предметной области**: черновики не видны публике, выезд в самый момент отправления.
2. **Границы**: ноль, один, максимум, пусто, `null`, переход через год.
3. **Ошибки**: что будет при неверном вводе, недоступной базе.
4. **Регрессии**: каждая найденная ошибка получает тест, который падает на ошибке и проходит на исправлении. Пример проекта: ответ на `HEAD` был `405`. В тесте записан комментарий: «a page gave 405». Ошибка больше не вернётся незаметно.
5. **Инварианты и свойства**: «обработка текста идемпотентна», «сайт на базе совпадает с сайтом на файлах».
6. **Доступность и политика**: `AccessibilityTest` проверяет ссылку-пропуск, подписи ориентиров, `lang`; `TypographyTest` проверяет, что в `content/*.md` нет прямых кавычек. Тесты охраняют не только код, но и **договорённости команды**.

Чего тестировать не стоит: библиотеки (Ktor уже проверен), геттеры, детали реализации.

## TDD: красный, зелёный, улучшение

Цикл разработки через тесты:

1. **Красный**: напишите тест на нужное поведение и убедитесь, что он **падает** по нужной причине.
2. **Зелёный**: напишите минимум кода, чтобы тест прошёл.
3. **Улучшение**: приведите код в порядок, не меняя поведения (тесты стерегут).

Зачем красный шаг: тест, который ни разу не падал, не доказывает, что умеет падать. Зачем «минимум» в зелёном: код, не нужный тестам, рискует оказаться нетестируемым. TDD ещё и заставляет думать об **интерфейсе** до реализации: вы сначала пишете, как хотите вызывать.

### Live coding: CatService по тестам

::::live Видимость, доступность, слаг
Построим сервис списка кошек в духе настоящего `CatService`. Фейк репозитория пишем в начале.

:::step Шаг 1. Красный: список не должен показывать черновики
Тест пишется первым и не компилируется, пока нет класса: это тоже «красный».

```kotlin
@Test
fun `drafts are not shown`() {
    val service = CatService(FakeCats(Cat(1, "Felix"), Cat(2, "Draft", published = false)))
    assertEquals(listOf("Felix"), service.getAllCats().map { it.name })
}
```
:::

:::step Шаг 2. Зелёный: минимум
Самое простое, что проходит тест.

```kotlin
class CatService(private val repository: CatRepository) {
    fun getAllCats() = repository.findAll().filter { it.published }
}
```
:::

:::step Шаг 3. Второе требование: доступные для усыновления
Новый тест: из опубликованных остаются только `FOR_ADOPTION`, а черновик с этим статусом всё равно скрыт.

```kotlin
fun getAvailableCats() = published().filter { it.stage == AdoptionStage.FOR_ADOPTION }
private fun published() = repository.findAll().filter { it.published }
```

Появилась маленькая приватная `published()`: её извлекли на этапе «улучшение», как только правило понадобилось дважды. Тесты первого шага продолжают проходить.
:::

:::step Шаг 4. Поиск по адресу и регрессия
Тест: неизвестный адрес даёт `null`, а черновик по адресу **не находится** (иначе его можно было бы увидеть, зная ссылку). Это именно такой тест, который пишут после ошибки.

```kotlin
fun getCatBySlug(slug: String): Cat? = published().firstOrNull { petSlug(it.name, it.id) == slug }
```
:::

:::step Шаг 5. Всё вместе
Запустите. Поменяйте `it.published` на `true` в `published()` и посмотрите, какие тесты упадут: именно они охраняют правило.

```test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

enum class AdoptionStage { NONE, FOR_ADOPTION, RESERVED, ADOPTED }

data class Cat(val id: Int, val name: String, val stage: AdoptionStage = AdoptionStage.NONE, val published: Boolean = true)

interface CatRepository {
    fun findAll(): List<Cat>
}

fun petSlug(name: String?, id: Int): String =
    name.orEmpty().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { id.toString() }

class CatService(private val repository: CatRepository) {
    fun getAllCats(): List<Cat> = published()

    fun getAvailableCats(): List<Cat> = published().filter { it.stage == AdoptionStage.FOR_ADOPTION }

    fun getCatBySlug(slug: String): Cat? = published().firstOrNull { petSlug(it.name, it.id) == slug }

    private fun published(): List<Cat> = repository.findAll().filter { it.published }
}

class FakeCats(vararg cats: Cat) : CatRepository {
    private val all = cats.toList()
    override fun findAll(): List<Cat> = all
}

class CatServiceTest {
    private val service = CatService(
        FakeCats(
            Cat(1, "Felix", AdoptionStage.FOR_ADOPTION),
            Cat(2, "Mila", AdoptionStage.ADOPTED),
            Cat(3, "Secret Draft", AdoptionStage.FOR_ADOPTION, published = false)
        )
    )

    @Test
    fun `drafts are not shown`() {
        assertEquals(listOf("Felix", "Mila"), service.getAllCats().map { it.name })
    }

    @Test
    fun `only published cats that look for a home are available`() {
        assertEquals(listOf("Felix"), service.getAvailableCats().map { it.name })
    }

    @Test
    fun `a cat is found by its slug`() {
        assertEquals(1, service.getCatBySlug("felix")?.id)
    }

    @Test
    fun `an unknown slug is null`() {
        assertNull(service.getCatBySlug("nobody"))
    }

    @Test
    fun `a draft is not reachable even when its address is known`() {
        assertNull(service.getCatBySlug("secret-draft"))
    }
}
```
:::
::::

## Качество самих тестов

Тесты тоже код, и они бывают хорошими и плохими.

| Запах | Признак | Лечение |
|---|---|---|
| **Хрупкий** | падает от безобидных правок разметки | проверять смысл (`hasTag`, `plainText`), а не строку целиком |
| **Нестабильный (flaky)** | то проходит, то нет | время через `Clock`, своя база на тест, никаких `sleep`, порядок независим |
| **Привязан к реализации** | мок проверяет порядок вызовов | проверять результат на фейке |
| **Загадочный гость** | непонятно, откуда данные в проверке | подготовка видна в самом тесте |
| **Тест без проверки** | выполняет код, но ничего не утверждает | `assert...` с понятным сообщением |
| **Дубликат подготовки** | один и тот же блок в двадцати тестах | вспомогательная функция или базовый класс (`AdminTestBase`) |

### Покрытие и мутации

«Покрытие строк 90%» значит лишь, что тесты **прошли** по 90% строк, а не что они **проверяют** их. Тест без `assert` даст стопроцентное покрытие и нулевую защиту. Честнее вопрос: «если я испорчу код, упадёт ли тест?» На нём построено **мутационное тестирование**: инструмент автоматически портит код (заменяет `<` на `<=`, удаляет вызов) и смотрит, замечают ли тесты. Выжившая мутация это дыра в тестах. Вы можете сделать то же вручную, как мы это делали в live coding: «поменяю условие и посмотрю, что упадёт».

## Практика

::::task Тесты для shortened
Напишите тесты для функции из урока про [моделирование](lesson:k3-modeling). Нужны минимум пять случаев: короткий текст без изменений; текст ровно по лимиту; обрезка по границе слова; удаление хвостовых знаков; слово длиннее лимита (в нём нет пробела).

```starter target="junit"
import kotlin.test.Test
import kotlin.test.assertEquals

fun String.shortened(limit: Int): String {
    if (length <= limit) return this
    val cut = take(limit)
    return cut.substringBeforeLast(' ', cut).trimEnd(',', ';', ':', '-', ' ') + "…"
}

class ShortenedTest {
    @Test
    fun `short text is unchanged`() {
        // TODO
    }

    // TODO: four more tests
}
```

:::solution
```test
import kotlin.test.Test
import kotlin.test.assertEquals

fun String.shortened(limit: Int): String {
    if (length <= limit) return this
    val cut = take(limit)
    return cut.substringBeforeLast(' ', cut).trimEnd(',', ';', ':', '-', ' ') + "…"
}

class ShortenedTest {
    @Test
    fun `short text is unchanged`() {
        assertEquals("Short text", "Short text".shortened(50))
    }

    @Test
    fun `text exactly at the limit is unchanged`() {
        assertEquals("12345", "12345".shortened(5))
    }

    @Test
    fun `a long text is cut at a word boundary`() {
        assertEquals("Cats like to…", "Cats like to sleep in the sun".shortened(15))
    }

    @Test
    fun `trailing punctuation is removed before the ellipsis`() {
        assertEquals("one two…", "one two, three".shortened(9))
    }

    @Test
    fun `a single word longer than the limit is cut inside the word`() {
        assertEquals("abcde…", "abcdefghij".shortened(5))
    }
}
```

Анализ границ: «ровно по лимиту» ловит ошибку `<` против `<=`; «слово длиннее лимита» проверяет второй аргумент `substringBeforeLast` (без него вышло бы пустое `"…"`). Каждый тест называется предложением и проверяет **одно** поведение.
:::
::::

::::task Тест кэша с управляемым временем
Напишите три теста для `CachedList` из урока про данные: (1) второй вызов в пределах TTL не обращается к источнику; (2) после TTL источник читается снова; (3) если источник упал после истечения TTL, отдаётся старая копия. Часы: переменная `clock`.

```starter target="junit"
import kotlin.test.Test
import kotlin.test.assertEquals

class CachedList<T>(
    private val ttlMillis: Long,
    private val now: () -> Long,
    private val load: () -> List<T>
) {
    private class Snapshot<T>(val loadedAt: Long, val items: List<T>)

    @Volatile
    private var snapshot: Snapshot<T>? = null

    fun findAll(): List<T> {
        val cached = snapshot
        val time = now()
        if (cached != null && time - cached.loadedAt < ttlMillis) return cached.items
        return try {
            load().also { snapshot = Snapshot(time, it) }
        } catch (e: IllegalStateException) {
            if (cached == null) throw e
            cached.items
        }
    }
}

// TODO: a test class with three tests; use `var clock = 0L` and a counter of source reads
```

:::solution
```test
import kotlin.test.Test
import kotlin.test.assertEquals

class CachedList<T>(
    private val ttlMillis: Long,
    private val now: () -> Long,
    private val load: () -> List<T>
) {
    private class Snapshot<T>(val loadedAt: Long, val items: List<T>)

    @Volatile
    private var snapshot: Snapshot<T>? = null

    fun findAll(): List<T> {
        val cached = snapshot
        val time = now()
        if (cached != null && time - cached.loadedAt < ttlMillis) return cached.items
        return try {
            load().also { snapshot = Snapshot(time, it) }
        } catch (e: IllegalStateException) {
            if (cached == null) throw e
            cached.items
        }
    }
}

class CachedListTest {
    private var clock = 0L
    private var reads = 0
    private var down = false

    private val cache = CachedList(ttlMillis = 30_000, now = { clock }) {
        reads++
        if (down) error("database is asleep")
        listOf("Felix")
    }

    @Test
    fun `a second call within the lifetime does not read the source`() {
        cache.findAll()
        clock = 29_999
        cache.findAll()

        assertEquals(1, reads)
    }

    @Test
    fun `after the lifetime the source is read again`() {
        cache.findAll()
        clock = 30_000
        cache.findAll()

        assertEquals(2, reads)
    }

    @Test
    fun `when the source fails after expiry the old copy is served`() {
        cache.findAll()
        clock = 31_000
        down = true

        assertEquals(listOf("Felix"), cache.findAll())
    }
}
```

Три теста идут по трём путям `findAll` и занимают миллисекунды: время переводится вручную. Границу `29_999` и `30_000` мы проверяем намеренно: именно здесь ошибки «на единицу» (`<` против `<=`). Обратите внимание, что поля `clock`, `reads`, `down` создаются заново для каждого теста: JUnit создаёт новый объект класса для каждого `@Test`, поэтому тесты не влияют друг на друга.
:::
::::

::::task Почините плохой тест
Что не так с этим тестом? Найдите минимум четыре проблемы и перепишите.

```kotlin
@Test
fun test1() {
    val service = CatService(mock(CatRepository::class.java))
    `when`(repo.findAll()).thenReturn(listOf(Cat(1, "A", published = false)))
    val result = service.getAllCats()
    verify(repo, times(1)).findAll()
    println(result)
    Thread.sleep(2000)
}
```

:::solution
Проблемы:

1. **Имя `test1`** не говорит, что проверяется.
2. **Нет проверки результата**: `println(result)` вместо `assert`. Тест не упадёт, даже если поведение сломано.
3. **Проверка взаимодействия** (`verify(... times(1))`): привязывает тест к реализации. Если сервис начнёт кэшировать, тест упадёт без изменения поведения.
4. **`Thread.sleep(2000)`** без причины делает тест медленным и скрывает проблему времени.
5. Моки там, где проще фейк. Плюс переменная `repo` не объявлена: ошибка компиляции, то есть «тест» вообще не работает.

Переписанный вариант:

```kotlin
@Test
fun `a draft is not shown to the public`() {
    val service = CatService(FakeCats(Cat(1, "A", published = false)))

    assertEquals(emptyList(), service.getAllCats())
}
```
:::
::::

:::quiz Проверьте себя
Q: Почему в проекте для `CatRepository` в тестах используют фейк (анонимный объект), а не мок-библиотеку?
- [ ] Мок-библиотеки запрещены в Kotlin.
- [x] Фейк проверяет результат (состояние) и переживает рефакторинг, а мок привязывает тест к тому, как сервис вызывает репозиторий.
- [ ] Фейк быстрее мока.
- [ ] Фейк нужен только для баз данных.
E: Если тест проверяет, **как** что-то сделано, а не **что** получилось, любой рефакторинг ломает тест.

Q: Для чего в коде введены `Clock` и параметр `today`?
- [ ] Для красоты.
- [x] Чтобы время можно было подменить в тесте и получить детерминированный результат.
- [ ] Чтобы ускорить код.
- [ ] Это требование Ktor.
E: «Сейчас» это скрытый вход функции. Явная передача делает поведение воспроизводимым.

Q: Чем опасен общий разделяемый между тестами объект (например, одна база данных на все тесты)?
- [ ] Ничем.
- [x] Тесты начинают влиять друг на друга, результат зависит от порядка запуска: возникают «плавающие» падения.
- [ ] Тесты становятся быстрее.
- [ ] База данных перестаёт работать.
E: Поэтому `newTestDataSource()` даёт каждому тесту свою пустую базу.

Q: Каков порядок шагов в TDD?
- [ ] Код, тест, рефакторинг.
- [x] Красный тест, минимальный код до зелёного, улучшение кода.
- [ ] Рефакторинг, код, тест.
- [ ] Тест после завершения всей разработки.
E: Красный шаг доказывает, что тест умеет падать; «минимум» не даёт нагромождать непроверяемый код.

Q: Что показывает мутационное тестирование?
- [ ] Скорость выполнения тестов.
- [x] Замечают ли тесты намеренную порчу кода: выжившая мутация это дыра в тестах.
- [ ] Процент покрытых строк.
- [ ] Количество тестов.
E: Покрытие говорит о том, что строка выполнялась, а мутации о том, что её правильность проверяется.

Q: Почему `hasTag` ищет тег по содержимому, а не сравнивает строку HTML целиком?
- [ ] Строки сравнивать нельзя.
- [x] Порядок атрибутов и мелкие правки вывода не должны ронять тест; проверяется только значимое.
- [ ] Так короче.
- [ ] Это требование kotlinx.html.
E: Хрупкий тест падает от любой правки вёрстки и приучает команду не верить красному.

Q: Что нужно сделать, когда найдена ошибка в работающем коде?
- [x] Сначала написать тест, воспроизводящий её, увидеть красный результат.
- [x] Затем исправить код и убедиться, что тест позеленел и остальные тоже.
- [ ] Исправить код и не писать тест: ошибка и так очевидна.
- [ ] Удалить упавшие тесты.
E: Регрессионный тест гарантирует, что та же ошибка не вернётся незаметно.
:::

## Итоги

- Много быстрых нижних тестов, мало медленных верхних; тесты называйте предложениями.
- Время, базу и внешний мир подменяйте фейками и параметрами; общего состояния между тестами быть не должно.
- Проверяйте результат, а не способ его получения; не привязывайтесь к несущественному (`plainText`, `hasTag`).
- Каждая найденная ошибка получает регрессионный тест. TDD: красный, зелёный, улучшение.
- Покрытие не равно защите: спрашивайте, упадёт ли тест, если код испортить.

Дальше: [Качество кода и рефакторинг](lesson:a4-refactoring).
