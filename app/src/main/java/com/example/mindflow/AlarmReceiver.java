package com.example.mindflow;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "mindflow_alarm_channel_high";

    @Override
    public void onReceive(Context context, Intent intent) {
        String taskContent = intent.getStringExtra("task_content");

        // =================================================
        // 🧨 暴力启动逻辑：不管锁没锁屏，直接尝试启动 Activity
        // =================================================
        Intent fullScreenIntent = new Intent(context, AlarmActivity.class);
        fullScreenIntent.putExtra("task_content", taskContent);
        // 必须加这两个 Flag，否则无法在广播里启动 Activity
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // 尝试直接启动页面 (暴力法)
        context.startActivity(fullScreenIntent);

        // =================================================
        // 🛡️ 兜底逻辑：为了兼容 Android 10+ 后台限制，依然发送全屏通知
        // =================================================
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                (int) System.currentTimeMillis(),
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "强力闹钟提醒",
                    NotificationManager.IMPORTANCE_HIGH // 必须是 HIGH
            );
            // 关键：开启震动和声音权限
            channel.enableVibration(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            // 关键：允许绕过免打扰
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                channel.setAllowBubbles(true);
            }
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ 任务时间到！")
                .setContentText(taskContent)
                .setPriority(NotificationCompat.PRIORITY_MAX) // 设为最高优先级
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                // 关键：设置全屏意图
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}