package org.naambadlo.keypad

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Home screen: one clear setup path, a test box, and the other two ways. */
class MainActivity : Activity() {

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var floatButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BLUE_DEEP
        val ents = NameStore.entities(this)
        val total = ents.sumOf { it.alternates.size }

        val page = Ui.vertical(this).apply { setBackgroundColor(Ui.PAGE) }

        // Header band
        val header = Ui.vertical(this).apply {
            setBackgroundColor(Ui.BLUE_DEEP)
            setPadding(Ui.dp(context, 22), Ui.dp(context, 30), Ui.dp(context, 22), Ui.dp(context, 28))
        }
        header.addView(Ui.label(this, NameStore.APP_NAME, 29f, Ui.ON_BLUE, bold = true))
        header.addView(Ui.label(this, "नाम छुपाओ योजना", 17f, Ui.ON_BLUE_MUTED))
        header.addView(Ui.spacer(this, 10))
        header.addView(Ui.label(this,
            "Swap ministers' and party names with code names before you post. " +
            "$total code names for ${ents.size} names. Works offline.", 14f, Ui.ON_BLUE_MUTED))
        page.addView(header)

        val body = Ui.vertical(this, 16)
        page.addView(body)

        // Card 1: floating button
        val c1 = Ui.card(this)
        c1.addView(row(
            Ui.label(this, "Floating button", 19f, Ui.INK, bold = true),
            Ui.label(this, "RECOMMENDED", 11f, Ui.BLUE, bold = true).apply {
                letterSpacing = 0.08f
                background = Ui.rounded(Ui.BLUE_TINT, 8, context)
                setPadding(Ui.dp(context, 8), Ui.dp(context, 3), Ui.dp(context, 8), Ui.dp(context, 3))
            }
        ))
        c1.addView(Ui.spacer(this, 6))
        c1.addView(Ui.label(this,
            "Keep your normal keyboard. When you type a name in Instagram, WhatsApp or any app, " +
            "a “Make it Meta friendly” button appears. Tap it to swap the names. " +
            "Tap again for fresh names. Hold to undo. Drag to move.", 14.5f, Ui.MUTED))
        c1.addView(Ui.spacer(this, 14))

        statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(Ui.dp(context, 9), Ui.dp(context, 9)).apply {
                setMargins(0, 0, Ui.dp(context, 8), 0)
            }
        }
        statusText = Ui.label(this, "", 14f, Ui.MUTED, bold = true)
        c1.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(statusDot); addView(statusText)
        })
        floatButton = Ui.button(this, "Turn on floating button") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        c1.addView(Ui.fullWidth(floatButton, this, topDp = 12))
        c1.addView(Ui.spacer(this, 10))
        c1.addView(Ui.label(this,
            "On the next screen: Installed apps → Naam Chupao button → On. Android shows a warning " +
            "for every accessibility app. This one reads only the box you are typing in, never " +
            "passwords, and has no internet permission.", 12.5f, Ui.MUTED))
        c1.addView(Ui.spacer(this, 8))
        c1.addView(Switch(this).apply {
            text = "Show only in Meta apps"
            typeface = Ui.SERIF
            setTextColor(Ui.INK)
            textSize = 14.5f
            isChecked = Prefs.metaOnly(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setMetaOnly(this@MainActivity, v) }
        })
        body.addView(c1)

        // Card 2: try it
        val c2 = Ui.card(this)
        c2.addView(Ui.label(this, "Try it here", 19f, Ui.INK, bold = true))
        c2.addView(Ui.spacer(this, 8))
        c2.addView(EditText(this).apply {
            hint = "Type: Modi aur Amit Shah ne kaha…"
            typeface = Ui.SERIF
            textSize = 16f
            setHintTextColor(Ui.MUTED)
            setTextColor(Ui.INK)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            background = Ui.rounded(Ui.BLUE_TINT, 12, this@MainActivity)
            val p = Ui.dp(this@MainActivity, 14)
            setPadding(p, p, p, p)
        })
        body.addView(c2)

        // Card 3: other ways
        val c3 = Ui.card(this)
        c3.addView(Ui.label(this, "Other ways", 19f, Ui.INK, bold = true))
        c3.addView(Ui.spacer(this, 10))
        c3.addView(Ui.label(this, "Select text", 15.5f, Ui.BLUE, bold = true))
        c3.addView(Ui.label(this,
            "Select your text, tap ⋮ in the pop-up menu, then “Naam Chupao”. Pick each code name yourself.",
            14f, Ui.MUTED))
        c3.addView(Ui.spacer(this, 14))
        c3.addView(Ui.label(this, "Add-on keyboard", 15.5f, Ui.BLUE, bold = true))
        c3.addView(Ui.label(this,
            "Switch to it after typing a name to choose from 50 code names. It returns to your keyboard by itself.",
            14f, Ui.MUTED))
        val kbRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        kbRow.addView(Ui.button(this, "Turn on", Ui.Style.OUTLINE) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, Ui.dp(context, 10), Ui.dp(context, 6), 0) } })
        kbRow.addView(Ui.button(this, "Switch keyboard", Ui.Style.OUTLINE) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(Ui.dp(context, 6), Ui.dp(context, 10), 0, 0) } })
        c3.addView(kbRow)
        body.addView(c3)

        body.addView(Ui.label(this,
            "No internet permission. Nothing you type is stored or sent anywhere.", 12.5f, Ui.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 24))
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.PAGE)
            addView(page)
        })

        Ui.enter(header, 0, rise = 0)
        Ui.enter(c1, 80); Ui.enter(c2, 160); Ui.enter(c3, 240)
    }

    private fun row(left: View, right: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(right)
    }

    override fun onResume() {
        super.onResume()
        val on = Prefs.floatingEnabled(this)
        statusDot.background = Ui.rounded(if (on) Ui.GOOD else Ui.LINE, 5, this)
        statusText.text = if (on) "On. Type a name in the box below." else "Off"
        statusText.setTextColor(if (on) Ui.GOOD else Ui.MUTED)
        floatButton.text = if (on) "Floating button settings" else "Turn on floating button"
    }
}
