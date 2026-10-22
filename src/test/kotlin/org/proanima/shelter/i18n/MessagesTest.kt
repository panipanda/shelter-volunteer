package org.proanima.shelter.i18n

import org.proanima.shelter.model.AppLocale
import java.util.Locale
import java.util.ResourceBundle
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MessagesTest {

    private val originalDefault = Locale.getDefault()

    // ResourceBundle caches lookups per default locale, so the cache is cleared around each test.
    @BeforeTest
    fun setUp() = ResourceBundle.clearCache()

    @AfterTest
    fun tearDown() {
        Locale.setDefault(originalDefault)
        ResourceBundle.clearCache()
    }

    @Test
    fun `English messages do not depend on the JVM default locale`() {
        Locale.setDefault(Locale.forLanguageTag("ru"))

        assertEquals("Age unknown", messagesFor(AppLocale.EN).t("cat.age.unknown"))
    }

    @Test
    fun `Russian and Serbian messages are still served in their own language`() {
        Locale.setDefault(Locale.forLanguageTag("en"))

        assertEquals("Возраст неизвестен", messagesFor(AppLocale.RU).t("cat.age.unknown"))
        assertEquals("Uzrast nepoznat", messagesFor(AppLocale.SR).t("cat.age.unknown"))
    }
}
