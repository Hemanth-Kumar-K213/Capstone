package com.womensafety.system.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.womensafety.system.ui.theme.*

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: Any
)

@Composable
fun UserBottomNavBar(navController: NavHostController) {
    val items = listOf(
        BottomNavItem("Home", Icons.Filled.Home, UserHome),
        BottomNavItem("Contacts", Icons.Filled.Contacts, UserContacts),
        BottomNavItem("Profile", Icons.Filled.Person, UserProfile)
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
                    selectedIconColor = GradientGold,
                    selectedTextColor = GradientGold,
                    unselectedIconColor = OnSurfaceVariantDark.copy(alpha = 0.5f),
                    unselectedTextColor = OnSurfaceVariantDark.copy(alpha = 0.5f),
                    indicatorColor = GradientAmber.copy(alpha = 0.12f)
                )
            )
        }
    }
}
