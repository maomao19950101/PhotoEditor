package com.photoeditor

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用程序类
 */
@HiltAndroidApp
class PhotoEditorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 应用初始化
    }
}
