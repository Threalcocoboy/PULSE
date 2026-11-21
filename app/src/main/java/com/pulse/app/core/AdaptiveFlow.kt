package com.pulse.app.core

enum class WidgetShape { RECTANGLE, SQUARE, CIRCLE, PILL, BANNER }
data class Footprint(val columns: Int, val rows: Int)
data class Widget(val id: String, val metric: Metric, val shape: WidgetShape = WidgetShape.RECTANGLE,
    val width: Int = 2, val height: Int = 2, val column: Int = 0, val row: Int = 0,
    val detailed: Boolean = true, val showGoal: Boolean = true, val showTrend: Boolean = true) {
    fun allowedSizes(): List<Footprint> = when(shape) {
        WidgetShape.CIRCLE -> listOf(Footprint(2,2), Footprint(3,3), Footprint(4,4))
        WidgetShape.SQUARE -> listOf(Footprint(1,1), Footprint(2,2), Footprint(3,3), Footprint(4,4))
        WidgetShape.PILL -> listOf(Footprint(2,1), Footprint(4,1))
        WidgetShape.BANNER -> listOf(Footprint(4,1), Footprint(4,2))
        WidgetShape.RECTANGLE -> listOf(Footprint(2,1), Footprint(2,2), Footprint(4,2), Footprint(4,3))
    }
    fun valid() = id.matches(Regex("[A-Za-z0-9_-]{1,80}")) && Footprint(width, height) in allowedSizes() && column in 0..(4-width) && row in 0..1000
}
object AdaptiveFlow {
    const val COLUMNS = 4
    fun configure(widgets: List<Widget>, id: String, replacement: Widget): List<Widget> {
        require(widgets.any { it.id == id })
        require(replacement.id == id && Footprint(replacement.width,replacement.height) in replacement.allowedSizes())
        val updated = widgets.map { if (it.id == id) replacement.copy(row = it.row, column = it.column) else it }
        return if (isValid(updated)) updated else pack(updated)
    }
    /** A move changes reading order; it never reserves empty rows. First-fit also backfills
     * holes with later small tiles. Returning packing order makes persistence idempotent. */
    fun pack(widgets: List<Widget>, selected: String? = null, column: Int = 0, row: Int = 0): List<Widget> {
        require(widgets.size <= 100)
        require(widgets.map { it.id }.distinct().size == widgets.size)
        widgets.forEach { require(it.id.matches(Regex("[A-Za-z0-9_-]{1,80}")) && Footprint(it.width,it.height) in it.allowedSizes()) }
        val order = widgets.toMutableList()
        widgets.firstOrNull { it.id == selected }?.let { chosen ->
            order.removeAll { it.id == selected }
            val destination = row.coerceAtLeast(0) * COLUMNS + column.coerceIn(0,COLUMNS-chosen.width)
            val forward=destination > chosen.row*COLUMNS+chosen.column
            val index = order.indexOfFirst {
                val anchor=it.row*COLUMNS+it.column
                if(forward) anchor>destination else anchor>=destination
            }
            order.add(if(index < 0) order.size else index,chosen)
        }
        val occupied = mutableSetOf<Pair<Int,Int>>()
        return order.map { w ->
            var y=0; var x=0
            fun fits() = (y until y+w.height).all { r -> (x until x+w.width).all { c -> c to r !in occupied } }
            while(!fits()) { x++; if(x+w.width>COLUMNS) {x=0;y++} }
            for(r in y until y+w.height) for(c in x until x+w.width) occupied += c to r
            w.copy(column=x,row=y)
        }
    }
    fun isValid(widgets: List<Widget>): Boolean {
        if(widgets.size > 100 || widgets.map { it.id }.distinct().size != widgets.size || widgets.any { !it.valid() }) return false
        val cells = mutableSetOf<Pair<Int, Int>>()
        return widgets.all { w -> (w.row until w.row+w.height).all { r -> (w.column until w.column+w.width).all { c -> cells.add(c to r) } } }
    }
    fun defaults() = pack(listOf(
        Widget("steps", Metric.STEPS, width=4, height=2), Widget("heart", Metric.HEART),
        Widget("battery", Metric.BATTERY, WidgetShape.CIRCLE), Widget("sleep", Metric.SLEEP), Widget("stress", Metric.STRESS),
        Widget("weekly", Metric.WEEKLY, WidgetShape.BANNER, 4, 1)))
}
object LayoutCodec {
    fun encode(layout: List<Widget>) = "v1\n" + layout.joinToString("\n") { w -> listOf(w.id,w.metric.name,w.shape.name,w.width,w.height,w.column,w.row,w.detailed,w.showGoal,w.showTrend).joinToString("|") }
    fun decode(raw: String): List<Widget> {
        val lines = raw.lines()
        if (lines.firstOrNull() != "v1" || raw.length >= 40000) return AdaptiveFlow.defaults()
        val rows = lines.drop(1).filter(String::isNotBlank)
        val widgets = rows.mapNotNull { row ->
            runCatching {
                val a = row.split('|')
                val base = Widget(a[0], Metric.valueOf(a[1]), a.getOrNull(2)?.let(WidgetShape::valueOf) ?: WidgetShape.RECTANGLE)
                val requested = Footprint(a.getOrNull(3)?.toIntOrNull() ?: base.width, a.getOrNull(4)?.toIntOrNull() ?: base.height)
                val size = requested.takeIf { it in base.allowedSizes() } ?: base.allowedSizes().first()
                base.copy(width = size.columns, height = size.rows, column = a.getOrNull(5)?.toIntOrNull() ?: 0,
                    row = a.getOrNull(6)?.toIntOrNull() ?: 0, detailed = a.getOrNull(7)?.toBooleanStrictOrNull() ?: true,
                    showGoal = a.getOrNull(8)?.toBooleanStrictOrNull() ?: true, showTrend = a.getOrNull(9)?.toBooleanStrictOrNull() ?: true)
                    .takeIf { it.id.matches(Regex("[A-Za-z0-9_-]{1,80}")) }
            }.getOrNull()
        }.distinctBy { it.id }.take(100)
        if (widgets.isEmpty() && rows.isNotEmpty()) return AdaptiveFlow.defaults()
        return if (AdaptiveFlow.isValid(widgets)) widgets else AdaptiveFlow.pack(widgets)
    }
}
class LayoutHistory(initial: List<Widget>) {
    var current = initial; private set
    private val past = ArrayDeque<List<Widget>>()
    private val future = ArrayDeque<List<Widget>>()
    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()
    fun commit(next: List<Widget>) { require(AdaptiveFlow.isValid(next)); if(next == current) return; past.addLast(current); if(past.size>50) past.removeFirst(); future.clear(); current=next }
    fun undo(): List<Widget> { if(canUndo) { future.addLast(current); current=past.removeLast() }; return current }
    fun redo(): List<Widget> { if(canRedo) { past.addLast(current); current=future.removeLast() }; return current }
    fun savedUndo(limit: Int = 50, maxChars: Int = Int.MAX_VALUE) = encodeHistory(past, limit, maxChars)
    fun savedRedo(limit: Int = 50, maxChars: Int = Int.MAX_VALUE) = encodeHistory(future, limit, maxChars)

    private fun encodeHistory(layouts: List<List<Widget>>, limit: Int, maxChars: Int): ArrayList<String> {
        val saved = ArrayList<String>()
        var size = 0
        for (layout in layouts.asReversed().take(limit)) {
            val encoded = LayoutCodec.encode(layout)
            size += encoded.length
            if (size > maxChars) break
            saved.add(encoded)
        }
        saved.reverse()
        return saved
    }
    fun restore(undo: List<String>, redo: List<String>) { past.clear(); past.addAll(undo.takeLast(50).map(LayoutCodec::decode)); future.clear(); future.addAll(redo.takeLast(50).map(LayoutCodec::decode)) }
}
