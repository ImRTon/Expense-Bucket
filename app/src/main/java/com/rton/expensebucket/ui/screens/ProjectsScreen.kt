package com.rton.expensebucket.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.data.model.Project
import com.rton.expensebucket.ui.components.ProjectFormDialog
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import com.rton.expensebucket.ui.components.ProjectTimelineChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    projects: List<Project>,
    expenseTotals: Map<Long, Double>,
    onAddProject: (Project) -> Unit,
    onProjectClick: (Long) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    val sortedProjects = remember(projects) {
        projects.sortedByDescending { it.startDate ?: it.createdAt }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "旅行專案",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, "新增") },
                text = { Text("新專案") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        if (projects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Luggage,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "尚無旅行專案",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        "建立一個專案開始追蹤旅途花費",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    ProjectTimelineChart(
                        projects = sortedProjects,
                        expenseTotals = expenseTotals,
                        onProjectFocused = { focusedId ->
                            if (focusedId != null) {
                                val idx = sortedProjects.indexOfFirst { it.id == focusedId }
                                if (idx >= 0) {
                                    scope.launch {
                                        // +1 because TimelineChart is item 0
                                        listState.animateScrollToItem(idx + 1)
                                    }
                                }
                            }
                        }
                    )
                }

                items(sortedProjects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        expenseTotal = expenseTotals[project.id],
                        onClick = { onProjectClick(project.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    // Add Project Dialog
    if (showAddDialog) {
        ProjectFormDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { project ->
                onAddProject(project)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    expenseTotal: Double?,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()) }
    val shortDateFormat = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(project.color).copy(alpha = 0.16f)
            ) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = project.icon,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    project.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (project.description.isNotBlank()) {
                    Text(
                        project.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Expense Total
                val expenseText = expenseTotal?.let {
                    val fmt = NumberFormat.getNumberInstance(Locale.getDefault())
                    fmt.maximumFractionDigits = 0
                    "總花費 ${project.defaultCurrency} ${fmt.format(it)}"
                }

                // Date range
                val dateRangeText = if (project.startDate != null && project.endDate != null) {
                    "${shortDateFormat.format(Date(project.startDate))} – ${shortDateFormat.format(Date(project.endDate))}"
                } else null

                // Budget
                val budgetText = project.budget?.let {
                    val fmt = NumberFormat.getNumberInstance(Locale.getDefault())
                    fmt.maximumFractionDigits = 0
                    "預算 ${project.defaultCurrency} ${fmt.format(it)}"
                }

                Text(
                    buildString {
                        if (!project.isActive) {
                            append("封存")
                            append(" · ")
                        }
                        if (expenseText != null) {
                            append(expenseText)
                            if (dateRangeText != null) append(" · $dateRangeText")
                            else if (budgetText != null) append(" · $budgetText")
                        } else {
                            append(project.defaultCurrency)
                            if (dateRangeText != null) append(" · $dateRangeText")
                            if (budgetText != null) append(" · $budgetText")
                            if (dateRangeText == null && budgetText == null) {
                                append(" · ${dateFormat.format(Date(project.createdAt))}")
                            }
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
