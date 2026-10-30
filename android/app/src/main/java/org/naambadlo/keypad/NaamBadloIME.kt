package org.naambadlo.keypad

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A slim add-on keyboard. Keep typing on your usual keyboard; switch here
 * after a name. Tap "Make it Meta friendly" to swap every name (tap again
 * for fresh code names), or pick one of 50 code names for the last word.
 */
class NaamBadloIME : InputMethodService() {

    private lateinit var title: TextView
    private lateinit var banner: TextView
    private lateinit var chips: LinearLayout
    private lateinit var metaKey: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val matcher by lazy { NameStore.matcher(this) }
    private val memory get() = NameStore.memory
    private var lastKey = ""

    override fun onCreateInputView(): View {
        val ctx: Context = this
        val root = Ui.vertical(ctx).apply {
            setBackgroundColor(Ui.BLUE_DEEP)
            setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 10), Ui.dp(ctx, 8), Ui.dp(ctx, 10))
        }

        // Title line, with the congratulations banner layered over it
        val top = FrameLayout(ctx)
        title = Ui.label(ctx, "", 13.5f, Ui.ON_BLUE_MUTED).apply {
            setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 2), Ui.dp(ctx, 6), Ui.dp(ctx, 6))
            maxLines = 1
        }
        banner = Ui.label(ctx, "✓  ${NameStore.CONGRATS}", 14f, Ui.BLUE_DEEP, bold = true).apply {
            background = Ui.rounded(Ui.SURFACE, 12, ctx)
            setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 10), Ui.dp(ctx, 14), Ui.dp(ctx, 10))
            visibility = View.GONE
        }
        top.addView(title)
        top.addView(banner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        root.addView(top)

        val (scroll, row) = Ui.chipRow(ctx)
        chips = row
        scroll.minimumHeight = Ui.dp(ctx, 50)
        root.addView(scroll)

        fun key(text: String, weight: Float, style: Ui.Style, action: () -> Unit) =
            Ui.button(ctx, text, style, action).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                layoutParams = LinearLayout.LayoutParams(0, Ui.dp(ctx, 48), weight).apply {
                    setMargins(Ui.dp(ctx, 3), Ui.dp(ctx, 6), Ui.dp(ctx, 3), 0)
                }
            }

        metaKey = key("✦  Make it Meta friendly", 1f, Ui.Style.ON_DARK_PRIMARY) { makeFriendly() }
        root.addView(LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; addView(metaKey) })

        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(key("⌨  Back", 1.6f, Ui.Style.ON_DARK) { switchBack() })
        bar.addView(key("space", 2f, Ui.Style.ON_DARK) { currentInputConnection?.commitText(" ", 1) })
        bar.addView(key("⌫", 1f, Ui.Style.ON_DARK) { backspace() })
        bar.addView(key("↵", 1f, Ui.Style.ON_DARK) { enter() })
        root.addView(bar)
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lastKey = ""
        refresh()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        refresh()
    }

    private fun before(): String =
        currentInputConnection?.getTextBeforeCursor(2000, 0)?.toString() ?: ""

    private fun refresh() {
        if (!::chips.isInitialized) return
        val text = before()
        val all = matcher.findAll(text, memory)
        val real = all.count { !it.fromMemory }
        metaKey.text = when {
            real > 0 -> "✦  Make it Meta friendly"
            all.isNotEmpty() -> "↻  Fresh names"
            else -> "✦  Make it Meta friendly"
        }
        metaKey.alpha = if (all.isEmpty()) 0.55f else 1f

        val m = matcher.matchAtEnd(text, memory)
        val key = m?.let { "${it.entity.id}:${it.start}:${text.length}" } ?: "none:${all.size}"
        if (key == lastKey) return
        lastKey = key
        chips.removeAllViews()
        if (m == null) {
            title.text = when {
                all.isNotEmpty() -> "${all.size} name(s) in your text"
                else -> "Type a name on your usual keyboard, then switch here"
            }
            return
        }
        val word = text.substring(m.start, m.end)
        title.text = "“$word”  ·  ${m.entity.label}  ·  pick a code name"
        m.entity.alternates.shuffled().forEachIndexed { i, alt ->
            val c = Ui.chip(this, alt, dark = true) { swap(alt) }
            chips.addView(c)
            if (i < 6) Ui.enter(c, i * 35L, rise = 6)
        }
        (chips.parent as? View)?.scrollTo(0, 0)
    }

    private fun swap(alt: String) {
        val ic = currentInputConnection ?: return
        val text = before()
        val m = matcher.matchAtEnd(text, memory) ?: return
        val tail = text.substring(m.end)
        memory.add(alt, m.entity)
        ic.beginBatchEdit()
        ic.deleteSurroundingText(text.length - m.start, 0)
        ic.commitText(alt + tail, 1)
        ic.endBatchEdit()
        celebrate(returnAfter = true)
    }

    private fun makeFriendly() {
        val ic = currentInputConnection ?: return
        val text = before()
        if (matcher.findAll(text, memory).isEmpty()) return
        val out = matcher.replaceAll(text, memory)
        ic.beginBatchEdit()
        ic.deleteSurroundingText(text.length, 0)
        ic.commitText(out, 1)
        ic.endBatchEdit()
        celebrate(returnAfter = true)
    }

    /** Show the congratulations banner, then go back to the user's keyboard. */
    private fun celebrate(returnAfter: Boolean) {
        handler.removeCallbacksAndMessages(null)
        banner.visibility = View.VISIBLE
        banner.alpha = 0f
        banner.translationY = -Ui.dp(this, 8).toFloat()
        banner.animate().alpha(1f).translationY(0f).setDuration(240).start()
        banner.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        handler.postDelayed({
            banner.animate().alpha(0f).setDuration(200).withEndAction { banner.visibility = View.GONE }.start()
            if (returnAfter) switchBack()
        }, 2400)
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        if (!sel.isNullOrEmpty()) ic.commitText("", 1)
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    private fun enter() {
        val ei = currentInputEditorInfo
        val action = ei?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnter = ei != null && (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED && !noEnter) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun switchBack() {
        if (Build.VERSION.SDK_INT >= 28) {
            if (!switchToPreviousInputMethod()) showPicker()
        } else {
            val token = window?.window?.attributes?.token
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            @Suppress("DEPRECATION")
            if (token == null || !imm.switchToLastInputMethod(token)) showPicker()
        }
    }

    private fun showPicker() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }
}
