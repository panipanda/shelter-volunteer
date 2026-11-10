# HTTP и Ktor

Сайт — это разговор по протоколу HTTP: браузер задаёт вопрос (запрос), сервер отвечает (ответ). Всё остальное (HTML, CSS, JavaScript, изображения) едет внутри этого разговора. Если вы понимаете запросы, статусы и заголовки, то понимаете 80% того, что происходит в веб-приложении и почему оно иногда ломается.

После урока вы сможете:

- читать запрос и ответ HTTP и выбирать правильные статусы: 200, 303, 304, 404, 429;
- объяснить, как работают заголовки кэширования и почему адрес стилей содержит «отпечаток»;
- понимать устройство Ktor: плагины, маршруты, функции-маршруты, страницы ошибок;
- писать тесты сервера, которые не поднимают сеть.

## Запрос и ответ

Посмотрим на настоящий сайт командой `curl` (флаг `-I` запрашивает только заголовки):

```bash
curl -I https://shelter-volunteer.onrender.com/styles/main.css
```

```text
HTTP/1.1 200 OK
Content-Type: text/css; charset=UTF-8
Cache-Control: max-age=3600
Content-Encoding: gzip
```

(Показаны только строки, о которых пойдёт речь; хостинг добавляет и свои заголовки.) Ответ состоит из трёх частей:

1. **Строка состояния**: версия, код (`200`), пояснение (`OK`).
2. **Заголовки**: пары «имя: значение», метаданные о теле. Они говорят браузеру тип содержимого, как долго его хранить, как оно сжато.
3. **Тело**: сам файл или страница.

Запрос выглядит так же: **метод** (`GET`, `POST`, `HEAD`), **путь**, заголовки, при `POST` тело с данными формы.

### Методы

| Метод | Смысл | Безопасен? |
|---|---|---|
| `GET` | «дай страницу» | да: не должен ничего менять |
| `HEAD` | «дай только заголовки такой страницы» | да |
| `POST` | «прими данные» (форма) | нет: меняет состояние |

Отсюда правило, на которое опирается весь проект: **всё, что меняет данные, делается через `POST`** (форма в админке), а страницы-«просмотры» только через `GET`. Поэтому ссылка не может случайно удалить кошку, а поисковый робот, переходя по ссылкам, ничего не ломает.

### Статусы: язык, на котором сервер говорит о результате

| Группа | Что значит | Что есть на сайте |
|---|---|---|
| `2xx` | успех | `200 OK` для страницы |
| `3xx` | «иди в другое место» | `/` → `302` на `/ru`; после формы `303` |
| `4xx` | вина клиента | `404` нет страницы; `403` неверный пароль; `413` слишком большой запрос; `429` слишком много попыток |
| `5xx` | вина сервера | `500` необработанная ошибка |

Проверим на живом сайте (результат на момент написания курса):

```bash
curl -s -o /dev/null -w "%{http_code} %{redirect_url}\n" https://shelter-volunteer.onrender.com/
# 302 https://shelter-volunteer.onrender.com/ru
curl -s -o /dev/null -w "%{http_code}\n" https://shelter-volunteer.onrender.com/ru/cats/nobody
# 404
curl -s -o /dev/null -w "%{http_code}\n" https://shelter-volunteer.onrender.com/admin
# 303  (to the login page)
```

:::note Почему статус важен, а не просто «страница с текстом»
Если для несуществующей кошки сервер ответит `200` с текстом «не найдено», поисковая система проиндексирует пустышку, а мониторинг не заметит проблему. Страница ошибки в проекте красивая и на нужном языке, но статус у неё честный: `404`. Такую ошибку называют *soft 404*.
:::

## Кэширование: самое сильное ускорение за ноль строк кода

Браузер может сохранить ответ и в следующий раз не спрашивать сервер. Для этого сервер присылает `Cache-Control: max-age=3600` («можешь хранить час»). В проекте это настроено в [Application.kt](repo:src/main/kotlin/org/proanima/shelter/Application.kt) плагином `CachingHeaders`:

```kotlin
install(CachingHeaders) {
    options { _, content ->
        when (content.contentType?.withoutParameters()?.contentType) {
            "image", "font" -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = ONE_DAY_SECONDS))
            else -> when (content.contentType?.withoutParameters()) {
                ContentType.Text.CSS, ContentType.Application.JavaScript ->
                    CachingOptions(CacheControl.MaxAge(maxAgeSeconds = ONE_HOUR_SECONDS))
                else -> null
            }
        }
    }
}
```

Картинки и шрифты хранятся сутки, стили и скрипты час, HTML-страницы не кэшируются (`null`: заголовка нет). Комментарий в коде объясняет, почему время небольшое: «Files are served under fixed names, so a replaced photo or stylesheet shows up for visitors only after the cache expires».

### Отпечаток в адресе

Проблема: поменяли стили, задеплоили, а браузер час берёт старый файл. Новая вёрстка, старые стили: страница выглядит сломанной. Решение в [StaticAssets.kt](repo:src/main/kotlin/org/proanima/shelter/views/StaticAssets.kt): к адресу добавляется короткий отпечаток содержимого, `/styles/main.css?v=1a2b3c4d`. Изменился файл, значит, изменился адрес, и браузер загружает его сразу. Не изменился — остаётся в кэше.

```run
import java.security.MessageDigest

fun fingerprint(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).take(4).joinToString("") { "%02x".format(it) }

fun versioned(path: String, content: String) = "$path?v=${fingerprint(content.toByteArray())}"

fun main() {
    println(versioned("/styles/main.css", "body { color: black }"))
    println(versioned("/styles/main.css", "body { color: black }"))
    println(versioned("/styles/main.css", "body { color: #111 }"))
}
```

```output
/styles/main.css?v=f674c2ff
/styles/main.css?v=f674c2ff
/styles/main.css?v=7ef9a234
```

Одинаковое содержимое даёт одинаковый адрес, малейшее изменение даёт другой. Такой адрес можно было бы кэшировать на год: он «вечный», потому что новая версия получает новое имя. Проект остаётся на часе из осторожности: единый механизм для всех файлов проще.

### ETag и ответ 304

Для фотографий из базы проект использует ещё один механизм, [ImageRoutes.kt](repo:src/main/kotlin/org/proanima/shelter/routes/ImageRoutes.kt): **ETag**. Сервер присылает хэш содержимого фото как «метку версии». Когда кэш браузера истёк, он спрашивает: «у меня версия `abc`, она ещё актуальна?» (заголовок `If-None-Match`). Если да, сервер отвечает коротким `304 Not Modified` без тела: не нужно пересылать мегабайты.

```run
fun etagMatches(header: String?, sha256: String): Boolean =
    header != null && header.split(',').map { it.trim().removePrefix("W/").trim('"') }.any { it == sha256 || it == "*" }

fun main() {
    println(etagMatches("\"abc\"", "abc"))
    println(etagMatches("W/\"abc\", \"def\"", "def"))
    println(etagMatches("*", "zzz"))
    println(etagMatches("\"abc\"", "xyz"))
    println(etagMatches(null, "abc"))
}
```

```output
true
true
true
false
false
```

Это чистая функция: заголовок и хэш на входе, `Boolean` на выходе. Её легко проверить без сервера (так её и тестируют). Заголовок может содержать несколько меток, «слабые» метки (`W/`) и звёздочку: все эти формы учтены.

### Сжатие

Плагин `Compression` сжимает ответы в gzip, но **только текст**: HTML, CSS, JavaScript. Фотографии и шрифты уже сжаты, повторное сжатие тратит процессор впустую. В коде: `matchContentType(ContentType.Text.Any, ContentType.Application.JavaScript)`.

## Ktor: как устроен сервер

Приложение стартует в `main`:

```kotlin
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(factory = Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}
```

Порт берётся из переменной окружения `PORT`: так хостинг сообщает приложению, куда слушать. Если её нет, используется 8080. Это принцип «конфигурация через окружение»; подробнее в уроке про [эксплуатацию](lesson:a5-ops).

`Application.module()` настраивает приложение, а `configureRoutes` описывает, что установлено и какие адреса есть. Структура такая:

1. **Плагины** (`install(...)`) оборачивают обработку каждого запроса: сжатие, заголовки кэша, обработка ошибок, сессии.
2. **Маршрутизация** (`routing { ... }`) связывает адрес и обработчик.
3. **Обработчик** получает `call` (запрос и ответ в одном объекте), читает параметры, вызывает сервисы и отвечает.

### Маршруты

```kotlin
routing {
    get("/") { call.respondRedirect("/${AppLocale.default.code}") }

    healthRoutes()
    staticResources("/styles", "static/styles")

    AppLocale.entries.forEach { locale ->
        route("/${locale.code}") {
            homeRoutes(catService, locale, clock, visitOverrides, siteContent)
            catRoutes(catService, locale, siteContent)
            // ... the other pages
        }
    }
}
```

Два решения стоит заметить:

- **Маршруты описаны функциями-расширениями** `Route.catRoutes(...)`, по одной на область сайта. Файл `Application.kt` остаётся оглавлением, а не свалкой на тысячу строк.
- **Языки — это цикл.** Одни и те же функции подключаются под `/ru`, `/en` и `/sr`, и каждая получает свой `locale`. Страницы не знают, под какой язык их монтируют.

Параметры пути пишутся в фигурных скобках: `get("/cats/{slug}")`, читаются `call.parameters["slug"]`. Плагин `IgnoreTrailingSlash` делает `/ru/cats/` и `/ru/cats` одним адресом.

Проверим эти идеи на минимальном, но настоящем коде. Он компилируется и проходит тест на тех же версиях Ktor, что и проект.

```kotlin check="test"
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

private val cats = mapOf("felix" to "Felix", "mila" to "Mila")

fun Route.catRoutes(lang: String) {
    get("/cats/{slug}") {
        val name = cats[call.parameters["slug"]!!]
        if (name == null) call.respond(HttpStatusCode.NotFound) else call.respondText("[$lang] Cat: $name")
    }
}

fun Application.demoModule() {
    routing {
        listOf("ru", "en").forEach { lang -> route("/$lang") { catRoutes(lang) } }
    }
}

class DemoRoutesTest {
    @Test
    fun `known cat, unknown cat and a second language`() = testApplication {
        application { demoModule() }

        assertEquals("[ru] Cat: Felix", client.get("/ru/cats/felix").bodyAsText())
        assertEquals("[en] Cat: Mila", client.get("/en/cats/mila").bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.get("/ru/cats/nobody").status)
    }
}
```

Обратите внимание: `testApplication` запускает приложение **в памяти**, без сети и портов. Тест выполняется за миллисекунды, а в проекте таких тестов сотни.

### Маршрут не рисует ошибку, он говорит «нет»

В маршруте кошки:

```kotlin
get("/cats/{slug}") {
    val slug = call.parameters["slug"]!!
    val cat = catService.getCatBySlug(slug)

    if (cat == null) {
        call.respond(HttpStatusCode.NotFound)
    } else {
        // ... draw the page
    }
}
```

Маршрут отвечает только статусом `404`. Красивую страницу рисует плагин `StatusPages` в [ErrorPages.kt](repo:src/main/kotlin/org/proanima/shelter/ErrorPages.kt), **один раз для всего сайта**, на языке из префикса адреса. Так ни один маршрут не думает про оформление ошибок.

```kotlin check="test"
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorPagesDemoTest {
    @Test
    fun `404 and 500 are drawn in one place`() = testApplication {
        application {
            install(StatusPages) {
                status(HttpStatusCode.NotFound) { call, _ ->
                    call.respondText("Page not found", status = HttpStatusCode.NotFound)
                }
                exception<Throwable> { call, _ ->
                    call.respondText("Something went wrong", status = HttpStatusCode.InternalServerError)
                }
            }
            routing {
                get("/boom") { error("broken") }
            }
        }

        val missing = client.get("/nope")
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals("Page not found", missing.bodyAsText())

        val broken = client.get("/boom")
        assertEquals(HttpStatusCode.InternalServerError, broken.status)
        assertEquals("Something went wrong", broken.bodyAsText())
    }
}
```

### Redirect после POST

Когда форма в админке успешно отправлена, сервер отвечает не страницей, а `303 See Other` на другую страницу. Браузер переходит туда `GET`-ом. Если бы сервер вернул страницу прямо на `POST`, то обновление страницы (`F5`) повторило бы отправку формы и могло создать вторую кошку. Схема называется *Post/Redirect/Get* и разобрана подробнее в уроке про [формы](lesson:w2-html-forms).

## Live coding: маршрут через тест

Напишем проверку живости `/health`, на которую опирается Render, начиная с теста.

::::live Health и HEAD, тест первым
Render опрашивает `/health`, чтобы понять, что приложение живое; мониторинг и предпросмотр ссылок могут использовать метод `HEAD`.

:::step Шаг 1. Красный тест
Сначала пишем, что хотим получить. Маршрута ещё нет, и тест не пройдёт.

```kotlin
@Test
fun `health answers OK`() = testApplication {
    application { configureRoutes(...) }
    val response = client.get("/health")
    assertEquals(HttpStatusCode.OK, response.status)
    assertEquals("OK", response.bodyAsText())
}
```

Сейчас ответ `404`: красный тест подтверждает, что мы проверяем именно то, что нужно.
:::

:::step Шаг 2. Минимальный код
Добавляем ровно столько, чтобы тест прошёл.

```kotlin
fun Route.healthRoutes() {
    get("/health") {
        call.respondText("OK")
    }
}
```

Это буквально [HealthRoutes.kt](repo:src/main/kotlin/org/proanima/shelter/routes/HealthRoutes.kt). Тест позеленел.
:::

:::step Шаг 3. Новое требование и новый красный тест
Предпросмотр ссылок и мониторинги шлют `HEAD`. Пишем тест: заголовки как у `GET`, тела нет.

```kotlin
val head = client.head("/health")
assertEquals(HttpStatusCode.OK, head.status)
assertEquals("", head.bodyAsText())
```

Без помощи фреймворка маршрут `get` не отвечает на `HEAD`, и результат `405 Method Not Allowed`. В проекте такой тест записан в `HeadRequestsTest` с комментарием: «Without help from the framework only files served from a folder answered it; a page gave 405».
:::

:::step Шаг 4. Плагин вместо дублирования
Не пишем `head("/health")` для каждой страницы. Один плагин отвечает на `HEAD` обработчиком `GET`, отбрасывая тело.

```kotlin
install(AutoHeadResponse)
```
:::

:::step Шаг 5. Итог: всё вместе
```kotlin check="test"
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

fun Route.healthRoutes() {
    get("/health") {
        call.respondText("OK")
    }
}

class HealthTest {
    @Test
    fun `health answers GET and HEAD`() = testApplication {
        application {
            install(AutoHeadResponse)
            routing { healthRoutes() }
        }

        val get = client.get("/health")
        assertEquals(HttpStatusCode.OK, get.status)
        assertEquals("OK", get.bodyAsText())

        val head = client.head("/health")
        assertEquals(HttpStatusCode.OK, head.status)
        assertEquals("", head.bodyAsText())
    }
}
```

Что мы сделали: поведение описано тестом, код минимален, а требование про `HEAD` не раздуло ни один маршрут. Такой цикл (красный, зелёный, улучшить) разберём в уроке про [TDD](lesson:a3-testing).
:::
::::

## Практика

::::task Исследуйте сайт командой curl
Выполните три запроса к любому сайту (можно к Pro Anima) и ответьте: какой статус и какие заголовки `Cache-Control` и `Content-Type` у (1) главной страницы `/ru`, (2) файла стилей, (3) несуществующей страницы? Как вы думаете, почему значения разные?

```bash
curl -I https://shelter-volunteer.onrender.com/ru
curl -I https://shelter-volunteer.onrender.com/styles/main.css
curl -I https://shelter-volunteer.onrender.com/ru/net-takoi-stranitsy
```

:::solution
Ожидаемые наблюдения:

1. `/ru`: `200`, `Content-Type: text/html; charset=UTF-8`, **нет** `Cache-Control`: HTML меняется вместе с данными (новая кошка, правка текста), и показывать устаревшую страницу нельзя.
2. `/styles/main.css`: `200`, `text/css`, `Cache-Control: max-age=3600`, `Content-Encoding: gzip`: статический файл, меняется редко и безопасно кэшируется, сжат как текст.
3. Несуществующая страница: `404` и всё равно `text/html`: страница ошибки оформлена, но статус честный.

Если вы в ответе увидите заголовки хостинга (`cf-cache-status`, `rndr-id`), это следы CDN и платформы. Они не из приложения.
:::
::::

::::task Redirect после формы
Напишите маршрут `post("/subscribe")`, который принимает поле `email` и отвечает `303 See Other` с заголовком `Location: /thanks`, а также тест, проверяющий статус и заголовок. Подсказка для теста: клиент по умолчанию **следует** за редиректами, поэтому создайте клиент так: `createClient { followRedirects = false }`.

:::hint
Готовая `call.respondRedirect("/thanks")` отвечает кодом `302`, а нам нужен `303`. Поэтому задайте заголовок и статус явно: `call.response.header(HttpHeaders.Location, "/thanks")`, затем `call.respond(HttpStatusCode.SeeOther)`.
:::

:::solution
```kotlin check="test"
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.application.call
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class SubscribeTest {
    @Test
    fun `a form post is answered with a redirect`() = testApplication {
        application {
            routing {
                post("/subscribe") {
                    call.receiveParameters()
                    call.response.header(HttpHeaders.Location, "/thanks")
                    call.respond(HttpStatusCode.SeeOther)
                }
            }
        }

        val client = createClient { followRedirects = false }
        val response = client.submitForm("/subscribe", parameters { append("email", "a@example.com") })

        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertEquals("/thanks", response.headers[HttpHeaders.Location])
    }
}
```

В проекте для этого есть маленькая функция `call.seeOther("/admin")`, которая делает именно это: статус `303` и заголовок `Location`. Выбор `303` (а не `302`) однозначно говорит браузеру «перейди по адресу методом GET», независимо от того, каким методом пришёл запрос.
:::
::::

::::task Какой Cache-Control?
Для каждого ресурса выберите подходящее поведение кэша и объясните одним предложением: (а) HTML-страница списка кошек; (б) `/styles/main.css?v=1a2b3c4d`; (в) `/images/felix.jpg`, фото, которое администратор может заменить, оставив имя; (г) страница админки.

:::solution
- (а) Не кэшировать (или кэшировать на секунды): содержимое зависит от данных, которые меняют волонтёры, и устаревшая страница хуже медленной.
- (б) Можно кэшировать долго (в проекте час, технически допустим и год): новый файл получит новый `?v=`.
- (в) Короткий кэш (в проекте сутки) плюс `ETag`: имя не меняется, значит, «вечно» кэшировать нельзя; ETag позволяет дёшево проверить актуальность. Загрузка новых фото в админке создаёт **новое имя** (с суффиксом из восьми знаков), чтобы эта проблема не возникала.
- (г) Запретить кэширование (`no-store`): страница показывает данные, которые не должны остаться на чужом компьютере и в промежуточных кэшах. В проекте страницы админки «never cached».
:::
::::

:::quiz Проверьте себя
Q: Почему для несуществующей кошки нужен статус 404, а не 200 со страницей «не найдено»?
- [ ] Браузер без него не покажет страницу.
- [x] Поисковики, мониторинг и клиенты принимают решения по статусу: ответ 200 выглядит как существующая страница.
- [ ] 404 быстрее.
- [ ] Так требует Ktor.
E: Страница может выглядеть как угодно, но статус говорит машинам правду.

Q: Зачем после успешной отправки формы отвечать `303` и переходом на другую страницу?
- [ ] Чтобы показать пользователю красивую анимацию.
- [x] Чтобы обновление страницы повторяло безопасный `GET`, а не повторную отправку формы.
- [ ] Чтобы ускорить сервер.
- [ ] Потому что `POST` нельзя отвечать страницей.
E: Схема Post/Redirect/Get защищает от дублей при обновлении страницы и кнопке «назад».

Q: Что означает адрес `/styles/main.css?v=1a2b3c4d`?
- [ ] Версия браузера.
- [ ] Идентификатор пользователя.
- [x] Отпечаток содержимого файла: при изменении файла адрес меняется, и кэш браузера не отдаёт старое.
- [ ] Номер релиза в Git.
E: Это приём «cache busting» через отпечаток: хэш вычисляется от самих байтов файла, поэтому нельзя забыть обновить версию.

Q: Какие ответы подходят для условного запроса с `If-None-Match`, если фото не менялось?
- [ ] `200` и полное тело.
- [x] `304 Not Modified` без тела.
- [ ] `404`.
- [ ] `204 No Content`.
E: `304` говорит «используй то, что у тебя есть» и экономит трафик, не теряя свежести.

Q: Почему сжатие gzip включено только для текстовых типов?
- [ ] Браузеры не распаковывают картинки.
- [x] Фотографии и шрифты уже сжаты; повторное сжатие тратит процессор, почти не уменьшая размер.
- [ ] Это требование хостинга.
- [ ] Сжатие ломает HTML.
E: Бесплатный тариф хостинга имеет очень скромный процессор; тратить его впустую нельзя.

Q: Что даёт оформление маршрутов функциями `Route.catRoutes(...)`?
- [ ] Ничего, это просто стиль.
- [x] Каждая область сайта в своём файле, а `Application.kt` остаётся оглавлением; одну функцию можно подключить под несколькими префиксами (языками).
- [ ] Ktor иначе не находит маршруты.
- [ ] Ускорение запуска.
E: Функции-расширения на `Route` позволяют собирать маршрутизацию из частей и параметризовать её (язык, сервисы).

Q: Что делает `testApplication { ... }`?
- [ ] Запускает настоящий сервер на порту 8080.
- [x] Запускает приложение в памяти и даёт клиента, который обращается к нему без сети.
- [ ] Подключается к тестовой базе в облаке.
- [ ] Генерирует тесты автоматически.
E: Поэтому тесты быстрые, повторяемые и не зависят от портов и сети.
:::

## Итоги

- Статус и заголовки — полноправная часть интерфейса: они читаются машинами и определяют поведение браузера.
- Кэширование строится на явных решениях: что можно хранить, как долго и как сообщить об изменении (отпечаток, ETag).
- Маршруты отвечают за «что вернуть», плагины за сквозное («как сжать», «как показать ошибку»).
- `testApplication` позволяет проверять сервер быстро и без сети.

Дальше: [HTML на сервере: семантика, формы, экранирование](lesson:w2-html-forms).
