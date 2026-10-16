package com.selfwallet.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.selfwallet.app.ui.screens.EditTransactionScreen
import com.selfwallet.app.ui.screens.HomeScreen
import com.selfwallet.app.ui.screens.ManageScreen
import com.selfwallet.app.ui.screens.OnboardingScreen
import com.selfwallet.app.ui.screens.ReportsScreen
import com.selfwallet.app.ui.screens.SettingsScreen
import com.selfwallet.app.ui.screens.TransactionsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("transactions", "Activity", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong),
    Tab("reports", "Reports", Icons.Outlined.PieChart, Icons.Filled.PieChart),
    Tab("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
)

object Routes {
    fun edit(id: Long = 0, type: String? = null) = "edit/$id" + (type?.let { "?type=$it" } ?: "")
    fun transactions(filter: String? = null) = "transactions" + (filter?.let { "?filter=$it" } ?: "")
    fun manage(what: String) = "manage/$what"
}

@Composable
fun AppRoot(vm: WalletViewModel, state: WalletState, openTxId: Long, onOpenTxConsumed: () -> Unit) {
    if (!state.loaded) {
        Box(Modifier.fillMaxSize())
        return
    }
    Crossfade(targetState = state.settings.onboardingDone, label = "onboarding") { done ->
        if (!done) {
            OnboardingScreen(vm = vm, state = state)
        } else {
            MainShell(vm, state, openTxId, onOpenTxConsumed)
        }
    }
}

@Composable
private fun MainShell(vm: WalletViewModel, state: WalletState, openTxId: Long, onOpenTxConsumed: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val showBar = tabs.any { route.startsWith(it.route) }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is UiEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }
    LaunchedEffect(openTxId) {
        if (openTxId != 0L) {
            nav.navigate(Routes.edit(openTxId))
            onOpenTxConsumed()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    tabs.forEach { tab ->
                        val selected = route.startsWith(tab.route)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.goToTab(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable("home") {
                HomeScreen(
                    state = state,
                    onAdd = { nav.navigate(Routes.edit(type = it)) },
                    onOpen = { nav.navigate(Routes.edit(it)) },
                    onSeeAll = { nav.goToTab("transactions") },
                    onReview = { nav.navigate(Routes.transactions("review")) { launchSingleTop = true } },
                    onDuplicates = { nav.navigate(Routes.transactions("dups")) { launchSingleTop = true } },
                    onClaims = { nav.navigate(Routes.transactions("claims")) { launchSingleTop = true } },
                    onBudgets = { nav.navigate(Routes.manage("budgets")) },
                    onFixRates = { nav.navigate(Routes.manage("rates")) },
                    onToggleHide = { vm.setHideAmounts(!state.settings.hideAmounts) },
                )
            }
            composable(
                "transactions?filter={filter}",
                arguments = listOf(navArgument("filter") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                TransactionsScreen(
                    state = state,
                    initialFilter = entry.arguments?.getString("filter"),
                    onAdd = { nav.navigate(Routes.edit()) },
                    onOpen = { nav.navigate(Routes.edit(it)) },
                    onDelete = { vm.deleteTransaction(it) },
                    onUndoDelete = { vm.restoreTransaction(it) },
                    onMarkReviewed = { vm.markReviewed(it) },
                    onToggleKind = { vm.toggleSpendKind(it) },
                    onSetClaim = { list, status -> vm.setClaimStatus(list, status) },
                    snackbar = snackbar,
                )
            }
            composable("reports") {
                ReportsScreen(
                    state = state,
                    onExport = { uri, rows -> vm.exportCsv(uri, rows) },
                    onExportPdf = { uri, info, rows, mark -> vm.exportPdf(uri, info, rows, mark) },
                )
            }
            composable("settings") {
                SettingsScreen(vm = vm, state = state, onManage = { nav.navigate(Routes.manage(it)) })
            }
            composable(
                "edit/{id}?type={type}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                EditTransactionScreen(
                    vm = vm,
                    state = state,
                    id = entry.arguments?.getLong("id") ?: 0L,
                    initialType = entry.arguments?.getString("type"),
                    onDone = { nav.popBackStack() },
                    onManage = { nav.navigate(Routes.manage(it)) },
                )
            }
            composable("manage/{what}") { entry ->
                ManageScreen(
                    vm = vm,
                    state = state,
                    what = entry.arguments?.getString("what") ?: "categories",
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.goToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
