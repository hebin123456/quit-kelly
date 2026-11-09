package com.quitkelly.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.quitkelly.app.ui.screens.ChatScreen
import com.quitkelly.app.ui.screens.DailyScreen
import com.quitkelly.app.ui.screens.SettingsScreen

private const val ROUTE_DAILY = "daily"
private const val ROUTE_CHAT = "chat"
private const val ROUTE_SETTINGS = "settings"

private data class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val destinations = listOf(
    Destination(ROUTE_DAILY, "日报", Icons.Filled.Home),
    Destination(ROUTE_CHAT, "专家", Icons.Filled.Person),
    Destination(ROUTE_SETTINGS, "设置", Icons.Filled.Settings)
)

@Composable
fun MainScreen() {
    var currentRoute by rememberSaveable { mutableStateOf(ROUTE_DAILY) }

    BackHandler(enabled = currentRoute != ROUTE_DAILY) {
        currentRoute = ROUTE_DAILY
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = { currentRoute = destination.route },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (currentRoute) {
                ROUTE_CHAT -> ChatScreen(
                    onOpenSettings = { currentRoute = ROUTE_SETTINGS }
                )

                ROUTE_SETTINGS -> SettingsScreen()

                else -> DailyScreen(
                    onOpenSettings = { currentRoute = ROUTE_SETTINGS }
                )
            }
        }
    }
}
