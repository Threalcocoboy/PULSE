package com.pulse.app.core
data class BadgeIdentity(val symbol:String,val mark:String="")
fun AchievementDefinition.badgeIdentity():BadgeIdentity=when(id){
 "first_steps"->BadgeIdentity("foot");"steps_5k"->BadgeIdentity("foot","5K");"steps_10k"->BadgeIdentity("foot","10K");"goal_crusher"->BadgeIdentity("target","1");"step_consistency"->BadgeIdentity("calendar","5");"walking_streak"->BadgeIdentity("foot","3")
 "sleep_explorer"->BadgeIdentity("moon");"well_rested"->BadgeIdentity("moon","8h");"dream_team"->BadgeIdentity("stars","7");"consistent_sleeper"->BadgeIdentity("moon","3");"sleep_routine"->BadgeIdentity("clock","4")
 "first_workout"->BadgeIdentity("runner","1");"getting_active"->BadgeIdentity("runner","5");"dedicated"->BadgeIdentity("runner","25");"half_hour_hero"->BadgeIdentity("timer","30");"endurance"->BadgeIdentity("timer","60");"variety"->BadgeIdentity("variety","3")
 "early_bird"->BadgeIdentity("sunrise","1");"sunrise_session"->BadgeIdentity("sunrise","3");"lunchtime_move"->BadgeIdentity("sun","12");"after_hours"->BadgeIdentity("night","18");"weekend_warrior"->BadgeIdentity("calendar","WE")
 "first_km"->BadgeIdentity("route","1");"five_km_day"->BadgeIdentity("route","5");"distance_explorer"->BadgeIdentity("route","50");"long_haul"->BadgeIdentity("route","100")
 "on_the_move"->BadgeIdentity("timer","A");"active_week"->BadgeIdentity("calendar","150");"moving_regularly"->BadgeIdentity("runner","D5")
 "first_checkin"->BadgeIdentity("pulse","1");"data_explorer"->BadgeIdentity("metrics","3");"one_week_in"->BadgeIdentity("calendar","7");"consistent_tracker"->BadgeIdentity("calendar","14");"triple_threat"->BadgeIdentity("variety","3×");"habit_builder"->BadgeIdentity("calendar","H");"the_comeback"->BadgeIdentity("return")
 else->{require(id.startsWith("monthly_steps_"));BadgeIdentity("monthly",id.removePrefix("monthly_steps_"))}
}
