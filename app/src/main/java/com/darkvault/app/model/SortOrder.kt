package com.darkvault.app.model

enum class SortOrder(val label: String) {
    NAME_ASC("Name A–Z"),
    NAME_DESC("Name Z–A"),
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    SIZE_DESC("Largest first"),
    SIZE_ASC("Smallest first"),
    TYPE_ASC("Type")
}

enum class FilterType(val label: String) {
    ALL("All"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    FOLDERS("Folders")
}
