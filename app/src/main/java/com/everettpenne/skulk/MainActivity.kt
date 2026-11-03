package com.everettpenne.skulk

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.everettpenne.media.VideoPip
import com.everettpenne.media.enterVideoPip
import com.everettpenne.skulk.ui.SkulkApp

class MainActivity : ComponentActivity() {
    private var deepLink by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        if (savedInstanceState == null) deepLink = intent?.data
        setContent { SkulkApp(deepLink, onDeepLinkHandled = { deepLink = null }) }
        SkulkShortcuts.publish(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink = intent.data
    }

    // A full-screen video floats into a small window when the user leaves the app; anything else (images, browsing) does not.
    override fun onUserLeaveHint() {
        if (!enterVideoPip(this)) super.onUserLeaveHint()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        VideoPip.pipChanged(isInPictureInPictureMode)
    }
}
