package app.antumbra.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.antumbra.R
import app.antumbra.ui.theme.MonoSmall

/**
 * Brand mark: an annular eclipse. A ring whose inner disc sits slightly off-centre.
 * [progress] in 0..1 draws an outer progress arc; null hides it.
 */
@Composable
fun Mark(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface, progress: Float? = null, accent: Color = MaterialTheme.colorScheme.primary) {
    val track = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val size = size.minDimension
        val outer = size * 0.34f
        val inner = outer * 0.77f
        val shift = outer * 0.12f
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(androidx.compose.ui.geometry.Rect(center, outer))
            addOval(androidx.compose.ui.geometry.Rect(Offset(center.x + shift, center.y - shift), inner))
        }
        drawPath(path, color)
        if (progress != null) {
            val radius = size * 0.46f
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(track, radius = radius, style = stroke)
            drawArc(
                color = accent, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = stroke,
            )
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

@Composable
fun TwoLine(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (!subtitle.isNullOrEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Native text field so the keyboard can be put in incognito mode
 * (IME_FLAG_NO_PERSONALIZED_LEARNING): nothing typed here trains or appears in suggestions.
 */
@Composable
fun IncognitoField(
    initial: String,
    hint: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    autoFocus: Boolean = false,
    imeAction: Int = EditorInfo.IME_ACTION_GO,
    onChange: (String) -> Unit = {},
    onSubmit: (String) -> Unit = {},
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    hintColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    clearSignal: Int = 0,
    password: Boolean = false,
) {
    val change by rememberUpdatedState(onChange)
    val submit by rememberUpdatedState(onSubmit)
    val context = androidx.compose.ui.platform.LocalContext.current
    val font = remember { runCatching { ResourcesCompat.getFont(context, R.font.sans_regular) }.getOrNull() }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            EditText(context).apply {
                setText(initial)
                this.hint = hint
                background = null
                typeface = font ?: Typeface.DEFAULT
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                if (password) transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding(0, 0, 0, 0)
                isSingleLine = singleLine
                if (!singleLine) {
                    maxLines = 6
                    setHorizontallyScrolling(false)
                }
                inputType = if (password) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                } else if (singleLine) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                }
                imeOptions = imeAction or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                setOnEditorActionListener { view, action, _ ->
                    if (action == imeAction) {
                        submit(view.text.toString()); true
                    } else false
                }
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: android.text.Editable?) = change(s?.toString().orEmpty())
                })
                if (autoFocus) post {
                    requestFocus()
                    selectAll()
                    context.getSystemService(InputMethodManager::class.java).showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                }
                tag = clearSignal
            }
        },
        update = { view ->
            view.setTextColor(textColor.toArgb())
            view.setHintTextColor(hintColor.toArgb())
            if (view.tag != clearSignal) {
                view.tag = clearSignal
                view.setText("")
            }
        },
    )
}

/** Copies text marked as sensitive and clears it after 60 seconds if unchanged. */
fun copySensitive(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("", text)
    clip.description.extras = PersistableBundle().apply {
        putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
    }
    clipboard.setPrimaryClip(clip)
    Handler(Looper.getMainLooper()).postDelayed({
        runCatching {
            if (clipboard.primaryClip?.getItemAt(0)?.text?.toString() == text) {
                if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }, 60_000)
}

@Composable
fun KeyValueRow(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value, style = MonoSmall, modifier = Modifier.weight(0.6f))
    }
}
