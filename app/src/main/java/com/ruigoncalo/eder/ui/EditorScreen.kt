package com.ruigoncalo.eder.ui

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ruigoncalo.eder.ui.theme.Typography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen() {
    val snackbarHostState = remember { SnackbarHostState() }
    val isEditing = remember { mutableStateOf(false) }

    val topBarTitle = if (isEditing.value) "Edit Mode" else "View Mode"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = topBarTitle) },
                actions = {
                    if (isEditing.value) {
                        IconButton(
                            onClick = { isEditing.value = false },
                            content = { Icon(Icons.Default.Done, contentDescription = "Done") }
                        )
                    } else {
                        IconButton(
                            onClick = { isEditing.value = true },
                            content = { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        Column(modifier = Modifier.padding(it)) {
            Box(modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (isEditing.value) {
                            isEditing.value = false
                        }
                    }
                }
            ) {
                LinkableTextField(
                    isEditing = isEditing,
                    initialText = "Initial text to see what happens www.hello.com and then stuff",
                    onLinkClick = { link ->
                        Log.d("Test", "Link clicked: $link")
                    }
                )
            }
        }
    }
}

@Composable
fun LinkableTextField(
    isEditing: MutableState<Boolean>,
    initialText: String,
    onLinkClick: (String) -> Unit
) {
    var text by remember { mutableStateOf(TextFieldValue(initialText)) }
    val context = LocalContext.current
    val annotatedString = remember(text.text) { buildAnnotatedStringWithLinks(text.text) }
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
    val interactionSource = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditing.value) {
        if (isEditing.value) {
            focusRequester.requestFocus() // Show keyboard
            //text = text.copy(selection = TextRange(text.text.length)) // Set caret to end
        } else {
            focusManager.clearFocus()
        }
    }

    // Use a side effect to listen for taps on the TextField
    LaunchedEffect(interactionSource, annotatedString) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    val offset = interaction.pressPosition
                    Log.d("Test", "Press text detected $offset)")
                    layoutResult.value?.let { layoutResult ->
                        val position = layoutResult.getOffsetForPosition(offset)
                        Log.d("Test", "Tap detected on char position $position")

                        annotatedString.getStringAnnotations(
                            tag = "URL",
                            start = position,
                            end = position
                        )
                            .also { Log.d("Test", "Annotation: $it for position $position") }
                            .firstOrNull()
                            ?.let { annotation -> onLinkClick(annotation.item) }
                            ?: run {
                                if (!isEditing.value) {
                                    isEditing.value = true
                                }
                                //text = text.copy(selection = TextRange(position))
                            }
                    }
                }

                else -> {
                    // Handle other interactions (e.g., focus changes, etc.)
                }
            }
        }
    }

    BasicTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .focusRequester(focusRequester)
            .onFocusEvent {
                Log.d("Test", "Focus changed to $it")
            },
        interactionSource = interactionSource,
        value = text,
        onValueChange = { text = it },
        //readOnly = !isEditing.value,
        visualTransformation = ClickableTextTransformation(annotatedString),
        onTextLayout = { layoutResult.value = it },
        textStyle = Typography.titleMedium
    )
}

private fun isTouchInsideTextField(
    textLayoutResult: TextLayoutResult?,
    offset: Offset
): Boolean {
    return textLayoutResult?.let {
        offset.x in 0f..it.size.width.toFloat() && offset.y in 0f..it.size.height.toFloat()
    } ?: false
}

private fun getClosestCharacterOffset(
    textLayoutResult: TextLayoutResult,
    touchPosition: Offset
): Int {
    if (textLayoutResult.lineCount == 0) return 0

    val touchY = touchPosition.y
    val lineIndex = when {
        touchY < 0 -> 0
        touchY > textLayoutResult.size.height -> textLayoutResult.lineCount - 1
        else -> textLayoutResult.getLineForVerticalPosition(touchY)
    }
    val lineStart = textLayoutResult.getLineStart(lineIndex)
    val lineEnd = textLayoutResult.getLineEnd(lineIndex)
    val offset = textLayoutResult.getOffsetForPosition(touchPosition)
    val result = when {
        offset < lineStart -> lineStart
        offset > lineEnd -> lineEnd
        touchPosition.x < textLayoutResult.getHorizontalPosition(offset, true) -> maxOf(
            lineStart,
            offset - 1
        )

        else -> offset
    }

    Log.d(
        "Test",
        "Closest offset: $result | Line: $lineIndex | Start: $lineStart | End: $lineEnd | Offset: $offset"
    )
    return result
}

private fun buildAnnotatedStringWithLinks(text: String): AnnotatedString {
    val annotatedString = buildAnnotatedString {
        // Simple regex to detect links
        val regex =
            Regex("(https?://)?(www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_+.~#?&//=]*)")
        var matchResult = regex.find(text)
        var start = 0

        while (matchResult != null) {
            // Add non-link text
            append(text.substring(start, matchResult.range.first))

            // Add link text as clickable
            pushStringAnnotation(tag = "URL", annotation = matchResult.value)
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
            matchResult = regex.find(text, start)
        }

        // Add remaining text
        append(text.substring(start))
    }

    Log.d(
        "Test",
        "Annotated string: ${annotatedString.getStringAnnotations("URL", 0, text.length)}"
    )
    return annotatedString
}

class ClickableTextTransformation(
    private val annotatedString: AnnotatedString
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(annotatedString, OffsetMapping.Identity)
    }
}

@Preview
@Composable
fun TextEditorWithLinksPreview() {
    EditorScreen()
}