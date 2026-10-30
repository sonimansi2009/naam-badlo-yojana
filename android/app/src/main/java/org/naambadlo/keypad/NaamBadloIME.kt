package org.naambadlo.keypad

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A slim add-on keyboard. You keep typing on your normal keyboard
 * (Gboard, SwiftKey, Samsung). When you want a swap, switch to this one:
 * it reads the word just before the cursor and offers 50 alternates.
 * Tap one and it switches you straight back to your normal keyboard.
 */
class NaamBadloIME : InputMethodService() {

    private lateinit var title: TextView
    private lateinit var chips: LinearLayout
    private val matcher by lazy { NameStore.matcher(this) }

    override fun onCreateInputView(): View {
        val ctx: Context = this
        val root = Ui.vertical(ctx, 8).apply { setBackgroundColor(Ui.BG) }

        title = Ui.label(ctx, "", 13f, Ui.MUTED).apply {
            setPadding(Ui.dp(ctx, 6), 0, 0, Ui.dp(ctx, 4))
        }
        root.addView(title)

        val (scroll, row) = Ui.chipRow(ctx)
        chips = row
        scroll.minimumHeight = Ui.dp(ctx, 52)
        root.addView(scroll)

        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(ctx, 6), 0, 0)
        }
        fun key(text: String, weight: Float, accent: Boolean = false, action: () -> Unit) =
            Ui.chip(ctx, text, accent, action).apply {
                layoutParams = LinearLayout.LayoutParams(0, Ui.dp(ctx, 46), weight).apply {
                    setMargins(Ui.dp(ctx, 3), 0, Ui.dp(ctx, 3), 0)
                }
            }
        // Big one-tap button on its own row, small keys under it
        val meta = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, Ui.dp(ctx, 6), 0, 0)
        }
        meta.addView(key("✦ Make it Meta friendly", 1f, accent = true) {
            replaceAllBeforeCursor()
            switchBack()
        })
        root.addView(meta)

        bar.addView(key("⌨ Back", 1.6f) { switchBack() })
        bar.addView(key("␣", 2f) { currentInputConnection?.commitText(" ", 1) })
        bar.addView(key("⌫", 1f) { backspace() })
        bar.addView(key("↵", 1f) { enter() })
        root.addView(bar)
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
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
        currentInputConnection?.getTextBeforeCursor(1000, 0)?.toString() ?: ""

    private fun refresh() {
        if (!::chips.isInitialized) return
        chips.removeAllViews()
        val text = before()
        val m = matcher.matchAtEnd(text)
        if (m == null) {
            val count = matcher.findAll(text).size
            title.text = if (count > 0)
                "$count name(s) in your text. Tap “Make it Meta friendly”."
            else
                "Type a name on your usual keyboard, then switch here."
            return
        }
        val word = text.substring(m.start, m.end)
        title.text = "“$word” → pick a swap (${m.entity.alternates.size})"
        for (alt in m.entity.alternates.shuffled()) {
            chips.addView(Ui.chip(this, alt) { swap(alt) })
        }
    }

    private fun swap(alt: String) {
        val ic = currentInputConnection ?: return
        val text = before()
        val m = matcher.matchAtEnd(text) ?: return
        val tail = text.substring(m.end)
        ic.beginBatchEdit()
        ic.deleteSurroundingText(text.length - m.start, 0)
        ic.commitText(alt + tail, 1)
        ic.endBatchEdit()
        switchBack()
    }

    private fun replaceAllBeforeCursor() {
        val ic = currentInputConnection ?: return
        val text = before()
        val out = matcher.replaceAll(text)
        if (out == text) return
        ic.beginBatchEdit()
        ic.deleteSurroundingText(text.length, 0)
        ic.commitText(out, 1)
        ic.endBatchEdit()
        refresh()
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
