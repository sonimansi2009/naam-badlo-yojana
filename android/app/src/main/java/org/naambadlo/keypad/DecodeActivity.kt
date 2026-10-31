package org.naambadlo.keypad

/**
 * Opens from the share sheet or the select-text menu ("Decode names"):
 * the same one-box screen, already in Decode mode.
 */
class DecodeActivity : DocumentActivity() {
    override val startMode = Workbench.Mode.DECODE
}
