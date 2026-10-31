package org.naambadlo.keypad

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
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
import android.widget.TextView
import android.widget.Toast

/**
 * The one box: type, paste or upload text, then Encode (swap names for code names)
 * or Decode (code names back to real names). Short posts and whole speeches use the
 * same box. Used on Home, and by the share-sheet screens.
 */
class Workbench(private val act: Activity, startMode: Mode, private val scrollTo: (View) -> Unit) {

    enum class Mode { ENCODE, DECODE }

    companion object {
        const val REQ_OPEN = 41
        const val REQ_SAVE = 42
        private const val PREVIEW_CHARS = 20_000
    }

    var mode = startMode
        private set
    val view: LinearLayout = Ui.vertical(act)

    private val modePills = ArrayList<TextView>()
    private lateinit var input: EditText
    private lateinit var fileLabel: TextView
    private val ctaHolder = Ui.horizontal(act).apply { gravity = Gravity.END }
    private val result = Ui.vertical(act)

    private var sourceName = "text"
    private var lastOut: String? = null
    private val confirmed = HashSet<String>()
    private val declined = HashSet<String>()
    private val chosen = HashMap<String, String>()      // person id -> code name, same all the way through
    private val matcher by lazy { NameStore.matcher(act) }
    private val decoder by lazy { Decoder(NameStore.entities(act)) }

    // decode state
    private var decText = ""
    private var hits: List<Decoder.Hit> = emptyList()
    private val kept = HashSet<Int>()
    private var decOut: TextView? = null

    init { build() }

    // ---- Layout ----

    private fun build() {
        // Encode | Decode: the only choice on the screen
        val switch = Ui.horizontal(act).apply { setPadding(0, 0, 0, Ui.dp(act, 12)) }
        for ((m, label) in listOf(Mode.ENCODE to "Encode", Mode.DECODE to "Decode")) {
            val p = Ui.pill(act, label, m == mode) { setMode(m) }.apply { gravity = Gravity.CENTER }
            modePills.add(p)
            switch.addView(p, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(if (modePills.size == 1) 0 else Ui.dp(act, 6), 0, 0, 0)
            })
        }
        view.addView(switch)

        val card = Ui.card(act, 14)
        input = EditText(act).apply {
            typeface = Ui.SANS
            textSize = 16.5f
            setTextColor(Ui.INK); setHintTextColor(Ui.MUTED)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 5
            maxLines = 14
            isVerticalScrollBarEnabled = true
            gravity = Gravity.TOP or Gravity.START
            background = Ui.rounded(Ui.SOFT, 22, act)
            val p = Ui.dp(act, 16); setPadding(p, p, p, p)
        }
        card.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Small tools under the box
        val tools = Ui.horizontal(act).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(act, 2), Ui.dp(act, 10), Ui.dp(act, 2), 0)
        }
        fun tool(text: String, onClick: () -> Unit) = Ui.label(act, text, 14f, Ui.INK, Ui.W.MED).apply {
            background = Ui.rounded(Ui.SOFT, 18, act)
            setPadding(Ui.dp(act, 14), Ui.dp(act, 8), Ui.dp(act, 14), Ui.dp(act, 8))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, 0, Ui.dp(act, 8), 0) }
            setOnClickListener { onClick() }
            Ui.pressable(this)
        }
        tools.addView(tool("Paste") { paste() })
        tools.addView(tool("⬆  Upload") { openFile() })
        tools.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))
        tools.addView(Ui.label(act, "Clear", 14f, Ui.MUTED, Ui.W.MED).apply {
            setPadding(Ui.dp(act, 10), Ui.dp(act, 8), Ui.dp(act, 6), Ui.dp(act, 8))
            setOnClickListener { input.setText(""); result.removeAllViews(); fileLabel.visibility = View.GONE; lastOut = null }
        })
        card.addView(tools)
        fileLabel = Ui.label(act, "", 12.5f, Ui.MUTED, Ui.W.MED).apply {
            setPadding(Ui.dp(act, 4), Ui.dp(act, 8), 0, 0); visibility = View.GONE
        }
        card.addView(fileLabel)
        card.addView(ctaHolder.apply { setPadding(0, Ui.dp(act, 14), 0, 0) })
        view.addView(card)
        view.addView(result)
        paintMode()
    }

    fun setMode(m: Mode) {
        if (m == mode) return
        mode = m
        result.removeAllViews(); lastOut = null
        paintMode()
    }

    private fun paintMode() {
        modePills.forEachIndexed { i, p -> Ui.stylePill(p, Mode.values()[i] == mode) }
        input.hint = if (mode == Mode.ENCODE) "Type or paste your post, a speech or a whole script…"
                     else "Paste a message written in code names, like “Kamal Party ne kaha, Mota Bhai ne suna…”"
        ctaHolder.removeAllViews()
        val cta = Ui.cta(act, if (mode == Mode.ENCODE) "Make it Meta friendly" else "Decode message") { run() }
        cta.getChildAt(0).layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        ctaHolder.addView(cta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun setText(text: CharSequence, m: Mode, go: Boolean) {
        setMode(m)
        input.setText(text)
        sourceName = "shared-text"
        if (go) run()
    }

    private fun run() { if (mode == Mode.ENCODE) { chosen.clear(); encode() } else decode() }

    // ---- Input ----

    private fun paste() {
        val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(act)
        if (t.isNullOrEmpty()) toast("Clipboard is empty") else { input.setText(t); sourceName = "pasted-text" }
    }

    private fun openFile() {
        // Any file can be picked; unsupported types get a clear message instead.
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
        runCatching { act.startActivityForResult(i, REQ_OPEN) }.onFailure { toast("No file picker found on this phone") }
    }

    /** Call from the host Activity's onActivityResult. Returns true if handled. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQ_OPEN && requestCode != REQ_SAVE) return false
        val uri = data?.data ?: return true
        if (resultCode != Activity.RESULT_OK) return true
        if (requestCode == REQ_OPEN) loadUri(uri) else saveTo(uri)
        return true
    }

    private fun displayName(uri: Uri): String = runCatching {
        act.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "file"

    fun loadUri(uri: Uri) {
        val name = displayName(uri)
        val mime = act.contentResolver.getType(uri)
        fileLabel.text = "Opening $name…"; fileLabel.visibility = View.VISIBLE
        Thread {
            val res = runCatching {
                val bytes = readBytes(uri)
                if (bytes == null) {
                    // Google Docs and other cloud files: ask for a plain-text copy
                    exportAsText(uri)?.let { DocText.Result.Ok(it) }
                        ?: DocText.Result.Error("Couldn't open that file. For a Google Doc, open it in Docs → Share → Make it Meta friendly.")
                } else DocText.read(name, mime, bytes)
            }.getOrElse { DocText.Result.Error("Couldn't open that file.") }
            act.runOnUiThread {
                when (res) {
                    is DocText.Result.Ok -> {
                        input.setText(res.text)
                        sourceName = name.substringBeforeLast('.')
                        val words = res.text.split(Regex("\\s+")).count { it.isNotBlank() }
                        fileLabel.text = "$name · ${"%,d".format(words)} words"
                        result.removeAllViews(); lastOut = null
                    }
                    is DocText.Result.Error -> { fileLabel.text = res.message; toast(res.message) }
                }
            }
        }.start()
    }

    private fun readBytes(uri: Uri): ByteArray? = runCatching {
        act.contentResolver.openInputStream(uri)?.use { s ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > DocText.MAX_BYTES + 1) break
            }
            out.toByteArray()
        }
    }.getOrNull()

    private fun exportAsText(uri: Uri): String? = runCatching {
        val types = act.contentResolver.getStreamTypes(uri, "text/*") ?: return null
        val t = types.firstOrNull { it == "text/plain" } ?: types.firstOrNull() ?: return null
        act.contentResolver.openTypedAssetFileDescriptor(uri, t, null)?.createInputStream()?.use {
            String(it.readBytes(), Charsets.UTF_8)
        }
    }.getOrNull()

    // ---- Encode ----

    private data class Report(
        val out: String,
        val swapped: Int,
        val people: List<Pair<Entity, Int>>,
        val asks: List<Triple<String, Entity, Int>>,
    )

    private fun encode() {
        val text = input.text.toString()
        if (text.isBlank()) { toast("Type or paste some text first"); return }
        result.removeAllViews()
        Thread {
            val found = matcher.findAll(text, NameStore.memory)
            val counts = LinkedHashMap<String, Pair<Entity, Int>>()
            val asks = LinkedHashMap<String, Triple<String, Entity, Int>>()
            var swapped = 0
            val out = matcher.replaceAll(text, NameStore.memory, include = { m, _ ->
                val w = Matcher.normalize(text.substring(m.start, m.end))
                val ok = m.fromMemory || !m.ask || w in confirmed
                if (!ok && w !in declined) {
                    val prev = asks[w]
                    asks[w] = Triple(text.substring(m.start, m.end), m.entity, (prev?.third ?: 0) + 1)
                }
                if (ok) { swapped++; counts[m.entity.id] = m.entity to ((counts[m.entity.id]?.second ?: 0) + 1) }
                ok
            }, pick = { e, cur -> chosen.getOrPut(e.id) { NameStore.pick(e, cur) } })
            val rep = Report(out, swapped, counts.values.sortedByDescending { it.second }, asks.values.toList())
            act.runOnUiThread { showEncoded(rep, found.isEmpty()) }
        }.start()
    }

    private fun showEncoded(rep: Report, none: Boolean) {
        lastOut = rep.out
        result.removeAllViews()
        if (none) { result.addView(note("No names from the list in this text.")); return }
        if (rep.swapped > 0) Feedback.swapped(act)

        val done = resultCard(
            "✓  ${NameStore.CONGRATS}",
            "${rep.swapped} name(s) swapped" + if (rep.people.size > 1) " across ${rep.people.size} people" else "",
        )
        val preview = if (rep.out.length > PREVIEW_CHARS) rep.out.take(PREVIEW_CHARS) + "\n\n…" else rep.out
        done.addView(inner(Ui.label(act, preview, 15.5f, Ui.GRAD_INK).apply { setTextIsSelectable(true) }))
        done.addView(actions(
            "Copy" to { copy(lastOut) },
            "Fresh names" to { chosen.clear(); encode() },
            "Share" to { share() },
        ))
        if (rep.out.length > PREVIEW_CHARS) done.addView(Ui.label(act, "Long text: Copy or Share gives you all of it.", 12.5f, Ui.GRAD_SUB).apply {
            setPadding(Ui.dp(act, 2), Ui.dp(act, 8), 0, 0)
        })
        result.addView(done)

        if (rep.asks.isNotEmpty()) {
            val c = Ui.card(act, 18)
            c.addView(Ui.label(act, "Check these", 16f, Ui.INK, Ui.W.SEMI))
            c.addView(Ui.label(act, "These could be someone else, so they were left as they are.", 13f, Ui.MUTED).apply {
                setPadding(0, Ui.dp(act, 2), 0, Ui.dp(act, 4))
            })
            for ((w, e, n) in rep.asks) {
                val key = Matcher.normalize(w)
                c.addView(Ui.label(act, "“$w”${if (n > 1) " · $n times" else ""} · is it ${e.label}?", 14.5f).apply {
                    setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 6))
                })
                c.addView(Ui.horizontal(act).apply {
                    addView(Ui.pill(act, "Yes, swap", true) { confirmed.add(key); encode() })
                    addView(Ui.pill(act, "No, keep", false) { declined.add(key); encode() })
                })
            }
            result.addView(c)
        }

        if (rep.people.isNotEmpty()) {
            val c = Ui.card(act, 18)
            c.addView(Ui.label(act, "Code names used", 16f, Ui.INK, Ui.W.SEMI))
            for ((e, n) in rep.people) {
                val row = Ui.horizontal(act).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, Ui.dp(act, 10), 0, 0) }
                row.addView(Ui.vertical(act).apply {
                    addView(Ui.label(act, e.label, 15f, Ui.INK, Ui.W.MED))
                    addView(Ui.label(act, "→ ${chosen[e.id] ?: ""}${if (n > 1) " · $n times" else ""}", 13.5f, Ui.MUTED))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(Ui.pill(act, "Change", false) {
                    chosen[e.id] = NameStore.pick(e, chosen[e.id] ?: "")
                    encode()
                })
                c.addView(row)
            }
            result.addView(c)
        }
        reveal()
    }

    // ---- Decode ----

    private fun decode() {
        decText = input.text.toString()
        if (decText.isBlank()) { toast("Paste a message first"); return }
        hits = decoder.find(decText)
        kept.clear()
        result.removeAllViews()
        if (hits.isEmpty()) { result.addView(note("No code names found in this message.")); return }
        Feedback.swapped(act)
        val people = hits.map { it.real }.distinct()
        val done = resultCard(
            "✓  Decoded ${hits.size} code name(s)",
            people.take(5).joinToString(", ") + if (people.size > 5) "…" else "",
        )
        decOut = Ui.label(act, "", 15.5f, Ui.GRAD_INK).apply {
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = 0
        }
        done.addView(inner(decOut!!))
        done.addView(Ui.label(act, "Tap a highlighted name to keep the word as written.", 12.5f, Ui.GRAD_SUB).apply {
            setPadding(Ui.dp(act, 2), Ui.dp(act, 8), 0, 0)
        })
        done.addView(actions("Copy" to { copy(decoder.decode(decText, hits, kept)) }))
        result.addView(done)
        paintDecoded()
        reveal()
    }

    private fun paintDecoded() {
        val sb = SpannableStringBuilder()
        var last = 0
        for (h in hits) {
            sb.append(decText, last, h.start)
            val s = sb.length
            val isKept = h.start in kept
            sb.append(if (isKept) h.code else h.real)
            if (!isKept) {
                sb.setSpan(BackgroundColorSpan(0x337B61FF), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            sb.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) { if (!kept.add(h.start)) kept.remove(h.start); paintDecoded() }
                override fun updateDrawState(ds: TextPaint) { ds.isUnderlineText = isKept; ds.color = Ui.GRAD_INK }
            }, s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            last = h.end
        }
        sb.append(decText, last, decText.length)
        decOut?.text = sb
    }

    // ---- Pieces ----

    private fun resultCard(title: String, sub: String) = Ui.card(act, 16).apply {
        background = Ui.gradient(26, act)
        addView(Ui.label(act, title, 16f, Ui.GRAD_INK, Ui.W.SEMI))
        if (sub.isNotEmpty()) addView(Ui.label(act, sub, 13.5f, Ui.GRAD_SUB).apply { setPadding(0, Ui.dp(act, 2), 0, 0) })
    }

    private fun inner(v: TextView) = v.apply {
        background = Ui.rounded(0xE6FFFFFF.toInt(), 18, act)
        val p = Ui.dp(act, 14); setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(0, Ui.dp(act, 12), 0, 0) }
    }

    /** First action is the main one (dark), the rest are quiet. */
    private fun actions(vararg items: Pair<String, () -> Unit>) = Ui.horizontal(act).apply {
        setPadding(0, Ui.dp(act, 12), 0, 0)
        items.forEachIndexed { i, (label, go) ->
            addView(Ui.label(act, label, 15f, if (i == 0) Ui.WHITE else Ui.GRAD_INK, Ui.W.SEMI).apply {
                gravity = Gravity.CENTER
                background = Ui.rounded(if (i == 0) Ui.GRAD_INK else 0x99FFFFFF.toInt(), 22, act)
                setPadding(Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 8), Ui.dp(act, 12))
                setOnClickListener { go() }
                Ui.pressable(this)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, if (i == 0) 1.2f else 1f).apply {
                setMargins(if (i == 0) 0 else Ui.dp(act, 8), 0, 0, 0)
            })
        }
    }

    private fun note(text: String) = Ui.label(act, text, 15f, Ui.MUTED).apply {
        setPadding(Ui.dp(act, 6), Ui.dp(act, 16), 0, 0)
    }

    private fun reveal() {
        for (i in 0 until result.childCount) Ui.enter(result.getChildAt(i), i * 70L)
        result.post { scrollTo(result) }
    }

    // ---- Output ----

    private fun copy(text: String?) {
        text ?: return
        (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(NameStore.APP_NAME, text))
        toast("Copied. Paste it anywhere.")
    }

    private fun share() {
        val out = lastOut ?: return
        act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, out)
        }, "Share Meta-friendly text"))
    }

    private fun saveTo(uri: Uri) {
        val out = lastOut ?: return
        runCatching { act.contentResolver.openOutputStream(uri)!!.use { it.write(out.toByteArray(Charsets.UTF_8)) } }
            .onSuccess { toast("Saved") }.onFailure { toast("Couldn't save the file") }
    }

    private fun toast(msg: String) = Toast.makeText(act, msg, Toast.LENGTH_SHORT).show()
}
