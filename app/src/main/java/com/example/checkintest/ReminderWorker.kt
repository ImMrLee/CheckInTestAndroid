package com.example.checkintest

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val isCheckedInToday = isTodayCheckedIn()

        val (title, content) = when {
            !isCheckedInToday -> {
                when (currentHour) {
                    7 -> "早安！记得打卡" to "新的一天开始啦，别忘了打卡记录哦~"
                    12 -> "午间提醒" to "中午好，今天打卡了吗？"
                    16 -> "下午提醒" to "下午好，记得完成今日打卡！"
                    20 -> "晚间提醒" to "晚上好，今天打卡了吗？睡前确认一下吧"
                    else -> "打卡提醒" to "今天还没有打卡哦"
                }
            }
            else -> {
                when (currentHour) {
                    7 -> "早安" to "新的一天，祝你心情愉快！"
                    12 -> "午安" to "中午好，记得准时吃饭~"
                    16 -> "下午好" to "祝你下午工作顺利！"
                    20 -> "晚安" to "晚上好，好好休息，明天见！"
                    else -> "打卡成功" to "今日打卡已完成，继续保持！"
                }
            }
        }

        val notificationHelper = NotificationHelper(applicationContext)
        notificationHelper.sendReminder(title, content, isReminder = !isCheckedInToday)

        return Result.success()
    }

    private fun isTodayCheckedIn(): Boolean {
        val prefs = applicationContext.getSharedPreferences("checkin_prefs", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastCheckinDate = prefs.getString("last_check_in_date", "")
        return lastCheckinDate == today
    }
}