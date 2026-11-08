package org.proanima.shelter.views

import org.proanima.shelter.i18n.ListKey
import org.proanima.shelter.i18n.PlainSetting

// The pieces of the pages that can be edited in the admin panel but are not a single message: lists (one
// edited text, one item per line) and values that are the same in every language. The views and the admin panel
// both read them from here, so a page and its edit form cannot disagree about what a key means.
object SiteSettings {

    val VOLUNTEER_CAT_TASKS = ListKey(
        "home.volunteers.cats.items",
        listOf("home.volunteers.cleaning.text", "home.volunteers.feeding.text", "home.volunteers.socialization.text")
    )

    val VOLUNTEER_DOG_TASKS = ListKey(
        "home.volunteers.dogs.items",
        listOf("home.volunteers.dogs.walking", "home.volunteers.dogs.cleaning", "home.volunteers.dogs.socialization")
    )

    val HELP_NEEDS = ListKey(
        "help.needs.items",
        listOf("help.item.donations", "help.item.food", "help.item.catThings", "help.item.supplies", "help.item.foster")
    )

    val SVORA_ACTIVITIES = ListKey(
        "help.svora.items",
        listOf("help.svora.item.trips", "help.svora.item.supplies", "help.svora.item.donations", "help.svora.item.events")
    )

    // Telegram names without the @ (t.me/<name>).
    val NATA_TELEGRAM = PlainSetting("contact.nata.telegram", "Nata_Rogava")
    val ALINA_TELEGRAM = PlainSetting("contact.alina.telegram", "Trikcsy")
    val DASHA_TELEGRAM_NAME = PlainSetting("contact.dasha.telegram", DASHA_TELEGRAM)

    val DONATE_URL = PlainSetting("help.donate.url", SVORA_LINKTREE_URL)

    // Addresses of the two photos of the pages. A photo put in through the admin panel has a new address, so the
    // setting holds it; without one the committed file is used.
    val HERO_PHOTO = PlainSetting("image.home.hero", "/images/home-hero.jpg")
    val GUIDE_PHOTO = PlainSetting("image.guide.photo", "/images/home-guide.jpg")
}
