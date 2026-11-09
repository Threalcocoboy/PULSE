package com.pulse.app.data

import android.content.Context
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
class PreferenceStore(context: Context) {
    private val store = context.pulseStore
    private val settingsKey = stringPreferencesKey("settings_v1")
    private val layoutKey = stringPreferencesKey("layout_v1")
    val preferences = store.data.catch { if(it is IOException) emit(emptyPreferences()) else throw it }.map { PreferencesCodec.decode(it[settingsKey] ?: "") }
    val layout = store.data.catch { if(it is IOException) emit(emptyPreferences()) else throw it }.map { LayoutCodec.decode(it[layoutKey] ?: "") }
    suspend fun update(transform: (Preferences) -> Preferences) { store.edit { it[settingsKey] = PreferencesCodec.encode(transform(PreferencesCodec.decode(it[settingsKey] ?: "")).sanitized()) } }
    suspend fun layout(value: List<Widget>) { require(AdaptiveFlow.isValid(value)); store.edit { it[layoutKey] = LayoutCodec.encode(value) } }
    suspend fun clear() { store.edit { it.clear() } }
}
object PreferencesCodec {
    fun encode(p: Preferences) = p.sanitized().let { listOf("v1",it.onboarded,it.mode.name,it.stepGoal,it.sleepGoal,it.theme,it.animations,it.haptics,it.insights,it.imperial,it.distanceGoal,it.activeGoal,it.workoutGoal,it.enabledGoals.joinToString(",") { g -> g.name },java.util.Base64.getUrlEncoder().encodeToString(it.displayName.toByteArray(Charsets.UTF_8)),it.introSeen,it.onboardingStep,it.progressNotifications,it.notificationMetrics.joinToString(","){g->g.name}).joinToString("|") }
    fun decode(raw: String): Preferences = runCatching { val a=raw.split('|'); require(a.size in setOf(10,14,19) && a[0]=="v1")
        Preferences(a[1].toBooleanStrict(),DataMode.valueOf(a[2]),a[3].toInt(),a[4].toDouble(),a[5],a[6].toBooleanStrict(),a[7].toBooleanStrict(),a[8].toBooleanStrict(),a[9].toBooleanStrict(),
            a.getOrNull(10)?.toDouble() ?: 5.0,a.getOrNull(11)?.toInt() ?: 30,a.getOrNull(12)?.toInt() ?: 3,
            a.getOrNull(13)?.split(',')?.map { Metric.valueOf(it) }?.toSet() ?: setOf(Metric.STEPS,Metric.SLEEP),a.getOrNull(14)?.let {String(java.util.Base64.getUrlDecoder().decode(it),Charsets.UTF_8)} ?: "",a.getOrNull(15)?.toBooleanStrict() ?: false,a.getOrNull(16)?.toInt() ?: 0,a.getOrNull(17)?.toBooleanStrict() ?: false,a.getOrNull(18)?.split(',')?.filter{it.isNotBlank()}?.map{Metric.valueOf(it)}?.toSet() ?: setOf(Metric.STEPS,Metric.DISTANCE)).sanitized()
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
    @Query("SELECT * FROM garmin_daily ORDER BY date") suspend fun garminDays(): List<GarminDayEntity>
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
