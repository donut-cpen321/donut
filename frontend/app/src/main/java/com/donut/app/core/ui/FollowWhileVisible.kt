package com.donut.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * Runs a screen's live updates only while the screen is visible: another screen on top or a backgrounded app
 * cancels follow, so no connection is kept open, and coming back runs it again, which reconnects (the backend
 * sends the current state first). Leaving the composition stops it too
 * @param key restarts follow when it changes, e.g. the view model
 * @param follow collects the screen's stream, e.g. viewModel::followUpdates
 */
@Composable
fun FollowWhileVisible(key: Any?, follow: suspend () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestFollow by rememberUpdatedState(follow)
    LaunchedEffect(lifecycleOwner, key) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            latestFollow()
        }
    }
}
