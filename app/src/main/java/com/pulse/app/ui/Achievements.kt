package com.pulse.app.ui

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.pulse.app.core.*
import com.pulse.app.data.ShareExport
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AchievementsScreen(snapshot: HealthSnapshot, detail: (YearMonth) -> Unit) {
    val preferences = Preferences()
    val allAchievements = remember(snapshot.days) {
        AchievementEngine.evaluate(snapshot, preferences)
    }

    var selectedCategory by rememberSaveable { mutableStateOf(AchievementCategory.ALL.name) }
    var selectedAchievement by remember { mutableStateOf<UserAchievement?>(null) }
    var viewMonthlyMedals by rememberSaveable { mutableStateOf(false) }

    val unlockedCount = allAchievements.count { it.unlocked }
    val totalCount = allAchievements.size
    val recentUnlocked = allAchievements.filter { it.unlocked }.maxByOrNull { it.unlockedDate ?: LocalDate.MIN }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PulseTokens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle("Achievements", "Badges & Milestones")
        if (LocalDemo.current) Label("●  DEMO MODE · SAMPLE ACHIEVEMENTS", color = Red)

        // Progress Overview Header
        PulseCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label("OVERALL PROGRESS")
                    Text("$unlockedCount / $totalCount", fontSize = 28.sp, fontWeight = FontWeight.Normal)
                    Label("${(unlockedCount.toDouble() / totalCount * 100).toInt()}% completed")
                }
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    DotRing(unlockedCount.toDouble() / totalCount, Modifier.fillMaxSize())
                    Text("${(unlockedCount.toDouble() / totalCount * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            recentUnlocked?.let { recent ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PulseBadgeArt(recent, Modifier.size(36.dp), compact = true)
                    Column {
                        Label("RECENTLY UNLOCKED", color = Red)
                        Text(recent.definition.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // Category Filter Chips
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !viewMonthlyMedals && selectedCategory == AchievementCategory.ALL.name,
                onClick = { viewMonthlyMedals = false; selectedCategory = AchievementCategory.ALL.name },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            AchievementCategory.entries.filterNot { it == AchievementCategory.ALL }.forEach { cat ->
                val selected = !viewMonthlyMedals && selectedCategory == cat.name
                FilterChip(
                    selected = selected,
                    onClick = { viewMonthlyMedals = false; selectedCategory = cat.name },
                    label = { Text(cat.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            FilterChip(
                selected = viewMonthlyMedals,
                onClick = { viewMonthlyMedals = true },
                label = { Text("Monthly Steps") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }

        if (viewMonthlyMedals) {
            // Preserved Monthly Step Badges Collection
            MonthlyMedalsSection(snapshot, detail)
        } else {
            // Badges Grid
            val filtered = if (selectedCategory == AchievementCategory.ALL.name) {
                allAchievements
            } else {
                allAchievements.filter { it.definition.category.name == selectedCategory }
            }

            val rows = (filtered.size + 1) / 2
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(rows) { r ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val item1 = filtered.getOrNull(r * 2)
                        val item2 = filtered.getOrNull(r * 2 + 1)

                        if (item1 != null) {
                            Box(Modifier.weight(1f)) {
                                BadgeCard(item1) { selectedAchievement = item1 }
                            }
                        }
                        if (item2 != null) {
                            Box(Modifier.weight(1f)) {
                                BadgeCard(item2) { selectedAchievement = item2 }
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    // Detail Bottom Sheet
    selectedAchievement?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { selectedAchievement = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background
        ) {
            AchievementDetailSheet(item) { selectedAchievement = null }
        }
    }
}

@Composable private fun BadgeCard(
    item: UserAchievement,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PulseTokens.corner))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (item.unlocked) Red.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(PulseTokens.corner)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PulseBadgeArt(item, Modifier.size(72.dp))
            Text(
                item.definition.name,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                item.definition.category.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Progress or Unlocked indicator
            if (item.unlocked) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Red.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("UNLOCKED", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Red)
                }
            } else {
                Text(
                    item.progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Geometric, minimal Nothing OS-inspired achievement artwork.
 */
@Composable fun PulseBadgeArt(
    achievement: UserAchievement,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val unlocked = achievement.unlocked
    val iconType = achievement.definition.iconType
    val outlineColor = if (unlocked) Red else MaterialTheme.colorScheme.outline
    val fgColor = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)

    // Subtle breathing pulse for unlocked badges
    val transition = rememberInfiniteTransition(label = "badgePulse")
    val scaleAnim by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (unlocked && !compact) 1.03f else 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )

    Canvas(
        modifier
            .scale(scaleAnim)
            .semantics { contentDescription = "${achievement.definition.name} badge: ${if (unlocked) "Unlocked" else "Locked"}" }
    ) {
        val w = size.width
        val h = size.height
        val s = min(w, h)
        val strokeWidth = (s * 0.045f).coerceAtLeast(1.2f)

        // 1. Geometric Outer Frame (Octagonal / Chamfered box)
        val inset = strokeWidth / 2f
        val cut = s * 0.18f
        val framePath = Path().apply {
            moveTo(cut + inset, inset)
            lineTo(s - cut - inset, inset)
            lineTo(s - inset, cut + inset)
            lineTo(s - inset, s - cut - inset)
            lineTo(s - cut - inset, s - inset)
            lineTo(cut + inset, s - inset)
            lineTo(inset, s - cut - inset)
            lineTo(inset, cut + inset)
            close()
        }
        drawPath(
            framePath,
            color = outlineColor,
            style = Stroke(strokeWidth, pathEffect = if (!unlocked) PathEffect.dashPathEffect(floatArrayOf(4f, 4f)) else null)
        )

        // 2. Corner Dot Accents (Nothing OS signature)
        if (unlocked) {
            val dotR = s * 0.028f
            drawCircle(Red, dotR, Offset(s * 0.12f, s * 0.12f))
            drawCircle(Red, dotR, Offset(s * 0.88f, s * 0.12f))
            drawCircle(Red, dotR, Offset(s * 0.88f, s * 0.88f))
            drawCircle(Red, dotR, Offset(s * 0.12f, s * 0.88f))
        }

        // 3. Central Geometric Symbol
        val cx = s / 2f
        val cy = s / 2f
        val glyphStroke = Stroke((s * 0.05f).coerceAtLeast(1.5f), cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (iconType) {
            "footsteps" -> {
                // Dual stepping footprints in dot linework
                repeat(4) { i ->
                    drawCircle(fgColor, s * 0.035f, Offset(cx - s * 0.14f, cy - s * 0.18f + i * s * 0.11f))
                    drawCircle(fgColor, s * 0.035f, Offset(cx + s * 0.14f, cy - s * 0.12f + i * s * 0.11f))
                }
            }
            "5k", "10k" -> {
                // Minimalist numeric target
                drawCircle(fgColor, s * 0.26f, style = glyphStroke)
                drawCircle(if (unlocked) Red else fgColor, s * 0.07f, Offset(cx, cy))
            }
            "target" -> {
                drawCircle(fgColor, s * 0.25f, style = glyphStroke)
                drawCircle(fgColor, s * 0.12f, style = glyphStroke)
                drawLine(fgColor, Offset(cx - s * 0.32f, cy), Offset(cx + s * 0.32f, cy), strokeWidth = strokeWidth)
                drawLine(fgColor, Offset(cx, cy - s * 0.32f), Offset(cx, cy + s * 0.32f), strokeWidth = strokeWidth)
            }
            "streak" -> {
                // Ascending stepped ladder
                repeat(4) { i ->
                    val y = cy + s * 0.18f - i * s * 0.12f
                    drawLine(fgColor, Offset(cx - s * 0.18f + i * s * 0.09f, y), Offset(cx + s * 0.18f, y), strokeWidth = strokeWidth * 1.5f, cap = StrokeCap.Round)
                }
            }
            "moon", "moon_stars" -> {
                // Crescent moon
                val moonPath = Path().apply {
                    moveTo(cx, cy - s * 0.24f)
                    cubicTo(cx + s * 0.22f, cy - s * 0.24f, cx + s * 0.22f, cy + s * 0.24f, cx, cy + s * 0.24f)
                    cubicTo(cx + s * 0.08f, cy + s * 0.14f, cx + s * 0.08f, cy - s * 0.14f, cx, cy - s * 0.24f)
                    close()
                }
                drawPath(moonPath, fgColor, style = glyphStroke)
                if (iconType == "moon_stars") {
                    drawCircle(if (unlocked) Red else fgColor, s * 0.04f, Offset(cx - s * 0.18f, cy - s * 0.12f))
                }
            }
            "dumbbell" -> {
                // Barbell
                drawLine(fgColor, Offset(cx - s * 0.25f, cy), Offset(cx + s * 0.25f, cy), strokeWidth = strokeWidth * 1.5f)
                drawLine(fgColor, Offset(cx - s * 0.2f, cy - s * 0.18f), Offset(cx - s * 0.2f, cy + s * 0.18f), strokeWidth = strokeWidth * 2f, cap = StrokeCap.Round)
                drawLine(fgColor, Offset(cx + s * 0.2f, cy - s * 0.18f), Offset(cx + s * 0.2f, cy + s * 0.18f), strokeWidth = strokeWidth * 2f, cap = StrokeCap.Round)
            }
            "sunrise" -> {
                // Horizon line + rising sun
                drawLine(fgColor, Offset(cx - s * 0.28f, cy + s * 0.1f), Offset(cx + s * 0.28f, cy + s * 0.1f), strokeWidth = strokeWidth)
                drawArc(fgColor, 180f, 180f, false, topLeft = Offset(cx - s * 0.18f, cy - s * 0.08f), size = Size(s * 0.36f, s * 0.36f), style = glyphStroke)
                repeat(5) { i ->
                    val a = PI + (i + 1) * PI / 6
                    val r1 = s * 0.22f
                    val r2 = s * 0.28f
                    drawLine(fgColor, Offset(cx + cos(a).toFloat() * r1, cy + s * 0.1f + sin(a).toFloat() * r1), Offset(cx + cos(a).toFloat() * r2, cy + s * 0.1f + sin(a).toFloat() * r2), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                }
            }
            "sun" -> {
                drawCircle(fgColor, s * 0.15f, style = glyphStroke)
                repeat(8) { i ->
                    val a = i * PI / 4
                    drawLine(fgColor, Offset(cx + cos(a).toFloat() * s * 0.2f, cy + sin(a).toFloat() * s * 0.2f), Offset(cx + cos(a).toFloat() * s * 0.28f, cy + sin(a).toFloat() * s * 0.28f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                }
            }
            "evening" -> {
                // Horizon line + evening star
                drawLine(fgColor, Offset(cx - s * 0.26f, cy + s * 0.14f), Offset(cx + s * 0.26f, cy + s * 0.14f), strokeWidth = strokeWidth)
                // 4-point star
                drawLine(if (unlocked) Red else fgColor, Offset(cx, cy - s * 0.2f), Offset(cx, cy + s * 0.04f), strokeWidth = strokeWidth * 1.5f, cap = StrokeCap.Round)
                drawLine(if (unlocked) Red else fgColor, Offset(cx - s * 0.12f, cy - s * 0.08f), Offset(cx + s * 0.12f, cy - s * 0.08f), strokeWidth = strokeWidth * 1.5f, cap = StrokeCap.Round)
            }
            "clock" -> {
                drawCircle(fgColor, s * 0.25f, style = glyphStroke)
                drawLine(fgColor, Offset(cx, cy), Offset(cx, cy - s * 0.15f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                drawLine(if (unlocked) Red else fgColor, Offset(cx, cy), Offset(cx + s * 0.12f, cy), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            }
            "route" -> {
                // Connected path
                drawCircle(fgColor, s * 0.06f, Offset(cx - s * 0.18f, cy + s * 0.15f))
                drawCircle(if (unlocked) Red else fgColor, s * 0.06f, Offset(cx + s * 0.18f, cy - s * 0.15f))
                drawLine(fgColor, Offset(cx - s * 0.18f, cy + s * 0.15f), Offset(cx, cy), strokeWidth = strokeWidth)
                drawLine(fgColor, Offset(cx, cy), Offset(cx + s * 0.18f, cy - s * 0.15f), strokeWidth = strokeWidth)
            }
            "crown" -> {
                val crownPath = Path().apply {
                    moveTo(cx - s * 0.24f, cy + s * 0.14f)
                    lineTo(cx + s * 0.24f, cy + s * 0.14f)
                    lineTo(cx + s * 0.26f, cy - s * 0.14f)
                    lineTo(cx + s * 0.12f, cy - s * 0.02f)
                    lineTo(cx, cy - s * 0.18f)
                    lineTo(cx - s * 0.12f, cy - s * 0.02f)
                    lineTo(cx - s * 0.26f, cy - s * 0.14f)
                    close()
                }
                drawPath(crownPath, fgColor, style = glyphStroke)
            }
            "flame" -> {
                val flamePath = Path().apply {
                    moveTo(cx, cy - s * 0.25f)
                    cubicTo(cx + s * 0.22f, cy - s * 0.1f, cx + s * 0.25f, cy + s * 0.18f, cx, cy + s * 0.25f)
                    cubicTo(cx - s * 0.25f, cy + s * 0.18f, cx - s * 0.22f, cy - s * 0.1f, cx, cy - s * 0.25f)
                    close()
                }
                drawPath(flamePath, fgColor, style = glyphStroke)
                drawCircle(if (unlocked) Red else fgColor, s * 0.05f, Offset(cx, cy + s * 0.08f))
            }
            else -> {
                // Default Geometric Star / Spark
                drawLine(fgColor, Offset(cx, cy - s * 0.24f), Offset(cx, cy + s * 0.24f), strokeWidth = strokeWidth * 1.5f, cap = StrokeCap.Round)
                drawLine(fgColor, Offset(cx - s * 0.24f, cy), Offset(cx + s * 0.24f, cy), strokeWidth = strokeWidth * 1.5f, cap = StrokeCap.Round)
                drawCircle(if (unlocked) Red else fgColor, s * 0.06f, Offset(cx, cy))
            }
        }
    }
}

@Composable private fun AchievementDetailSheet(
    item: UserAchievement,
    dismiss: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PulseBadgeArt(item, Modifier.size(120.dp))

        Text(
            item.definition.name,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Box(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                item.definition.category.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (item.unlocked) Red else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            item.definition.description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        PulseCard(Modifier.fillMaxWidth()) {
            Label("REQUIREMENT")
            Text(item.definition.requirement, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Label("PROGRESS")
                Text(
                    item.progressText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (item.unlocked) Red else MaterialTheme.colorScheme.onSurface
                )
            }

            // Progress bar
            val progressFrac = (item.progress / item.definition.target).coerceIn(0.0, 1.0).toFloat()
            LinearProgressIndicator(
                progress = { progressFrac },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Red,
                trackColor = MaterialTheme.colorScheme.outline
            )

            if (item.unlocked) {
                item.unlockedDate?.let { date ->
                    Label("Unlocked on ${date.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))}")
                } ?: Label("Unlocked from recorded health data")
            } else {
                Label("In progress · unlocks automatically with recorded records")
            }
        }

        PulseButton("Close", Modifier.fillMaxWidth(), onClick = dismiss)
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Preserves the monthly step collection view so existing tests and monthly medals remain functional.
 */
@Composable private fun MonthlyMedalsSection(snapshot: HealthSnapshot, detail: (YearMonth) -> Unit) {
    var year by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    val medals = remember(snapshot.days, year) {
        (1..12).map { Achievements.calculate(YearMonth.of(year, it), snapshot.days, LocalDate.now()) }
    }
    val earned = medals.filter { it.tier != null && it.state == "FINAL" }
    val best = medals.filter { it.total != null }.maxByOrNull { it.total!! }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAction("back", "Previous year") { year-- }
            Text(year.toString(), fontSize = 22.sp, fontWeight = FontWeight.Light)
            IconAction("next", "Next year", year < LocalDate.now().year) { year++ }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { Label("Finalized"); Text("${earned.size} / 12", fontSize = 22.sp) }
            Column(Modifier.weight(1f)) { Label("Diamond"); Text(earned.count { it.tier == Tier.DIAMOND }.toString(), fontSize = 22.sp) }
            Column(Modifier.weight(1.3f)) {
                Label("Best month")
                Text(best?.month?.format(DateTimeFormatter.ofPattern("MMM yyyy")) ?: "—", style = MaterialTheme.typography.titleMedium)
                Label(number(best?.total?.toDouble()))
            }
        }

        repeat(4) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { col ->
                    val medal = medals[row * 3 + col]
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { detail(medal.month) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        MedalArt(medal, Modifier.fillMaxWidth().aspectRatio(1f))
                        Text(medal.month.month.name.take(3).lowercase().replaceFirstChar(Char::titlecase), style = MaterialTheme.typography.bodySmall)
                        Text(
                            when {
                                medal.state == "FUTURE" -> "Locked"
                                medal.state == "INCOMPLETE" -> "Incomplete"
                                medal.state == "PROVISIONAL" -> "In progress"
                                medal.tier == null -> "Unearned"
                                else -> medal.tier.name.lowercase().replaceFirstChar(Char::titlecase)
                            },
                            fontSize = 11.sp,
                            color = if (medal.state == "PROVISIONAL") Red else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Text("Bronze 90k · Silver 150k · Gold 225k · Diamond 300k", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable fun MedalArt(medal: Medal, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "${medal.month}, ${medal.tier?.name ?: "no medal"}, ${medal.state}, ${medal.total ?: "no recorded"} steps" }) {
        drawIntoCanvas { canvas ->
            val n = canvas.nativeCanvas
            val save = n.save()
            val side = size.minDimension
            n.translate((size.width - side) / 2, (size.height - side) / 2)
            n.scale(side / 400, side / 400)
            drawMedal(n, medal, side < 160.dp.toPx())
            n.restoreToCount(save)
        }
    }
}

fun drawMedal(c: android.graphics.Canvas, m: Medal, compact: Boolean = false) {
    val tone = when (m.tier) {
        Tier.BRONZE -> 0xFFC89C79.toInt()
        Tier.SILVER -> 0xFFC5C9CD.toInt()
        Tier.GOLD -> 0xFFD7C18A.toInt()
        Tier.DIAMOND -> 0xFFD4E5E9.toInt()
        else -> 0xFF8F9397.toInt()
    }
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = if (m.tier == null) 0xFF202224.toInt() else tone
    c.drawCircle(200f, 200f, 178f, p)
    p.style = Paint.Style.STROKE
    p.strokeWidth = 2.5f
    p.color = tone
    if (m.state == "INCOMPLETE") p.pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    c.drawCircle(200f, 200f, 178f, p)
    p.pathEffect = null
    val ink = if (m.tier == null) 0xFFD2D4D6.toInt() else 0xFF252624.toInt()
    p.color = ink
    p.strokeWidth = 3.5f
    p.strokeCap = Paint.Cap.ROUND
    when (m.season) {
        Season.WINTER -> repeat(6) { i -> val a = i * PI / 3; c.drawLine(200f, 110f, 200 + cos(a).toFloat() * 19, 110 + sin(a).toFloat() * 19, p) }
        Season.SUMMER -> {
            c.drawCircle(200f, 110f, 10f, p)
            repeat(8) { i -> val a = i * PI / 4; c.drawLine(200 + cos(a).toFloat() * 16, 110 + sin(a).toFloat() * 16, 200 + cos(a).toFloat() * 22, 110 + sin(a).toFloat() * 22, p) }
        }
        Season.SPRING -> {
            val path = android.graphics.Path().apply {
                moveTo(200f, 132f); lineTo(200f, 106f); cubicTo(176f, 108f, 178f, 90f, 198f, 100f)
                moveTo(201f, 116f); cubicTo(226f, 111f, 220f, 91f, 202f, 108f)
            }
            c.drawPath(path, p)
        }
        Season.AUTUMN -> {
            val path = android.graphics.Path().apply {
                moveTo(187f, 132f); lineTo(211f, 93f); quadTo(235f, 131f, 196f, 128f); lineTo(187f, 132f)
            }
            c.drawPath(path, p)
        }
    }
    p.style = Paint.Style.FILL
    p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    p.textAlign = Paint.Align.CENTER
    p.textSize = 78f
    c.drawText(m.engraving, 200f, 226f, p)
    if (!compact) {
        p.textSize = 23f
        c.drawText(m.tier?.name?.lowercase()?.replaceFirstChar(Char::titlecase) ?: if (m.state == "FUTURE") "Locked" else "No medal", 200f, 272f, p)
    }
    repeat(7) { i -> c.drawCircle(152f + i * 16f, 310f, 2.4f, p) }
    if (m.state == "PROVISIONAL") {
        p.color = 0xFFFF3535.toInt()
        c.drawCircle(325f, 74f, 9f, p)
    }
}

@Composable fun MedalScreen(month: YearMonth, snapshot: HealthSnapshot, back: () -> Unit) {
    val medal = remember(month, snapshot.days) { Achievements.calculate(month, snapshot.days, LocalDate.now()) }
    val context = LocalContext.current
    val demo = LocalDemo.current
    val days = (1..month.lengthOfMonth()).map { d -> snapshot.days.firstOrNull { it.date == month.atDay(d)}?.value(Metric.STEPS) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconAction("back", "Back", onClick = back)
            Label(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")).uppercase())
        }
        if (demo) Label("DEMO COLLECTION / SAMPLE DATA", color = Red)
        MedalArt(medal, Modifier.fillMaxWidth(.84f).aspectRatio(1f))
        Text(medal.tier?.name ?: "NO MEDAL", fontSize = 28.sp, fontWeight = FontWeight.Normal)
        Label("MONTHLY STEP BADGE · ${medal.state}", color = if (medal.state == "PROVISIONAL") Red else MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${number(medal.total?.toDouble())} TOTAL STEPS", style = MaterialTheme.typography.titleLarge)
        if (medal.next != null && medal.state == "PROVISIONAL") {
            val next = medal.next!!
            DotChart(days, Modifier.fillMaxWidth().height(70.dp), bars = true, description = "Recorded daily steps in $month")
            Label("${number((next.threshold - (medal.total ?: 0)).toDouble())} TO ${next.name}")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Label("DAILY AVERAGE")
                Text(number(medal.average), fontSize = 22.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Label("MOST ACTIVE DAY")
                Text(number(medal.best?.toDouble()), fontSize = 22.sp)
            }
        }
        DotChart(days, Modifier.fillMaxWidth().height(140.dp), bars = true, description = "${medal.days} recorded days. ${number(medal.total?.toDouble())} total steps in $month")
        Label("${medal.days} / ${month.lengthOfMonth()} DAYS RECORDED")
        if (medal.state == "INCOMPLETE") {
            Text("The source has not confirmed complete monthly coverage. This result may change when late records arrive.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PulseButton("Share medal", Modifier.fillMaxWidth(), enabled = medal.tier != null) {
            ShareExport.medal(context, medal, demo)
        }
        Spacer(Modifier.height(20.dp))
    }
}
