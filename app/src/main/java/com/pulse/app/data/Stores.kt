package com.pulse.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pulse.app.core.*
import com.pulse.app.core.Preferences
import kotlinx.coroutines.flow.*
import java.io.IOException

internal val Context.pulseStore by preferencesDataStore(name = "pulse_preferences")
class PreferenceStore internal constructor(private val store: DataStore<androidx.datastore.preferences.core.Preferences>) {
    constructor(context: Context) : this(context.pulseStore)
    private val settingsKey = stringPreferencesKey("settings_v1")
    private val layoutKey = stringPreferencesKey("layout_v1")
    private val modesKey = stringPreferencesKey("dashboard_modes_v1")
    val preferences = store.data.catch { if(it is IOException) emit(emptyPreferences()) else throw it }.map { PreferencesCodec.decode(it[settingsKey] ?: "") }
    suspend fun update(transform: (Preferences) -> Preferences) { store.edit { it[settingsKey] = PreferencesCodec.encode(transform(PreferencesCodec.decode(it[settingsKey] ?: "")).sanitized()) } }
    suspend fun dashboardModes(): PulseModes {
        val saved = store.edit { values ->
            if (values[modesKey] == null) {
                val legacy = values[layoutKey]?.let(LayoutCodec::decode)
                val oldSettings = PreferencesCodec.decode(values[settingsKey].orEmpty())
                val initial = PulseModes.initial(legacy ?: AdaptiveFlow.defaults().takeIf { oldSettings.onboarded }, observations = oldSettings.insights)
                values[modesKey] = PulseModesCodec.encode(initial)
            }
        }
        return checkNotNull(saved[modesKey]?.let(PulseModesCodec::decode))
    }
    suspend fun saveModes(value: PulseModes) = store.edit { it[modesKey] = PulseModesCodec.encode(value) }
    suspend fun clear() { store.edit { it.clear() } }
}
object PreferencesCodec {
    fun encode(p: Preferences) = p.sanitized().let {
        listOf("v5", it.onboarded, it.mode.name, it.stepGoal, it.sleepGoal, it.theme, it.animations, it.haptics, it.insights,
            it.imperial, it.distanceGoal, it.activeGoal, it.workoutGoal, it.enabledGoals.joinToString(",") { goal -> goal.name },
            java.util.Base64.getUrlEncoder().encodeToString(it.displayName.toByteArray(Charsets.UTF_8)), it.introSeen,
            it.onboardingStep, it.progressNotifications, it.notificationMetrics.joinToString(",") { metric -> metric.name },
            it.setupSource.name, it.sourceSetup.name, it.goalsSetup.name, it.notificationSetup.name, it.combineHealthConnect,
            it.providerPriority.entries.sortedBy { entry -> entry.key.name }
                .joinToString(",") { entry -> "${entry.key.name}=${entry.value.name}" }, it.weeklyActiveGoal,
            PersonalGoalCodec.encode(it.personalGoals), it.liveGoal.goalId.orEmpty(), it.liveGoal.enabled,
            it.liveGoal.dismissed, it.liveGoal.hideOnLockScreen, it.liveGoal.token,
            it.liveGoal.permissionRequested).joinToString("|")
    }

    fun decode(raw: String): Preferences = runCatching {
        val fields = raw.split('|')
        val legacy = fields[0] == "v1"
        require((legacy && fields.size in setOf(10, 14, 19)) || (fields[0] == "v2" && fields.size == 23) ||
            (fields[0] == "v3" && fields.size in 25..26) || (fields[0] == "v4" && fields.size == 27) ||
            (fields[0] == "v5" && fields.size == 33))
        val onboarded = fields[1].toBooleanStrict()
        val mode = DataMode.fromStored(fields[2])
        val oldStep = fields.getOrNull(16)?.toInt() ?: 0
        val step = if (!legacy) oldStep else when {
            oldStep <= 1 -> SetupStep.WELCOME.ordinal
            mode == DataMode.NONE -> SetupStep.SOURCE.ordinal
            else -> SetupStep.CONNECT.ordinal
        }
        fun metrics(index: Int, defaults: Set<Metric>) = fields.getOrNull(index)?.split(',')
            ?.mapNotNull { name -> Metric.entries.firstOrNull { it.name == name } }?.toSet() ?: defaults
        fun status(index: Int) = fields.getOrNull(index)?.let(SetupStatus::valueOf) ?: SetupStatus.NOT_STARTED
        Preferences(
            onboarded = onboarded, mode = mode, stepGoal = fields[3].toInt(), sleepGoal = fields[4].toDouble(),
            theme = fields[5], animations = fields[6].toBooleanStrict(), haptics = fields[7].toBooleanStrict(),
            insights = fields[8].toBooleanStrict(), imperial = fields[9].toBooleanStrict(),
            distanceGoal = fields.getOrNull(10)?.toDouble() ?: 5.0, activeGoal = fields.getOrNull(11)?.toInt() ?: 30,
            workoutGoal = fields.getOrNull(12)?.toInt() ?: 3, enabledGoals = metrics(13, setOf(Metric.STEPS, Metric.SLEEP)),
            displayName = fields.getOrNull(14)?.let { String(java.util.Base64.getUrlDecoder().decode(it), Charsets.UTF_8) }.orEmpty(),
            introSeen = fields.getOrNull(15)?.toBooleanStrict() ?: false, onboardingStep = step,
            progressNotifications = (fields.getOrNull(17)?.toBooleanStrict() ?: false) && mode != DataMode.NONE,
            notificationMetrics = metrics(18, setOf(Metric.STEPS, Metric.DISTANCE)),
            setupSource = fields.getOrNull(19)?.let(DataMode::fromStored) ?: mode,
            sourceSetup = status(20), goalsSetup = status(21), notificationSetup = status(22),
            combineHealthConnect = fields.getOrNull(23)?.toBooleanStrictOrNull() ?: false,
            providerPriority = fields.getOrNull(24).orEmpty().split(',').mapNotNull { entry ->
                val metric = Metric.entries.firstOrNull { it.name == entry.substringBefore('=') }
                val provider = DataMode.fromStored(entry.substringAfter('=', ""))
                metric?.takeIf { provider in setOf(DataMode.HEALTH_CONNECT, DataMode.GOOGLE_HEALTH) }?.let { it to provider }
            }.toMap(),
            weeklyActiveGoal = fields.getOrNull(25)?.toIntOrNull() ?: 150,
            personalGoals = PersonalGoalCodec.decode(fields.getOrNull(26).orEmpty()),
            liveGoal = LiveGoalPreference(
                goalId = fields.getOrNull(27)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,80}")) },
                enabled = fields.getOrNull(28)?.toBooleanStrictOrNull() == true,
                dismissed = fields.getOrNull(29)?.toBooleanStrictOrNull() == true,
                hideOnLockScreen = fields.getOrNull(30)?.toBooleanStrictOrNull() == true,
                token = fields.getOrNull(31).orEmpty(),
                permissionRequested = fields.getOrNull(32)?.toBooleanStrictOrNull() == true
            )
        ).sanitized()
    }.getOrDefault(Preferences())
}
@Entity(tableName="daily", primaryKeys=["date","zone"])
data class DailyEntity(val date: String, val zone: String, val steps: Double?, val sleep: Double?, val heart: Double?, val distance: Double?, val synced: Long)
@Entity(tableName="medals", primaryKeys=["month","zone"])
data class MedalEntity(val month: String, val zone: String, val steps: Long?, val tier: String?, val state: String, val recalculated: Long)
@Entity(tableName="garmin_daily",primaryKeys=["date","zone"])
data class GarminDayEntity(val date: String,val zone: String,val payload: String,val synced: Long)
@Entity(tableName="achievement_progress", primaryKeys=["scope","instance"])
data class AchievementProgressEntity(val scope: String, val instance: String, val definition: String, val period: String?, val progress: Double, val unlocked: Boolean, val unlockedDate: String?)
@Entity(tableName="unlock_events",primaryKeys=["scope","instance"])
data class UnlockEvent(val scope:String,val instance:String,val acknowledged:Boolean=false)
@Entity(tableName="heart_samples",primaryKeys=["zone","time","source"])
data class HeartSampleEntity(val zone:String,val time:Long,val source:String,val bpm:Double)
@Entity(tableName="health_extra",primaryKeys=["zone","date"])
data class HealthExtraEntity(val zone:String,val date:String,val payload:String,val synced:Long)
@Dao
interface HealthDao {
    @Query("SELECT * FROM unlock_events WHERE scope=:scope AND acknowledged=0") suspend fun pending(scope:String):List<UnlockEvent>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun addUnlocks(events:List<UnlockEvent>)
    @Query("UPDATE unlock_events SET acknowledged=1 WHERE scope=:scope AND instance IN (:ids)") suspend fun acknowledge(scope:String,ids:List<String>)
    @Transaction suspend fun recordAchievements(scope:String,rows:List<AchievementProgressEntity>) {
        val previous=achievements(scope).associateBy{it.instance}
        addUnlocks(rows.filter{it.unlocked && previous[it.instance]?.unlocked!=true}.map{UnlockEvent(scope,it.instance)})
        saveAchievements(rows)
    }
    @Upsert suspend fun saveSamples(rows:List<HeartSampleEntity>)
    @Query("DELETE FROM heart_samples WHERE zone=:zone AND time>=:start AND time<:end") suspend fun removeSampleWindow(zone:String,start:Long,end:Long)
    @Query("SELECT * FROM heart_samples WHERE zone=:zone ORDER BY time") suspend fun samples(zone:String):List<HeartSampleEntity>
    @Transaction suspend fun replaceSamples(zone:String,start:Long,end:Long,rows:List<HeartSampleEntity>){removeSampleWindow(zone,start,end);saveSamples(rows)}
    @Upsert suspend fun saveExtras(rows:List<HealthExtraEntity>)
    @Query("SELECT * FROM health_extra WHERE zone=:zone ORDER BY date") suspend fun extras(zone:String):List<HealthExtraEntity>
    @Query("DELETE FROM unlock_events") suspend fun clearUnlocks()
    @Query("DELETE FROM heart_samples") suspend fun clearSamples()
    @Query("DELETE FROM health_extra") suspend fun clearExtras()

    @Query("SELECT * FROM achievement_progress WHERE scope = :scope") suspend fun achievements(scope: String): List<AchievementProgressEntity>
    @Upsert suspend fun saveAchievements(rows: List<AchievementProgressEntity>)
    @Query("DELETE FROM achievement_progress") suspend fun deleteAchievements()
    @Query("DELETE FROM achievement_progress WHERE scope = 'GARMIN_ACCOUNT_LOCAL'") suspend fun deleteGarminAchievementRows()
    @Query("DELETE FROM unlock_events WHERE scope = 'GARMIN_ACCOUNT_LOCAL'") suspend fun deleteGarminUnlocks()
    @Transaction suspend fun deleteGarminAchievements(){deleteGarminAchievementRows();deleteGarminUnlocks()}
    @Query("SELECT * FROM daily WHERE zone = :zone ORDER BY date") suspend fun days(zone: String): List<DailyEntity>
    @Upsert suspend fun saveDays(rows: List<DailyEntity>)
    @Upsert suspend fun saveMedals(rows: List<MedalEntity>)
    @Query("SELECT * FROM garmin_daily WHERE zone = :scope ORDER BY date") suspend fun garminDays(scope: String = "GARMIN_ACCOUNT_LOCAL"): List<GarminDayEntity>
    @Upsert suspend fun saveGarmin(rows: List<GarminDayEntity>)
    @Query("DELETE FROM garmin_daily") suspend fun deleteGarmin()
    @Query("DELETE FROM daily") suspend fun deleteDays()
    @Query("DELETE FROM medals") suspend fun deleteMedals()
    @Transaction suspend fun deleteAll() { deleteDays(); deleteMedals(); deleteGarmin(); deleteAchievements();clearUnlocks();clearSamples();clearExtras() }
}
@Database(entities=[DailyEntity::class, MedalEntity::class,GarminDayEntity::class, AchievementProgressEntity::class,UnlockEvent::class,HeartSampleEntity::class,HealthExtraEntity::class], version=4, exportSchema=true)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun health(): HealthDao
    companion object {
        val MIGRATION_3_4=object:Migration(3,4){override fun migrate(db:SupportSQLiteDatabase){
            db.execSQL("CREATE TABLE IF NOT EXISTS unlock_events (scope TEXT NOT NULL, instance TEXT NOT NULL, acknowledged INTEGER NOT NULL, PRIMARY KEY(scope,instance))")
            db.execSQL("INSERT OR IGNORE INTO unlock_events SELECT scope,instance,1 FROM achievement_progress WHERE unlocked=1")
            db.execSQL("CREATE TABLE IF NOT EXISTS heart_samples (zone TEXT NOT NULL,time INTEGER NOT NULL,source TEXT NOT NULL,bpm REAL NOT NULL,PRIMARY KEY(zone,time,source))")
            db.execSQL("CREATE TABLE IF NOT EXISTS health_extra (zone TEXT NOT NULL,date TEXT NOT NULL,payload TEXT NOT NULL,synced INTEGER NOT NULL,PRIMARY KEY(zone,date))")
        }}
        val MIGRATION_2_3=object: Migration(2,3) {override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `achievement_progress` (`scope` TEXT NOT NULL, `instance` TEXT NOT NULL, `definition` TEXT NOT NULL, `period` TEXT, `progress` REAL NOT NULL, `unlocked` INTEGER NOT NULL, `unlockedDate` TEXT, PRIMARY KEY(`scope`, `instance`))")
        }}
        val MIGRATION_1_2=object: Migration(1,2) {override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `garmin_daily` (`date` TEXT NOT NULL, `zone` TEXT NOT NULL, `payload` TEXT NOT NULL, `synced` INTEGER NOT NULL, PRIMARY KEY(`date`, `zone`))")
        }}
        fun open(context: Context)=Room.databaseBuilder(context,PulseDatabase::class.java,"pulse_health.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4).build()
    }
}
