package cn.aeolusdev.netinfo.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cn.aeolusdev.netinfo.ui.networkTypeIcon
import cn.aeolusdev.netinfo.ui.screens.AboutScreen
import cn.aeolusdev.netinfo.ui.screens.ExternalInfoToolScreen
import cn.aeolusdev.netinfo.ui.screens.LanScannerToolScreen
import cn.aeolusdev.netinfo.ui.screens.NetworkViewModel
import cn.aeolusdev.netinfo.ui.screens.PingToolScreen
import cn.aeolusdev.netinfo.ui.screens.StatusScreen
import cn.aeolusdev.netinfo.ui.screens.ToolsScreen
import cn.aeolusdev.netinfo.ui.screens.ToolsViewModel
import cn.aeolusdev.netinfo.ui.screens.TracerouteToolScreen

sealed class Screen(val route: String, val label: String) {
    data object Status : Screen("status", "状态")
    data object Tools : Screen("tools", "工具")
    data object About : Screen("about", "关于")
}

sealed class ToolScreen(val route: String, val label: String) {
    data object Home : ToolScreen(Screen.Tools.route, "工具")
    data object Ping : ToolScreen("tools/ping", "Ping")
    data object ExternalInfo : ToolScreen("tools/external", "外网信息")
    data object LanScanner : ToolScreen("tools/lan", "局域网扫描仪")
    data object Traceroute : ToolScreen("tools/traceroute", "路由跟踪")

    companion object {
        val all = listOf(Home, Ping, ExternalInfo, LanScanner, Traceroute)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val sharedViewModel: NetworkViewModel = viewModel()
    val toolsViewModel: ToolsViewModel = viewModel()

    val networkInfo by sharedViewModel.networkInfo.collectAsState()
    val pingState by toolsViewModel.pingState.collectAsState()
    val lanScanState by toolsViewModel.lanScanState.collectAsState()
    val traceState by toolsViewModel.traceState.collectAsState()

    val topLevelItems = listOf(Screen.Status, Screen.Tools, Screen.About)
    val currentTopLevel = when {
        currentRoute == Screen.Status.route -> Screen.Status
        currentRoute == Screen.About.route -> Screen.About
        currentRoute?.startsWith(Screen.Tools.route) == true -> Screen.Tools
        else -> Screen.Status
    }

    val currentToolScreen = ToolScreen.all.firstOrNull { screen ->
        currentRoute == screen.route
    }

    val isToolDetailScreen = currentToolScreen != null && currentToolScreen != ToolScreen.Home
    val appBarTitle = currentToolScreen?.label ?: currentTopLevel.label

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        ),
        topBar = {
            TopAppBar(
                title = { Text(appBarTitle) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    if (isToolDetailScreen) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                },
                actions = {
                    if (currentTopLevel == Screen.Status && currentToolScreen == null) {
                        IconButton(
                            onClick = { sharedViewModel.setPaused(!networkInfo.updatesPaused) }
                        ) {
                            Icon(
                                imageVector = if (networkInfo.updatesPaused) {
                                    Icons.Filled.PlayArrow
                                } else {
                                    Icons.Filled.Pause
                                },
                                contentDescription = if (networkInfo.updatesPaused) {
                                    "恢复刷新"
                                } else {
                                    "暂停刷新"
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!isToolDetailScreen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp
                ) {
                    topLevelItems.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = when (screen) {
                                        Screen.Status -> networkTypeIcon(networkInfo)
                                        Screen.Tools -> Icons.Filled.Build
                                        Screen.About -> Icons.Filled.Info
                                    },
                                    contentDescription = screen.label
                                )
                            },
                            label = {
                                Text(
                                    text = if (screen == Screen.Status && networkInfo.updatesPaused) {
                                        "${screen.label}·暂停"
                                    } else {
                                        screen.label
                                    }
                                )
                            },
                            selected = when (screen) {
                                Screen.Status -> currentRoute == Screen.Status.route
                                Screen.Tools -> currentRoute?.startsWith(Screen.Tools.route) == true
                                Screen.About -> currentRoute == Screen.About.route
                            },
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Status.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(
                route = Screen.Status.route,
                enterTransition = { forwardEnter() },
                exitTransition = { forwardExit() },
                popEnterTransition = { backwardEnter() },
                popExitTransition = { backwardExit() }
            ) {
                StatusScreen(viewModel = sharedViewModel)
            }

            composable(
                route = ToolScreen.Home.route,
                enterTransition = { forwardEnter() },
                exitTransition = { forwardExit() },
                popEnterTransition = { backwardEnter() },
                popExitTransition = { backwardExit() }
            ) {
                ToolsScreen(
                    pingState = pingState,
                    lanScanState = lanScanState,
                    traceState = traceState,
                    onOpenPing = { navController.navigate(ToolScreen.Ping.route) },
                    onOpenExternalInfo = { navController.navigate(ToolScreen.ExternalInfo.route) },
                    onOpenLanScanner = { navController.navigate(ToolScreen.LanScanner.route) },
                    onOpenTraceroute = { navController.navigate(ToolScreen.Traceroute.route) }
                )
            }

            composable(
                route = ToolScreen.Ping.route,
                enterTransition = { forwardEnter() },
                exitTransition = { forwardExit() },
                popEnterTransition = { backwardEnter() },
                popExitTransition = { backwardExit() }
            ) {
                PingToolScreen(
                    state = pingState,
                    onHostChange = toolsViewModel::updatePingHost,
                    onStartPing = toolsViewModel::startPing
                )
            }

            composable(
                route = ToolScreen.ExternalInfo.route,
                enterTransition = { forwardEnter() },
                exitTransition = { forwardExit() },
                popEnterTransition = { backwardEnter() },
                popExitTransition = { backwardExit() }
            ) {
                ExternalInfoToolScreen(
                    networkInfo = networkInfo,
                    onRefresh = sharedViewModel::refresh
                )
            }

            composable(
                route = ToolScreen.LanScanner.route,
                enterTransition = { forwardEnter() },
                exitTransition = { forwardExit() },
                popEnterTransition = { backwardEnter() },
                popExitTransition = { backwardExit() }
            ) {
                LanScannerToolScreen(
                    networkInfo = networkInfo,
                    state = lanScanState,
                    onStartScan = toolsViewModel::startLanScan
                )
            }

            composable(
                route = ToolScreen.Traceroute.route,
                enterTransition = { forwardEnter() },
                exitTransition = { forwardExit() },
                popEnterTransition = { backwardEnter() },
                popExitTransition = { backwardExit() }
            ) {
                TracerouteToolScreen(
                    state = traceState,
                    onHostChange = toolsViewModel::updateTraceHost,
                    onStartTrace = toolsViewModel::startTraceroute
                )
            }

            composable(
                route = Screen.About.route,
                enterTransition = { forwardEnter() },
                exitTransition = { forwardExit() },
                popEnterTransition = { backwardEnter() },
                popExitTransition = { backwardExit() }
            ) {
                AboutScreen()
            }
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.forwardEnter() =
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(280)
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.forwardExit() =
    slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(280)
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.backwardEnter() =
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(280)
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.backwardExit() =
    slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(280)
    )
