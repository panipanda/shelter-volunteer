package org.proanima.shelter.service

import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.Dog
import java.io.File

private const val IMAGES_URL_PREFIX = "/images/"

// Checks the content files for mistakes that would otherwise show up only when someone opens the
// affected page: duplicate ids or slugs (the second pet becomes unreachable), a month outside 1..12,
// and photos that are not on disk or have no card copy. Returns human-readable problems; an empty list
// means the data is fine.
fun validatePets(cats: List<Cat>, dogs: List<Dog>, imagesDir: File): List<String> =
    validatePets(cats, dogs, imagesDir.path) { name -> File(imagesDir, name).isFile }

// The same checks for photos kept somewhere other than a folder (the database): `photosAt` names the place
// in the messages, `hasPhoto` says whether a file name (the part of the URL after /images/) is there.
fun validatePets(cats: List<Cat>, dogs: List<Dog>, photosAt: String, hasPhoto: (String) -> Boolean): List<String> {
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
        problems += missingPhotos("cats.json", "cat ${cat.id}", cat.photoUrls, photosAt, hasPhoto)
    }
    dogs.forEach { dog ->
        if (dog.birthMonth != null && dog.birthMonth !in 1..12) {
            problems += "dogs.json: dog ${dog.id} has birthMonth ${dog.birthMonth}, expected 1..12"
        }
        if (dog.birthMonth != null && dog.birthYear == null) {
            problems += "dogs.json: dog ${dog.id} has birthMonth but no birthYear"
        }
        problems += missingPhotos("dogs.json", "dog ${dog.id}", dog.photoUrls, photosAt, hasPhoto)
    }

    return problems
}

private fun duplicates(file: String, field: String, values: List<String>): List<String> =
    values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        .map { "$file: duplicate $field \"$it\"" }

private fun missingPhotos(
    file: String,
    owner: String,
    photoUrls: List<String>,
    photosAt: String,
    hasPhoto: (String) -> Boolean
): List<String> =
    photoUrls.mapNotNull { url ->
        when {
            !url.startsWith(IMAGES_URL_PREFIX) -> "$file: $owner has photo \"$url\" outside $IMAGES_URL_PREFIX"
            !hasPhoto(url.removePrefix(IMAGES_URL_PREFIX)) -> "$file: $owner photo \"$url\" is not in $photosAt"
            !hasCardCopy(url, hasPhoto) ->
                "$file: $owner photo \"$url\" has no card copy \"${cardPhotoUrl(url)}\" in $photosAt; run tools/make-card-images.py"
            else -> null
        }
    }

private fun hasCardCopy(url: String, hasPhoto: (String) -> Boolean): Boolean {
    val card = cardPhotoUrl(url)
    return card == url || hasPhoto(card.removePrefix(IMAGES_URL_PREFIX))
}
