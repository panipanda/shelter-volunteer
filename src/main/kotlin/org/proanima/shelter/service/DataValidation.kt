package org.proanima.shelter.service

import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.Dog
import java.io.File

private const val IMAGES_URL_PREFIX = "/images/"

// Checks the content files for mistakes that would otherwise show up only when someone opens the
// affected page: duplicate ids or slugs (the second pet becomes unreachable), a month outside 1..12,
// and photos that are not on disk. Returns human-readable problems; an empty list means the data is fine.
fun validatePets(cats: List<Cat>, dogs: List<Dog>, imagesDir: File): List<String> {
    val problems = mutableListOf<String>()

    problems += duplicates("cats.json", "id", cats.map { it.id.toString() })
    problems += duplicates("cats.json", "slug", cats.map { petSlug(it.name?.en, it.id) })
    problems += duplicates("dogs.json", "id", dogs.map { it.id.toString() })
    problems += duplicates("dogs.json", "slug", dogs.map { petSlug(it.name.en, it.id) })

    cats.forEach { cat ->
        if (cat.birthMonth != null && cat.birthMonth !in 1..12) {
            problems += "cats.json: cat ${cat.id} has birthMonth ${cat.birthMonth}, expected 1..12"
        }
        if (cat.birthMonth != null && cat.birthYear == null) {
            problems += "cats.json: cat ${cat.id} has birthMonth but no birthYear"
        }
        problems += missingPhotos("cats.json", "cat ${cat.id}", cat.photoUrls, imagesDir)
    }
    dogs.forEach { dog ->
        problems += missingPhotos("dogs.json", "dog ${dog.id}", dog.photoUrls, imagesDir)
    }

    return problems
}

private fun duplicates(file: String, field: String, values: List<String>): List<String> =
    values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        .map { "$file: duplicate $field \"$it\"" }

private fun missingPhotos(file: String, owner: String, photoUrls: List<String>, imagesDir: File): List<String> =
    photoUrls.mapNotNull { url ->
        when {
            !url.startsWith(IMAGES_URL_PREFIX) -> "$file: $owner has photo \"$url\" outside $IMAGES_URL_PREFIX"
            !File(imagesDir, url.removePrefix(IMAGES_URL_PREFIX)).isFile -> "$file: $owner photo \"$url\" is not in ${imagesDir.path}"
            else -> null
        }
    }
