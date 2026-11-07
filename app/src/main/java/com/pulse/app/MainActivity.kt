package com.pulse.app

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
import androidx.compose.ui.platform.LocalContext
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        if(android.os.Build.VERSION.SDK_INT>=29) window.isNavigationBarContrastEnforced=false
        setContent {
            val vm: PulseViewModel=viewModel()
            val p by vm.preferences.collectAsStateWithLifecycle()
            val ready by vm.ready.collectAsStateWithLifecycle()
            val systemMotion=Settings.Global.getFloat(contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0
            val systemDark=androidx.compose.foundation.isSystemInDarkTheme()
            SideEffect {
                val light=when(p.theme){"LIGHT"->true;"SYSTEM"->!systemDark;else->false}
                WindowCompat.getInsetsController(window,window.decorView).apply {isAppearanceLightStatusBars=light;isAppearanceLightNavigationBars=light}
            }
            PulseTheme(p.copy(animations=p.animations && systemMotion)) {
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                    val permissionLauncher=rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.refresh() }
                    val connect: ()->Unit = {
                        if(vm.healthConnect.availability()==androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE) {
                            vm.preferences { it.copy(mode=DataMode.HEALTH_CONNECT) }; permissionLauncher.launch(HealthConnectRepository.permissions)
                        } else {
                            vm.preferences { it.copy(mode=DataMode.HEALTH_CONNECT) }; vm.refresh(DataMode.HEALTH_CONNECT)
                        }
                    }
                    val privacyRequested=intent?.action in setOf("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE","android.intent.action.VIEW_PERMISSION_USAGE")
                    if(privacyRequested) PrivacyRationale { finish() }
                    else if(!ready) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { DotWordmark() }
                    else if(!p.onboarded) Onboarding(p,vm::preferences,connect) { vm.preferences { it.copy(onboarded=true) } }
                    else PulseApp(vm,p,connect)
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
    val snapshotRaw by vm.snapshot.collectAsStateWithLifecycle()
    val snapshot=if(snapshotRaw.mode==p.mode) snapshotRaw else HealthSnapshot(loading=true,mode=p.mode)
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner,p.mode) {
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME && p.mode==DataMode.HEALTH_CONNECT)vm.refresh(p.mode)}
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer)}
    }
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar=remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    BackHandler(editing && current=="home") { vm.edit(false) }
    Scaffold(containerColor=MaterialTheme.colorScheme.background,snackbarHost={ SnackbarHost(snackbar) },bottomBar={
        if(current in listOf("home","history","achievements")) PulseBottomNavigation(current) {route->
            vm.edit(false);nav.navigate(route){popUpTo(nav.graph.findStartDestination().id){saveState=true};launchSingleTop=true;restoreState=true}
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter) {
            NavHost(nav,startDestination="home",modifier=Modifier.widthIn(max=680.dp).fillMaxSize()) {
                composable("home") { HomeScreen(
                    vm,
                    p,
                    snapshot,
                    {nav.navigate("settings")},
                    {metric, date -> nav.navigate("metric/${metric.name}/$date")},
                    {nav.navigate("day/$it")}
                ) }
                composable("history") { HistoryScreen(p,snapshot) {nav.navigate("day/$it")} }
                composable("achievements") { AchievementsScreen(snapshot) {nav.navigate("medal/$it")} }
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
                composable("medal/{month}") { entry -> MedalScreen(java.time.YearMonth.parse(entry.arguments?.getString("month")),snapshot) {nav.popBackStack()} }
            }
        }
    }
}
