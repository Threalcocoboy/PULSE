package com.pulse.app

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.core.view.WindowCompat
import androidx.activity.compose.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.health.connect.client.PermissionController
import com.pulse.app.core.*
import com.pulse.app.integration.HealthConnectRepository
import com.pulse.app.ui.*

class MainActivity : ComponentActivity() {
    companion object {private var processOpened=false}
    private var coldLaunch=false
    override fun onCreate(savedInstanceState: Bundle?) {
        coldLaunch=savedInstanceState==null&&!processOpened;processOpened=true
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        if(android.os.Build.VERSION.SDK_INT>=29) window.isNavigationBarContrastEnforced=false
        setContent {
            val vm: PulseViewModel=viewModel()
            val p by vm.preferences.collectAsStateWithLifecycle()
            val ready by vm.ready.collectAsStateWithLifecycle()
            val setupSnapshot by vm.snapshot.collectAsStateWithLifecycle()
            val systemMotion=Settings.Global.getFloat(contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0
            CompositionLocalProvider(LocalColdLaunch provides coldLaunch) {
            PulseTheme(p.copy(animations=p.animations && systemMotion)) {
                val lightSurface = MaterialTheme.colorScheme.background.luminance() > .5f
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightSurface
                        isAppearanceLightNavigationBars = lightSurface
                    }
                }
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                    val permissionLauncher=rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.refresh() }
                    val connect: ()->Unit = {
                        if(vm.healthConnect.availability()==androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE) {
                            vm.preferences { it.copy(mode=DataMode.HEALTH_CONNECT) }; permissionLauncher.launch(vm.healthConnect.requestedPermissions())
                        } else {
                            vm.preferences { it.copy(mode=DataMode.HEALTH_CONNECT) }; vm.refresh(DataMode.HEALTH_CONNECT)
                        }
                    }
                    val privacyRequested=intent?.action in setOf("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE","android.intent.action.VIEW_PERMISSION_USAGE")
                    if(privacyRequested) PrivacyRationale { finish() }
                    else if(!ready) Box(Modifier.fillMaxSize())
                    else if(!p.onboarded&&!p.introSeen) FirstLaunchIntro(coldLaunch){vm.preferences{it.copy(introSeen=true)}}
                    else if(!p.onboarded) Onboarding(p,setupSnapshot,vm::preferences,connect,vm) { vm.preferences { it.copy(onboarded=true) } }
                    else PulseApp(vm,p,connect)
                }
            }
        }
        }
    }
}
@Composable private fun PulseApp(vm: PulseViewModel,p: Preferences,connect:()->Unit) {
    val nav=rememberNavController()
    val backstack by nav.currentBackStackEntryAsState()
    val current=backstack?.destination?.route ?: "home"
    val editing by vm.editing.collectAsStateWithLifecycle()
    val pending by vm.pendingUnlocks.collectAsStateWithLifecycle()
    val achievements by vm.achievements.collectAsStateWithLifecycle()
    val snapshotRaw by vm.snapshot.collectAsStateWithLifecycle()
    val snapshot=if(snapshotRaw.mode==p.mode) snapshotRaw else HealthSnapshot(loading=true,mode=p.mode)
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner,p.mode) {
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME && p.mode!=DataMode.DEMO)vm.foregroundRefresh()}
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(current) { if(current=="home") vm.refreshIfNeeded() }
    val motion=LocalAnimations.current
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar=remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    BackHandler(editing && current=="home") { vm.edit(false) }
    Scaffold(containerColor=MaterialTheme.colorScheme.background,snackbarHost={ SnackbarHost(snackbar) },bottomBar={
        if(current in listOf("home","history","achievements")) PulseBottomNavigation(current) {route->
            vm.edit(false);nav.navigate(route){popUpTo(nav.graph.findStartDestination().id){saveState=true};launchSingleTop=true;restoreState=true}
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),contentAlignment=Alignment.TopCenter) {
            NavHost(nav,startDestination="home",modifier=Modifier.widthIn(max=680.dp).fillMaxSize(),
                enterTransition={fadeIn(tween(if(motion) 180 else 0))+slideInHorizontally(tween(if(motion) 180 else 0)){it/24}},
                exitTransition={fadeOut(tween(if(motion) 140 else 0))},
                popEnterTransition={fadeIn(tween(if(motion) 180 else 0))},
                popExitTransition={fadeOut(tween(if(motion) 140 else 0))}) {
                composable("home") { HomeScreen(
                    vm,
                    p,
                    snapshot,
                    {nav.navigate("settings")},
                    {metric, date -> nav.navigate("metric/${metric.name}/$date")},
                    {nav.navigate("day/$it")}
                ) }
                composable("history") { RefreshSurface(snapshot.loading,{vm.refresh()}) {HistoryScreen(p,snapshot) {nav.navigate("day/$it")}} }
                composable("achievements") { CollectionScreen(vm,snapshot,achievements,{nav.navigate("year/$it")},{nav.navigate("medal/$it")},{nav.navigate("challenge/new")},{nav.navigate("challenge/detail/$it")});UnlockCelebration(pending,vm::acknowledgeUnlocks) }
                composable("challenge/new") { ChallengeMaker(vm){nav.popBackStack()} }
                composable("challenge/detail/{id}") { entry -> ChallengeDetailScreen(vm,entry.arguments?.getString("id") ?: ""){nav.popBackStack()} }
                composable("settings") { SettingsScreen(vm,p,snapshot,connect,{nav.popBackStack()},{nav.popBackStack(); vm.edit(true)}) }
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
}
