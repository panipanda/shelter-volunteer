fun displayCatName(name: String?): String {
    return name ?: "Unnamed"
}

fun displayPhotoUrl(photoUrl: String?): String {
    return photoUrl ?: "/images/default-cat.jpg"
}

fun displayCatAge(age: Int?): String {
    return if (age == null) {
        "Age unknown"
    } else if (age == 1) {
        "1 year old"
    } else {
        "$age years old"
    }
}

fun displayCatAvailability(isAvailable: Boolean): String {
    return if (isAvailable) {
        "Available for adoption"
    } else {
        "Not available for adoption"
    }
}

fun displayVisitAvailability(status: VisitStatus, freePlaces: Int?): String {
    return if (status == VisitStatus.CANCELLED) {
        "Visit cancelled"
    } else if (status == VisitStatus.TENTATIVE) {
        "Details are being updated"
    } else if (status == VisitStatus.COMPLETED) {
        "Visit completed"
    } else if (status == VisitStatus.FULL) {
        "No places left"
    } else if (freePlaces == null) {
        "Availability is being updated"
    } else if (freePlaces > 0) {
        "Free places: $freePlaces"
    } else {
        "No places left"
    }
}