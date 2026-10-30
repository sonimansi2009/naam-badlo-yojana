package org.naambadlo.keypad

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Home: big headline, three ways as pill tabs, a card per way, a floating bottom bar. */
class MainActivity : Activity() {

    private enum class Screen { HOME, NAMES, PRIVACY }
    private enum class Way { FLOATING, KEYBOARD, SELECT }

    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var statusPill: TextView
    private lateinit var navItems: Map<Screen, LinearLayout>
    private var screen = Screen.HOME
    private var way = Way.FLOATING

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        window.statusBarColor = Ui.BG_TOP
        window.navigationBarColor = Ui.BG
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val root = FrameLayout(this).apply { background = Ui.pageBackground() }

        val column = Ui.vertical(this).apply {
            setPadding(Ui.dp(context, 20), Ui.dp(context, 14), Ui.dp(context, 20), Ui.dp(context, 120))
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
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        bar.addView(Ui.grow(statusPill))
        bar.addView(Ui.iconCircle(this, R.drawable.ic_tune) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        updateStatus()
        return bar
    }

    private fun updateStatus() {
        if (!::statusPill.isInitialized) return
        val on = Prefs.floatingEnabled(this)
        statusPill.text = if (on) "●  Button is on" else "○  Button is off"
        statusPill.setTextColor(if (on) Ui.GOOD else Ui.MUTED)
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
            Screen.PRIVACY to (R.drawable.ic_shield to "Privacy"),
        )
        val made = HashMap<Screen, LinearLayout>()
        for ((s, pair) in items) {
            val item = Ui.horizontal(this).apply {
                gravity = Gravity.CENTER
                setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12))
                addView(Ui.icon(context, pair.first, Ui.INK, 22))
                addView(Ui.label(context, pair.second, 14f, Ui.INK, Ui.W.MED).apply {
                    setPadding(Ui.dp(context, 8), 0, 0, 0)
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
            item.background = if (active) Ui.rounded(Ui.SOFT, 26, this) else null
            item.getChildAt(1).visibility = if (active) View.VISIBLE else View.GONE
            (item.getChildAt(0) as? android.widget.ImageView)?.imageTintList =
                android.content.res.ColorStateList.valueOf(if (active) Ui.INK else Ui.MUTED)
        }
    }

    private fun render(animate: Boolean) {
        content.removeAllViews()
        paintNav()
        when (screen) {
            Screen.HOME -> home()
            Screen.NAMES -> names()
            Screen.PRIVACY -> privacy()
        }
        if (animate) {
            scroll.scrollTo(0, 0)
            for (i in 0 until content.childCount) Ui.enter(content.getChildAt(i), i * 60L)
        }
    }

    // ---- Screens ----

    private fun home() {
        val ents = NameStore.entities(this)
        val total = ents.sumOf { it.alternates.size }

        content.addView(Ui.headline(this, "Naam *Chupao*\nYojana", 48f).apply {
            setPadding(0, Ui.dp(context, 26), 0, Ui.dp(context, 6))
        })
        content.addView(Ui.label(this, "नाम छुपाओ योजना · code names for ministers and parties", 14f, Ui.MUTED).apply {
            setPadding(0, 0, 0, Ui.dp(context, 22))
        })

        // Pill tabs
        val (tabScroll, tabs) = Ui.chipRow(this)
        val labels = listOf(Way.FLOATING to "Floating button", Way.KEYBOARD to "Keyboard", Way.SELECT to "Select text")
        for ((w, text) in labels) {
            tabs.addView(Ui.pill(this, text, w == way) {
                if (way != w) { way = w; render(animate = false); content.getChildAt(3)?.let { Ui.enter(it, 0, 8) } }
            })
        }
        content.addView(tabScroll.apply { setPadding(0, 0, 0, Ui.dp(context, 14)) })

        // Main card
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
            Way.FLOATING -> listOf("${ents.size} names", "$total code names", "One tap", "Hold to undo", "Offline")
            Way.KEYBOARD -> listOf("Hindi + English", "50 per name", "Auto return", "Offline")
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
                "Hold it to undo. Drag it anywhere.",
            )
            Way.KEYBOARD -> listOf(
                "Turn on Naam Chupao keyboard.",
                "After typing a name, switch keyboards.",
                "Tap “Make it Meta friendly” or pick a code name.",
                "It returns to your keyboard by itself.",
            )
            Way.SELECT -> listOf(
                "Select your text in any app.",
                "Tap ⋮ in the pop-up menu, then Naam Chupao.",
                "Pick code names or shuffle, then confirm.",
            )
        }
        steps.forEachIndexed { i, s ->
            panel.addView(Ui.horizontal(this).apply {
                gravity = Gravity.TOP
                setPadding(0, if (i == 0) 0 else Ui.dp(context, 10), 0, 0)
                addView(Ui.label(context, "${i + 1}", 12f, Ui.INK, Ui.W.SEMI).apply {
                    gravity = Gravity.CENTER
                    background = Ui.oval(Ui.SURFACE)
                    layoutParams = LinearLayout.LayoutParams(Ui.dp(context, 24), Ui.dp(context, 24))
                })
                addView(Ui.label(context, s, 15f).apply { setPadding(Ui.dp(context, 12), Ui.dp(context, 1), 0, 0) })
            })
        }
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
                Way.SELECT -> scroll.smoothScrollTo(0, content.getChildAt(4)?.top ?: 0)
            }
        })
        panel.addView(ctaRow)
        card.addView(panel)
        content.addView(card)

        // Try it
        val tryCard = Ui.card(this, 18)
        tryCard.addView(Ui.label(this, "Try it here", 17f, Ui.INK, Ui.W.SEMI))
        tryCard.addView(Ui.label(this, "Type a minister's name. The blue button shows up if it is on.", 13f, Ui.MUTED).apply {
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
        content.addView(tryCard)
    }

    private fun names() {
        val ents = NameStore.entities(this)
        content.addView(Ui.headline(this, "${ents.size} *names,*\n${ents.sumOf { it.alternates.size }} code names", 40f).apply {
            setPadding(0, Ui.dp(context, 26), 0, Ui.dp(context, 20))
        })
        for (e in ents) {
            val card = Ui.card(this, 18)
            card.addView(Ui.label(this, e.label, 17f, Ui.INK, Ui.W.SEMI))
            card.addView(Ui.label(this, "Watches for: " + e.triggers.take(4).joinToString(", "), 12.5f, Ui.MUTED).apply {
                setPadding(0, Ui.dp(context, 2), 0, Ui.dp(context, 10))
            })
            val flow = FlowLayout(this)
            e.alternates.take(10).forEach { flow.addView(Ui.tag(this, it)) }
            flow.addView(Ui.tag(this, "+${e.alternates.size - 10} more").apply { setTextColor(Ui.MUTED) })
            card.addView(flow)
            content.addView(card)
        }
    }

    private fun privacy() {
        content.addView(Ui.headline(this, "Your *privacy,*\nkept simple", 42f).apply {
            setPadding(0, Ui.dp(context, 26), 0, Ui.dp(context, 20))
        })
        val points = listOf(
            "No internet" to "The app has no internet permission. It cannot send anything anywhere.",
            "Only the box you type in" to "The floating button reads only the text box you are typing in, never password fields.",
            "Nothing stored" to "Your text is not saved. The list of names lives inside the app.",
            "Why Android warns you" to "Android shows a warning for every app that uses accessibility. That is the only way one app can edit text in another.",
        )
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
