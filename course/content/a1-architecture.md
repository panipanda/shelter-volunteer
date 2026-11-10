# Архитектура: слои, зависимости, решения

Архитектура — это не схема на стене, а ответ на вопрос «что от чего зависит и во что обойдётся изменение». Хорошая архитектура делает частые изменения дешёвыми: добавить язык, сменить хранилище, поправить правило показа. Плохая делает дорогим всё. В этом уроке мы разберём архитектуру проекта по реальному коду, измерим её (а не поверим на слово) и научимся записывать решения так, чтобы через год их можно было понять.

После урока вы сможете:

- объяснить слои проекта и направление зависимостей, нарисовать их и проверить автоматически;
- применять инверсию зависимостей, внедрение через конструктор, корень композиции, Null Object;
- отличать «чистое ядро» от «императивной оболочки»;
- записывать архитектурные решения (ADR) и честно называть их цену;
- находить расхождения между описанной и реальной архитектурой.

## Слои и правило зависимостей

README проекта описывает поток данных одной строкой:

```text
routes -> services -> repositories -> JSON/Markdown files   (no DATABASE_URL)
routes -> services -> repositories -> PostgreSQL            (DATABASE_URL set)
```

Разложим по пакетам и направим стрелки «знает о»:

```text
         admin ─────────────┐
           │                │
         routes             │
           │  ╲             │
         views  service ◄───┤
           │      │  ╲      │
           ▼      ▼   ▼     ▼
           model ◄─ repository
```

Главное правило: **зависимости направлены к центру**, а в центре самое стабильное и чистое. `model` ничего не знает ни о ком (в его файлах нет ни одного импорта из других пакетов проекта). `service` знает о `model` и об *интерфейсах* репозиториев, но не о том, как они реализованы. `views` рисуют данные и не лезут в базу. `routes` склеивают запрос с сервисом и ответом.

Что это даёт на практике:

| Изменение | Что затронет |
|---|---|
| Перейти с файлов на PostgreSQL | только `repository/` и одну функцию в `Application.kt` |
| Поменять разметку карточки | только `views/` |
| Изменить правило «кто виден публике» | только `service/` |
| Добавить язык | `AppLocale`, файл сообщений, данные |

## Инверсия зависимостей: интерфейс на стороне потребителя

`CatService` хочет получить кошек. Он мог бы вызвать `File("data/cats.json")` напрямую, но тогда он знал бы про файлы, и перейти на базу значило бы переписать сервис. Вместо этого сервис объявляет, **что ему нужно**, интерфейсом, а реализации подстраиваются:

```run
interface CatRepository {
    fun findAll(): List<String>
}

class JsonCats : CatRepository {
    override fun findAll() = listOf("Felix (file)")
}

class PgCats : CatRepository {
    override fun findAll() = listOf("Felix (database)")
}

class CatService(private val repository: CatRepository) {
    fun names() = repository.findAll().map { it.uppercase() }
}

fun compose(databaseUrl: String?): CatService =
    CatService(if (databaseUrl.isNullOrBlank()) JsonCats() else PgCats())

fun main() {
    println(compose(null).names())
    println(compose("postgresql://example").names())
}
```

```output
[FELIX (FILE)]
[FELIX (DATABASE)]
```

Это три идеи в одном примере.

1. **Инверсия зависимостей.** Сервис зависит от *абстракции* (`CatRepository`), а не от файлов или базы. Зависимость «перевернулась»: не высокий уровень подстраивается под детали хранения, а детали под него.
2. **Внедрение через конструктор.** Сервис получает репозиторий в конструкторе и сам его не создаёт. Никакого фреймворка внедрения зависимостей не нужно: достаточно соглашения «всё необходимое передают снаружи».
3. **Корень композиции.** Функция `compose` — единственное место, где решается, какая реализация в работе. В проекте это `Application.kt`: `moduleWithFiles()` и `moduleWithDatabase()`. Все остальные части приложения живут, не зная об этом выборе.

:::project В проекте
Откройте [Application.kt](repo:src/main/kotlin/org/proanima/shelter/Application.kt). Выбор режима хранения делает функция `module()`: `if (databaseUrl.isEmpty()) moduleWithFiles() else moduleWithDatabase(databaseUrl)`. Дальше обе ветки вызывают **одну и ту же** `configureRoutes(...)` с разными репозиториями. Строки маршрутов и представлений не содержат ни одного `if (database)`. А тест `DatabaseModeTest` проверяет самое важное следствие: сайт на пустой базе выглядит **байт в байт** так же, как сайт на файлах.
:::

## Паттерны, которые встретились в проекте

Названия паттернов нужны не для экзаменов, а чтобы быстро объяснять идеи коллегам.

| Паттерн | Где в проекте | Зачем |
|---|---|---|
| Repository | `CatRepository`, `PgCatRepository`, `JsonCatRepository` | отделить правила от хранения |
| Dependency Injection (конструктор) | `CatService(repository)` | подменяемые зависимости, тесты |
| Composition Root | `Application.kt` | одно место выбора реализаций |
| Null Object | `NoSiteContent`, `NoSections`, `NoMenu` | нет проверок «а есть ли» |
| Decorator | `OverlayBundle`, `EditableGuideRepository` | наложить правки на исходные тексты |
| Strategy | `PetKind<T>` в админке: набор функций (путь, форма, сохранение) | один код маршрутов для кошек, собак и записей разделов |
| Execute-around | `inTransaction`, `SiteMenu.showing` | гарантированное завершение |
| Snapshot + атомарная подмена | `SiteContent`, кэш `PgDocuments` | читатели не блокируются |

`PetKind<T>` ([AdminRoutes.kt](repo:src/main/kotlin/org/proanima/shelter/admin/AdminRoutes.kt)) стоит рассмотреть отдельно. Кошки, собаки и записи разделов имеют одинаковый набор действий (список, форма, предпросмотр, фото, удаление), различаются они данными и формой. Вместо наследования проект описал различия **записью функций**:

```kotlin
internal class PetKind<T : Any>(
    val urlName: String,
    val id: (T) -> Int,
    val title: (T) -> String,
    val all: () -> List<T>,
    val save: (T) -> T,
    val delete: (Int) -> Boolean,
    val validate: (Map<String, String>, T?, List<T>) -> FormResult<T>,
    ...
)
```

Это «стратегия через лямбды». Добавление нового вида записей это ещё один экземпляр `PetKind`, а не новый класс с переопределением. Цена: у `PetKind` около двадцати полей, и она читается тяжело (мы вернёмся к этому в уроке про [рефакторинг](lesson:a4-refactoring)).

## Чистое ядро и императивная оболочка

Функцию `nextScheduledVisit(now, overrides)` ([NextVisit.kt](repo:src/main/kotlin/org/proanima/shelter/service/NextVisit.kt)) легко тестировать, потому что она **чистая**: ничего не читает снаружи (время передано параметром), ничего не пишет, результат зависит только от аргументов. Всё «грязное» (взять текущее время из `Clock`, достать отмены из файла, ответить по HTTP) находится снаружи, в маршруте:

```kotlin
get("/visits") {
    val nextVisit = nextScheduledVisit(LocalDateTime.now(clock), visitOverrides)
    val messages = siteContent.load().bundle(locale)
    call.respondPage(locale) { visitsPage(nextVisit, locale, call.request.path(), messages) }
}
```

Эту схему называют **функциональное ядро, императивная оболочка**: чем больше логики живёт в чистых функциях, тем дешевле тестировать и понимать систему. Обратите внимание на `Clock` в параметрах: *время* — тоже внешняя зависимость, и поэтому его передают, как репозиторий.

## Измеряем архитектуру

Мы описали правила, но верны ли они? Проверим по реальным импортам. Выше сказано: `model` не знает ни о ком; `service` не знает о `views`, `routes`, `admin`; `views` не знают о `repository`.

Подсчёт по коду на момент написания курса:

- `model`: ни одного импорта из других пакетов проекта.
- `service`: нет импортов `views`, `routes`, `admin`.
- `views`: нет импортов `repository`, `routes`, `admin`.
- **`routes`**: 7 из 9 файлов импортируют `SiteContentRepository` и `NoSiteContent` из `repository`, а один (маршрут картинок) работает с `PhotoStore` напрямую.
- **`repository`** импортирует типы из `service`: `ContentKey`, `SiteContent`, `NextVisit`, `VisitOverrides`.

Два последних пункта показывают, что **документированная архитектура и реальная расходятся**. Страницы читают тексты через `SiteContentRepository`, минуя сервис, а доменные типы вроде `SiteContent` живут в пакете `service`, хотя нужны и репозиториям (зависимость «снизу вверх»). Ни то ни другое не катастрофа, но если не замечать, расхождений будет больше с каждым месяцем.

### Live coding: тест, который охраняет архитектуру

Проверку правил можно превратить в автоматический тест: «функция пригодности» (fitness function). Начнём с таблицы разрешённых зависимостей.

::::live Архитектурный тест из тридцати строк
Мы напишем ровно то, что делают библиотеки вроде ArchUnit, но на нашем уровне.

:::step Шаг 1. Правила словами
Для каждого слоя перечисляем слои, о которых он **может** знать.

```kotlin
val allowed = mapOf(
    "model" to setOf(),
    "repository" to setOf("model"),
    "service" to setOf("model", "repository", "i18n"),
    "views" to setOf("model", "service", "i18n"),
    "routes" to setOf("model", "service", "views", "i18n")
)
```

Это уже документация: любой новичок видит, как должно быть.
:::

:::step Шаг 2. Данные: файл и его импорты
В настоящем тесте импорты читаются из исходников регулярным выражением `import org.proanima.shelter.(\w+)`. Здесь для запуска в браузере возьмём выборку реальных файлов.

```kotlin
val imports = mapOf(
    "service/CatService.kt" to listOf("model.Cat", "repository.CatRepository"),
    "routes/CatRoutes.kt" to listOf("repository.SiteContentRepository", "service.CatService"),
    "repository/SiteContentRepository.kt" to listOf("service.ContentKey", "service.SiteContent")
)
```
:::

:::step Шаг 3. Нарушения
Слой файла — первая часть пути. Всё импортированное, что не в списке и не из того же слоя, нарушение.

```run
val allowed = mapOf(
    "model" to setOf(),
    "repository" to setOf("model"),
    "service" to setOf("model", "repository", "i18n"),
    "views" to setOf("model", "service", "i18n"),
    "routes" to setOf("model", "service", "views", "i18n")
)

val imports = mapOf(
    "service/CatService.kt" to listOf("model.Cat", "repository.CatRepository"),
    "views/CatViews.kt" to listOf("i18n.t", "model.Cat", "service.petSlug"),
    "routes/CatRoutes.kt" to listOf("model.AppLocale", "repository.SiteContentRepository", "repository.NoSiteContent", "service.CatService", "views.respondPage"),
    "repository/SiteContentRepository.kt" to listOf("service.ContentKey", "service.SiteContent"),
    "routes/ImageRoutes.kt" to listOf("repository.PhotoStore")
)

fun violations(): List<String> = imports.flatMap { (file, imported) ->
    val layer = file.substringBefore('/')
    imported.map { it.substringBefore('.') }.distinct()
        .filter { it != layer && it !in allowed.getValue(layer) }
        .map { "$file must not depend on $it" }
}

fun main() {
    violations().forEach(::println)
}
```

```output
routes/CatRoutes.kt must not depend on repository
repository/SiteContentRepository.kt must not depend on service
routes/ImageRoutes.kt must not depend on repository
```
:::

:::step Шаг 4. Что делать с найденным
У каждого нарушения три возможных судьбы, и выбор важнее самого теста.

1. **Исправить код.** Для `repository → service` разумно перенести `SiteContent` и `ContentKey` в `model/`: это данные, а не правило.
2. **Изменить правило.** Для `routes → repository` можно признать, что страницы читают тексты напрямую, и разрешить это для конкретного типа. Правило должно описывать *реальное желаемое*, а не идеал.
3. **Зафиксировать долг.** Если не успеваем, запомнить текущий список нарушений как «известные» и запретить **новые**. Тест тогда не краснеет сразу, но защищает от ухудшения.

Не следует оставлять нарушения «непринятыми»: тест, который постоянно красный, ничего не защищает.
:::

:::step Шаг 5. В настоящем проекте
В репозитории такого теста пока нет. Это хороший первый вклад: тест читает `src/main/kotlin`, строит таблицу импортов и сравнивает с `allowed`. Двадцать строк кода дают гарантию, которую иначе пришлось бы искать глазами при каждом ревью. См. задание ниже.
:::
::::

## Два режима, одна сложность

Платой за возможность запускать сайт и на файлах, и на базе стала повторяемая сборка: `moduleWithFiles` (около тридцати строк) и `moduleWithDatabase` (около шестидесяти), и `configureRoutes` с **десятью** параметрами:

```kotlin
fun Application.configureRoutes(
    catService: CatService,
    guideService: GuideService,
    clock: Clock = Clock.system(SHELTER_ZONE),
    dogService: DogService = DogService(JsonDogRepository()),
    visitOverrides: VisitOverrides = VisitOverrides(),
    photoStore: PhotoStore? = null,
    admin: AdminSetup? = null,
    siteContent: SiteContentRepository = admin?.content ?: NoSiteContent,
    sections: SectionRepository = admin?.sections ?: NoSections,
    menu: MenuRepository = admin?.menu ?: NoMenu
)
```

Значения по умолчанию спрятали сложность: тесты вызывают функцию с двумя аргументами. Но она вырастет дальше: каждая новая возможность добавляет параметр. Типичное лекарство называется *объект-параметр* (набор зависимостей в одном классе `Dependencies`), и вопрос лишь в том, когда он окупится. Пока почти все параметры имеют умолчания, проект оправданно ждёт.

## Решения как артефакт: ADR

Код отвечает на вопрос «как», но не «почему». Через полгода никто не вспомнит, почему выбрана PostgreSQL, а не SQLite. В проекте решения записаны в [docs/decisions.md](repo:docs/decisions.md), и этот файл ценнее многих комментариев. Формальная версия такой записи называется **ADR** (Architecture Decision Record). Минимальный шаблон:

```text
# ADR-007: PostgreSQL (Neon) instead of SQLite

Status: accepted (2026-11)

Context
  The admin panel must keep content edited while the site runs. The free hosting
  plan wipes its disk on every deploy, so a SQLite file would be wiped too.

Decision
  Cats, dogs and photos live in PostgreSQL (Neon, free plan) when DATABASE_URL is set.
  Files stay the default mode for local runs and tests. Same repository interfaces.

Consequences
  + Content survives a deploy; routes and services did not change.
  + Tests run on H2 in PostgreSQL mode: no server, no network.
  - The database sleeps after ~5 minutes: the pool and a 30 s cache are needed.
  - Two storage modes to keep working.

Considered
  SQLite (dropped: the disk is wiped). A paid plan with a disk was not needed: a free
  database outside the host is the cheapest thing that survives a deploy.
```

Обратите внимание на структуру: **контекст** (какие ограничения), **решение**, **последствия** (плюсы *и минусы*), **что ещё рассматривали**. Запись без минусов подозрительна: у любого решения есть цена. Хорошая запись не оправдывает выбор, а позволяет будущему читателю понять, при каких условиях он перестанет быть верным (например, «мы переедем на платный хостинг»).

В проекте есть и другие носители знаний: `docs/design.md` (дизайн-система), `docs/roadmap.md` (что сделано и что дальше), `docs/redesign/HANDOFF.md` и `docs/admin-panel/HANDOFF.md` (заметки передачи между рабочими сессиями), `docs/smoke-checklist.md`. Идея заметок передачи пригодится нам и в части про [ИИ-агентов](lesson:ai1-agents): агент, как и новый разработчик, каждый раз начинает «с нуля».

## Цена решений: честная таблица

Любое решение проекта можно оценить в двух столбцах.

| Решение | Что выиграли | Чем платим |
|---|---|---|
| Серверная вёрстка без SPA | быстро, без JS, просто | интерактив труднее |
| Документы в таблицах | нет миграций | нет запросов по полям |
| Два режима хранения | локально и в тестах без базы | дублирование сборки |
| `ThreadLocal` для меню страницы | не тащить меню через десятки функций | скрытая зависимость, работает только синхронно |
| Один общий пароль | просто для волонтёров | нет журнала «кто менял» |
| Кэш на 30 с | быстро, переживает сон базы | до 30 с устаревания |
| Один `main.css` | нет сборки | 1300 строк в одном файле |
| `AdminRoutes.kt` на 700 строк | один «пропускной пункт» | файл трудно читать |

Заметьте, что выбор «неправильным» назвать нельзя: для приюта с несколькими волонтёрами он разумен. Вопрос архитектора не «правильно ли», а «выдержит ли это то, что мы ожидаем в ближайший год, и как заметим, что перестало».

## Практика

::::task Допишите режим «демо»
В проекте есть `DevAdminServer`: сайт с базой в памяти для просмотра админки. Представьте третий режим в корне композиции. Если переменная `DEMO=true`, используется репозиторий в памяти с тремя тестовыми кошками. Допишите функцию `compose`, не меняя `CatService`.

```starter
interface CatRepository {
    fun findAll(): List<String>
}

class JsonCats : CatRepository {
    override fun findAll() = listOf("Felix (file)")
}

class PgCats : CatRepository {
    override fun findAll() = listOf("Felix (database)")
}

class CatService(private val repository: CatRepository) {
    fun names() = repository.findAll().map { it.uppercase() }
}

fun compose(env: Map<String, String>): CatService {
    // TODO: DEMO=true -> an in-memory repository with three cats; DATABASE_URL set -> PgCats; otherwise JsonCats
    TODO()
}

fun main() {
    println(compose(mapOf()).names())
    println(compose(mapOf("DATABASE_URL" to "postgresql://x")).names())
    println(compose(mapOf("DEMO" to "true", "DATABASE_URL" to "postgresql://x")).names())
}
```

:::hint
Допустим анонимный объект: `object : CatRepository { override fun findAll() = listOf(...) }`. Порядок проверок важен: демо-режим побеждает базу.
:::

:::solution
```run
interface CatRepository {
    fun findAll(): List<String>
}

class JsonCats : CatRepository {
    override fun findAll() = listOf("Felix (file)")
}

class PgCats : CatRepository {
    override fun findAll() = listOf("Felix (database)")
}

class CatService(private val repository: CatRepository) {
    fun names() = repository.findAll().map { it.uppercase() }
}

fun compose(env: Map<String, String>): CatService {
    val repository: CatRepository = when {
        env["DEMO"] == "true" -> object : CatRepository {
            override fun findAll() = listOf("Demo Felix", "Demo Mila", "Demo Fox")
        }
        !env["DATABASE_URL"].isNullOrBlank() -> PgCats()
        else -> JsonCats()
    }
    return CatService(repository)
}

fun main() {
    println(compose(mapOf()).names())
    println(compose(mapOf("DATABASE_URL" to "postgresql://x")).names())
    println(compose(mapOf("DEMO" to "true", "DATABASE_URL" to "postgresql://x")).names())
}
```

```output
[FELIX (FILE)]
[FELIX (DATABASE)]
[DEMO FELIX, DEMO MILA, DEMO FOX]
```

`CatService` не изменился ни на строчку, и это главная проверка качества границ: новая возможность добавляется в одном месте (корне композиции) и в новой реализации интерфейса. Окружение передано **параметром** (`env`), а не прочитано из `System.getenv` внутри функции: так её можно тестировать без изменения реальных переменных (как `adminConfigFromEnv(env: (String) -> String? = System::getenv)` в проекте).
:::
::::

::::task Охранный тест для настоящего проекта
Напишите тест (в вашей копии проекта), который читает все файлы из `src/main/kotlin/org/proanima/shelter/views` и проверяет, что ни один не импортирует `org.proanima.shelter.repository`. Используйте только стандартную библиотеку.

:::hint
`File("src/main/kotlin/org/proanima/shelter/views").walkTopDown().filter { it.extension == "kt" }` даёт файлы; затем `readLines().filter { it.startsWith("import org.proanima.shelter.repository") }`.
:::

:::solution
```kotlin check="test"
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ArchitectureTest {
    private val root = File("src/main/kotlin/org/proanima/shelter")

    private fun importsOf(layer: String): Map<String, List<String>> =
        File(root, layer).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { file ->
                file.name to file.readLines().filter { it.startsWith("import org.proanima.shelter.") }
            }

    @Test
    fun `views do not know about repositories`() {
        // In this check-only copy the folder may not exist; the real test runs inside the project.
        if (!File(root, "views").exists()) return

        val violations = importsOf("views").flatMap { (file, imports) ->
            imports.filter { it.startsWith("import org.proanima.shelter.repository") }.map { "$file: $it" }
        }
        assertTrue(violations.isEmpty(), "views must not import repository code:\n" + violations.joinToString("\n"))
    }
}
```

Обратите внимание на сообщение в `assertTrue`: при падении тест выведет **список нарушений**, а не просто «false». Тест, который говорит, что именно не так, чинят за минуту. Для правил «`model` не импортирует ничего» и «`service` не импортирует `views`» добавляются аналогичные методы с другим слоем.

(В этой версии для курса тест тихо выходит, если папки нет: это нужно только потому, что проверка кода курса запускается вне проекта. В самом проекте оставьте строгую проверку.)
:::
::::

::::task Решение для SiteContent: исправить или признать
Прочитайте найденные расхождения («`routes` читают `SiteContentRepository` напрямую» и «`repository` импортирует `SiteContent` из `service`»). Для каждого выберите: исправить, изменить правило или записать как долг. Обоснуйте в трёх предложениях и набросайте ADR из пяти строк (контекст, решение, последствия).

:::solution
Возможный ответ (допустимы и другие, если они аргументированы):

- **`SiteContent` и `ContentKey` → в `model/`.** Исправить. Это данные без поведения, нужные нескольким слоям, а их положение в `service` создаёт цикл «`repository` знает о `service`». Перенос механический (изменятся импорты), риск низкий, польза в чистой зависимости.
- **Маршруты читают `SiteContentRepository` напрямую.** Изменить правило и записать. Тексты страниц — это справочник, а не бизнес-логика; сервис-прокладка, которая лишь вызывает `load().bundle(locale)`, добавила бы файл без пользы (так называемый «пустой посредник»). Разрешаем `routes → repository` только для `SiteContentRepository` и `NoSiteContent`.

Набросок ADR: «Контекст: страницы получают тексты через `SiteContentRepository`; сервис был бы тонкой оберткой. Решение: допускаем прямой доступ маршрутов к этому репозиторию; тип `SiteContent` переносим в `model`. Последствия: (+) меньше кода; (−) правило слоёв имеет исключение, которое фиксирует архитектурный тест; при появлении логики вокруг текстов вводим сервис».
:::
::::

:::quiz Проверьте себя
Q: В чём суть инверсии зависимостей на примере `CatService`?
- [ ] Сервис вызывает базу данных напрямую, чтобы было быстрее.
- [x] Сервис зависит от интерфейса `CatRepository`; конкретные реализации (файл, база) подстраиваются под этот интерфейс.
- [ ] Репозиторий зависит от сервиса.
- [ ] Сервис не использует интерфейсы.
E: Высокоуровневый код определяет, что ему нужно (интерфейс), а детали хранения реализуют это. Тогда замена хранилища не затрагивает сервис.

Q: Что такое корень композиции в проекте?
- [ ] Класс `Cat`.
- [x] Единственное место (`Application.kt`), где решается, какие реализации подключить, и собираются зависимости.
- [ ] Корневая папка репозитория.
- [ ] Главная страница сайта.
E: Остальной код получает зависимости снаружи и не знает, как они устроены.

Q: Почему `nextScheduledVisit` хорошо тестируется?
- [ ] Она короткая.
- [x] Она чистая: время и данные приходят параметрами, ничего не читается и не пишется снаружи.
- [ ] Она не использует `null`.
- [ ] Она вызывается только из тестов.
E: Результат определяется аргументами; `Clock` в маршруте даёт «настоящее» время, а тест подставляет любое.

Q: Что такое «функция пригодности» (fitness function) в архитектуре?
- [ ] Функция, считающая возраст кошки.
- [x] Автоматическая проверка свойства архитектуры (например, слой `views` не импортирует `repository`), которая запускается вместе с тестами.
- [ ] Метрика производительности сервера.
- [ ] Паттерн из GoF.
E: Такой тест не даёт архитектуре гнить незаметно: нарушение сразу видно в CI.

Q: Что должно быть в хорошей записи архитектурного решения (ADR)?
- [x] Контекст и ограничения, в которых решение принималось.
- [x] Принятое решение и его последствия, включая минусы.
- [x] Что рассматривали и почему отвергли.
- [ ] Только вывод «так лучше».
E: ADR объясняет «почему» и условия, при которых решение нужно пересмотреть.

Q: Что показал подсчёт импортов в проекте?
- [ ] Архитектура идеальна.
- [x] Документированная и реальная архитектуры немного расходятся: маршруты читают репозиторий текстов напрямую, а репозитории импортируют типы из `service`.
- [ ] Слоёв в проекте нет.
- [ ] `model` зависит от `routes`.
E: Расхождения нормальны, главное замечать их и сознательно решать: исправить, принять или записать как долг.

Q: Почему тест нового режима («демо») не требует изменений в `CatService`?
- [ ] Потому что тестов нет.
- [x] Новая возможность сводится к ещё одной реализации интерфейса и строке в корне композиции.
- [ ] Потому что `CatService` умеет всё.
- [ ] Потому что Kotlin автоматически создаёт реализации.
E: Если добавление возможности требует правок в нескольких слоях, границы проведены неудачно.
:::

## Итоги

- Зависимости направляйте к стабильному центру. Интерфейс объявляет потребитель, реализации подстраиваются.
- Собирайте систему в одном месте (корень композиции), а остальной код оставляйте не знающим о выборе.
- Чистое ядро плюс тонкая оболочка: время, ввод и вывод передавайте снаружи.
- Проверяйте архитектуру тестами, а не верой в схему. Расхождения исправляйте, принимайте или фиксируйте как долг.
- Записывайте решения вместе с ценой.

Дальше: [Алгоритмы и структуры данных](lesson:a2-algorithms).
