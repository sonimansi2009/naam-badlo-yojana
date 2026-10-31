package org.naambadlo.keypad

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.PixelFormat
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * The floating "Make it Meta friendly" button (full version only).
 *
 * Keep typing on your normal keyboard. When the text box contains a name from
 * the list, a blue pill appears. Tap it to swap every name; tap again for fresh
 * code names. After a swap the pill offers Undo for a few seconds. For a bare
 * first name or common surname ("Shah") it asks first: "Amit Shah?  Yes  No".
 *
 * Privacy: this service only looks at the text box you are typing in, never
 * password fields, and the app has no internet permission.
 */
class FloatingButtonService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val matcher by lazy { NameStore.matcher(this) }
    private val memory get() = NameStore.memory
    private lateinit var wm: WindowManager
    private var pill: LinearLayout? = null
    private lateinit var label: TextView
    private lateinit var yesBtn: TextView
    private lateinit var noBtn: TextView
    private var params: WindowManager.LayoutParams? = null
    private var shown = false
    private var currentPkg: String? = null

    private var undoOriginal: String? = null   // text before the first swap
    private var lastOut: String? = null        // text after the latest swap
    private var celebrateUntil = 0L

    // Answers to "Do you mean …?" for bare first names / common surnames
    private val confirmed = HashSet<String>()
    private val declined = HashSet<String>()
    private var question: Match? = null
    private var questionText: String? = null

    private val check = Runnable { evaluate() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Ui.init(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createPill()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()
        if (pkg != null && pkg != "com.android.systemui") {
            if (pkg != currentPkg) { confirmed.clear(); declined.clear() }
            currentPkg = pkg
        }
        handler.removeCallbacks(check)
        handler.postDelayed(check, 220)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        pill?.let { runCatching { wm.removeView(it) } }
        pill = null
        super.onDestroy()
    }

    // ---- What's in the text box ----

    private fun focusedField(): AccessibilityNodeInfo? {
        val n = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
        if (!n.isEditable || n.isPassword) return null
        return n
    }

    private fun fieldText(n: AccessibilityNodeInfo): String {
        if (Build.VERSION.SDK_INT >= 26 && n.isShowingHintText) return ""
        return n.text?.toString() ?: ""
    }

    private fun allowedHere(): Boolean {
        val pkg = currentPkg ?: return false
        if (pkg == packageName) return true            // the app's own test box
        return !Prefs.metaOnly(this) || pkg in Prefs.META_APPS
    }

    private fun key(text: String, m: Match) = Matcher.normalize(text.substring(m.start, m.end))

    /** Real names we will swap now: sure ones, plus bare names the user said yes to. */
    private fun ready(text: String, m: Match) =
        !m.fromMemory && (!m.ask || key(text, m) in confirmed)

    private fun pending(text: String, m: Match) =
        m.ask && !m.fromMemory && key(text, m) !in confirmed && key(text, m) !in declined

    private fun evaluate() {
        if (SystemClock.uptimeMillis() < celebrateUntil) return
        if (!allowedHere()) return hide()
        val node = focusedField() ?: return hide()
        val text = fieldText(node)
        val found = matcher.findAll(text, memory)
        val real = found.count { ready(text, it) }
        val ask = found.firstOrNull { pending(text, it) }
        val again = found.count { it.fromMemory }
        if (!shown && (real > 0 || ask != null)) Feedback.found(this)   // soft chime when a name is spotted
        when {
            real > 0 -> showAction("Make it Meta friendly · $real")
            ask != null -> showQuestion(text, ask)
            again > 0 -> showAction("Fresh names · $again")
            else -> hide()
        }
    }

    // ---- Actions ----

    private fun makeFriendly() {
        if (SystemClock.uptimeMillis() < celebrateUntil) return undoLast()
        val node = focusedField() ?: return toast("Tap into the text box first")
        val text = fieldText(node)
        val out = matcher.replaceAll(text, memory, include = { m, _ ->
            m.fromMemory || ready(text, m)
        }, pick = NameStore::pick)
        if (out == text) return
        if (setText(node, out)) {
            if (text != lastOut || undoOriginal == null) undoOriginal = text
            lastOut = out
            celebrate()
        } else {
            // Some apps block direct editing. Fall back to the clipboard.
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(NameStore.APP_NAME, out))
            toast("Copied. Select all in the box and paste.")
        }
    }

    private fun answer(yes: Boolean) {
        val m = question ?: return
        val text = questionText ?: return
        val k = key(text, m)
        if (yes) confirmed.add(k) else declined.add(k)
        question = null
        if (yes) {
            val node = focusedField() ?: return
            val now = fieldText(node)
            val out = matcher.replaceAll(now, memory, include = { mm, _ ->
                !mm.fromMemory && key(now, mm) == k
            }, pick = NameStore::pick)
            if (out != now && setText(node, out)) {
                if (now != lastOut || undoOriginal == null) undoOriginal = now
                lastOut = out
                celebrate()
                return
            }
        }
        handler.postDelayed(check, 120)
    }

    private fun undoLast() {
        val original = undoOriginal ?: return toast("Nothing to undo")
        val node = focusedField() ?: return
        if (fieldText(node) != lastOut) return toast("Nothing to undo here")
        if (setText(node, original)) {
            undoOriginal = null; lastOut = null; celebrateUntil = 0
            toast("Undone")
        }
        handler.postDelayed(check, 150)
    }

    private fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false
        val sel = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, value.length)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, value.length)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
        node.refresh()
        return fieldText(node) == value
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /** One-tap copy of the whole converted text box. */
    private fun copyField() {
        val text = focusedField()?.let { fieldText(it) } ?: lastOut ?: return
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(NameStore.APP_NAME, text))
        toast("Copied")
    }

    // ---- The pill ----

    /** After a swap: the message plus a visible Undo for a few seconds. */
    private fun celebrate() {
        val v = pill ?: return
        celebrateUntil = SystemClock.uptimeMillis() + 4500
        Feedback.swapped(this)
        label.maxWidth = (resources.displayMetrics.widthPixels * 0.62).toInt()
        label.maxLines = 2
        yesBtn.text = "Undo"
        yesBtn.visibility = View.VISIBLE
        noBtn.text = "Copy"
        noBtn.visibility = View.VISIBLE
        show(NameStore.CONGRATS)
        v.scaleX = 0.9f; v.scaleY = 0.9f
        v.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(OvershootInterpolator(2.2f)).start()
        handler.postDelayed({
            celebrateUntil = 0
            label.maxLines = 1
            evaluate()
        }, 4550)
    }

    private fun showAction(text: String) {
        question = null
        yesBtn.visibility = View.GONE
        noBtn.visibility = View.GONE
        label.maxLines = 1
        show(text)
    }

    private fun showQuestion(text: String, m: Match) {
        question = m; questionText = text
        val word = text.substring(m.start, m.end)
        yesBtn.text = "Yes"; yesBtn.visibility = View.VISIBLE
        noBtn.text = "No"; noBtn.visibility = View.VISIBLE
        label.maxLines = 2
        label.maxWidth = (resources.displayMetrics.widthPixels * 0.5).toInt()
        show("“$word” means ${m.entity.label}?")
    }

    private fun smallButton(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        typeface = Ui.SANS_SEMI
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(Ui.WHITE)
        background = Ui.rounded(Ui.GRAD_INK, 18, context)
        setPadding(Ui.dp(context, 14), Ui.dp(context, 7), Ui.dp(context, 14), Ui.dp(context, 7))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(Ui.dp(context, 8), 0, 0, 0) }
        isClickable = true
        setOnClickListener { onClick() }
        visibility = View.GONE
    }

    private fun createPill() {
        val dot = Ui.dp(this, 32)
        val chameleon = getDrawable(R.drawable.ic_chameleon)!!
        val badge = LayerDrawable(arrayOf(Ui.oval(Ui.WHITE).apply { setStroke(Ui.dp(this@FloatingButtonService, 1), 0xFFD9D2FF.toInt()) }, InsetDrawable(chameleon, Ui.dp(this, 2))))
            .apply { setBounds(0, 0, dot, dot) }
        label = TextView(this).apply {
            setTextColor(Ui.GRAD_INK)
            typeface = Ui.SANS_SEMI
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxLines = 1
            gravity = Gravity.CENTER_VERTICAL
            setCompoundDrawables(badge, null, null, null)
            compoundDrawablePadding = Ui.dp(context, 10)
        }
        yesBtn = smallButton("Yes") {
            if (SystemClock.uptimeMillis() < celebrateUntil) undoLast() else answer(true)
        }
        noBtn = smallButton("No") {
            if (SystemClock.uptimeMillis() < celebrateUntil) copyField() else answer(false)
        }
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 6), Ui.dp(context, 6), Ui.dp(context, 8), Ui.dp(context, 6))
            background = Ui.gradient(28, context)
            elevation = Ui.dp(context, 10).toFloat()
            contentDescription = "Make it Meta friendly"
            addView(label)
            addView(yesBtn)
            addView(noBtn)
        }
        val dm = resources.displayMetrics
        val (sx, sy) = Prefs.buttonPos(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (sx >= 0) sx else dm.widthPixels / 5
            y = if (sy >= 0) sy else (dm.heightPixels * 0.30).toInt()
        }
        attachTouch(v, lp)
        pill = v
        params = lp
    }

    private fun attachTouch(v: View, lp: WindowManager.LayoutParams) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        val longPress = ViewConfiguration.getLongPressTimeout().toLong()
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        var dragging = false; var longFired = false
        val longRunnable = Runnable {
            longFired = true
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            undoLast()
        }

        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y
                    dragging = false; longFired = false
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start()
                    handler.postDelayed(longRunnable, longPress)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        handler.removeCallbacks(longRunnable)
                    }
                    if (dragging) {
                        lp.x = (startX + dx).toInt().coerceAtLeast(0)
                        lp.y = (startY + dy).toInt().coerceAtLeast(0)
                        runCatching { wm.updateViewLayout(v, lp) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longRunnable)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(160)
                        .setInterpolator(OvershootInterpolator(2f)).start()
                    if (dragging) Prefs.setButtonPos(this, lp.x, lp.y)
                    else if (!longFired && e.actionMasked == MotionEvent.ACTION_UP && question == null) makeFriendly()
                }
            }
            true
        }
    }

    private fun show(text: String) {
        val v = pill ?: return
        label.text = text
        if (shown) return
        shown = true
        v.animate().cancel()
        if (!v.isAttachedToWindow) {
            if (runCatching { wm.addView(v, params) }.isFailure) { shown = false; return }
        }
        v.alpha = 0f; v.scaleX = 0.85f; v.scaleY = 0.85f
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220)
            .setInterpolator(OvershootInterpolator(1.6f)).start()
    }

    private fun hide() {
        val v = pill ?: return
        if (!shown) return
        shown = false
        question = null
        v.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(160).withEndAction {
            if (!shown) runCatching { wm.removeView(v) }
        }.start()
    }
}
