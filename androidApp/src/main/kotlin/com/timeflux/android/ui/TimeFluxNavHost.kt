package com.timeflux.android.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.timeflux.android.ui.modules.ModulesScreen
import com.timeflux.android.ui.timeline.TimelineScreen
import kotlinx.serialization.Serializable

/**
 * Typed navigation destinations. Serializable objects rather than string routes so arguments —
 * when destinations start taking them — are checked by the compiler.
 */
@Serializable
data object TimelineRoute

@Serializable
data object ModulesRoute

/**
 * The app's single navigation graph.
 *
 * Modules is entered from the timeline's app bar rather than sitting in a bottom bar: it's a place
 * you visit, not a peer of the timeline. A bottom bar becomes right once Search and Insights exist
 * (spec 001, decision D6).
 */
@Composable
fun TimeFluxNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = TimelineRoute) {
        composable<TimelineRoute> {
            TimelineScreen(onOpenModules = { navController.navigate(ModulesRoute) })
        }
        composable<ModulesRoute> {
            ModulesScreen(onBack = { navController.popBackStack() })
        }
    }
}
