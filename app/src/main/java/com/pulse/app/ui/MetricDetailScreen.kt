package com.pulse.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.pulse.app.core.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable fun MetricDetailScreen(
    initialMetric: Metric,
    initialDate: LocalDate,
    p: Preferences,
    snapshot: HealthSnapshot,
    back: () -> Unit
) {
    var currentDate by rememberSaveable { mutableStateOf(initialDate.toString()) }
    val date = LocalDate.parse(currentDate)
    val day = snapshot.days.firstOrNull { it.date == date }
    val today = LocalDate.now()

    // Canonical metric mapping (treat aliases as base metric)
    val metric = when (initialMetric) {
        Metric.GOAL, Metric.WEEKLY, Metric.TREND -> Metric.STEPS
        Metric.SLEEP_STAGES -> Metric.SLEEP
        else -> initialMetric
    }

    val value = day?.value(metric)
    val state = day?.readings?.get(metric)?.state ?: DataState.EMPTY

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PulseTokens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Navigation Bar
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAction("back", "Back", onClick = back)
                Spacer(Modifier.width(8.dp))
                Label(metric.label.uppercase())
            }

            if (date != today) {
                SmallAction("Today") { currentDate = today.toString() }
            }
        }

        // Date Bar with Previous / Next Controls
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAction("back", "Previous day") {
                currentDate = date.minusDays(1).toString()
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (date == today) "Today" else date.year.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconAction("next", "Next day", enabled = date < today) {
                currentDate = date.plusDays(1).coerceAtMost(today).toString()
            }
        }

        if (LocalDemo.current) {
            Label("●  DEMO DATA · NOT LIVE", color = Red)
        }

        // Render Metric-Specific Detail Content
        when (metric) {
            Metric.STEPS -> StepsDetailContent(date, value, day, p, snapshot)
            Metric.SLEEP -> SleepDetailContent(date, value, day, p, snapshot)
            Metric.HEART -> HeartRateDetailContent(date, value, day, p, snapshot)
            Metric.RESTING_HEART -> RestingHeartDetailContent(date, value, day, p, snapshot)
            Metric.HRV -> HrvDetailContent(date, value, day, p, snapshot)
            Metric.STRESS -> StressDetailContent(date, value, day, p, snapshot)
            Metric.BATTERY -> BodyBatteryDetailContent(date, value, day, p, snapshot)
            Metric.DISTANCE -> DistanceDetailContent(date, value, day, p, snapshot)
            Metric.ACTIVE -> ActiveMinutesDetailContent(date, value, day, p, snapshot)
            Metric.CALORIES -> CaloriesDetailContent(date, value, day, p, snapshot)
            Metric.ACTIVITIES -> ActivitiesDetailContent(date, value, day, p, snapshot)
            else -> StepsDetailContent(date, value, day, p, snapshot)
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 1. STEPS DETAIL SCREEN */
@Composable private fun StepsDetailContent(
    date: LocalDate,
    value: Double?,
    day: DayData?,
    p: Preferences,
    snapshot: HealthSnapshot
) {
    val stepGoal = p.stepGoal.toDouble()
    val percent = ((value ?: 0.0) / stepGoal * 100).toInt()
    val remaining = (stepGoal - (value ?: 0.0)).coerceAtLeast(0.0)

    // Primary Overview Card
    PulseCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("STEPS RECORDED")
                Text(valueText(value, Metric.STEPS), fontSize = 42.sp, fontWeight = FontWeight.Normal)
                Text(
                    if (value == null) "No step data for this date"
                    else if (value >= stepGoal) "Daily goal achieved!"
                    else "${number(remaining)} steps to reach goal",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (value != null && value >= stepGoal) Red else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                DotRing((value ?: 0.0) / stepGoal, Modifier.fillMaxSize())
                Text("$percent%", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    // Associated Movement Stats
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PulseCard(Modifier.weight(1f)) {
            Label("DISTANCE")
            val dist = day?.value(Metric.DISTANCE) ?: ((value ?: 0.0) * 0.00073)
            Text(valueText(dist, Metric.DISTANCE, p.imperial), fontSize = 22.sp)
            Label(if (p.imperial) "miles" else "km")
        }
        PulseCard(Modifier.weight(1f)) {
            Label("CALORIES")
            val cal = day?.value(Metric.CALORIES) ?: ((value ?: 0.0) * 0.04)
            Text(number(cal), fontSize = 22.sp)
            Label("kcal burned")
        }
    }

    // 7-Day Steps Trend
    val start7 = date.minusDays(6)
    val past7Days = remember(snapshot.days, date) {
        (0L..6L).map { ago ->
            val d = start7.plusDays(ago)
            d to snapshot.days.firstOrNull { it.date == d }?.value(Metric.STEPS)
        }
    }

    PulseCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Label("7-DAY STEP HISTORY")
            Label("Goal: ${number(stepGoal)}")
        }

        DotChart(
            past7Days.map { it.second },
            Modifier.fillMaxWidth().height(110.dp),
            bars = true,
            description = "Daily steps for the past 7 days"
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            past7Days.forEach { (d, _) ->
                Text(
                    d.format(DateTimeFormatter.ofPattern("E")).take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d == date) Red else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Goal Completion Summary
    val goalHits = past7Days.count { (it.second ?: 0.0) >= stepGoal }
    PulseCard(Modifier.fillMaxWidth()) {
        Label("GOAL CONSISTENCY")
        Text("$goalHits of 7 days reached goal", style = MaterialTheme.typography.titleMedium)
        Text(
            "Maintaining regular step volume supports metabolic health and cardiovascular endurance.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 2. SLEEP DETAIL SCREEN */
@Composable private fun SleepDetailContent(
    date: LocalDate,
    value: Double?,
    day: DayData?,
    p: Preferences,
    snapshot: HealthSnapshot
) {
    val sleepGoal = p.sleepGoal
    val percent = if (value != null) ((value / sleepGoal) * 100).toInt() else 0

    PulseCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("TOTAL SLEEP DURATION")
                Text(valueText(value, Metric.SLEEP), fontSize = 38.sp, fontWeight = FontWeight.Normal)
                Text(
                    if (value == null) "No sleep session recorded"
                    else "$percent% of your ${sleepGoal}h target",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                DotRing((value ?: 0.0) / sleepGoal, Modifier.fillMaxSize())
                Text(
                    if (value == null) "—" else "${(value * 10).roundToInt() / 10.0}h",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    // Sleep Stages Breakdown (Honest data presentation)
    if (day?.sleepStages?.isNotEmpty() == true) {
        PulseCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Label("SLEEP STAGES BREAKDOWN")
                Text("Total recorded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SleepStageDots(day.sleepStages, Modifier.fillMaxWidth().height(32.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                day.sleepStages.forEach { (stage, hours) ->
                    val stagePercent = if (value != null && value > 0) ((hours / value) * 100).toInt() else 0
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stage, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${valueText(hours, Metric.SLEEP)} ($stagePercent%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                "Sleep stages are duration totals from source records, not a reconstructed timeline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    } else {
        PulseCard(Modifier.fillMaxWidth()) {
            Label("SLEEP STAGES")
            Text("Stage breakdown unavailable", style = MaterialTheme.typography.titleMedium)
            Text(
                "Detailed REM, Deep, and Light sleep stages require a device that writes stage data to Health Connect or Garmin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 7-Night History
    val past7Sleep = remember(snapshot.days, date) {
        (0L..6L).map { ago ->
            val d = date.minusDays(6 - ago)
            d to snapshot.days.firstOrNull { it.date == d }?.value(Metric.SLEEP)
        }
    }

    PulseCard(Modifier.fillMaxWidth()) {
        Label("7-NIGHT SLEEP TREND")
        DotChart(
            past7Sleep.map { it.second },
            Modifier.fillMaxWidth().height(100.dp),
            bars = true,
            description = "Nightly sleep durations"
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            past7Sleep.forEach { (d, _) ->
                Text(
                    d.format(DateTimeFormatter.ofPattern("E")).take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d == date) Red else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 3. HEART RATE DETAIL SCREEN */
@Composable private fun HeartRateDetailContent(
    date: LocalDate,
    value: Double?,
    day: DayData?,
    p: Preferences,
    snapshot: HealthSnapshot
) {
    val resting = day?.value(Metric.RESTING_HEART)

    PulseCard(Modifier.fillMaxWidth()) {
        Label("HEART RATE")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    if (value == null) "—" else "${value.roundToInt()}",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Normal
                )
                Label("bpm · recorded average")
            }
            if (resting != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("${resting.roundToInt()} bpm", style = MaterialTheme.typography.titleMedium)
                    Label("Resting HR")
                }
            }
        }
    }

    // Heart rate across recent days
    val past7Heart = remember(snapshot.days, date) {
        (0L..6L).map { ago ->
            val d = date.minusDays(6 - ago)
            d to snapshot.days.firstOrNull { it.date == d }?.value(Metric.HEART)
        }
    }

    PulseCard(Modifier.fillMaxWidth()) {
        Label("RECENT HEART RATE PATTERN")
        DotChart(
            past7Heart.map { it.second },
            Modifier.fillMaxWidth().height(120.dp),
            description = "Recent heart rate values"
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            past7Heart.forEach { (d, _) ->
                Text(
                    d.format(DateTimeFormatter.ofPattern("E")).take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d == date) Red else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    PulseCard(Modifier.fillMaxWidth()) {
        Label("PHYSIOLOGICAL CONTEXT")
        Text("Cardiovascular demand", style = MaterialTheme.typography.titleMedium)
        Text(
            "Heart rate varies dynamically with physical exertion, hydration, stress, and recovery. Lower resting averages indicate cardiovascular adaptation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 4. RESTING HEART RATE DETAIL SCREEN */
@Composable private fun RestingHeartDetailContent(
    date: LocalDate,
    value: Double?,
    day: DayData?,
    p: Preferences,
    snapshot: HealthSnapshot
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val past14 = remember(snapshot.days, date) {
        (0L..13L).map { ago ->
            val d = date.minusDays(13 - ago)
            snapshot.days.firstOrNull { it.date == d }?.value(Metric.RESTING_HEART)
        }
    }
    val baseline = past14.filterNotNull().takeIf { it.isNotEmpty() }?.average()
    val diff = if (value != null && baseline != null) value - baseline else null

    PulseCard(Modifier.fillMaxWidth()) {
        Label("RESTING HEART RATE")
        Text(
            if (value == null) "—" else "${value.roundToInt()} bpm",
            fontSize = 42.sp,
            fontWeight = FontWeight.Normal
        )
        if (diff != null) {
            Text(
                "${if (diff >= 0) "+" else ""}${String.format(locale, "%.1f", diff)} bpm vs 14-day baseline",
                style = MaterialTheme.typography.bodySmall,
                color = if (abs(diff) > 4) Red else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    PulseCard(Modifier.fillMaxWidth()) {
        Label("14-DAY RESTING HR TREND")
        DotChart(
            past14,
            Modifier.fillMaxWidth().height(120.dp),
            description = "Resting heart rate trend"
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("14 days ago")
            baseline?.let { Label("Baseline: ${it.roundToInt()} bpm") }
            Label("Today")
        }
    }

    PulseCard(Modifier.fillMaxWidth()) {
        Label("RECOVERY OBSERVATION")
        Text("Baseline indicator", style = MaterialTheme.typography.titleMedium)
        Text(
            "Resting heart rate reflects basal autonomic tone. An elevated resting rate over several days can indicate accumulated fatigue, illness, or under-recovery.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 5. HRV (HEART RATE VARIABILITY) DETAIL SCREEN */
@Composable private fun HrvDetailContent(
    date: LocalDate,
    value: Double?,
    day: DayData?,
    p: Preferences,
    snapshot: HealthSnapshot
) {
    val past7 = remember(snapshot.days, date) {
        (0L..6L).map { ago ->
            val d = date.minusDays(6 - ago)
            d to snapshot.days.firstOrNull { it.date == d }?.value(Metric.HRV)
        }
    }

    PulseCard(Modifier.fillMaxWidth()) {
        Label("HEART RATE VARIABILITY (HRV)")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    if (value == null) "—" else "${value.roundToInt()}",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Normal
                )
                Label("ms (rMSSD) · overnight average")
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Autonomic balance", fontSize = 12.sp, color = Red)
            }
        }
    }

    PulseCard(Modifier.fillMaxWidth()) {
        Label("7-DAY HRV TRAJECTORY")
        DotChart(
            past7.map { it.second },
            Modifier.fillMaxWidth().height(110.dp),
            description = "Overnight HRV readings"
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            past7.forEach { (d, _) ->
                Text(
                    d.format(DateTimeFormatter.ofPattern("E")).take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d == date) Red else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    PulseCard(Modifier.fillMaxWidth()) {
        Label("UNDERSTANDING HRV")
        Text("Personal trend vs absolute numbers", style = MaterialTheme.typography.titleMedium)
        Text(
            "HRV measures natural variation between heartbeats governed by the sympathetic and parasympathetic nervous systems. Individual values vary widely—compare your reading to your own personal trend rather than other people.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 6. STRESS DETAIL SCREEN */
@Composable private fun StressDetailContent(
    date: LocalDate,
    value: Double?,
    day: DayData?,
    p: Preferences,
    snapshot: HealthSnapshot
) {
    val level = value?.toInt()
    val category = when (level) {
        null -> "No data"
        in 0..25 -> "Rest · Low demand"
        in 26..50 -> "Low stress"
        in 51..75 -> "Medium stress"
        else -> "High stress"
    }

    PulseCard(Modifier.fillMaxWidth()) {
        Label("DAILY STRESS LEVEL")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    if (value == null) "—" else "${level}",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Normal
                )
                Label("Scale 0–100 · daily average")
            }
            Text(category, style = MaterialTheme.typography.bodyMedium, color = if ((level ?: 0) > 50) Red else MaterialTheme.colorScheme.onSurface)
        }
    }

    val past7Stress = remember(snapshot.days, date) {
        (0L..6L).map { ago ->
            val d = date.minusDays(6 - ago)
            d to snapshot.days.firstOrNull { it.date == d }?.value(Metric.STRESS)
        }
    }

    PulseCard(Modifier.fillMaxWidth()) {
        Label("RECENT STRESS SUMMARY")
        DotChart(
            past7Stress.map { it.second },
            Modifier.fillMaxWidth().height(100.dp),
            bars = true,
            description = "Daily stress levels"
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            past7Stress.forEach { (d, _) ->
                Text(
                    d.format(DateTimeFormatter.ofPattern("E")).take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d == date) Red else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "Daily average stress score from source. Continuous timeline is not recorded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/** 7. BODY BATTERY DETAIL SCREEN */
@Composable private fun BodyBatteryDetailContent(
    date: LocalDate,
    value: Double?,
    day: DayData?,
    p: Preferences,
    snapshot: HealthSnapshot
) {
    if (snapshot.mode != DataMode.GARMIN && snapshot.mode != DataMode.DEMO) {
        PulseCard(Modifier.fillMaxWidth()) {
            Label("GARMIN EXCLUSIVE METRIC")
            Text("Body Battery unavailable in Health Connect", style = MaterialTheme.typography.titleMedium)
            Text(
                "Body Battery is a proprietary Garmin energy score based on HRV, stress, and sleep recovery. To view Body Battery data, switch data mode to Garmin in Settings and configure your access key.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val score = value?.toInt()
    PulseCard(Modifier.fillMaxWidth()) {
        Label("BODY BATTERY (GARMIN)")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    if (value == null) "—" else "$score",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Normal
                )
                Label("Score 0–100 · energy reserve")
            }
            Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                DotRing((value ?: 0.0) / 100.0, Modifier.fillMaxSize())
                Text(if (score == null) "—" else "$score", fontSize = 14.sp)
            }
        }
    }

    val past7Battery = remember(snapshot.days, date) {
        (0L..6L).map { ago ->
            val d = date.minusDays(6 - ago)
            d to snapshot.days.firstOrNull { it.date == d }?.value(Metric.BATTERY)
        }
    }

    PulseCard(Modifier.fillMaxWidth()) {
        Label("7-DAY BATTERY TREND")
        DotChart(
            past7Battery.map { it.second },
            Modifier.fillMaxWidth().height(110.dp),
            description = "Body battery trend"
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            past7Battery.forEach { (d, _) ->
                Text(
                    d.format(DateTimeFormatter.ofPattern("E")).take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d == date) Red else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 8. DISTANCE DETAIL SCREEN */
@Composable private fun DistanceDetailContent(
    date: LocalDate,
    value: Double?,
    day: DayData?,
    p: Preferences,
    snapshot: HealthSnapshot
) {
    PulseCard(Modifier.fillMaxWidth()) {
        Label("DISTANCE COVERED")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    valueText(value, Metric.DISTANCE, p.imperial),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Normal
                )
                Label(if (p.imperial) "miles traveled" else "km traveled")
            }
            day?.value(Metric.STEPS)?.let { steps ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(number(steps), style = MaterialTheme.typography.titleMedium)
                    Label("associated steps")
                }
            }
        }
    }

    val past7Dist = remember(snapshot.days, date) {
        (0L..6L).map { ago ->
            val d = date.minusDays(6 - ago)
            d to snapshot.days.firstOrNull { it.date == d }?.value(Metric.DISTANCE)
        }
    }

    PulseCard(Modifier.fillMaxWidth()) {
        Label("7-DAY DISTANCE TREND")
        DotChart(
            past7Dist.map { it.second },
            Modifier.fillMaxWidth().height(100.dp),
            bars = true,
            description = "Daily distance"
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            past7Dist.forEach { (d, _) ->
                Text(
                    d.format(DateTimeFormatter.ofPattern("E")).take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d == date) Red else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 9. ACTIVE MINUTES DETAIL SCREEN */
@Composable private fun ActiveMinutesDetailContent(
    date: LocalDate,
    value: Double?,
    day: DayData?,
    p: Preferences,
    snapshot: HealthSnapshot
) {
    val dailyTarget = 30.0
    PulseCard(Modifier.fillMaxWidth()) {
        Label("ACTIVE MINUTES")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    if (value == null) "—" else "${value.roundToInt()}",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Normal
                )
                Label("minutes · moderate & vigorous activity")
            }
            Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                DotRing((value ?: 0.0) / dailyTarget, Modifier.fillMaxSize())
                Text(
                    if (value == null) "—" else "${((value / dailyTarget) * 100).toInt()}%",
                    fontSize = 12.sp
                )
            }
        }
    }

    val past7Active = remember(snapshot.days, date) {
        (0L..6L).map { ago ->
            val d = date.minusDays(6 - ago)
            d to snapshot.days.firstOrNull { it.date == d }?.value(Metric.ACTIVE)
        }
    }

    PulseCard(Modifier.fillMaxWidth()) {
        Label("7-DAY ACTIVITY CONSISTENCY")
        DotChart(
            past7Active.map { it.second },
            Modifier.fillMaxWidth().height(100.dp),
            bars = true,
            description = "Daily active minutes"
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            past7Active.forEach { (d, _) ->
                Text(
                    d.format(DateTimeFormatter.ofPattern("E")).take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d == date) Red else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "Target: 30 minutes daily or 150 minutes weekly recommended by health guidelines.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 10. CALORIES DETAIL SCREEN */
@Composable private fun CaloriesDetailContent(
    date: LocalDate,
    value: Double?,
    day: DayData?,
    p: Preferences,
    snapshot: HealthSnapshot
) {
    PulseCard(Modifier.fillMaxWidth()) {
        Label("CALORIES BURNED")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    if (value == null) "—" else number(value),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Normal
                )
                Label("kcal · total daily expenditure")
            }
        }
    }

    val past7Cal = remember(snapshot.days, date) {
        (0L..6L).map { ago ->
            val d = date.minusDays(6 - ago)
            d to snapshot.days.firstOrNull { it.date == d }?.value(Metric.CALORIES)
        }
    }

    PulseCard(Modifier.fillMaxWidth()) {
        Label("7-DAY CALORIC TREND")
        DotChart(
            past7Cal.map { it.second },
            Modifier.fillMaxWidth().height(100.dp),
            bars = true,
            description = "Daily calories"
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            past7Cal.forEach { (d, _) ->
                Text(
                    d.format(DateTimeFormatter.ofPattern("E")).take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d == date) Red else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 11. RECORDED ACTIVITIES SCREEN */
@Composable private fun ActivitiesDetailContent(
    date: LocalDate,
    value: Double?,
    day: DayData?,
    p: Preferences,
    snapshot: HealthSnapshot
) {
    PulseCard(Modifier.fillMaxWidth()) {
        Label("RECORDED ACTIVITIES")
        Text("${day?.activities?.size ?: 0} sessions", fontSize = 32.sp)
    }

    if (day?.activities?.isNotEmpty() == true) {
        day.activities.forEach { act ->
            PulseCard(Modifier.fillMaxWidth()) {
                Text(act.name, style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Label(act.type.replace('_', ' '))
                    Text("${number(act.minutes)} min", style = MaterialTheme.typography.bodyMedium)
                }
                act.distanceKm?.let { km ->
                    Label("${valueText(km, Metric.DISTANCE, p.imperial)} ${if (p.imperial) "mi" else "km"}")
                }
            }
        }
    } else {
        PulseCard(Modifier.fillMaxWidth()) {
            Text("No recorded workouts for this day", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Workouts tracked via Garmin or Health Connect apps will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
