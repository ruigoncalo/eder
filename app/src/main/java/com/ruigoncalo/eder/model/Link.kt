package com.ruigoncalo.eder.model

import java.util.UUID

data class Link(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val range: IntRange,
    val url: String,
    val isDeleted: Boolean = false
) {
    fun isLink(linkText: String, linkRange: IntRange): Boolean =
        (text == linkText && range == linkRange) ||
                (range.first == linkRange.first && linkText.startsWith(text))
}