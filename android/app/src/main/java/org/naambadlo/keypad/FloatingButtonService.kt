package org.naambadlo.keypad

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * The floating "Make it Meta friendly" button.
 *
 * Keep typing on your normal keyboard. When the text box you are typing in
 * contains a name from the list, a small pill appears. Tap it and every name
 * in that box is swapped. Long-press to undo. Drag it anywhere.
 *
 * Privacy: this service only looks at the text box you are typing in, never
 * password fields, and the app has no internet permission, so nothing leaves
 * the phone.
 */
class FloatingButtonService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val matcher by lazy { NameStore.matcher(this) }
    private lateinit var wm: WindowManager
    private var pill: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var shown = false
    private var currentPkg: String? = null
    private var undo: Pair<String, String>? = null   // original text to replaced text

    private val check = Runnable { evaluate() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createPill()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()
        if (pkg != null && pkg != packageName && pkg != "com.android.systemui") currentPkg = pkg
        handler.removeCallbacks(check)
        handler.postDelayed(check, 250)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(check)
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
        if (pkg == packageName) return true            // our own test box
        return !Prefs.metaOnly(this) || pkg in Prefs.META_APPS
    }

    private fun evaluate() {
        if (!allowedHere()) return hide()
        val node = focusedField() ?: return hide()
        val text = fieldText(node)
        val count = matcher.findAll(text).size
        when {
            count > 0 -> show("✦ Make it Meta friendly · $count")
            undo?.second == text && text.isNotEmpty() -> show("✓ Meta friendly · hold to undo")
            else -> hide()
        }
    }

    // ---- Actions ----

    private fun makeFriendly() {
        val node = focusedField() ?: return toast("Tap into the text box first")
        val text = fieldText(node)
        val out = matcher.replaceAll(text)
        if (out == text) return
        if (setText(node, out)) {
            undo = text to out
            pill?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } else {
            // Some apps block direct editing. Fall back to the clipboard.
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Naam Badlo", out))
            toast("Copied. Select all in the box and paste.")
        }
        handler.postDelayed(check, 200)
    }

    private fun undoLast() {
        val (original, replaced) = undo ?: return
        val node = focusedField() ?: return
        if (fieldText(node) != replaced) return toast("Nothing to undo here")
        if (setText(node, original)) {
            undo = null
            toast("Undone")
        }
        handler.postDelayed(check, 200)
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

    // ---- The pill ----

    private fun createPill() {
        val v = TextView(this).apply {
            setTextColor(0xFF1D1C1A.toInt())
            textSize = 14f
            setPadding(Ui.dp(context, 14), Ui.dp(context, 9), Ui.dp(context, 14), Ui.dp(context, 9))
            background = Ui.rounded(Ui.ACCENT, 22, context)
            elevation = Ui.dp(context, 6).toFloat()
            contentDescription = "Make it Meta friendly"
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
            x = if (sx >= 0) sx else dm.widthPixels / 3
            y = if (sy >= 0) sy else (dm.heightPixels * 0.30).toInt()
        }
        attachTouch(v, lp)
        pill = v
        params = lp
    }

    private fun attachTouch(v: TextView, lp: WindowManager.LayoutParams) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        val longPress = ViewConfiguration.getLongPressTimeout().toLong()
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        var dragging = false; var longFired = false
        val longRunnable = Runnable { longFired = true; v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); undoLast() }

        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y
                    dragging = false; longFired = false
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
                    if (dragging) Prefs.setButtonPos(this, lp.x, lp.y)
                    else if (!longFired && e.actionMasked == MotionEvent.ACTION_UP) makeFriendly()
                }
            }
            true
        }
    }

    private fun show(label: String) {
        val v = pill ?: return
        v.text = label
        if (!shown) {
            runCatching { wm.addView(v, params) }.onSuccess { shown = true }
        }
    }

    private fun hide() {
        val v = pill ?: return
        if (shown) {
            runCatching { wm.removeView(v) }
            shown = false
        }
    }
}
