package org.naambadlo.keypad

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * First-run tour (5 short pages), and a "What's new" page after an update.
 * Swipe or tap Next. Skip any time. Can be replayed from Settings.
 */
class TutorialActivity : Activity() {

    companion object {
        const val EXTRA_UPDATE = "update"

        /** Shows the tour on first install, or What's new after an update. */
        fun maybeShow(a: Activity) {
            val seen = Prefs.tutorialSeen(a)
            if (seen >= BuildConfig.VERSION_CODE) return
            a.startActivity(Intent(a, TutorialActivity::class.java).putExtra(EXTRA_UPDATE, seen > 0))
        }

        /** What changed in this version, in plain words. */
        val WHATS_NEW = listOf(
            "The button is now “Make it Meta-proof”.",
            "Symbol letters: 2–3 letters of each code name become look-alikes, like K@mal P@rty. Turn it off in Settings.",
            "First names like Narendra or Nirmala are spotted too (the app asks first).",
            "Real names are never shown in full on screen: N*re*d*a M*di.",
            "Decode reads symbol letters and jumbles like “Nodi” or “Marendra”.",
        )
    }

    private data class Page(val build: (LinearLayout) -> Unit)

    private lateinit var stage: FrameLayout
    private lateinit var dots: LinearLayout
    private lateinit var next: LinearLayout
    private lateinit var back: TextView
    private lateinit var skip: TextView
    private var pages: List<Page> = emptyList()
    private var index = 0
    private var whatsNewMode = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        window.statusBarColor = Ui.BG_TOP
        window.navigationBarColor = Ui.BG
        val isUpdate = intent.getBooleanExtra(EXTRA_UPDATE, false)
        whatsNewMode = isUpdate
        pages = if (isUpdate) listOf(Page(::whatsNew)) else tour()

        val root = Ui.vertical(this).apply {
            background = Ui.pageBackground()
            setPadding(Ui.dp(context, 24), Ui.dp(context, 14), Ui.dp(context, 24), Ui.dp(context, 24))
        }
        root.addView(Ui.horizontal(this).apply {
            gravity = Gravity.END
            skip = Ui.label(context, "Skip", 15f, Ui.MUTED, Ui.W.MED).apply {
                setPadding(Ui.dp(context, 14), Ui.dp(context, 10), Ui.dp(context, 4), Ui.dp(context, 10))
                setOnClickListener { done() }
                visibility = if (pages.size > 1) View.VISIBLE else View.INVISIBLE
            }
            addView(skip)
        })
        stage = FrameLayout(this)
        root.addView(stage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        dots = Ui.horizontal(this).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, Ui.dp(context, 18)) }
        dots.visibility = if (pages.size > 1) View.VISIBLE else View.GONE
        root.addView(dots)
        val bar = Ui.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        back = Ui.label(this, "Back", 15f, Ui.MUTED, Ui.W.MED).apply {
            setPadding(Ui.dp(context, 6), Ui.dp(context, 12), Ui.dp(context, 16), Ui.dp(context, 12))
            setOnClickListener { if (whatsNewMode) done() else go(index - 1) }
        }
        bar.addView(back)
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        next = Ui.horizontal(this)
        bar.addView(next)
        root.addView(bar)
        setContentView(root)

        // Swipe left / right between pages
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dx = e2.x - (e1?.x ?: e2.x)
                if (abs(dx) < Ui.dp(this@TutorialActivity, 60) || abs(vx) < abs(vy)) return false
                go(if (dx < 0) index + 1 else index - 1); return true
            }
        })
        stage.setOnTouchListener { _, ev -> gd.onTouchEvent(ev) }
        go(0, animate = false)
        Feedback.welcome(this)      // "S S Y" in Morse code
    }

    private fun startTour() {
        whatsNewMode = false
        pages = tour(); index = 0
        dots.visibility = View.VISIBLE; skip.visibility = View.VISIBLE
        go(0)
    }

    private fun go(i: Int, animate: Boolean = true) {
        if (i < 0) return
        if (i >= pages.size) { done(); return }
        val forward = i >= index
        index = i
        handler.removeCallbacksAndMessages(null)
        val page = Ui.vertical(this).apply { gravity = Gravity.CENTER_VERTICAL }
        pages[i].build(page)
        stage.removeAllViews()
        stage.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (animate) {
            page.alpha = 0f; page.translationX = Ui.dp(this, if (forward) 40 else -40).toFloat()
            page.animate().alpha(1f).translationX(0f).setDuration(260).start()
        }
        // dots
        dots.removeAllViews()
        for (k in pages.indices) dots.addView(View(this).apply {
            background = Ui.rounded(if (k == i) Ui.INK else Ui.LINE, 4, context)
            layoutParams = LinearLayout.LayoutParams(Ui.dp(context, if (k == i) 22 else 8), Ui.dp(context, 8))
                .apply { setMargins(Ui.dp(context, 3), 0, Ui.dp(context, 3), 0) }
        })
        next.removeAllViews()
        if (whatsNewMode) {
            // What's new: the tour is the big button, Start is a quiet text link
            back.text = "Start"; back.setTextColor(Ui.INK); back.visibility = View.VISIBLE
            next.addView(Ui.cta(this, "Take the tour", iconRes = null) { startTour() })
            return
        }
        back.text = "Back"; back.setTextColor(Ui.MUTED)
        back.visibility = if (i > 0) View.VISIBLE else View.INVISIBLE
        val last = i == pages.size - 1
        next.addView(Ui.cta(this, if (last) "Start" else "Next", iconRes = null) { go(index + 1) })
    }

    private fun done() {
        Prefs.setTutorialSeen(this, BuildConfig.VERSION_CODE)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    @Deprecated("Back closes the tour and marks it seen")
    override fun onBackPressed() { if (index > 0) go(index - 1) else done() }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }

    // ---- Pages ----

    private fun title(text: String, size: Float = 36f) = Ui.headline(this, text, size).apply {
        gravity = Gravity.CENTER
        setPadding(0, Ui.dp(context, 18), 0, Ui.dp(context, 10))
    }

    private fun body(text: String) = Ui.label(this, text, 16f, Ui.MUTED).apply {
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.15f)
        setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0)
    }

    /** A card that flips between the plain line and the swapped line. */
    private fun demo(before: String, after: String, highlight: String, label: String): View {
        val card = Ui.card(this, 18).apply { background = Ui.gradient(26, context) }
        val tag = Ui.label(this, label, 12.5f, Ui.GRAD_SUB, Ui.W.SEMI)
        val line = Ui.label(this, before, 19f, Ui.GRAD_INK, Ui.W.MED).apply { setPadding(0, Ui.dp(context, 6), 0, 0) }
        card.addView(tag); card.addView(line)
        var showingAfter = false
        val tick = object : Runnable {
            override fun run() {
                showingAfter = !showingAfter
                line.animate().alpha(0f).setDuration(160).withEndAction {
                    if (showingAfter) {
                        val sp = SpannableString(after)
                        val at = after.indexOf(highlight)
                        if (at >= 0) {
                            sp.setSpan(BackgroundColorSpan(0x4DFF9933), at, at + highlight.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            sp.setSpan(StyleSpan(android.graphics.Typeface.BOLD), at, at + highlight.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        line.text = sp; tag.text = "After"
                    } else { line.text = before; tag.text = label }
                    line.animate().alpha(1f).setDuration(220).start()
                }.start()
                handler.postDelayed(this, if (showingAfter) 2200 else 1400)
            }
        }
        handler.postDelayed(tick, 900)
        return card.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, Ui.dp(context, 24), 0, 0) }
        }
    }

    private fun row(icon: Int, t: String, s: String) = Ui.horizontal(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, Ui.dp(context, 14), 0, 0)
        addView(Ui.iconCircle(context, icon, Ui.SOFT, Ui.INK, 42))
        addView(Ui.vertical(context).apply {
            setPadding(Ui.dp(context, 14), 0, 0, 0)
            addView(Ui.label(context, t, 16f, Ui.INK, Ui.W.SEMI))
            addView(Ui.label(context, s, 13.5f, Ui.MUTED))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun tour(): List<Page> = listOf(
        Page { p ->
            p.addView(SheenImageView(this, R.drawable.seal_white).apply {
                layoutParams = LinearLayout.LayoutParams(Ui.dp(context, 190), Ui.dp(context, 190)).apply { gravity = Gravity.CENTER_HORIZONTAL }
            })
            p.addView(Ui.label(this, "Making censorship work harder,\none word at a time.", 21f, Ui.INK).apply {
                typeface = Ui.SERIF_ITALIC; gravity = Gravity.CENTER; setPadding(0, Ui.dp(context, 18), 0, Ui.dp(context, 14))
            })
            p.addView(body("Some names come with side effects. This app swaps Meta-censored names for code names your readers already understand."))
        },
        Page { p ->
            p.addView(title("*Encode*"))
            p.addView(body("Type or paste your post, or upload a whole speech. Tap “Make it Meta-proof”. Copy. Post."))
            p.addView(demo("Aaj Modi ji ne kaha…", "Aaj Odiji 👑 ne kaha…", "Odiji 👑", "Your post"))
        },
        Page { p ->
            p.addView(title("*Decode*"))
            p.addView(body("Got a message full of code names? Switch to Decode and read it with the real names back."))
            p.addView(demo("Kamal Party 🪷 phir jeetegi", "BJP phir jeetegi", "BJP", "Code-name message"))
        },
        Page { p ->
            p.addView(title("Use it *everywhere*", 32f))
            p.addView(body("Set up once in Settings. Then you never need to open this app."))
            if (BuildConfig.HAS_FLOATING) p.addView(row(R.drawable.ic_sparkle, "Floating button", "Pops up in Instagram & WhatsApp as you type"))
            p.addView(row(R.drawable.ic_keyboard, "Keyboard", "Switch to it after typing a name"))
            p.addView(row(R.drawable.ic_text, "Select text", "Select → ⋮ → Shadowban Suraksha"))
        },
        Page { p ->
            p.addView(title("Satire, *not a shield*", 32f))
            p.addView(row(R.drawable.ic_shield, "No internet", "Your text never leaves this phone"))
            p.addView(row(R.drawable.ic_eye, "Not 100% encrypted", "People, and sharp AI, still know who Mota Bhai is"))
            p.addView(row(R.drawable.ic_check, "Your call", "Use it to be less obvious, not invisible"))
        },
    )

    private fun whatsNew(p: LinearLayout) {
        p.addView(SheenImageView(this, R.drawable.seal_white).apply {
            layoutParams = LinearLayout.LayoutParams(Ui.dp(context, 120), Ui.dp(context, 120)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        })
        p.addView(title("What’s *new*", 34f))
        p.addView(Ui.label(this, "Version ${BuildConfig.VERSION_NAME}", 13.5f, Ui.MUTED).apply { gravity = Gravity.CENTER })
        val card = Ui.card(this, 18).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, Ui.dp(context, 18), 0, 0) }
        }
        WHATS_NEW.forEachIndexed { i, s ->
            card.addView(Ui.horizontal(this).apply {
                gravity = Gravity.TOP
                setPadding(0, if (i == 0) 0 else Ui.dp(context, 12), 0, 0)
                addView(Ui.label(context, "✦", 14f, Ui.SAFFRON, Ui.W.SEMI).apply { setPadding(0, Ui.dp(context, 1), Ui.dp(context, 12), 0) })
                addView(Ui.label(context, s, 15f, Ui.INK))
            })
        }
        p.addView(card)
    }
}
