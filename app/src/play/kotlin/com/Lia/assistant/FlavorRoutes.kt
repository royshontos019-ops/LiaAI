package com.Lia.assistant

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

object FlavorRoutes {
    const val FLAVOR_NAME = "play"
    const val SUPPORTS_SCREEN_CONTROL = false
    val extraRoutes: List<String> = emptyList()
}

@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.flavorDestinations(nav: NavHostController) = Unit
