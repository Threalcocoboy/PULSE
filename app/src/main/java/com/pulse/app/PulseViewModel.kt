package com.pulse.app

import android.app.Application
import androidx.activity.result.ActivityResult
import androidx.lifecycle.*
import com.pulse.app.core.*
import com.pulse.app.data.*
import com.pulse.app.integration.*
import com.pulse.app.integration.garmin.*
import com.pulse.app.integration.googlehealth.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import java.time.LocalDate

class PulseViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    val store = PreferenceStore(application)
    private val db = PulseDatabase.open(application)
    val healthConnect = HealthConnectRepository(application,db.health())
    private val garminKeys=GarminKeyStore(application)
    private val providerSync = ProviderSync(application, db.health())
    private val googleProvider = GoogleHealthProvider(application)
    private val googleAwards = GoogleHealthStorage(application)
    private val googleAuth = GoogleHealthAuth(application)
    private val _googleSetup = MutableStateFlow(GoogleHealthResult())
    internal val googleSetup = _googleSetup.asStateFlow()
    private val _googlePrompt = MutableStateFlow<GoogleHealthPrompt?>(null)
    internal val googlePrompt = _googlePrompt.asStateFlow()
    private var googleStage: GoogleHealthAuthStage? = null
    private var googleScopes: Set<String> = emptySet()
    private var googleAccount: String? = null
    private var googlePrior: GoogleHealthResult? = null
    private val _healthSetup = MutableStateFlow(HealthSnapshot(mode = DataMode.HEALTH_CONNECT))
    val healthSetup = _healthSetup.asStateFlow()
    private val _garminConfigured=MutableStateFlow(garminKeys.configured());val garminConfigured=_garminConfigured.asStateFlow()
    private val _keyError=MutableStateFlow<String?>(null);val keyError=_keyError.asStateFlow()
    private val _keyBusy=MutableStateFlow(false);val keyBusy=_keyBusy.asStateFlow()
    val preferences = store.preferences.stateIn(viewModelScope,SharingStarted.Eagerly,Preferences())
    private val _ready=MutableStateFlow(false); val ready=_ready.asStateFlow()
    private val _modes=MutableStateFlow(PulseModes.initial()); val modes=_modes.asStateFlow()
    private val modeHistories = mutableMapOf<String, LayoutHistory>()
    private val modeWrites = Mutex()
    private var modeGeneration = 0
    private var deletingLocal = false
    private var modesLoaded = false
    private val _undo=MutableStateFlow(false); val canUndo=_undo.asStateFlow()
    private val _redo=MutableStateFlow(false); val canRedo=_redo.asStateFlow()
    private val _snapshot=MutableStateFlow(HealthSnapshot(mode=DataMode.NONE)); val snapshot=_snapshot.asStateFlow()
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
            combine(snapshot,challenges,calendarDay,preferences) { data,_,day,p -> Triple(data,day,p) }.collect { (data,day,p) ->
                try { challengeRepository.evaluate(if (data.mode == p.mode) data else HealthSnapshot(mode = p.mode),day,p) } catch(e:CancellationException){throw e} catch(e:Exception){_message.value="Could not save challenge progress. Retry."}
            }
        }
        viewModelScope.launch {
            val initial = try { store.dashboardModes() } catch (e: CancellationException) { throw e }
                catch (_: Exception) { _message.value = "Could not read saved dashboards. Restart PULSE to retry."; null }
            if (initial != null) {
                modesLoaded = true
                _modes.value = initial
                val mode = initial.active
                val savedId = saved.get<String>("historyMode") ?: "everyday"
                if (savedId == mode.id) history(mode).restore(saved.get<ArrayList<String>>("undo").orEmpty(), saved.get<ArrayList<String>>("redo").orEmpty())
                updateHistory()
            }
            store.preferences.first(); _ready.value=true
        }
        viewModelScope.launch {
            combine(preferences, snapshot, calendarDay, challenges) { p, data, _, items -> Triple(p, data, items) }
                .collect { (p, data, items) ->
                    HealthSyncWorker.schedule(application, p, data, items)
                    ProgressNotifications.publish(application, p, data)
                }
        }
        viewModelScope.launch {
            store.preferences.map { it.mode }.distinctUntilChanged().collect { mode ->
                if (_snapshot.value.mode != mode || !_snapshot.value.access.verified) refresh(mode)
            }
        }
        viewModelScope.launch { while(isActive) { delay(30_000); calendarDay.value=LocalDate.now() } }
        viewModelScope.launch {
            combine(snapshot,preferences,calendarDay) { data,p,day -> Triple(data,p,day) }.collect { (data,p,today) ->
                if(data.mode != p.mode || p.mode == DataMode.NONE) { _achievements.value=emptyList();_pending.value=emptyList(); return@collect }
                awardMutex.lock()
                try {
                    if(data != _snapshot.value || p != preferences.value) return@collect
                    val scope=data.sourceScope ?: return@collect
                    val googleScope = scope.startsWith("GOOGLE_HEALTH:")
                    val stored = if (googleScope) withContext(Dispatchers.IO) { googleAwards.achievements(scope) }
                        else db.health().achievements(scope)
                    val months=(data.days.map { java.time.YearMonth.from(it.date) } + stored.mapNotNull { it.period?.let(java.time.YearMonth::parse) } + java.time.YearMonth.from(today)).distinct().filter { it <= java.time.YearMonth.from(today) }
                    val evaluated=withContext(Dispatchers.Default) {
                        AchievementEngine.evaluate(data,p,today) + months.flatMap { AchievementCollection.monthly(data,it,today) }
                    }
                    val old=stored.associateBy { it.instance }
                    val retained=evaluated.map { item -> old[item.instanceId]?.let { AchievementCollection.retain(item,it.progress,it.unlocked,it.unlockedDate?.let(LocalDate::parse)) } ?: item }
                    if(data != _snapshot.value || p != preferences.value || today != calendarDay.value) return@collect
                    if (!data.loading && data.access.verified && data.error == null) {
                        val rows = retained.map {
                            AchievementProgressEntity(scope,it.instanceId,it.definition.id,it.period?.toString(),it.progress,it.unlocked,it.unlockedDate?.toString())
                        }
                        if (googleScope) withContext(Dispatchers.IO) { googleAwards.record(scope, rows) }
                        else db.health().recordAchievements(scope, rows)
                    }
                    _achievements.value=retained
                    awardScope=scope
                    val pending = if (googleScope) withContext(Dispatchers.IO) { googleAwards.pending(scope) }
                        else db.health().pending(scope).map { it.instance }.toSet()
                    _pending.value = retained.filter { it.instanceId in pending }
                } catch(e: CancellationException) {throw e} catch(e: Exception) {
                    _message.value="Achievement history could not be saved. Retry synchronization."
                } finally { awardMutex.unlock() }
            }
        }
    }
    fun edit(value: Boolean) { saved["editing"]=value }
    fun preferences(transform: (Preferences)->Preferences) { viewModelScope.launch { try { store.update(transform) } catch(e: CancellationException) { throw e } catch(e: Exception) { _message.value="Could not save preferences. Please retry." } } }
    private fun history(mode: PulseMode) = modeHistories.getOrPut(mode.id) { LayoutHistory(mode.widgets) }

    fun commit(modeId: String, value: List<Widget>) = editModeLayout(modeId) { it.commit(value) }
    fun undo() = editModeLayout(_modes.value.activeId) { it.undo() }
    fun redo() = editModeLayout(_modes.value.activeId) { it.redo() }
    fun resetLayout(modeId: String) {
        _modes.value.modes.firstOrNull { it.id == modeId }?.let { commit(modeId, it.reset().widgets) }
    }

    private fun editModeLayout(id: String, edit: (LayoutHistory) -> Unit) {
        if (!modesLoaded || deletingLocal) return
        val mode = _modes.value.modes.firstOrNull { it.id == id } ?: return
        val history = history(mode)
        edit(history)
        changeModes { it.update(id) { item -> item.copy(widgets = history.current) } }
    }

    fun selectMode(id: String) { if (!editing.value) changeModes { it.select(id) } }
    fun moveMode(id: String, offset: Int) = changeModes { it.move(id, offset) }
    fun removeMode(id: String) {
        if (editing.value || _modes.value.modes.size <= 1) return
        _modes.value.removed?.let { modeHistories.remove(it.mode.id) }
        changeModes { it.remove(id) }
    }
    fun restoreMode() = changeModes(PulseModes::restore)
    fun saveMode(id: String?, name: String, icon: ModeIcon, preset: ModePreset?, duplicate: String?, showObservations: Boolean) {
        val clean = PulseModes.cleanName(name)
        if (clean.isEmpty() || editing.value) return
        changeModes { modes ->
            if (id == null) modes.create(clean, icon, preset, duplicate, showObservations)
            else modes.update(id) { it.copy(name = clean, icon = icon, showObservations = showObservations) }
        }
    }

    private fun updateHistory() {
        val mode = _modes.value.active
        val history = history(mode)
        _undo.value = history.canUndo
        _redo.value = history.canRedo
        saved["historyMode"] = mode.id
        saved["undo"] = history.savedUndo(limit = 12, maxChars = 24000)
        saved["redo"] = history.savedRedo(limit = 12, maxChars = 24000)
    }

    private fun changeModes(change: (PulseModes) -> PulseModes) {
        if (!modesLoaded || deletingLocal) return
        val before = _modes.value
        val after = change(before)
        _modes.value = after
        updateHistory()
        if (after == before) return
        val generation = modeGeneration
        viewModelScope.launch {
            modeWrites.withLock {
                if (generation != modeGeneration) return@withLock
                try { store.saveModes(after) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { _message.value = "Dashboard changes could not be saved. Make another change to retry." }
            }
        }
    }
    fun refreshIfNeeded() {if(googlePrior == null && RefreshPolicy.allow(lastRefreshAttempt,syncJob?.isActive==true,java.time.Instant.now()))refresh()}
    fun foregroundRefresh(){viewModelScope.launch{
        if(preferences.value.mode==DataMode.HEALTH_CONNECT && healthConnect.availability()==androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE){
            try{val granted=androidx.health.connect.client.HealthConnectClient.getOrCreate(getApplication()).permissionController.getGrantedPermissions();if(granted!=_snapshot.value.granted){refresh();return@launch}}catch(e:CancellationException){throw e}catch(e:Exception){/* Refresh retains the existing provider error handling. */}
        }
        refreshIfNeeded()
    }}
    fun refresh(mode: DataMode=preferences.value.mode, force: Boolean = false) {
        lastRefreshAttempt=java.time.Instant.now()
        syncJob?.cancel()
        if(_snapshot.value.mode!=mode) _achievements.value=emptyList()
        _snapshot.value=if(_snapshot.value.mode==mode && (_snapshot.value.days.isNotEmpty() || _snapshot.value.access.verified))_snapshot.value.copy(loading=true)else HealthSnapshot(loading=true,mode=mode)
        syncJob = viewModelScope.launch {
            val today = LocalDate.now()
            val p = store.preferences.first()
            val result = ProviderSync.mutex.withLock {
                providerSync.load(mode, today, onCached = if (mode == DataMode.GARMIN && _snapshot.value.days.isEmpty()) {
                    { cached -> _snapshot.value = cached }
                } else null, preferences = p, force = force, onGoogle = { outcome ->
                    if (googlePrior == null) _googleSetup.value = outcome
                })
            }
            _snapshot.value = result
            if (mode == DataMode.HEALTH_CONNECT) _healthSetup.value = result
            if (preferences.value.onboarded && mode != DataMode.GOOGLE_HEALTH) result.error?.let { _message.value = it }
        }
    }
    fun checkHealthConnect() {
        if (_healthSetup.value.loading) return
        _healthSetup.value = HealthSnapshot(loading = true, mode = DataMode.HEALTH_CONNECT)
        viewModelScope.launch {
            val result = ProviderSync.mutex.withLock { healthConnect.load(LocalDate.now()) }
            _healthSetup.value = result
            val p = store.preferences.first()
            if (GoogleHealthAvailability.enabled && p.mode == DataMode.GOOGLE_HEALTH) {
                if (result.access.verified) {
                    val updated = p.copy(combineHealthConnect = true)
                    store.update { it.copy(combineHealthConnect = true) }
                    refresh(updated.mode)
                }
                return@launch
            }
            if (result.access.verified || p.mode == DataMode.HEALTH_CONNECT) {
                syncJob?.cancelAndJoin()
                _snapshot.value = result
            }
            if (result.access.verified) {
                try {
                    store.update { it.copy(mode = DataMode.HEALTH_CONNECT, sourceSetup = SetupStatus.COMPLETE) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _healthSetup.value = result.copy(access = ProviderAccess.ERROR, error = "Could not save the connection. Try again.")
                }
            }
        }
    }

    internal fun showGoogleHealth() {
        if (!GoogleHealthAvailability.enabled) return
        if (preferences.value.mode == DataMode.GOOGLE_HEALTH || googlePrior != null) return
        val initial = _googleSetup.value
        viewModelScope.launch {
            val result = ProviderSync.mutex.withLock { googleProvider.load(LocalDate.now()) }
            if (_googleSetup.value == initial && googlePrior == null &&
                store.preferences.first().mode != DataMode.GOOGLE_HEALTH) _googleSetup.value = result
        }
    }

    internal val googleAuthorizationInProgress get() = googlePrior != null

    internal fun beginGoogleAuthorization(scopes: Set<String>, switchAccount: Boolean) {
        if (!GoogleHealthAvailability.enabled) return
        if (googlePrior != null || scopes.isEmpty()) return
        googlePrior = _googleSetup.value
        googleScopes = scopes
        googleStage = GoogleHealthAuthStage.AUTHORIZE
        _googleSetup.value = _googleSetup.value.copy(status = GoogleHealthStatus.AUTHORIZING, problem = null)
        viewModelScope.launch {
            googleOperation {
                val existing = ProviderSync.mutex.withLock { googleProvider.connection() }
                if (existing == null || switchAccount) {
                    googleStage = GoogleHealthAuthStage.ACCOUNT_PICKER
                    _googlePrompt.value = GoogleHealthPrompt.ChooseAccount(googleAuth.accountPicker())
                } else requestGoogle(existing.account)
            }
        }
    }

    internal fun consumeGooglePrompt(prompt: GoogleHealthPrompt) = _googlePrompt.compareAndSet(prompt, null)

    internal fun googlePromptFailed() {
        finishGoogleFailure(GoogleHealthFailure(GoogleHealthProblem.AUTHORIZATION_LAUNCH_FAILED))
    }

    internal fun googleAccountSelected(result: ActivityResult) {
        if (!GoogleHealthAvailability.enabled) return
        if (googleStage != GoogleHealthAuthStage.ACCOUNT_PICKER) return
        val account = try {
            googleAuth.accountFrom(result)
        } catch (e: GoogleHealthFailure) {
            finishGoogleFailure(e, result.resultCode)
            return
        }
        logGoogleAuthorization(GoogleHealthAuthStage.ACCOUNT_PICKER, null, result.resultCode)
        googleStage = GoogleHealthAuthStage.AUTHORIZE
        viewModelScope.launch { googleOperation { requestGoogle(account) } }
    }

    private suspend fun requestGoogle(account: String) {
        googleAccount = account
        val result = googleAuth.request(account, googleScopes)
        logGoogleAuthorization(GoogleHealthAuthStage.AUTHORIZE, null, null)
        when (result) {
            is GoogleAuthorization.Consent -> {
                googleStage = GoogleHealthAuthStage.CONSENT
                _googlePrompt.value = GoogleHealthPrompt.Consent(result.intent)
            }
            is GoogleAuthorization.Granted -> connectGoogle(account, result.token)
        }
    }

    internal fun googleConsentFinished(result: ActivityResult) {
        if (!GoogleHealthAvailability.enabled) return
        if (googleStage != GoogleHealthAuthStage.CONSENT) return
        val account = googleAccount ?: return finishGoogleFailure(GoogleHealthFailure(GoogleHealthProblem.AUTHENTICATION), result.resultCode)
        val token = try {
            googleAuth.finish(result, account)
        } catch (e: GoogleHealthFailure) {
            finishGoogleFailure(e, result.resultCode)
            return
        }
        logGoogleAuthorization(GoogleHealthAuthStage.CONSENT, null, result.resultCode)
        googleStage = GoogleHealthAuthStage.IDENTITY
        viewModelScope.launch { googleOperation { connectGoogle(account, token) } }
    }

    private suspend fun connectGoogle(account: String, token: GoogleHealthToken) {
        googleStage = GoogleHealthAuthStage.IDENTITY
        syncJob?.cancelAndJoin()
        val today = LocalDate.now()
        ProviderSync.mutex.withLock {
            _googleSetup.value = _googleSetup.value.copy(status = GoogleHealthStatus.VERIFYING)
            val connection = googleProvider.verify(account, token)
            logGoogleAuthorization(GoogleHealthAuthStage.IDENTITY, null, null)
            googleStage = null
            _googleSetup.value = GoogleHealthResult(GoogleHealthStatus.FIRST_SYNC, connection)
            _snapshot.value = HealthSnapshot(mode = DataMode.GOOGLE_HEALTH, loading = true)
            _achievements.value = emptyList()
            _pending.value = emptyList()
            val result = googleProvider.connectSync(connection, token, today)
            val p = store.preferences.first()
            val next = p.copy(mode = DataMode.GOOGLE_HEALTH,
                combineHealthConnect = p.combineHealthConnect || p.mode == DataMode.HEALTH_CONNECT,
                sourceSetup = if (result.initialSyncFinished) SetupStatus.COMPLETE else SetupStatus.NEEDS_ATTENTION)
            _googleSetup.value = result
            _snapshot.value = providerSync.combineGoogle(result.snapshot, today, next, background = false)
            store.update { it.copy(mode = next.mode, combineHealthConnect = next.combineHealthConnect, sourceSetup = next.sourceSetup) }
            clearGoogleAttempt()
        }
    }

    private suspend fun googleOperation(action: suspend () -> Unit) {
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            finishGoogleFailure(e as? GoogleHealthFailure ?: GoogleHealthFailure(GoogleHealthProblem.STORAGE))
        }
    }

    private fun finishGoogleFailure(failure: GoogleHealthFailure, resultCode: Int? = null) {
        googleStage?.let { logGoogleAuthorization(it, failure, resultCode) }
        val problem = failure.problem
        val prior = googlePrior ?: _googleSetup.value
        val status = when (problem) {
            GoogleHealthProblem.CONSENT -> GoogleHealthStatus.CONSENT
            GoogleHealthProblem.NOT_LINKED -> GoogleHealthStatus.UNLINKED
            GoogleHealthProblem.ACCOUNT_SELECTION_CANCELLED, GoogleHealthProblem.AUTHORIZATION_CANCELLED -> prior.status
            GoogleHealthProblem.REAUTHORIZE, GoogleHealthProblem.ACCOUNT_CHANGED, GoogleHealthProblem.EXPIRED,
            GoogleHealthProblem.PERMISSION, GoogleHealthProblem.AUTHENTICATION -> GoogleHealthStatus.REAUTHORIZE
            else -> GoogleHealthStatus.ERROR
        }
        _googleSetup.value = prior.copy(status = status, problem = problem)
        clearGoogleAttempt()
    }

    private fun clearGoogleAttempt() {
        _googlePrompt.value = null
        googleStage = null
        googlePrior = null
        googleAccount = null
        googleScopes = emptySet()
    }

    internal fun syncGoogleNow() {
        if (!GoogleHealthAvailability.enabled) return
        if (googlePrior != null || _googleSetup.value.status == GoogleHealthStatus.SYNCING) return
        if (preferences.value.mode == DataMode.GOOGLE_HEALTH) {
            _googleSetup.value = _googleSetup.value.copy(status = GoogleHealthStatus.SYNCING, problem = null)
            refresh(force = true)
        } else {
            _googleSetup.value = _googleSetup.value.copy(status = GoogleHealthStatus.SYNCING, problem = null)
            viewModelScope.launch {
                _googleSetup.value = ProviderSync.mutex.withLock { googleProvider.load(LocalDate.now(), force = true) }
            }
        }
    }

    internal fun googlePriority(metric: Metric, mode: DataMode?) {
        if (!GoogleHealthAvailability.enabled) return
        viewModelScope.launch {
            store.update { p ->
                val key = ProviderPriority.key(metric)
                p.copy(providerPriority = if (mode == null) p.providerPriority - key else p.providerPriority + (key to mode))
            }
            refresh()
        }
    }

    internal fun stopCombiningHealthConnect() {
        viewModelScope.launch {
            store.update { it.copy(combineHealthConnect = false) }
            refresh()
        }
    }

    internal fun disconnectGoogle(removeData: Boolean) {
        if (!GoogleHealthAvailability.enabled) return
        if (googlePrior != null || _googleSetup.value.status == GoogleHealthStatus.SYNCING) return
        _googleSetup.value = _googleSetup.value.copy(status = GoogleHealthStatus.SYNCING)
        viewModelScope.launch {
            googleOperation {
                syncJob?.cancelAndJoin()
                ProviderSync.mutex.withLock {
                    val p = store.preferences.first()
                    val next = if (p.combineHealthConnect) DataMode.HEALTH_CONNECT else DataMode.NONE
                    if (p.mode == DataMode.GOOGLE_HEALTH) {
                        _snapshot.value = HealthSnapshot(mode = next, loading = true)
                        ProgressNotifications.cancel(getApplication())
                    }
                    val (connection, revoked) = awardMutex.withLock { googleProvider.disconnect(removeData) }
                    if (removeData && connection != null) challengeRepository.forgetGoogleProgress(connection.sourceScope)
                    _googleSetup.value = GoogleHealthResult()
                    if (p.mode == DataMode.GOOGLE_HEALTH) {
                        store.update { it.copy(mode = next, sourceSetup = SetupStatus.NOT_STARTED,
                            progressNotifications = it.progressNotifications && next != DataMode.NONE) }
                    }
                    if (!revoked) _message.value = "Disconnected from PULSE. Remove PULSE access in your Google Account to finish revoking consent."
                }
                refresh(store.preferences.first().mode)
            }
        }
    }
    fun finishOnboarding() {
        viewModelScope.launch {
            try {
                store.update { it.copy(onboarded = true) }
                refresh(store.preferences.first().mode)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _message.value = "Could not finish setup. Try again."
            }
        }
    }
    suspend fun createChallenge(c:PersonalChallenge):Boolean = try { challengeRepository.create(ChallengeEvaluator.evaluate(c.copy(sourceScope = _snapshot.value.sourceScope),_snapshot.value,LocalDate.now(),preferences.value));true } catch(e:CancellationException){throw e} catch(e:Exception){_message.value="Could not create challenge. Retry.";false}
    private fun challengeAction(block:suspend ()->Unit){viewModelScope.launch{try{block()}catch(e:CancellationException){throw e}catch(e:Exception){_message.value="Could not save challenge change. Retry."}}}
    fun addChallengeProgress(id:String,amount:Double)=challengeAction{challengeRepository.add(id,amount,LocalDate.now(),_snapshot.value)}
    fun undoChallengeProgress(id:String)=challengeAction{challengeRepository.undo(id,_snapshot.value,LocalDate.now())}
    fun challengeStatus(id:String,status:ChallengeStatus)=challengeAction{challengeRepository.update{list->list.map{if(it.id==id)it.copy(status=status)else it}}}
    fun editChallengeIdentity(id:String,name:String,description:String)=challengeAction{require(name.isNotBlank());challengeRepository.update{list->list.map{if(it.id==id&&it.completed==null)it.copy(name=name.trim(),description=description.trim())else it}}}
    fun acknowledgeUnlocks(ids:List<String>){
        challengeAction{challengeRepository.update{list->list.map{if(it.reward().instanceId in ids)it.copy(rewardAcknowledged=true)else it}}}
        val scope = awardScope
        viewModelScope.launch {
            try {
                awardMutex.withLock {
                    if (scope.startsWith("GOOGLE_HEALTH:")) {
                        if (_snapshot.value.sourceScope == scope) withContext(Dispatchers.IO) { googleAwards.acknowledge(scope, ids) }
                    }
                    else db.health().acknowledge(scope, ids)
                    if (awardScope == scope) _pending.value = _pending.value.filterNot { it.instanceId in ids }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _message.value = "Could not acknowledge achievements. Please retry." }
        }
    }
    fun clearMessage() { _message.value=null }
    suspend fun cachedRecords() = db.health().days(java.time.ZoneId.systemDefault().id)
    suspend fun cachedGarminRecords() = withContext(Dispatchers.IO) {
        garminKeys.credential()?.let { db.health().garminDays(it.scope) }.orEmpty()
    }

    fun saveGarminKey(raw: String) {
        viewModelScope.launch { keyOperation {
            val key = raw.trim()
            if (key.isEmpty()) error("Enter your Garmin Personal API key.")
            if (key.length > 4096 || key.any { it.isWhitespace() || it.code !in 33..126 }) {
                error("Check that you pasted the complete Garmin key.")
            }
            val api = GarminApi(OfficialGarminTransport(), { key })
            val account = api.validateKey(key)
            syncJob?.cancelAndJoin()
            val scope = withContext(Dispatchers.IO) {
                ProviderSync.mutex.withLock {
                    val prior = runCatching { garminKeys.credential() }.getOrNull()
                    val knownAccount = prior?.account ?: prior?.let {
                        if (it.key == key) account else try {
                            api.validateKey(it.key)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: GarminFailure) {
                            null
                        }
                    }
                    val sameAccount = prior != null && (prior.key == key || account != null && knownAccount == account)
                    val next = if (sameAccount) requireNotNull(prior).scope else
                        "GARMIN:${account ?: java.util.UUID.randomUUID().toString()}"
                    garminKeys.save(key, next, account ?: knownAccount.takeIf { sameAccount })
                    ProviderSync.clearGarminSession()
                    next
                }
            }
            _garminConfigured.value = true
            _snapshot.value = HealthSnapshot(mode = DataMode.GARMIN, sourceScope = scope,
                access = ProviderAccess.CONNECTED, status = "Garmin key verified")
            store.update { it.copy(mode = DataMode.GARMIN, sourceSetup = SetupStatus.COMPLETE) }
            refresh(DataMode.GARMIN)
        } }
    }
    fun useSavedGarminKey() {
        viewModelScope.launch { keyOperation {
            val credential = withContext(Dispatchers.IO) { garminKeys.credential() }
                ?: throw GarminFailure(GarminError.KEY_REQUIRED)
            GarminApi(OfficialGarminTransport(), { credential.key }).validateKey(credential.key)
            syncJob?.cancelAndJoin()
            _snapshot.value = HealthSnapshot(mode = DataMode.GARMIN, sourceScope = credential.scope,
                access = ProviderAccess.CONNECTED, status = "Garmin key verified")
            store.update { it.copy(mode = DataMode.GARMIN, sourceSetup = SetupStatus.COMPLETE) }
            refresh(DataMode.GARMIN)
        } }
    }
    fun removeGarminKey() {
        viewModelScope.launch { keyOperation {
            syncJob?.cancelAndJoin()
            ProviderSync.mutex.withLock {
                withContext(Dispatchers.IO) { garminKeys.delete() }
                ProviderSync.clearGarminSession()
                db.health().deleteGarmin()
                _garminConfigured.value = false
                store.update { p -> if (p.mode == DataMode.GARMIN) p.copy(mode = DataMode.NONE,
                    progressNotifications = false, sourceSetup = SetupStatus.NOT_STARTED) else p }
            }
        } }
    }
    private suspend fun keyOperation(block: suspend () -> Unit) {
        if (_keyBusy.value) return
        _keyBusy.value = true
        _keyError.value = null
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: GarminFailure) {
            _keyError.value = e.message
        } catch (e: IllegalStateException) {
            _keyError.value = when (e.message) {
                "Enter your Garmin Personal API key.", "Check that you pasted the complete Garmin key." -> e.message
                else -> "Could not save the Garmin key. Try again."
            }
        } catch (_: Exception) {
            _keyError.value = "Could not save the Garmin key. Try again."
        } finally {
            _keyBusy.value = false
        }
    }
    fun deleteLocal() { viewModelScope.launch {
        if (deletingLocal) return@launch
        deletingLocal = true
        modeGeneration++
        try {
            syncJob?.cancelAndJoin()
            ProviderSync.mutex.withLock { awardMutex.withLock {
                _pending.value=emptyList();ProgressNotifications.cancel(getApplication());db.health().deleteAll(); _achievements.value=emptyList(); _snapshot.value=HealthSnapshot(loading=true,mode=DataMode.NONE); modeWrites.withLock { store.clear() }
            }
            withContext(Dispatchers.IO){garminKeys.delete()}
            googleProvider.deleteAll()
            withContext(Dispatchers.IO) { challengeRepository.clearGoogleKey() }
            _googleSetup.value = GoogleHealthResult()
            ProviderSync.clearGarminSession();_garminConfigured.value=false
            }
            withContext(Dispatchers.IO) { java.io.File(getApplication<Application>().cacheDir,"exports").let { if(it.exists() && !it.deleteRecursively()) error("Could not clear export cache") } }
            saved["editing"]=false; modeHistories.clear(); _modes.value=store.dashboardModes(); modesLoaded=true; updateHistory(); refresh(DataMode.NONE)
        } catch(e: CancellationException) {throw e} catch(e: Exception) {_message.value="Local data could not be fully removed. Please retry."}
        finally { deletingLocal = false }
    } }
    override fun onCleared() { db.close() }
}
