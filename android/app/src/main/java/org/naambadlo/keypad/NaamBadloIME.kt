package org.naambadlo.keypad

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Naam Chupao keyboard. Keep typing on your usual keyboard; switch here after
 * a name. Tap "Make it Meta friendly" to swap every name (again for fresh
 * code names), or pick one of 50 code names for the last word.
 */
class NaamBadloIME : InputMethodService() {

    private lateinit var title: TextView
    private lateinit var banner: TextView
    private lateinit var chips: LinearLayout
    private lateinit var metaLabel: TextView
    private lateinit var metaKey: View
    private val handler = Handler(Looper.getMainLooper())
    private val matcher by lazy { NameStore.matcher(this) }
    private val memory get() = NameStore.memory
    private var lastKey = ""

    override fun onCreateInputView(): View {
        val ctx: Context = this
        Ui.init(ctx)
        val root = Ui.vertical(ctx).apply {
            background = Ui.pageBackground()
            setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 12), Ui.dp(ctx, 10), Ui.dp(ctx, 10))
        }

        // Title line, with the congratulations banner layered over it
        val top = FrameLayout(ctx)
        title = Ui.label(ctx, "", 13.5f, Ui.MUTED, Ui.W.MED).apply {
            setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 2), Ui.dp(ctx, 8), Ui.dp(ctx, 8))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        banner = Ui.label(ctx, "✓  ${NameStore.CONGRATS}", 14f, Ui.WHITE, Ui.W.MED).apply {
            background = Ui.rounded(Ui.BLACK, 18, ctx)
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 11), Ui.dp(ctx, 16), Ui.dp(ctx, 11))
            visibility = View.GONE
            elevation = Ui.dp(ctx, 6).toFloat()
        }
        top.addView(title)
        top.addView(banner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        root.addView(top)

        val (scroll, row) = Ui.chipRow(ctx)
        chips = row
        scroll.minimumHeight = Ui.dp(ctx, 52)
        root.addView(scroll)

        // Big Meta-blue action
        val meta = Ui.cta(ctx, "Make it Meta friendly", R.drawable.ic_sparkle) { makeFriendly() }
        metaLabel = meta.getChildAt(0) as TextView
        metaKey = meta
        root.addView(meta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 52)).apply {
            setMargins(Ui.dp(ctx, 2), Ui.dp(ctx, 8), Ui.dp(ctx, 2), 0)
        })
        metaLabel.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

        fun key(text: String, weight: Float, action: () -> Unit) =
            Ui.lightButton(ctx, text, action).apply {
                setPadding(0, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, Ui.dp(ctx, 46), weight).apply {
                    setMargins(Ui.dp(ctx, 3), Ui.dp(ctx, 8), Ui.dp(ctx, 3), 0)
                }
            }
        val bar = Ui.horizontal(ctx)
        bar.addView(key("⌨  Back", 1.6f) { switchBack() })
        bar.addView(key("space", 2.2f) { currentInputConnection?.commitText(" ", 1) })
        bar.addView(key("⌫", 1f) { backspace() })
        bar.addView(key("↵", 1f) { enter() })
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
        metaLabel.text = if (real == 0 && all.isNotEmpty()) "Fresh names" else "Make it Meta friendly"
        metaKey.alpha = if (all.isEmpty()) 0.6f else 1f

        val m = matcher.matchAtEnd(text, memory)
        val key = m?.let { "${it.entity.id}:${it.start}:${text.length}" } ?: "none:${all.size}"
        if (key == lastKey) return
        lastKey = key
        chips.removeAllViews()
        if (m == null) {
            title.text = if (all.isNotEmpty()) "${all.size} name(s) in your text"
            else "Type a name on your usual keyboard, then switch here"
            return
        }
        val word = text.substring(m.start, m.end)
        title.text = "“$word” · ${m.entity.label} · pick a code name"
        m.entity.alternates.shuffled().forEachIndexed { i, alt ->
            val c = Ui.chip(this, alt) { swap(alt) }
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
        celebrate()
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
        celebrate()
    }

    /** Show the congratulations banner, then go back to the user's keyboard. */
    private fun celebrate() {
        handler.removeCallbacksAndMessages(null)
        banner.visibility = View.VISIBLE
        banner.alpha = 0f
        banner.translationY = -Ui.dp(this, 8).toFloat()
        banner.animate().alpha(1f).translationY(0f).setDuration(260).start()
        banner.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        handler.postDelayed({
            banner.animate().alpha(0f).setDuration(200).withEndAction { banner.visibility = View.GONE }.start()
            switchBack()
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
