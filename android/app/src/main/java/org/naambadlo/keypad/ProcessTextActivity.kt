package org.naambadlo.keypad

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Works with ANY keyboard: select text, tap "Naam Chupao" in the pop-up menu,
 * pick code names (or shuffle as often as you like), then Done.
 */
class ProcessTextActivity : Activity() {

    private lateinit var original: String
    private lateinit var matches: List<Match>
    private lateinit var choices: Array<String>
    private lateinit var preview: TextView
    private lateinit var banner: TextView
    private val rows = ArrayList<LinearLayout>()
    private var readOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        original = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""
        readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        matches = NameStore.matcher(this).findAll(original, NameStore.memory)
        choices = Array(matches.size) { Matcher.freshPick(matches[it].entity, original.substring(matches[it].start, matches[it].end)) }
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

    private fun build(): View {
        val root = Ui.vertical(this, 20).apply { setBackgroundColor(Ui.PAGE) }
        root.addView(Ui.heading(this, NameStore.APP_NAME, 24f, Ui.BLUE_DEEP))
        root.addView(Ui.spacer(this, 10))

        if (matches.isEmpty()) {
            root.addView(Ui.label(this, "No names from the list in this text.", 15f, Ui.MUTED))
            root.addView(Ui.fullWidth(Ui.button(this, "Close") { finish() }, this, topDp = 16))
            return root
        }

        preview = Ui.label(this, result(), 16.5f).apply {
            background = Ui.rounded(Ui.SURFACE, 14, this@ProcessTextActivity, Ui.LINE)
            val p = Ui.dp(this@ProcessTextActivity, 14)
            setPadding(p, p, p, p)
            setTextIsSelectable(true)
        }
        root.addView(preview)

        banner = Ui.label(this, "✓  ${NameStore.CONGRATS}", 14f, Ui.ON_BLUE, bold = true).apply {
            background = Ui.gradient(this@ProcessTextActivity, 14)
            val p = Ui.dp(this@ProcessTextActivity, 12)
            setPadding(p, p, p, p)
            visibility = View.GONE
        }
        root.addView(Ui.fullWidth(banner, this, topDp = 10))
        root.addView(Ui.spacer(this, 12))

        matches.forEachIndexed { i, m ->
            val word = original.substring(m.start, m.end)
            root.addView(Ui.label(this, "“$word”  ·  ${m.entity.label}", 13f, Ui.MUTED))
            val (scroll, row) = Ui.chipRow(this)
            for (alt in m.entity.alternates) {
                val chip = Ui.chip(this, alt, selected = alt == choices[i]) {
                    choices[i] = alt
                    refresh()
                }
                row.addView(chip)
            }
            rows.add(row)
            root.addView(scroll)
            root.addView(Ui.spacer(this, 8))
        }

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun weighted(v: View, w: Float, left: Int, right: Int) = v.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w).apply {
                setMargins(Ui.dp(context, left), Ui.dp(context, 8), Ui.dp(context, right), 0)
            }
        }
        bar.addView(weighted(Ui.button(this, "↻  Shuffle", Ui.Style.OUTLINE) { shuffle() }, 1f, 0, 6))
        bar.addView(weighted(Ui.button(this, if (readOnly) "Copy" else "Make it Meta friendly") { done() }, 1.6f, 6, 0))
        root.addView(bar)

        Ui.enter(root, 0)
        return ScrollView(this).apply { setBackgroundColor(Ui.PAGE); addView(root) }
    }

    private fun shuffle() {
        for (i in choices.indices) choices[i] = Matcher.freshPick(matches[i].entity, choices[i])
        preview.animate().alpha(0.35f).setDuration(90).withEndAction {
            refresh()
            preview.animate().alpha(1f).setDuration(180).start()
        }.start()
    }

    private fun refresh() {
        preview.text = result()
        rows.forEachIndexed { i, row ->
            for (k in 0 until row.childCount) {
                val c = row.getChildAt(k) as TextView
                Ui.styleChip(c, c.text.toString() == choices[i], dark = false)
            }
        }
    }

    private fun done() {
        val text = result()
        matches.forEachIndexed { i, m -> NameStore.memory.add(choices[i], m.entity) }
        if (readOnly) {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText(NameStore.APP_NAME, text))
        } else {
            setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text))
        }
        banner.visibility = View.VISIBLE
        Ui.enter(banner, 0, rise = 6)
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1900)
        if (readOnly) Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }
}
