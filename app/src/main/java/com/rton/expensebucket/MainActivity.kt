package com.rton.expensebucket

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
import com.rton.expensebucket.navigation.Screen
import com.rton.expensebucket.ocr.OcrEngine
import com.rton.expensebucket.ui.screens.*
import com.rton.expensebucket.ui.theme.ExpensesTheme
import com.rton.expensebucket.ui.viewmodel.MainViewModel
import com.rton.expensebucket.ui.viewmodel.CompareMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var ocrEngine: OcrEngine

    /** Reactive state for shared image URI — drives navigation + OCR without recreate(). */
    private val _sharedImageUri = MutableStateFlow<Uri?>(null)

    /** Reactive state for notification deep-link target. */
    private val _pendingNavigateTo = MutableStateFlow<String?>(null)

    // Must register before CREATED state
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — no action needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Handle share intent (image shared from another app)
        _sharedImageUri.value = handleShareIntent(intent)

        // Handle deep-link from notification action
        _pendingNavigateTo.value = intent?.getStringExtra("navigate_to")

        setContent {
            val sharedImageUri by _sharedImageUri.collectAsState()
            val navigateTo by _pendingNavigateTo.collectAsState()

            val viewModel: MainViewModel = hiltViewModel()
            val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()

            ExpensesTheme(appTheme = currentTheme) {
                ExpensesApp(
                    ocrEngine = ocrEngine,
                    sharedImageUri = sharedImageUri,
                    navigateTo = navigateTo,
                    onSharedImageConsumed = { _sharedImageUri.value = null },
                    onNavigateToConsumed = { _pendingNavigateTo.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Handle new share intents — no recreate() needed, StateFlow drives recomposition
        val uri = handleShareIntent(intent)
        if (uri != null) {
            _sharedImageUri.value = uri
            return
        }

        // Handle deep-link from notification
        val navigateTo = intent.getStringExtra("navigate_to")
        if (navigateTo != null) {
            _pendingNavigateTo.value = navigateTo
        }
    }

    /**
     * Extract shared image from intent and copy to internal cache.
     * Copying immediately ensures we have a readable file:// URI even after
     * the temporary content URI permission expires (e.g. after recreate / config change).
     */
    private fun handleShareIntent(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) {
            return null
        }

        val sourceUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }

        return sourceUri?.let { copyToCache(it) }
    }

    /**
     * Copy a content:// URI to the app's cache directory so OCR can access it
     * without depending on the caller's temporary read permission grant.
     */
    private fun copyToCache(sourceUri: Uri): Uri? {
        return try {
            // Clean up previous shared images
            cacheDir.listFiles()
                ?.filter { it.name.startsWith("shared_ocr_") }
                ?.forEach { it.delete() }

            val destFile = File(cacheDir, "shared_ocr_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(destFile)
        } catch (e: Exception) {
            // If copy fails (e.g. permission already revoked), return null
            null
        }
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
    BottomNavItem("專案管理", Icons.Filled.Luggage,    Screen.Projects.route),
    BottomNavItem("設定",     Icons.Filled.Settings,   Screen.Settings.route)
)

/** Routes that show the bottom bar (top-level tabs only). */
private val bottomBarRoutes = bottomNavItems.map { it.route }.toSet()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesApp(
    ocrEngine: OcrEngine,
    sharedImageUri: Uri? = null,
    navigateTo: String? = null,
    onSharedImageConsumed: () -> Unit = {},
    onNavigateToConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val viewModel: MainViewModel = hiltViewModel()

    // Collect states
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val filteredTransactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val draftTransactions by viewModel.draftTransactions.collectAsStateWithLifecycle()
    val expenseCategories by viewModel.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by viewModel.incomeCategories.collectAsStateWithLifecycle()
    val periodData by viewModel.periodData.collectAsStateWithLifecycle()
    val monthlyBudget by viewModel.monthlyBudget.collectAsStateWithLifecycle()
    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val periodLabel by viewModel.currentPeriodLabel.collectAsStateWithLifecycle()
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val activeProjects by viewModel.activeProjects.collectAsStateWithLifecycle()
    val allPaymentMethods by viewModel.allPaymentMethods.collectAsStateWithLifecycle()
    val compareMode by viewModel.compareMode.collectAsStateWithLifecycle()

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

    // Navigate to DraftsScreen if opened from notification
    LaunchedEffect(navigateTo) {
        if (navigateTo == "drafts") {
            navController.navigate(Screen.Drafts.route) {
                launchSingleTop = true
            }
            onNavigateToConsumed()
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
        // Map bottom-nav routes to their tab index for directional animation
        val tabIndexMap = bottomNavItems.mapIndexed { index, item -> item.route to index }.toMap()

        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                val fromIndex = tabIndexMap[initialState.destination.route]
                val toIndex = tabIndexMap[targetState.destination.route]
                val direction = if (fromIndex != null && toIndex != null) {
                    if (toIndex > fromIndex) AnimatedContentTransitionScope.SlideDirection.Left
                    else AnimatedContentTransitionScope.SlideDirection.Right
                } else AnimatedContentTransitionScope.SlideDirection.Left
                slideIntoContainer(direction, tween(300)) + fadeIn(tween(300))
            },
            exitTransition = {
                val fromIndex = tabIndexMap[initialState.destination.route]
                val toIndex = tabIndexMap[targetState.destination.route]
                val direction = if (fromIndex != null && toIndex != null) {
                    if (toIndex > fromIndex) AnimatedContentTransitionScope.SlideDirection.Left
                    else AnimatedContentTransitionScope.SlideDirection.Right
                } else AnimatedContentTransitionScope.SlideDirection.Left
                slideOutOfContainer(direction, tween(300)) + fadeOut(tween(300))
            },
            popEnterTransition = {
                val fromIndex = tabIndexMap[initialState.destination.route]
                val toIndex = tabIndexMap[targetState.destination.route]
                val direction = if (fromIndex != null && toIndex != null) {
                    if (toIndex < fromIndex) AnimatedContentTransitionScope.SlideDirection.Right
                    else AnimatedContentTransitionScope.SlideDirection.Left
                } else AnimatedContentTransitionScope.SlideDirection.Right
                slideIntoContainer(direction, tween(300)) + fadeIn(tween(300))
            },
            popExitTransition = {
                val fromIndex = tabIndexMap[initialState.destination.route]
                val toIndex = tabIndexMap[targetState.destination.route]
                val direction = if (fromIndex != null && toIndex != null) {
                    if (toIndex < fromIndex) AnimatedContentTransitionScope.SlideDirection.Right
                    else AnimatedContentTransitionScope.SlideDirection.Left
                } else AnimatedContentTransitionScope.SlideDirection.Right
                slideOutOfContainer(direction, tween(300)) + fadeOut(tween(300))
            }
        ) {
            // ─── Home ───────────────────────────────────────────────
            composable(Screen.Home.route) {
                HomeScreen(
                    transactions = filteredTransactions,
                    categories = allCategories,
                    periodData = periodData,
                    monthlyBudget = monthlyBudget,
                    compareMode = compareMode,
                    onCompareModeChanged = { viewModel.setCompareMode(it) },
                    selectedPeriod = selectedPeriod,
                    periodLabel = periodLabel,
                    draftCount = draftTransactions.size,
                    onSelectPeriod = { viewModel.setSelectedPeriod(it) },
                    onStepPeriod = { viewModel.stepPeriod(it) },
                    onAddClick = { navController.navigate(Screen.AddTransaction.route) },
                    onTransactionClick = { transaction ->
                        navController.navigate(Screen.EditTransaction.createRoute(transaction.id))
                    },
                    onDeleteTransaction = { viewModel.deleteTransaction(it) },
                    onNavigateToDrafts = { navController.navigate(Screen.Drafts.route) },
                    onSetPeriodDate = { viewModel.setPeriodDate(it) }
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

                var existingTransaction by remember { mutableStateOf<com.rton.expensebucket.data.model.Transaction?>(null) }
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
                    }
                )
            }

            // ─── Project Detail ─────────────────────────────────────
            composable(
                route = Screen.ProjectDetail.route,
                arguments = listOf(navArgument("projectId") { type = NavType.LongType })
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getLong("projectId") ?: return@composable
                val project = allProjects.find { it.id == projectId }
                val projectTransactions by viewModel.getTransactionsByProject(projectId)
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val projectTotal by viewModel.getProjectExpenseTotal(projectId)
                    .collectAsStateWithLifecycle(initialValue = 0.0)

                ProjectDetailScreen(
                    projectId = projectId,
                    project = project,
                    transactions = projectTransactions,
                    categories = allCategories,
                    totalExpense = projectTotal ?: 0.0,
                    onBack = { navController.popBackStack() },
                    onUpdateProject = { viewModel.updateProject(it) },
                    onDeleteProject = { viewModel.deleteProject(it) },
                    onTransactionClick = { transaction ->
                        navController.navigate(Screen.EditTransaction.createRoute(transaction.id))
                    }
                )
            }

            // ─── Payment Methods ────────────────────────────────────
            composable(Screen.PaymentMethods.route) {
                PaymentMethodsScreen(
                    paymentMethods = allPaymentMethods,
                    onAdd = { viewModel.addPaymentMethod(it) },
                    onEdit = { viewModel.updatePaymentMethod(it) },
                    onDelete = { viewModel.deletePaymentMethod(it) },
                    onSetDefault = { viewModel.setDefaultPaymentMethod(it) },
                    onBack = { navController.popBackStack() }
                )
            }

            // ─── Settings ───────────────────────────────────────────
            composable(Screen.Settings.route) {
                val firstDayOfWeek by viewModel.firstDayOfWeek.collectAsState()
                SettingsScreen(
                    currentTheme = currentTheme,
                    onSetTheme = { viewModel.setTheme(it) },
                    monthlyBudget = monthlyBudget,
                    onSetMonthlyBudget = { viewModel.setMonthlyBudget(it) },
                    firstDayOfWeek = firstDayOfWeek,
                    onSetFirstDayOfWeek = { viewModel.setFirstDayOfWeek(it) },
                    onNavigateToPaymentMethods = {
                        navController.navigate(Screen.PaymentMethods.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToCategories = {
                        navController.navigate(Screen.CategoryManage.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToDrafts = {
                        navController.navigate(Screen.Drafts.route) {
                            launchSingleTop = true
                        }
                    },
                    onExportData = { ctx, uri, callback -> viewModel.exportData(ctx, uri, callback) },
                    onImportData = { ctx, uri, callback -> viewModel.importData(ctx, uri, callback) }
                )
            }

            // ─── Category Management ───────────────────────────────
            composable(Screen.CategoryManage.route) {
                CategoryManageScreen(
                    categories = allCategories,
                    onAddCategory = { viewModel.addCategory(it) },
                    onUpdateCategory = { viewModel.updateCategory(it) },
                    onUpdateCategories = { viewModel.updateCategories(it) },
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
                    onBack = {
                        onSharedImageConsumed()
                        navController.popBackStack()
                    }
                )
            }

            // ─── Drafts ─────────────────────────────────────────────
            composable(Screen.Drafts.route) {
                DraftsScreen(
                    drafts = draftTransactions,
                    categories = allCategories,
                    onConfirm = { viewModel.confirmDraft(it) },
                    onDelete = { viewModel.deleteTransaction(it) },
                    onEdit = { transaction ->
                        navController.navigate(Screen.EditTransaction.createRoute(transaction.id))
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}