package com.pulse.app
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import com.pulse.app.core.*
import com.pulse.app.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
class OverhaulPersistenceTest {
 @Test fun pendingUnlockSurvivesReopenAndAcknowledgementIsDurable()=runBlocking {
 val c=InstrumentationRegistry.getInstrumentation().targetContext;val name="overhaul-unlock-test.db";c.deleteDatabase(name)
 fun open()=Room.databaseBuilder(c,PulseDatabase::class.java,name).build()
 var db=open();val award=AchievementProgressEntity("test","steps:2026-11","steps","2026-11",90000.0,true,"2026-11-08")
 try{db.health().recordAchievements("test",listOf(award));db.health().recordAchievements("test",listOf(award));assertEquals(1,db.health().pending("test").size);db.close();db=open();assertEquals(1,db.health().pending("test").size);db.health().acknowledge("test",listOf(award.instance));db.close();db=open();db.health().recordAchievements("test",listOf(award));assertTrue(db.health().pending("test").isEmpty());assertTrue(db.health().achievements("test").single().unlocked)}finally{db.close();c.deleteDatabase(name)}
 }
 @Test fun repeatedRawSampleImportsReplaceWindowWithoutDuplicates()=runBlocking{val c=InstrumentationRegistry.getInstrumentation().targetContext;val db=Room.inMemoryDatabaseBuilder(c,PulseDatabase::class.java).build();try{val rows=listOf(HeartSampleEntity("test",1000L,"source",72.0));repeat(3){db.health().replaceSamples("test",0L,2000L,rows)};assertEquals(1,db.health().samples("test").size);db.health().replaceSamples("test",0L,2000L,emptyList());assertTrue(db.health().samples("test").isEmpty())}finally{db.close()}}
 @Test fun migrationAcknowledgesLegacyAwardsWithoutDeletingThem(){val c=InstrumentationRegistry.getInstrumentation().targetContext;val configuration=androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(c).callback(object:androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3){override fun onCreate(db:androidx.sqlite.db.SupportSQLiteDatabase){db.execSQL("CREATE TABLE achievement_progress(scope TEXT NOT NULL,instance TEXT NOT NULL,unlocked INTEGER NOT NULL)");db.execSQL("INSERT INTO achievement_progress VALUES('test','old',1),('test','locked',0)")};override fun onUpgrade(db:androidx.sqlite.db.SupportSQLiteDatabase,oldVersion:Int,newVersion:Int){}}).build();val helper=androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(configuration);try{val sql=helper.writableDatabase;PulseDatabase.MIGRATION_3_4.migrate(sql);sql.query("SELECT count(*) FROM achievement_progress").use{assertTrue(it.moveToFirst());assertEquals(2,it.getInt(0))};sql.query("SELECT instance,acknowledged FROM unlock_events").use{assertTrue(it.moveToFirst());assertEquals("old",it.getString(0));assertEquals(1,it.getInt(1));assertFalse(it.moveToNext())}}finally{helper.close()}}

 @Test fun installedDatabaseOpensAtVersionFourWithoutRecreatingIt()=runBlocking{val c=InstrumentationRegistry.getInstrumentation().targetContext;val db=PulseDatabase.open(c);try{db.health().achievements("OVERHAUL_READ_ONLY_PROBE");assertEquals(4,db.openHelper.readableDatabase.version)}finally{db.close()}}

 @Test fun dashboardMigrationRunsOnceAndKeepsSelectionsAndEditsAfterReopening() = runBlocking {
     val context = InstrumentationRegistry.getInstrumentation().targetContext
     val file = File(context.cacheDir, "modes-${UUID.randomUUID()}.preferences_pb")
     var job = SupervisorJob()
     fun open() = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
     val widget = Widget("my-steps", Metric.STEPS, WidgetShape.CIRCLE, column = 2, row = 3, showTrend = false)
     val prefs = Preferences(onboarded = true, stepGoal = 7050, insights = false)
     try {
         val legacyStore = open()
         legacyStore.edit {
             it[stringPreferencesKey("settings_v1")] = PreferencesCodec.encode(prefs)
             it[stringPreferencesKey("layout_v1")] = LayoutCodec.encode(listOf(widget))
         }
         val store = PreferenceStore(legacyStore)
         val migrated = store.dashboardModes()
         assertEquals(listOf(widget), migrated.active.widgets)
         assertFalse(migrated.active.showObservations)
         val changed = migrated.select("fitness").update("fitness") { it.copy(widgets = emptyList()) }
         store.saveModes(changed)
         job.cancelAndJoin()
         job = SupervisorJob()
         val reopened = PreferenceStore(open())
         assertEquals(changed, reopened.dashboardModes())
         assertEquals(listOf(widget), reopened.dashboardModes().select("everyday").active.widgets)
         assertEquals(prefs, reopened.preferences.first())
     } finally {
         job.cancelAndJoin()
         file.delete()
     }
     Unit
 }
}
