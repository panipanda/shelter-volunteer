package org.proanima.shelter.views

import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.li
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.title
import kotlinx.html.ul
import org.proanima.shelter.model.AppLocale

// lang намеренно "en", а не locale.code: текст гайда пока захардкожен на английском
// независимо от локали (content/volunteer-guide.md ещё не подключён), lang должен
// отражать реальный язык контента, а не язык роута
fun HTML.guidePage(locale: AppLocale) {
    lang = "en"
    head {
        meta(charset = "UTF-8")
        title { +"Volunteer guide" }
    }
    body {
        navigation(locale)

        main {
            h1 { +"Volunteer guide" }

            p {
                +(
                    "This page is a short guide for volunteers who want to help with cats at Pro Anima shelter. " +
                        "It explains how to prepare for a visit, what to bring, and how signup works."
                    )
            }

            section {
                h2 { +"Before the visit" }
                ul {
                    li { +"Check the current visit availability before planning your trip." }
                    li { +"Make sure you know the visit date, time, and meeting point." }
                    li { +"If you're not feeling well, it's better to stay home, join us the other time." }
                    li { +"Wear clothes that can get dirty or take some that you can change into" }
                }
            }

            section {
                h2 { +"What to bring" }
                ul {
                    li { +"Comfortable clothes and closed shoes." }
                    li { +"Water for yourself." }
                    li { +"Work gloves, if you have them." }
                    li { +"Cat and dog treats." }
                }
            }

            section {
                h2 { +"During the visit" }
                ul {
                    li { +"Follow coordinator instructions." }
                    li { +"Follow the rules when opening doors to avoid accidents." }
                    li { +"Be gentle and patient with the animals, they may be scared or stressed." }
                    li { +"Report anything unusual: injuries, sickness signs, stress, aggression, or escaped animals." }
                    li { +"Ask questions if you're unsure about something, coordinators are there to help you." }
                    li { +"Have fun and enjoy your time with the cats!" }
                }
            }

            section {
                h2 { +"How signup works" }
                p {
                    +(
                        "Visit signup is coordinated manually. Check the volunteer visits page, then follow the " +
                            "signup instructions shown for the selected visit."
                        )
                }
                p {
                    +"If a visit is full or closed, feel free to check back later or contact a coordinator."
                }
            }
        }
    }
}
