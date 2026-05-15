package com.timeflux.android.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timeflux.android.ui.accentColor
import com.timeflux.android.ui.defaultEmoji
import com.timeflux.android.ui.displayName
import com.timeflux.domain.model.ModuleType
import com.timeflux.domain.model.Tag

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    filter: TimelineFilter,
    availableTags: List<Tag>,
    onApply: (TimelineFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local draft state — applied only when the user taps Apply
    var moduleType      by remember { mutableStateOf(filter.moduleType) }
    var dateRange       by remember { mutableStateOf(filter.dateRange) }
    var selectedTags    by remember { mutableStateOf(filter.tags.toSet()) }
    var tagsMatchAll    by remember { mutableStateOf(filter.tagsMatchAll) }
    var significantOnly by remember { mutableStateOf(filter.significantOnly) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Filter", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = {
                    moduleType = null; dateRange = null
                    selectedTags = emptySet(); tagsMatchAll = false; significantOnly = false
                }) {
                    Text("Clear all")
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- Module type ----
            FilterSectionLabel("Entry type")
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = moduleType == null,
                    onClick  = { moduleType = null },
                    label    = { Text("All") },
                )
                listOf(ModuleType.MILESTONE, ModuleType.MOOD).forEach { type ->
                    FilterChip(
                        selected = moduleType == type,
                        onClick  = { moduleType = if (moduleType == type) null else type },
                        label    = { Text("${type.defaultEmoji()} ${type.displayName()}") },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---- Date range ----
            FilterSectionLabel("Date range")
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateRangePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = dateRange == preset,
                        onClick  = { dateRange = if (dateRange == preset) null else preset },
                        label    = { Text(preset.label) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---- Tags ----
            if (availableTags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterSectionLabel("Tags")
                    if (selectedTags.size > 1) {
                        TextButton(onClick = { tagsMatchAll = !tagsMatchAll }) {
                            Text(
                                if (tagsMatchAll) "Match all" else "Match any",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableTags.forEach { tag ->
                        val selected = tag.name in selectedTags
                        FilterChip(
                            selected = selected,
                            onClick  = {
                                selectedTags = if (selected) selectedTags - tag.name
                                              else selectedTags + tag.name
                            },
                            label = { Text("#${tag.name}") },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // ---- Significant milestones ----
            if (moduleType == null || moduleType == ModuleType.MILESTONE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Major & Defining milestones only", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "⭐ Major and 🌟 Defining entries",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = significantOnly,
                        onCheckedChange = { significantOnly = it },
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            Button(
                onClick = {
                    onApply(
                        TimelineFilter(
                            moduleType      = moduleType,
                            dateRange       = dateRange,
                            tags            = selectedTags.toList(),
                            tagsMatchAll    = tagsMatchAll,
                            significantOnly = significantOnly,
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply filter")
            }
        }
    }
}

@Composable
private fun FilterSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
