package com.safewave.guardian

import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.View
import android.widget.RemoteViews
import com.safewave.guardian.ui.look
import com.safewave.guardian.ui.timeText

/** 갤럭시 플립은 접으면 안쪽 화면(0번)이 꺼지고 커버 화면(1번)만 켜져 있음 */
object Cover {
    const val COVER_DISPLAY_ID = 1

    /** 접혀 있으면 커버 화면 번호, 아니면 null */
    fun foldedCoverId(ctx: Context): Int? = runCatching {
        val dm = ctx.getSystemService(DisplayManager::class.java)
        val main = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val cover = dm.getDisplay(COVER_DISPLAY_ID) ?: return@runCatching null
        if (main != null && main.state == Display.STATE_OFF && cover.state != Display.STATE_OFF) COVER_DISPLAY_ID else null
    }.getOrNull()

    /** 접혀 있으면 커버 화면에 열리도록 하는 옵션 */
    fun launchOptions(ctx: Context): Bundle? {
        val id = foldedCoverId(ctx) ?: return null
        return ActivityOptions.makeBasic().setLaunchDisplayId(id).toBundle()
    }
}

class StatusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        StatusWidget.update(context)
    }
}

/** 커버 화면·홈 화면 위젯: 어르신 상태를 큰 글씨로 */
object StatusWidget {
    private var lastKey = ""

    fun update(ctx: Context, force: Boolean = false) {
        val mgr = AppWidgetManager.getInstance(ctx) ?: return
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, StatusWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val l = Store.live.value
        val p = Store.profile.value
        val loggedIn = Auth.hasSaved(ctx)
        val k = look(l, p.displayName)
        val key = "$loggedIn|${l.status}|${l.acking}|${l.graceLeft}|${p.displayName}|${l.lastSeen / 60000}"
        if (!force && key == lastKey) return
        lastKey = key

        val v = RemoteViews(ctx.packageName, R.layout.widget_status)
        val bg = when {
            !loggedIn -> R.drawable.wbg_off
            l.status == Status.EMERGENCY && !l.acking -> R.drawable.wbg_crit
            l.status == Status.GRACE || l.status == Status.EMERGENCY -> R.drawable.wbg_warn
            l.status == Status.NORMAL -> R.drawable.wbg_ok
            l.status == Status.EMPTY -> R.drawable.wbg_empty
            else -> R.drawable.wbg_off
        }
        v.setInt(R.id.w_root, "setBackgroundResource", bg)
        v.setTextViewText(R.id.w_name, if (loggedIn) "${p.displayName} 님" else "SafeWave")
        v.setTextViewText(R.id.w_title, if (loggedIn) k.title else "로그인이 필요해요")
        v.setTextViewText(R.id.w_sub, if (loggedIn) k.sub else "앱을 열어 로그인해 주세요")
        val alarming = loggedIn && l.alarming
        v.setViewVisibility(R.id.w_btn, if (alarming) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.w_time, if (l.lastSeen > 0) "센서 신호: ${timeText(l.lastSeen)}" else "")

        // 누르면: 긴급이면 경보 화면, 아니면 앱. 접혀 있으면 커버 화면에서 열림
        val target = if (alarming) Intent(ctx, AlarmActivity::class.java) else Intent(ctx, MainActivity::class.java)
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            ctx, if (alarming) 11 else 10, target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, Cover.launchOptions(ctx)
        )
        v.setOnClickPendingIntent(R.id.w_root, pi)
        runCatching { mgr.updateAppWidget(ids, v) }
    }
}
