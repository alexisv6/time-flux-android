package com.timeflux.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.timeflux.android.ui.modules.ModulesScreen
import com.timeflux.android.ui.modules.ModulesScreenMode
import com.timeflux.android.ui.timeline.TimelineScreen
import com.timeflux.module.ModuleRegistry
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

/**
 * Typed navigation destinations. Serializable objects rather than string routes so arguments —
 * when destinations start taking them — are checked by the compiler.
 */
@Serializable
data object TimelineRoute

@Serializable
data object ModulesRoute

/** The module picker shown once, on first launch. Same screen as [ModulesRoute], different framing. */
@Serializable
data object FirstRunRoute

/**
 * The app's single navigation graph.
 *
 * Modules is entered from the timeline's app bar rather than sitting in a bottom bar: it's a place
 * you visit, not a peer of the timeline. A bottom bar becomes right once Search and Insights exist
 * (spec 001, decision D6).
 */
@Composable
fun TimeFluxNavHost(registry: ModuleRegistry = koinInject()) {
    // Null until the stored flag is read. Nothing renders in the meantime — guessing wrong would
    // flash the timeline at a brand-new user before replacing it with onboarding.
    var isFirstRunComplete by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { isFirstRunComplete = registry.isFirstRunComplete() }

    val firstRunComplete = isFirstRunComplete ?: return
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (firstRunComplete) TimelineRoute else FirstRunRoute,
    ) {
        composable<TimelineRoute> {
            TimelineScreen(onOpenModules = { navController.navigate(ModulesRoute) })
        }
        composable<ModulesRoute> {
            ModulesScreen(
                onDone = { navController.popBackStack() },
                mode = ModulesScreenMode.SETTINGS,
            )
        }
        composable<FirstRunRoute> {
            ModulesScreen(
                onDone = {
                    // Pop first run off the stack — system back must exit, not re-enter onboarding.
                    navController.navigate(TimelineRoute) {
                        popUpTo(FirstRunRoute) { inclusive = true }
                    }
                },
                mode = ModulesScreenMode.FIRST_RUN,
            )
        }
    }
}
