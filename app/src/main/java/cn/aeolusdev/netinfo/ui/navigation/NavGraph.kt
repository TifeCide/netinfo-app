package cn.aeolusdev.netinfo.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cn.aeolusdev.netinfo.ui.screens.AboutScreen
import cn.aeolusdev.netinfo.ui.screens.StatusScreen
import cn.aeolusdev.netinfo.ui.screens.ToolsScreen
import cn.aeolusdev.netinfo.ui.screens.NetworkViewModel

sealed class Screen(val route: String, val label: String) {
    object Status : Screen("status", "状态")
    object Tools : Screen("tools", "工具")
    object About : Screen("about", "关于")
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(Screen.Status, Screen.Tools, Screen.About)

    val sharedViewModel: NetworkViewModel = viewModel()

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = when (screen) {
                                    Screen.Status -> Icons.Filled.Wifi
                                    Screen.Tools -> Icons.Filled.NetworkCheck
                                    Screen.About -> Icons.Filled.Info
                                },
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Status.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Status.route) {
                StatusScreen(viewModel = sharedViewModel)
            }
            composable(Screen.Tools.route) {
                ToolsScreen()
            }
            composable(Screen.About.route) {
                AboutScreen()
            }
        }
    }
}
