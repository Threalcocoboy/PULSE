package com.pulse.app.core

import kotlin.math.abs

enum class StudioGesture { MOVE, RESIZE }
object StudioGestureRules {
    fun resizeHit(editing: Boolean, selected: Boolean, x: Float, y: Float, width: Float, height: Float, target: Float): Boolean =
        editing && selected && x in (width-target)..width && y in (height-target)..height

    /** A new size must be meaningfully closer than the current size, preventing boundary flicker. */
    fun resize(widget: Widget, columns: Float, rows: Float, previous: Footprint): Footprint {
        val square=widget.shape in setOf(WidgetShape.CIRCLE,WidgetShape.SQUARE)
        val dx=columns-widget.width;val dy=rows-widget.height
        val diameter=widget.width+if(abs(dx)>abs(dy))dx else dy
        fun distance(s: Footprint) = if(square)2*abs(s.columns-diameter)else abs(s.columns-columns)+abs(s.rows-rows)
        val best=widget.allowedSizes().minBy(::distance)
        return if(distance(best)+0.28f < distance(previous)) best else previous
    }

    data class LiveDimensions(val widthPx: Float, val heightPx: Float, val targetFootprint: Footprint)

    fun calculateLive(widget: Widget, deltaX: Float, deltaY: Float, stepX: Float, stepY: Float, gapPx: Float, previous: Footprint): LiveDimensions {
        val baseW = widget.width * stepX - gapPx
        val baseH = widget.height * stepY - gapPx
        val rawCols = widget.width + deltaX / stepX
        val rawRows = widget.height + deltaY / stepY
        val target = resize(widget, rawCols, rawRows, previous)

        val (liveW, liveH) = when (widget.shape) {
            WidgetShape.CIRCLE -> {
                val minD = 2 * stepX - gapPx
                val maxD = 4 * stepX - gapPx
                val d = if (abs(deltaX) > abs(deltaY)) deltaX else deltaY
                val size = (baseW + d).coerceIn(minD, maxD)
                size to size
            }
            WidgetShape.SQUARE -> {
                val minD = 1 * stepX - gapPx
                val maxD = 4 * stepX - gapPx
                val d = if (abs(deltaX) > abs(deltaY)) deltaX else deltaY
                val size = (baseW + d).coerceIn(minD, maxD)
                size to size
            }
            WidgetShape.PILL -> {
                val minW = 2 * stepX - gapPx
                val maxW = 4 * stepX - gapPx
                val w = (baseW + deltaX).coerceIn(minW, maxW)
                w to baseH
            }
            WidgetShape.BANNER -> {
                val minH = 1 * stepY - gapPx
                val maxH = 2 * stepY - gapPx
                val h = (baseH + deltaY).coerceIn(minH, maxH)
                baseW to h
            }
            WidgetShape.RECTANGLE -> {
                val minW = 2 * stepX - gapPx
                val maxW = 4 * stepX - gapPx
                val minH = 1 * stepY - gapPx
                val maxH = 3 * stepY - gapPx
                (baseW + deltaX).coerceIn(minW, maxW) to (baseH + deltaY).coerceIn(minH, maxH)
            }
        }
        return LiveDimensions(liveW, liveH, target)
    }
}
