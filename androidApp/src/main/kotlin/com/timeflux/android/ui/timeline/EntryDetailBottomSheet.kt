package com.timeflux.android.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import com.timeflux.android.ui.EMOTION_VOCABULARY
import com.timeflux.android.ui.MOOD_FACTORS
import com.timeflux.android.ui.add.TagInputField
import com.timeflux.android.ui.displayLabel
import com.timeflux.android.ui.energyScoreEmoji
import com.timeflux.android.ui.moodScoreEmoji
import com.timeflux.data.json.AppJson
import com.timeflux.domain.model.ModuleType
import com.timeflux.domain.model.Tag
import com.timeflux.domain.model.TimelineEntry
import com.timeflux.module.milestone.MilestoneCategory
import com.timeflux.module.milestone.MilestonePayload
import com.timeflux.module.milestone.MilestoneSignificance
import com.timeflux.module.milestone.UpdateMilestoneUseCase
import com.timeflux.module.mood.MoodPayload
import com.timeflux.module.mood.UpdateMoodEntryUseCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailBottomSheet(
    entry: TimelineEntry,
    availableTags: List<Tag>,
    onUpdateMilestone: (UpdateMilestoneUseCase.Params) -> Unit,
    onUpdateMoodEntry: (UpdateMoodEntryUseCase.Params) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        when (entry.moduleType) {
            ModuleType.MILESTONE -> EditMilestoneForm(
                entry         = entry,
                availableTags = availableTags,
                onSubmit      = onUpdateMilestone,
                onDismiss     = onDismiss,
            )
            ModuleType.MOOD -> EditMoodForm(
                entry         = entry,
                availableTags = availableTags,
                onSubmit      = onUpdateMoodEntry,
                onDismiss     = onDismiss,
            )
            else -> {
                Text(
                    text = "Editing this entry type is not yet supported.",
                    modifier = Modifier.padding(32.dp).fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ---- Edit Milestone form -------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditMilestoneForm(
    entry: TimelineEntry,
    availableTags: List<Tag>,
    onSubmit: (UpdateMilestoneUseCase.Params) -> Unit,
    onDismiss: () -> Unit,
) {
    val payload = remember(entry.payload) {
        try { AppJson.decodeFromString(MilestonePayload.serializer(), entry.payload) }
        catch (_: Exception) { MilestonePayload() }
    }

    var title        by remember { mutableStateOf(entry.title ?: "") }
    var note         by remember { mutableStateOf(entry.note ?: "") }
    var category     by remember { mutableStateOf(payload.category) }
    var significance by remember { mutableStateOf(payload.significance) }
    var emoji        by remember { mutableStateOf(payload.emoji ?: "") }
    var tags         by remember { mutableStateOf(payload.tags) }
    var titleError   by remember { mutableStateOf<String?>(null) }
    var company      by remember { mutableStateOf(payload.company ?: "") }
    var role         by remember { mutableStateOf(payload.role ?: "") }
    var institution  by remember { mutableStateOf(payload.institution ?: "") }
    var program      by remember { mutableStateOf(payload.program ?: "") }
    var destination  by remember { mutableStateOf(payload.destination ?: "") }
    var people       by remember { mutableStateOf(payload.people ?: "") }
    var whatChanged  by remember { mutableStateOf(payload.whatChanged ?: "") }
    var showMore     by remember { mutableStateOf(emoji.isNotBlank() || whatChanged.isNotBlank()) }

    fun buildParams(isEnriched: Boolean) = UpdateMilestoneUseCase.Params(
        id          = entry.id,
        title       = title.trim(),
        note        = note.trim().ifBlank { null },
        category    = category,
        significance = significance,
        emoji       = emoji.trim().ifBlank { null },
        isPinned    = entry.isPinned,
        tags        = tags,
        isEnriched  = isEnriched,
        company     = company.trim().ifBlank { null },
        role        = role.trim().ifBlank { null },
        institution = institution.trim().ifBlank { null },
        program     = program.trim().ifBlank { null },
        destination = destination.trim().ifBlank { null },
        people      = people.trim().ifBlank { null },
        whatChanged = whatChanged.trim().ifBlank { null },
    )

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Edit Milestone", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        if (!payload.isEnriched) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "This entry was saved as a draft — add more detail below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
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

        DetailSectionLabel("Category")
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MilestoneCategory.entries) { cat ->
                FilterChip(
                    selected = cat == category,
                    onClick  = {
                        category = cat
                        company = ""; role = ""; institution = ""; program = ""
                        destination = ""; people = ""
                    },
                    label = { Text(cat.id.replaceFirstChar { it.uppercaseChar() }) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        DetailSectionLabel("Significance")
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MilestoneSignificance.entries.forEach { sig ->
                FilterChip(
                    selected = sig == significance,
                    onClick  = { significance = sig },
                    label    = { Text(sig.displayLabel()) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        MilestoneSmartFieldsDetail(
            category    = category,
            company     = company,     onCompanyChange     = { company = it },
            role        = role,        onRoleChange        = { role = it },
            institution = institution, onInstitutionChange = { institution = it },
            program     = program,     onProgramChange     = { program = it },
            destination = destination, onDestinationChange = { destination = it },
            people      = people,      onPeopleChange      = { people = it },
        )

        Spacer(Modifier.height(16.dp))

        TagInputField(
            selectedTags  = tags,
            availableTags = availableTags,
            onTagsChanged = { tags = it },
            modifier      = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        FilledTonalButton(
            onClick = { showMore = !showMore },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                if (showMore) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
            )
            Spacer(Modifier.size(6.dp))
            Text(if (showMore) "Less detail" else "Add more context")
        }

        if (showMore) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = emoji,
                onValueChange = { if (it.length <= 2) emoji = it },
                label = { Text("Emoji (optional)") },
                modifier = Modifier.fillMaxWidth(0.45f),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = whatChanged,
                onValueChange = { whatChanged = it },
                label = { Text("What changed after this?") },
                placeholder = { Text("How did this shift things for you?") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (title.isBlank()) titleError = "Title is required"
                else onSubmit(buildParams(isEnriched = true))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Changes")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                if (title.isBlank()) titleError = "Title is required"
                else onSubmit(buildParams(isEnriched = false))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text("Save — add more later")
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MilestoneSmartFieldsDetail(
    category: MilestoneCategory,
    company: String,     onCompanyChange: (String) -> Unit,
    role: String,        onRoleChange: (String) -> Unit,
    institution: String, onInstitutionChange: (String) -> Unit,
    program: String,     onProgramChange: (String) -> Unit,
    destination: String, onDestinationChange: (String) -> Unit,
    people: String,      onPeopleChange: (String) -> Unit,
) {
    when (category) {
        MilestoneCategory.CAREER -> {
            OutlinedTextField(
                value = company, onValueChange = onCompanyChange,
                label = { Text("Company (optional)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = role, onValueChange = onRoleChange,
                label = { Text("Role (optional)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }
        MilestoneCategory.EDUCATION -> {
            OutlinedTextField(
                value = institution, onValueChange = onInstitutionChange,
                label = { Text("Institution (optional)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = program, onValueChange = onProgramChange,
                label = { Text("Degree / Program (optional)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }
        MilestoneCategory.TRAVEL -> {
            OutlinedTextField(
                value = destination, onValueChange = onDestinationChange,
                label = { Text("Destination (optional)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }
        MilestoneCategory.FAMILY -> {
            OutlinedTextField(
                value = people, onValueChange = onPeopleChange,
                label = { Text("People involved (optional)") },
                placeholder = { Text("e.g. Mom, Sarah, Jake") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }
        else -> Unit
    }
}

// ---- Edit Mood form -----------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditMoodForm(
    entry: TimelineEntry,
    availableTags: List<Tag>,
    onSubmit: (UpdateMoodEntryUseCase.Params) -> Unit,
    onDismiss: () -> Unit,
) {
    val payload = remember(entry.payload) {
        try { AppJson.decodeFromString(MoodPayload.serializer(), entry.payload) }
        catch (_: Exception) { MoodPayload(score = 3) }
    }

    var score    by remember { mutableIntStateOf(payload.score) }
    var energy   by remember { mutableStateOf(payload.energy) }
    var emotions by remember { mutableStateOf(payload.emotions.toSet()) }
    var factors  by remember { mutableStateOf(payload.factors.toSet()) }
    var note     by remember { mutableStateOf(entry.note ?: "") }
    var tags     by remember { mutableStateOf(payload.tags) }

    fun buildParams(isEnriched: Boolean) = UpdateMoodEntryUseCase.Params(
        id         = entry.id,
        score      = score,
        note       = note.trim().ifBlank { null },
        energy     = energy,
        emotion    = payload.emotion,
        emotions   = emotions.toList(),
        factors    = factors.toList(),
        tags       = tags,
        isEnriched = isEnriched,
    )

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Edit Check-in", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        if (!payload.isEnriched) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "This entry was saved as a draft — add more detail below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(12.dp))

        DetailSectionLabel("How were you feeling?")
        Spacer(Modifier.height(8.dp))
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

        Spacer(Modifier.height(20.dp))

        DetailSectionLabel("Energy level (optional)")
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (1..5).forEach { e ->
                val selected = e == energy
                FilledTonalButton(
                    onClick = { energy = if (selected) null else e },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.secondary
                                         else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor   = if (selected) MaterialTheme.colorScheme.onSecondary
                                         else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(energyScoreEmoji(e))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        DetailSectionLabel("How would you describe it? (optional)")
        Spacer(Modifier.height(8.dp))
        EMOTION_VOCABULARY.forEach { (groupLabel, words) ->
            Text(
                text = groupLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                words.forEach { word ->
                    val selected = word in emotions
                    FilterChip(
                        selected = selected,
                        onClick  = { emotions = if (selected) emotions - word else emotions + word },
                        label    = { Text(word) },
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        DetailSectionLabel("What influenced it? (optional)")
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            MOOD_FACTORS.forEach { (name, emoji) ->
                val selected = name in factors
                FilterChip(
                    selected = selected,
                    onClick  = { factors = if (selected) factors - name else factors + name },
                    label    = { Text("$emoji $name") },
                )
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

        Spacer(Modifier.height(12.dp))

        TagInputField(
            selectedTags  = tags,
            availableTags = availableTags,
            onTagsChanged = { tags = it },
            modifier      = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onSubmit(buildParams(isEnriched = true)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Changes")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { onSubmit(buildParams(isEnriched = false)) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text("Save — add more later")
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DetailSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
