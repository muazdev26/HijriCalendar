package com.muazdev.hijricalendar.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue

const val HIJRI_JUMP_TO_TODAY_NOTIFICATION = "hijriJumpToToday"

@Composable
fun rememberDeepLinkJumpTick(): State<Int> {
    val tick = remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val token = NSNotificationCenter.defaultCenter.addObserverForName(
            name = HIJRI_JUMP_TO_TODAY_NOTIFICATION,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            tick.intValue++
        }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(token)
        }
    }
    return tick
}