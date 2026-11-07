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

private val Context.pulseStore by preferencesDataStore(name = "pulse_preferences")
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
    fun encode(p: Preferences) = p.sanitized().let { listOf("v1",it.onboarded,it.mode.name,it.stepGoal,it.sleepGoal,it.theme,it.animations,it.haptics,it.insights,it.imperial).joinToString("|") }
    fun decode(raw: String): Preferences = runCatching { val a=raw.split('|'); require(a.size==10 && a[0]=="v1")
        Preferences(a[1].toBooleanStrict(),DataMode.valueOf(a[2]),a[3].toInt(),a[4].toDouble(),a[5],a[6].toBooleanStrict(),a[7].toBooleanStrict(),a[8].toBooleanStrict(),a[9].toBooleanStrict()).sanitized()
    }.getOrDefault(Preferences())
}
@Entity(tableName="daily", primaryKeys=["date","zone"])
data class DailyEntity(val date: String, val zone: String, val steps: Double?, val sleep: Double?, val heart: Double?, val distance: Double?, val synced: Long)
@Entity(tableName="medals", primaryKeys=["month","zone"])
data class MedalEntity(val month: String, val zone: String, val steps: Long?, val tier: String?, val state: String, val recalculated: Long)
@Entity(tableName="garmin_daily",primaryKeys=["date","zone"])
data class GarminDayEntity(val date: String,val zone: String,val payload: String,val synced: Long)
@Dao
interface HealthDao {
    @Query("SELECT * FROM daily WHERE zone = :zone ORDER BY date") suspend fun days(zone: String): List<DailyEntity>
    @Upsert suspend fun saveDays(rows: List<DailyEntity>)
    @Upsert suspend fun saveMedals(rows: List<MedalEntity>)
    @Query("SELECT * FROM garmin_daily ORDER BY date") suspend fun garminDays(): List<GarminDayEntity>
    @Upsert suspend fun saveGarmin(rows: List<GarminDayEntity>)
    @Query("DELETE FROM garmin_daily") suspend fun deleteGarmin()
    @Query("DELETE FROM daily") suspend fun deleteDays()
    @Query("DELETE FROM medals") suspend fun deleteMedals()
    @Transaction suspend fun deleteAll() { deleteDays(); deleteMedals(); deleteGarmin() }
}
@Database(entities=[DailyEntity::class, MedalEntity::class,GarminDayEntity::class], version=2, exportSchema=true)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun health(): HealthDao
    companion object {
        val MIGRATION_1_2=object: Migration(1,2) {override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `garmin_daily` (`date` TEXT NOT NULL, `zone` TEXT NOT NULL, `payload` TEXT NOT NULL, `synced` INTEGER NOT NULL, PRIMARY KEY(`date`, `zone`))")
        }}
        fun open(context: Context)=Room.databaseBuilder(context,PulseDatabase::class.java,"pulse_health.db").addMigrations(MIGRATION_1_2).build()
    }
}
