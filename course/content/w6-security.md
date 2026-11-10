# Безопасность веб-приложения

На сайте приюта нет платёжных данных и паролей пользователей, и всё же у него есть админка, доступная из интернета. Ломать её будут не «хакеры из кино», а автоматические сканеры, перебирающие пароли, и случайные ошибки. Хорошая новость: основные приёмы защиты просты, и в коде проекта их можно увидеть в чистом виде. Главная идея: **любой ввод недоверенный**, а защита строится слоями, чтобы одна ошибка не открывала всё.

После урока вы сможете:

- описать угрозы для небольшого сайта и выбрать соразмерные меры;
- объяснить, как устроены сессия в подписанной cookie, токен CSRF и сравнение за постоянное время;
- защитить загрузку файлов: размер, тип по содержимому, число пикселей, чистка метаданных;
- хранить секреты и писать сообщения об ошибках так, чтобы ничего не утекало.

## Что и от кого защищаем

Перед тем как защищаться, надо ответить на вопрос «от чего». Для этого сайта:

| Угроза | Чем грозит | Основная защита |
|---|---|---|
| Подбор пароля админки | посторонний правит сайт | длина пароля, ограничитель попыток |
| CSRF: чужая страница шлёт запрос от имени админа | удаление записей, запуск тяжёлой операции | токен формы, `SameSite`, только `POST` |
| XSS: вредоносный текст в данных | кража сессии посетителей | экранирование, белый список ссылок ([HTML](lesson:w2-html-forms)) |
| Вредоносный файл вместо фото | падение сервера, заражение | проверка по содержимому, лимиты, перекодирование |
| Утечка секретов | полный доступ | переменные окружения, сообщения без значений |
| Огромные запросы | отказ в обслуживании | лимит размера до чтения тела |
| Утечка персональных данных | вред людям | не хранить и не показывать лишнее |

## Аутентификация: один пароль, но по-взрослому

В проекте один общий пароль на несколько волонтёров, без аккаунтов. Это осознанный компромисс (в `docs/decisions.md`: нет записи «кто что изменил», при уходе человека меняют пароль), но технически вход сделан тщательно.

### Требования к настройкам при старте

Мы уже видели `adminConfigFromEnv`: пароль не короче 12 знаков, секрет сессии не короче 32, обе настройки только вместе. Неверная конфигурация **останавливает запуск**. Пароль и секрет приходят из переменных окружения (в Render они вводятся в панели, а в `render.yaml` помечены `sync: false`, и значений в репозитории нет).

### Сравнение за постоянное время

Если сравнивать пароль обычным `==`, то сравнение прекращается на первом различающемся символе. Тот, кто измеряет время ответа очень точно, может подбирать пароль по символам. Защита: сравнивать **хэши** функцией, работающей одинаковое время:

```run
import java.security.MessageDigest

fun sha256(text: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())

fun tokensMatch(sent: String?, expected: String): Boolean =
    sent != null && MessageDigest.isEqual(sha256(sent), sha256(expected))

fun main() {
    println(tokensMatch("abc", "abc"))
    println(tokensMatch("abd", "abc"))
    println(tokensMatch(null, "abc"))
}
```

```output
true
false
false
```

Оба значения сначала превращаются в хэши **одной длины**, поэтому время не зависит ни от того, где они различаются, ни от длины введённого пароля. `MessageDigest.isEqual` сравнивает массивы за постоянное время. Это точная копия `passwordMatches` и `tokensMatch` из [AdminAuth.kt](repo:src/main/kotlin/org/proanima/shelter/admin/AdminAuth.kt).

### Замедление подбора

Мы строили `FailedLoginThrottle` в уроке про [конкурентность](lesson:k5-errors-concurrency): неверные попытки идут через один «шлюз», по секунде каждая; верный пароль шлюз не трогает. Блокировка по числу попыток сознательно **не** используется, потому что её можно использовать как оружие против волонтёров.

## Сессия в подписанной cookie

После входа браузер получает cookie `shelter_admin`. Сервер не хранит список сессий: вся информация лежит **в самой cookie**, защищённая подписью.

```kotlin
install(Sessions) {
    cookie<AdminSession>(SESSION_COOKIE) {
        serializer = AdminSessionSerializer
        transform(SessionTransportTransformerMessageAuthentication(sha256(config.sessionSecret)))
        cookie.path = "/admin"
        cookie.httpOnly = true
        cookie.secure = config.secureCookie
        cookie.maxAgeInSeconds = SESSION_LIFETIME.seconds
        cookie.extensions["SameSite"] = "Lax"
    }
}
```

Что делает каждая строка:

| Настройка | Защита |
|---|---|
| подпись (`MessageAuthentication`) | подделать или изменить cookie нельзя без секрета; сервер видит это и отвергает |
| `httpOnly` | JavaScript не может прочитать cookie: XSS не украдёт сессию |
| `secure` | cookie отправляется только по HTTPS (в локальной разработке по http отключается переменной `ADMIN_COOKIE_SECURE=false`) |
| `path = "/admin"` | браузер не присылает cookie на публичные страницы, где в ней нет нужды |
| `SameSite=Lax` | браузер не присылает cookie в запросах, инициированных другим сайтом (кроме обычных переходов по ссылке) |
| `maxAge` 12 часов | сессия сама кончается; сервер также проверяет возраст |

Подпись — это HMAC: хэш от значения и секрета. Проверим идею на упрощённой версии:

```run
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val SECRET = "s3cret-at-least-32-characters-long!!"

fun sign(value: String): String {
    val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(SECRET.toByteArray(), "HmacSHA256")) }
    val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.toByteArray()))
    return "$value:$signature"
}

fun verify(cookie: String): String? {
    val value = cookie.substringBeforeLast(':', "")
    val valid = value.isNotEmpty() && MessageDigest.isEqual(sign(value).toByteArray(), cookie.toByteArray())
    return value.takeIf { valid }
}

fun main() {
    val cookie = sign("csrf123.1762600000")
    println(verify(cookie))
    println(verify(cookie.replace("csrf123", "csrf999")))
    println(verify("garbage"))
}
```

```output
csrf123.1762600000
null
null
```

Подделать значение (`csrf999`) нельзя: подпись соответствует старому значению. Подписать новое без секрета тоже нельзя. Поэтому **секрет сессии никогда не попадает в репозиторий**, а при уходе человека, знавшего пароль, меняют `ADMIN_PASSWORD` **и** `SESSION_SECRET`: смена секрета обесценивает все выданные cookie.

:::note Цена такого решения
Сессия без серверного хранилища не отзывается по одной: «выкинуть вот этого пользователя» нельзя, можно только сменить секрет и разлогинить всех. Для сайта с одним паролем это приемлемо. Для сервиса с аккаунтами нужна таблица сессий.
:::

### Возраст сессии

```kotlin
internal fun currentSession(call: ApplicationCall): AdminSession? {
    val session = call.sessions.get<AdminSession>() ?: return null
    val age = clock.instant().epochSecond - session.issuedAtEpochSecond
    return session.takeIf { age in 0..SESSION_LIFETIME.seconds }
}
```

Условие `age in 0..LIFETIME` отвергает не только слишком старые сессии, но и сессии «из будущего» (отрицательный возраст), которые бывают при подделке или сбое часов:

```run
import java.time.Duration

val SESSION_LIFETIME: Duration = Duration.ofHours(12)

class Session(val issuedAtEpochSecond: Long)

fun valid(session: Session?, nowEpochSecond: Long): Boolean {
    if (session == null) return false
    val age = nowEpochSecond - session.issuedAtEpochSecond
    return age in 0..SESSION_LIFETIME.seconds
}

fun main() {
    val issued = 1_000_000L
    println(valid(Session(issued), issued + 60))
    println(valid(Session(issued), issued + 13 * 3600))
    println(valid(Session(issued), issued - 5))
    println(valid(null, issued))
}
```

```output
true
false
false
false
```

Время берётся из `Clock`, а не из системы напрямую, поэтому тест может «промотать» 13 часов за миллисекунду.

## CSRF: чужая страница действует от вашего имени

Представьте: админ вошёл в панель и в соседней вкладке открыл чужой сайт. На нём спрятана форма, которая отправляет `POST /admin/cats/3/delete`. Браузер приложит к такому запросу cookie админа, и сервер увидит «законного» пользователя. Это **CSRF** (подделка межсайтового запроса).

Защита в проекте многослойная:

1. **Меняющие запросы только `POST`**. Ссылка (`GET`) не может ничего изменить, а в проекте есть пример внимательности: даже резервная копия запускается `POST`-ом, потому что «it makes the server read every photo, so a page on another site must not be able to start it».
2. **`SameSite=Lax`**: современные браузеры не пришлют cookie в `POST`, инициированный другим сайтом.
3. **Токен формы.** При входе генерируется случайный токен `csrf` (32 случайных байта), хранится в сессии и вставляется в **каждую** форму скрытым полем `_csrf`. Чужая страница токена не знает.

Все три слоя применяются в одном месте. Это единственный «пропускной пункт» для всех изменений ([AdminRoutes.kt](repo:src/main/kotlin/org/proanima/shelter/admin/AdminRoutes.kt), `withForm`):

```kotlin
internal suspend inline fun AdminSetup.withForm(call: ApplicationCall, maxBytes: Long = MAX_FORM_BYTES, block: (AdminSession, Map<String, String>) -> Unit) {
    val session = currentSession(call)
    if (session == null) { call.seeOther("/admin/login"); return }
    val length = call.request.contentLength()
    if (length == null || length > maxBytes) { call.respond(HttpStatusCode.PayloadTooLarge); return }
    val parameters = call.receiveParameters()
    if (!tokensMatch(parameters["_csrf"], session.csrf)) { call.respondHtml(HttpStatusCode.Forbidden) { ... }; return }
    block(session, parameters.entries().associate { (name, values) -> name to values.first() })
}
```

Три проверки по порядку: человек вошёл; размер тела допустим (**до** чтения); токен совпал. Лишь после этого выполняется действие. Один «охранник у входа» лучше, чем десять разбросанных проверок: новый маршрут, который забыл проверить, невозможно написать, если он обязан пройти через `withForm`.

## Live coding: пропускной пункт

::::live Охранник формы
Построим упрощённую `withForm` как чистую функцию и покроем тестами каждую ветку. Модель решений, а не HTTP.

:::step Шаг 1. Результат как значение
Решений четыре: пропустить, отправить на вход, слишком большой запрос, запрещено.

```kotlin
sealed interface Decision {
    data object Allowed : Decision
    data object ToLogin : Decision
    data object TooLarge : Decision
    data object Forbidden : Decision
}
```
:::

:::step Шаг 2. Порядок проверок
Порядок существенен. Незалогиненный не должен узнать ничего о правилах: сначала вход. Затем размер (читать огромное тело нельзя), затем токен.

```kotlin
fun decide(session: Session?, contentLength: Long?, sentToken: String?, maxBytes: Long = 200_000): Decision = when {
    session == null -> Decision.ToLogin
    contentLength == null || contentLength > maxBytes -> Decision.TooLarge
    !tokensMatch(sentToken, session.csrf) -> Decision.Forbidden
    else -> Decision.Allowed
}
```

Отсутствие `Content-Length` (`null`) тоже отвергается: клиент, не сообщающий размер, мог бы слать данные бесконечно.
:::

:::step Шаг 3. Тесты на каждую ветку
Запустите тесты. Попробуйте поменять местами строки в `when`: тест на порядок упадёт.

```test
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

sealed interface Decision {
    data object Allowed : Decision
    data object ToLogin : Decision
    data object TooLarge : Decision
    data object Forbidden : Decision
}

class Session(val csrf: String)

private fun sha256(text: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())

fun tokensMatch(sent: String?, expected: String): Boolean =
    sent != null && MessageDigest.isEqual(sha256(sent), sha256(expected))

fun decide(session: Session?, contentLength: Long?, sentToken: String?, maxBytes: Long = 200_000): Decision = when {
    session == null -> Decision.ToLogin
    contentLength == null || contentLength > maxBytes -> Decision.TooLarge
    !tokensMatch(sentToken, session.csrf) -> Decision.Forbidden
    else -> Decision.Allowed
}

class GuardTest {
    private val session = Session("token-1")

    @Test
    fun `a good request passes`() {
        assertEquals(Decision.Allowed, decide(session, 1_000, "token-1"))
    }

    @Test
    fun `nobody logged in goes to the login page, whatever else is wrong`() {
        assertEquals(Decision.ToLogin, decide(null, 1_000, "token-1"))
        assertEquals(Decision.ToLogin, decide(null, 999_999_999, null))
    }

    @Test
    fun `a body that is too big or has no length is refused before the token is looked at`() {
        assertEquals(Decision.TooLarge, decide(session, 200_001, "wrong"))
        assertEquals(Decision.TooLarge, decide(session, null, "token-1"))
    }

    @Test
    fun `a wrong or missing token is forbidden`() {
        assertEquals(Decision.Forbidden, decide(session, 1_000, "other"))
        assertEquals(Decision.Forbidden, decide(session, 1_000, null))
    }
}
```
:::

:::step Шаг 4. Что мы сделали
Правила безопасности превращены в **маленькую чистую функцию**, которую можно проверить за миллисекунды без сервера, браузера и cookie. В проекте у HTTP-версии есть такие же тесты, но через `testApplication` и с реальными cookie: сначала поведение в чистом виде, затем интеграция.
:::
::::

## Загрузка файлов: самая опасная функция админки

Файл от пользователя — худший вид ввода: его размер, формат и содержимое выбирает другой человек. В [ImageProcessing.kt](repo:src/main/kotlin/org/proanima/shelter/admin/ImageProcessing.kt) защита построена эшелонами:

1. **Лимиты запроса**: до 5 файлов, каждый не более 8 МБ; общий размер запроса ограничен.
2. **Тип по содержимому**, а не по имени и не по заявлению браузера. Первые байты файла сравниваются с сигнатурой JPEG или PNG:

```run
private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

fun sniff(bytes: ByteArray): String? = when {
    bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "jpeg"
    bytes.size > 8 && bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE) -> "png"
    else -> null
}

fun main() {
    println(sniff(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
    println(sniff(PNG_SIGNATURE + byteArrayOf(0)))
    println(sniff("<?php echo 1; ?>".toByteArray()))
    println(sniff(byteArrayOf()))
}
```

```output
jpeg
png
null
null
```

Файл с именем `cat.jpg`, внутри которого скрипт, будет отвергнут. Всё, чего нет в белом списке форматов, отклоняется.

3. **Число пикселей считается по заголовку, до распаковки.** Маленький файл может **объявить** огромную картинку (бомба распаковки): 20 000 × 20 000 пикселей это 1,2 ГБ памяти после декодирования. Проект читает размеры из заголовка и отказывает, если больше 30 мегапикселей. И в этой проверке спрятан классический капкан:

```run
fun acceptWrong(width: Int, height: Int) = width * height <= 30_000_000

fun acceptRight(width: Int, height: Int) = width.toLong() * height <= 30_000_000L

fun main() {
    println(acceptWrong(Int.MAX_VALUE, 2))
    println(acceptRight(Int.MAX_VALUE, 2))
}
```

```output
true
false
```

`Int.MAX_VALUE * 2` в `Int` **переполняется** и даёт `-2`, а это меньше лимита, поэтому «неправильная» версия пропускает гигантскую картинку. В проекте стоит `width.toLong() * height`. Переполнение целых чисел — частый источник уязвимостей; при проверке размеров считайте в `Long`.

4. **Перекодирование.** Фото не сохраняется как есть: оно декодируется, при необходимости поворачивается по метке EXIF и записывается заново как JPEG **без метаданных**. Побочные выгоды: внутри не может скрываться ничего постороннего, а GPS-координаты телефона не покидают телефон (в комментарии: «a phone's GPS position does not leave the phone»).
5. **Имя файла выбирает сервер**: `/images/cat-12-3fa9c1d2.jpg` (тип, номер, 8 случайных шестнадцатеричных знаков). Имя от пользователя не используется вообще, поэтому невозможно ни `../../etc/passwd`, ни перезапись чужого файла.
6. **Ограничение ресурсов**: фото обрабатываются по одному (`Mutex`), чтобы не исчерпать память.

## Секреты и конфигурация

- Секреты **только** в переменных окружения, не в коде, не в репозитории, не в Docker-образе. В `render.yaml` значения помечены `sync: false`: «Entered in the Render dashboard, never committed».
- Сообщения об ошибках не содержат значений настроек (мы видели это в `parseDatabaseUrl`).
- Контейнер работает **не от root**: в `Dockerfile` строка `USER app` под системным пользователем без оболочки и домашней папки. Если в приложении найдут уязвимость, у атакующего меньше возможностей.
- Если секрет попал в чат или репозиторий, его считают скомпрометированным и **заменяют**, а не «стирают из истории».

## Заголовки ответа

Страницы админки получают набор заголовков в одном месте:

```kotlin
route("/admin") {
    intercept(ApplicationCallPipeline.Plugins) {
        call.response.header("X-Robots-Tag", "noindex, nofollow")
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.response.header("X-Frame-Options", "DENY")
        call.response.header("Referrer-Policy", "same-origin")
    }
    ...
}
```

`noindex` не пускает поисковики, `no-store` запрещает кэшам хранить страницу, `X-Frame-Options: DENY` запрещает вставлять страницу в чужой `iframe` (защита от кликджекинга), `Referrer-Policy` не выдаёт адреса админки другим сайтам.

:::warn Честный список того, чего нет
Для публичных страниц проект **не** задаёт `Content-Security-Policy` (политику, ограничивающую источники скриптов) и `X-Content-Type-Options: nosniff`. HTTPS обеспечивает хостинг, а заголовок HSTS приложение само не задаёт. Это возможные улучшения: они добавляют защиту «в глубину» на случай, если экранирование где-то подведёт. Попробуйте добавить их в практическом задании.
:::

## Практика

::::task Заголовки безопасности
Дополните блок `intercept` для `/admin`: добавьте `X-Content-Type-Options: nosniff` и `Content-Security-Policy: default-src 'self'`. Напишите тест, который проверяет, что на `/admin` пришли все пять заголовков (`X-Robots-Tag`, `Cache-Control`, `X-Frame-Options`, `X-Content-Type-Options`, `Content-Security-Policy`), а на `/public` нет.

:::hint
В `testApplication` ответ клиента даёт `response.headers["Имя"]`. Маршруты: `route("/admin") { intercept(ApplicationCallPipeline.Plugins) { ... }; get { call.respondText("admin") } }` и `get("/public") { ... }`.
:::

:::solution
```kotlin check="test"
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdminHeadersTest {
    @Test
    fun `the admin area carries the security headers and the public area does not`() = testApplication {
        application {
            routing {
                route("/admin") {
                    intercept(ApplicationCallPipeline.Plugins) {
                        call.response.header("X-Robots-Tag", "noindex, nofollow")
                        call.response.header(HttpHeaders.CacheControl, "no-store")
                        call.response.header("X-Frame-Options", "DENY")
                        call.response.header("X-Content-Type-Options", "nosniff")
                        call.response.header("Content-Security-Policy", "default-src 'self'")
                    }
                    get { call.respondText("admin") }
                }
                get("/public") { call.respondText("public") }
            }
        }

        val admin = client.get("/admin")
        assertEquals("noindex, nofollow", admin.headers["X-Robots-Tag"])
        assertEquals("no-store", admin.headers[HttpHeaders.CacheControl])
        assertEquals("DENY", admin.headers["X-Frame-Options"])
        assertEquals("nosniff", admin.headers["X-Content-Type-Options"])
        assertEquals("default-src 'self'", admin.headers["Content-Security-Policy"])

        val public = client.get("/public")
        assertNull(public.headers["X-Frame-Options"])
        assertNull(public.headers["Content-Security-Policy"])
    }
}
```

Осторожно с `Content-Security-Policy: default-src 'self'` на реальном сайте: такая политика запретит **встроенные** скрипты и стили, а страница проекта содержит маленький встроенный скрипт темы. Чтобы включить CSP, нужно либо добавить `nonce`/хэш для этого скрипта, либо вынести его (но тогда теряется «выставить тему до первой отрисовки»). Безопасность часто требует компромисса с удобством, поэтому её вводят постепенно: сначала в режиме отчёта (`Content-Security-Policy-Report-Only`).
:::
::::

::::task Найдите уязвимости
Здесь четыре проблемы безопасности. Назовите их и предложите исправление.

```kotlin
post("/photos") {
    val upload = call.receiveMultipart()
    upload.forEachPart { part ->
        if (part is PartData.FileItem) {
            val name = part.originalFileName ?: "photo.jpg"
            File("data/images/$name").writeBytes(part.streamProvider().readBytes())
        }
    }
    call.respondText("ok")
}

fun checkPassword(entered: String, real: String) = entered == real
```

:::solution
1. **Имя файла от пользователя в пути** (`$name`): имя `../../app/config` выходит за папку, а совпавшее имя перезаписывает чужой файл. Исправление: имя генерирует сервер.
2. **Нет проверки типа и содержимого:** можно загрузить скрипт, исполняемый файл, огромный файл. Исправление: проверка сигнатуры, лимиты размера и пикселей, перекодирование.
3. **Нет ограничения размера** (`readBytes()` читает всё в память): бесконечный запрос исчерпает память. Исправление: проверять `Content-Length` до чтения и читать с ограничением.
4. **Нет проверки входа и CSRF-токена:** загружать может кто угодно, а чужая страница может отправить форму от имени админа. Исправление: пропускать маршрут через `withForm`.

Плюс пятая, в `checkPassword`: сравнение `==` не за постоянное время и без ограничителя попыток.
:::
::::

::::task Подберите защиту
Сопоставьте угрозу и меру, объясните одним предложением, почему мера работает: (а) JavaScript с чужого сайта пытается прочитать cookie админа; (б) чужая страница отправляет форму удаления; (в) файл `cat.jpg` оказался программой; (г) уволенный волонтёр знает пароль; (д) кто-то шлёт 5 ГБ на адрес входа.

:::solution
- (а) `httpOnly`: cookie недоступна для JavaScript. Плюс экранирование, чтобы чужой скрипт не попал на страницу.
- (б) Токен формы `_csrf` (чужая страница его не знает) и `SameSite=Lax`.
- (в) Проверка по содержимому (сигнатура), а не по имени; перекодирование: из «программы» получится либо отказ, либо безобидный JPEG.
- (г) Смена `ADMIN_PASSWORD` и `SESSION_SECRET`: пароль уже не подходит, а ранее выданные cookie недействительны.
- (д) Проверка `Content-Length` **до** чтения тела: в проекте лимит 4 КБ для формы входа и 200 КБ для остальных форм (ответ `413`).
:::
::::

:::quiz Проверьте себя
Q: Зачем пароль и токен сравнивают через хэши и `MessageDigest.isEqual`, а не через `==`?
- [ ] Так короче.
- [x] Сравнение `==` заканчивается на первом отличии, и время ответа выдаёт, насколько «близко» значение; сравнение хэшей занимает одинаковое время.
- [ ] Хэш защищает от SQL-инъекций.
- [ ] `==` не работает со строками.
E: Это защита от атаки по времени. Хэширование обоих значений делает и длину ввода непоказательной.

Q: Что делает флаг `httpOnly` у cookie сессии?
- [ ] Передаёт cookie только по HTTP.
- [x] Запрещает JavaScript читать cookie, поэтому XSS не сможет её украсть.
- [ ] Шифрует cookie.
- [ ] Продлевает срок жизни.
E: Для передачи только по HTTPS отвечает `secure`, а `httpOnly` ограждает от скриптов.

Q: Почему сессию нельзя «отозвать» у одного пользователя в этой схеме?
- [ ] Из-за ошибки в коде.
- [x] Сервер не хранит список сессий: вся информация в подписанной cookie, поэтому можно только сменить секрет и разлогинить всех.
- [ ] Потому что Ktor не умеет.
- [ ] Потому что cookie бессрочная.
E: Это цена «безсессионного» подхода. Для сервиса с аккаунтами нужна серверная таблица сессий.

Q: Какие меры защищают от CSRF в проекте?
- [x] Изменения только через `POST`.
- [x] Случайный токен `_csrf` в каждой форме, который сверяется с токеном сессии.
- [x] `SameSite=Lax` у cookie.
- [ ] Скрытие адреса `/admin`.
E: Скрытие адреса не защита: адрес угадывается. Проект его не рекламирует, но полагается на настоящие механизмы.

Q: Почему число пикселей картинки читают из заголовка до декодирования?
- [ ] Заголовок читается быстрее.
- [x] Маленький файл может объявлять огромные размеры; декодирование заняло бы гигабайты памяти.
- [ ] Заголовок точнее.
- [ ] Так требует формат JPEG.
E: Это защита от «бомбы распаковки». Проверка должна происходить до дорогой операции.

Q: В чём ошибка в проверке `width * height <= 30_000_000` для `Int`?
- [ ] Ошибки нет.
- [x] Произведение двух `Int` может переполниться и стать отрицательным, и огромная картинка пройдёт проверку.
- [ ] Нужно использовать `Double`.
- [ ] Нужно сравнивать со строкой.
E: Перед умножением приводите к `Long`: `width.toLong() * height`.

Q: Почему имя загруженного файла выбирает сервер?
- [ ] Чтобы имена были красивее.
- [x] Имя от пользователя может содержать `../` и перезаписать чужие файлы; серверное имя исключает такие атаки.
- [ ] Чтобы файл не занимал места.
- [ ] Этого требует JPEG.
E: Любой ввод недоверенный, а имя файла вводит человек.
:::

## Итоги

- Определите, от чего защищаетесь, и выбирайте меры соразмерно. Любой ввод недоверенный.
- Один пропускной пункт (`withForm`) для всех изменений: вход, размер, токен.
- Подпись cookie, `httpOnly`, `secure`, `SameSite`; секреты в окружении, сообщения без значений.
- Файлы: тип по содержимому, лимиты до распаковки, `Long` для размеров, перекодирование, серверные имена.
- Знайте свои пробелы (нет CSP) и закрывайте их постепенно.

Часть про веб пройдена. Дальше начинается самая важная часть курса: [Архитектура: слои, зависимости, решения](lesson:a1-architecture).
