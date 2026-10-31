package org.naambadlo.keypad

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Shadowban Suraksha keyboard. Keep typing on your usual keyboard; switch here after
 * a name. "Make it Meta friendly" swaps every name (again for fresh code names),
 * or pick a code name for the last word. Long-press a code name to star it.
 * Bare first names / common surnames ("Shah") ask first.
 */
class NaamBadloIME : InputMethodService() {

    private lateinit var title: TextView
    private lateinit var banner: LinearLayout
    private lateinit var chips: LinearLayout
    private lateinit var metaLabel: TextView
    private lateinit var metaKey: View
    private val handler = Handler(Looper.getMainLooper())
    private val matcher by lazy { NameStore.matcher(this) }
    private val memory get() = NameStore.memory
    private var lastKey = ""
    private val confirmed = HashSet<String>()
    private val declined = HashSet<String>()
    private var undoState: Pair<String, String>? = null   // before-cursor text: original to replaced

    override fun onCreateInputView(): View {
        val ctx: Context = this
        Ui.init(ctx)
        NameStore.matcher(ctx)
        val root = Ui.vertical(ctx).apply {
            background = Ui.pageBackground()
            setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 12), Ui.dp(ctx, 10), Ui.dp(ctx, 10))
        }

        // Title line, with the confirmation banner (message + Undo) layered over it
        val top = FrameLayout(ctx)
        title = Ui.label(ctx, "", 13.5f, Ui.MUTED, Ui.W.MED).apply {
            setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 2), Ui.dp(ctx, 8), Ui.dp(ctx, 8))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        banner = Ui.horizontal(ctx).apply {
            background = Ui.gradient(18, ctx)
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8))
            visibility = View.GONE
            elevation = Ui.dp(ctx, 6).toFloat()
            addView(Ui.label(ctx, "✓  ${NameStore.CONGRATS}", 14f, Ui.WHITE, Ui.W.MED),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Ui.label(ctx, "Copy", 14f, Ui.META, Ui.W.SEMI).apply {
                background = Ui.rounded(Ui.WHITE, 16, ctx)
                setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 7), Ui.dp(ctx, 14), Ui.dp(ctx, 7))
                setOnClickListener { copyAll() }
            })
            addView(Ui.label(ctx, "Undo", 14f, Ui.META, Ui.W.SEMI).apply {
                background = Ui.rounded(Ui.WHITE, 16, ctx)
                setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 7), Ui.dp(ctx, 14), Ui.dp(ctx, 7))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(Ui.dp(ctx, 6), 0, 0, 0) }
                setOnClickListener { undo() }
            })
        }
        top.addView(title)
        top.addView(banner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        root.addView(top)

        val (scroll, row) = Ui.chipRow(ctx)
        chips = row
        scroll.minimumHeight = Ui.dp(ctx, 52)
        root.addView(scroll)

        val meta = Ui.cta(ctx, "Make it Meta friendly") { makeFriendly() }
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
        if (!restarting) { confirmed.clear(); declined.clear() }
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
        currentInputConnection?.getTextBeforeCursor(20_000, 0)?.toString() ?: ""

    private fun wordKey(text: String, m: Match) = Matcher.normalize(text.substring(m.start, m.end))
    private fun ready(text: String, m: Match) = m.fromMemory || !m.ask || wordKey(text, m) in confirmed
    private fun pending(text: String, m: Match) =
        m.ask && !m.fromMemory && wordKey(text, m) !in confirmed && wordKey(text, m) !in declined

    private fun refresh() {
        if (!::chips.isInitialized) return
        val text = before()
        val all = matcher.findAll(text, memory)
        val usable = all.filter { ready(text, it) }
        val real = usable.count { !it.fromMemory }
        metaLabel.text = if (real == 0 && usable.isNotEmpty()) "Fresh names" else "Make it Meta friendly"
        metaKey.alpha = if (usable.isEmpty()) 0.6f else 1f

        val m = matcher.matchAtEnd(text, memory)?.takeIf { it.fromMemory || !it.ask || !declined.contains(wordKey(text, it)) }
        val key = m?.let { "${it.entity.id}:${it.start}:${text.length}:${pending(text, it)}" } ?: "none:${all.size}"
        if (key == lastKey) return
        lastKey = key
        chips.removeAllViews()
        if (m == null) {
            val ask = all.firstOrNull { pending(text, it) }
            title.text = when {
                ask != null -> "“${text.substring(ask.start, ask.end)}”: put the cursor after it to choose"
                all.isNotEmpty() -> "${usable.size} name(s) in your text"
                else -> "Type a name on your usual keyboard, then switch here"
            }
            return
        }
        val word = text.substring(m.start, m.end)
        if (pending(text, m)) {
            title.text = "“$word” means ${m.entity.label}?"
            chips.addView(Ui.chip(this, "Yes, ${m.entity.label}", selected = true) {
                confirmed.add(wordKey(text, m)); lastKey = ""; refresh()
            })
            chips.addView(Ui.chip(this, "No, someone else") {
                declined.add(wordKey(text, m)); lastKey = ""; refresh()
            })
            return
        }
        title.text = "“$word” · ${m.entity.label} · hold a code name to star it"
        val favs = Prefs.favourites(this, m.entity.id)
        NameStore.ordered(this, m.entity).forEachIndexed { i, alt ->
            val c = Ui.chip(this, if (alt in favs) "★ $alt" else alt) { swap(alt) }
            c.setOnLongClickListener {
                val added = Prefs.toggleFavourite(this, m.entity.id, alt)
                c.text = if (added) "★ $alt" else alt
                c.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                Toast.makeText(this, if (added) "Starred. Used first from now on." else "Unstarred", Toast.LENGTH_SHORT).show()
                true
            }
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
        val out = text.substring(0, m.start) + alt + tail
        replaceBefore(ic, text, out)
        celebrate(text, out)
    }

    private fun makeFriendly() {
        val ic = currentInputConnection ?: return
        val text = before()
        val out = matcher.replaceAll(text, memory, include = { m, _ -> ready(text, m) }, pick = NameStore::pick)
        if (out == text) return
        replaceBefore(ic, text, out)
        celebrate(text, out)
    }

    private fun replaceBefore(ic: android.view.inputmethod.InputConnection, old: String, new: String) {
        ic.beginBatchEdit()
        ic.deleteSurroundingText(old.length, 0)
        ic.commitText(new, 1)
        ic.endBatchEdit()
    }

    /** Copy the whole text box (not just the part before the cursor). */
    private fun copyAll() {
        val ic = currentInputConnection ?: return
        val all = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)?.text?.toString()
            ?: (before() + (ic.getTextAfterCursor(100_000, 0) ?: ""))
        (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .setPrimaryClip(android.content.ClipData.newPlainText(NameStore.APP_NAME, all))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun undo() {
        val (original, replaced) = undoState ?: return
        val ic = currentInputConnection ?: return
        if (before() != replaced) return
        replaceBefore(ic, replaced, original)
        undoState = null
        handler.removeCallbacksAndMessages(null)
        banner.animate().alpha(0f).setDuration(150).withEndAction { banner.visibility = View.GONE }.start()
        lastKey = ""
        refresh()
    }

    /** Message with Undo, then back to the user's keyboard. */
    private fun celebrate(original: String, replaced: String) {
        undoState = original to replaced
        handler.removeCallbacksAndMessages(null)
        banner.visibility = View.VISIBLE
        banner.alpha = 0f
        banner.translationY = -Ui.dp(this, 8).toFloat()
        banner.animate().alpha(1f).translationY(0f).setDuration(260).start()
        Feedback.swapped(this)
        handler.postDelayed({
            banner.animate().alpha(0f).setDuration(200).withEndAction { banner.visibility = View.GONE }.start()
            undoState = null
            switchBack()
        }, 3200)
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
