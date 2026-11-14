package com.vrgames.jiguan.game

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.vrgames.jiguan.core.Levels

/**
 * The test hook (debug builds only): adb shell am broadcast -a com.vrgames.jiguan.TEST -p
 * com.vrgames.jiguan --es cmd walk. The walk asks the solver for each level's best moves and calls the game
 * directly, so it proves the rules and the rendering, not that a pointing hand can reach a piece.
 *
 * cmd: walk · level --es id L5 · begin · solve (the rest of this level) · step (one best move) · select --es id H
 * · undo · hint · spread --es id 0|1 · grant --es id 3 · show · endshow · speed --es id 4 · reset · stop
 * · view --es id "x,y,z,yaw,scale,pitch"|reset (the board's pose for the emulator's fixed camera)
 */
object AutoPilot {
    const val ACTION = "com.vrgames.jiguan.TEST"
    const val TAG = "BoardTest"
    private val main = Handler(Looper.getMainLooper())
    var debug = false; private set
    private var at = 0
    private var walking = false

    fun register(context: Context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        debug = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = command(i.getStringExtra("cmd") ?: "", i.getStringExtra("id"))
        }
        ContextCompat.registerReceiver(context.applicationContext, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        Log.i(TAG, "ready")
    }

    fun ready() = Log.i(TAG, "table ready")

    private fun command(cmd: String, id: String?) {
        Log.i(TAG, "cmd $cmd ${id.orEmpty()}")
        when (cmd) {
            "walk" -> { main.removeCallbacksAndMessages(null); walking = true; at = 0; main.postDelayed(::walkLevel, 500) }
            "level" -> id?.let { Levels.byId(it) }?.let { Game.grant(1); Game.intro(it) }
            "lift" -> Game.lift(id?.toIntOrNull() ?: 1)
            "turn" -> Game.turn(id?.toIntOrNull() ?: 1)
            "begin" -> Game.begin()
            "solve" -> { walking = false; playOn() }
            "step" -> bestMove()?.let { Game.doMove(it) }
            "select" -> id?.let { Game.select(it) }
            "undo" -> Game.undo()
            "hint" -> Game.askHint()
            "spread" -> Game.spread = id != "0"
            "grant" -> Game.grant(id?.toIntOrNull() ?: 3)
            "show" -> Game.show()
            "endshow" -> Game.endShow()
            "speed" -> Game.timeScale = id?.toDoubleOrNull() ?: 1.0
            "view" -> Game.debugView = id?.takeIf { it != "reset" }
            "reset" -> { main.removeCallbacksAndMessages(null); walking = false; Game.wipe() }
            "stop" -> { main.removeCallbacksAndMessages(null); walking = false }
            else -> Log.w(TAG, "unknown command $cmd")
        }
    }

    private fun bestMove() = Game.play?.let { p -> p.rules.solve(p.state)?.firstOrNull() }

    private fun walkLevel() {
        if (at >= Levels.ALL.size) {
            Log.i(TAG, "walk done stars=${Game.progress.stars}/${Game.progress.maxStars} trophies=${Game.progress.trophies}")
            walking = false
            return
        }
        Game.grant(0)
        Game.intro(Levels.ALL[at])
        main.postDelayed({ Game.begin(); main.postDelayed(::playOn, 800) }, 1500)
    }

    private fun playOn() {
        val p = Game.play ?: return
        if (Game.view?.busy == true) {
            main.postDelayed(::playOn, 200)
            return
        }
        when {
            p.won -> {
                Log.i(TAG, "result ${p.level.id} stars=${p.stars()} steps=${p.steps} par=${p.level.par}")
                if (walking) {
                    at++
                    main.postDelayed(::walkLevel, 2000)
                }
            }
            else -> {
                val m = bestMove()
                if (m == null) {
                    Log.w(TAG, "result ${p.level.id} STUCK")
                    return
                }
                Game.doMove(m)
                main.postDelayed(::playOn, 200)
            }
        }
    }
}
