package com.ruigoncalo.eder

import android.content.Context
import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ruigoncalo.eder.UiEvent.HideBottomSheet
import com.ruigoncalo.eder.UiEvent.None
import com.ruigoncalo.eder.UiEvent.OpenBrowser
import com.ruigoncalo.eder.UiEvent.OpenBottomSheet
import com.ruigoncalo.eder.UiEvent.TextUpdate
import com.ruigoncalo.eder.model.Link
import com.ruigoncalo.eder.theme.Typography
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel = viewModel()
) {
    val uiEvent: UiEvent by viewModel.uiEvent.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    val isEditing = remember { mutableStateOf(false) }
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
    val caretPosition = remember { mutableStateOf<Int?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }

    val text = remember { mutableStateOf(TextFieldValue("")) }

    val annotatedString = remember(text.value.text, uiEvent) {
        viewModel.buildAnnotatedStringWithLinks(text.value.text)
    }

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
                    // tapping outside the text field
                    detectTapGestures { offset ->
                        if(!isEditing.value) {
                            isEditing.value = true

                            layoutResult.value?.let { layout ->
                                val closestTextPoint = getClosestCharacterOffset(
                                    textLayoutResult = layout,
                                    touchPosition = offset
                                )

                                caretPosition.value = closestTextPoint
                            }
                        }
                    }
                }
            ) {
                LinkableTextField(
                    isEditing = isEditing,
                    text = text,
                    annotatedString = annotatedString,
                    layoutResult = layoutResult,
                    updateCaretPosition = caretPosition.value,
                    onOpenLink = { link -> viewModel.onOpenLink(link) },
                    onEditLink = { link -> viewModel.onEditLink(link) }
                )
            }
        }
    }

    LaunchedEffect(uiEvent) {
        when (uiEvent) {
            is None -> Unit
            is HideBottomSheet -> showBottomSheet = false
            is OpenBottomSheet -> showBottomSheet = true
            is TextUpdate -> {
                val textUpdated = (uiEvent as TextUpdate).text
                Log.d("Test", "Text update $textUpdated")
                showBottomSheet = false
                text.value = TextFieldValue(textUpdated)
            }

            is OpenBrowser -> {
                val url = (uiEvent as OpenBrowser).url
                openBrowser(url, context)
            }
        }
    }


    if (showBottomSheet && uiEvent is OpenBottomSheet) {
        EditLinkBottomSheet(
            link = (uiEvent as OpenBottomSheet).link,
            onDismiss = { showBottomSheet = false },
            onSave = { link, newText, newUrl ->
                viewModel.onSaveLink(
                    link = link,
                    linkText = newText,
                    linkUrl = newUrl,
                    currentText = text.value.text
                )
            },
            onRemoveLink = { link -> viewModel.onRemoveLink(link) }
        )
    }
}

@Composable
fun LinkableTextField(
    isEditing: MutableState<Boolean>,
    text: MutableState<TextFieldValue>,
    annotatedString: AnnotatedString,
    layoutResult: MutableState<TextLayoutResult?>,
    updateCaretPosition: Int?,
    onOpenLink: (String) -> Unit,
    onEditLink: (String) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditing.value) {
        if (isEditing.value) {
            focusRequester.requestFocus() // Show keyboard
        } else {
            focusManager.clearFocus() // Hide keyboard
        }
    }

    // Use a side effect to listen to updates on the caret position
    LaunchedEffect(updateCaretPosition) {
        if (updateCaretPosition != null) {
            text.value = text.value.copy(selection = TextRange(updateCaretPosition))
        }
    }

    // Use a side effect to listen to taps on the TextField and changes on the text
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    val offset = interaction.pressPosition
                    val position = layoutResult.value?.getOffsetForPosition(offset)

                    if (position != null) {
                        val annotation = annotatedString
                            .getStringAnnotations(tag = LINK_TAG, start = position, end = position)
                            .firstOrNull()

                        if (annotation != null) {
                            onOpenLink(annotation.item)
                            focusManager.clearFocus()
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
            .onFocusEvent { isEditing.value = it.isFocused },
        interactionSource = interactionSource,
        value = text.value,
        onValueChange = { text.value = it },
        visualTransformation = ClickableTextTransformation(annotatedString),
        onTextLayout = { layoutResult.value = it },
        textStyle = Typography.bodyMedium,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.value.text.isEmpty()) {
                    Text(
                        text = "Enter your text here",
                        color = Color.Gray,
                        style = Typography.bodyMedium
                    )
                }
                innerTextField()
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLinkBottomSheet(
    link: Link,
    onDismiss: () -> Unit,
    onSave: (Link, String, String) -> Unit,
    onRemoveLink: (Link) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var urlTextField by remember { mutableStateOf(TextFieldValue(link.url)) }
    var textTextField by remember { mutableStateOf(TextFieldValue(link.text)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        // Sheet content
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row {
                Text(
                    modifier = Modifier.padding(end = 16.dp),
                    text = "Link",
                    style = Typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                BasicTextField(
                    modifier = Modifier.weight(1f),
                    textStyle = Typography.bodyMedium,
                    value = urlTextField,
                    onValueChange = {
                        urlTextField = it
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Text(
                    modifier = Modifier.padding(end = 16.dp),
                    text = "Text",
                    style = Typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                BasicTextField(
                    modifier = Modifier.weight(1f),
                    textStyle = Typography.bodyMedium,
                    value = textTextField,
                    onValueChange = {
                        textTextField = it
                    }
                )
            }
            Row {
                Button(
                    modifier = Modifier
                        .padding(16.dp)
                        .weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    onClick = { onRemoveLink(link) }
                ) {
                    Text(text = if (link.isDeleted) "Add link" else "Remove link")
                }

                Button(
                    modifier = Modifier
                        .padding(16.dp)
                        .weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    onClick = { onSave(link, textTextField.text, urlTextField.text) }
                ) {
                    Text(text = "Save")
                }
            }
        }
    }
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

private fun isTouchInsideTextField(
    textLayoutResult: TextLayoutResult?,
    offset: Offset
): Boolean {
    return textLayoutResult?.let {
        offset.x in 0f..it.size.width.toFloat() && offset.y in 0f..it.size.height.toFloat()
    } ?: false
}

private fun openBrowser(url: String, context: Context) {
    val fullUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
        url
    } else {
        "https://$url"
    }

    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
    context.startActivity(intent)
}

class ClickableTextTransformation(
    private val annotatedString: AnnotatedString
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(annotatedString, OffsetMapping.Identity)
    }
}

private fun prettyTime(time: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    return sdf.format(time)
}

@Preview
@Composable
fun TextEditorWithLinksPreview() {
    EditorScreen()
}