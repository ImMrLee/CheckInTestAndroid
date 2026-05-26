package com.example.checkintest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 这里可以启动你的 MainActivity 或者显示提示
            val startIntent = Intent(context, MainActivity::class.java)
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(startIntent)

            Toast.makeText(context, "Keep Alive", Toast.LENGTH_SHORT).show()
        }
    }
}