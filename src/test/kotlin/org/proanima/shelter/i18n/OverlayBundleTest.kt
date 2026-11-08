package org.proanima.shelter.i18n

import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.service.ContentKey
import org.proanima.shelter.service.SiteContent
import org.proanima.shelter.views.SiteSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OverlayBundleTest {

    private fun content(vararg entries: Pair<ContentKey, String>) = SiteContent(mapOf(*entries))

    @Test
    fun `a site nobody has edited reads exactly as the properties files do`() {
        val bundle = SiteContent.EMPTY.bundle(AppLocale.RU)

        assertSame(messagesFor(AppLocale.RU), bundle)
    }

    @Test
    fun `an edited key replaces the shipped text in its own language only`() {
        val content = content(ContentKey("home.hero.headline", "ru") to "Новый заголовок")

        assertEquals("Новый заголовок", content.bundle(AppLocale.RU).t("home.hero.headline"))
        assertEquals(messagesFor(AppLocale.EN).t("home.hero.headline"), content.bundle(AppLocale.EN).t("home.hero.headline"))
        assertEquals(messagesFor(AppLocale.SR).t("home.hero.headline"), content.bundle(AppLocale.SR).t("home.hero.headline"))
    }

    @Test
    fun `keys that were not edited are the shipped texts, Serbian and English included`() {
        val content = content(ContentKey("home.hero.headline", "ru") to "Новый заголовок")

        assertEquals(messagesFor(AppLocale.RU).t("home.about.title"), content.bundle(AppLocale.RU).t("home.about.title"))
        assertEquals(messagesFor(AppLocale.SR).t("cat.age.unknown"), content.bundle(AppLocale.SR).t("cat.age.unknown"))
        assertEquals("Age unknown", content.bundle(AppLocale.EN).t("cat.age.unknown"))
    }

    @Test
    fun `an apostrophe in a text that takes an argument does not eat the rest of the sentence`() {
        val content = content(ContentKey("home.nextVisit.departure", "en") to "We're leaving at {0} sharp.")

        assertEquals("We're leaving at 9:00 sharp.", content.bundle(AppLocale.EN).t("home.nextVisit.departure", "9:00"))
    }

    @Test
    fun `an apostrophe in a text without arguments stays one apostrophe`() {
        val content = content(ContentKey("home.about.text", "en") to "It's a shelter.")

        assertEquals("It's a shelter.", content.bundle(AppLocale.EN).t("home.about.text"))
    }

    @Test
    fun `a list is the shipped messages until it is edited, then one item per line`() {
        val shipped = messagesFor(AppLocale.RU)
        assertEquals(
            SiteSettings.HELP_NEEDS.defaultKeys.map { shipped.t(it) },
            SiteContent.EMPTY.bundle(AppLocale.RU).lines(SiteSettings.HELP_NEEDS)
        )

        val edited = content(ContentKey(SiteSettings.HELP_NEEDS.key, "ru") to "корм\n\n  лежанки  \nмиски")
        assertEquals(listOf("корм", "лежанки", "миски"), edited.bundle(AppLocale.RU).lines(SiteSettings.HELP_NEEDS))
        // The other languages keep their own shipped lists.
        assertEquals(
            SiteSettings.HELP_NEEDS.defaultKeys.map { messagesFor(AppLocale.EN).t(it) },
            edited.bundle(AppLocale.EN).lines(SiteSettings.HELP_NEEDS)
        )
    }

    @Test
    fun `a list edited into nothing falls back to the shipped one`() {
        val edited = content(ContentKey(SiteSettings.HELP_NEEDS.key, "ru") to " \n ")

        assertEquals(
            SiteSettings.HELP_NEEDS.defaultKeys.map { messagesFor(AppLocale.RU).t(it) },
            edited.bundle(AppLocale.RU).lines(SiteSettings.HELP_NEEDS)
        )
    }

    @Test
    fun `a setting is the same in every language and has a default`() {
        assertEquals("Nata_Rogava", SiteContent.EMPTY.bundle(AppLocale.EN).plain(SiteSettings.NATA_TELEGRAM))

        val edited = content(ContentKey(SiteSettings.NATA_TELEGRAM.key) to "New_Name")
        AppLocale.entries.forEach { locale ->
            assertEquals("New_Name", edited.bundle(locale).plain(SiteSettings.NATA_TELEGRAM), locale.code)
        }
    }

    @Test
    fun `the bundle keeps the language of the one it covers`() {
        val bundle = content(ContentKey("home.hero.headline", "sr") to "Naslov").bundle(AppLocale.SR)

        assertEquals("sr", bundle.locale.language)
        assertTrue(bundle.containsKey("home.hero.headline"))
        assertTrue(bundle.containsKey("nav.cats"))
    }
}
