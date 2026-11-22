package com.scanread.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withTranslation
import com.scanread.R
import com.scanread.render.BlockSurface
import java.util.Locale

/**
 * Draws covers and translations in screen coordinates. Used by the on-screen overlay and by
 * tests that render results into image files.
 */
class TranslationRenderer(
    context: Context,
    /** Language of the drawn translations; used for hyphenation. */
    private val textLocale: Locale = Locale.getDefault(),
) {

    /**
     * @property text what to draw.
     * @property surface where and how to draw it.
     * @property originalLineHeight height of one original text line (px); the translation aims for it.
     */
    class Item(val text: String, val surface: BlockSurface, val originalLineHeight: Float)

    /** A prepared item; [fits] is `false` when the text did not fit its area at the minimum size. */
    class Prepared internal constructor(
        internal val cover: Bitmap?,
        internal val coverRect: RectF,
        internal val coverColor: Int,
        internal val layout: StaticLayout,
        internal val textLeft: Float,
        internal val textTop: Float,
        val fits: Boolean,
    ) {
        fun recycle() {
            cover?.recycle()
        }
    }

    /** Comic-style lettering; narrow, so longer translations still fit balloons at a readable size. */
    private val typeface: Typeface? = ResourcesCompat.getFont(context, R.font.patrick_hand)

    /**
     * Capital-letter height as a share of the text size for [typeface]. OCR line boxes hug the
     * capitals of comic lettering, so sizes are chosen to match capital heights, whatever the font.
     */
    private val capHeightRatio: Float = Paint().run {
        typeface = this@TranslationRenderer.typeface
        textSize = CAP_MEASURE_SIZE
        val bounds = Rect()
        getTextBounds(CAP_SAMPLE, 0, CAP_SAMPLE.length, bounds)
        (bounds.height() / CAP_MEASURE_SIZE).takeIf { it > 0f } ?: DEFAULT_CAP_HEIGHT_RATIO
    }
    private val cornerRadius = context.resources.getDimension(R.dimen.translation_corner_radius)
    private val minTextSize = context.resources.getDimension(R.dimen.translation_min_text_size)
    private val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun prepare(item: Item): Prepared {
        val surface = item.surface
        val fill = surface.fillBounds
        val area = surface.textArea

        val fitter = TextFitter(
            basePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = surface.textColor
                typeface = this@TranslationRenderer.typeface
            },
            minTextSize = minTextSize,
            locale = textLocale,
        )
        val maxTextSize = item.originalLineHeight * CAP_HEIGHT_TARGET / capHeightRatio
        val fitted = fitter.fit(item.text, area.width, area.height, maxTextSize)
        val layout = fitted.layout
        // Centered on the area; an overflowing layout (wider or taller) extends evenly on both sides.
        val textLeft = area.left + (area.width - layout.width) / 2f
        val textTop = area.top + (area.height - layout.height) / 2f

        val coverRect = RectF(fill.left.toFloat(), fill.top.toFloat(), fill.right.toFloat(), fill.bottom.toFloat())
        if (surface.fillMask == null) {
            // Rectangular covers always extend under overflowing text.
            coverRect.union(textLeft - TEXT_COVER_MARGIN, textTop - TEXT_COVER_MARGIN,
                textLeft + layout.width + TEXT_COVER_MARGIN, textTop + layout.height + TEXT_COVER_MARGIN)
        }

        return Prepared(
            cover = surface.fillMask?.let { mask -> maskBitmap(mask, fill.width, fill.height, surface.fillColor) },
            coverRect = coverRect,
            coverColor = surface.fillColor,
            layout = layout,
            textLeft = textLeft,
            textTop = textTop,
            fits = fitted.fits,
        )
    }

    fun draw(canvas: Canvas, items: List<Prepared>) {
        // All covers first, then all text, so a neighbour's cover never hides a translation.
        for (item in items) {
            val cover = item.cover
            if (cover != null) {
                canvas.drawBitmap(cover, item.coverRect.left, item.coverRect.top, bitmapPaint)
            } else {
                coverPaint.color = item.coverColor
                canvas.drawRoundRect(item.coverRect, cornerRadius, cornerRadius, coverPaint)
            }
        }
        for (item in items) {
            canvas.withTranslation(item.textLeft, item.textTop) { item.layout.draw(this) }
        }
    }

    private fun maskBitmap(mask: BooleanArray, width: Int, height: Int, color: Int): Bitmap {
        val colors = IntArray(mask.size) { if (mask[it]) color else 0 }
        return Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        /** Translated capitals are at most this share of the original line height. */
        const val CAP_HEIGHT_TARGET = 0.85f
        const val CAP_MEASURE_SIZE = 100f
        const val CAP_SAMPLE = "H"
        const val DEFAULT_CAP_HEIGHT_RATIO = 0.7f
        const val TEXT_COVER_MARGIN = 3f
    }
}
