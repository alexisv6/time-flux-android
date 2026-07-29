package com.timeflux.android.ui.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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

/**
 * The module picker. Lists the modules that are built today, plus a footer naming the ones still
 * to come — eight dead switches would make the screen feel broken (spec 001, decision D5).
 *
 * Phase 2 renders this read-only; Phase 3 binds the switches to ModuleRegistry state, and Phase 5
 * adds the hide-entries control to disabled rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulesScreen(
    onBack: () -> Unit,
    viewModel: ModulesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modules") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "Time Flux is built from modules. Turn on the parts of your life you " +
                        "want on your timeline — you can change this any time.",
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
                        onEnabledChange = { viewModel.setEnabled(module.type, it) },
                        interactive = true,
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
    onEnabledChange: (Boolean) -> Unit,
    interactive: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = module.type.accentColor().copy(alpha = 0.10f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            Switch(
                checked = isEnabled,
                onCheckedChange = onEnabledChange,
                enabled = interactive,
            )
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
