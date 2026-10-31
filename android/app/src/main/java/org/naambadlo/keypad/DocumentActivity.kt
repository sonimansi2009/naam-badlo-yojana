package org.naambadlo.keypad

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Long scripts, notes and documents.
 * Paste text, open a .txt / .md / .docx file, or share text or a file into the app.
 * By default each person keeps one code name through the whole text, so long
 * pieces stay readable. Result: one-tap Copy, plus Share and Save as .txt.
 */
class DocumentActivity : Activity() {

    private lateinit var input: EditText
    private lateinit var fileLabel: TextView
    private lateinit var result: LinearLayout
    private lateinit var scroll: ScrollView
    private var sourceName = "text"
    private var lastOut: String? = null
    private var samePerPerson = true
    private val confirmed = HashSet<String>()
    private val declined = HashSet<String>()
    private val chosen = HashMap<String, String>()     // person id -> code name (same-per-person mode)
    private val matcher by lazy { NameStore.matcher(this) }

    companion object {
        private const val REQ_OPEN = 41
        private const val REQ_SAVE = 42
        private const val PREVIEW_CHARS = 20_000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        NameStore.matcher(this)
        window.statusBarColor = Ui.BG_TOP
        window.navigationBarColor = Ui.BG

        val col = Ui.vertical(this).apply {
            setPadding(Ui.dp(context, 20), Ui.dp(context, 14), Ui.dp(context, 20), Ui.dp(context, 40))
        }
        col.addView(Ui.horizontal(this).apply {
            addView(Ui.label(context, "←", 22f, Ui.INK, Ui.W.MED).apply {
                gravity = Gravity.CENTER
                background = Ui.oval(Ui.SURFACE)
                layoutParams = LinearLayout.LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44))
                setOnClickListener { finish() }
                Ui.pressable(this)
            })
        })
        col.addView(Ui.headline(this, "Long *text*", 42f).apply {
            setPadding(0, Ui.dp(context, 22), 0, Ui.dp(context, 6))
        })
        col.addView(Ui.label(this,
            "Speeches, scripts, notes and documents. Paste text, or open a .txt, .md or Word (.docx) file.",
            14f, Ui.MUTED).apply { setPadding(0, 0, 0, Ui.dp(context, 18)) })

        val card = Ui.card(this, 16)
        val actions = Ui.horizontal(this)
        actions.addView(Ui.lightButton(this, "Upload") { openFile() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, Ui.dp(context, 6), 0) }
        })
        actions.addView(Ui.lightButton(this, "Paste") { pasteClipboard() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(Ui.dp(context, 6), 0, Ui.dp(context, 6), 0) }
        })
        actions.addView(Ui.lightButton(this, "Clear") { input.setText(""); result.removeAllViews(); fileLabel.visibility = View.GONE }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f).apply { setMargins(Ui.dp(context, 6), 0, 0, 0) }
        })
        card.addView(actions)
        fileLabel = Ui.label(this, "", 13f, Ui.MUTED, Ui.W.MED).apply {
            setPadding(Ui.dp(context, 4), Ui.dp(context, 10), 0, 0); visibility = View.GONE
        }
        card.addView(fileLabel)
        input = EditText(this).apply {
            hint = "Paste your script here…"
            typeface = Ui.SANS
            textSize = 16f
            setTextColor(Ui.INK); setHintTextColor(Ui.MUTED)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 8
            gravity = Gravity.TOP or Gravity.START
            background = Ui.rounded(Ui.SOFT, 20, this@DocumentActivity)
            val p = Ui.dp(this@DocumentActivity, 16); setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, Ui.dp(context, 12), 0, 0) }
        }
        card.addView(input)
        card.addView(Switch(this).apply {
            text = "Same code name for each person"
            typeface = Ui.SANS_MEDIUM; textSize = 15f; setTextColor(Ui.INK)
            isChecked = samePerPerson
            setPadding(Ui.dp(context, 4), Ui.dp(context, 14), 0, 0)
            setOnCheckedChangeListener { _, v -> samePerPerson = v; if (lastOut != null) convert() }
        })
        card.addView(Ui.label(this, "Keeps long pieces readable: Modi is the same code name from start to end.", 12.5f, Ui.MUTED).apply {
            setPadding(Ui.dp(context, 4), Ui.dp(context, 2), 0, 0)
        })
        card.addView(Ui.horizontal(this).apply {
            gravity = Gravity.END
            setPadding(0, Ui.dp(context, 16), 0, 0)
            addView(Ui.cta(context, "Make it Meta friendly") { chosen.clear(); convert() })
        })
        col.addView(card)

        result = Ui.vertical(this)
        col.addView(result)

        scroll = ScrollView(this).apply { background = Ui.pageBackground(); isVerticalScrollBarEnabled = false; addView(col) }
        setContentView(scroll)
        Ui.enter(card, 0)
        handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncoming(it) }
    }

    /** Text or a file shared from another app (Notes, Keep, Docs, Files…). */
    private fun handleIncoming(i: Intent) {
        if (i.action != Intent.ACTION_SEND) return
        @Suppress("DEPRECATION")
        val uri = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (uri != null) { loadUri(uri); return }
        i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let { input.setText(it); sourceName = "shared-text" }
    }

    private fun openFile() {
        // Any file can be picked (some phones label .md or .docx oddly and grey them out
        // when the list is filtered). Unsupported types get a clear message instead.
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        runCatching { startActivityForResult(i, REQ_OPEN) }
            .onFailure { toast("No file picker found on this phone") }
    }

    private fun pasteClipboard() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)
        if (t.isNullOrEmpty()) toast("Clipboard is empty") else { input.setText(t); sourceName = "pasted-text" }
    }

    @Deprecated("Activity result API kept simple for minSdk 24")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_OPEN -> loadUri(uri)
            REQ_SAVE -> saveTo(uri)
        }
    }

    private fun displayName(uri: Uri): String =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "file"

    private fun loadUri(uri: Uri) {
        val name = displayName(uri)
        val mime = contentResolver.getType(uri)
        fileLabel.text = "Opening $name…"; fileLabel.visibility = View.VISIBLE
        Thread {
            val res = runCatching {
                val bytes = readBytes(uri)
                // Google Docs and other cloud files: ask the provider for a plain-text copy
                if (bytes == null) {
                    val txt = exportAsText(uri)
                    if (txt != null) DocText.Result.Ok(txt)
                    else DocText.Result.Error("Couldn't open that file. For a Google Doc, open it in Docs → Share → Make it Meta friendly.")
                } else DocText.read(name, mime, bytes)
            }.getOrElse { DocText.Result.Error("Couldn't open that file.") }
            runOnUiThread {
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
        contentResolver.openInputStream(uri)?.use { s ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > DocText.MAX_BYTES + 1) break     // stop early on huge files
            }
            out.toByteArray()
        }
    }.getOrNull()

    private fun exportAsText(uri: Uri): String? = runCatching {
        val types = contentResolver.getStreamTypes(uri, "text/*") ?: return null
        val t = types.firstOrNull { it == "text/plain" } ?: types.firstOrNull() ?: return null
        contentResolver.openTypedAssetFileDescriptor(uri, t, null)?.createInputStream()?.use {
            String(it.readBytes(), Charsets.UTF_8)
        }
    }.getOrNull()

    // ---- Converting ----

    private data class Report(
        val out: String,
        val swapped: Int,
        val people: List<Pair<Entity, Int>>,
        val asks: List<Triple<String, Entity, Int>>,   // word, person, count
    )

    private fun convert() {
        val text = input.text.toString()
        if (text.isBlank()) { toast("Paste some text or open a file first"); return }
        result.removeAllViews()
        result.addView(Ui.label(this, "Working…", 14f, Ui.MUTED).apply { setPadding(Ui.dp(context, 4), Ui.dp(context, 12), 0, 0) })
        val same = samePerPerson
        Thread {
            val found = matcher.findAll(text, NameStore.memory)
            fun word(m: Match) = Matcher.normalize(text.substring(m.start, m.end))
            val counts = LinkedHashMap<String, Pair<Entity, Int>>()
            val asks = LinkedHashMap<String, Triple<String, Entity, Int>>()
            var swapped = 0
            val out = matcher.replaceAll(text, NameStore.memory, include = { m, _ ->
                val w = word(m)
                val ok = m.fromMemory || !m.ask || w in confirmed
                if (!ok && w !in declined) {
                    val prev = asks[w]
                    asks[w] = Triple(text.substring(m.start, m.end), m.entity, (prev?.third ?: 0) + 1)
                }
                if (ok) {
                    swapped++
                    counts[m.entity.id] = m.entity to ((counts[m.entity.id]?.second ?: 0) + 1)
                }
                ok
            }, pick = { e, cur ->
                if (same) chosen.getOrPut(e.id) { NameStore.pick(e, cur) } else NameStore.pick(e, cur)
            })
            val rep = Report(out, swapped, counts.values.sortedByDescending { it.second }, asks.values.toList())
            runOnUiThread { show(rep, found.isEmpty()) }
        }.start()
    }

    private fun show(rep: Report, none: Boolean) {
        lastOut = rep.out
        result.removeAllViews()
        if (none) {
            result.addView(Ui.card(this, 18).apply {
                addView(Ui.label(context, "No names from the list in this text.", 15f, Ui.MUTED))
            })
            return
        }

        if (rep.swapped > 0) Feedback.swapped(this)

        // Congratulations + one-tap Copy
        val done = Ui.card(this, 16).apply { background = Ui.gradient(26, context) }
        done.addView(Ui.label(this, "✓  ${NameStore.CONGRATS}", 16f, Ui.WHITE, Ui.W.SEMI))
        done.addView(Ui.label(this, "${rep.swapped} name(s) swapped across ${rep.people.size} people.", 13.5f, 0xFFDCE7FF.toInt()).apply {
            setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 12))
        })
        done.addView(Ui.label(this, "Copy text", 16f, Ui.META, Ui.W.SEMI).apply {
            gravity = Gravity.CENTER
            background = Ui.rounded(Ui.WHITE, 24, context)
            setPadding(0, Ui.dp(context, 14), 0, Ui.dp(context, 14))
            setOnClickListener { copy() }
            Ui.pressable(this)
        })
        result.addView(done)

        // Bare first names / surnames still waiting for a yes or no
        if (rep.asks.isNotEmpty()) {
            val c = Ui.card(this, 18)
            c.addView(Ui.label(this, "Check these", 17f, Ui.INK, Ui.W.SEMI))
            c.addView(Ui.label(this, "These could be someone else, so they were left as they are.", 13f, Ui.MUTED).apply {
                setPadding(0, Ui.dp(context, 2), 0, Ui.dp(context, 8))
            })
            for ((w, e, n) in rep.asks) {
                val key = Matcher.normalize(w)
                c.addView(Ui.label(this, "“$w” · $n time(s) · is it ${e.label}?", 14.5f).apply {
                    setPadding(0, Ui.dp(context, 10), 0, Ui.dp(context, 6))
                })
                val row = Ui.horizontal(this)
                row.addView(Ui.pill(this, "Yes, swap", true) { confirmed.add(key); convert() })
                row.addView(Ui.pill(this, "No, keep", false) { declined.add(key); convert() })
                c.addView(row)
            }
            result.addView(c)
        }

        // Who was swapped, with their code name (change it in one tap)
        if (rep.people.isNotEmpty()) {
            val c = Ui.card(this, 18)
            c.addView(Ui.label(this, if (samePerPerson) "Code names used" else "People swapped", 17f, Ui.INK, Ui.W.SEMI))
            for ((e, n) in rep.people) {
                val row = Ui.horizontal(this).apply { setPadding(0, Ui.dp(context, 12), 0, 0) }
                row.addView(Ui.vertical(this).apply {
                    addView(Ui.label(context, e.label, 15f, Ui.INK, Ui.W.MED))
                    addView(Ui.label(context, if (samePerPerson) "→ ${chosen[e.id] ?: ""} · $n time(s)" else "$n time(s)", 13.5f, Ui.MUTED))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (samePerPerson) row.addView(Ui.pill(this, "Change", false) {
                    chosen[e.id] = NameStore.pick(e, chosen[e.id] ?: "")
                    convert()
                })
                c.addView(row)
            }
            result.addView(c)
        }

        // The converted text
        val t = Ui.card(this, 16)
        t.addView(Ui.label(this, "Meta-friendly text", 17f, Ui.INK, Ui.W.SEMI).apply { setPadding(Ui.dp(context, 4), 0, 0, Ui.dp(context, 10)) })
        val preview = if (rep.out.length > PREVIEW_CHARS) rep.out.take(PREVIEW_CHARS) + "\n\n…" else rep.out
        t.addView(Ui.label(this, preview, 15.5f).apply {
            background = Ui.rounded(Ui.SOFT, 18, context)
            val p = Ui.dp(context, 14); setPadding(p, p, p, p)
            setTextIsSelectable(true)
        })
        if (rep.out.length > PREVIEW_CHARS) t.addView(Ui.label(this,
            "Showing the start. Copy, Share or Save gives you all of it.", 12.5f, Ui.MUTED).apply { setPadding(Ui.dp(context, 4), Ui.dp(context, 8), 0, 0) })
        val bar = Ui.horizontal(this).apply { setPadding(0, Ui.dp(context, 14), 0, 0) }
        fun grow(v: View, l: Int, r: Int) = v.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(Ui.dp(context, l), 0, Ui.dp(context, r), 0) }
        }
        bar.addView(grow(Ui.lightButton(this, "Fresh names") { chosen.clear(); convert() }, 0, 4))
        bar.addView(grow(Ui.lightButton(this, "Share") { share() }, 4, 4))
        bar.addView(grow(Ui.lightButton(this, "Save .txt") { save() }, 4, 0))
        t.addView(bar)
        t.addView(Ui.fullWidth(Ui.cta(this, "Copy text") { copy() }.apply {
            getChildAt(0).layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }, this, topDp = 10))
        result.addView(t)

        for (i in 0 until result.childCount) Ui.enter(result.getChildAt(i), i * 70L)
        result.post { scroll.smoothScrollTo(0, result.top) }
    }

    // ---- Output ----

    private fun copy() {
        val out = lastOut ?: return
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(NameStore.APP_NAME, out))
        toast("Copied. Paste it anywhere.")
    }

    private fun share() {
        val out = lastOut ?: return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, out)
        }, "Share Meta-friendly text"))
    }

    private fun save() {
        if (lastOut == null) return
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "$sourceName-meta-friendly.txt")
        }
        runCatching { startActivityForResult(i, REQ_SAVE) }.onFailure { toast("Couldn't open the save screen") }
    }

    private fun saveTo(uri: Uri) {
        val out = lastOut ?: return
        runCatching {
            contentResolver.openOutputStream(uri)!!.use { it.write(out.toByteArray(Charsets.UTF_8)) }
        }.onSuccess { toast("Saved") }.onFailure { toast("Couldn't save the file") }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
