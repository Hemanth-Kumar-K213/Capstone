package com.womensafety.system.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.womensafety.system.ui.theme.*

@Composable
fun TCBottomNavBar(navController: NavHostController) {
    val items = listOf(
        BottomNavItem("Home", Icons.Filled.Home, TCHome),
        BottomNavItem("Profile", Icons.Filled.Person, TCProfile)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = SurfaceDark,
        contentColor = OnSurfaceDark
    ) {
        items.forEach { item ->
            val itemRoute = item.route::class.qualifiedName
            val selected = currentRoute?.contains(itemRoute ?: "") == true
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = GradientRose,
                    selectedTextColor = GradientRose,
                    unselectedIconColor = OnSurfaceVariantDark.copy(alpha = 0.5f),
                    unselectedTextColor = OnSurfaceVariantDark.copy(alpha = 0.5f),
                    indicatorColor = GradientRose.copy(alpha = 0.12f)
                )
            )
        }
    }
}
