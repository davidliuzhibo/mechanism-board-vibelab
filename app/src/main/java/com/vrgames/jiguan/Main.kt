package com.vrgames.jiguan

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.vrgames.jiguan.content.BoardStage
import com.vrgames.jiguan.content.LEVELS_ID
import com.vrgames.jiguan.content.LevelsWindow

/** Two linked spaces: the craftsman's desk (a full-immersion Stage) and the window with levels and trophies. */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme { BoardStage() }
        }
        WindowContainer(LEVELS_ID, Form.Planar, { defaultSize = WindowContainerSize(820.dp, 1000.dp) }) {
            PicoTheme { LevelsWindow() }
        }
    }
