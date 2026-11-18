package com.rton.expensebucket.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.rton.expensebucket.ui.statistics.StatsPeriod
import com.rton.expensebucket.ui.util.localizedLabel

@Composable
internal fun StatisticsPeriodSelector(
    period: StatsPeriod,
    onPeriodChange: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalContext.current.resources
    SlidingPillSelector(
        options = StatsPeriod.entries,
        selected = period,
        onSelect = onPeriodChange,
        label = { strings.localizedLabel(it.label) },
        modifier = modifier.testTag("statistics_period_selector"),
        indicatorTestTag = "statistics_period_indicator",
        optionTestTag = { "statistics_period_${it.name.lowercase()}" }
    )
}
