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
    fun pack(widgets: List<Widget>, selected: String? = null, column: Int = 0, row: Int = 0): List<Widget> {
        require(widgets.size <= 100) { "Dashboard supports up to 100 widgets" }
        require(widgets.map { it.id }.distinct().size == widgets.size) { "Duplicate widget IDs" }
        widgets.forEach { require(Footprint(it.width, it.height) in it.allowedSizes()) { "Unsupported footprint" } }
        val occupied = mutableSetOf<Pair<Int, Int>>()
        fun fits(w: Widget, x: Int, y: Int) = x >= 0 && x+w.width <= COLUMNS && y >= 0 &&
            (y until y+w.height).all { r -> (x until x+w.width).all { c -> c to r !in occupied } }
        fun reserve(w: Widget, x: Int, y: Int): Widget {
            for(r in y until y+w.height) for(c in x until x+w.width) occupied += c to r
            return w.copy(column = x, row = y)
        }
        val result = mutableMapOf<String, Widget>()
        widgets.firstOrNull { it.id == selected }?.let { result[it.id] = reserve(it, column.coerceIn(0, COLUMNS-it.width), row.coerceIn(0, widgets.size*4)) }
        widgets.filter { it.id != selected }.forEach { w ->
            var y = 0; var x = 0
            while(!fits(w, x, y)) { x++; if(x+w.width > COLUMNS) { x = 0; y++ } }
            result[w.id] = reserve(w, x, y)
        }
        return widgets.map { result.getValue(it.id) }
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
    fun decode(raw: String): List<Widget> = runCatching {
        require(raw.startsWith("v1\n")); require(raw.length < 40000)
        val rows = raw.removePrefix("v1\n").lines().filter { it.isNotBlank() }
        val result = rows.map { row -> val a = row.split('|'); require(a.size == 10)
            Widget(a[0],Metric.valueOf(a[1]),WidgetShape.valueOf(a[2]),a[3].toInt(),a[4].toInt(),a[5].toInt(),a[6].toInt(),a[7].toBooleanStrict(),a[8].toBooleanStrict(),a[9].toBooleanStrict()) }
        require(AdaptiveFlow.isValid(result)); result
    }.getOrElse { AdaptiveFlow.defaults() }
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
    fun savedUndo() = ArrayList(past.map(LayoutCodec::encode))
    fun savedRedo() = ArrayList(future.map(LayoutCodec::encode))
    fun restore(undo: List<String>, redo: List<String>) { past.clear(); past.addAll(undo.takeLast(50).map(LayoutCodec::decode)); future.clear(); future.addAll(redo.takeLast(50).map(LayoutCodec::decode)) }
}
