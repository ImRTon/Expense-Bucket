package com.rton.expensebucket.ui.screens

import androidx.compose.ui.platform.LocalContext
import com.rton.expensebucket.ui.util.localizedLabel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.ui.util.IconMapper

@Composable
fun CategoryGrid(
    parentCategories: List<Category>,
    childCategoriesMap: Map<Long?, List<Category>>,
    selectedCategory: Category?,
    expandedParentId: Long?,
    onSelectCategory: (Category) -> Unit,
    onToggleExpandParent: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {    if (parentCategories.isEmpty()) return

    val rows = remember(parentCategories) { parentCategories.chunked(4) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { rowCategories ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowCategories.forEach { parent ->
                    val children = childCategoriesMap[parent.id].orEmpty()
                    val isParentOrChildSelected = selectedCategory?.id == parent.id ||
                        selectedCategory?.parentId == parent.id
                    val isExpanded = expandedParentId == parent.id

                    CategoryGridTile(
                        category = parent,
                        isSelected = isParentOrChildSelected,
                        hasChildren = children.isNotEmpty(),
                        isExpanded = isExpanded,
                        circleSize = 46.dp,
                        iconSize = 22.dp,
                        onClick = {
                            if (children.isEmpty()) {
                                onToggleExpandParent(null)
                                onSelectCategory(parent)
                            } else {
                                if (!isParentOrChildSelected) {
                                    onSelectCategory(parent)
                                }
                                onToggleExpandParent(if (isExpanded) null else parent.id)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(4 - rowCategories.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            // Second-level subcategory 4-column icon grid
            val expandedInThisRow = rowCategories.find { it.id == expandedParentId }
            val expandedChildren = expandedInThisRow?.let { childCategoriesMap[it.id] }.orEmpty()
            AnimatedVisibility(
                visible = expandedInThisRow != null && expandedChildren.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val parentAccent = expandedInThisRow?.let { Color(it.color) }
                    ?: MaterialTheme.colorScheme.primary
                val childRows = remember(expandedChildren) { expandedChildren.chunked(4) }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, parentAccent.copy(alpha = 0.22f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        childRows.forEach { childRow ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                childRow.forEach { child ->
                                    val isChildSelected = selectedCategory?.id == child.id
                                    CategoryGridTile(
                                        category = child,
                                        isSelected = isChildSelected,
                                        hasChildren = false,
                                        isExpanded = false,
                                        circleSize = 40.dp,
                                        iconSize = 19.dp,
                                        onClick = {
                                            onSelectCategory(child)
                                            onToggleExpandParent(null)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(4 - childRow.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryGridTile(
    category: Category,
    isSelected: Boolean,
    hasChildren: Boolean,
    isExpanded: Boolean,
    circleSize: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalContext.current.resources
    val categoryColor = Color(category.color)
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "categoryScale"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(circleSize)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    if (isSelected) categoryColor else categoryColor.copy(alpha = 0.14f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = IconMapper.getIcon(category.icon),
                contentDescription = strings.localizedLabel(category.name),
                tint = if (isSelected) Color.White else categoryColor,
                modifier = Modifier.size(iconSize)
            )
        }
        Text(
            text = when {
                hasChildren && isExpanded -> "${strings.localizedLabel(category.name)} ▴"
                hasChildren -> "${strings.localizedLabel(category.name)} ▾"
                else -> strings.localizedLabel(category.name)
            },
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isSelected) categoryColor else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
