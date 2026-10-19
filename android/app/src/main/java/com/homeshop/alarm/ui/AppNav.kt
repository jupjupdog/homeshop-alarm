package com.homeshop.alarm.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.homeshop.alarm.ui.detail.DetailScreen
import com.homeshop.alarm.ui.schedule.ScheduleScreen
import com.homeshop.alarm.ui.settings.SettingsScreen

private object Routes {
    const val SCHEDULE = "schedule"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{key}"
    fun detail(key: String) = "detail/${Uri.encode(key)}"
}

@Composable
fun AppNav(openProductKey: String?, onOpenedProduct: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // 알림에서 들어온 경우 상세 화면으로 바로 이동
    LaunchedEffect(openProductKey) {
        if (openProductKey != null) {
            nav.navigate(Routes.detail(openProductKey))
            onOpenedProduct()
        }
    }

    Scaffold(
        bottomBar = {
            if (route != Routes.DETAIL) {
                NavigationBar {
                    listOf(
                        Triple(Routes.SCHEDULE, "편성표", Icons.Filled.Tv),
                        Triple(Routes.SETTINGS, "관심·알림", Icons.Filled.Settings),
                    ).forEach { (r, label, icon) ->
                        NavigationBarItem(
                            selected = route == r,
                            onClick = {
                                nav.navigate(r) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.SCHEDULE, modifier = Modifier.padding(padding)) {
            composable(Routes.SCHEDULE) {
                ScheduleScreen(onOpenProduct = { nav.navigate(Routes.detail(it)) })
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("key") { type = NavType.StringType }),
            ) { entry ->
                DetailScreen(
                    productKey = Uri.decode(entry.arguments?.getString("key").orEmpty()),
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
