package com.pulse.app.integration.googlehealth

import com.pulse.app.core.*
import com.pulse.app.core.Metric.*
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object GoogleHealthMapping {
    fun date(point: JSONObject, type: GoogleHealthType, zone: ZoneId, rollup: Boolean = false): LocalDate? {
        if (rollup) return point.optJSONObject("civilStartTime").civilDate()
        val data = point.optJSONObject(type.payloadKey) ?: return null
        val interval = data.optJSONObject("interval")
        val sample = data.optJSONObject("sampleTime")
        return when (type.time) {
            GoogleHealthTime.DAILY -> data.optJSONObject("date").date()
            GoogleHealthTime.SAMPLE -> sample?.optJSONObject("civilTime").civilDate()
                ?: sample.localTime("physicalTime", "utcOffset", zone)?.toLocalDate()
            GoogleHealthTime.START -> interval?.optJSONObject("civilStartTime").civilDate()
                ?: interval.localTime("startTime", "startUtcOffset", zone)?.toLocalDate()
            GoogleHealthTime.END -> interval?.optJSONObject("civilEndTime").civilDate()
                ?: interval.localTime("endTime", "endUtcOffset", zone)?.toLocalDate()
        }
    }

    fun reduce(type: GoogleHealthType, points: List<JSONObject>, details: List<JSONObject>, day: GoogleHealthDay, zone: ZoneId) {
        val values = points.mapNotNull { it.optJSONObject(type.payloadKey) }
        val value = values.firstOrNull()
        fun set(metric: Metric, number: Double?) { if (number != null) day.values[metric] = number }
        fun set(metric: Metric, field: String, factor: Double = 1.0) { set(metric, value.num(field)?.times(factor)) }
        fun average(metric: Metric, field: String) { set(metric, values.mapNotNull { it.num(field) }.mean()) }
        when (type.id) {
            "steps" -> set(STEPS, "countSum")
            "distance" -> set(DISTANCE, "millimetersSum", 1.0 / 1_000_000)
            "altitude" -> set(ELEVATION, "gainMillimetersSum", 0.001)
            "active-energy-burned" -> set(ACTIVE_CALORIES, "kcalSum")
            "total-calories" -> set(CALORIES, "kcalSum")
            "basal-energy-burned" -> set(BASAL_CALORIES, values.mapNotNull { it.num("kcal") }.total())
            "floors" -> set(FLOORS, "countSum")
            "heart-rate" -> set(HEART, "beatsPerMinuteAvg")
            "body-fat" -> set(BODY_FAT, "bodyFatPercentageAvg")
            "weight" -> set(WEIGHT, "weightGramsAvg", 0.001)
            "height" -> set(HEIGHT, values.mapNotNull { it.num("heightMillimeters") }.mean()?.div(10))
            "blood-glucose" -> average(BLOOD_GLUCOSE, "bloodGlucoseMilligramsPerDeciliter")
            "daily-vo2-max" -> average(DAILY_VO2_MAX, "vo2Max")
            "vo2-max" -> average(VO2_MAX, "vo2Max")
            "run-vo2-max" -> set(RUN_VO2_MAX, "rateAvg")
            "daily-resting-heart-rate" -> average(RESTING_HEART, "beatsPerMinute")
            "heart-rate-variability" -> average(HRV, "rootMeanSquareOfSuccessiveDifferencesMilliseconds")
            "daily-heart-rate-variability" -> average(DAILY_HRV, "averageHeartRateVariabilityMilliseconds")
            "oxygen-saturation" -> average(SPO2, "percentage")
            "daily-oxygen-saturation" -> average(DAILY_SPO2, "averagePercentage")
            "daily-respiratory-rate" -> average(DAILY_RESPIRATORY_RATE, "breathsPerMinute")
            "respiratory-rate-sleep-summary" -> set(RESPIRATORY_RATE, values.mapNotNull {
                it.optJSONObject("fullSleepStats").num("breathsPerMinute")
            }.mean())
            "core-body-temperature" -> average(BODY_TEMPERATURE, "temperatureCelsius")
            "daily-sleep-temperature-derivations" -> {
                average(SLEEP_TEMPERATURE, "nightlyTemperatureCelsius")
                average(TEMPERATURE_BASELINE, "baselineTemperatureCelsius")
            }
            "active-minutes" -> {
                val levels = value?.optJSONArray("activeMinutesRollupByActivityLevel").objects()
                set(ACTIVE, levels.mapNotNull { it.num("activeMinutesSum") }.total())
                day.detail(ACTIVE, levels.mapNotNull { row ->
                    row.num("activeMinutesSum")?.let { "${row.optString("activityLevel").title()} · ${formatted(it)} min" }
                })
            }
            "active-zone-minutes" -> set(ACTIVE_ZONE,
                listOf("sumInFatBurnHeartZone", "sumInCardioHeartZone", "sumInPeakHeartZone")
                    .mapNotNull { value.num(it) }.total())
            "activity-level" -> mapActivityLevels(values, day)
            "sedentary-period" -> set(SEDENTARY, value.seconds("durationSum")?.div(60))
            "time-in-heart-rate-zone" -> {
                val zones = value?.optJSONArray("timeInHeartRateZones").objects()
                set(HEART_ZONE_TIME, zones.mapNotNull { it.seconds("duration")?.div(60) }.total())
                day.detail(HEART_ZONE_TIME, zones.mapNotNull { row ->
                    row.seconds("duration")?.let { "${row.optString("heartRateZone").title()} · ${formatted(it / 60)} min" }
                })
            }
            "calories-in-heart-rate-zone" -> {
                val zones = value?.optJSONArray("caloriesInHeartRateZones").objects()
                set(HEART_ZONE_ENERGY, zones.mapNotNull { it.num("kcal") }.total())
                day.detail(HEART_ZONE_ENERGY, zones.mapNotNull { row ->
                    row.num("kcal")?.let { "${row.optString("heartRateZone").title()} · ${formatted(it)} kcal" }
                })
            }
            "daily-heart-rate-zones" -> day.detail(HEART_ZONES, values.flatMap {
                it.optJSONArray("heartRateZones").objects().mapNotNull { row ->
                    val minimum = row.num("minBeatsPerMinute") ?: return@mapNotNull null
                    val maximum = row.num("maxBeatsPerMinute")
                    "${row.optString("heartRateZoneType").title()} · ${formatted(minimum)}${maximum?.let { "–${formatted(it)}" } ?: "+"} bpm"
                }
            })
            "swim-lengths-data" -> {
                set(SWIM_STROKES, values.mapNotNull { it.num("strokeCount") }.total())
                day.detail(SWIM_STROKES, values.groupBy { it.optString("swimStrokeType").title() }.mapNotNull { (style, strokes) ->
                    strokes.mapNotNull { it.num("strokeCount") }.total()?.let { "$style · ${formatted(it)} strokes" }
                })
            }
            "hydration-log" -> {
                set(HYDRATION, value?.optJSONObject("amountConsumed").num("millilitersSum")?.div(1000))
                day.detail(HYDRATION, details.mapNotNull { point ->
                    val log = point.optJSONObject("hydrationLog") ?: return@mapNotNull null
                    val ml = log.optJSONObject("amountConsumed").num("milliliters") ?: return@mapNotNull null
                    "${localStart(log, zone)?.format(clock) ?: "Water"} · ${formatted(ml)} mL"
                })
            }
            "nutrition-log" -> {
                set(FOOD_ENERGY, value?.optJSONObject("energy").num("kcalSum"))
                set(CARBOHYDRATES, value?.optJSONObject("totalCarbohydrate").num("gramsSum"))
                set(FAT, value?.optJSONObject("totalFat").num("gramsSum"))
                val nutrients = value?.optJSONArray("nutrients").objects()
                set(PROTEIN, nutrients.firstOrNull { it.optString("nutrient") == "PROTEIN" }?.optJSONObject("quantity").num("gramsSum"))
                day.detail(FOOD_ENERGY, nutrients.mapNotNull { nutrient ->
                    nutrient.optJSONObject("quantity").num("gramsSum")?.let {
                        "${nutrient.optString("nutrient").title()} · ${formatted(it)} g"
                    }
                })
                val logs = details.mapNotNull { it.optJSONObject("nutritionLog") }
                set(FOOD_LOGS, logs.size.toDouble().takeIf { logs.isNotEmpty() })
                day.detail(FOOD_LOGS, logs.map { foodLog(it, zone) })
            }
            "exercise" -> mapExercises(points, day, zone)
            "sleep" -> mapSleep(values, day, zone)
        }
    }

    fun heartSample(point: JSONObject): HeartSample? {
        val heart = point.optJSONObject("heartRate") ?: return null
        val time = heart.optJSONObject("sampleTime").instant("physicalTime") ?: return null
        val bpm = heart.num("beatsPerMinute")?.takeIf { it > 0 } ?: return null
        val source = point.optJSONObject("dataSource")
        val label = source?.optJSONObject("device")?.string("displayName")
            ?: source?.optJSONObject("application")?.string("packageName") ?: "Google Health"
        return HeartSample(time, bpm, label)
    }

    fun unique(points: List<JSONObject>): List<JSONObject> = points.distinctBy { point ->
        point.string("dataPointName") ?: point.string("name") ?: point.toString()
    }

    private fun mapActivityLevels(values: List<JSONObject>, day: GoogleHealthDay) {
        val mapping = mapOf("LIGHTLY_ACTIVE" to LIGHT_ACTIVITY, "MODERATELY_ACTIVE" to MODERATE_ACTIVITY,
            "VERY_ACTIVE" to VIGOROUS_ACTIVITY)
        values.groupBy { it.optString("activityLevelType") }.forEach { (activity, rows) ->
            val metric = mapping[activity] ?: return@forEach
            val minutes = rows.mapNotNull { it.optJSONObject("interval").bounds() }.takeIf(List<*>::isNotEmpty)
                ?.let { HistoryMath.sleepHours(it) * 60 }
            if (minutes != null) day.values[metric] = minutes
        }
    }

    private fun mapExercises(points: List<JSONObject>, day: GoogleHealthDay, zone: ZoneId) {
        val rows = unique(points).mapNotNull { point ->
            val data = point.optJSONObject("exercise") ?: return@mapNotNull null
            val bounds = data.optJSONObject("interval").bounds() ?: return@mapNotNull null
            Triple(point, data, bounds)
        }.sortedBy { it.third.first }
        val accepted = mutableListOf<Triple<JSONObject, JSONObject, Pair<Instant, Instant>>>()
        for (row in rows) {
            val type = workoutTypeName(row.second.optString("exerciseType"))
            val duplicate = accepted.any { other ->
                val overlap = Duration.between(maxOf(row.third.first, other.third.first), minOf(row.third.second, other.third.second)).seconds
                val shorter = minOf(Duration.between(row.third.first, row.third.second).seconds,
                    Duration.between(other.third.first, other.third.second).seconds)
                type == workoutTypeName(other.second.optString("exerciseType")) && overlap > 0 && overlap * 2 >= shorter
            }
            if (!duplicate) accepted += row
        }
        if (accepted.isEmpty()) return
        day.values[ACTIVITIES] = accepted.size.toDouble()
        val workoutMinutes = mutableListOf<Double>()
        val swimLengths = mutableListOf<Double>()
        for ((point, data, bounds) in accepted) {
            val type = workoutTypeName(data.optString("exerciseType"))
            val summary = data.optJSONObject("metricsSummary")
            val minutes = data.seconds("activeDuration")?.div(60)
            val distance = summary.num("distanceMillimeters")?.div(1_000_000)
            val start = localStart(data, zone)?.toString() ?: bounds.first.atZone(zone).toLocalTime().toString()
            val id = point.string("dataPointName") ?: point.string("name") ?: "google-health:${bounds.first}:${bounds.second}:$type"
            day.activities += RecordedActivity(id.substringAfterLast('/'), data.string("displayName") ?: type, type, start, distance, minutes)
            minutes?.let(workoutMinutes::add)
            summary.num("totalSwimLengths")?.let(swimLengths::add)
            val elapsed = HistoryMath.sleepHours(listOf(bounds)) * 60
            val measurements = listOfNotNull("${formatted(elapsed)} min elapsed", minutes?.let { "${formatted(it)} min active" },
                distance?.let { "${formatted(it)} km" },
                summary.num("caloriesKcal")?.let { "${formatted(it)} kcal" },
                summary.num("steps")?.let { "${formatted(it)} steps" },
                summary.num("activeZoneMinutes")?.let { "${formatted(it)} min in active zones" },
                summary.num("averageHeartRateBeatsPerMinute")?.let { "${formatted(it)} bpm average" },
                summary.num("elevationGainMillimeters")?.let { "${formatted(it / 1000)} m ascent" })
            day.detail(ACTIVITIES, listOf("$type · ${measurements.joinToString(" · ")}"))
        }
        if (workoutMinutes.size == accepted.size) day.values[WORKOUT_DURATION] = workoutMinutes.sum()
        swimLengths.total()?.let { day.values[SWIM_LENGTHS] = it }
    }

    private fun mapSleep(values: List<JSONObject>, day: GoogleHealthDay, zone: ZoneId) {
        val candidates = values.mapNotNull { row -> row.optJSONObject("interval").bounds()?.let { row to it } }
            .sortedWith(compareByDescending<Pair<JSONObject, Pair<Instant, Instant>>> {
                it.first.optJSONObject("metadata")?.optBoolean("mainSleep") == true
            }.thenByDescending { Duration.between(it.second.first, it.second.second) })
        val accepted = mutableListOf<Pair<JSONObject, Pair<Instant, Instant>>>()
        candidates.forEach { row ->
            if (accepted.none { it.second.first < row.second.second && row.second.first < it.second.second }) accepted += row
        }
        if (accepted.isEmpty()) return
        day.sleepStart = accepted.first().second.first
        day.sleepEnd = accepted.first().second.second
        day.sleepWakeMinute = accepted.first().first.optJSONObject("interval")?.let { interval ->
            (interval.optJSONObject("civilEndTime").civilClock() ?: interval.localTime("endTime", "endUtcOffset", zone)?.toLocalTime())
                ?.let { it.hour * 60 + it.minute }
        }
        val asleepHours = mutableListOf<Double>()
        val awakeHours = mutableListOf<Double>()
        val inBedHours = mutableListOf<Double>()
        val sleepPeriod = mutableListOf<Double>()
        for ((row, interval) in accepted.sortedBy { it.second.first }) {
            val stages = row.optJSONArray("stages").objects().groupBy { it.optString("type").lowercase(Locale.ROOT) }
            val exact = stages.mapValues { (_, group) -> group.mapNotNull { it.bounds() }.takeIf(List<*>::isNotEmpty)
                ?.let(HistoryMath::sleepHours) }.mapNotNull { (name, duration) -> duration?.let { name to it } }.toMap()
            val summary = row.optJSONObject("summary")
            summary.num("minutesInSleepPeriod")?.div(60)?.let(sleepPeriod::add)
            if (row.optJSONObject("metadata")?.optBoolean("manuallyEdited") == true && row == accepted.first().first) {
                summary.num("minutesToFallAsleep")?.let { day.values[SLEEP_LATENCY] = it }
            }
            val totals = summary?.optJSONArray("stagesSummary").objects().mapNotNull { stage ->
                stage.num("minutes")?.let { stage.optString("type").lowercase(Locale.ROOT) to it / 60 }
            }.toMap() + exact
            totals.forEach { (name, duration) -> day.sleepStages.merge(name, duration, Double::plus) }
            (summary.num("minutesAsleep")?.div(60) ?: totals.filterKeys { it in setOf("asleep", "deep", "light", "rem") }
                .values.toList().total())?.let(asleepHours::add)
            (summary.num("minutesAwake")?.div(60) ?: totals["awake"])?.let(awakeHours::add)
            val outOfBed = row.optJSONArray("outOfBedSegments").objects().mapNotNull { it.bounds() }
                .map { maxOf(interval.first, it.first) to minOf(interval.second, it.second) }
            inBedHours += (HistoryMath.sleepHours(listOf(interval)) - HistoryMath.sleepHours(outOfBed)).coerceAtLeast(0.0)
            day.detail(SLEEP, listOf("${interval.first.atZone(zone).format(dayClock)} – ${interval.second.atZone(zone).format(dayClock)}"))
            for (key in listOf("stages", "shortAwakenings", "outOfBedSegments")) {
                val lines = row.optJSONArray(key).objects().mapNotNull { stage ->
                    val span = stage.bounds() ?: return@mapNotNull null
                    val start = stage.localTime("startTime", "startUtcOffset", zone)?.format(clock) ?: return@mapNotNull null
                    val name = when (key) {
                        "shortAwakenings" -> "Short awakening"
                        "outOfBedSegments" -> "Out of bed"
                        else -> stage.optString("type").title()
                    }
                    "$start · $name · ${formatted(HistoryMath.sleepHours(listOf(span)) * 60)} min"
                }
                day.detail(SLEEP_STAGES, lines)
            }
        }
        if (asleepHours.size == accepted.size) day.values[SLEEP] = asleepHours.sum()
        if (awakeHours.size == accepted.size) day.values[SLEEP_AWAKE] = awakeHours.sum()
        day.values[TIME_IN_BED] = inBedHours.sum()
        if (sleepPeriod.size == accepted.size) day.values[SLEEP_PERIOD] = sleepPeriod.sum()
        if (day.sleepStages.isNotEmpty()) day.values[SLEEP_STAGES] = day.sleepStages.values.sum()
    }

    private fun foodLog(log: JSONObject, zone: ZoneId): String {
        val serving = log.optJSONObject("serving")
        return listOfNotNull(localStart(log, zone)?.format(clock), log.string("mealType")?.title(),
            log.string("foodDisplayName") ?: "Food entry",
            serving.num("amount")?.let { "${formatted(it)} ${serving?.string("foodMeasurementUnitDisplayName") ?: "servings"}" },
            log.optJSONObject("energy").num("kcal")?.let { "${formatted(it)} kcal" }).joinToString(" · ")
    }

    private fun localStart(data: JSONObject, zone: ZoneId): LocalTime? = data.optJSONObject("interval")?.let { interval ->
        interval.optJSONObject("civilStartTime").civilClock() ?: interval.localTime("startTime", "startUtcOffset", zone)?.toLocalTime()
    }
    private val clock = DateTimeFormatter.ofPattern("HH:mm")
    private val dayClock = DateTimeFormatter.ofPattern("d MMM HH:mm")
    private fun formatted(value: Double) = java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(value)
    private fun String.title() = lowercase(Locale.ROOT).replace('_', ' ').replaceFirstChar(Char::titlecase)
}

internal class GoogleHealthDay {
    val values = mutableMapOf<Metric, Double>()
    val details = mutableMapOf<Metric, List<String>>()
    val sleepStages = mutableMapOf<String, Double>()
    val activities = mutableListOf<RecordedActivity>()
    var sleepStart: Instant? = null
    var sleepEnd: Instant? = null
    var sleepWakeMinute: Int? = null

    fun detail(metric: Metric, rows: List<String>) {
        if (rows.isNotEmpty()) details[metric] = details[metric].orEmpty() + rows
    }
}

internal fun JSONObject?.num(key: String): Double? = this?.optDouble(key)?.takeIf(Double::isFinite)
internal fun JSONObject?.string(key: String): String? = this?.takeUnless { it.isNull(key) }?.optString(key)?.takeIf(String::isNotBlank)
private fun JSONObject?.seconds(key: String): Double? =
    string(key)?.takeIf { it.endsWith('s') }?.dropLast(1)?.toDoubleOrNull()?.takeIf(Double::isFinite)
private fun JSONObject?.instant(key: String): Instant? = string(key)?.let { runCatching { Instant.parse(it) }.getOrNull() }
private fun JSONObject?.bounds(): Pair<Instant, Instant>? {
    val start = instant("startTime") ?: return null
    val end = instant("endTime")?.takeIf { it > start } ?: return null
    return start to end
}
private fun JSONObject?.date(): LocalDate? = this?.let {
    runCatching { LocalDate.of(it.getInt("year"), it.getInt("month"), it.getInt("day")) }.getOrNull()
}
private fun JSONObject?.civilDate() = this?.optJSONObject("date").date()
private fun JSONObject?.civilClock(): LocalTime? = this?.optJSONObject("time")?.let {
    runCatching { LocalTime.of(it.optInt("hours"), it.optInt("minutes"), it.optInt("seconds"), it.optInt("nanos")) }.getOrNull()
}
private fun JSONObject?.localTime(physical: String, offset: String, fallback: ZoneId): LocalDateTime? = instant(physical)?.let { time ->
    val zone = seconds(offset)?.toInt()?.let { runCatching { ZoneOffset.ofTotalSeconds(it) }.getOrNull() } ?: fallback
    time.atZone(zone).toLocalDateTime()
}
private fun List<Double>.mean(): Double? = takeIf { it.isNotEmpty() }?.average()
private fun List<Double>.total(): Double? = takeIf { it.isNotEmpty() }?.sum()
