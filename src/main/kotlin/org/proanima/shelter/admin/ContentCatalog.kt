package org.proanima.shelter.admin

import org.proanima.shelter.i18n.ListKey
import org.proanima.shelter.i18n.PlainSetting
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.repository.GUIDE_MARKDOWN_KEY
import org.proanima.shelter.repository.GuideRepository
import org.proanima.shelter.service.ALL_LANGUAGES
import org.proanima.shelter.service.ContentKey
import org.proanima.shelter.views.SiteSettings

// What can be edited on the pages of the site, and how: the list the admin panel's page forms are made from.
// The fields are the texts the pages already have, so the structure of a page (which blocks it has and in what
// order) stays the code's business; the admin panel changes what the blocks say.

enum class PlainFormat { TELEGRAM_NAME, WEB_ADDRESS }

sealed class ContentField(val key: String, val label: String, val hint: String?) {

    // The same in every language, so the form has one field for it, not three.
    open val localized: Boolean get() = true

    // One text in three languages. A short one is a line, a long one a paragraph (the site prints it as one paragraph).
    // placeholders: pieces the sentence must keep, such as "{0}" for the time the site puts in.
    class Text(
        key: String,
        label: String,
        hint: String? = null,
        val maxLength: Int = 300,
        val rows: Int = 1,
        val placeholders: List<String> = emptyList()
    ) : ContentField(key, label, hint)

    // A list kept as one text, one item per line.
    class Lines(
        val list: ListKey,
        label: String,
        hint: String? = null,
        val maxItems: Int = 12,
        val maxItemLength: Int = 300
    ) : ContentField(list.key, label, hint)

    // The guide: the whole page in the small markdown that content/volunteer-guide.md uses.
    class Markdown(key: String, label: String, hint: String? = null, val maxLength: Int = 30_000) : ContentField(key, label, hint)

    // A value that does not depend on the language: a Telegram name, a link.
    class Plain(val setting: PlainSetting, label: String, hint: String? = null, val format: PlainFormat) :
        ContentField(setting.key, label, hint) {
        override val localized: Boolean get() = false
    }
}

// A photo of a page. The setting holds its address; without an edit it is the committed file. Each photo that is
// uploaded gets a new address that starts with namePrefix, so a browser never shows an old picture in its place.
class PagePhoto(val setting: PlainSetting, val namePrefix: String, val title: String, val hint: String)

// showsPhoto: the page's photo is shown at the top of this section (it is changed on a page of its own).
class ContentSection(
    val title: String,
    val note: String? = null,
    val fields: List<ContentField>,
    val showsPhoto: Boolean = false
)

class ContentPage(
    val id: String,
    val title: String,
    val publicPath: String,
    val summary: String,
    val sections: List<ContentSection>,
    val photo: PagePhoto? = null,
    val note: String? = null,
    // The page this page's note points to, if any.
    val noteLinkPageId: String? = null
) {
    val fields: List<ContentField> get() = sections.flatMap { it.fields }

    // Every place in the store that holds something of this page.
    val storageKeys: List<ContentKey> get() = fields.flatMap { it.storageKeys() } + listOfNotNull(photo?.let { ContentKey(it.setting.key) })
}

fun ContentField.storageKeys(): List<ContentKey> =
    if (localized) CONTENT_LANGUAGES.map { ContentKey(key, it) } else listOf(ContentKey(key, ALL_LANGUAGES))

// The name of the form field that holds this field's text in this language (the language is ignored for a
// field that has only one value).
fun ContentField.formName(language: String): String = if (localized) "${key}_$language" else key

// What the site says until somebody edits it, in a language.
class ContentDefaults(private val guide: GuideRepository) {

    fun of(field: ContentField, language: String): String {
        val locale = AppLocale.entries.firstOrNull { it.code == language } ?: AppLocale.default
        return when (field) {
            is ContentField.Text -> shippedMessage(locale, field.key)
            is ContentField.Lines -> field.list.defaultKeys.joinToString("\n") { shippedMessage(locale, it) }
            is ContentField.Markdown -> normalizeMarkdown(guide.findGuideMarkdown(locale).markdown)
            is ContentField.Plain -> field.setting.default
        }
    }

    // A pattern that takes arguments writes a quote as two (MessageFormat); a person reads and types one.
    private fun shippedMessage(locale: AppLocale, key: String): String {
        val pattern = messagesFor(locale).getString(key)
        return if (Regex("""\{\d""").containsMatchIn(pattern)) pattern.replace("''", "'") else pattern
    }
}

fun normalizeMarkdown(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n').trim()

private fun text(key: String, label: String, hint: String? = null, maxLength: Int = 300, rows: Int = 1, placeholders: List<String> = emptyList()) =
    ContentField.Text(key, label, hint, maxLength, rows, placeholders)

private const val ONE_PER_LINE = "Один пункт на строку. Пустые строки пропускаются."
private const val ONE_PARAGRAPH = "Одним абзацем: переносы строк на сайте не сохраняются."

object ContentCatalog {

    private val home = ContentPage(
        id = "home",
        title = "Главная",
        publicPath = "",
        summary = "Заголовок и вступление, фото, чем помогают волонтёры, о приюте.",
        note = "Блоки «Ближайший визит» и «Как присоединиться» показаны и на главной, но правятся на странице «Визиты».",
        noteLinkPageId = "visits",
        photo = PagePhoto(
            SiteSettings.HERO_PHOTO, "page-home-hero", "Фото на главной",
            "Лучше горизонтальное: на сайте оно показывается в рамке 16:10, и всё, что не поместилось, обрезается по краям."
        ),
        sections = listOf(
            ContentSection(
                "Верх страницы",
                fields = listOf(
                    text("home.hero.headline", "Заголовок", maxLength = 120),
                    text("home.hero.lead", "Текст под заголовком", ONE_PARAGRAPH, maxLength = 400, rows = 3),
                    text("home.hero.help", "Кнопка про деньги и вещи", "Надпись на кнопке под текстом: она ведёт на страницу «Как помочь».", maxLength = 120)
                )
            ),
            ContentSection(
                "Фото",
                fields = listOf(
                    text(
                        "home.hero.photo.alt", "Описание фото",
                        "Его зачитывают программы для незрячих и показывает браузер, если фото не загрузилось. Одной фразой: что на снимке.",
                        maxLength = 200
                    )
                ),
                showsPhoto = true
            ),
            ContentSection(
                "Кошки в поиске дома",
                fields = listOf(
                    text("home.cats.title", "Заголовок раздела", maxLength = 120),
                    text("home.link.cats", "Ссылка на каталог", "Под тремя карточками кошек.", maxLength = 80)
                )
            ),
            ContentSection(
                "Чем помогают волонтёры",
                fields = listOf(
                    text("home.volunteers.title", "Заголовок раздела", maxLength = 120),
                    text("home.volunteers.cats.title", "Подзаголовок про кошек", maxLength = 80),
                    ContentField.Lines(SiteSettings.VOLUNTEER_CAT_TASKS, "Что делают с кошками", ONE_PER_LINE),
                    text("home.volunteers.dogs.title", "Подзаголовок про собак", maxLength = 80),
                    ContentField.Lines(SiteSettings.VOLUNTEER_DOG_TASKS, "Что делают с собаками", ONE_PER_LINE)
                )
            ),
            ContentSection(
                "О приюте",
                fields = listOf(
                    text("home.about.title", "Заголовок раздела", maxLength = 120),
                    text("home.about.text", "О приюте", ONE_PARAGRAPH, maxLength = 600, rows = 4),
                    text("home.about.quote", "Цитата", ONE_PARAGRAPH, maxLength = 600, rows = 4),
                    text("home.about.quote.author", "Кто сказал", "Имя и кем человек приходится приюту.", maxLength = 150)
                )
            ),
            ContentSection(
                "Для поиска и ссылок",
                fields = listOf(
                    text("home.page.title", "Название страницы", "Видно на вкладке браузера, в поиске и в предпросмотре ссылки.", maxLength = 120),
                    text("home.page.description", "Описание страницы", "Одно-два предложения для поиска и предпросмотра ссылки в мессенджерах.", maxLength = 300, rows = 3)
                )
            )
        )
    )

    private val visits = ContentPage(
        id = "visits",
        title = "Визиты",
        publicPath = "/visits",
        summary = "Ближайший визит, как присоединиться, контакты координаторов. Блоки видны и на главной.",
        note = "Дату и время ближайшего выезда сайт считает сам по расписанию (среда в 9:00, суббота в 10:00). " +
            "Расписание и отмену выездов пока меняет разработчик: здесь правятся только тексты.",
        sections = listOf(
            ContentSection(
                "Заголовок страницы",
                fields = listOf(text("visit.page.upcoming.title", "Заголовок", maxLength = 120))
            ),
            ContentSection(
                "Ближайший визит",
                note = "Этот блок есть и на главной.",
                fields = listOf(
                    text("home.nextVisit.title", "Заголовок блока", maxLength = 120),
                    text(
                        "home.nextVisit.departure", "Время выезда",
                        "Оставьте в тексте {0}: сайт сам подставит туда время выезда.",
                        maxLength = 200, placeholders = listOf("{0}")
                    ),
                    text("home.nextVisit.chat", "Про расписание и запись", ONE_PARAGRAPH, maxLength = 400, rows = 3),
                    text("home.nextVisit.cta", "Кнопка", "Ведёт к блоку «Как присоединиться».", maxLength = 80)
                )
            ),
            ContentSection(
                "Как присоединиться",
                note = "Этот блок есть и на главной. Шаги нумеруются сами.",
                fields = listOf(
                    text("home.join.title", "Заголовок блока", maxLength = 120),
                    text("home.join.intro", "Фраза перед шагами", maxLength = 200),
                    text(
                        "home.join.step1", "Первый шаг: гайд",
                        "Оставьте в тексте {link}: на этом месте будет ссылка на гайд. Слова для ссылки — в следующем поле.",
                        maxLength = 300, placeholders = listOf("{link}")
                    ),
                    text("home.join.step1.link", "Первый шаг: слова для ссылки", maxLength = 80),
                    text(
                        "home.join.step2", "Второй шаг: написать координатору (кошки)",
                        "После текста сайт сам добавит имена и ссылки координаторов из раздела ниже.",
                        maxLength = 300
                    ),
                    text(
                        "home.join.dogs", "Третий шаг: собаки",
                        "После текста сайт сам добавит имя и ссылку модератора чата по собакам.",
                        maxLength = 300
                    ),
                    text("home.join.step3", "Последний шаг: выбрать визит", maxLength = 300)
                )
            ),
            ContentSection(
                "Контакты координаторов",
                note = "Показываются в шагах «Как присоединиться»: имя стоит в тексте, а ссылка ведёт в Telegram.",
                fields = listOf(
                    text("home.contact.nata", "Первый координатор кошек: имя", maxLength = 60),
                    ContentField.Plain(
                        SiteSettings.NATA_TELEGRAM, "Первый координатор кошек: Telegram",
                        "Имя пользователя без @, например Nata_Rogava.", PlainFormat.TELEGRAM_NAME
                    ),
                    text("home.contact.alina", "Второй координатор кошек: имя", maxLength = 60),
                    ContentField.Plain(
                        SiteSettings.ALINA_TELEGRAM, "Второй координатор кошек: Telegram",
                        "Имя пользователя без @.", PlainFormat.TELEGRAM_NAME
                    ),
                    text("home.contact.dasha", "Модератор чата по собакам: имя", maxLength = 60),
                    ContentField.Plain(
                        SiteSettings.DASHA_TELEGRAM_NAME, "Модератор чата по собакам: Telegram",
                        "Имя пользователя без @.", PlainFormat.TELEGRAM_NAME
                    )
                )
            )
        )
    )

    private val help = ContentPage(
        id = "help",
        title = "Как помочь",
        publicPath = "/help",
        summary = "Кнопка доната, что нужно приюту, рассказ о Своре.",
        sections = listOf(
            ContentSection(
                "Верх страницы",
                fields = listOf(
                    text("help.title", "Заголовок страницы", maxLength = 120),
                    text("help.intro", "Вступление", maxLength = 300, rows = 2),
                    text("help.link.donate", "Надпись на кнопке доната", maxLength = 80),
                    ContentField.Plain(
                        SiteSettings.DONATE_URL, "Адрес кнопки доната",
                        "Ссылка на сбор или на страницу со ссылками. Должна начинаться с https://.", PlainFormat.WEB_ADDRESS
                    )
                )
            ),
            ContentSection(
                "Что нужно приюту",
                fields = listOf(
                    text("help.needs", "Фраза перед списком", maxLength = 120),
                    ContentField.Lines(SiteSettings.HELP_NEEDS, "Список", ONE_PER_LINE),
                    text("help.contact", "Фраза про координаторов", "Стоит перед ссылкой, после ссылки сайт ставит точку.", maxLength = 120),
                    text("help.contact.link", "Слово-ссылка", "Ведёт к контактам на главной.", maxLength = 60)
                )
            ),
            ContentSection(
                "Кто такая Свора",
                fields = listOf(
                    text("help.svora.title", "Заголовок раздела", maxLength = 120),
                    text("help.svora.text", "Рассказ", maxLength = 300, rows = 2),
                    ContentField.Lines(SiteSettings.SVORA_ACTIVITIES, "Чем занимается Свора", ONE_PER_LINE)
                )
            ),
            ContentSection(
                "Для поиска и ссылок",
                fields = listOf(
                    text("help.page.description", "Описание страницы", "Одно-два предложения для поиска и предпросмотра ссылки в мессенджерах.", maxLength = 300, rows = 3)
                )
            )
        )
    )

    private val guide = ContentPage(
        id = "guide",
        title = "Гайд волонтёра",
        publicPath = "/guide",
        summary = "Весь текст гайда на трёх языках и фото над ним.",
        photo = PagePhoto(
            SiteSettings.GUIDE_PHOTO, "page-guide", "Фото в гайде",
            "Лучше горизонтальное: на сайте оно показывается в рамке 16:10, и всё, что не поместилось, обрезается по краям."
        ),
        sections = listOf(
            ContentSection(
                "Фото",
                fields = listOf(
                    text(
                        "guide.photo.alt", "Описание фото",
                        "Его зачитывают программы для незрячих и показывает браузер, если фото не загрузилось. Одной фразой: что на снимке.",
                        maxLength = 200
                    )
                ),
                showsPhoto = true
            ),
            ContentSection(
                "Текст гайда",
                fields = listOf(
                    ContentField.Markdown(
                        GUIDE_MARKDOWN_KEY, "Гайд",
                        "Каждый язык — отдельный текст. Правила разметки — в подсказке под полем."
                    )
                )
            )
        )
    )

    val pages: List<ContentPage> = listOf(home, visits, guide, help)

    fun find(id: String?): ContentPage? = pages.firstOrNull { it.id == id }
}
