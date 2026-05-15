package com.timeflux.android.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timeflux.android.ui.accentColor
import com.timeflux.android.ui.add.AddEntryBottomSheet
import com.timeflux.android.ui.badgeColor
import com.timeflux.android.ui.badgeLabel
import com.timeflux.android.ui.cardEmoji
import com.timeflux.android.ui.displayName
import com.timeflux.android.ui.energyScoreEmoji
import com.timeflux.android.ui.toDisplayDate
import com.timeflux.data.json.AppJson
import com.timeflux.domain.model.ModuleType
import com.timeflux.domain.model.TimelineEntry
import com.timeflux.module.milestone.MilestonePayload
import com.timeflux.module.milestone.MilestoneSignificance
import com.timeflux.module.mood.MoodPayload
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimelineScreen(viewModel: TimelineViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddSheet    by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    val displayEntries = remember(state.entries, state.filter) {
        if (state.filter.isActive) state.entries.filter { state.filter.matches(it) }
        else state.entries
    }

    LaunchedEffect(Unit) {
        viewModel.entryAdded.collect { showAddSheet = false }
    }

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Time Flux") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = if (state.filter.isActive) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add entry")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            ActiveFilterBar(
                filter = state.filter,
                onRemoveModuleType  = { viewModel.setFilter(state.filter.copy(moduleType = null)) },
                onRemoveDateRange   = { viewModel.setFilter(state.filter.copy(dateRange = null)) },
                onRemoveTag         = { tag -> viewModel.setFilter(state.filter.copy(tags = state.filter.tags - tag)) },
                onRemoveSignificant = { viewModel.setFilter(state.filter.copy(significantOnly = false)) },
            )
            TimelineContent(
                entries       = displayEntries,
                allCount      = state.entries.size,
                filterActive  = state.filter.isActive,
                isLoading     = state.isLoading,
                isLoadingMore = state.isLoadingMore,
                hasMore       = state.hasMore,
                onLoadMore    = viewModel::loadNextPage,
            )
        }
    }

    if (showAddSheet) {
        AddEntryBottomSheet(
            onDismiss         = { showAddSheet = false },
            onSubmitMilestone = viewModel::addMilestone,
            onSubmitMood      = viewModel::addMoodEntry,
            availableTags     = state.availableTags,
        )
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            filter        = state.filter,
            availableTags = state.availableTags,
            onApply       = { viewModel.setFilter(it) },
            onDismiss     = { showFilterSheet = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilterBar(
    filter: TimelineFilter,
    onRemoveModuleType: () -> Unit,
    onRemoveDateRange: () -> Unit,
    onRemoveTag: (String) -> Unit,
    onRemoveSignificant: () -> Unit,
) {
    if (!filter.isActive) return

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        filter.moduleType?.let { type ->
            ActiveChip(label = type.displayName(), onRemove = onRemoveModuleType)
        }
        filter.dateRange?.let { range ->
            ActiveChip(label = range.label, onRemove = onRemoveDateRange)
        }
        filter.tags.forEach { tag ->
            ActiveChip(label = "#$tag", onRemove = { onRemoveTag(tag) })
        }
        if (filter.significantOnly) {
            ActiveChip(label = "Major+ only", onRemove = onRemoveSignificant)
        }
    }
}

@Composable
private fun ActiveChip(label: String, onRemove: () -> Unit) {
    AssistChip(
        onClick = onRemove,
        label   = { Text(label, style = MaterialTheme.typography.labelSmall) },
        trailingIcon = {
            Icon(Icons.Default.Close, contentDescription = "Remove filter", modifier = Modifier.size(14.dp))
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor     = MaterialTheme.colorScheme.onPrimaryContainer,
            trailingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

// ---- Content area --------------------------------------------------------------------------

@Composable
private fun TimelineContent(
    entries: List<TimelineEntry>,
    allCount: Int,
    filterActive: Boolean,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        isLoading        -> FullScreenLoader(modifier)
        entries.isEmpty() && !filterActive -> EmptyTimeline(modifier)
        entries.isEmpty() -> EmptyFilterResult(modifier)
        else -> TimelineList(
            entries       = entries,
            allCount      = allCount,
            filterActive  = filterActive,
            isLoadingMore = isLoadingMore,
            hasMore       = hasMore,
            onLoadMore    = onLoadMore,
            modifier      = modifier,
        )
    }
}

@Composable
private fun FullScreenLoader(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyTimeline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📅", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(12.dp))
            Text("Your timeline is empty", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tap + to add your first entry",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyFilterResult(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔍", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(12.dp))
            Text("No entries match", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Try adjusting your filters",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimelineList(
    entries: List<TimelineEntry>,
    allCount: Int,
    filterActive: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    val loadMoreTrigger by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3 && hasMore && !isLoadingMore
        }
    }
    LaunchedEffect(loadMoreTrigger) {
        if (loadMoreTrigger) onLoadMore()
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        if (filterActive) {
            item {
                Text(
                    text = "Showing ${entries.size} of $allCount loaded entries",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }

        items(entries, key = { it.id }) { entry ->
            TimelineEntryCard(entry)
        }

        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

// ---- Entry card ----------------------------------------------------------------------------

private fun payloadTags(payload: String): List<String> =
    try {
        AppJson.parseToJsonElement(payload)
            .jsonObject["tags"]
            ?.jsonArray
            ?.map { it.jsonPrimitive.content }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    } catch (_: Exception) { emptyList() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineEntryCard(entry: TimelineEntry, modifier: Modifier = Modifier) {
    val tags = remember(entry.payload) { payloadTags(entry.payload) }
    val significanceBadge = remember(entry.moduleType, entry.payload) {
        if (entry.moduleType != ModuleType.MILESTONE) null
        else try {
            AppJson.decodeFromString(MilestonePayload.serializer(), entry.payload)
                .significance.badgeLabel()
        } catch (_: Exception) { null }
    }
    val significanceColor = remember(entry.moduleType, entry.payload) {
        if (entry.moduleType != ModuleType.MILESTONE) null
        else try {
            AppJson.decodeFromString(MilestonePayload.serializer(), entry.payload)
                .significance.badgeColor()
        } catch (_: Exception) { null }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Colored circle with module emoji
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = entry.moduleType.accentColor().copy(alpha = 0.15f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = entry.cardEmoji(), style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    // Title / score / note
                    Column(modifier = Modifier.weight(1f)) {
                        entry.title?.let { title ->
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (entry.moduleType == ModuleType.MOOD) {
                            Spacer(Modifier.height(4.dp))
                            MoodScoreRow(entry)
                        }
                        entry.note?.let { note ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Date + pin + significance
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = entry.createdAt.toDisplayDate(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (entry.isPinned) {
                            Text("📌", style = MaterialTheme.typography.labelSmall)
                        }
                        if (significanceBadge != null && significanceColor != null) {
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = significanceColor.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = significanceBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = significanceColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }

                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = entry.moduleType.accentColor().copy(alpha = 0.12f),
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = entry.moduleType.accentColor(),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodScoreRow(entry: TimelineEntry) {
    val payload = remember(entry.payload) {
        try { AppJson.decodeFromString(MoodPayload.serializer(), entry.payload) }
        catch (_: Exception) { MoodPayload(score = 3) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            Text(
                text  = if (i < payload.score) "●" else "○",
                color = if (i < payload.score) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        payload.energy?.let { e ->
            Spacer(Modifier.width(8.dp))
            Text(
                text  = energyScoreEmoji(e),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }

    val displayEmotions = remember(payload) {
        payload.emotions.take(3).joinToString(" · ").ifBlank {
            payload.emotion?.take(30) ?: ""
        }
    }
    if (displayEmotions.isNotBlank()) {
        Spacer(Modifier.height(2.dp))
        Text(
            text  = displayEmotions,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
