package com.dkanada.gramophone.views

import android.content.Context
import android.graphics.Canvas
import android.text.Layout
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatTextView

/**
 * One lyric line that fills with a second color as it plays, like a karaoke display. The text color is the base,
 * and [fill] is the fraction of the laid-out width drawn again in [fillColor]. A wrapped line fills one visual line
 * after another, each from its own start edge, so right-to-left text fills from the right.
 *
 * Changing the fill only redraws. Text, size and wrapping stay put, so a line filling in a list never moves it.
 */
class LyricLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {
    @ColorInt
    var fillColor = 0
        set(value) {
            if (field == value) return
            field = value
            if (fill > 0f) invalidate()
        }

    var fill = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field == clamped) return
            field = clamped
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val layout = layout ?: return
        if (fill <= 0f || layout.lineCount == 0) return

        var total = 0f
        for (line in 0 until layout.lineCount) total += layout.getLineWidth(line)
        var remaining = fill * total

        val paint = paint
        val baseColor = paint.color
        paint.color = fillColor

        val left = totalPaddingLeft.toFloat()
        val top = totalPaddingTop.toFloat()
        val save = canvas.save()
        canvas.translate(left, top)

        // a line's clip reaches past the text's own box, so a glyph that overhangs it isn't cut off
        val outerLeft = -left
        val outerRight = width - left
        for (line in 0 until layout.lineCount) {
            if (remaining <= 0f) break

            val lineWidth = layout.getLineWidth(line)
            val lineTop = if (line == 0) -top else layout.getLineTop(line).toFloat()
            val lineBottom = if (line == layout.lineCount - 1) height - top else layout.getLineBottom(line).toFloat()

            val lineSave = canvas.save()
            if (remaining >= lineWidth) {
                canvas.clipRect(outerLeft, lineTop, outerRight, lineBottom)
            } else if (layout.getParagraphDirection(line) == Layout.DIR_RIGHT_TO_LEFT) {
                canvas.clipRect(layout.getLineRight(line) - remaining, lineTop, outerRight, lineBottom)
            } else {
                canvas.clipRect(outerLeft, lineTop, layout.getLineLeft(line) + remaining, lineBottom)
            }

            // the layout skips the lines outside the clip, so this draws only the one line
            layout.draw(canvas)
            canvas.restoreToCount(lineSave)
            remaining -= lineWidth
        }

        canvas.restoreToCount(save)
        paint.color = baseColor
    }
}
