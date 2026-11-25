package com.safewave.guardian

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 휴대폰을 껐다 켜도 로그인되어 있으면 자동으로 다시 지켜봄 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Auth.hasSaved(context)) WatchService.start(context)
    }
}
