package com.safewave.guardian

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.safewave.guardian.ui.EmergencyScreen
import com.safewave.guardian.ui.SafeTheme
import kotlinx.coroutines.launch

/** 잠금 화면 위로 뜨는 긴급 경보 화면 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Store.fontLevel.value = Prefs(this).fontLevel

        setContent {
            val font by Store.fontLevel.collectAsState()
            val live by Store.live.collectAsState()
            val test by Store.testAlarm.collectAsState()
            val profile by Store.profile.collectAsState()
            val scope = rememberCoroutineScope()
            var busy by remember { mutableStateOf(false) }

            // 긴급이 풀리면 (사이트나 다른 보호자가 확인해도) 화면을 닫음
            LaunchedEffect(live.alarming, test) { if (!live.alarming && !test) finish() }

            SafeTheme(font) {
                Box(Modifier.fillMaxSize().systemBarsPadding()) {
                    EmergencyScreen(
                        profile.displayName, test && !live.alarming, busy, profile.contacts.firstOrNull { it.phone.isNotBlank() },
                        onAck = {
                            scope.launch {
                                busy = true
                                if (test && !live.alarming) Store.testAlarm.value = false else Store.ack(this@AlarmActivity)
                                busy = false
                            }
                        },
                        onSilence = { Store.silenced.value = true },
                        onCall = { dial(it.phone) },
                        on119 = { dial("119") }
                    )
                }
            }
        }
    }
}
