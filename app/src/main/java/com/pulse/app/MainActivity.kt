package com.pulse.app

import androidx.compose.animation.*
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.activity.compose.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.health.connect.client.PermissionController
import com.pulse.app.core.*
import com.pulse.app.integration.HealthConnectRepository
import com.pulse.app.integration.LiveGoalNotifications
import com.pulse.app.integration.ProgressNotifications
import com.pulse.app.integration.googlehealth.GoogleHealthPrompt
import com.pulse.app.integration.googlehealth.GoogleHealthAvailability
import com.pulse.app.update.PULSE_REPOSITORY
import com.pulse.app.update.PulseUpdates
import com.pulse.app.update.UpdateAlerts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.pulse.app.ui.*

class MainActivity : ComponentActivity() {
    private val vm: PulseViewModel by viewModels()
    private val updates: PulseUpdates by viewModels()
    private val googleAccountPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.googleAccountSelected(it)
    }
    private val googleConsent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.googleConsentFinished(it)
    }

    companion object {private var processOpened=false}
    private var coldLaunch=false
    private val dashboardRequest = MutableStateFlow(0)
    private val goalRequest = MutableStateFlow<String?>(null)
    private var showIntro by mutableStateOf(false)
    private var leftApp = false
    private var reopen = false
    private var requestingHealthAccess = false

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        leftApp = !requestingHealthAccess && !vm.googleAuthorizationInProgress
    }

    override fun onStop() {
        reopen = leftApp && !isChangingConfigurations
        showIntro = false
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        if (reopen) showIntro = true
        reopen = false
    }

    override fun onResume() {
        super.onResume()
        leftApp = false
        vm.refreshGoalNotification()
        updates.checkOnOpen()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ProgressNotifications.OPEN_DASHBOARD) {
            dashboardRequest.value++
            showIntro = true
        }
        if (intent.action == LiveGoalNotifications.OPEN_GOAL) readGoalRequest(intent)
        if (intent.action == UpdateAlerts.OPEN_UPDATES) updates.openFromNotification()
    }
    private fun readGoalRequest(intent: Intent?) {
        goalRequest.value = intent?.takeIf { it.action == LiveGoalNotifications.OPEN_GOAL }
            ?.getStringExtra(LiveGoalNotifications.GOAL_ID)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,80}")) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        coldLaunch=savedInstanceState==null&&!processOpened;processOpened=true
        super.onCreate(savedInstanceState)
        if (GoogleHealthAvailability.enabled) lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                vm.googlePrompt.collect { prompt ->
                    if (prompt == null || !vm.consumeGooglePrompt(prompt)) return@collect
                    try {
                        when (prompt) {
                            is GoogleHealthPrompt.ChooseAccount -> googleAccountPicker.launch(prompt.intent)
                            is GoogleHealthPrompt.Consent -> googleConsent.launch(IntentSenderRequest.Builder(prompt.intent.intentSender).build())
                        }
                    } catch (_: android.content.ActivityNotFoundException) {
                        vm.googlePromptFailed()
                    } catch (_: SecurityException) {
                        vm.googlePromptFailed()
                    }
                }
            }
        }
        showIntro = savedInstanceState == null
        if (intent?.action == ProgressNotifications.OPEN_DASHBOARD) dashboardRequest.value++
        if (savedInstanceState == null) readGoalRequest(intent)
        if (savedInstanceState == null && intent?.action == UpdateAlerts.OPEN_UPDATES) updates.openFromNotification()
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        if(android.os.Build.VERSION.SDK_INT>=29) window.isNavigationBarContrastEnforced=false
        setContent {
            val p by vm.preferences.collectAsStateWithLifecycle()
            val ready by vm.ready.collectAsStateWithLifecycle()
            val setupSnapshot by vm.snapshot.collectAsStateWithLifecycle()
            val privacyRequested = intent?.action in setOf("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE", "android.intent.action.VIEW_PERMISSION_USAGE")
            CompositionLocalProvider(LocalColdLaunch provides coldLaunch) {
            PulseTheme(p) {
                val motionEnabled = LocalAnimations.current
                val introVisible = showIntro && ready && !privacyRequested && motionEnabled
                LaunchedEffect(ready, motionEnabled) { if (ready && !motionEnabled) showIntro = false }
                val lightSurface = !introVisible && MaterialTheme.colorScheme.background.luminance() > .5f
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightSurface
                        isAppearanceLightNavigationBars = lightSurface
                    }
                }
                Box(Modifier.fillMaxSize()) {
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                    val permissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
                        requestingHealthAccess = false
                        vm.checkHealthConnect()
                    }
                    val requestHealth: (Set<String>) -> Unit = { permissions ->
                        if (vm.healthConnect.availability() == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE && permissions.isNotEmpty()) {
                            requestingHealthAccess = true
                            permissionLauncher.launch(permissions)
                        } else vm.checkHealthConnect()
                    }
                    if(privacyRequested) PrivacyRationale { finish() }
                    else if (!ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        BrandLogo(Modifier.width(220.dp).height(52.dp), loading = true)
                    }
                    else {
                        PulseSwap(p.onboarded, Modifier.fillMaxSize(), label = "Onboarding to Home", distance = 24) { onboarded ->
                            if (onboarded) {
                                val openDashboard by dashboardRequest.collectAsStateWithLifecycle()
                                val openGoal by goalRequest.collectAsStateWithLifecycle()
                                PulseApp(vm, p, setupSnapshot, requestHealth, openDashboard, updates, openGoal) {
                                    goalRequest.value = null
                                }
                            } else Onboarding(p, setupSnapshot, vm) { requestHealth(HealthConnectRepository.permissions) }
                        }
                    }
                }
                if (introVisible) LaunchIntro { showIntro = false }
                }
            }
        }
        }
    }
}
@Composable internal fun PulseApp(vm: PulseViewModel,p: Preferences,snapshotRaw: HealthSnapshot,connect:(Set<String>)->Unit,openDashboard:Int,
    updates: PulseUpdates = androidx.lifecycle.viewmodel.compose.viewModel(), openGoal: String? = null,
    onGoalOpened: () -> Unit = {}) {
    val nav=rememberNavController()
    var tab by rememberSaveable { mutableStateOf("home") }
    var outgoingTab by remember { mutableStateOf<String?>(null) }
    val tabStates = rememberSaveableStateHolder()
    val motion = LocalAnimations.current
    fun selectTab(destination: String) {
        if (tab != destination) {
            outgoingTab = tab.takeIf { motion }
            tab = destination
        }
    }
    LaunchedEffect(openDashboard) {
        if (openDashboard > 0) {
            vm.edit(false)
            selectTab("home")
            nav.navigate("home") { popUpTo(nav.graph.findStartDestination().id); launchSingleTop = true }
        }
    }
    LaunchedEffect(openGoal) {
        openGoal?.let { id ->
            vm.edit(false)
            nav.navigate("goal/$id") { launchSingleTop = true }
            onGoalOpened()
        }
    }
    val backstack by nav.currentBackStackEntryAsState()
    val current=backstack?.destination?.route ?: "home"
    fun openPage(route: String) {
        val destination = if (route == "about") "settings/about" else route
        if (nav.currentDestination?.route != destination) {
            nav.navigate(destination) { launchSingleTop = true }
        }
        if (route == "updates") updates.check()
    }
    val isTab = current == "home"
    val editing by vm.editing.collectAsStateWithLifecycle()
    val pending by vm.pendingUnlocks.collectAsStateWithLifecycle()
    val achievements by vm.achievements.collectAsStateWithLifecycle()
    val snapshot=if(snapshotRaw.mode==p.mode) snapshotRaw else HealthSnapshot(loading=true,mode=p.mode)
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner,p.mode) {
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME && p.mode!=DataMode.NONE)vm.foregroundRefresh()}
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(current, tab) { if (isTab && tab == "home") vm.refreshIfNeeded() }
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar=remember { SnackbarHostState() }
    val updateRequest by updates.openRequested.collectAsStateWithLifecycle()
    val updateOffer by updates.popup.collectAsStateWithLifecycle()
    LaunchedEffect(updateRequest) {
        if (updateRequest) {
            openPage("updates")
            updates.consumeOpenRequest()
        }
    }
    if (current != "updates" && !updateRequest) updateOffer?.let { offer ->
        AlertDialog(
            onDismissRequest = { updates.dismissOffer(offer.code) },
            title = { Text("PULSE ${offer.version} is available") },
            text = { Text("Open Updates to review what's new and install the update.") },
            confirmButton = { TextButton({
                updates.dismissOffer(offer.code)
                openPage("updates")
            }) { Text("View update") } },
            dismissButton = { TextButton({ updates.dismissOffer(offer.code) }) { Text("Later") } }
        )
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    BackHandler(isTab && tab != "home") { selectTab("home") }
    BackHandler(editing && isTab && tab == "home") { vm.edit(false) }
    Scaffold(containerColor=MaterialTheme.colorScheme.background,snackbarHost={
        SnackbarHost(snackbar, Modifier.padding(bottom = if (isTab) PulseNavHeight else 0.dp))
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),contentAlignment=Alignment.TopCenter) {
            SharedTransitionLayout(Modifier.widthIn(max = 680.dp).fillMaxSize()) {
            val shared = this
            CompositionLocalProvider(LocalPulseMenu provides { manageModes ->
                PulseOverflow(::openPage, BuildConfig.GITHUB_REPOSITORY_VERIFIED, manageModes)
            }) {
            NavHost(nav,startDestination="home",modifier=Modifier.fillMaxSize(),
                enterTransition={PulseMotion.enter(motion, back = false)},
                exitTransition={PulseMotion.exit(motion, back = false)},
                popEnterTransition={PulseMotion.enter(motion, back = true)},
                popExitTransition={PulseMotion.exit(motion, back = true)}) {
                composable("home") {
                    CompositionLocalProvider(LocalSharedMotion provides shared, LocalSharedPage provides this) {
                        PulseTabPages(tab, outgoingTab, tabStates, onSettled = { from, to ->
                            if (outgoingTab == from && tab == to) outgoingTab = null
                        }) { route ->
                            when (route) {
                                "home" -> HomeScreen(vm, p, snapshot, { nav.navigate("settings") },
                                    { metric, date -> nav.navigate(if (metric == Metric.GOAL) "settings/goals" else "metric/${metric.name}/$date") },
                                    { nav.navigate("day/$it") })
                                "history" -> RefreshSurface(snapshot.loading, { vm.refresh() }) {
                                    HistoryScreen(p, snapshot) { nav.navigate("day/$it") }
                                }
                                "achievements" -> CollectionScreen(vm, snapshot, achievements,
                                    { nav.navigate("year/$it") }, { nav.navigate("medal/$it") },
                                    { nav.navigate("challenge/new") }, { nav.navigate("challenge/detail/$it") })
                                else -> Unit
                            }
                        }
                    }
                }
                composable("challenge/new") { ChallengeMaker(vm){nav.popBackStack()} }
                composable("challenge/detail/{id}") { entry ->
                    CompositionLocalProvider(LocalSharedMotion provides shared, LocalSharedPage provides this) {
                        ChallengeDetailScreen(vm,entry.arguments?.getString("id") ?: ""){nav.popBackStack()}
                    }
                }
                val categoryLink: (Int) -> Unit = { index ->
                    openPage(if (settingsCategories.getOrNull(index) == "About PULSE") "about" else "settings/section/$index")
                }
                composable("settings") {
                    SettingsScreen(vm,p,snapshot,connect, openCategory = categoryLink, openPage = ::openPage) { nav.popBackStack() }
                }
                composable("settings/goals") {
                    SettingsScreen(vm,p,snapshot,connect,initialCategory = "Profile & Goals", openPage = ::openPage) { nav.popBackStack() }
                }
                composable("goal/{id}") { entry ->
                    LiveGoalDetail(entry.arguments?.getString("id").orEmpty(), p, snapshot,
                        { openPage("settings/goals") }) { nav.popBackStack() }
                }
                composable("settings/about") {
                    SettingsScreen(vm,p,snapshot,connect,initialCategory = "About PULSE", openPage = ::openPage) { nav.popBackStack() }
                }
                composable("settings/section/{index}") { entry ->
                    val category = entry.arguments?.getString("index")?.toIntOrNull()?.let { settingsCategories.getOrNull(it) }.orEmpty()
                    SettingsScreen(vm,p,snapshot,connect,initialCategory = category, openCategory = categoryLink, openPage = ::openPage) { nav.popBackStack() }
                }
                composable("privacy") {
                    PulseReadingPage("Privacy Policy", "PRIVACY.md", { nav.popBackStack() },
                        ("Read current policy on GitHub" to "$PULSE_REPOSITORY/blob/main/PRIVACY.md").takeIf { BuildConfig.GITHUB_REPOSITORY_VERIFIED })
                }
                composable("terms") {
                    PulseReadingPage("Terms of Service", "TERMS.md", { nav.popBackStack() },
                        ("Read current terms on GitHub" to "$PULSE_REPOSITORY/blob/main/TERMS.md").takeIf { BuildConfig.GITHUB_REPOSITORY_VERIFIED })
                }
                composable("licenses") { PulseReadingPage("Third-party licenses", "NOTICE.md", { nav.popBackStack() }) }
                composable("whats-new") {
                    PulseReadingPage("What's New · ${BuildConfig.VERSION_NAME}", "RELEASE_NOTES.md", { nav.popBackStack() },
                        ("View this release on GitHub" to "$PULSE_REPOSITORY/releases/tag/v${BuildConfig.VERSION_NAME}").takeIf { BuildConfig.GITHUB_REPOSITORY_VERIFIED })
                }
                composable("updates") { UpdateScreen(updates) { nav.popBackStack() } }
                composable("day/{date}") { entry ->
                    val date = java.time.LocalDate.parse(entry.arguments?.getString("date") ?: java.time.LocalDate.now().toString())
                    DailyScreen(date, p, snapshot, {nav.popBackStack()}, {metric, d -> nav.navigate("metric/${metric.name}/$d")})
                }
                composable("metric/{metric}/{date}") { entry ->
                    val metric = Metric.valueOf(entry.arguments?.getString("metric") ?: Metric.STEPS.name)
                    val date = java.time.LocalDate.parse(entry.arguments?.getString("date") ?: java.time.LocalDate.now().toString())
                    MetricDetailScreen(metric, date, p, snapshot) { nav.popBackStack() }
                }
                composable("year/{year}") { entry -> MonthlyYearScreen(entry.arguments?.getString("year")?.toIntOrNull() ?: java.time.Year.now().value,snapshot,achievements,{nav.popBackStack()}) {nav.navigate("medal/$it")} }
                composable("medal/{month}") { entry -> MedalScreen(java.time.YearMonth.parse(entry.arguments?.getString("month")),snapshot,achievements) {nav.popBackStack()} }
            }
            }
            }
            AnimatedVisibility(isTab, Modifier.align(Alignment.BottomCenter),
                enter = if (motion) fadeIn(PulseMotion.tween(true)) + slideInVertically(PulseMotion.tween(true)) { it / 2 } else EnterTransition.None,
                exit = if (motion) fadeOut(PulseMotion.tween(true, PulseMotion.Selection)) + slideOutVertically(PulseMotion.tween(true)) { it / 2 } else ExitTransition.None) {
                PulseBottomNavigation(if (isTab) tab else current) { route ->
                    if (nav.currentDestination?.route == "home" && route != tab) {
                        vm.edit(false)
                        selectTab(route)
                    }
                }
            }
            if (isTab || current == "challenge/detail/{id}") UnlockCelebration(pending, vm::acknowledgeUnlocks)
        }
    }
}
