package org.naambadlo.keypad

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ScrollView

/** Setup screen: enable the add-on, then try it in the test box. */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ents = NameStore.entities(this)
        val total = ents.sumOf { it.alternates.size }

        val root = Ui.vertical(this, 20).apply { setBackgroundColor(Ui.BG) }
        root.addView(Ui.label(this, "Naam Badlo", 26f, Ui.TEXT, bold = true))
        root.addView(Ui.label(this, "${ents.size} names · $total alternates · works offline", 13f, Ui.MUTED))
        root.addView(Ui.spacer(this, 18))

        root.addView(Ui.label(this, "Way 1 — Select text (works with any keyboard)", 16f, Ui.ACCENT, bold = true))
        root.addView(Ui.label(this,
            "Type normally. Long-press to select your text, tap ⋮ or the arrow in the pop-up menu, " +
            "then tap “Naam Badlo”. Pick a swap for each name and tap Done.", 14f))
        root.addView(Ui.spacer(this, 16))

        root.addView(Ui.label(this, "Way 2 — Add-on keyboard", 16f, Ui.ACCENT, bold = true))
        root.addView(Ui.label(this,
            "1. Turn on “Naam Badlo add-on” below.\n" +
            "2. Keep typing on your usual keyboard.\n" +
            "3. After a name, switch keyboards (globe or keyboard icon). Pick a swap.\n" +
            "4. It jumps back to your usual keyboard by itself.", 14f))
        root.addView(Ui.spacer(this, 10))
        root.addView(Ui.chip(this, "1. Turn on the add-on", accent = true) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        root.addView(Ui.chip(this, "2. Switch keyboard") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        root.addView(Ui.spacer(this, 16))

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
        root.addView(Ui.spacer(this, 18))
        root.addView(Ui.label(this,
            "This app has no internet permission. It cannot see, store or send what you type " +
            "anywhere. The name list is in the app itself.", 12f, Ui.MUTED))

        setContentView(ScrollView(this).apply { addView(root) })
    }
}
