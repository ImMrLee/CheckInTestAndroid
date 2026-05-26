package com.example.checkintest

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleReminders()
    }

    private fun scheduleReminders() {
        val workManager = WorkManager.getInstance(this)
        workManager.cancelAllWork()
        val reminderTimes = listOf(7, 12, 16, 20)

        reminderTimes.forEach { hour ->
            val workRequest = PeriodicWorkRequestBuilder<ReminderWorker>(
                1, TimeUnit.DAYS  // 每天重复
            ).setInitialDelay(
                calculateInitialDelay(hour),
                TimeUnit.MILLISECONDS
            ).setConstraints(
                Constraints.NONE
            ).addTag("reminder_$hour")
                .build()

            workManager.enqueueUniquePeriodicWork(
                "reminder_$hour",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

        fun calculateInitialDelay(targetHour: Int): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (target.before(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            return target.timeInMillis - now.timeInMillis
        }
    }