package org.proanima.shelter.views

fun renderGuidePage(): String = """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <title>Volunteer guide</title>
    </head>
    <body>
        ${renderNavigation()}

        <main>
            <h1>Volunteer guide</h1>

            <p>
                This page is a short guide for volunteers who want to help with cats at Pro Anima shelter.
                It explains how to prepare for a visit, what to bring, and how signup works.
            </p>

            <section>
                <h2>Before the visit</h2>
                <ul>
                    <li>Check the current visit availability before planning your trip.</li>
                    <li>Make sure you know the visit date, time, and meeting point.</li>
                    <li>If you're not feeling well, it's better to stay home, join us the other time.</li>
                    <li>Wear clothes that can get dirty or take some that you can change into</li>
                </ul>
            </section>

            <section>
                <h2>What to bring</h2>
                <ul>
                    <li>Comfortable clothes and closed shoes.</li>
                    <li>Water for yourself.</li>
                    <li>Work gloves, if you have them.</li>
                    <li>Cat and dog treats.</li>
                </ul>
            </section>

            <section>
                <h2>During the visit</h2>
                <ul>
                    <li>Follow coordinator instructions.</li>
                    <li>Follow the rules when opening doors to avoid accidents.</li>
                    <li>Be gentle and patient with the animals, they may be scared or stressed.</li>
                    <li>Report anything unusual: injuries, sickness signs, stress, aggression, or escaped animals.</li>
                    <li>Ask questions if you're unsure about something, coordinators are there to help you.</li>
                    <li>Have fun and enjoy your time with the cats!</li>
                </ul>
            </section>

            <section>
                <h2>How signup works</h2>
                <p>
                    Visit signup is coordinated manually. Check the volunteer visits page, then follow the signup
                    instructions shown for the selected visit.
                </p>
                <p>
                    If a visit is full or closed, feel free to check back later or contact a coordinator.
                </p>
            </section>
        </main>
    </body>
    </html>
""".trimIndent()