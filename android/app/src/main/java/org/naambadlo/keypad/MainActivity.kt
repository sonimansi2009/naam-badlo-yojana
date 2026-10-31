package org.naambadlo.keypad

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Home (one box: Encode or Decode) and Settings, behind a floating bottom bar. Reach test sits inside Settings. */
class MainActivity : Activity() {

    private enum class Screen { HOME, TEST, SETTINGS }

    /** The Lite build has no accessibility service, so no floating button. */
    private val hasFloating = BuildConfig.HAS_FLOATING

    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var statusPill: TextView
    private lateinit var navItems: Map<Screen, LinearLayout>
    private var screen = Screen.HOME
    private lateinit var bench: Workbench

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        NameStore.matcher(this)
        window.statusBarColor = Ui.BG_TOP
        window.navigationBarColor = Ui.BG

        val root = FrameLayout(this).apply { background = Ui.pageBackground() }
        val column = Ui.vertical(this).apply {
            setPadding(Ui.dp(context, 20), Ui.dp(context, 14), Ui.dp(context, 20), Ui.dp(context, 130))
        }
        column.addView(topBar())
        content = Ui.vertical(this)
        bench = Workbench(this, Workbench.Mode.ENCODE) { v -> scroll.smoothScrollTo(0, content.top + (v.parent as View).top + v.top - Ui.dp(this, 12)) }
        column.addView(content)
        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(column) }
        root.addView(scroll)

        root.addView(bottomBar(), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ).apply { setMargins(Ui.dp(this@MainActivity, 18), 0, Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 18)) })

        setContentView(root)
        render(animate = true)
        TutorialActivity.maybeShow(this)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        if (screen == Screen.SETTINGS) render(animate = false)
    }

    // ---- Chrome ----

    private fun topBar(): View {
        statusPill = Ui.label(this, "", 14f, Ui.INK, Ui.W.MED).apply {
            gravity = Gravity.CENTER
            setPadding(Ui.dp(context, 14), Ui.dp(context, 8), Ui.dp(context, 14), Ui.dp(context, 8))
            background = Ui.rounded(Ui.SURFACE, 20, context)
            setOnClickListener { if (screen != Screen.SETTINGS) { screen = Screen.SETTINGS; render(animate = true) } }
            Ui.pressable(this)
        }
        updateStatus()
        return FrameLayout(this).apply {
            visibility = if (hasFloating) View.VISIBLE else View.GONE
            addView(statusPill, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
    }

    private fun updateStatus() {
        if (!::statusPill.isInitialized) return
        if (!hasFloating) {
            statusPill.text = "Lite version"
            statusPill.setTextColor(Ui.MUTED)
            return
        }
        val on = Prefs.floatingEnabled(this)
        statusPill.text = if (on) "●  Button is on" else "○  Turn on button  ›"
        statusPill.setTextColor(if (on) Ui.GOOD else Ui.META)
    }

    private fun bottomBar(): View {
        val bar = Ui.horizontal(this).apply {
            background = Ui.rounded(Ui.SURFACE, 34, context)
            elevation = Ui.dp(context, 10).toFloat()
            setPadding(Ui.dp(context, 8), Ui.dp(context, 8), Ui.dp(context, 8), Ui.dp(context, 8))
        }
        val items = linkedMapOf(
            Screen.HOME to (R.drawable.ic_home to "Home"),
            Screen.SETTINGS to (R.drawable.ic_tune to "Settings"),
        )
        val made = HashMap<Screen, LinearLayout>()
        for ((s, pair) in items) {
            val item = Ui.vertical(this).apply {
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(context, 8), 0, Ui.dp(context, 8))
                addView(Ui.icon(context, pair.first, Ui.INK, 22).apply {
                    (layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER_HORIZONTAL
                })
                addView(Ui.label(context, pair.second, 11.5f, Ui.INK, Ui.W.MED).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, Ui.dp(context, 2), 0, 0)
                })
                isClickable = true
                setOnClickListener { if (screen != s) { screen = s; render(animate = true) } }
                Ui.pressable(this)
            }
            bar.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            made[s] = item
        }
        navItems = made
        return bar
    }

    private fun paintNav() {
        for ((s, item) in navItems) {
            val active = s == screen || (s == Screen.SETTINGS && screen == Screen.TEST)
            item.background = if (active) Ui.rounded(Ui.SOFT, 22, this) else null
            (item.getChildAt(0) as? ImageView)?.imageTintList = ColorStateList.valueOf(if (active) Ui.INK else Ui.MUTED)
            (item.getChildAt(1) as? TextView)?.setTextColor(if (active) Ui.INK else Ui.MUTED)
        }
    }

    private fun render(animate: Boolean) {
        content.removeAllViews()
        paintNav()
        when (screen) {
            Screen.HOME -> home()
            Screen.TEST -> reachTest()
            Screen.SETTINGS -> settings()
        }
        if (animate) {
            scroll.scrollTo(0, 0)
            for (i in 0 until content.childCount) Ui.enter(content.getChildAt(i), i * 60L)
        }
    }

    private fun headline(text: String, size: Float = 44f) = Ui.headline(this, text, size).apply {
        setPadding(0, Ui.dp(context, 26), 0, Ui.dp(context, 6))
    }

    private fun sub(text: String) = Ui.label(this, text, 14f, Ui.MUTED).apply {
        setPadding(0, 0, 0, Ui.dp(context, 20))
    }

    // ---- Home: one box ----

    private fun home() {
        content.addView(SheenImageView(this, R.drawable.seal_white).apply {
            contentDescription = "Shadowban Suraksha Yojana seal: a chameleon on a lotus"
            layoutParams = LinearLayout.LayoutParams(Ui.dp(context, 140), Ui.dp(context, 140)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, Ui.dp(context, 2), 0, 0)
            }
        })
        content.addView(Ui.label(this, "Making censorship work harder,\none word at a time.", 18f, Ui.INK).apply {
            typeface = Ui.SERIF_ITALIC
            gravity = Gravity.CENTER
            alpha = 0.88f
            setLineSpacing(0f, 1.08f)
            setPadding(0, Ui.dp(context, 2), 0, Ui.dp(context, 18))
        })
        (bench.view.parent as? ViewGroup)?.removeView(bench.view)
        content.addView(bench.view)
    }

    private fun cardHead(iconRes: Int, title: String, sub: String) = Ui.horizontal(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(Ui.iconCircle(context, iconRes, Ui.SOFT, Ui.INK, 40))
        addView(Ui.vertical(context).apply {
            setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 8), 0)
            addView(Ui.label(context, title, 16f, Ui.INK, Ui.W.SEMI))
            addView(Ui.label(context, sub, 13f, Ui.MUTED))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    /** Set up once: the ways to use it inside other apps. */
    private fun waysCard(): View {
        val card = Ui.card(this, 18)
        card.addView(Ui.label(this, "Use it in Instagram & WhatsApp", 17f, Ui.INK, Ui.W.SEMI))
        card.addView(Ui.label(this, "Set up once. Then you never need to open this app.", 13f, Ui.MUTED).apply {
            setPadding(0, Ui.dp(context, 2), 0, Ui.dp(context, 6))
        })
        fun row(icon: Int, title: String, sub: String, action: String?, go: () -> Unit) {
            card.addView(cardHead(icon, title, sub).apply {
                setPadding(0, Ui.dp(context, 12), 0, 0)
                if (action != null) addView(Ui.pill(context, action, false) { go() })
            })
        }
        if (hasFloating) {
            val on = Prefs.floatingEnabled(this)
            row(R.drawable.ic_sparkle, "Floating button" + if (on) " · on" else "",
                "Type a name in any app, tap the button that appears.", if (on) "Settings" else "Turn on") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            if (!on) card.addView(Ui.label(this,
                "If Android says “Restricted setting”: App info → ⋮ → Allow restricted settings.",
                12.5f, Ui.MUTED).apply { setPadding(Ui.dp(context, 52), Ui.dp(context, 6), 0, 0) })
        }
        row(R.drawable.ic_keyboard, "Keyboard", "After typing a name, switch to it and tap the button.", "Turn on") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        row(R.drawable.ic_text, "Select text", "Select text in any app → ⋮ → Shadowban Suraksha.", null) {}
        if (!hasFloating) card.addView(Ui.label(this,
            "Want the floating button? It needs the full version, which Android allows after you pause Play Protect once during install.",
            12.5f, Ui.MUTED).apply { setPadding(0, Ui.dp(context, 14), 0, 0) })
        return card
    }

    private fun step(n: Int, s: String, first: Boolean) = Ui.horizontal(this).apply {
        gravity = Gravity.TOP
        setPadding(0, if (first) 0 else Ui.dp(context, 10), 0, 0)
        addView(Ui.label(context, "$n", 12f, Ui.INK, Ui.W.SEMI).apply {
            gravity = Gravity.CENTER
            background = Ui.oval(Ui.SURFACE)
            layoutParams = LinearLayout.LayoutParams(Ui.dp(context, 24), Ui.dp(context, 24))
        })
        addView(Ui.label(context, s, 15f).apply { setPadding(Ui.dp(context, 12), Ui.dp(context, 1), 0, 0) })
    }

    /** Recognisable (default) vs Stealth code names. */
    private fun styleCard(): View {
        val card = Ui.card(this, 18)
        card.addView(Ui.label(this, "Code name style", 17f, Ui.INK, Ui.W.SEMI))
        val desc = Ui.label(this, "", 13.5f, Ui.MUTED).apply { setPadding(0, Ui.dp(context, 2), 0, Ui.dp(context, 12)) }
        card.addView(desc)
        val (row, pills) = Ui.chipRow(this)
        val opts = listOf(
            Prefs.Tier.RECOGNISABLE to "Recognisable",
            Prefs.Tier.MIX to "Mix",
            Prefs.Tier.STEALTH to "Stealth",
        )
        fun describe(t: Prefs.Tier) = when (t) {
            Prefs.Tier.RECOGNISABLE -> "Names your readers already know, like Feku, Mota Bhai, Mama ji. Best for being understood."
            Prefs.Tier.MIX -> "Any of the 50 per name. More variety, a little harder to read."
            Prefs.Tier.STEALTH -> "Sound-alikes like Bodi, Gadani, Badda. Harder for filters, a little harder for readers."
        }
        val icons = mapOf(Prefs.Tier.RECOGNISABLE to R.drawable.ic_eye, Prefs.Tier.MIX to R.drawable.ic_shuffle, Prefs.Tier.STEALTH to R.drawable.ic_eye_off)
        val made = ArrayList<LinearLayout>()
        fun paint(v: LinearLayout, on: Boolean) {
            v.background = Ui.rounded(if (on) 0xFF353A46.toInt() else Ui.SOFT, 22, this, if (on) 0xFF5A6070.toInt() else null)
            val c = if (on) Ui.INK else Ui.MUTED
            (v.getChildAt(0) as ImageView).imageTintList = ColorStateList.valueOf(c)
            (v.getChildAt(1) as TextView).setTextColor(c)
        }
        for ((t, label) in opts) {
            val p = Ui.horizontal(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(context, 12), Ui.dp(context, 8), Ui.dp(context, 14), Ui.dp(context, 8))
                addView(Ui.icon(context, icons.getValue(t), Ui.MUTED, 17))
                addView(Ui.label(context, label, 14f, Ui.MUTED, Ui.W.MED).apply { setPadding(Ui.dp(context, 6), 0, 0, 0) })
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(0, 0, Ui.dp(context, 8), 0) }
                isClickable = true
                setOnClickListener {
                    Prefs.setTier(this@MainActivity, t)
                    made.forEachIndexed { i, v -> paint(v, opts[i].first == t) }
                    desc.text = describe(t)
                }
                Ui.pressable(this)
            }
            paint(p, Prefs.tier(this) == t)
            made.add(p); pills.addView(p)
        }
        desc.text = describe(Prefs.tier(this))
        card.addView(row)
        card.addView(Ui.label(this, "Starred favourites are always used first. Star one by holding a code name on the keyboard.", 12.5f, Ui.MUTED).apply {
            setPadding(0, Ui.dp(context, 12), 0, 0)
        })
        return card
    }

    // ---- Reach test ----

    private fun reachTest() {
        content.addView(headline("Does it *work?*", 44f))
        content.addView(sub("Post the same thing twice, once plain and once Meta-proof, and compare reach."))

        val c1 = Ui.card(this, 18)
        c1.addView(Ui.label(this, "1. Make a test pair", 17f, Ui.INK, Ui.W.SEMI))
        c1.addView(Ui.label(this, "Write a post with names. You get two versions to copy.", 13f, Ui.MUTED).apply {
            setPadding(0, Ui.dp(context, 2), 0, Ui.dp(context, 10))
        })
        val input = EditText(this).apply {
            hint = "Modi ji ne aaj phir kaha…"
            typeface = Ui.SANS; textSize = 16f
            setTextColor(Ui.INK); setHintTextColor(Ui.MUTED)
            minLines = 3; gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = Ui.rounded(Ui.SOFT, 20, this@MainActivity)
            val p = Ui.dp(this@MainActivity, 16); setPadding(p, p, p, p)
        }
        c1.addView(input)
        val out = Ui.vertical(this)
        c1.addView(out)
        c1.addView(Ui.horizontal(this).apply {
            gravity = Gravity.END
            setPadding(0, Ui.dp(context, 12), 0, 0)
            addView(Ui.cta(context, "Make pair", iconRes = null) {
                val plain = input.text.toString()
                val matcher = NameStore.matcher(this@MainActivity)
                val swapped = matcher.replaceAll(plain, null, include = { _, _ -> true }, pick = NameStore::pick)
                out.removeAllViews()
                if (swapped == plain) {
                    out.addView(Ui.label(context, "No names from the list in this text.", 13.5f, Ui.MUTED).apply { setPadding(0, Ui.dp(context, 10), 0, 0) })
                    return@cta
                }
                out.addView(pairRow("A · plain", plain))
                out.addView(pairRow("B · Meta-proof", swapped))
                Ui.enter(out, 0)
            })
        })
        content.addView(c1)

        val c2 = Ui.card(this, 18)
        c2.addView(Ui.label(this, "2. Post both, wait 24 hours", 17f, Ui.INK, Ui.W.SEMI))
        listOf(
            "Same account, same type of post (both text, or both the same image).",
            "Post them a few hours apart, at similar times of day.",
            "After 24 hours, open Insights and note “Accounts reached” for each.",
            "Do this at least 5 times. One pair proves nothing.",
        ).forEachIndexed { i, s -> c2.addView(step(i + 1, s, first = i == 0).apply { setPadding(0, Ui.dp(context, 10), 0, 0) }) }
        content.addView(c2)

        val c3 = Ui.card(this, 18)
        c3.addView(Ui.label(this, "3. Log the results", 17f, Ui.INK, Ui.W.SEMI))
        val plainIn = numberField("Reach of A (plain)")
        val swapIn = numberField("Reach of B (Meta-proof)")
        c3.addView(plainIn.apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = Ui.dp(context, 10) })
        c3.addView(swapIn)
        val (platRow, platPills) = Ui.chipRow(this)
        var platform = "Instagram"
        val pl = ArrayList<TextView>()
        for (p in listOf("Instagram", "Facebook", "Threads")) {
            val v = Ui.pill(this, p, p == platform) {
                platform = p; pl.forEach { Ui.stylePill(it, it.text == p) }
            }
            pl.add(v); platPills.addView(v)
        }
        c3.addView(platRow.apply { setPadding(0, Ui.dp(context, 10), 0, 0) })
        val summary = Ui.vertical(this)
        c3.addView(Ui.horizontal(this).apply {
            gravity = Gravity.END
            setPadding(0, Ui.dp(context, 14), 0, 0)
            addView(Ui.cta(context, "Save result", iconRes = null) {
                val a = plainIn.text.toString().toIntOrNull()
                val b = swapIn.text.toString().toIntOrNull()
                if (a == null || b == null || a <= 0) {
                    Toast.makeText(context, "Enter both reach numbers first", Toast.LENGTH_SHORT).show()
                    return@cta
                }
                val day = SimpleDateFormat("d MMM", Locale.ENGLISH).format(Date())
                Prefs.addReach(context, "$day|$platform|$a|$b")
                plainIn.setText(""); swapIn.setText("")
                fillSummary(summary)
            })
        })
        c3.addView(summary)
        fillSummary(summary)
        content.addView(c3)
    }

    private fun numberField(hintText: String) = EditText(this).apply {
        hint = hintText
        typeface = Ui.SANS; textSize = 16f
        setTextColor(Ui.INK); setHintTextColor(Ui.MUTED)
        inputType = InputType.TYPE_CLASS_NUMBER
        background = Ui.rounded(Ui.SOFT, 18, this@MainActivity)
        val p = Ui.dp(this@MainActivity, 14); setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(0, Ui.dp(context, 8), 0, 0) }
    }

    private fun pairRow(title: String, text: String) = Ui.vertical(this).apply {
        setPadding(0, Ui.dp(context, 12), 0, 0)
        addView(Ui.horizontal(context).apply {
            addView(Ui.label(context, title, 13f, Ui.MUTED, Ui.W.SEMI), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Ui.label(context, "Copy", 13.5f, Ui.META, Ui.W.SEMI).apply {
                setPadding(Ui.dp(context, 10), Ui.dp(context, 4), 0, Ui.dp(context, 4))
                setOnClickListener {
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(title, text))
                    Toast.makeText(context, "Copied $title", Toast.LENGTH_SHORT).show()
                }
            })
        })
        addView(Ui.label(context, text, 15f).apply {
            background = Ui.rounded(Ui.SOFT, 16, context)
            val p = Ui.dp(context, 12); setPadding(p, p, p, p)
            setTextIsSelectable(true)
        })
    }

    private fun fillSummary(box: LinearLayout) {
        box.removeAllViews()
        val rows = Prefs.reachLog(this).mapNotNull { line ->
            val p = line.split('|')
            if (p.size == 4) Triple(p[0] + " · " + p[1], p[2].toIntOrNull() ?: 0, p[3].toIntOrNull() ?: 0) else null
        }
        if (rows.isEmpty()) {
            box.addView(Ui.label(this, "No results yet.", 13.5f, Ui.MUTED).apply { setPadding(0, Ui.dp(context, 12), 0, 0) })
            return
        }
        val a = rows.sumOf { it.second }.toDouble()
        val b = rows.sumOf { it.third }.toDouble()
        val change = if (a > 0) ((b - a) / a * 100).toInt() else 0
        val verdict = when {
            rows.size < 5 -> "Too early to tell. Keep going until you have 5 pairs."
            change >= 15 -> "Meta-proof posts reached more people."
            change <= -15 -> "Meta-proof posts reached fewer people."
            else -> "No clear difference yet."
        }
        box.addView(Ui.label(this, "${if (change >= 0) "+" else ""}$change%", 40f, if (change >= 0) Ui.GOOD else Ui.INK, Ui.W.MED).apply {
            setPadding(0, Ui.dp(context, 16), 0, 0)
        })
        box.addView(Ui.label(this, "Reach of B compared with A, over ${rows.size} pair(s). $verdict", 13.5f, Ui.MUTED))
        for ((label, x, y) in rows.takeLast(6).reversed()) {
            box.addView(Ui.horizontal(this).apply {
                setPadding(0, Ui.dp(context, 8), 0, 0)
                addView(Ui.label(context, label, 14f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(Ui.label(context, "A $x  ·  B $y", 14f, Ui.INK, Ui.W.MED))
            })
        }
        box.addView(Ui.label(this, "Clear results", 13.5f, Ui.MUTED, Ui.W.MED).apply {
            setPadding(0, Ui.dp(context, 12), 0, 0)
            setOnClickListener { Prefs.clearReach(context); fillSummary(box) }
        })
    }

    // ---- Settings ----

    private fun settings() {
        content.addView(headline("*Settings*", 44f))
        content.addView(sub("Set up once, then forget about it."))
        content.addView(waysCard())
        content.addView(styleCard())

        val sw = Ui.card(this, 18)
        sw.addView(Ui.label(this, "Options", 17f, Ui.INK, Ui.W.SEMI))
        fun switch(label: String, on: Boolean, top: Int, set: (Boolean) -> Unit) = Switch(this).apply {
            text = label
            typeface = Ui.SANS_MEDIUM
            textSize = 15f
            setTextColor(Ui.INK)
            isChecked = on
            setOnCheckedChangeListener { _, v -> set(v) }
            setPadding(0, Ui.dp(context, top), 0, 0)
        }
        sw.addView(switch("Symbol letters, like K@mal P@rty", Prefs.symbols(this), 12) { Prefs.setSymbols(this, it) })
        sw.addView(Ui.label(this, "Swaps 2–3 letters of each code name for look-alikes: @ for a, 0 for o, 1 for i, 3 for e, $ for s.", 12.5f, Ui.MUTED).apply {
            setPadding(0, Ui.dp(context, 2), 0, 0)
        })
        sw.addView(switch("Soft sound when a name is found", Prefs.sound(this), 14) { Prefs.setSound(this, it) })
        if (hasFloating) {
            sw.addView(switch("Floating button only in Meta apps", Prefs.metaOnly(this), 14) { Prefs.setMetaOnly(this, it) })
        }
        content.addView(sw)

        content.addView(Ui.card(this, 18).apply {
            addView(cardHead(R.drawable.ic_shuffle, "Does it work?", "Compare the reach of plain and Meta-proof posts.").apply {
                addView(Ui.pill(context, "Open", false) { screen = Screen.TEST; render(animate = true) })
            })
        })
        content.addView(Ui.card(this, 18).apply {
            addView(cardHead(R.drawable.ic_sparkle, "Tutorial", "A quick tour of how it works.").apply {
                addView(Ui.pill(context, "Replay", false) {
                    startActivity(Intent(this@MainActivity, TutorialActivity::class.java))
                })
            })
        })
        content.addView(Ui.label(this, "Your privacy", 20f, Ui.INK, Ui.W.SEMI).apply {
            setPadding(Ui.dp(context, 4), Ui.dp(context, 18), 0, Ui.dp(context, 4))
        })
        val points = buildList {
            add("No internet" to "The app has no internet permission. It cannot send anything anywhere.")
            if (hasFloating) add("Only the box you type in" to "The floating button reads only the text box you are typing in, never password fields.")
            add("Nothing stored" to "Your text is not saved. Only your settings, stars and reach-test numbers stay on this phone.")
            if (hasFloating) add("Why Android warns you" to "Android warns about every app that uses accessibility. It is the only way one app can edit text in another.")
            add("Not 100% encrypted" to "Swapped names can still be understood by people and by AI. Use it to make posts less obvious, not invisible.")
        }
        for ((t, d) in points) {
            val card = Ui.card(this, 18)
            val row = Ui.horizontal(this).apply { gravity = Gravity.TOP }
            row.addView(Ui.iconCircle(this, R.drawable.ic_check, Ui.SOFT, Ui.INK, 36))
            row.addView(Ui.vertical(this).apply {
                setPadding(Ui.dp(context, 12), 0, 0, 0)
                addView(Ui.label(context, t, 16f, Ui.INK, Ui.W.SEMI))
                addView(Ui.label(context, d, 14f, Ui.MUTED).apply { setPadding(0, Ui.dp(context, 2), 0, 0) })
            })
            card.addView(row)
            content.addView(card)
        }
    }

    @Deprecated("Activity result API kept simple for minSdk 24")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        bench.onActivityResult(requestCode, resultCode, data)
    }
}
