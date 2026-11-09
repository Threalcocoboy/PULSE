package com.pulse.app.core
import java.time.*
object RefreshPolicy {
 fun allow(lastAttempt:Instant?,running:Boolean,now:Instant):Boolean {if(running)return false;if(lastAttempt==null)return true;val age=Duration.between(lastAttempt,now).seconds;return age<0||age>=60}
 fun needsHistory(rows:List<Pair<LocalDate,Instant>>,today:LocalDate,now:Instant):Boolean {val oldest=rows.filter{it.first>=today.minusDays(365)}.minByOrNull{it.first};return oldest==null||oldest.first>today.minusDays(365)||oldest.second<now.minus(Duration.ofDays(7))}
}
