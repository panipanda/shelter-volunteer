# Ошибки, ресурсы и конкурентность

Сервер живёт долго и обслуживает много людей одновременно. Значит, две темы неизбежны: что делать, когда что-то идёт не так, и как не испортить общие данные, когда два запроса работают одновременно. В проекте обе темы решены аккуратно и на небольшом коде, что делает его хорошим учебником.

После урока вы сможете:

- выбирать между исключением, `null` и результатом-значением и писать сообщения об ошибках, по которым можно действовать;
- использовать `require`, `check`, `error`, `use`, `try/finally`;
- объяснить, что такое корутина, `suspend` и `Mutex`, и написать ограничитель попыток входа;
- распознать гонку данных и выбрать средство: неизменяемость, `@Volatile`, `Atomic*`, блокировку.

## Часть 1. Ошибки

### Три способа сказать «не получилось»

| Способ | Когда | Пример в проекте |
|---|---|---|
| `null` | отсутствие значения ожидаемо и не требует объяснения | `findById(id): Cat?`, `toIntOrNull()` |
| Результат-значение | причин неудачи несколько и их нужно показать человеку | `FormResult<T>` в админке |
| Исключение | нарушено то, что не должно нарушаться; продолжать нельзя | `check(...)` при старте, `NoSuchElementException` |

Хорошее правило: **исключение — для ситуаций, которых вызывающий код не ожидает и не может разумно обработать на месте**. «Пользователь ввёл не число» — не исключение, а обычный вход. «Нет соединения с базой при запуске» — исключение, потому что продолжать бессмысленно.

### require, check, error

```run
fun parsePort(text: String): Int {
    val port = text.toIntOrNull()
    require(port != null) { "PORT must be a number, got \"$text\"" }
    require(port in 1..65535) { "PORT must be in 1..65535, got $port" }
    return port
}

fun main() {
    println(parsePort("8080"))
    listOf("abc", "70000").forEach { text ->
        try {
            parsePort(text)
        } catch (e: IllegalArgumentException) {
            println("error: ${e.message}")
        }
    }
    val started = false
    try {
        check(started) { "the server is not started" }
    } catch (e: IllegalStateException) {
        println("state: ${e.message}")
    }
}
```

```output
8080
error: PORT must be a number, got "abc"
error: PORT must be in 1..65535, got 70000
state: the server is not started
```

- `require(условие) { сообщение }` проверяет **аргументы**: если ложь, бросает `IllegalArgumentException`. Вызывающий передал плохое значение.
- `check(условие) { сообщение }` проверяет **состояние**: бросает `IllegalStateException`. Объект или программа в неподходящем состоянии.
- `error("сообщение")` бросает `IllegalStateException`, когда выполнение дошло туда, куда не должно.
- После `require(port != null)` компилятор знает, что `port` не `null`. Это умное приведение через контракт.

:::project В проекте: ошибки, по которым можно действовать
Функция `adminConfigFromEnv` ([AdminAuth.kt](repo:src/main/kotlin/org/proanima/shelter/admin/AdminAuth.kt)) проверяет настройки админки при старте:

```kotlin
check(password != null && secret != null) { "ADMIN_PASSWORD and SESSION_SECRET must be set together" }
check(password.length >= MIN_PASSWORD_LENGTH) { "ADMIN_PASSWORD is too short: use at least $MIN_PASSWORD_LENGTH characters" }
```

Обратите внимание на три свойства этих сообщений. Во-первых, они говорят, **что** не так и **что делать** («use at least 12 characters»). Во-вторых, они **никогда не содержат само значение**: пароль не попадёт в журнал. То же в `parseDatabaseUrl`: строка подключения содержит пароль, поэтому сообщение об ошибке говорит только «DATABASE_URL has no host». В-третьих, неверная настройка **останавливает запуск**: при деплое на Render новая версия не заменит рабочую, и сайт продолжит работать на предыдущей. Лучше упасть сразу и громко, чем работать с дырой в защите.
:::

### Когда ломать, а когда деградировать

В проекте есть два противоположных поведения, и оба осознанные:

- **Файловый режим:** проверка данных (`validatePets`) при старте, при ошибке приложение **не запускается**. Данные лежат в репозитории, и ошибку исправляет разработчик до деплоя.
- **Режим базы:** та же проверка только **пишет предупреждения**, сайт работает. Данные редактируют волонтёры через админку, и опечатка не должна «класть» сайт для всех посетителей.

Решение зависит от того, кто может исправить ошибку и какова цена простоя. Это не вопрос вкуса: в `docs/decisions.md` оно записано прямым текстом.

Тот же подход к деградации виден в меню. Если база недоступна, `siteMenu` ловит исключение и возвращает меню из пяти страниц: страница откроется без дополнительных разделов, а не покажет ошибку.

```kotlin
internal fun ApplicationCall.siteMenu(locale: AppLocale): NavMenu {
    val source = application.attributes.getOrNull(SiteMenuKey) ?: return NavMenu.fixed(locale)
    return try {
        source(locale)
    } catch (e: Exception) {
        log.warn("The menu could not be built: {}", e.message)
        NavMenu.fixed(locale)
    }
}
```

### Результат как значение

Форма в админке должна показать **все** ошибки сразу, а не первую. Исключение для этого не подходит, нужно значение.

```run
class FormResult<T>(val value: T?, val errors: Map<String, String>) {
    val ok: Boolean get() = value != null && errors.isEmpty()
}

data class Year(val value: Int)

fun parseYear(text: String, today: Int): FormResult<Year> {
    val year = text.trim().toIntOrNull()
    return when {
        year == null -> FormResult(null, mapOf("birthYear" to "Enter a number"))
        year !in 2000..today -> FormResult(null, mapOf("birthYear" to "Enter a year from 2000 to $today"))
        else -> FormResult(Year(year), emptyMap())
    }
}

fun main() {
    for (input in listOf("2021", "abc", "1999")) {
        val result = parseYear(input, 2026)
        println("$input -> ok=${result.ok} ${result.value ?: result.errors}")
    }
}
```

```output
2021 -> ok=true Year(value=2021)
abc -> ok=false {birthYear=Enter a number}
1999 -> ok=false {birthYear=Enter a year from 2000 to 2026}
```

Это упрощённая версия `FormResult` из [AdminForms.kt](repo:src/main/kotlin/org/proanima/shelter/admin/AdminForms.kt). Ошибки привязаны к **имени поля**, поэтому страница может показать сообщение рядом с нужным полем и оставить всё, что человек уже набрал. Реальные формы строятся на этом: см. урок про [UX](lesson:u1-ux).

### Ресурсы: use и finally

```run
class Resource(val name: String) : AutoCloseable {
    init { println("open $name") }
    override fun close() = println("close $name")
}

fun main() {
    Resource("a").use { println("working with ${it.name}") }
    try {
        Resource("b").use { error("boom") }
    } catch (e: IllegalStateException) {
        println("caught ${e.message}")
    }
}
```

```output
open a
working with a
close a
open b
close b
caught boom
```

`use` закрывает ресурс при выходе из блока, **в том числе при исключении**. Все JDBC-вызовы в проекте обёрнуты так: `connection.prepareStatement(...).use { ... }`. Забытое закрытие соединения при пуле из трёх штук (`maximumPoolSize = 3`) означало бы, что через три такие ошибки сайт перестанет отвечать.

:::warn runCatching: аккуратно
`runCatching { ... }` перехватывает **всё**, включая отмену корутины (`CancellationException`) и `OutOfMemoryError`. В серверном коде лучше ловить конкретные исключения. В проекте везде `catch (e: SQLException)`, `catch (e: DateTimeParseException)`, и только на границах (`StatusPages`, `siteMenu`) `Exception` или `Throwable`.
:::

## Часть 2. Конкурентность

### Что происходит на сервере

Netty обслуживает много соединений на небольшом числе потоков. Ktor запускает обработчик маршрута как **корутину**. Если два посетителя открывают сайт одновременно, два обработчика могут работать **одновременно** и касаться одних и тех же объектов: кэша, меню, счётчиков.

### Корутины за пять минут

Корутина — лёгкая «задача», которая умеет приостанавливаться, не блокируя поток. Функция, способная приостанавливаться, помечается `suspend`.

```run
import kotlinx.coroutines.*

fun main() = runBlocking {
    val a = async { delay(100); "cats" }
    val b = async { delay(100); "dogs" }
    println("${a.await()} and ${b.await()}")

    val job = launch {
        try {
            delay(10_000)
        } finally {
            println("cancelled, cleaning up")
        }
    }
    delay(50)
    job.cancelAndJoin()
}
```

```output
cats and dogs
cancelled, cleaning up
```

- `launch` запускает задачу «и забыл» и возвращает `Job`; `async` возвращает `Deferred` с результатом (`await()`).
- `delay` приостанавливает корутину, **не занимая поток**, в отличие от `Thread.sleep`.
- Обе `delay(100)` в `async` выполнились параллельно: общее время около 100 мс, а не 200.
- Отмена (`cancelAndJoin`) прерывает корутину в точке приостановки, а `finally` выполняется. Структурная конкурентность: дочерние корутины живут внутри родительской и не «утекают».

### Блокирующий код в корутинах

JDBC блокирующий. Вызов `statement.executeQuery(...)` в обработчике держит поток, пока база отвечает. На небольшом сайте с кэшем на 30 секунд это допустимо: в [PgRepositories.kt](repo:src/main/kotlin/org/proanima/shelter/repository/PgRepositories.kt) почти все чтения идут из памяти. Блокирующие вызовы переносят на отдельный диспетчер: `withContext(Dispatchers.IO) { ... }`. Проект так и делает там, где чтение не из кэша: маршрут картинок ([ImageRoutes.kt](repo:src/main/kotlin/org/proanima/shelter/routes/ImageRoutes.kt)) читает фото из базы внутри `withContext(Dispatchers.IO) { store.get(name) }`, потому что файл может быть в несколько мегабайт.

А вот обработку фотографий проект **выносит** из потока обработчика: `ImageProcessing.process` выполняет её в `withContext(Dispatchers.Default)` и пускает по одной штуке через `Mutex`:

```kotlin
private val gate = Mutex()

suspend fun process(bytes: ByteArray): ProcessedPhoto = gate.withLock {
    withContext(Dispatchers.Default) { processNow(bytes) }
}
```

Причина указана в комментарии: «decoding and scaling a large photo takes tens of megabytes, and the hosting plan has little memory. The others wait their turn». Это **ограничение ресурса**: пять одновременных загрузок по 30 мегапикселей уложили бы сервер с 256 МБ кучи.

### Гонка данных

Классический пример: два потока увеличивают общий счётчик.

```kotlin
var counter = 0
// two threads, each: repeat(1000) { counter++ }
// the result is often less than 2000
```

`counter++` — это три шага: прочитать, прибавить, записать. Если два потока прочитали одно значение, один прирост теряется. Средства защиты, от лучших к худшим:

1. **Неизменяемость.** Нет записи — нет гонки. Модели проекта — `data class` с `val`.
2. **Неизменяемый снимок с атомарной подменой.** `SiteContent` никогда не меняется после создания, правка создаёт новый и подменяет ссылку.
3. **Атомарные типы.** `AtomicInteger.incrementAndGet()` выполняет «прочитать-прибавить-записать» неделимо.
4. **Блокировки.** `Mutex` для корутин, `synchronized` для потоков.

```run
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*

fun main() = runBlocking {
    val safe = AtomicInteger()
    withContext(Dispatchers.Default) {
        coroutineScope {
            repeat(100) {
                launch {
                    repeat(1000) { safe.incrementAndGet() }
                }
            }
        }
    }
    println(safe.get())
}
```

```output
100000
```

### @Volatile и неизменяемый снимок

Посмотрите на `InMemorySiteContentRepository` ([SiteContentRepository.kt](repo:src/main/kotlin/org/proanima/shelter/repository/SiteContentRepository.kt)):

```kotlin
class InMemorySiteContentRepository(initial: Map<ContentKey, String> = emptyMap()) : MutableSiteContentRepository {

    @Volatile
    private var content = SiteContent(initial)

    override fun load(): SiteContent = content

    @Synchronized
    override fun update(changes: Map<ContentKey, String?>) {
        val entries = content.entries.toMutableMap()
        changes.forEach { (key, text) -> if (text == null) entries.remove(key) else entries[key] = text }
        content = SiteContent(entries)
    }
}
```

Читатели (`load`) не блокируются и всегда видят **целый** снимок. Писатели (`update`) идут по одному (`@Synchronized`), собирают новую карту и подменяют ссылку. `@Volatile` гарантирует, что подмену сразу увидят другие потоки. Читатель никогда не увидит наполовину обновлённое состояние, потому что старое никто не меняет.

### Кэш и счётчик поколений

Самое тонкое место проекта: кэш списка в `PgDocuments`. Страницы читают список из памяти до 30 секунд, запись сбрасывает кэш. Проблема в гонке:

1. Читатель начал загрузку из базы (запрос ещё идёт).
2. Администратор сохранил кошку, кэш сброшен.
3. Читатель получил **старый** результат запроса, начатого до записи, и положил его в кэш. 30 секунд сайт показывает устаревшее, хотя админ только что сохранил.

Лечение, найденное в коде, называется «счётчик поколений»:

```kotlin
@Volatile
private var generation = 0L

fun findAll(): List<T> {
    ...
    val startedAt = generation
    return try {
        load().also { items ->
            if (generation == startedAt) {
                snapshot = Snapshot(now, items)
            }
        }
    } ...
}

private fun invalidate() {
    generation++
    snapshot = null
}
```

Читатель запоминает поколение до запроса; если после запроса оно изменилось, значит была запись, и результат в кэш не кладётся (но текущему читателю отдаётся). Комментарий в коде: «Counts writes. A read that started before a write must not put its (already stale) result in the cache».

:::note Честное замечание
`generation++` на `@Volatile` не атомарно. Если две записи придут одновременно, одно приращение может потеряться. Для этого кода это безвредно: важно лишь, чтобы значение **изменилось**, а не сколько раз. Для настоящего счётчика нужен `AtomicLong`. Хороший пример того, что корректность зависит от того, что именно вам нужно от переменной.
:::

### ThreadLocal и корутины

`SiteMenu` в [SiteMenu.kt](repo:src/main/kotlin/org/proanima/shelter/views/SiteMenu.kt) хранит меню «текущей страницы» в `ThreadLocal`, чтобы не передавать его параметром в десятки функций-представлений. Это безопасно только потому, что страница строится **целиком в одном потоке без приостановок**: блок `HTML.() -> Unit` — обычная, не `suspend`-лямбда. Если бы внутри блока был `suspend`-вызов, корутина могла бы продолжиться на другом потоке и «потерять» меню. Комментарий в коде это прямо оговаривает («every page is drawn by respondPage in one go on one thread»). Скрытая зависимость, зафиксированная комментарием, — типичный компромисс; об альтернативах поговорим в уроке про [рефакторинг](lesson:a4-refactoring).

## Live coding: ограничитель неудачных входов

Задача: админка защищена паролем. Нужно, чтобы подбор пароля был медленным, но настоящий администратор не мог оказаться заблокированным злоумышленником.

::::live Throttle шаг за шагом
Мы повторим `FailedLoginThrottle` из проекта и посмотрим, зачем нужна каждая строка.

:::step Шаг 1. Просто задержка
После неверного пароля подождать секунду.

```kotlin
suspend fun penalize() {
    delay(1000)
}
```

Это не помогает: сто одновременных попыток ждут свои секунды **параллельно**. Злоумышленник перебирает сто паролей в секунду.
:::

:::step Шаг 2. Один за другим через Mutex
Пропускаем неудачные попытки по одной.

```kotlin
private val gate = Mutex()

suspend fun penalize() {
    gate.withLock { delay(1000) }
}
```

Теперь **суммарно** не больше одной попытки в секунду, сколько бы запросов ни пришло. Но очередь неограничена: десять тысяч запросов выстроятся в очередь и будут держать память.
:::

:::step Шаг 3. Ограничиваем очередь
Считаем ожидающих. Если их слишком много, отвечаем сразу «позже».

```kotlin
private val waiting = AtomicInteger()

suspend fun penalize(): Boolean {
    if (waiting.incrementAndGet() > maxWaiting) {
        waiting.decrementAndGet()
        return false
    }
    ...
}
```

Счётчик увеличивается **атомарно**: два запроса одновременно не получат одно и то же значение.
:::

:::step Шаг 4. Освобождаем счётчик всегда
Если ожидание прервано (запрос отменён), счётчик всё равно должен уменьшиться.

```kotlin
try {
    gate.withLock { delay(penaltyMillis) }
} finally {
    waiting.decrementAndGet()
}
return true
```

Без `finally` отменённые запросы «залипали» бы в счётчике, и через время все попытки отклонялись бы навсегда.
:::

:::step Шаг 5. Запускаем
Семь одновременных неудачных попыток при лимите очереди пять.

```run
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FailedLoginThrottle(private val penaltyMillis: Long = 1000, private val maxWaiting: Int = 5) {
    private val gate = Mutex()
    private val waiting = AtomicInteger()

    // false: too many attempts are waiting, answer "try later" without waiting.
    suspend fun penalize(): Boolean {
        if (waiting.incrementAndGet() > maxWaiting) {
            waiting.decrementAndGet()
            return false
        }
        try {
            gate.withLock { delay(penaltyMillis) }
        } finally {
            waiting.decrementAndGet()
        }
        return true
    }
}

fun main() = runBlocking {
    val throttle = FailedLoginThrottle(penaltyMillis = 50, maxWaiting = 5)
    val results = (1..7).map { async { throttle.penalize() } }.awaitAll()
    println("accepted=${results.count { it }} turnedAway=${results.count { !it }}")
}
```

```output
accepted=5 turnedAway=2
```

Пять попыток получили свою паузу по очереди, две последние были отвергнуты сразу.
:::

:::step Шаг 6. Почему администратор не заблокирован
Посмотрите, как маршрут `POST /admin/login` использует ограничитель (из `AdminRoutes.kt`):

```kotlin
if (passwordMatches(entered, admin.config.password)) {
    // correct password: log in at once, the throttle is not touched
} else if (admin.throttle.penalize()) {
    // wrong password, answered after the pause: 403
} else {
    // too many wrong attempts are waiting: 429
}
```

Верный пароль **не заходит** в ограничитель. Поэтому даже во время атаки настоящий администратор входит мгновенно. В `docs/decisions.md` это записано как сознательное решение: блокировка по числу попыток позволила бы посторонним заблокировать волонтёров.
:::
::::

## Практика

::::task Безопасный счётчик посещений
Запустите 50 корутин на `Dispatchers.Default`, каждая увеличивает общий счётчик 1000 раз. Напишите версию на `AtomicInteger`, которая всегда даёт 50000.

```starter
import kotlinx.coroutines.*

fun main() = runBlocking {
    // TODO: share a safe counter between 50 coroutines on Dispatchers.Default
    println(0)
}
```

:::hint
Скопируйте образец из урока и поменяйте числа. Продумайте, почему `var counter = 0` с `counter++` здесь даёт случайные результаты.
:::

:::solution
```run
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*

fun main() = runBlocking {
    val counter = AtomicInteger()
    withContext(Dispatchers.Default) {
        coroutineScope {
            repeat(50) {
                launch { repeat(1000) { counter.incrementAndGet() } }
            }
        }
    }
    println(counter.get())
}
```

```output
50000
```

Альтернатива: `Mutex` вокруг `counter++`. Она работает, но медленнее и сложнее: когда нужна одна общая переменная, атомарный тип лучше блокировки.
:::
::::

::::task Результат вместо исключения
Напишите `parseMonth(text: String): MonthResult`, где `MonthResult` — `sealed interface` с `Ok(month: Int)` и `Invalid(message: String)`. Сообщения: для не числа `"not a number"`, для числа вне 1..12 `"month must be 1..12, got N"`.

```starter
sealed interface MonthResult {
    // TODO: Ok and Invalid
}

fun parseMonth(text: String): MonthResult {
    // TODO
    TODO()
}

fun main() {
    listOf("5", "13", "x").forEach { println(parseMonth(it)) }
}
```

:::solution
```run
sealed interface MonthResult {
    data class Ok(val month: Int) : MonthResult
    data class Invalid(val message: String) : MonthResult
}

fun parseMonth(text: String): MonthResult {
    val month = text.trim().toIntOrNull() ?: return MonthResult.Invalid("not a number")
    return if (month in 1..12) MonthResult.Ok(month) else MonthResult.Invalid("month must be 1..12, got $month")
}

fun main() {
    listOf("5", "13", "x").forEach { println(parseMonth(it)) }
}
```

```output
Ok(month=5)
Invalid(message=month must be 1..12, got 13)
Invalid(message=not a number)
```

Сравните с `FormResult` проекта: он собирает **несколько** ошибок по полям формы. Для одного значения хватает `sealed` с двумя вариантами. А вызывающий код с `when` обязан обработать оба случая.
:::
::::

::::task Что может пойти не так
Найдите в этом коде две ошибки, связанные с темой урока, и опишите, как их исправить.

```kotlin
class Counter {
    private var hits = 0

    fun hit() { hits++ }                      // called by many request handlers

    fun readFile(path: String): String {
        val reader = java.io.File(path).bufferedReader()
        return reader.readText()
    }
}
```

:::solution
1. **Гонка данных.** `hits++` не атомарна, при одновременных вызовах приросты теряются. Исправление: `private val hits = AtomicInteger()` и `hits.incrementAndGet()`.
2. **Утечка ресурса.** `bufferedReader()` не закрывается. Исправление: `java.io.File(path).bufferedReader().use { it.readText() }` или просто `java.io.File(path).readText()`, который закрывает файл сам.

Вторая ошибка особенно коварна: на тестах не видна, а на сервере через время заканчиваются дескрипторы файлов.
:::
::::

:::quiz Проверьте себя
Q: Пользователь ввёл в форме год рождения «abc». Какой способ сообщить об ошибке подходит лучше всего?
- [ ] Бросить `IllegalStateException`.
- [ ] Вернуть `null` без объяснения.
- [x] Вернуть результат с сообщением, привязанным к полю формы, чтобы показать его рядом с полем.
- [ ] Завершить программу.
E: Некорректный ввод — обычная ситуация, а не сбой программы. Результат-значение позволяет собрать все ошибки и показать их человеку.

Q: В чём разница между `require` и `check`?
- [ ] `require` для чисел, `check` для строк.
- [x] `require` проверяет аргументы (`IllegalArgumentException`), `check` проверяет состояние (`IllegalStateException`).
- [ ] Они взаимозаменяемы без различий.
- [ ] `check` работает только в тестах.
E: Разные исключения говорят, кто виноват: вызывающий (плохой аргумент) или сам объект (неподходящее состояние).

Q: Почему сообщения в `adminConfigFromEnv` и `parseDatabaseUrl` не содержат значений настроек?
- [ ] Чтобы сообщения были короче.
- [x] Значение может быть паролем или строкой подключения с паролем; сообщения попадают в журнал.
- [ ] Так требует Kotlin.
- [ ] Значения всё равно неизвестны.
E: Секреты не должны оказываться в логах, трассировках и сообщениях об ошибках. Сообщение описывает требование, а не вводит значение.

Q: Почему в режиме базы проверка данных только пишет предупреждения, а в файловом режиме останавливает запуск?
- [ ] Это случайность.
- [x] В режиме базы данные правят волонтёры без разработчика, и опечатка не должна «класть» сайт; в файловом режиме ошибку исправит разработчик до деплоя.
- [ ] База данных не умеет проверять данные.
- [ ] Файлы всегда содержат ошибки.
E: Выбор между «упасть» и «деградировать» определяется тем, кто исправит ошибку и сколько стоит простой.

Q: Какие утверждения о `Mutex` в `ImageProcessing` верны?
- [x] Он пропускает обработку фото по одной.
- [x] Причина: большая картинка занимает десятки мегабайт, а памяти на хостинге мало.
- [ ] Он ускоряет обработку.
- [ ] Он нужен, потому что JPEG нельзя читать параллельно.
E: Это ограничение ресурса, а не ускорение. Конкурентные запросы ждут своей очереди, не занимая поток (приостановка, а не блокировка).

Q: Зачем в `PgDocuments` читатель запоминает `generation` до запроса?
- [ ] Чтобы считать число запросов.
- [x] Чтобы не положить в кэш результат запроса, начатого до записи: он уже устарел.
- [ ] Чтобы ускорить SQL.
- [ ] Чтобы сериализовать JSON.
E: Если за время запроса случилась запись, поколение изменилось, и кэшировать результат нельзя: иначе 30 секунд сайт показывал бы старые данные.

Q: Почему `ThreadLocal` в `SiteMenu` безопасен именно в этом коде?
- [ ] `ThreadLocal` безопасен всегда.
- [x] Страница строится синхронно в одном потоке: блок не `suspend`, корутина не может переехать на другой поток посередине.
- [ ] Меню неизменяемо.
- [ ] Потому что используется `@Volatile`.
E: `ThreadLocal` привязан к потоку. Корутины могут продолжаться на другом потоке после приостановки; без приостановок внутри блока привязка стабильна.
:::

## Итоги

- Ожидаемое (плохой ввод, отсутствие) выражайте значением. Исключение — для нарушенных инвариантов и невозможности продолжать.
- Сообщения об ошибках должны подсказывать действие и не содержать секретов.
- `use` и «выполнить вокруг» гарантируют освобождение ресурсов.
- От гонок защищают, в порядке предпочтения: неизменяемость, атомарная подмена снимка, `Atomic*`, блокировки.
- Верный пароль не должен ждать: ограничивайте злоумышленника, а не законного пользователя.

Дальше: [Данные: JSON, SQL и JDBC](lesson:k6-data).
