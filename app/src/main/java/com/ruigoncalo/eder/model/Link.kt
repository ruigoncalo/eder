package com.ruigoncalo.eder.model

data class Link(
    val text: String,
    val range: IntRange,
    val url: String,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false
) {
    fun isSame(compareText: String, start: Int): Boolean =
        text == compareText && range.first == start

    fun isSame(link: Link): Boolean =
        text == link.text && range.first == link.range.first
}