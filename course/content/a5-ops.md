# Сборка, деплой и эксплуатация

Код, который не работает в продакшене, ничего не стоит, а продакшен у этого сайта скромный: бесплатный тариф хостинга с 512 МБ памяти на всё и бесплатная база, засыпающая через пять минут. Почти каждое решение, которое вы видели в предыдущих уроках (кэш на 30 секунд, пул без простаивающих соединений, ограничение обработки фото, сжатие только текста), выросло из этих ограничений. В этом уроке мы соберём картину целиком: как код превращается в работающий сайт и что делать, когда он перестал работать.

После урока вы сможете:

- прочитать `build.gradle.kts`, `Dockerfile`, workflow GitHub Actions и `render.yaml` и объяснить, что делает каждый;
- объяснить, как тесты стоят на страже деплоя в двух местах;
- настраивать приложение через переменные окружения и писать разбор строки подключения без утечки секретов;
- спланировать резервные копии, проверку живости и порядок действий при сбое.

## Gradle: сборка и зависимости

Файл [build.gradle.kts](repo:build.gradle.kts) описывает проект на Kotlin DSL (то есть сам написан на Kotlin):

```kotlin
plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:2.3.12")
    // ...
    testImplementation(kotlin("test"))
    // The database code is tested on an in-memory H2 in PostgreSQL mode, so tests need no server and no network.
    testImplementation("com.h2database:h2:2.3.232")
}

application {
    mainClass.set("org.proanima.shelter.ApplicationKt")
}
```

Что здесь важно:

- **Версии зафиксированы точно** (`2.3.12`, а не «последняя»): сборка сегодня и через год даёт один и тот же результат. Единственная цена — обновлять надо сознательно.
- **`implementation` и `testImplementation`**: что нужно приложению и что нужно только тестам (H2 в рабочий образ не попадает).
- **Gradle Wrapper** (`gradlew`) скачивает нужную версию Gradle сам: не требуется ничего ставить, и у всех одинаковая сборка.
- **Своя задача `devAdmin`:** запуск сайта с админкой на базе в памяти. Её особенность в параметрах JVM:

```kotlin
tasks.register<JavaExec>("devAdmin") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.proanima.shelter.DevAdminServerKt")
    // The same limits as on the hosting plan (render.yaml), so that memory problems show up here and not there.
    jvmArgs("-Xmx256m", "-Xss256k", "-XX:MaxMetaspaceSize=128m")
    environment("PORT", "8095")
    environment("ADMIN_PASSWORD", "dev-password-123")
    ...
}
```

Комментарий выражает важный принцип: **локальная среда должна повторять ограничения продакшена**, иначе «у меня работает» не значит ничего. Хостинг даёт 256 МБ кучи, значит, разработка тоже идёт на 256 МБ.

## Docker: упаковка и первый страж

[Dockerfile](repo:Dockerfile) делится на два этапа:

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
# The tests read the real content files, so the build stage needs them too.
COPY data ./data
COPY content ./content
# A failing test fails the build, so a broken commit never reaches Render.
RUN chmod +x gradlew && ./gradlew --no-daemon test installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/install/shelter-volunteer ./
COPY data ./data
COPY content ./content

# The app only reads its files, so it does not need root.
RUN useradd --system --no-create-home --shell /usr/sbin/nologin app
USER app

EXPOSE 8080
ENTRYPOINT ["./bin/shelter-volunteer"]
```

| Решение | Зачем |
|---|---|
| Два этапа (`build` и итоговый) | В итоговом образе только JRE и готовое приложение, без JDK, Gradle и исходников: он меньше и безопаснее |
| `test` в сборке | **Упавший тест останавливает сборку**, и новая версия не заменит рабочую |
| Копирование `data/` и `content/` в образ | Контент поставляется вместе с кодом; в файловом режиме запись на диск не нужна |
| `USER app` | Процесс без прав root: в случае уязвимости у атакующего меньше возможностей |
| Копирование `build.gradle.kts` до `src` | Слой с зависимостями можно было бы кэшировать (честная оговорка: проект запускает `gradlew test` уже после копирования `src`, поэтому кэш зависимостей используется не полностью; это возможная оптимизация времени сборки) |

Заметьте простую, но важную вещь: тестам для работы нужны **реальные файлы контента** (`data/`, `content/`), поэтому они копируются и на этапе сборки. Тест, который читает настоящие данные, это защита от опечатки в JSON: ошибочный файл не пройдёт сборку.

## GitHub Actions: второй страж

```yaml
name: Tests

on:
  push:
    branches: [main]
  pull_request:

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      - run: chmod +x gradlew
      - run: ./gradlew test --no-daemon
```

Зачем тесты в **двух** местах: Docker-сборка защищает **продакшен** (сломанный коммит не развернётся), а workflow даёт **быструю обратную связь на pull request**: автор видит красный крест до слияния, а не после. Страж в Docker остаётся последним рубежом, если кто-то слил, не дождавшись проверки.

Версии действий (`@v4`) закреплены: это та же идея, что и фиксированные версии библиотек.

## Конфигурация через окружение

Принцип «двенадцати факторов»: конфигурация живёт **в переменных окружения**, а не в коде и не в файлах репозитория. Один и тот же образ работает на ноутбуке, в тестах и в продакшене, отличается только окружение.

| Переменная | Смысл | Нет значения |
|---|---|---|
| `PORT` | порт, на котором слушать | 8080 |
| `DATABASE_URL` | строка подключения к PostgreSQL | режим файлов, админки нет |
| `ADMIN_PASSWORD`, `SESSION_SECRET` | включают админку | админка выключена |
| `PUBLIC_BASE_URL` | публичный адрес для `canonical` и Open Graph | адрес на `onrender.com` |
| `JAVA_OPTS` | параметры JVM (лимиты памяти) | значения по умолчанию JVM |
| `ADMIN_COOKIE_SECURE` | `false` для локального запуска без HTTPS | `true` |

В `render.yaml` секреты описаны как `sync: false`: значение **вводится в панели хостинга и никогда не попадает в репозиторий**, а сам файл лишь объявляет, что такая переменная существует.

### Разбор строки подключения

Хостинги баз данных выдают строку вида `postgresql://user:password@host/db?sslmode=require`, а JDBC-драйвер хочет логин и пароль отдельно от адреса. Функция `parseDatabaseUrl` ([Database.kt](repo:src/main/kotlin/org/proanima/shelter/repository/Database.kt)) делает перевод. Это хороший пример функции на границе системы: ввод ненадёжный (человек скопировал строку из панели, возможно, с кавычками), а ошибки не должны раскрывать пароль. Напишем её так, как это делают на практике: от теста.

::::live Разбор DATABASE_URL по шагам
Каждый шаг добавляет одно требование. Последний шаг содержит полную версию с тестами, которые можно запустить.

:::step Шаг 1. Что хотим получить
Хостинг выдаёт адрес с паролем, JDBC хочет отдельный адрес, логин и пароль. Это и есть первый тест.

```kotlin
val config = parseDatabaseUrl("postgresql://alice:pw@db.example.com/shelter?sslmode=require")
assertEquals("jdbc:postgresql://db.example.com:5432/shelter?sslmode=require", config.jdbcUrl)
assertEquals("alice", config.username)
assertEquals("pw", config.password)
```
:::

:::step Шаг 2. Минимальный код
`java.net.URI` уже умеет разбирать адрес на части; нам остаётся собрать JDBC-строку и подставить порт по умолчанию.

```kotlin
val uri = URI(url)
val port = if (uri.port == -1) 5432 else uri.port
val jdbcUrl = "jdbc:postgresql://${uri.host}:$port${uri.path}" + (uri.rawQuery?.let { "?$it" } ?: "")
```
:::

:::step Шаг 3. Реальный ввод грязный
Пароль может содержать `@` (в адресе он записан как `%40`) и `+`; значение скопировано из файла `.env` вместе с кавычками; у Neon в запросе есть параметр, который драйверу не нужен. Каждый пункт это новый тест, затем новая строка кода: `percentDecode`, `removeSurrounding`, фильтр параметров.
:::

:::step Шаг 4. Ошибки не должны раскрывать пароль
Тест подаёт сломанные адреса с паролем `topsecret` и проверяет, что в сообщении этого слова нет. Код опирается на `require` с **фиксированными** текстами и на `e.reason`, а не на саму строку.
:::

:::step Шаг 5. Полная версия и тесты
Запустите. Измените, например, `removePrefix("/")` или фильтр `channel_binding` и посмотрите, какой тест укажет на ошибку.

```test
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DatabaseConfig(val jdbcUrl: String, val username: String?, val password: String?)

private const val DEFAULT_POSTGRES_PORT = 5432

fun parseDatabaseUrl(url: String): DatabaseConfig {
    if (url.startsWith("jdbc:")) {
        return DatabaseConfig(url, null, null)
    }
    // A value copied from a .env file often keeps its quotes: DATABASE_URL="postgresql://...".
    val uri = try {
        URI(url.trim().removeSurrounding("\"").removeSurrounding("'"))
    } catch (e: URISyntaxException) {
        throw IllegalArgumentException("DATABASE_URL is not a valid URL (${e.reason})")
    }
    require(uri.scheme == "postgresql" || uri.scheme == "postgres") {
        "DATABASE_URL must start with postgresql://"
    }
    val host = uri.host
    require(!host.isNullOrBlank()) { "DATABASE_URL has no host" }
    val database = uri.rawPath.orEmpty().removePrefix("/")
    require(database.isNotBlank()) { "DATABASE_URL has no database name" }

    val userInfo = uri.rawUserInfo
    val username = userInfo?.substringBefore(':')?.let(::percentDecode)?.ifBlank { null }
    val password = userInfo?.takeIf { ':' in it }?.substringAfter(':')?.let(::percentDecode)

    // pgjdbc has no use for channel_binding; everything else (sslmode, ...) goes through unchanged.
    val query = uri.rawQuery.orEmpty().split('&').filter { it.isNotBlank() && !it.startsWith("channel_binding=") }
    val port = if (uri.port == -1) DEFAULT_POSTGRES_PORT else uri.port
    val jdbcUrl = "jdbc:postgresql://$host:$port/$database" + if (query.isEmpty()) "" else "?" + query.joinToString("&")
    return DatabaseConfig(jdbcUrl, username, password)
}

// A plus sign in a password is a plus sign, not a space, so only %XX sequences are decoded.
private fun percentDecode(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8)

class DatabaseUrlTest {
    @Test
    fun `a Neon style address is split into jdbc url, user and password`() {
        val config = parseDatabaseUrl(
            "postgresql://alice:s3cr%40t@ep-cool-123.eu-central-1.aws.neon.tech/shelter?sslmode=require&channel_binding=require"
        )

        assertEquals("jdbc:postgresql://ep-cool-123.eu-central-1.aws.neon.tech:5432/shelter?sslmode=require", config.jdbcUrl)
        assertEquals("alice", config.username)
        assertEquals("s3cr@t", config.password)
    }

    @Test
    fun `a jdbc address is passed through for tests`() {
        val config = parseDatabaseUrl("jdbc:h2:mem:test;MODE=PostgreSQL")

        assertEquals("jdbc:h2:mem:test;MODE=PostgreSQL", config.jdbcUrl)
    }

    @Test
    fun `quotes copied from a dotenv file are removed`() {
        assertEquals("alice", parseDatabaseUrl("\"postgresql://alice:pw@db.example.com/shelter\"").username)
    }

    @Test
    fun `a plus sign in a password stays a plus sign`() {
        assertEquals("pa+ss", parseDatabaseUrl("postgresql://alice:pa+ss@db.example.com/shelter").password)
    }

    @Test
    fun `error messages never contain the password`() {
        val broken = listOf(
            "postgresql://alice:topsecret@/shelter",
            "postgresql://alice:top secret@db.example.com/shelter",
            "mysql://alice:topsecret@db.example.com/shelter"
        )
        broken.forEach { url ->
            val error = assertFailsWith<IllegalArgumentException> { parseDatabaseUrl(url) }
            assertFalse("secret" in error.message.orEmpty(), error.message)
        }
    }
}
```
:::
::::

Три свойства, ради которых стоит перечитать функцию. **Терпимость к вводу:** кавычки, `postgres://` и `postgresql://`, порт по умолчанию. **Правильное декодирование:** `%40` становится `@`, а `+` остаётся плюсом (в пароле его легко потерять). **Безопасные ошибки:** в сообщения идёт только `e.reason` и фиксированные тексты, никогда сама строка, поэтому в журнале Render пароля не окажется. Последнее проверяет отдельный тест: свойство безопасности, записанное кодом.

## Запуск, живость и логи

- **Проверка живости.** Render опрашивает `/health` (`healthCheckPath` в `render.yaml`), и маршрут отвечает `OK`. Он не ходит в базу: проверка «сервер жив» не должна зависеть от того, проснулась ли база, иначе платформа будет перезапускать здоровое приложение. (Проверка *готовности* к работе, включающая зависимости, отдельная задача.)
- **Метод HEAD.** Мониторинги аптайма и предпросмотр ссылок используют `HEAD`; в проекте для этого включён `AutoHeadResponse` (мы разбирали это в уроке про [HTTP](lesson:w1-http-ktor)).
- **Засыпание.** Бесплатный сервис останавливается после 15 минут без запросов и просыпается около минуты. Для сайта волонтёров это приемлемо, но первый запрос после простоя медленный: так что приложение должно стартовать **быстро** (по возможности без тяжёлой работы при запуске) и не падать, пока база просыпается (см. `initializationFailTimeout = 30_000` в пуле соединений).
- **Логи.** [logback.xml](repo:src/main/resources/logback.xml) задаёт уровень `INFO`: «Without this file logback defaults to DEBUG and floods the log with Netty internals». Логируется то, что помогает разбираться: `warn` при недоступности базы и устаревшей копии, `error` с адресом запроса для необработанного исключения, информация о резервной копии. Не логируется: пароли, токены, строки подключения, персональные данные.

## Резервные копии и восстановление

Бесплатная база хранит историю недолго, поэтому в админке есть кнопка **резервной копии**: один zip с `cats.json`, `dogs.json`, `site-content.json`, `sections.json`, `menu.json` и фотографиями в **том же формате, что и файловый режим**. Из этого вытекает хорошее свойство: восстановление это распаковка архива в `data/` и запуск на пустой базе (первая загрузка засевает её из `data/`). Формат резервной копии совпадает с исходным форматом данных, и отдельной «процедуры восстановления» придумывать не нужно.

Две простые величины описывают любую стратегию резервного копирования: **RPO** (сколько работы мы готовы потерять: здесь всё сделанное после последнего скачивания копии) и **RTO** (за сколько восстановимся: минуты на распаковку и деплой). Решение проекта: копию делают вручную, волонтёрам напоминают сохранять её. Это дёшево, но держится на людях. Улучшением была бы регулярная автоматическая копия в стороннее хранилище.

## Релиз и откат

1. Изменение попадает в `main` (через pull request, где работает workflow).
2. Render собирает образ; **тесты внутри сборки**.
3. Если сборка успешна, новая версия заменяет старую, иначе остаётся прежняя (`failed deploy leaves the previous version running`).
4. После выката: `docs/smoke-checklist.md`: открыть страницы на трёх языках, проверить тему, фото, форму.
5. Откат: повторное развёртывание предыдущего коммита в панели Render или `git revert` и пуш.

Для миграций данных этого достаточно именно потому, что **миграций нет** (добавление полей с умолчаниями). Как только понадобятся настоящие миграции схемы (таблицы участников, см. урок про [данные](lesson:k6-data)), в процесс добавится новый шаг: версионированные скрипты (Flyway или Liquibase) и правило «сначала совместимая схема, потом код».

## Ограничения как источник решений

| Ограничение платформы | Решение в проекте |
|---|---|
| Диск стирается при деплое | База в Neon; файловый режим только для чтения |
| 512 МБ памяти | `-Xmx256m`, фото по одному, лимит пикселей, сжатие только текста |
| 0,1 CPU | кэш списков на 30 секунд, перекодирование фото в `Dispatchers.Default` под `Mutex` |
| База засыпает через 5 минут | пул без простаивающих соединений, `maxLifetime` 240 с, кэш со старой копией |
| Сервис засыпает через 15 минут | быстрый старт, терпеливый пул |
| 5 ГБ трафика в месяц | карточные копии фото, `loading="lazy"`, gzip для текста, кэш заголовков |
| Бесплатный план | без внешних сервисов, без постоянных «пингов», держащих всё бодрым |

## Практика

::::task Прочитайте и объясните workflow
Дан workflow деплоя курса на GitHub Pages. Объясните, зачем нужны блоки `permissions`, `concurrency` и два задания (`check` и `deploy`).

```yaml
name: Course

on:
  push:
    branches: [main]
    paths: ["course/**"]
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: pages
  cancel-in-progress: false

jobs:
  check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 22
      - run: node tools/course-check.mjs

  deploy:
    needs: check
    runs-on: ubuntu-latest
    environment:
      name: github-pages
    steps:
      - uses: actions/checkout@v4
      - uses: actions/upload-pages-artifact@v3
        with:
          path: course
      - uses: actions/deploy-pages@v4
```

:::solution
- **`permissions`**: принцип наименьших привилегий. Задание получает только то, что нужно: читать код, писать в Pages и получать токен для развёртывания (`id-token`). Без явного блока права по умолчанию шире.
- **`concurrency` с `cancel-in-progress: false`**: два деплоя не идут одновременно; второй ждёт первого и **не отменяет** его, чтобы недоделанный выкат не оставил сайт в промежуточном состоянии.
- **Два задания**: `check` проверяет курс (разметка, ссылки, квизы), `deploy` зависит от него (`needs: check`). Сломанный курс не публикуется: та же идея, что тесты в Docker-сборке.
- **`paths: ["course/**"]`**: запускать только при изменении курса, а не при каждом коммите.
- **`workflow_dispatch`**: ручной запуск кнопкой.
:::
::::

::::task Что сломается
Для каждого сценария скажите, что произойдёт с сайтом и что бы вы изменили: (а) в `ADMIN_PASSWORD` задано 8 знаков; (б) в `DATABASE_URL` пароль содержит `@`; (в) тест упал на коммите, влитом в `main`; (г) за ночь никто не заходил на сайт, и утром открывает волонтёр.

:::solution
- (а) Приложение **не запустится** с сообщением `ADMIN_PASSWORD is too short: use at least 12 characters`; в Render новая версия не заменит рабочую (проверка при старте падает до начала обслуживания; предыдущий деплой остаётся). Исправление: пароль не короче 12 знаков.
- (б) Если знак закодирован как `%40`, разбор работает (тест выше). Если написан как есть, `URI` либо разберёт неверно, либо выдаст `not a valid URL`. Исправление: кодировать спецсимволы в пароле; сообщение об ошибке не раскроет пароль.
- (в) Сборка Docker упадёт на шаге `test`, новая версия **не выкатится**, работает прежняя; в GitHub видна красная отметка коммита. Исправление: поправить код или откатить коммит.
- (г) Сервис «заснул»: первый запрос ждёт около минуты, пока он проснётся, плюс несколько секунд, пока проснётся база. Волонтёр увидит долгую загрузку. Исправление на уровне продукта: пояснить на сайте или использовать платный тариф; хакать «пингами» не стоит, это тратит бесплатные часы.
:::
::::

::::task Составьте чек-лист релиза
Составьте для сайта чек-лист из шести-восьми пунктов, которые вы проверяете после выката. Для каждого укажите, **можно ли автоматизировать** проверку.

:::solution
Пример:

1. `/health` отвечает `200`: автоматизируется (мониторинг).
2. Главная страница открывается на `ru`, `en`, `sr` без ошибок: автоматизируется (`DatabaseModeTest` и HTTP-тесты).
3. Страница кошки с фото загружается, картинки отдаются со статусом `200`: автоматизируется.
4. Переключение языка и темы работает: частично (тесты разметки), финально вручную.
5. Вход в админку и сохранение тестовой записи (и удаление её): автоматизируется на тестовом окружении, на продакшене вручную.
6. Нет горизонтального скролла на телефоне (375 px), нет «прыжков» вёрстки: вручную или скриншоты Playwright.
7. Резервная копия скачивается и распаковывается: вручную, раз в месяц.
8. Журнал без `ERROR` после выката: вручную.

Идея: автоматизировать всё, что можно выразить условием «да/нет», а людям оставить то, где нужен глаз (вид и удобство).
:::
::::

:::quiz Проверьте себя
Q: Почему в `Dockerfile` команда `./gradlew test installDist` выполняется на этапе сборки?
- [ ] Чтобы образ получился меньше.
- [x] Упавший тест останавливает сборку, и сломанная версия не заменит рабочую.
- [ ] Чтобы ускорить запуск.
- [ ] Потому что Render так требует.
E: Тесты в сборке последний рубеж перед продакшеном. Предыдущая версия продолжает работать.

Q: Зачем нужен многоэтапный Docker-образ?
- [ ] Чтобы запускать два приложения.
- [x] В итоговый образ попадает только JRE и готовое приложение, без JDK, Gradle и исходников: меньше размер и меньше поверхность атаки.
- [ ] Чтобы тесты шли быстрее.
- [ ] Это требование Kotlin.
E: Сборочные инструменты нужны только на этапе сборки.

Q: Почему секреты задаются в панели хостинга (`sync: false`), а не в `render.yaml`?
- [ ] Файл `render.yaml` не поддерживает переменные.
- [x] `render.yaml` лежит в репозитории и виден всем с доступом к коду; секрет в нём стал бы публичным.
- [ ] Так быстрее.
- [ ] В панели секреты шифруются лучше всех.
E: Файл объявляет, что переменная существует; значение вводится отдельно и не коммитится.

Q: Почему `/health` не обращается к базе данных?
- [ ] Это ошибка.
- [x] Проверка «процесс жив» не должна зависеть от внешних сервисов: иначе платформа перезапускала бы здоровое приложение, пока спящая база просыпается.
- [ ] База не умеет отвечать на запросы.
- [ ] Так проще тестировать.
E: Разделяют проверку живости (процесс отвечает) и готовности (зависимости доступны).

Q: Что такое RPO в стратегии резервного копирования?
- [ ] Время восстановления.
- [x] Сколько работы мы готовы потерять: промежуток между последней копией и сбоем.
- [ ] Размер архива.
- [ ] Число копий.
E: RTO — время восстановления, RPO — допустимая потеря данных. Для ручной копии RPO равен времени с момента последнего скачивания.

Q: Выберите решения проекта, которые прямо вытекают из ограничения «512 МБ памяти».
- [x] Параметр JVM `-Xmx256m` и такие же лимиты в локальной задаче `devAdmin`.
- [x] Обработка загруженных фото по одному и ограничение числа пикселей.
- [ ] Хранение данных в PostgreSQL.
- [ ] Три языка сайта.
E: Хранение в базе вызвано стиранием диска, а языки продуктовой потребностью. Память ограничила именно обработку изображений и размер кучи.

Q: Почему тесты запускаются и в Docker-сборке, и в GitHub Actions?
- [ ] Это ошибка дублирования.
- [x] Actions даёт быструю обратную связь на pull request до слияния; сборка Docker защищает продакшен, даже если кто-то слил без проверки.
- [ ] Docker запускает тесты быстрее.
- [ ] Actions нужны только для красивого значка.
E: Два рубежа решают две задачи: раннюю обратную связь и гарантию деплоя.
:::

## Итоги

- Сборка воспроизводима: точные версии, wrapper, Docker в два этапа.
- Тесты стоят на страже дважды: в CI для скорости обратной связи, в Docker-сборке для защиты продакшена.
- Конфигурация живёт в окружении; ошибки конфигурации громкие, но без секретов.
- Бесплатные ограничения платформы вели архитектурные решения: проектируйте под реальный продакшен, а не абстрактный.
- Продумайте резервную копию, живость и откат заранее.

Часть про архитектуру и качество пройдена. Дальше: [UX: принципы, информационная архитектура, тексты](lesson:u1-ux).
