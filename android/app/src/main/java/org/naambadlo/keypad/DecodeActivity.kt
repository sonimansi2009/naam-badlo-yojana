package org.naambadlo.keypad

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Decode: paste a message full of code names and read it with the real names back.
 * Tap a restored name to keep the code name instead (for words that were never code names).
 * Also opens from the share sheet as "Decode names".
 */
class DecodeActivity : Activity() {

    private lateinit var input: EditText
    private lateinit var result: LinearLayout
    private val decoder by lazy { Decoder(NameStore.entities(this)) }
    private var text = ""
    private var hits: List<Decoder.Hit> = emptyList()
    private val skip = HashSet<Int>()
    private var out: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        window.statusBarColor = Ui.BG_TOP
        window.navigationBarColor = Ui.BG

        val col = Ui.vertical(this).apply {
            setPadding(Ui.dp(context, 20), Ui.dp(context, 14), Ui.dp(context, 20), Ui.dp(context, 40))
        }
        col.addView(Ui.label(this, "←", 22f, Ui.INK, Ui.W.MED).apply {
            gravity = Gravity.CENTER
            background = Ui.oval(Ui.SURFACE)
            layoutParams = LinearLayout.LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44))
            setOnClickListener { finish() }
            Ui.pressable(this)
        })
        col.addView(Ui.headline(this, "Decode *message*", 42f).apply { setPadding(0, Ui.dp(context, 22), 0, Ui.dp(context, 6)) })
        col.addView(Ui.label(this, "Paste a message written in code names. You get it back with the real names.",
            14f, Ui.MUTED).apply { setPadding(0, 0, 0, Ui.dp(context, 18)) })

        val card = Ui.card(this, 16)
        card.addView(Ui.horizontal(this).apply {
            addView(Ui.lightButton(context, "Paste") { paste() }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, Ui.dp(context, 6), 0) }
            })
            addView(Ui.lightButton(context, "Clear") { input.setText(""); result.removeAllViews() }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(Ui.dp(context, 6), 0, 0, 0) }
            })
        })
        input = EditText(this).apply {
            hint = "e.g. Kamal Party ne kaha, Mota Bhai ne suna…"
            typeface = Ui.SANS; textSize = 16f
            setTextColor(Ui.INK); setHintTextColor(Ui.MUTED)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
            background = Ui.rounded(Ui.SOFT, 20, this@DecodeActivity)
            val p = Ui.dp(this@DecodeActivity, 16); setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, Ui.dp(context, 12), 0, 0) }
        }
        card.addView(input)
        card.addView(Ui.horizontal(this).apply {
            gravity = Gravity.END
            setPadding(0, Ui.dp(context, 16), 0, 0)
            addView(Ui.cta(context, "Decode message") { decode() })
        })
        col.addView(card)
        result = Ui.vertical(this)
        col.addView(result)
        setContentView(ScrollView(this).apply { background = Ui.pageBackground(); isVerticalScrollBarEnabled = false; addView(col) })
        Ui.enter(card, 0)

        val incoming = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            else -> null
        }
        incoming?.let { input.setText(it); decode() }
    }

    private fun paste() {
        val clip = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        val t = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (t.isNullOrEmpty()) { Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show(); return }
        input.setText(t); decode()
    }

    private fun decode() {
        text = input.text.toString()
        if (text.isBlank()) { Toast.makeText(this, "Paste a message first", Toast.LENGTH_SHORT).show(); return }
        hits = decoder.find(text)
        skip.clear()
        result.removeAllViews()
        if (hits.isEmpty()) {
            result.addView(Ui.label(this, "No code names found in this message.", 15f, Ui.MUTED).apply {
                setPadding(Ui.dp(context, 4), Ui.dp(context, 18), 0, 0)
            })
            return
        }
        Feedback.swapped(this)
        val done = Ui.card(this, 16).apply { background = Ui.gradient(26, context) }
        val people = hits.map { it.real }.distinct()
        done.addView(Ui.label(this, "✓  Decoded ${hits.size} code name(s)", 16f, Ui.GRAD_INK, Ui.W.SEMI))
        done.addView(Ui.label(this, people.take(6).joinToString(", ") + if (people.size > 6) "…" else "", 13.5f, Ui.GRAD_SUB).apply {
            setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 12))
        })
        done.addView(Ui.label(this, "Copy decoded text", 16f, Ui.WHITE, Ui.W.SEMI).apply {
            gravity = Gravity.CENTER
            background = Ui.rounded(Ui.GRAD_INK, 24, context)
            setPadding(0, Ui.dp(context, 14), 0, Ui.dp(context, 14))
            setOnClickListener { copy() }
            Ui.pressable(this)
        })
        result.addView(Ui.spacer(this, 16))
        result.addView(done)
        result.addView(Ui.label(this, "Decoded message", 16f, Ui.INK, Ui.W.SEMI).apply {
            setPadding(Ui.dp(context, 4), Ui.dp(context, 18), 0, Ui.dp(context, 2))
        })
        result.addView(Ui.label(this, "Tap a highlighted name to keep the original word.", 12.5f, Ui.MUTED).apply {
            setPadding(Ui.dp(context, 4), 0, 0, Ui.dp(context, 8))
        })
        out = Ui.label(this, "", 16f).apply {
            background = Ui.rounded(Ui.SOFT, 20, this@DecodeActivity)
            val p = Ui.dp(this@DecodeActivity, 16); setPadding(p, p, p, p)
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = 0
            setTextIsSelectable(false)
        }
        result.addView(out)
        paint()
        Ui.enter(done, 0)
    }

    private fun paint() {
        val sb = SpannableStringBuilder()
        var last = 0
        for (h in hits) {
            sb.append(text, last, h.start)
            val s = sb.length
            val kept = h.start in skip
            sb.append(if (kept) h.code else h.real)
            if (!kept) {
                sb.setSpan(BackgroundColorSpan(0x557B61FF), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            sb.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    if (!skip.add(h.start)) skip.remove(h.start)
                    paint()
                }
                override fun updateDrawState(ds: TextPaint) { ds.isUnderlineText = false; ds.color = Ui.INK }
            }, s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            last = h.end
        }
        sb.append(text, last, text.length)
        out?.text = sb
    }

    private fun copy() {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Decoded", decoder.decode(text, hits, skip)))
        Toast.makeText(this, "Copied. Paste it anywhere.", Toast.LENGTH_SHORT).show()
    }
}
