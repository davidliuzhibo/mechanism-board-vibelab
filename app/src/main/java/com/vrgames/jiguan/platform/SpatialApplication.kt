package com.vrgames.jiguan.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.vrgames.jiguan.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
