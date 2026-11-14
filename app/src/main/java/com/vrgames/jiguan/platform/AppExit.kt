package com.vrgames.jiguan.platform

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator

/**
 * Leaving the game closes every space it opened (device 2026-11-14, 捏糖人: after quitting, the levels window stayed alone
 * in the home space). The levels window is a separate window with its own task, so finishing the stage's task alone left
 * it behind; and a close asked of the navigator while the stage is being torn down did not always arrive.
 */
object AppExit {
    private const val TAG = "AppExit"

    /** True while the stage is up. The levels window closes itself when it goes down. */
    var stageAlive by mutableStateOf(false)

    /** Finishes every task of this app: the stage and any window opened from it. */
    fun finishAll(context: Context) {
        val am = context.getSystemService(ActivityManager::class.java)
        val tasks = runCatching { am?.appTasks }.getOrNull().orEmpty()
        Log.i(TAG, "finish ${tasks.size} task(s)")
        tasks.forEach { runCatching { it.finishAndRemoveTask() } }
        context.activity()?.let { runCatching { it.finishAndRemoveTask() } }
    }

    /** Put inside the levels window: once the stage is gone, the window goes too. */
    @Composable
    fun CloseWithStage(windowId: String) {
        val context = LocalContext.current
        val navigator = LocalSpatialNavigator.current
        LaunchedEffect(stageAlive) {
            if (stageAlive) return@LaunchedEffect
            // A moment's grace, in case the window is drawn before the stage on a restore.
            kotlinx.coroutines.delay(1500)
            if (stageAlive) return@LaunchedEffect
            Log.i(TAG, "stage gone: closing $windowId")
            runCatching { navigator.closeWindowContainer(windowId) }
            context.activity()?.let { runCatching { it.finishAndRemoveTask() } }
        }
    }

    private fun Context.activity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
