package com.pulse.app.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import com.pulse.app.core.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
@Composable fun MonthlySpotlight(s:HealthSnapshot,h:List<UserAchievement>,month:YearMonth,onMonth:(YearMonth)->Unit,select:(UserAchievement)->Unit,year:(Int)->Unit){
 val base=YearMonth.of(2000,1);val pager=rememberPagerState(initialPage=java.time.temporal.ChronoUnit.MONTHS.between(base,month).toInt(),pageCount={2400});val scope=rememberCoroutineScope()
 LaunchedEffect(pager.settledPage){onMonth(base.plusMonths(pager.settledPage.toLong()))}
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
 Row(verticalAlignment=Alignment.CenterVertically){IconAction("back","Previous month"){scope.launch{pager.animateScrollToPage((pager.currentPage-1).coerceAtLeast(0))}};Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),Modifier.weight(1f));IconAction("next","Next month"){scope.launch{pager.animateScrollToPage((pager.currentPage+1).coerceAtMost(2399))}}}
 HorizontalPager(pager){page->val m=base.plusMonths(page.toLong());val items=MonthlyBadges.items(s,h,m,LocalDate.now());val main=MonthlyBadges.main(items);val next=items.firstOrNull{!it.unlocked};Column(Modifier.fillMaxWidth().clickable{select(main)},horizontalAlignment=Alignment.CenterHorizontally){SeasonalBadge(if(next!=null)main.copy(progress=next.progress,definition=main.definition.copy(target=next.definition.target))else main,Modifier.size(180.dp));Text(if(main.unlocked)"${main.definition.name.removeSuffix(" Walker").uppercase()} CLASS" else "NO CLASS YET",style=MaterialTheme.typography.headlineSmall,color=Red);Text("Monthly Step Badge",style=MaterialTheme.typography.titleLarge);Text("${main.progress.toLong()} steps");Text(next?.let{"Next: ${it.definition.name} · ${it.definition.target.toLong()} steps"} ?: "Highest class achieved");Label(MonthlyBadges.status(items,m,LocalDate.now()))}}
 Label("Monthly Step Badge · Unlocked ${MonthlyBadges.familyCount(h)} times")
 PulseButton("See full year",Modifier.fillMaxWidth()){year(month.year)}
 }
}
@Composable fun MonthlyYearScreen(initialYear:Int,s:HealthSnapshot,h:List<UserAchievement>,back:()->Unit,detail:(YearMonth)->Unit){var year by remember{mutableIntStateOf(initialYear)};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)){IconAction("back","Back",onClick=back);Row{IconAction("back","Previous year"){year--};Text("$year",Modifier.weight(1f));IconAction("next","Next year"){year++}};(1..12).toList().chunked(3).forEach{row->Row{row.forEach{n->val m=YearMonth.of(year,n);val items=MonthlyBadges.items(s,h,m,LocalDate.now());Column(Modifier.weight(1f).clickable{detail(m)},horizontalAlignment=Alignment.CenterHorizontally){SeasonalBadge(MonthlyBadges.main(items),Modifier.fillMaxWidth().aspectRatio(1f));Text(m.month.name.take(3));val badge=MonthlyBadges.main(items);if(badge.unlocked)Text(badge.definition.name.removeSuffix(" Walker"),style=MaterialTheme.typography.labelSmall,color=Red);Text(MonthlyBadges.status(items,m,LocalDate.now()),style=MaterialTheme.typography.labelSmall)}}}}}}
