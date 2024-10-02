package com.ruigoncalo.eder

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.ViewModel
import com.ruigoncalo.eder.UiEvent.HideBottomSheet
import com.ruigoncalo.eder.UiEvent.None
import com.ruigoncalo.eder.UiEvent.OpenBottomSheet
import com.ruigoncalo.eder.model.Link
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

const val LINK_TAG = "link"
private const val regex =
    "(https?://)?(www\\.)?" +
            "[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_+.~#?&//=]*)"

class EditorViewModel : ViewModel() {

    private val links = mutableListOf<Link>()
    private val editedLinks = mutableListOf<Link>()

    private val _uiEvent = MutableStateFlow<UiEvent>(None)
    val uiEvent: StateFlow<UiEvent> = _uiEvent

    fun onSaveLink(link: Link, linkText: String, linkUrl: String, currentText: String) {
        links.firstOrNull { it.isSame(link) }?.let {
            val editedLink = it.copy(
                text = linkText,
                url = linkUrl,
                range = IntRange(it.range.first, it.range.first + linkText.length)
            )
            editedLinks.add(editedLink)

            val updatedText = currentText.replace(link.text, linkText)
            _uiEvent.update { UiEvent.TextUpdate(updatedText, caretPosition = editedLink.range.last) }
        }
    }

    fun onEditLink(text: String, start: Int, end: Int) {
        Log.d("Test", "Looking for link = ($start, $end) $text")
        links.firstOrNull { it.isSame(text, start, end) }
            ?.let { link ->
                Log.d("Test", "Found $link")
                _uiEvent.update { OpenBottomSheet(link) }
            }
            ?: Log.d("Test", "Not found")
    }

    fun onRemoveLink(link: Link) {
        links.firstOrNull { it.isSame(link) }?.let {
            val updatedLink = it.copy(isDeleted = true)
            links[links.indexOf(it)] = updatedLink
        }

        _uiEvent.update { HideBottomSheet }
    }

    fun buildAnnotatedStringWithLinks(text: String): AnnotatedString {
        val annotatedString = buildAnnotatedString {
            val activeLinks = editedLinks.filter { !it.isDeleted }

            val dynamicRegex = if (activeLinks.isNotEmpty()) {
                val linkTextsRegex = activeLinks
                    .joinToString(separator = "|") { "\\b${it.text}\\b" }
                Regex("$regex|$linkTextsRegex")
            } else {
                Regex(regex)
            }
            Log.d("Test", "Dynamic regex = ${dynamicRegex.pattern}")

            var matchResult = dynamicRegex.find(text)
            var start = 0

            while (matchResult != null) {
                // Add non-link text
                append(text.substring(start, matchResult.range.first))

                // Add link text and highlight
                pushStringAnnotation(tag = LINK_TAG, annotation = matchResult.value)
                withStyle(
                    style = SpanStyle(
                        color = Color.Blue,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(matchResult!!.value)
                }

                pop()

                start = matchResult.range.last + 1
                matchResult = dynamicRegex.find(text, start)
            }

            // Add remaining text
            append(text.substring(start))
        }

        links.clear()
        annotatedString.getStringAnnotations(LINK_TAG, 0, text.length)
            .forEach { annotation ->
                val link = Link(
                    text = annotation.item,
                    range = IntRange(annotation.start, annotation.end),
                    url = annotation.item
                )
                links.add(link)
            }

        Log.d(
            "Test",
            "Annotated string: ${annotatedString.getStringAnnotations(LINK_TAG, 0, text.length)}"
        )
        return annotatedString
    }

    private fun addLink(text: String, range: IntRange, url: String): Link {
        val link = Link(text = text, range = range, url = url)
        links.add(link)
        return link
    }

    private fun removeLink(link: Link) {
        links.firstOrNull { it.isSame(link) }
            ?.let { links.remove(it) }
    }
}

sealed class UiEvent {

    data object None : UiEvent()

    data object HideBottomSheet : UiEvent()

    data class OpenBottomSheet(val link: Link) : UiEvent()

    data class TextUpdate(val text: String, val caretPosition: Int) : UiEvent()
}