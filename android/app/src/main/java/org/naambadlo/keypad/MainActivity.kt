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

/** Home, Names, Reach test and Privacy, behind a floating bottom bar. */
class MainActivity : Activity() {

    private enum class Screen { HOME, NAMES, TEST, PRIVACY }
    private enum class Way { FLOATING, KEYBOARD, SELECT }

    /** The Lite build has no accessibility service, so no floating button. */
    private val hasFloating = BuildConfig.HAS_FLOATING

    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var statusPill: TextView
    private lateinit var navItems: Map<Screen, LinearLayout>
    private var screen = Screen.HOME
    private var way = if (BuildConfig.HAS_FLOATING) Way.FLOATING else Way.KEYBOARD

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
        column.addView(content)
        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(column) }
        root.addView(scroll)

        root.addView(bottomBar(), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ).apply { setMargins(Ui.dp(this@MainActivity, 18), 0, Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 18)) })

        setContentView(root)
        render(animate = true)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        if (screen == Screen.HOME) render(animate = false)
    }

    // ---- Chrome ----

    private fun topBar(): View {
        val bar = Ui.horizontal(this)
        bar.addView(Ui.iconCircle(this, R.drawable.ic_sparkle))
        statusPill = Ui.label(this, "", 14f, Ui.INK, Ui.W.MED).apply {
            gravity = Gravity.CENTER
            setPadding(Ui.dp(context, 14), Ui.dp(context, 9), Ui.dp(context, 14), Ui.dp(context, 9))
            background = Ui.rounded(Ui.SURFACE, 20, context)
            setOnClickListener { openSetup() }
            Ui.pressable(this)
        }
        bar.addView(FrameLayout(this).apply {
            addView(statusPill, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(Ui.iconCircle(this, R.drawable.ic_tune) { openSetup() })
        updateStatus()
        return bar
    }

    private fun openSetup() {
        startActivity(Intent(if (hasFloating) Settings.ACTION_ACCESSIBILITY_SETTINGS else Settings.ACTION_INPUT_METHOD_SETTINGS))
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
            Screen.NAMES to (R.drawable.ic_list to "Names"),
            Screen.TEST to (R.drawable.ic_shuffle to "Test"),
            Screen.PRIVACY to (R.drawable.ic_shield to "Privacy"),
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
            val active = s == screen
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
            Screen.NAMES -> names()
            Screen.TEST -> reachTest()
            Screen.PRIVACY -> privacy()
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

    // ---- Home ----

    private fun home() {
        content.addView(SheenImageView(this, R.drawable.seal_white).apply {
            contentDescription = "Shadowban Suraksha Yojana seal: a chameleon on a lotus"
            layoutParams = LinearLayout.LayoutParams(Ui.dp(context, 210), Ui.dp(context, 210)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, Ui.dp(context, 18), 0, Ui.dp(context, 8))
            }
        })
        content.addView(Ui.label(this, "A government-approved solution\nto government disapproval.", 19f, Ui.INK).apply {
            typeface = Ui.SERIF_ITALIC
            gravity = Gravity.CENTER
            alpha = 0.88f
            setLineSpacing(0f, 1.1f)
            setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 22))
        })

        val (tabScroll, tabs) = Ui.chipRow(this)
        val labels = buildList {
            if (hasFloating) add(Way.FLOATING to "Floating button")
            add(Way.KEYBOARD to "Keyboard")
            add(Way.SELECT to "Select text")
        }
        for ((w, text) in labels) {
            tabs.addView(Ui.pill(this, text, w == way) {
                if (way != w) { way = w; render(animate = false); content.getChildAt(3)?.let { Ui.enter(it, 0, 8) } }
            })
        }
        content.addView(tabScroll.apply { setPadding(0, 0, 0, Ui.dp(context, 14)) })
        content.addView(wayCard())
        content.addView(longTextCard())
        content.addView(styleCard())
        if (!hasFloating) content.addView(liteNote())
        content.addView(tryCard())
    }

    private fun wayCard(): View {
        val card = Ui.card(this, 14)
        val head = Ui.horizontal(this).apply { setPadding(Ui.dp(context, 4), Ui.dp(context, 4), 0, Ui.dp(context, 12)) }
        val (iconRes, title, sub) = when (way) {
            Way.FLOATING -> Triple(R.drawable.ic_sparkle, "Floating button", "Works over Instagram, WhatsApp, any app")
            Way.KEYBOARD -> Triple(R.drawable.ic_keyboard, "Add-on keyboard", "Pick from 50 code names per name")
            Way.SELECT -> Triple(R.drawable.ic_text, "Select text", "Choose each code name yourself")
        }
        head.addView(Ui.iconCircle(this, iconRes, Ui.SOFT, Ui.INK, 44))
        head.addView(Ui.vertical(this).apply {
            setPadding(Ui.dp(context, 12), 0, 0, 0)
            addView(Ui.label(context, title, 17f, Ui.INK, Ui.W.SEMI))
            addView(Ui.label(context, sub, 13f, Ui.MUTED))
        })
        card.addView(head)

        val tags = FlowLayout(this).apply { setPadding(Ui.dp(context, 2), 0, 0, Ui.dp(context, 12)) }
        val tagTexts = when (way) {
            Way.FLOATING -> listOf("One tap", "Undo", "Offline")
            Way.KEYBOARD -> listOf("Hindi + English", "Star favourites", "Auto return", "Offline")
            Way.SELECT -> listOf("Any keyboard", "Shuffle anytime", "Offline")
        }
        tagTexts.forEach { tags.addView(Ui.tag(this, it)) }
        card.addView(tags)

        val panel = Ui.panel(this)
        val steps = when (way) {
            Way.FLOATING -> listOf(
                "Keep your normal keyboard.",
                "Type a name. A blue button appears.",
                "Tap it to swap. Tap again for fresh names.",
                "Changed your mind? Tap Undo right after.",
            )
            Way.KEYBOARD -> listOf(
                "Turn on the Shadowban Suraksha keyboard.",
                "After typing a name, switch keyboards.",
                "Tap “Make it Meta friendly” or pick a code name.",
                "Hold a code name to star it as a favourite.",
            )
            Way.SELECT -> listOf(
                "Select your text in any app.",
                "Tap ⋮ in the pop-up menu, then Shadowban Suraksha.",
                "Pick code names or shuffle, then confirm.",
            )
        }
        steps.forEachIndexed { i, s -> panel.addView(step(i + 1, s, first = i == 0)) }
        if (way == Way.FLOATING) {
            panel.addView(Switch(this).apply {
                text = "Only in Meta apps"
                typeface = Ui.SANS_MEDIUM
                textSize = 15f
                setTextColor(Ui.INK)
                isChecked = Prefs.metaOnly(this@MainActivity)
                setOnCheckedChangeListener { _, v -> Prefs.setMetaOnly(this@MainActivity, v) }
                setPadding(0, Ui.dp(context, 14), 0, 0)
            })
            panel.addView(Switch(this).apply {
                text = "Soft sound when a name is found"
                typeface = Ui.SANS_MEDIUM
                textSize = 15f
                setTextColor(Ui.INK)
                isChecked = Prefs.sound(this@MainActivity)
                setOnCheckedChangeListener { _, v -> Prefs.setSound(this@MainActivity, v) }
                setPadding(0, Ui.dp(context, 10), 0, 0)
            })
            if (!Prefs.floatingEnabled(this)) {
                panel.addView(Ui.label(this,
                    "If Android says “Restricted setting”: open App info → ⋮ → Allow restricted settings, then try again.",
                    12.5f, Ui.MUTED).apply { setPadding(0, Ui.dp(context, 12), 0, 0) })
            }
        }
        val ctaText = when (way) {
            Way.FLOATING -> if (Prefs.floatingEnabled(this)) "Button settings" else "Turn on"
            Way.KEYBOARD -> "Turn on"
            Way.SELECT -> "Try it below"
        }
        val ctaRow = Ui.horizontal(this).apply {
            gravity = Gravity.END
            setPadding(0, Ui.dp(context, 18), 0, 0)
        }
        if (way == Way.KEYBOARD) {
            ctaRow.addView(Ui.lightButton(this, "Switch") {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(0, 0, Ui.dp(context, 8), 0) }
            })
        }
        ctaRow.addView(Ui.cta(this, ctaText) {
            when (way) {
                Way.FLOATING -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Way.KEYBOARD -> startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                Way.SELECT -> scroll.smoothScrollTo(0, scroll.getChildAt(0).height)
            }
        })
        panel.addView(ctaRow)
        card.addView(panel)
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

    /** Speeches, scripts, notes and documents. */
    private fun longTextCard(): View {
        val card = Ui.card(this, 14)
        val head = Ui.horizontal(this).apply { setPadding(Ui.dp(context, 4), Ui.dp(context, 4), 0, Ui.dp(context, 12)) }
        head.addView(Ui.iconCircle(this, R.drawable.ic_text, Ui.SOFT, Ui.INK, 44))
        head.addView(Ui.vertical(this).apply {
            setPadding(Ui.dp(context, 12), 0, 0, 0)
            addView(Ui.label(context, "Long text", 17f, Ui.INK, Ui.W.SEMI))
            addView(Ui.label(context, "Scripts, speeches, notes, documents", 13f, Ui.MUTED))
        })
        card.addView(head)
        val tags = FlowLayout(this).apply { setPadding(Ui.dp(context, 2), 0, 0, Ui.dp(context, 12)) }
        listOf("Upload .txt, .md, .docx", "Paste", "Share from any app").forEach { tags.addView(Ui.tag(this, it)) }
        card.addView(tags)
        card.addView(Ui.horizontal(this).apply {
            gravity = Gravity.END
            setPadding(0, 0, Ui.dp(context, 4), Ui.dp(context, 4))
            addView(Ui.cta(context, "Open long text") {
                startActivity(Intent(this@MainActivity, DocumentActivity::class.java))
            })
        })
        return card
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
            Prefs.Tier.STEALTH -> "Jumbles like Nodi, Sh4h, B•J•P. Harder for filters, harder for readers too."
        }
        val made = ArrayList<TextView>()
        for ((t, label) in opts) {
            val p = Ui.pill(this, label, Prefs.tier(this) == t) {
                Prefs.setTier(this, t)
                made.forEachIndexed { i, v -> Ui.stylePill(v, opts[i].first == t) }
                desc.text = describe(t)
            }
            made.add(p); pills.addView(p)
        }
        desc.text = describe(Prefs.tier(this))
        card.addView(row)
        card.addView(Ui.label(this, "Starred favourites are always used first. Star them in the Names tab or by holding a code name.", 12.5f, Ui.MUTED).apply {
            setPadding(0, Ui.dp(context, 12), 0, 0)
        })
        return card
    }

    private fun liteNote(): View {
        val card = Ui.card(this, 18)
        card.addView(Ui.label(this, "Want the floating button?", 16f, Ui.INK, Ui.W.SEMI))
        card.addView(Ui.label(this,
            "This Lite version installs without warnings. The floating button needs the full version, " +
            "which Android only allows after you pause Play Protect once during install.", 13.5f, Ui.MUTED).apply {
            setPadding(0, Ui.dp(context, 4), 0, 0)
        })
        return card
    }

    private fun tryCard(): View {
        val tryCard = Ui.card(this, 18)
        tryCard.addView(Ui.label(this, "Try it here", 17f, Ui.INK, Ui.W.SEMI))
        tryCard.addView(Ui.label(this,
            if (hasFloating) "Type a minister's name. The blue button shows up if it is on."
            else "Type a minister's name, then switch to the Shadowban Suraksha keyboard.", 13f, Ui.MUTED).apply {
            setPadding(0, Ui.dp(context, 2), 0, Ui.dp(context, 12))
        })
        tryCard.addView(EditText(this).apply {
            hint = "Modi aur Amit Shah ne kaha…"
            typeface = Ui.SANS
            textSize = 16f
            setHintTextColor(Ui.MUTED)
            setTextColor(Ui.INK)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            background = Ui.rounded(Ui.SOFT, 20, this@MainActivity)
            val p = Ui.dp(this@MainActivity, 16)
            setPadding(p, p, p, p)
        })
        return tryCard
    }

    // ---- Names ----

    private fun names() {
        val ents = NameStore.entities(this)
        content.addView(headline("Names *&*\ncode names", 40f))
        content.addView(sub("Tap a code name to star it. Starred ones are used first."))
        for (e in ents) {
            val card = Ui.card(this, 18)
            card.addView(Ui.label(this, e.label, 17f, Ui.INK, Ui.W.SEMI))
            card.addView(Ui.label(this, "Watches for: " + e.triggers.take(4).joinToString(", "), 12.5f, Ui.MUTED).apply {
                setPadding(0, Ui.dp(context, 2), 0, Ui.dp(context, 10))
            })
            val favs = Prefs.favourites(this, e.id)
            val flow = FlowLayout(this)
            val shown = (favs.filter { it in e.alternates } + e.recognisable).distinct().take(14)
            for (alt in shown) {
                val chip = Ui.chip(this, if (alt in favs) "★ $alt" else alt, selected = alt in favs) {}
                chip.setOnClickListener {
                    val added = Prefs.toggleFavourite(this, e.id, alt)
                    chip.text = if (added) "★ $alt" else alt
                    Ui.styleChip(chip, added)
                    chip.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                flow.addView(chip)
            }
            card.addView(flow)
            content.addView(card)
        }
    }

    // ---- Reach test ----

    private fun reachTest() {
        content.addView(headline("Does it *work?*", 44f))
        content.addView(sub("Post the same thing twice, once plain and once Meta friendly, and compare reach."))

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
            addView(Ui.cta(context, "Make pair") {
                val plain = input.text.toString()
                val matcher = NameStore.matcher(this@MainActivity)
                val swapped = matcher.replaceAll(plain, null, include = { _, _ -> true }, pick = NameStore::pick)
                out.removeAllViews()
                if (swapped == plain) {
                    out.addView(Ui.label(context, "No names from the list in this text.", 13.5f, Ui.MUTED).apply { setPadding(0, Ui.dp(context, 10), 0, 0) })
                    return@cta
                }
                out.addView(pairRow("A · plain", plain))
                out.addView(pairRow("B · Meta friendly", swapped))
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
        val swapIn = numberField("Reach of B (Meta friendly)")
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
            addView(Ui.cta(context, "Save result") {
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
            change >= 15 -> "Meta friendly posts reached more people."
            change <= -15 -> "Meta friendly posts reached fewer people."
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

    // ---- Privacy ----

    private fun privacy() {
        content.addView(headline("Your *privacy,*\nkept simple", 42f))
        content.addView(sub("What the app can and cannot see."))
        val points = buildList {
            add("No internet" to "The app has no internet permission. It cannot send anything anywhere.")
            if (hasFloating) add("Only the box you type in" to "The floating button reads only the text box you are typing in, never password fields.")
            add("Nothing stored" to "Your text is not saved. Only your settings, stars and reach-test numbers stay on this phone.")
            if (hasFloating) add("Why Android warns you" to "Android warns about every app that uses accessibility. It is the only way one app can edit text in another.")
            add("Code names are not encryption" to "Swapped names can still be understood by people and by AI. Use it to make posts less obvious, not invisible.")
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
}
