package com.scanread.text

import com.scanread.text.model.Box

/**
 * Tells which light image area a piece of text sits in, e.g. the inside of a speech balloon.
 *
 * Text in different areas is separated by a visible boundary and never belongs together.
 */
fun interface RegionLookup {

    /** Returns the area containing [box], or `null` if it cannot be determined. */
    fun regionOf(box: Box): Region?

    /**
     * @property id identifies the area; equal ids mean the same connected area.
     * @property isEnclosed the area is fully surrounded by a boundary and reasonably small,
     * like a speech balloon (as opposed to the page background).
     */
    data class Region(val id: Int, val isEnclosed: Boolean)

    companion object {
        val NONE = RegionLookup { null }
    }
}
