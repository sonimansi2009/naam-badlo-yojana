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
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Works with ANY keyboard: select text, tap "Naam Chupao" in the pop-up menu,
 * pick code names (or shuffle as often as you like), then confirm.
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
        Ui.init(this)
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
        val root = Ui.vertical(this, 20).apply { background = Ui.pageBackground() }
        root.addView(Ui.headline(this, "Naam *Chupao*", 32f).apply {
            setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 14))
        })

        if (matches.isEmpty()) {
            root.addView(Ui.label(this, "No names from the list in this text.", 15f, Ui.MUTED))
            root.addView(Ui.fullWidth(Ui.darkButton(this, "Close") { finish() }, this, topDp = 16))
            return root
        }

        val card = Ui.card(this, 16)
        preview = Ui.label(this, result(), 17f).apply {
            background = Ui.rounded(Ui.SOFT, 20, this@ProcessTextActivity)
            val p = Ui.dp(this@ProcessTextActivity, 14)
            setPadding(p, p, p, p)
            setTextIsSelectable(true)
        }
        card.addView(preview)

        banner = Ui.label(this, "✓  ${NameStore.CONGRATS}", 14f, Ui.WHITE, Ui.W.MED).apply {
            background = Ui.rounded(Ui.BLACK, 18, this@ProcessTextActivity)
            val p = Ui.dp(this@ProcessTextActivity, 13)
            setPadding(p, p, p, p)
            visibility = View.GONE
        }
        card.addView(Ui.fullWidth(banner, this, topDp = 10))
        card.addView(Ui.spacer(this, 8))

        matches.forEachIndexed { i, m ->
            val word = original.substring(m.start, m.end)
            card.addView(Ui.label(this, "“$word” · ${m.entity.label}", 13f, Ui.MUTED, Ui.W.MED).apply {
                setPadding(Ui.dp(context, 4), Ui.dp(context, 8), 0, Ui.dp(context, 2))
            })
            val (scroll, row) = Ui.chipRow(this)
            for (alt in m.entity.alternates) {
                row.addView(Ui.chip(this, alt, selected = alt == choices[i]) {
                    choices[i] = alt
                    refresh()
                })
            }
            rows.add(row)
            card.addView(scroll)
        }
        root.addView(card)

        val bar = Ui.horizontal(this).apply { gravity = Gravity.END }
        bar.addView(Ui.lightButton(this, "Shuffle") { shuffle() }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, 0, Ui.dp(context, 8), 0) }
        })
        bar.addView(Ui.cta(this, if (readOnly) "Copy" else "Make it Meta friendly") { done() })
        root.addView(bar)

        Ui.enter(card, 0)
        Ui.enter(bar, 80)
        return ScrollView(this).apply { background = Ui.pageBackground(); addView(root) }
    }

    private fun shuffle() {
        for (i in choices.indices) choices[i] = Matcher.freshPick(matches[i].entity, choices[i])
        preview.animate().alpha(0.3f).setDuration(90).withEndAction {
            refresh()
            preview.animate().alpha(1f).setDuration(200).start()
        }.start()
    }

    private fun refresh() {
        preview.text = result()
        rows.forEachIndexed { i, row ->
            for (k in 0 until row.childCount) {
                val c = row.getChildAt(k) as TextView
                Ui.styleChip(c, c.text.toString() == choices[i])
            }
        }
    }

    private fun done() {
        val text = result()
        matches.forEachIndexed { i, m -> NameStore.memory.add(choices[i], m.entity) }
        if (readOnly) {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText(NameStore.APP_NAME, text))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        } else {
            setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text))
        }
        banner.visibility = View.VISIBLE
        Ui.enter(banner, 0, rise = 6)
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1900)
    }
}
