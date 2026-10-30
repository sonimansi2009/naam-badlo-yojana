package org.naambadlo.keypad

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Works with ANY keyboard: select text in WhatsApp, X, Instagram etc.,
 * tap "Naam Badlo" in the pop-up menu, pick swaps, tap Done.
 */
class ProcessTextActivity : Activity() {

    private lateinit var original: String
    private lateinit var matches: List<Match>
    private lateinit var choices: Array<String>
    private lateinit var preview: TextView
    private var readOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        original = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""
        readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        matches = NameStore.matcher(this).findAll(original)
        choices = Array(matches.size) { matches[it].entity.alternates.random() }
        setContentView(build())
    }

    private fun result(): String {
        val sb = StringBuilder()
        var last = 0
        matches.forEachIndexed { i, m ->
            sb.append(original, last, m.start).append(choices[i])
            last = m.end
        }
        sb.append(original, last, original.length)
        return sb.toString()
    }

    private fun build(): ScrollView {
        val root = Ui.vertical(this, 18).apply { setBackgroundColor(Ui.BG) }
        root.addView(Ui.label(this, "Naam Badlo", 20f, Ui.TEXT, bold = true))
        root.addView(Ui.spacer(this, 8))

        if (matches.isEmpty()) {
            root.addView(Ui.label(this, "No names from the list found in this text.", 15f, Ui.MUTED))
            root.addView(Ui.spacer(this, 12))
            root.addView(Ui.chip(this, "Close", accent = true) { finish() })
            return ScrollView(this).apply { addView(root) }
        }

        preview = Ui.label(this, result(), 16f).apply {
            background = Ui.rounded(Ui.PANEL, 10, this@ProcessTextActivity)
            val p = Ui.dp(this@ProcessTextActivity, 12)
            setPadding(p, p, p, p)
            setTextIsSelectable(true)
        }
        root.addView(preview)
        root.addView(Ui.spacer(this, 14))

        matches.forEachIndexed { i, m ->
            val word = original.substring(m.start, m.end)
            root.addView(Ui.label(this, "“$word”  ·  ${m.entity.label}", 13f, Ui.MUTED))
            val (scroll, row) = Ui.chipRow(this)
            for (alt in m.entity.alternates) {
                row.addView(Ui.chip(this, alt) {
                    choices[i] = alt
                    preview.text = result()
                })
            }
            root.addView(scroll)
            root.addView(Ui.spacer(this, 10))
        }

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(Ui.chip(this, "🎲 Shuffle") {
            for (i in choices.indices) choices[i] = matches[i].entity.alternates.random()
            preview.text = result()
        })
        bar.addView(Ui.chip(this, "Copy") { copy(result()) })
        bar.addView(Ui.chip(this, if (readOnly) "Copy & close" else "Done", accent = true) { done() })
        root.addView(bar)
        return ScrollView(this).apply { addView(root) }
    }

    private fun copy(text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Naam Badlo", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun done() {
        val text = result()
        if (readOnly) {
            copy(text)
        } else {
            setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text))
        }
        finish()
    }
}
