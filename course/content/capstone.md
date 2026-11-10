# Итоговый проект

Пора собрать всё вместе. Вы добавите в сайт настоящую функцию: **фильтр кошек по статусу** на странице списка. Она маленькая, но проходит через все слои проекта: модель и сервис, маршрут, представление, тексты на трёх языках, CSS с тёмной темой, доступность, тесты и документацию. Задание рассчитано на самостоятельную работу в вашей копии репозитория; агента можно и нужно использовать так, как вы научились в части про ИИ.

Цель не в самой функции, а в **привычке**: каждый шаг делается тестом вперёд, маленькими изменениями, с проверкой и записью решения.

## Что делаем

**Пользовательская история.** Волонтёр, открывший список кошек, хочет увидеть только тех, кто ищет дом, чтобы не листать всех. Над списком появляются ссылки-фильтры: «Все», «Ищут дом», «Забронированы», «Нашли дом». Выбранный фильтр отражается в адресе (`/ru/cats?stage=for-adoption`), чтобы им можно было поделиться ссылкой.

**Критерии приёмки.**

1. Адрес `/{язык}/cats?stage=for-adoption` (также `reserved`, `adopted`) показывает только кошек с этим статусом; без параметра и с неизвестным значением показываются все (как сейчас).
2. Фильтры показываются **только для статусов, у которых есть опубликованная кошка**. Черновики на набор фильтров не влияют (иначе это утечка: человек узнает, что есть неопубликованная кошка).
3. Если фильтр выбран, но кошек нет (например, всех усыновили после того, как ссылку отправили), страница отвечает **спокойным пустым состоянием**, а не пустотой и не ошибкой.
4. Работает **без JavaScript**: это обычные ссылки. Текущий фильтр отмечен `aria-current="true"`, а не только цветом.
5. Тексты на `ru`, `en`, `sr`; ничего не ломается в самом длинном языке на ширине 360 px; тёмная тема.
6. Принципы дизайна не нарушены: один акцент, никаких украшений без причины, факт не повторяется (например, **без счётчиков в ссылках**: число кошек уже видно в списке).
7. Тесты: сервис, маршрут (HTTP), доступность. `./gradlew test` зелёный. В `docs/` записано решение.

:::note О границах
Фильтр не меняет «раздел нашедших дом» внизу страницы, пока фильтр не выбран. Что показывать при `stage=adopted`, решите сами и запишите решение (варианты: один список вместо двух или раздел остаётся). Решение, принятое и записанное, лучше правильного, но неозвученного.
:::

## Карта изменений

Прежде чем писать код, пройдитесь по слоям (урок про [архитектуру](lesson:a1-architecture)) и запишите, что где поменяется. Вот как выглядит карта.

| Слой | Файл | Что меняется |
|---|---|---|
| Сервис | `service/CatService.kt` и новый маленький файл фильтра | разбор параметра, набор доступных фильтров, выборка (чистые функции) |
| Маршрут | `routes/CatRoutes.kt` | читает `stage` из `call.request.queryParameters`, передаёт в представление |
| Представление | `views/CatViews.kt` | навигация-фильтр над списком, пустое состояние |
| Тексты | `i18n/messages.properties` (английский), `messages_ru`, `messages_sr`; `messages_en` пустой намеренно | новые ключи (подписи фильтров, пустое состояние, название навигации) |
| Стили | `static/styles/main.css` | вид фильтра, тёмная тема, 640 px |
| Тесты | `service/`, `routes/` | сервис, HTTP, доступность |
| Документы | `docs/design.md`, `docs/decisions.md` | компонент и решение |

Принцип: **логика в чистых функциях**, которые проверяются без сервера; маршрут и представление тонкие.

## Порядок работы

0. **Ветка и чистое состояние.** `git switch -c cat-stage-filter`, `./gradlew test` зелёный до начала.
1. **Ядро с тестами** (задание 1).
2. **Маршрут** (задание 2).
3. **Представление и пустое состояние.**
4. **Тексты** на трёх языках, проверка полноты (задание 3).
5. **CSS**: вид, тёмная тема, узкий экран.
6. **Доступность и обзор** (задание 4).
7. **Документы**: компонент в `docs/design.md`, решение в `docs/decisions.md`.
8. **Ревью с агентом** и самопроверка по таблице внизу.

После каждого шага: тесты, маленький коммит. Если вы работаете с агентом, давайте ему один шаг за раз, с критериями (см. [урок про агентов](lesson:ai1-agents)).

## Практика

::::task Шаг 1. Ядро: параметр, набор фильтров, выборка
Напишите чистые функции и сделайте тесты зелёными.

```starter target="junit"
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

enum class AdoptionStage { NONE, FOR_ADOPTION, RESERVED, ADOPTED }

data class Cat(val id: Int, val name: String, val stage: AdoptionStage, val published: Boolean = true)

// "for-adoption" -> FOR_ADOPTION, "reserved" -> RESERVED, "adopted" -> ADOPTED; anything else -> null
fun parseStageFilter(param: String?): AdoptionStage? {
    TODO()
}

fun urlKey(stage: AdoptionStage): String {
    TODO()
}

// The stages that have at least one PUBLISHED cat, in the order: for adoption, reserved, adopted
fun stagesPresent(cats: List<Cat>): List<AdoptionStage> {
    TODO()
}

// Published cats only; stage == null means no filter
fun filterByStage(cats: List<Cat>, stage: AdoptionStage?): List<Cat> {
    TODO()
}

class StageFilterTest {
    private val cats = listOf(
        Cat(1, "Felix", AdoptionStage.FOR_ADOPTION),
        Cat(2, "Mila", AdoptionStage.ADOPTED),
        Cat(3, "Secret", AdoptionStage.RESERVED, published = false),
        Cat(4, "Fox", AdoptionStage.FOR_ADOPTION)
    )

    @Test
    fun `the parameter is read leniently and unknown values mean no filter`() {
        assertEquals(AdoptionStage.RESERVED, parseStageFilter("reserved"))
        assertEquals(AdoptionStage.ADOPTED, parseStageFilter(" ADOPTED "))
        assertNull(parseStageFilter("nonsense"))
        assertNull(parseStageFilter(""))
        assertNull(parseStageFilter(null))
    }

    @Test
    fun `a draft does not make its stage appear in the filters`() {
        assertEquals(listOf(AdoptionStage.FOR_ADOPTION, AdoptionStage.ADOPTED), stagesPresent(cats))
    }

    @Test
    fun `filtering keeps only published cats of the stage`() {
        assertEquals(listOf("Felix", "Fox"), filterByStage(cats, AdoptionStage.FOR_ADOPTION).map { it.name })
        assertEquals(emptyList(), filterByStage(cats, AdoptionStage.RESERVED))
        assertEquals(listOf("Felix", "Mila", "Fox"), filterByStage(cats, null).map { it.name })
    }

    @Test
    fun `url keys round trip`() {
        listOf(AdoptionStage.FOR_ADOPTION, AdoptionStage.RESERVED, AdoptionStage.ADOPTED).forEach {
            assertEquals(it, parseStageFilter(urlKey(it)))
        }
    }
}
```

:::hint
Держите соответствие «ключ в адресе → статус» в **одной** карте `linkedMapOf(...)`: из неё берутся и разбор, и обратное преобразование, и порядок показа. Так три функции не разойдутся.
:::

:::solution
```test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

enum class AdoptionStage { NONE, FOR_ADOPTION, RESERVED, ADOPTED }

data class Cat(val id: Int, val name: String, val stage: AdoptionStage, val published: Boolean = true)

// One table is the single source of truth: the key in the address, the stage and the display order.
private val STAGE_KEYS = linkedMapOf(
    "for-adoption" to AdoptionStage.FOR_ADOPTION,
    "reserved" to AdoptionStage.RESERVED,
    "adopted" to AdoptionStage.ADOPTED
)

fun parseStageFilter(param: String?): AdoptionStage? = STAGE_KEYS[param?.trim()?.lowercase()]

fun urlKey(stage: AdoptionStage): String = STAGE_KEYS.entries.first { it.value == stage }.key

fun stagesPresent(cats: List<Cat>): List<AdoptionStage> =
    STAGE_KEYS.values.filter { stage -> cats.any { it.published && it.stage == stage } }

fun filterByStage(cats: List<Cat>, stage: AdoptionStage?): List<Cat> =
    cats.filter { it.published && (stage == null || it.stage == stage) }

class StageFilterTest {
    private val cats = listOf(
        Cat(1, "Felix", AdoptionStage.FOR_ADOPTION),
        Cat(2, "Mila", AdoptionStage.ADOPTED),
        Cat(3, "Secret", AdoptionStage.RESERVED, published = false),
        Cat(4, "Fox", AdoptionStage.FOR_ADOPTION)
    )

    @Test
    fun `the parameter is read leniently and unknown values mean no filter`() {
        assertEquals(AdoptionStage.RESERVED, parseStageFilter("reserved"))
        assertEquals(AdoptionStage.ADOPTED, parseStageFilter(" ADOPTED "))
        assertNull(parseStageFilter("nonsense"))
        assertNull(parseStageFilter(""))
        assertNull(parseStageFilter(null))
    }

    @Test
    fun `a draft does not make its stage appear in the filters`() {
        assertEquals(listOf(AdoptionStage.FOR_ADOPTION, AdoptionStage.ADOPTED), stagesPresent(cats))
    }

    @Test
    fun `filtering keeps only published cats of the stage`() {
        assertEquals(listOf("Felix", "Fox"), filterByStage(cats, AdoptionStage.FOR_ADOPTION).map { it.name })
        assertEquals(emptyList(), filterByStage(cats, AdoptionStage.RESERVED))
        assertEquals(listOf("Felix", "Mila", "Fox"), filterByStage(cats, null).map { it.name })
    }

    @Test
    fun `url keys round trip`() {
        listOf(AdoptionStage.FOR_ADOPTION, AdoptionStage.RESERVED, AdoptionStage.ADOPTED).forEach {
            assertEquals(it, parseStageFilter(urlKey(it)))
        }
    }
}
```

Три решения, которые стоит заметить. Одна таблица `STAGE_KEYS` определяет и разбор, и ключи, и **порядок** показа. Неизвестное значение параметра не ошибка, а «без фильтра»: ссылка, отправленная месяц назад, не должна ломаться (принцип «лояльно к входу»). И черновик не влияет на набор фильтров: тест фиксирует это правило, как мы фиксировали «черновики не видны» в уроке про [тестирование](lesson:a3-testing). В проекте эти функции естественно положить в новый небольшой файл `service/StageFilter.kt`, а `CatService` получит метод `getCatsByStage(stage)` поверх `published()`.
:::
::::

::::task Шаг 2. Маршрут и представление
Подключите ядро к странице. Ориентир (без полного кода: допишите сами, это и есть задание).

```kotlin
get("/cats") {
    val stage = parseStageFilter(call.request.queryParameters["stage"])
    val allCats = catService.getAllCats()
    val cats = filterByStage(allCats, stage)
    val filters = stagesPresent(allCats)
    val messages = siteContent.load().bundle(locale)

    call.respondPage(locale) {
        catsListPage(cats, locale, call.request.path(), messages, stage = stage, filters = filters)
    }
}
```

Что нужно придумать самим: как `catsListPage` рисует навигацию (`nav` с подписью, список ссылок), как отмечается текущий фильтр (`aria-current="true"` у выбранного), как выглядит пустое состояние, что делает ссылка «Все» (адрес без параметра), что меняется в разделе «нашедших дом».

:::hint
Навигация это `nav(classes = "cat-filter")` с `aria-label`, внутри `ul`/`li` или просто последовательность `a`. Подписи берутся из сообщений (`messages.t("cat.filter.forAdoption")`). Пустое состояние: абзац `p(classes = "list-note")` с текстом «Сейчас таких кошек нет» плюс ссылка «Показать всех».
:::

:::solution
Один из допустимых вариантов представления (дополните импортами и ключами сообщений):

```kotlin
internal fun FlowContent.stageFilter(filters: List<AdoptionStage>, current: AdoptionStage?, locale: AppLocale, messages: ResourceBundle) {
    if (filters.isEmpty()) return
    val base = "/${locale.code}/cats"
    nav(classes = "cat-filter") {
        attributes["aria-label"] = messages.t("cat.filter.label")
        a(href = base) {
            if (current == null) attributes["aria-current"] = "true"
            +messages.t("cat.filter.all")
        }
        filters.forEach { stage ->
            a(href = "$base?stage=${urlKey(stage)}") {
                if (stage == current) attributes["aria-current"] = "true"
                +messages.t("cat.filter.${urlKey(stage)}")
            }
        }
    }
}
```

Замечания для ревью: (1) ссылки обычные, без JS; (2) выбранный пункт отмечен не только стилем; (3) подписи в сообщениях на трёх языках (ключи вида `cat.filter.for-adoption`; если ключ содержит дефис, это допустимо в `.properties`); (4) при выбранном фильтре без кошек (`cats.isEmpty()`) рядом со списком выводится сообщение и ссылка «Показать всех». Страница **не должна** отвечать 404: фильтр это просмотр, а не отдельный ресурс.
:::
::::

::::task Шаг 3. Тексты и проверка полноты
Выпишите **все** новые ключи сообщений и их переводы на `ru`, `en`, `sr` (латиница). Затем примените функцию `missingKeys` из урока про [агентов](lesson:ai1-agents), чтобы убедиться, что ни один ключ не забыт.

:::solution
Набор ключей:

| Ключ | ru | en | sr |
|---|---|---|---|
| `cat.filter.label` | Фильтр по статусу | Filter by status | Filter po statusu |
| `cat.filter.all` | Все | All | Sve |
| `cat.filter.for-adoption` | Ищут дом | Looking for a home | Traže dom |
| `cat.filter.reserved` | Забронированы | Reserved | Rezervisane |
| `cat.filter.adopted` | Нашли дом | Found a home | Našle su dom |
| `cat.filter.empty` | Сейчас таких кошек нет | No cats here right now | Trenutno nema takvih mačaka |
| `cat.filter.showAll` | Показать всех | Show all cats | Prikaži sve |

Проверьте самую длинную строку на ширине 360 px (сербское «Trenutno nema takvih mačaka»). Хорошая практика: превратить `missingKeys` в тест, читающий реальные файлы `messages*.properties` и падающий, если ключ есть не везде. Тогда «забытый перевод» перестанет быть возможным.

Подумайте о качестве переводов: сербский текст проверьте у носителя или сообщите, что он не вычитан. Тексты по-сербски, написанные «на глаз» агентом или вами, это **риск репутации**, а не мелочь.
:::
::::

::::task Шаг 4. Доступность, дизайн, обзор
Проверьте результат по списку и ответьте на вопросы. Затем попросите агента сделать **независимое ревью** вашего диффа (подагент с чистым контекстом) и сравните его замечания со своими.

1. Проходится ли фильтр с клавиатуры (Tab, Enter)? Виден ли фокус?
2. Как скринридер читает навигацию? Есть ли у неё имя?
3. Какой контраст у выбранного пункта в обеих темах? (Посчитайте.)
4. Что происходит на ширине 360 px на сербском: переносятся ли ссылки, нет ли горизонтальной прокрутки?
5. Что говорит `docs/design.md`: нужен ли новый компонент или подойдут существующие правила? Что вы допишете?
6. Какие три теста вы написали бы для маршрута (HTTP)?

:::solution
Ориентиры ответов:

1. Обычные ссылки фокусируются и открываются Enter; рамка фокуса задана глобально `:focus-visible`.
2. «Навигация, Фильтр по статусу», затем ссылки; выбранный воспринимается как «текущий» благодаря `aria-current`.
3. Используйте токены (`--color-accent` на `--color-bg`/`--color-surface`): значения уже проверены, но если вы ввели новый цвет или фон у выбранного пункта, считайте заново (калькулятор из урока про [доступность](lesson:u2-a11y-perf)).
4. Ссылки идут в строку с переносом (`flex-wrap: wrap` и отступы), без фиксированной ширины («побеждает самый длинный язык»).
5. Скорее всего подойдёт «вторичное действие»: обычная ссылка, а выбранный пункт отличается подчёркиванием и `aria-current`, без нового цвета. В `design.md` добавьте короткий раздел компонента и одно предложение, почему без счётчиков.
6. Три теста HTTP: (а) `?stage=for-adoption` показывает Felix и не показывает Mila; (б) неизвестное значение показывает всех; (в) фильтр без подходящих кошек отвечает `200` с пустым состоянием; плюс проверка, что в разметке есть `nav` с `aria-label` и `aria-current` у выбранной ссылки (по образцу `AccessibilityTest`).

Типичные замечания независимого ревьюера: забытый перевод в сербском файле; фильтр показывает статус, у которого только черновик; пустое состояние без ссылки «Показать всех»; не обновлён `docs/`.
:::
::::

## Как работать с агентом на этом проекте

Разделите работу: **человеку** остаются решения (что показывать при `stage=adopted`, формулировки, дизайн), **агенту** отдайте рутину под вашу проверку. Примеры запросов по шагам:

```text
Шаг 1:  Прочитай service/CatService.kt и service/DisplayHelpers.kt. Напиши новый файл
        service/StageFilter.kt с функциями parseStageFilter, urlKey, stagesPresent, filterByStage
        и тесты в src/test/.../service/StageFilterTest.kt по этим правилам: [критерии].
        Сначала тесты, покажи, что они падают. Ничего больше не меняй.

Шаг 4:  Добавь ключи cat.filter.* в messages.properties, messages_ru.properties и messages_sr.properties (messages_en.properties пустой намеренно). Сербский пиши латиницей.
        Напиши тест, который падает, если ключ есть не во всех файлах. Покажи вывод ./gradlew test.

Ревью:  Ты независимый ревьюер. Прочитай дифф ветки cat-stage-filter против main. Найди: утечку
        черновиков, забытые языки, нарушения docs/design.md, слабые тесты. Не правь, только перечисли.
```

## Самооценка

Оцените себя по таблице, честно. Цель не «четыре из четырёх везде», а увидеть, где вы пока слабее.

| Критерий | Недостаточно | Хорошо | Отлично |
|---|---|---|---|
| **Функция** | работает для счастливого пути | все критерии приёмки, пустое состояние | плюс решения по границам записаны |
| **Тесты** | один тест на путь | сервис, маршрут, доступность; границы | тест полноты переводов; проверена мутациями |
| **Архитектура** | логика в маршруте или представлении | логика в чистых функциях, слои соблюдены | функции вынесены, при необходимости добавлен архитектурный тест |
| **UX** | ссылки есть, вид не продуман | принципы дизайна соблюдены, пустое состояние | проверено на самом длинном языке и в обеих темах |
| **Доступность** | не проверялась | клавиатура, `aria-current`, контраст | проверено скринридером и axe |
| **Безопасность** | не думал | параметр разбирается безопасно, вывод экранируется | черновики не утекают, лояльный разбор |
| **Процесс** | один большой коммит | мелкие коммиты с тестами | ADR записан; ревью агента сопоставлено со своим |
| **ИИ** | вставил результат без проверки | проверил дифф и запуск | превратил найденную ошибку в правило или тест |

:::quiz Проверьте понимание перед сдачей
Q: Почему набор фильтров строится только по опубликованным кошкам?
- [ ] Так проще писать код.
- [x] Иначе наличие фильтра раскрыло бы существование неопубликованной (черновой) записи.
- [ ] Черновики не имеют статуса.
- [ ] Это требование Ktor.
E: Публичная страница не должна зависеть от того, чего посетитель не видит. Это та же логика, что у `CatService.published()`.

Q: Что должен вернуть маршрут для `/ru/cats?stage=nonsense`?
- [ ] 404.
- [ ] 400.
- [x] Страницу со всеми кошками (лояльно к вводу, как ссылка без параметра).
- [ ] Пустой список.
E: Параметр это необязательная настройка вида, а не отдельный ресурс. Неизвестное значение не должно ломать старые ссылки.

Q: Почему ключ в адресе, статус и порядок показа хранятся в одной таблице?
- [ ] Для экономии памяти.
- [x] Чтобы разбор, обратное преобразование и порядок не разошлись при изменении.
- [ ] Так требует Kotlin.
- [ ] Чтобы ускорить фильтрацию.
E: «Один источник правды» для связанных фактов.

Q: Какая мера делает фильтр доступным и без цвета?
- [ ] Красная рамка у выбранного.
- [x] `aria-current` у выбранной ссылки и текстовая подпись навигации.
- [ ] Иконка в цвете акцента.
- [ ] Всплывающая подсказка.
E: Смысл «этот пункт выбран» должен быть доступен программно и не только цветом.

Q: Что лучше всего превратить в автоматическую проверку по итогам проекта?
- [x] «Ключ сообщения есть во всех языковых файлах».
- [x] «Черновик не влияет на набор фильтров».
- [ ] «Кнопка красиво выглядит».
- [ ] «Текст приятно читать».
E: Автоматизируется то, что можно выразить условием «да/нет». Вид и приятность проверяет человек.
:::

## Итоги и что дальше

Если вы дошли сюда, вы прошли путь от `val` и `?.` до функции, которая проходит через все слои реального сайта, написана тестами вперёд, доступна, переведена и записана в документации, и сделали это вместе с агентом, а не вместо себя. Дальше полезно:

- Расширить функцию: фильтр по расположению (`CatLocation`), сочетание фильтров, запоминание выбора (сначала подумайте, нужно ли это, и не нарушает ли «скажи один раз»).
- Сделать архитектурный тест из урока про [архитектуру](lesson:a1-architecture) и добавить его в проект.
- Вынести кэш из трёх репозиториев в общий класс, как в уроке про [рефакторинг](lesson:a4-refactoring).
- Написать скилл для вашей рутины (урок про [скиллы](lesson:ai2-skills)).

Завершите курс [экзаменом](lesson:exam) и [шпаргалкой](lesson:cheatsheet).
