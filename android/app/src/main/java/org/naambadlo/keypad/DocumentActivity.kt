package org.naambadlo.keypad

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Opens from the share sheet ("Make it Meta friendly"): text or a file shared from
 * another app lands in the same one-box screen as Home, in Encode mode.
 */
open class DocumentActivity : Activity() {

    protected lateinit var bench: Workbench
    protected open val startMode = Workbench.Mode.ENCODE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        window.statusBarColor = Ui.BG_TOP
        window.navigationBarColor = Ui.BG

        val col = Ui.vertical(this).apply {
            setPadding(Ui.dp(context, 20), Ui.dp(context, 14), Ui.dp(context, 20), Ui.dp(context, 40))
        }
        val scroll = ScrollView(this).apply { background = Ui.pageBackground(); isVerticalScrollBarEnabled = false; addView(col) }
        col.addView(Ui.label(this, "←", 22f, Ui.INK, Ui.W.MED).apply {
            gravity = Gravity.CENTER
            background = Ui.oval(Ui.SURFACE)
            layoutParams = LinearLayout.LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44)).apply { bottomMargin = Ui.dp(context, 18) }
            setOnClickListener { finish() }
            Ui.pressable(this)
        })
        bench = Workbench(this, startMode) { v: View -> scroll.smoothScrollTo(0, v.top) }
        col.addView(bench.view)
        setContentView(scroll)
        Ui.enter(bench.view, 0)
        handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncoming(it) }
    }

    protected open fun handleIncoming(i: Intent) {
        when (i.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val uri = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) bench.loadUri(uri)
                else i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let { bench.setText(it, startMode, go = true) }
            }
            Intent.ACTION_PROCESS_TEXT ->
                i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.let { bench.setText(it, startMode, go = true) }
        }
    }

    @Deprecated("Activity result API kept simple for minSdk 24")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        bench.onActivityResult(requestCode, resultCode, data)
    }
}
