package com.pulse.app

import android.app.Application
import androidx.lifecycle.*
import com.pulse.app.core.*
import com.pulse.app.data.*
import com.pulse.app.integration.*
import com.pulse.app.integration.garmin.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class PulseViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    val store = PreferenceStore(application)
    private val db = PulseDatabase.open(application)
    val healthConnect = HealthConnectRepository(application,db.health())
    private val garminKeys=GarminKeyStore(application)
    private val garminApi=GarminApi(OfficialGarminTransport(),garminKeys::read)
    private val garmin=GarminRepository(garminApi,db.health())
    private val _garminConfigured=MutableStateFlow(garminKeys.configured());val garminConfigured=_garminConfigured.asStateFlow()
    private val _keyError=MutableStateFlow<String?>(null);val keyError=_keyError.asStateFlow()
    private val _keyBusy=MutableStateFlow(false);val keyBusy=_keyBusy.asStateFlow()
    val preferences = store.preferences.stateIn(viewModelScope,SharingStarted.Eagerly,Preferences())
    private val _ready=MutableStateFlow(false); val ready=_ready.asStateFlow()
    private val _layout=MutableStateFlow(AdaptiveFlow.defaults()); val layout=_layout.asStateFlow()
    private var history=LayoutHistory(_layout.value)
    private val _undo=MutableStateFlow(false); val canUndo=_undo.asStateFlow()
    private val _redo=MutableStateFlow(false); val canRedo=_redo.asStateFlow()
    private val _snapshot=MutableStateFlow(HealthSnapshot(loading=true)); val snapshot=_snapshot.asStateFlow()
    private val _achievements=MutableStateFlow<List<UserAchievement>>(emptyList()); val challengeRepository=ChallengeRepository(application)
    val challenges=challengeRepository.challenges.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val achievements=combine(_achievements,challenges) { badges,items -> badges+items.map{it.reward()} }.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    private val _pending=MutableStateFlow<List<UserAchievement>>(emptyList());val pendingUnlocks=combine(_pending,challenges) { badges,items -> badges+items.filter{it.completed!=null&&!it.rewardAcknowledged}.map{it.reward()} }.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    private var awardScope=""
    private val awardMutex=kotlinx.coroutines.sync.Mutex()
    private val calendarDay=MutableStateFlow(LocalDate.now())
    private val _message=MutableStateFlow<String?>(null); val message=_message.asStateFlow()
    private var lastRefreshAttempt:java.time.Instant?=null
    private var syncJob: Job? = null
    val editing=saved.getStateFlow("editing",false)
    init {
        viewModelScope.launch {
            combine(snapshot,challenges,calendarDay) { data,_,day -> data to day }.collect { (data,day) ->
                try { challengeRepository.evaluate(data,day) } catch(e:CancellationException){throw e} catch(e:Exception){_message.value="Could not save challenge progress. Retry."}
            }
        }
        viewModelScope.launch {
            val initial=store.layout.first()
            store.layout(initial)
            _layout.value=initial; history=LayoutHistory(initial)
            history.restore(saved.get<ArrayList<String>>("undo") ?: emptyList(),saved.get<ArrayList<String>>("redo") ?: emptyList()); updateHistory()
            store.preferences.first(); _ready.value=true
        }
        viewModelScope.launch { store.preferences.collect { HealthSyncWorker.schedule(application,it.onboarded && it.mode==DataMode.HEALTH_CONNECT);if(!it.progressNotifications)ProgressNotifications.cancel(application) else ProgressNotifications.publish(application,it,_snapshot.value) } }
        viewModelScope.launch { store.preferences.map { it.mode }.distinctUntilChanged().collect { refresh(it) } }
        viewModelScope.launch { while(isActive) { delay(30_000); calendarDay.value=LocalDate.now() } }
        viewModelScope.launch {
            combine(snapshot,preferences,calendarDay) { data,p,day -> Triple(data,p,day) }.collect { (data,p,today) ->
                if(data.mode != p.mode) { _achievements.value=emptyList();_pending.value=emptyList(); return@collect }
                awardMutex.lock()
                try {
                    if(data != _snapshot.value || p != preferences.value) return@collect
                    val scope=if(p.mode==DataMode.GARMIN) "GARMIN_ACCOUNT_LOCAL" else "${p.mode}:${java.time.ZoneId.systemDefault().id}"
                    val stored=if(p.mode==DataMode.DEMO) emptyList() else db.health().achievements(scope)
                    val months=(data.days.map { java.time.YearMonth.from(it.date) } + stored.mapNotNull { it.period?.let(java.time.YearMonth::parse) } + java.time.YearMonth.from(today)).distinct().filter { it <= java.time.YearMonth.from(today) }
                    val evaluated=withContext(Dispatchers.Default) {
                        AchievementEngine.evaluate(data,p,today) + months.flatMap { AchievementCollection.monthly(data,it,today) }
                    }
                    val old=stored.associateBy { it.instance }
                    val retained=evaluated.map { item -> old[item.instanceId]?.let { AchievementCollection.retain(item,it.progress,it.unlocked,it.unlockedDate?.let(LocalDate::parse)) } ?: item }
                    if(data != _snapshot.value || p != preferences.value || today != calendarDay.value) return@collect
                    if(!data.loading && data.error==null && p.mode!=DataMode.DEMO) db.health().recordAchievements(scope,retained.map {
                        AchievementProgressEntity(scope,it.instanceId,it.definition.id,it.period?.toString(),it.progress,it.unlocked,it.unlockedDate?.toString())
                    })
                    _achievements.value=retained
                    awardScope=scope
                    _pending.value=if(p.mode==DataMode.DEMO) emptyList() else {val ids=db.health().pending(scope).map{it.instance}.toSet();retained.filter{it.instanceId in ids}}
                } catch(e: CancellationException) {throw e} catch(e: Exception) {
                    _message.value="Achievement history could not be saved. Retry synchronization."
                } finally { awardMutex.unlock() }
            }
        }
    }
    fun edit(value: Boolean) { saved["editing"]=value }
    fun preferences(transform: (Preferences)->Preferences) { viewModelScope.launch { try { store.update(transform) } catch(e: Exception) { _message.value="Could not save preferences. Please retry." } } }
    fun commit(value: List<Widget>) { history.commit(AdaptiveFlow.pack(value)); _layout.value=history.current; persistLayout() }
    fun undo() { _layout.value=history.undo(); persistLayout() }
    fun redo() { _layout.value=history.redo(); persistLayout() }
    fun resetLayout() = commit(AdaptiveFlow.defaults())
    private fun updateHistory() { _undo.value=history.canUndo; _redo.value=history.canRedo; saved["undo"]=history.savedUndo(); saved["redo"]=history.savedRedo() }
    private fun persistLayout() {
        updateHistory(); val current=_layout.value
        viewModelScope.launch { try { store.layout(current) } catch(e: Exception) { _message.value="Layout could not be saved. Retry your change." } }
    }
    fun refreshIfNeeded() {if(RefreshPolicy.allow(lastRefreshAttempt,syncJob?.isActive==true,java.time.Instant.now()))refresh()}
    fun foregroundRefresh(){viewModelScope.launch{
        if(preferences.value.mode==DataMode.HEALTH_CONNECT && healthConnect.availability()==androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE){
            try{val granted=androidx.health.connect.client.HealthConnectClient.getOrCreate(getApplication()).permissionController.getGrantedPermissions();if(granted!=_snapshot.value.granted){refresh();return@launch}}catch(e:CancellationException){throw e}catch(e:Exception){/* Refresh retains the existing provider error handling. */}
        }
        refreshIfNeeded()
    }}
    fun refresh(mode: DataMode=preferences.value.mode) {
        lastRefreshAttempt=java.time.Instant.now()
        syncJob?.cancel()
        if(_snapshot.value.mode!=mode) _achievements.value=emptyList()
        _snapshot.value=if(_snapshot.value.mode==mode && _snapshot.value.days.isNotEmpty())_snapshot.value.copy(loading=true)else HealthSnapshot(loading=true,mode=mode)
        syncJob=viewModelScope.launch {
            if(mode==DataMode.GARMIN && _snapshot.value.days.isEmpty())_snapshot.value=garmin.cached()
            _snapshot.value=when(mode){DataMode.DEMO->DemoRepository().load(LocalDate.now());DataMode.HEALTH_CONNECT->healthConnect.load(LocalDate.now());DataMode.GARMIN->garmin.load(LocalDate.now())}
            val result=_snapshot.value
            result.error?.let{_message.value=it}
            com.pulse.app.integration.ProgressNotifications.publish(getApplication(),preferences.value,result)
        }
    }
    suspend fun createChallenge(c:PersonalChallenge):Boolean = try { challengeRepository.create(ChallengeEvaluator.evaluate(c,_snapshot.value,LocalDate.now()));true } catch(e:CancellationException){throw e} catch(e:Exception){_message.value="Could not create challenge. Retry.";false}
    private fun challengeAction(block:suspend ()->Unit){viewModelScope.launch{try{block()}catch(e:CancellationException){throw e}catch(e:Exception){_message.value="Could not save challenge change. Retry."}}}
    fun addChallengeProgress(id:String,amount:Double)=challengeAction{challengeRepository.add(id,amount,LocalDate.now(),_snapshot.value)}
    fun undoChallengeProgress(id:String)=challengeAction{challengeRepository.undo(id,_snapshot.value,LocalDate.now())}
    fun challengeStatus(id:String,status:ChallengeStatus)=challengeAction{challengeRepository.update{list->list.map{if(it.id==id)it.copy(status=status)else it}}}
    fun editChallengeIdentity(id:String,name:String,description:String)=challengeAction{require(name.isNotBlank());challengeRepository.update{list->list.map{if(it.id==id&&it.completed==null)it.copy(name=name.trim(),description=description.trim())else it}}}
    fun acknowledgeUnlocks(ids:List<String>){
        challengeAction{challengeRepository.update{list->list.map{if(it.reward().instanceId in ids)it.copy(rewardAcknowledged=true)else it}}}
val scope=awardScope;viewModelScope.launch{try{db.health().acknowledge(scope,ids);if(awardScope==scope)_pending.value=_pending.value.filterNot{it.instanceId in ids}}catch(e:Exception){_message.value="Could not acknowledge achievements. Please retry."}}}
    fun clearMessage() { _message.value=null }
    suspend fun cachedRecords() = db.health().days(java.time.ZoneId.systemDefault().id)
    suspend fun cachedGarminRecords()=db.health().garminDays()
    fun saveGarminKey(raw: String){viewModelScope.launch{keyOperation {applyGarminKey(raw)}}}
    fun importGarminKey(uri: android.net.Uri){viewModelScope.launch{keyOperation {
        val raw=withContext(Dispatchers.IO){getApplication<Application>().contentResolver.openInputStream(uri)?.use{input->
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(1024)
            while(true){val n=input.read(buffer);if(n<0)break;require(output.size()+n<=4096);output.write(buffer,0,n)}
            output.toString("UTF-8").removePrefix("\uFEFF")
        } ?: error("No key file")}
        applyGarminKey(raw)
    }}}
    private suspend fun applyGarminKey(raw: String) {
        require(raw.trim().length in 16..4096 && raw.trim().none{it.isWhitespace() || it.code !in 33..126})
        garminApi.validateKey(raw.trim())
        syncJob?.cancelAndJoin()
        awardMutex.withLock {
            db.health().deleteGarmin(); db.health().deleteGarminAchievements(); _achievements.value=emptyList()
            _snapshot.value=HealthSnapshot(loading=true,mode=DataMode.GARMIN)
        }
        withContext(Dispatchers.IO){garminKeys.save(raw)}
        garminApi.reset();_garminConfigured.value=true
        val alreadySelected=preferences.value.mode==DataMode.GARMIN
        store.update{it.copy(mode=DataMode.GARMIN)}
        if(alreadySelected)refresh(DataMode.GARMIN)
    }
    fun removeGarminKey(){viewModelScope.launch{keyOperation {
        syncJob?.cancelAndJoin();withContext(Dispatchers.IO){garminKeys.delete()};garminApi.reset()
        awardMutex.withLock {
            db.health().deleteGarmin();db.health().deleteGarminAchievements();_garminConfigured.value=false
            if(preferences.value.mode==DataMode.GARMIN) { _achievements.value=emptyList();_snapshot.value=HealthSnapshot(mode=DataMode.GARMIN) }
        }
        if(preferences.value.mode==DataMode.GARMIN)refresh(DataMode.GARMIN)
    }}}
    private suspend fun keyOperation(block: suspend ()->Unit){
        if(_keyBusy.value)return
        _keyBusy.value=true;_keyError.value=null
        try{block()}catch(e: CancellationException){throw e}catch(e: Exception){_keyError.value="Could not validate the Garmin key. Check the key and connection; saved credentials were not replaced.";_message.value=_keyError.value}finally{_keyBusy.value=false}
    }
    fun deleteLocal() { viewModelScope.launch {
        try {
            syncJob?.cancelAndJoin()
            awardMutex.withLock {
                _pending.value=emptyList();ProgressNotifications.cancel(getApplication());db.health().deleteAll(); _achievements.value=emptyList(); _snapshot.value=HealthSnapshot(loading=true,mode=DataMode.DEMO); store.clear()
            }
            withContext(Dispatchers.IO){garminKeys.delete()};garminApi.reset();_garminConfigured.value=false
            withContext(Dispatchers.IO) { java.io.File(getApplication<Application>().cacheDir,"exports").let { if(it.exists() && !it.deleteRecursively()) error("Could not clear export cache") } }
            saved["editing"]=false; history=LayoutHistory(AdaptiveFlow.defaults()); _layout.value=history.current; updateHistory(); refresh(DataMode.DEMO)
        } catch(e: CancellationException) {throw e} catch(e: Exception) {_message.value="Local data could not be fully removed. Please retry."}
    } }
    override fun onCleared() { db.close() }
}
