package com.timeflux.android.ui.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timeflux.android.ui.accentColor
import com.timeflux.android.ui.defaultEmoji
import com.timeflux.module.AVAILABLE_MODULES
import com.timeflux.module.LifeModule
import com.timeflux.module.UPCOMING_MODULES
import org.koin.androidx.compose.koinViewModel

/** How the picker is being shown. The list is identical; only the framing differs (spec 001, D7). */
enum class ModulesScreenMode { FIRST_RUN, SETTINGS }

/**
 * The module picker. Lists the modules that are built today, plus a footer naming the ones still
 * to come — eight dead switches would make the screen feel broken (spec 001, decision D5).
 *
 * One composable serves both first run and settings: two screens showing the same list would
 * drift apart. First run gets a welcome title and a "Start my timeline" action; settings gets a
 * back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulesScreen(
    onDone: () -> Unit,
    mode: ModulesScreenMode = ModulesScreenMode.SETTINGS,
    viewModel: ModulesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isFirstRun = mode == ModulesScreenMode.FIRST_RUN

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isFirstRun) "Welcome to Time Flux" else "Modules") },
                navigationIcon = {
                    // No way back during first run — there's no timeline behind it yet.
                    if (!isFirstRun) {
                        IconButton(onClick = onDone) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            if (isFirstRun && state.isLoaded) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Button(
                        onClick = { viewModel.completeFirstRun(onDone) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text("Start my timeline")
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = if (isFirstRun) {
                        "Time Flux is built from modules — the parts of your life you want on one " +
                            "timeline. These are on to start with, and you can change them any time."
                    } else {
                        "Time Flux is built from modules. Turn on the parts of your life you " +
                            "want on your timeline — you can change this any time."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            // Nothing is rendered until the registry has emitted, so a switch never shows the
            // wrong position for a frame.
            if (state.isLoaded) {
                items(AVAILABLE_MODULES, key = { it.type.name }) { module ->
                    ModuleRow(
                        module = module,
                        isEnabled = module.type in state.enabled,
                        isHidden = module.type in state.hidden,
                        onEnabledChange = { viewModel.setEnabled(module.type, it) },
                        onHiddenChange = { viewModel.setHidden(module.type, it) },
                    )
                }

                item { UpcomingModulesFooter() }
            }
        }
    }
}

@Composable
private fun ModuleRow(
    module: LifeModule,
    isEnabled: Boolean,
    isHidden: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onHiddenChange: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = module.type.accentColor().copy(alpha = 0.10f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(module.type.defaultEmoji(), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(module.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = module.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = isEnabled, onCheckedChange = onEnabledChange)
            }

            // Only a disabled module can be hidden — "enabled but hidden" would let the user
            // create entries that never appear (spec 001, decision D4).
            if (!isEnabled) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hide past entries",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "They stay saved — turning the module back on brings them back.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = isHidden, onCheckedChange = onHiddenChange)
                }
            }
        }
    }
}

/**
 * Names the unbuilt modules in one line, derived from [UPCOMING_MODULES] so it corrects itself as
 * modules ship rather than becoming a claim someone has to remember to edit.
 */
@Composable
private fun UpcomingModulesFooter() {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(
            text = "More on the way",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = UPCOMING_MODULES.joinToString { it.displayName },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
    }
}
