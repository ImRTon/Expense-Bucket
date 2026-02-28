package com.rton.expanses

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rton.expanses.navigation.Screen
import com.rton.expanses.ocr.OcrEngine
import com.rton.expanses.ui.screens.*
import com.rton.expanses.ui.theme.ExpansesTheme
import com.rton.expanses.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var ocrEngine: OcrEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle share intent (image shared from another app)
        val sharedImageUri = handleShareIntent(intent)

        setContent {
            ExpansesTheme {
                ExpansesApp(
                    ocrEngine = ocrEngine,
                    sharedImageUri = sharedImageUri
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle new share intents when activity already exists
        val uri = handleShareIntent(intent)
        if (uri != null) {
            setIntent(intent)
            recreate()
        }
    }

    private fun handleShareIntent(intent: Intent?): Uri? {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            return intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
        return null
    }
}

/**
 * Bottom navigation tab definitions.
 */
data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem("首頁",     Icons.Filled.Home,       Screen.Home.route),
    BottomNavItem("統計",     Icons.Filled.PieChart,   Screen.Statistics.route),
    BottomNavItem("支付工具", Icons.Filled.CreditCard, Screen.PaymentMethods.route),
    BottomNavItem("設定",     Icons.Filled.Settings,   Screen.Settings.route)
)

/** Routes that show the bottom bar (top-level tabs only). */
private val bottomBarRoutes = bottomNavItems.map { it.route }.toSet()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpansesApp(
    ocrEngine: OcrEngine,
    sharedImageUri: Uri? = null
) {
    val navController = rememberNavController()
    val viewModel: MainViewModel = hiltViewModel()

    // Collect states
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val draftTransactions by viewModel.draftTransactions.collectAsStateWithLifecycle()
    val expenseCategories by viewModel.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by viewModel.incomeCategories.collectAsStateWithLifecycle()
    val monthlyExpense by viewModel.monthlyExpense.collectAsStateWithLifecycle()
    val monthlyIncome by viewModel.monthlyIncome.collectAsStateWithLifecycle()
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val activeProjects by viewModel.activeProjects.collectAsStateWithLifecycle()
    val allPaymentMethods by viewModel.allPaymentMethods.collectAsStateWithLifecycle()

    val allCategories = remember(expenseCategories, incomeCategories) {
        expenseCategories + incomeCategories
    }

    // Navigate to OCR preview if image was shared
    LaunchedEffect(sharedImageUri) {
        if (sharedImageUri != null) {
            navController.navigate(Screen.OcrPreview.route) {
                launchSingleTop = true
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomBarRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = navBackStackEntry?.destination?.hierarchy?.any {
                            it.route == item.route
                        } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(item.icon, contentDescription = item.label)
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    tween(300)
                ) + fadeIn(tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    tween(300)
                ) + fadeOut(tween(300))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    tween(300)
                ) + fadeIn(tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    tween(300)
                ) + fadeOut(tween(300))
            }
        ) {
            // ─── Home ───────────────────────────────────────────────
            composable(Screen.Home.route) {
                HomeScreen(
                    transactions = transactions,
                    categories = allCategories,
                    monthlyExpense = monthlyExpense,
                    monthlyIncome = monthlyIncome,
                    draftCount = draftTransactions.size,
                    onAddClick = { navController.navigate(Screen.AddTransaction.route) },
                    onTransactionClick = { transaction ->
                        navController.navigate(Screen.EditTransaction.createRoute(transaction.id))
                    },
                    onDeleteTransaction = { viewModel.deleteTransaction(it) },
                    onNavigateToProjects = { navController.navigate(Screen.Projects.route) },
                    onNavigateToDrafts = { navController.navigate(Screen.Drafts.route) }
                )
            }

            // ─── Add Transaction ────────────────────────────────────
            composable(Screen.AddTransaction.route) {
                AddTransactionScreen(
                    categories = allCategories,
                    projects = activeProjects,
                    paymentMethods = allPaymentMethods,
                    onSave = { transaction -> viewModel.addTransaction(transaction) },
                    onBack = { navController.popBackStack() }
                )
            }

            // ─── Edit Transaction ───────────────────────────────────
            composable(
                route = Screen.EditTransaction.route,
                arguments = listOf(navArgument("transactionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val transactionId = backStackEntry.arguments?.getLong("transactionId") ?: return@composable

                var existingTransaction by remember { mutableStateOf<com.rton.expanses.data.model.Transaction?>(null) }
                var isLoading by remember { mutableStateOf(true) }

                LaunchedEffect(transactionId) {
                    existingTransaction = viewModel.getTransactionById(transactionId)
                    isLoading = false
                }

                if (!isLoading && existingTransaction != null) {
                    AddTransactionScreen(
                        categories = allCategories,
                        projects = activeProjects,
                        paymentMethods = allPaymentMethods,
                        existingTransaction = existingTransaction,
                        onSave = { transaction -> viewModel.updateTransaction(transaction) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // ─── Statistics ─────────────────────────────────────────
            composable(Screen.Statistics.route) {
                StatisticsScreen(
                    transactions = transactions,
                    categories = allCategories
                )
            }

            // ─── Projects (Travel Mode) ────────────────────────────
            composable(Screen.Projects.route) {
                ProjectsScreen(
                    projects = allProjects,
                    onAddProject = { viewModel.addProject(it) },
                    onProjectClick = { projectId ->
                        navController.navigate(Screen.ProjectDetail.createRoute(projectId))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // ─── Project Detail ─────────────────────────────────────
            composable(
                route = Screen.ProjectDetail.route,
                arguments = listOf(navArgument("projectId") { type = NavType.LongType })
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getLong("projectId") ?: return@composable
                val projectTransactions by viewModel.getTransactionsByProject(projectId)
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val projectTotal by viewModel.getProjectExpenseTotal(projectId)
                    .collectAsStateWithLifecycle(initialValue = 0.0)

                ProjectDetailScreen(
                    projectId = projectId,
                    transactions = projectTransactions,
                    categories = allCategories,
                    totalExpense = projectTotal ?: 0.0,
                    onBack = { navController.popBackStack() }
                )
            }

            // ─── Payment Methods ────────────────────────────────────
            composable(Screen.PaymentMethods.route) {
                PaymentMethodsScreen(
                    paymentMethods = allPaymentMethods,
                    onAdd = { viewModel.addPaymentMethod(it) },
                    onDelete = { viewModel.deletePaymentMethod(it) },
                    onSetDefault = { viewModel.setDefaultPaymentMethod(it) }
                )
            }

            // ─── Settings ───────────────────────────────────────────
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToPaymentMethods = {
                        navController.navigate(Screen.PaymentMethods.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToCategories = {
                        navController.navigate(Screen.CategoryManage.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // ─── Category Management ───────────────────────────────
            composable(Screen.CategoryManage.route) {
                CategoryManageScreen(
                    categories = allCategories,
                    onAddCategory = { viewModel.addCategory(it) },
                    onUpdateCategory = { viewModel.updateCategory(it) },
                    onDeleteCategory = { viewModel.deleteCategory(it) },
                    onBack = { navController.popBackStack() }
                )
            }

            // ─── OCR Preview ────────────────────────────────────────
            composable(Screen.OcrPreview.route) {
                OcrPreviewScreen(
                    imageUri = sharedImageUri,
                    categories = allCategories,
                    paymentMethods = allPaymentMethods,
                    ocrEngine = ocrEngine,
                    onSave = { viewModel.addTransaction(it) },
                    onBack = { navController.popBackStack() }
                )
            }

            // ─── Drafts ─────────────────────────────────────────────
            composable(Screen.Drafts.route) {
                DraftsScreen(
                    drafts = draftTransactions,
                    categories = allCategories,
                    onConfirm = { viewModel.confirmDraft(it) },
                    onDelete = { viewModel.deleteTransaction(it) },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}