package com.timeflux.android.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timeflux.android.ui.accentColor
import com.timeflux.android.ui.defaultEmoji
import com.timeflux.android.ui.displayName
import com.timeflux.android.ui.moodScoreEmoji
import com.timeflux.domain.model.ModuleType
import com.timeflux.module.milestone.CreateMilestoneUseCase
import com.timeflux.module.milestone.MilestoneCategory
import com.timeflux.module.mood.CreateMoodEntryUseCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryBottomSheet(
    onDismiss: () -> Unit,
    onSubmitMilestone: (CreateMilestoneUseCase.Params) -> Unit,
    onSubmitMood: (CreateMoodEntryUseCase.Params) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedType by remember { mutableStateOf<ModuleType?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        when (val type = selectedType) {
            null -> ModuleTypeSelector(onTypeSelected = { selectedType = it })

            ModuleType.MILESTONE -> MilestoneForm(
                onSubmit = onSubmitMilestone,
                onBack   = { selectedType = null },
            )

            ModuleType.MOOD -> MoodForm(
                onSubmit = onSubmitMood,
                onBack   = { selectedType = null },
            )

            else -> {
                Text(
                    text = "${type.displayName()} module coming soon!",
                    modifier = Modifier.padding(32.dp).fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ---- Step 1: module type selector ----------------------------------------------------------

@Composable
private fun ModuleTypeSelector(onTypeSelected: (ModuleType) -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            text = "Add to Timeline",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 12.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModuleCard(ModuleType.MILESTONE, Modifier.weight(1f)) { onTypeSelected(ModuleType.MILESTONE) }
            ModuleCard(ModuleType.MOOD,      Modifier.weight(1f)) { onTypeSelected(ModuleType.MOOD) }
        }
    }
}

@Composable
private fun ModuleCard(type: ModuleType, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        colors = CardDefaults.cardColors(containerColor = type.accentColor().copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(type.defaultEmoji(), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text(type.displayName(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ---- Step 2a: Milestone form ---------------------------------------------------------------

@Composable
private fun MilestoneForm(
    onSubmit: (CreateMilestoneUseCase.Params) -> Unit,
    onBack: () -> Unit,
) {
    var title    by remember { mutableStateOf("") }
    var note     by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MilestoneCategory.PERSONAL) }
    var emoji    by remember { mutableStateOf("") }
    var titleError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .imePadding(),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("New Milestone", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it; titleError = null },
            label = { Text("Title *") },
            isError = titleError != null,
            supportingText = titleError?.let { msg -> { Text(msg) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note (optional)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Category",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MilestoneCategory.entries) { cat ->
                FilterChip(
                    selected = cat == category,
                    onClick  = { category = cat },
                    label    = { Text(cat.id.replaceFirstChar { it.uppercaseChar() }) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = emoji,
            onValueChange = { if (it.length <= 2) emoji = it },
            label = { Text("Emoji (optional)") },
            modifier = Modifier.fillMaxWidth(0.45f),
            singleLine = true,
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (title.isBlank()) {
                    titleError = "Title is required"
                } else {
                    onSubmit(
                        CreateMilestoneUseCase.Params(
                            title    = title.trim(),
                            note     = note.trim().ifBlank { null },
                            category = category,
                            emoji    = emoji.trim().ifBlank { null },
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Milestone")
        }
    }
}

// ---- Step 2b: Mood form --------------------------------------------------------------------

@Composable
private fun MoodForm(
    onSubmit: (CreateMoodEntryUseCase.Params) -> Unit,
    onBack: () -> Unit,
) {
    var score   by remember { mutableIntStateOf(3) }
    var note    by remember { mutableStateOf("") }
    var emotion by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .imePadding(),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Mood Check-in", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(12.dp))

        Text("How are you feeling?", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))

        // 1–5 score row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (1..5).forEach { s ->
                val selected = s == score
                FilledTonalButton(
                    onClick = { score = s },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primary
                                         else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor   = if (selected) MaterialTheme.colorScheme.onPrimary
                                         else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(moodScoreEmoji(s))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("What's on your mind? (optional)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = emotion,
            onValueChange = { emotion = it },
            label = { Text("Emotion tag (e.g. anxious, grateful)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                onSubmit(
                    CreateMoodEntryUseCase.Params(
                        score   = score,
                        note    = note.trim().ifBlank { null },
                        emotion = emotion.trim().ifBlank { null },
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Check-in")
        }
    }
}
