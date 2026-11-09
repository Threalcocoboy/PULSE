package com.pulse.app.core
import java.time.*
enum class AchievementFilter(val label:String){ALL("All"),UNLOCKED("Unlocked"),LOCKED("Locked"),IN_PROGRESS("In progress")}
enum class AchievementRepeat {ONE_TIME,MONTHLY,STEP_FAMILY}
val UserAchievement.instanceId get()="${definition.id}:${period ?: "lifetime"}"
val UserAchievement.repeat get()=if(definition.id.startsWith("monthly_steps_"))AchievementRepeat.STEP_FAMILY else if(period!=null)AchievementRepeat.MONTHLY else AchievementRepeat.ONE_TIME
object AchievementCollection {
 fun filter(items:List<UserAchievement>,status:AchievementFilter,category:AchievementCategory)=items.filter{(category==AchievementCategory.ALL||it.definition.category==category)&&when(status){AchievementFilter.ALL->true;AchievementFilter.UNLOCKED->it.unlocked;AchievementFilter.LOCKED->!it.unlocked;AchievementFilter.IN_PROGRESS->!it.unlocked&&it.progress>0}}
 fun monthly(s:HealthSnapshot,m:YearMonth,today:LocalDate)=Tier.entries.map{tier->val days=HistoryMath.unique(s.days).filter{YearMonth.from(it.date)==m&&it.date<=today};var total=0.0;var crossed:LocalDate?=null;days.forEach{d->val reading=d.readings[Metric.STEPS];total+=reading?.takeIf{it.state in setOf(DataState.READY,DataState.STALE)}?.value?.takeIf{it.isFinite()&&it>=0} ?: 0.0;if(total>=tier.threshold&&crossed==null)crossed=d.date};UserAchievement(AchievementDefinition("monthly_steps_${tier.name}",AchievementCategory.STEPS,"${tier.name.lowercase().replaceFirstChar(Char::titlecase)} Walker","Persistent monthly step badge family","Record ${tier.threshold} steps in $m",tier.threshold.toDouble(),"steps"),total,crossed!=null,crossed,"${total.toLong()} / ${tier.threshold} steps",m)}
 fun retain(a:UserAchievement,p:Double?,unlocked:Boolean,date:LocalDate?):UserAchievement {val progress=maxOf(a.progress,p ?: 0.0);return a.copy(progress=progress,unlocked=a.unlocked||unlocked,unlockedDate=listOfNotNull(a.unlockedDate,date).minOrNull(),progressText="${progress.toLong()} / ${a.definition.target.toLong()} ${a.definition.unit}")}
}
object MonthlyBadges {
 fun items(s:HealthSnapshot,h:List<UserAchievement>,m:YearMonth,t:LocalDate)=AchievementCollection.monthly(s,m,t).map{a->h.firstOrNull{it.instanceId==a.instanceId}?.let{AchievementCollection.retain(a,it.progress,it.unlocked,it.unlockedDate)} ?: a}
 fun main(items:List<UserAchievement>)=items.lastOrNull{it.unlocked} ?: items.first()
 fun history(a:UserAchievement,h:List<UserAchievement>)=(h+a).filter{it.definition.id==a.definition.id&&it.unlocked}.distinctBy{it.instanceId}
 fun familyCount(h:List<UserAchievement>)=h.filter{it.repeat==AchievementRepeat.STEP_FAMILY&&it.unlocked}.mapNotNull{it.period}.distinct().size
 fun status(i:List<UserAchievement>,m:YearMonth,t:LocalDate)=when{m>YearMonth.from(t)->"Not started";m==YearMonth.from(t)->"In progress";i.any{it.unlocked}->"Completed";else->"Missed"}
}
