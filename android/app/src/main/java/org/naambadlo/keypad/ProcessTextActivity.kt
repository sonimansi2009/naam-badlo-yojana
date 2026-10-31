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
 * pick code names (or shuffle), then confirm or copy.
 * One row per person, so long selections stay short; each person keeps one code name.
 * Bare first names / common surnames start as "keep as is" until you pick a code name.
 * Long-press a code name to star it.
 */
class ProcessTextActivity : Activity() {

    /** One row per person (or per bare name that needs a yes). */
    private class Group(val key: String, val entity: Entity, val word: String, val ask: Boolean, var count: Int)

    private lateinit var original: String
    private lateinit var matches: List<Match>
    private lateinit var groupOf: IntArray          // match index -> group index
    private val groups = ArrayList<Group>()
    private lateinit var choices: Array<String>     // per group; "" = keep the original words
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
        val index = HashMap<String, Int>()
        groupOf = IntArray(matches.size) { i ->
            val m = matches[i]
            val w = original.substring(m.start, m.end)
            val ask = m.ask && !m.fromMemory
            val key = if (ask) "ask:" + Matcher.normalize(w) else m.entity.id
            index[key]?.also { groups[it].count++ } ?: run {
                groups.add(Group(key, m.entity, w, ask, 1)); index[key] = groups.size - 1; groups.size - 1
            }
        }
        choices = Array(groups.size) { g ->
            val gr = groups[g]
            if (gr.ask) "" else NameStore.pickPlain(gr.entity, gr.word)
        }
        setContentView(build())
    }

    /** Code name as it goes into the text: with the symbol layer (K@mal P@rty), the same each time. */
    private val shownAs = HashMap<String, String>()
    private fun shown(c: String) = shownAs.getOrPut(c) { NameStore.maybeDisguise(c) }

    private fun result(): String {
        val sb = StringBuilder()
        var last = 0
        matches.forEachIndexed { i, m ->
            val c = choices[groupOf[i]]
            sb.append(original, last, m.start).append(if (c.isEmpty()) original.substring(m.start, m.end) else shown(c))
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
            maxLines = 14
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        card.addView(preview)

        banner = Ui.horizontal(this).apply {
            background = Ui.gradient(18, this@ProcessTextActivity)
            setPadding(Ui.dp(context, 14), Ui.dp(context, 8), Ui.dp(context, 8), Ui.dp(context, 8))
            visibility = View.GONE
            addView(Ui.label(context, "✓  ${NameStore.CONGRATS}", 14f, Ui.GRAD_INK, Ui.W.MED),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(smallWhite("Copy") { copy(true) })
            addView(smallWhite("Undo") { undo() })
        }
        card.addView(Ui.fullWidth(banner, this, topDp = 10))
        card.addView(Ui.spacer(this, 8))

        groups.forEachIndexed { g, gr ->
            val times = if (gr.count > 1) " · ${gr.count}×" else ""
            val heading = if (gr.ask) "“${NameStore.mask(gr.word)}”$times · is this ${NameStore.mask(gr.entity.label)}? Pick a code name if yes"
                          else "${NameStore.mask(gr.entity.label)}$times"
            card.addView(Ui.label(this, heading, 13f, Ui.MUTED, Ui.W.MED).apply {
                setPadding(Ui.dp(context, 4), Ui.dp(context, 8), 0, Ui.dp(context, 2))
            })
            val (scroll, row) = Ui.chipRow(this)
            if (gr.ask) {
                row.addView(Ui.chip(this, "Keep “${gr.word}”", selected = choices[g].isEmpty()) {
                    choices[g] = ""; refresh()
                }.apply { tag = "" })
            }
            val favs = Prefs.favourites(this, gr.entity.id)
            for (alt in NameStore.ordered(this, gr.entity)) {
                val chip = Ui.chip(this, if (alt in favs) "★ $alt" else alt, selected = alt == choices[g]) {
                    choices[g] = alt; refresh()
                }
                chip.tag = alt
                chip.setOnLongClickListener {
                    val added = Prefs.toggleFavourite(this, gr.entity.id, alt)
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
        bar.addView(Ui.lightButton(this, "Copy") { copy(false) }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, 0, Ui.dp(context, 8), 0) }
        })
        bar.addView(Ui.cta(this, if (readOnly) "Copy" else "Make it Meta-proof") { done() })
        root.addView(bar)

        Ui.enter(card, 0)
        Ui.enter(bar, 80)
        return ScrollView(this).apply { background = Ui.pageBackground(); addView(root) }
    }

    private fun smallWhite(text: String, onClick: () -> Unit) = Ui.label(this, text, 14f, Ui.WHITE, Ui.W.SEMI).apply {
        background = Ui.rounded(Ui.GRAD_INK, 16, this@ProcessTextActivity)
        setPadding(Ui.dp(context, 14), Ui.dp(context, 7), Ui.dp(context, 14), Ui.dp(context, 7))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(Ui.dp(context, 6), 0, 0, 0) }
        setOnClickListener { onClick() }
    }

    private fun shuffle() {
        for (g in choices.indices) {
            if (choices[g].isEmpty()) continue           // leave "keep as is" alone
            choices[g] = NameStore.pickPlain(groups[g].entity, choices[g])
        }
        preview.animate().alpha(0.3f).setDuration(90).withEndAction {
            refresh()
            preview.animate().alpha(1f).setDuration(200).start()
        }.start()
    }

    private fun refresh() {
        preview.text = result()
        rows.forEachIndexed { g, row ->
            for (k in 0 until row.childCount) {
                val c = row.getChildAt(k) as TextView
                Ui.styleChip(c, (c.tag as? String ?: "") == choices[g])
            }
        }
    }

    /** Copy the converted text. While the confirmation is showing, also keep the window open. */
    private fun copy(fromBanner: Boolean) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(NameStore.APP_NAME, result()))
        Toast.makeText(this, "Copied. Paste it anywhere.", Toast.LENGTH_SHORT).show()
        if (fromBanner) { finishing = false; handler.removeCallbacksAndMessages(null); handler.postDelayed({ finish() }, 900) }
    }

    private fun done() {
        val text = result()
        groups.forEachIndexed { g, gr -> if (choices[g].isNotEmpty()) NameStore.memory.add(shown(choices[g]), gr.entity) }
        if (readOnly) {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText(NameStore.APP_NAME, text))
        } else {
            setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text))
        }
        banner.visibility = View.VISIBLE
        Ui.enter(banner, 0, rise = 6)
        Feedback.encrypted(this)
        finishing = true
        handler.postDelayed({ if (finishing) finish() }, 3200)
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
