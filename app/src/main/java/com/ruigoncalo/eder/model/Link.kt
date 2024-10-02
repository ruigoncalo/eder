package com.ruigoncalo.eder.model

data class Link(
    val text: String,
    val range: IntRange,
    val url: String,
    val isDeleted: Boolean = false
) {
    fun isSame(compareText: String, start: Int, end: Int): Boolean =
        text == compareText && range.first == start && range.last == end

    fun isSame(link: Link): Boolean =
        text == link.text && range.first == link.range.first && range.last == link.range.last
}