package com.ruigoncalo.eder.model

import java.util.UUID

data class Link(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val range: IntRange,
    val url: String,
    val isDeleted: Boolean = false
) {
    fun isLink(text: String, range: IntRange): Boolean =
        this.text == text && this.range == range
}