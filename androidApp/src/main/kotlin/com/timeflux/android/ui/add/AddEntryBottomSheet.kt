package com.timeflux.android.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.timeflux.android.ui.EMOTION_VOCABULARY
import com.timeflux.android.ui.MOOD_FACTORS
import com.timeflux.android.ui.accentColor
import com.timeflux.android.ui.defaultEmoji
import com.timeflux.android.ui.displayLabel
import com.timeflux.android.ui.displayName
import com.timeflux.android.ui.energyScoreEmoji
import com.timeflux.android.ui.moodScoreEmoji
import com.timeflux.domain.model.ModuleType
import com.timeflux.domain.model.Tag
import com.timeflux.module.AVAILABLE_MODULES
import com.timeflux.module.milestone.CreateMilestoneUseCase
import com.timeflux.module.milestone.MilestoneCategory
import com.timeflux.module.milestone.MilestoneSignificance
import com.timeflux.module.mood.CreateMoodEntryUseCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryBottomSheet(
    onDismiss: () -> Unit,
    onSubmitMilestone: (CreateMilestoneUseCase.Params) -> Unit,
    onSubmitMood: (CreateMoodEntryUseCase.Params) -> Unit,
    enabledModules: Set<ModuleType>,
    onOpenModules: () -> Unit,
    availableTags: List<Tag> = emptyList(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedType by remember { mutableStateOf<ModuleType?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        when (val type = selectedType) {
            null -> ModuleTypeSelector(
                enabledModules = enabledModules,
                onTypeSelected = { selectedType = it },
                onOpenModules  = onOpenModules,
            )

            ModuleType.MILESTONE -> MilestoneForm(
                availableTags = availableTags,
                onSubmit      = onSubmitMilestone,
                onBack        = { selectedType = null },
            )

            ModuleType.MOOD -> MoodForm(
                availableTags = availableTags,
                onSubmit      = onSubmitMood,
                onBack        = { selectedType = null },
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
private fun ModuleTypeSelector(
    enabledModules: Set<ModuleType>,
    onTypeSelected: (ModuleType) -> Unit,
    onOpenModules: () -> Unit,
) {
    // Priority order from the catalogue, not Set iteration order.
    val types = AVAILABLE_MODULES.filter { it.type in enabledModules }.map { it.type }

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

        if (types.isEmpty()) {
            NoModulesEnabled(onOpenModules = onOpenModules)
            return@Column
        }

        // Two per row, keeping the square cards; a trailing spacer preserves the width of a
        // lone card on the last row.
        types.chunked(2).forEach { rowTypes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowTypes.forEach { type ->
                    ModuleCard(type, Modifier.weight(1f)) { onTypeSelected(type) }
                }
                if (rowTypes.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun NoModulesEnabled(onOpenModules: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Text(
            text = "No modules are enabled",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Turn on at least one module and it will show up here as something you can add.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onOpenModules, modifier = Modifier.fillMaxWidth()) {
            Text("Open Modules")
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
    availableTags: List<Tag>,
    onSubmit: (CreateMilestoneUseCase.Params) -> Unit,
    onBack: () -> Unit,
) {
    var title        by remember { mutableStateOf("") }
    var note         by remember { mutableStateOf("") }
    var category     by remember { mutableStateOf(MilestoneCategory.PERSONAL) }
    var significance by remember { mutableStateOf(MilestoneSignificance.NOTABLE) }
    var emoji        by remember { mutableStateOf("") }
    var tags         by remember { mutableStateOf(emptyList<String>()) }
    var titleError   by remember { mutableStateOf<String?>(null) }
    // Smart fields
    var company      by remember { mutableStateOf("") }
    var role         by remember { mutableStateOf("") }
    var institution  by remember { mutableStateOf("") }
    var program      by remember { mutableStateOf("") }
    var destination  by remember { mutableStateOf("") }
    var people       by remember { mutableStateOf("") }
    // Expandable deeper section
    var showMore     by remember { mutableStateOf(false) }
    var whatChanged  by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .imePadding(),
    ) {
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

        // ---- Category ----
        FormSectionLabel("Category")
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MilestoneCategory.entries) { cat ->
                FilterChip(
                    selected = cat == category,
                    onClick  = {
                        category = cat
                        // Clear smart fields when switching category
                        company = ""; role = ""; institution = ""; program = ""
                        destination = ""; people = ""
                    },
                    label = { Text(cat.id.replaceFirstChar { it.uppercaseChar() }) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Significance ----
        FormSectionLabel("Significance")
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

        // ---- Category-specific smart fields ----
        MilestoneSmartFields(
            category    = category,
            company     = company,     onCompanyChange    = { company = it },
            role        = role,        onRoleChange       = { role = it },
            institution = institution, onInstitutionChange = { institution = it },
            program     = program,     onProgramChange    = { program = it },
            destination = destination, onDestinationChange = { destination = it },
            people      = people,      onPeopleChange     = { people = it },
        )

        Spacer(Modifier.height(16.dp))

        TagInputField(
            selectedTags  = tags,
            availableTags = availableTags,
            onTagsChanged = { tags = it },
            modifier      = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        // ---- Expandable deeper context ----
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

        fun buildMilestoneParams(isEnriched: Boolean) = CreateMilestoneUseCase.Params(
            title       = title.trim(),
            note        = note.trim().ifBlank { null },
            category    = category,
            significance = significance,
            emoji       = emoji.trim().ifBlank { null },
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

        Button(
            onClick = {
                if (title.isBlank()) titleError = "Title is required"
                else onSubmit(buildMilestoneParams(isEnriched = true))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Milestone")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                if (title.isBlank()) titleError = "Title is required"
                else onSubmit(buildMilestoneParams(isEnriched = false))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text("Save — add more later")
        }
    }
}

@Composable
private fun FormSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MilestoneSmartFields(
    category: MilestoneCategory,
    company: String,      onCompanyChange: (String) -> Unit,
    role: String,         onRoleChange: (String) -> Unit,
    institution: String,  onInstitutionChange: (String) -> Unit,
    program: String,      onProgramChange: (String) -> Unit,
    destination: String,  onDestinationChange: (String) -> Unit,
    people: String,       onPeopleChange: (String) -> Unit,
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

// ---- Step 2b: Mood form --------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoodForm(
    availableTags: List<Tag>,
    onSubmit: (CreateMoodEntryUseCase.Params) -> Unit,
    onBack: () -> Unit,
) {
    var score    by remember { mutableIntStateOf(3) }
    var energy   by remember { mutableStateOf<Int?>(null) }
    var emotions by remember { mutableStateOf(emptySet<String>()) }
    var factors  by remember { mutableStateOf(emptySet<String>()) }
    var note     by remember { mutableStateOf("") }
    var tags     by remember { mutableStateOf(emptyList<String>()) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .imePadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Mood Check-in", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(12.dp))

        // ---- Mood score ----
        FormSectionLabel("How are you feeling?")
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

        // ---- Energy level ----
        FormSectionLabel("Energy level (optional)")
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

        // ---- Emotion vocabulary ----
        FormSectionLabel("How would you describe it? (optional)")
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
                        onClick  = {
                            emotions = if (selected) emotions - word else emotions + word
                        },
                        label = { Text(word) },
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // ---- Factors ----
        FormSectionLabel("What influenced it? (optional)")
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            MOOD_FACTORS.forEach { (name, emoji) ->
                val selected = name in factors
                FilterChip(
                    selected = selected,
                    onClick  = {
                        factors = if (selected) factors - name else factors + name
                    },
                    label = { Text("$emoji $name") },
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

        fun buildMoodParams(isEnriched: Boolean) = CreateMoodEntryUseCase.Params(
            score      = score,
            energy     = energy,
            emotions   = emotions.toList(),
            factors    = factors.toList(),
            note       = note.trim().ifBlank { null },
            tags       = tags,
            isEnriched = isEnriched,
        )

        Button(
            onClick = { onSubmit(buildMoodParams(isEnriched = true)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Check-in")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { onSubmit(buildMoodParams(isEnriched = false)) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text("Save — add more later")
        }
    }
}

// ---- Tag input with autocomplete -----------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagInputField(
    selectedTags: List<String>,
    availableTags: List<Tag>,
    onTagsChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input    by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val trimmed = input.trim()
    val suggestions = remember(trimmed, availableTags, selectedTags) {
        if (trimmed.isEmpty()) emptyList()
        else availableTags.filter {
            it.name.contains(trimmed, ignoreCase = true) && it.name !in selectedTags
        }.take(5)
    }
    val showCreate = trimmed.isNotEmpty() &&
        availableTags.none { it.name.equals(trimmed, ignoreCase = true) } &&
        trimmed !in selectedTags

    expanded = suggestions.isNotEmpty() || showCreate

    fun addTag(name: String) {
        val tag = name.trim()
        if (tag.isNotBlank() && tag !in selectedTags) {
            onTagsChanged(selectedTags + tag)
        }
        input = ""
        expanded = false
    }

    Column(modifier = modifier) {
        Text(
            text = "Tags",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        if (selectedTags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                selectedTags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick  = {},
                        label    = { Text("#$tag") },
                        trailingIcon = {
                            IconButton(
                                onClick = { onTagsChanged(selectedTags - tag) },
                                modifier = Modifier.size(18.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove $tag",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { raw ->
                    when {
                        raw.endsWith(",") -> addTag(raw.dropLast(1))
                        else -> input = raw
                    }
                },
                label = { Text(if (selectedTags.isEmpty()) "Add tags" else "Add another tag") },
                placeholder = { Text("Type to search or create…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addTag(input) }),
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                suggestions.forEach { tag ->
                    DropdownMenuItem(
                        text = { Text(tag.name) },
                        onClick = { addTag(tag.name) },
                    )
                }
                if (showCreate) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "+ Create \"$trimmed\"",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = { addTag(trimmed) },
                    )
                }
            }
        }
    }
}
