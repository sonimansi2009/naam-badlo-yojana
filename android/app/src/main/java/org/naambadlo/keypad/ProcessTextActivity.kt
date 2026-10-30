package org.naambadlo.keypad

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Works with ANY keyboard: select text, tap "Shadowban Suraksha" in the pop-up menu,
 * pick code names (or shuffle as often as you like), then confirm.
 * Bare first names / common surnames start as "keep as is" until you pick a code name.
 * Long-press a code name to star it.
 */
class ProcessTextActivity : Activity() {

    private lateinit var original: String
    private lateinit var matches: List<Match>
    private lateinit var choices: Array<String>     // "" = keep the original word
    private lateinit var preview: TextView
    private lateinit var banner: LinearLayout
    private val rows = ArrayList<LinearLayout>()
    private var readOnly = false
    private val handler = Handler(Looper.getMainLooper())
    private var finishing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        original = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""
        readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        matches = NameStore.matcher(this).findAll(original, NameStore.memory)
        choices = Array(matches.size) { i ->
            val m = matches[i]
            if (m.ask && !m.fromMemory) "" else NameStore.pick(m.entity, original.substring(m.start, m.end))
        }
        setContentView(build())
    }

    private fun word(i: Int) = original.substring(matches[i].start, matches[i].end)

    private fun result(): String {
        val sb = StringBuilder()
        var last = 0
        matches.forEachIndexed { i, m ->
            sb.append(original, last, m.start).append(choices[i].ifEmpty { word(i) })
            last = m.end
        }
        sb.append(original, last, original.length)
        return sb.toString()
    }

    private fun build(): View {
        val root = Ui.vertical(this, 20).apply { background = Ui.pageBackground() }
        root.addView(Ui.headline(this, "Shadowban *Suraksha*", 30f).apply {
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

        banner = Ui.horizontal(this).apply {
            background = Ui.rounded(Ui.META, 18, this@ProcessTextActivity)
            setPadding(Ui.dp(context, 14), Ui.dp(context, 8), Ui.dp(context, 8), Ui.dp(context, 8))
            visibility = View.GONE
            addView(Ui.label(context, "✓  ${NameStore.CONGRATS}", 14f, Ui.WHITE, Ui.W.MED),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Ui.label(context, "Undo", 14f, Ui.META, Ui.W.SEMI).apply {
                background = Ui.rounded(Ui.WHITE, 16, context)
                setPadding(Ui.dp(context, 14), Ui.dp(context, 7), Ui.dp(context, 14), Ui.dp(context, 7))
                setOnClickListener { undo() }
            })
        }
        card.addView(Ui.fullWidth(banner, this, topDp = 10))
        card.addView(Ui.spacer(this, 8))

        matches.forEachIndexed { i, m ->
            val w = word(i)
            val heading = if (m.ask && !m.fromMemory) "“$w” · is this ${m.entity.label}? Pick a code name if yes"
                          else "“$w” · ${m.entity.label}"
            card.addView(Ui.label(this, heading, 13f, Ui.MUTED, Ui.W.MED).apply {
                setPadding(Ui.dp(context, 4), Ui.dp(context, 8), 0, Ui.dp(context, 2))
            })
            val (scroll, row) = Ui.chipRow(this)
            if (m.ask && !m.fromMemory) {
                row.addView(Ui.chip(this, "Keep “$w”", selected = choices[i].isEmpty()) {
                    choices[i] = ""; refresh()
                }.apply { tag = "" })
            }
            val favs = Prefs.favourites(this, m.entity.id)
            for (alt in NameStore.ordered(this, m.entity)) {
                val chip = Ui.chip(this, if (alt in favs) "★ $alt" else alt, selected = alt == choices[i]) {
                    choices[i] = alt; refresh()
                }
                chip.tag = alt
                chip.setOnLongClickListener {
                    val added = Prefs.toggleFavourite(this, m.entity.id, alt)
                    chip.text = if (added) "★ $alt" else alt
                    chip.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    Toast.makeText(this, if (added) "Starred. Used first from now on." else "Unstarred", Toast.LENGTH_SHORT).show()
                    true
                }
                row.addView(chip)
            }
            rows.add(row)
            card.addView(scroll)
        }
        card.addView(Ui.label(this, "Tip: hold a code name to star it.", 12.5f, Ui.MUTED).apply {
            setPadding(Ui.dp(context, 4), Ui.dp(context, 10), 0, 0)
        })
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
        for (i in choices.indices) {
            if (choices[i].isEmpty()) continue           // leave "keep as is" alone
            choices[i] = NameStore.pick(matches[i].entity, choices[i])
        }
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
                Ui.styleChip(c, (c.tag as? String ?: "") == choices[i])
            }
        }
    }

    private fun done() {
        val text = result()
        matches.forEachIndexed { i, m -> if (choices[i].isNotEmpty()) NameStore.memory.add(choices[i], m.entity) }
        if (readOnly) {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText(NameStore.APP_NAME, text))
        } else {
            setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text))
        }
        banner.visibility = View.VISIBLE
        Ui.enter(banner, 0, rise = 6)
        finishing = true
        handler.postDelayed({ if (finishing) finish() }, 2600)
    }

    /** Stay open and put the original text back. */
    private fun undo() {
        finishing = false
        handler.removeCallbacksAndMessages(null)
        if (readOnly) {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText(NameStore.APP_NAME, original))
        } else {
            setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, original))
        }
        banner.visibility = View.GONE
        Toast.makeText(this, "Undone. Your text is unchanged.", Toast.LENGTH_SHORT).show()
    }
}
