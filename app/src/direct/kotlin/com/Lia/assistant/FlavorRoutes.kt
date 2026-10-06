package com.Lia.assistant

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

object FlavorRoutes {
    const val FLAVOR_NAME = "direct"
    const val SUPPORTS_SCREEN_CONTROL = true
    val extraRoutes: List<String> = listOf("accessibility_setup")
}

@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.flavorDestinations(nav: NavHostController) {
    // Direct-only destinations (e.g. accessibility onboarding) are registered here.
}
