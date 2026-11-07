package com.pulse.app.ui

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
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
@Composable fun AchievementsScreen(snapshot: HealthSnapshot, allAchievements: List<UserAchievement>, fullYear: (Int)->Unit, detail: (YearMonth) -> Unit) {
    var category by rememberSaveable { mutableStateOf(AchievementCategory.ALL.name) }
    var status by rememberSaveable { mutableStateOf(AchievementFilter.ALL.name) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    val month = YearMonth.parse(monthText)
    val today = LocalDate.now()
    val monthly = allAchievements.filter { it.period == month }.ifEmpty { AchievementCollection.monthly(snapshot,month,today) }
    val collection = allAchievements.filter { it.period == null } + monthly
    val selectedCategory = AchievementCategory.valueOf(category)
    val selectedStatus = AchievementFilter.valueOf(status)
    val filtered = remember(collection, category, status) { AchievementCollection.filter(collection, selectedStatus, selectedCategory) }
    val lifetime = allAchievements.filter { it.period == null }
    val earned = lifetime.count { it.unlocked }
    val selected = (allAchievements + monthly).firstOrNull { it.instanceId == selectedId }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PulseTokens.screenPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Achievements", "Your collection")
        if (LocalDemo.current) Label("Demo · sample achievements", color = Red)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f)) {
                Text("$earned / ${lifetime.size}", style = MaterialTheme.typography.headlineLarge)
                Text("Lifetime achievements unlocked", style = MaterialTheme.typography.bodySmall)
            }
            DotRing(if (lifetime.isEmpty()) 0.0 else earned.toDouble()/lifetime.size, Modifier.size(56.dp))
        }
        HorizontalDivider()
        MonthlySpotlight(snapshot,allAchievements,month,{monthText=it.toString()},{selectedId=it.instanceId},fullYear)
        HorizontalDivider()
        Text("Explore collection", style = MaterialTheme.typography.titleLarge)
        // Two rows stay readable at narrow widths and larger font sizes.
        Column {
            AchievementFilter.entries.chunked(2).forEach { options ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { filter ->
                        FilterChip(selected = selectedStatus == filter, onClick = { status=filter.name }, modifier=Modifier.weight(1f), label={Text(filter.label, style=MaterialTheme.typography.bodyMedium)})
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick={showCategories=true}, modifier=Modifier.weight(1f)) { Text("Category: ${selectedCategory.label} ▾") }
            if(category != AchievementCategory.ALL.name || status != AchievementFilter.ALL.name) TextButton(onClick={category=AchievementCategory.ALL.name;status=AchievementFilter.ALL.name}) { Text("Reset") }
        }
        Label("${filtered.size} achievements · monthly results for ${month.format(DateTimeFormatter.ofPattern("MMM yyyy"))}")
        if(filtered.isEmpty()) {
            Text("No achievements match these filters.")
            SmallAction("Show all achievements") { category=AchievementCategory.ALL.name;status=AchievementFilter.ALL.name }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns=if(maxWidth < 340.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f) 1 else 2
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                filtered.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        row.forEach { item -> Box(Modifier.weight(1f)) { BadgeCard(item) { selectedId=item.instanceId } } }
                        if(row.size < columns) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
    if(showCategories) ModalBottomSheet(onDismissRequest={showCategories=false}, sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Choose category", style=MaterialTheme.typography.titleLarge)
            AchievementCategory.entries.forEach { cat ->
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selectedCategory==cat, onClick={category=cat.name;showCategories=false}, role=Role.RadioButton), verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selectedCategory==cat,onClick=null)
                    Text("${cat.label} (${AchievementCollection.filter(collection,selectedStatus,cat).size})",Modifier.padding(start=12.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    selected?.let { item -> ModalBottomSheet(onDismissRequest={selectedId=null}, sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true), containerColor=MaterialTheme.colorScheme.background) {
        AchievementDetailSheet(item,allAchievements) { selectedId=null }
    } }
}

@Composable private fun BadgeCard(item: UserAchievement, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick=onClick).padding(12.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(8.dp)) {
        PulseBadgeArt(item,Modifier.size(80.dp))
        Text(item.definition.name,style=MaterialTheme.typography.titleMedium,textAlign=TextAlign.Center)
        Label(item.period?.format(DateTimeFormatter.ofPattern("MMM yyyy")) ?: item.definition.category.label)
        Text(if(item.unlocked) "Unlocked" else item.progressText,style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center,color=if(item.unlocked) Red else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun AchievementDetailSheet(
    item: UserAchievement,
    history: List<UserAchievement>,
    dismiss: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PulseBadgeArt(item, Modifier.size(120.dp))
        item.period?.let { Text(it.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style=MaterialTheme.typography.titleMedium) }

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
                Label("Best confirmed progress")
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
                } ?: Label("Unlocked from recorded health data · exact date unavailable")
            } else {
                Label(if(item.period != null && item.period < YearMonth.now()) "Not unlocked in this month" else "Progress from recorded data")
            }
            Text("Earned achievements stay unlocked when goals change or older records are no longer available. Progress reflects the best result confirmed on this device.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val earned=MonthlyBadges.history(item,history)
        Label("${if(item.repeat==AchievementRepeat.MONTHLY) "Repeatable monthly" else "One-time achievement"} · Unlocked ${earned.size} times")
        earned.forEach { award ->
            Text(listOfNotNull(award.period?.format(DateTimeFormatter.ofPattern("MMMM yyyy")),award.unlockedDate?.format(DateTimeFormatter.ofPattern("d MMM yyyy"))).joinToString(" · "),style=MaterialTheme.typography.bodySmall)
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
        Tier.BRONZE -> 0xFF999EA3.toInt()
        Tier.SILVER -> 0xFFC5C9CD.toInt()
        Tier.GOLD -> 0xFFE0E2E4.toInt()
        Tier.DIAMOND -> 0xFFF5F5F5.toInt()
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

@Composable fun MedalScreen(month: YearMonth, snapshot: HealthSnapshot, history: List<UserAchievement> = emptyList(), back: () -> Unit) {
    val items=MonthlyBadges.items(snapshot,history,month,LocalDate.now())
    val medal = remember(month, snapshot.days,items) {
        val raw=Achievements.calculate(month,snapshot.days,LocalDate.now())
        val total=maxOf(raw.total ?: 0L,items.maxOfOrNull {it.progress.toLong()} ?: 0L)
        raw.copy(total=total.takeIf {it>0 || raw.total!=null},tier=Achievements.tier(total))
    }
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
        val main=items.lastOrNull {it.unlocked} ?: items.first()
        SeasonalBadge(main, Modifier.fillMaxWidth(.84f).aspectRatio(1f))
        Label("Unlocked ${MonthlyBadges.history(main,history).size} times")
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
