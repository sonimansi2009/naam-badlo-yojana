package org.naambadlo.keypad

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView

/** Setup screen: floating button first, then the other two ways, then a test box. */
class MainActivity : Activity() {

    private lateinit var floatStatus: TextView
    private lateinit var floatButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ents = NameStore.entities(this)
        val total = ents.sumOf { it.alternates.size }

        val root = Ui.vertical(this, 20).apply { setBackgroundColor(Ui.BG) }
        root.addView(Ui.label(this, "Naam Badlo", 26f, Ui.TEXT, bold = true))
        root.addView(Ui.label(this, "${ents.size} names · $total alternates · works offline", 13f, Ui.MUTED))
        root.addView(Ui.spacer(this, 18))

        // 1. Floating button (recommended)
        root.addView(Ui.label(this, "Floating button  ·  easiest", 16f, Ui.ACCENT, bold = true))
        root.addView(Ui.label(this,
            "Keep your normal keyboard. When you type a name in Instagram, WhatsApp or any app, " +
            "a “Make it Meta friendly” button appears. Tap it to swap every name. " +
            "Hold it to undo. Drag it anywhere.", 14f))
        root.addView(Ui.spacer(this, 8))
        floatStatus = Ui.label(this, "", 13f, Ui.MUTED)
        root.addView(floatStatus)
        floatButton = Ui.chip(this, "Turn on floating button", accent = true) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        root.addView(floatButton)
        root.addView(Ui.label(this,
            "In the next screen: Installed apps (or Downloaded apps) → Naam Badlo floating button → On. " +
            "Android calls this an accessibility service. It only reads the text box you are typing in, " +
            "never passwords, and the app has no internet permission.", 12f, Ui.MUTED))
        root.addView(CheckBox(this).apply {
            text = "Show only in Meta apps (Instagram, Facebook, WhatsApp, Threads, Messenger)"
            setTextColor(Ui.TEXT)
            isChecked = Prefs.metaOnly(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setMetaOnly(this@MainActivity, v) }
        })
        root.addView(Ui.spacer(this, 18))

        // 2. Select text
        root.addView(Ui.label(this, "Select text", 16f, Ui.ACCENT, bold = true))
        root.addView(Ui.label(this,
            "Select your text, tap ⋮ in the pop-up menu, then “Naam Badlo”. " +
            "Pick each swap yourself and tap Done.", 14f))
        root.addView(Ui.spacer(this, 18))

        // 3. Add-on keyboard
        root.addView(Ui.label(this, "Add-on keyboard", 16f, Ui.ACCENT, bold = true))
        root.addView(Ui.label(this,
            "For choosing from all 50 swaps while typing. Switch to it with the keyboard icon, " +
            "tap a swap, and it jumps back to your normal keyboard.", 14f))
        root.addView(Ui.chip(this, "Turn on add-on keyboard") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        root.addView(Ui.chip(this, "Switch keyboard") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        root.addView(Ui.spacer(this, 18))

        root.addView(Ui.label(this, "Try it here", 16f, Ui.ACCENT, bold = true))
        root.addView(EditText(this).apply {
            hint = "Type: Modi aur Amit Shah ne kaha…"
            setHintTextColor(Ui.MUTED)
            setTextColor(Ui.TEXT)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            background = Ui.rounded(Ui.PANEL, 10, this@MainActivity)
            val p = Ui.dp(this@MainActivity, 12)
            setPadding(p, p, p, p)
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        val on = Prefs.floatingEnabled(this)
        floatStatus.text = if (on) "● On. Type a name in the box below to see it." else "○ Off"
        floatStatus.setTextColor(if (on) 0xFF6BCB77.toInt() else Ui.MUTED)
        floatButton.text = if (on) "Floating button settings" else "Turn on floating button"
    }
}
