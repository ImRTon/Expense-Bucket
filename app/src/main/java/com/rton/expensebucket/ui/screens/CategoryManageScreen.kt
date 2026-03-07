package com.rton.expensebucket.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.ui.util.IconMapper
import org.burnoutcrew.reorderable.*
import androidx.compose.ui.draw.shadow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManageScreen(
    categories: List<Category>,
    onAddCategory: (Category) -> Unit,
    onUpdateCategory: (Category) -> Unit,
    onUpdateCategories: (List<Category>) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onBack: () -> Unit
) {
    var isExpenseTab by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var addAsChildOf by remember { mutableStateOf<Category?>(null) } // which parent to add child to
    var expandedParentIds by remember { mutableStateOf(setOf<Long>()) }

    val parentCategories = remember(categories, isExpenseTab) {
        categories.filter { it.isExpense == isExpenseTab && it.parentId == null }
            .sortedBy { it.sortOrder }
    }
    val childCategoriesMap = remember(categories) {
        categories.filter { it.parentId != null }
            .groupBy { it.parentId }
            .mapValues { (_, v) -> v.sortedBy { it.sortOrder } }
    }

    // Flatten representation for dragging
    data class FlatCat(val category: Category, val isParent: Boolean, val idGroup: Long)
    var dragStateList by remember { mutableStateOf(emptyList<FlatCat>()) }

    LaunchedEffect(parentCategories, childCategoriesMap, expandedParentIds) {
        val list = mutableListOf<FlatCat>()
        for (p in parentCategories) {
            list.add(FlatCat(p, true, p.id))
            if (expandedParentIds.contains(p.id)) {
                childCategoriesMap[p.id]?.forEach { c -> 
                    list.add(FlatCat(c, false, p.id)) 
                }
            }
        }
        dragStateList = list
    }

    val state = rememberReorderableLazyListState(
        onMove = { from, to ->
            dragStateList = dragStateList.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        },
        canDragOver = { draggedOver, dragging ->
            val draggedItem = dragStateList.getOrNull(dragging.index)
            val targetItem = dragStateList.getOrNull(draggedOver.index)
            if (draggedItem == null || targetItem == null) false
            else if (draggedItem.isParent && targetItem.isParent) true
            else if (!draggedItem.isParent && !targetItem.isParent && draggedItem.idGroup == targetItem.idGroup) true
            else false
        },
        onDragEnd = { startIndex, endIndex ->
            // Save updated sorts to database
            val updatedCategories = dragStateList.mapIndexed { index, flatCat ->
                flatCat.category.copy(sortOrder = index)
            }
            onUpdateCategories(updatedCategories)
        }
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "分類管理",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    addAsChildOf = null
                    showAddDialog = true
                },
                icon = { Icon(Icons.Filled.Add, "新增") },
                text = { Text("新增母分類") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ─── Expense / Income Tab ────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    SegmentedButton(
                        selected = isExpenseTab,
                        onClick = { isExpenseTab = true },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        label = { Text("支出") }
                    )
                    SegmentedButton(
                        selected = !isExpenseTab,
                        onClick = { isExpenseTab = false },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        label = { Text("收入") }
                    )
                }
            }

            // ─── Category List ───────────────────────────────
            if (parentCategories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Category,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "尚無分類",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = state.listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize().reorderable(state)
                ) {
                    items(dragStateList, key = { if (it.isParent) "parent_${it.category.id}" else "child_${it.category.id}" }) { flatCat ->
                        ReorderableItem(state, key = if (flatCat.isParent) "parent_${flatCat.category.id}" else "child_${flatCat.category.id}") { isDragging ->
                            val elevation = if (isDragging) 8.dp else 0.dp
                            
                            Box(modifier = Modifier
                                .detectReorderAfterLongPress(state)
                                .shadow(elevation, RoundedCornerShape(14.dp))
                            ) {
                                val isExpanded = flatCat.isParent && expandedParentIds.contains(flatCat.category.id)
                                val children = childCategoriesMap[flatCat.category.id] ?: emptyList()
                                val childCount = if (flatCat.isParent) children.size else 0

                                CategoryListItem(
                                    category = flatCat.category,
                                    isParent = flatCat.isParent,
                                    isExpanded = isExpanded,
                                    childCount = childCount,
                                    onToggleExpand = {
                                        if (flatCat.isParent) {
                                            expandedParentIds = if (isExpanded) {
                                                expandedParentIds - flatCat.category.id
                                            } else {
                                                expandedParentIds + flatCat.category.id
                                            }
                                        }
                                    },
                                    onEdit = { editingCategory = flatCat.category },
                                    onDelete = { onDeleteCategory(flatCat.category) },
                                    onAddChild = {
                                        addAsChildOf = flatCat.category
                                        showAddDialog = true
                                    }
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    // ─── Add / Edit Dialog ───────────────────────────────────
    if (showAddDialog || editingCategory != null) {
        CategoryEditDialog(
            existing = editingCategory,
            isExpense = isExpenseTab,
            parentCategory = addAsChildOf,
            onDismiss = {
                showAddDialog = false
                editingCategory = null
                addAsChildOf = null
            },
            onSave = { cat ->
                if (editingCategory != null) {
                    onUpdateCategory(cat)
                } else {
                    onAddCategory(cat)
                }
                showAddDialog = false
                editingCategory = null
                addAsChildOf = null
            }
        )
    }
}

@Composable
private fun CategoryListItem(
    category: Category,
    isParent: Boolean,
    isExpanded: Boolean,
    childCount: Int,
    onToggleExpand: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddChild: () -> Unit
) {
    val catColor = Color(category.color)
    val indent = if (isParent) 0.dp else 28.dp

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isParent)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Icon(
                IconMapper.getIcon(category.icon),
                contentDescription = null,
                tint = catColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))

            // Name + child count
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isParent) FontWeight.SemiBold else FontWeight.Normal
                    )
                )
                if (isParent && childCount > 0) {
                    Text(
                        "$childCount 個子分類",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // Actions
            if (isParent) {
                // Add child
                IconButton(onClick = onAddChild, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "新增子分類",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                // Expand/collapse
                if (childCount > 0) {
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isExpanded) "收合" else "展開",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Edit
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "編輯",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Delete
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "刪除",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun CategoryEditDialog(
    existing: Category?,
    isExpense: Boolean,
    parentCategory: Category?,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(existing?.icon ?: "Restaurant") }
    var selectedColorIndex by remember { mutableStateOf(0) }

    val colorPalette = listOf(
        0xFFF97316, 0xFF3B82F6, 0xFFEC4899, 0xFF8B5CF6,
        0xFF10B981, 0xFF06B6D4, 0xFF84CC16, 0xFFEF4444,
        0xFFF59E0B, 0xFF14B8A6, 0xFFE879F9, 0xFF6B7280,
        0xFF1E40AF, 0xFFA16207, 0xFFEA580C, 0xFF0891B2
    )

    LaunchedEffect(existing) {
        if (existing != null) {
            selectedColorIndex = colorPalette.indexOfFirst { it == existing.color }
                .coerceAtLeast(0)
        }
    }

    val iconOptions = listOf(
        "Restaurant", "ShoppingCart", "LocalCafe", "DeliveryDining",
        "DirectionsBus", "Flight", "LocalGasStation",
        "SportsEsports", "ShoppingBag", "Checkroom",
        "Home", "Receipt", "CleaningServices",
        "LocalHospital", "Spa", "School", "MenuBook",
        "AccountBalance", "EmojiEvents", "TrendingUp", "Wallet",
        "MoreHoriz", "Category", "Star", "Favorite"
    )

    val isParentAdd = parentCategory == null && existing == null
    val title = when {
        existing != null -> "編輯分類"
        parentCategory != null -> "新增子分類 (${parentCategory.name})"
        else -> "新增母分類"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分類名稱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Icon selector
                Text("圖示", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(6),
                    modifier = Modifier.heightIn(max = 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(iconOptions.size) { idx ->
                        val icon = iconOptions[idx]
                        val isSelected = icon == selectedIcon
                        Surface(
                            onClick = { selectedIcon = icon },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surface,
                            border = if (isSelected)
                                ButtonDefaults.outlinedButtonBorder(true)
                            else null
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    IconMapper.getIcon(icon),
                                    contentDescription = icon,
                                    modifier = Modifier.size(22.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Color selector
                Text("顏色", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(8),
                    modifier = Modifier.heightIn(max = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(colorPalette.size) { idx ->
                        val color = Color(colorPalette[idx])
                        val isSelected = idx == selectedColorIndex
                        Surface(
                            onClick = { selectedColorIndex = idx },
                            shape = RoundedCornerShape(8.dp),
                            color = color,
                            modifier = Modifier.size(32.dp),
                            border = if (isSelected)
                                ButtonDefaults.outlinedButtonBorder(true)
                            else null
                        ) {
                            if (isSelected) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cat = Category(
                        id = existing?.id ?: 0,
                        name = name.trim(),
                        icon = selectedIcon,
                        color = colorPalette[selectedColorIndex],
                        isExpense = existing?.isExpense ?: isExpense,
                        parentId = existing?.parentId ?: parentCategory?.id,
                        sortOrder = existing?.sortOrder ?: 0
                    )
                    onSave(cat)
                },
                enabled = name.isNotBlank()
            ) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
