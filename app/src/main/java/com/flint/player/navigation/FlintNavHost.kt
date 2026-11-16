// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.navigation

import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flint.player.data.FlintData
import com.flint.player.data.UriAccess
import com.flint.player.home.HomeScreen
import com.flint.player.player.LibVlcProvider
import com.flint.player.player.FlintPlayer
import com.flint.player.settings.SettingsScreen

const val HOME_ROUTE = "home"
const val SETTINGS_ROUTE = "settings"
private const val ARG_URI = "uri"
private const val ARG_PLAYLIST = "playlist"
private const val ARG_INDEX = "index"
private const val PLAYER_ROUTE =
    "player?$ARG_URI={$ARG_URI}&$ARG_PLAYLIST={$ARG_PLAYLIST}&$ARG_INDEX={$ARG_INDEX}"

fun playerRoute(uri: Uri): String =
    "player?$ARG_URI=${Uri.encode(uri.toString())}"

fun playlistRoute(id: String, index: Int): String =
    "player?$ARG_PLAYLIST=${Uri.encode(id)}&$ARG_INDEX=$index"

@Composable
fun FlintNavHost(
    activity: ComponentActivity,
    incomingUri: Uri?,
    onIncomingHandled: () -> Unit
) {
    val navController = rememberNavController()

    // Opens the player, replacing one that is already on screen. Without popUpTo, a video opened
    // from another app while the player is showing would reuse the old screen and not switch.
    fun openVideo(uri: Uri) {
        if (!UriAccess.isReadable(activity, uri)) {
            // Typical for history entries of files that were shared with the app only once
            Toast.makeText(activity, "That video can't be opened anymore", Toast.LENGTH_SHORT).show()
            FlintData.removeHistory(uri.toString())
            return
        }
        LibVlcProvider.warmUp(activity)
        navController.navigate(playerRoute(uri)) { popUpTo(HOME_ROUTE) }
    }

    LaunchedEffect(incomingUri) {
        incomingUri?.let {
            openVideo(it)
            onIncomingHandled()
        }
    }

    Box(Modifier.fillMaxSize()) {
    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE,
        // Settings slides in sideways like a page; the player appears instantly
        enterTransition = {
            if (targetState.destination.route == SETTINGS_ROUTE) {
                slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { it / 3 } +
                    fadeIn(tween(220))
            } else {
                // Plain fade: the player is solid black with a black cover over the video
                // surface, so home dissolves into black and then the video fades in
                fadeIn(tween(220))
            }
        },
        exitTransition = {
            if (targetState.destination.route == SETTINGS_ROUTE) {
                slideOutHorizontally(tween(260)) { -it / 6 } + fadeOut(tween(180))
            } else {
                // Home stays put until the player is in place
                ExitTransition.KeepUntilTransitionsFinished
            }
        },
        popEnterTransition = {
            if (initialState.destination.route == SETTINGS_ROUTE) {
                slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { -it / 6 } +
                    fadeIn(tween(220))
            } else {
                // Home is already waiting underneath while the black player dissolves away
                EnterTransition.None
            }
        },
        popExitTransition = {
            if (initialState.destination.route == SETTINGS_ROUTE) {
                slideOutHorizontally(tween(240)) { it / 3 } + fadeOut(tween(180))
            } else {
                fadeOut(tween(200))
            }
        }
    ) {
        composable(HOME_ROUTE) {
            HomeScreen(
                onOpenVideo = { uri -> openVideo(uri) },
                onPlayPlaylist = { id, index ->
                    LibVlcProvider.warmUp(activity)
                    navController.navigate(playlistRoute(id, index)) { popUpTo(HOME_ROUTE) }
                },
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) }
            )
        }

        composable(SETTINGS_ROUTE) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = PLAYER_ROUTE,
            arguments = listOf(
                navArgument(ARG_URI) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(ARG_PLAYLIST) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(ARG_INDEX) {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { entry ->
            val uri = entry.arguments?.getString(ARG_URI)?.let(Uri::parse)
            val playlistId = entry.arguments?.getString(ARG_PLAYLIST)
            val index = entry.arguments?.getInt(ARG_INDEX) ?: 0
            FlintPlayer(
                uri = uri,
                playlistId = playlistId,
                startIndex = index,
                activity = activity
            )
        }
    }
    }
}