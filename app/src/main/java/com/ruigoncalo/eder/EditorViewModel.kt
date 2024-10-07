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

    fun onSaveLink(link: Link, updatedText: String, updatedUrl: String, currentText: String) {
        links.firstOrNull { it.isSame(link) }?.let {
            val editedLink = it.copy(
                text = updatedText,
                url = updatedUrl,
                range = IntRange(it.range.first, it.range.first + updatedText.length),
                isEdited = true
            )
            links[links.indexOf(it)] = editedLink

            val refreshedText = currentText.replace(link.text, updatedText)
            _uiEvent.update {
                UiEvent.TextUpdate(
                    refreshedText,
                    caretPosition = editedLink.range.last
                )
            }
        }
    }

    fun onEditLink(text: String, start: Int) {
        Log.d("Test", "Looking for link with start at $start and text $text")
        links.firstOrNull { it.isSame(text, start) }
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

                val savedLink = links.firstOrNull { it.range.first == matchResult!!.range.first }

                if (savedLink != null) {
                    if(savedLink.text != matchResult.value) {
                        val updatedLink = Link(
                            text = if(savedLink.isEdited) savedLink.text else matchResult.value,
                            range = if(savedLink.isEdited) savedLink.range else matchResult.range,
                            url = if(savedLink.isEdited) savedLink.url else matchResult.value
                        )
                        links[links.indexOf(savedLink)] = updatedLink
                        pushStringAnnotation(tag = LINK_TAG, annotation = updatedLink.url)
                    } else {
                        pushStringAnnotation(tag = LINK_TAG, annotation = savedLink.url)
                    }
                } else {
                    val newLink = Link(
                        text = matchResult.value,
                        range = matchResult.range,
                        url = matchResult.value
                    )

                    links.add(newLink)
                    pushStringAnnotation(tag = LINK_TAG, annotation = newLink.url)
                }

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