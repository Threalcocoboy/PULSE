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
}
