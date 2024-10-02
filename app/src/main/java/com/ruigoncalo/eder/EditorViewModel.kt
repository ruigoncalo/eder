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

    private val _uiEvent = MutableStateFlow<UiEvent>(None)
    val uiEvent: StateFlow<UiEvent> = _uiEvent

    fun onSaveLink(link: Link, linkText: String, linkUrl: String, currentText: String) {
        links.firstOrNull { it.id == link.id }?.let {
            val updatedLink = it.copy(text = linkText, url = linkUrl)
            links[links.indexOf(it)] = updatedLink
        }

        val updatedText = currentText.replace(link.text, linkText)

        _uiEvent.update { UiEvent.TextUpdate(updatedText) }
    }

    fun onEditLink(linkId: String) {
        _uiEvent.update { None }
        Log.d("Test", "Edit link = ${getLink(linkId)}")
        getLink(linkId)?.let { link ->
            _uiEvent.update { OpenBottomSheet(link) }
        }
    }

    fun onOpenLink(linkId: String) {
        _uiEvent.update { None }
        getLink(linkId)?.let { link ->
            _uiEvent.update { UiEvent.OpenBrowser(link.url) }
        }
    }

    fun onRemoveLink(link: Link) {
        links.firstOrNull { it.id == link.id }?.let {
            val updatedLink = it.copy(isDeleted = true)
            links[links.indexOf(it)] = updatedLink
        }

        _uiEvent.update { HideBottomSheet }
    }

    fun buildAnnotatedStringWithLinks(text: String): AnnotatedString {
        val annotatedString = buildAnnotatedString {
            val activeLinks = links.filter { !it.isDeleted }

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

    private fun onTextLinkMatch(text: String, range: IntRange): Link {
        val savedLink = findLink(text, range)

        Log.d("Test", "Try to match $text in $range. Saved link = $savedLink")
        return if (savedLink == null) {
            val link = addLink(text = text, range = range, url = text)
            Log.d("Test", "So adding new link $link")
            link
        } else {
            if (savedLink.text != text) { // link text has changed (ex: from "site.c" to "site.co")
                val updatedLink = savedLink.copy(text = text, range = range)
                links[links.indexOf(savedLink)] = updatedLink
                updatedLink
            } else {
                savedLink
            }
        }
    }

    private fun addLink(text: String, range: IntRange, url: String): Link {
        val link = Link(text = text, range = range, url = url)
        links.add(link)
        return link
    }

    private fun removeLink(id: String) {
        links.firstOrNull { it.id == id }
            ?.let { links.remove(it) }
    }

    private fun getLink(id: String): Link? =
        links.firstOrNull { it.id == id }

    private fun findLink(text: String, range: IntRange): Link? =
        links.firstOrNull { it.isLink(text, range) }
}

sealed class UiEvent {

    data object None : UiEvent()

    data object HideBottomSheet : UiEvent()

    data class OpenBottomSheet(val link: Link) : UiEvent()

    data class TextUpdate(val text: String) : UiEvent()

    data class OpenBrowser(val url: String) : UiEvent()
}