package com.example.mindflow;

import android.app.KeyguardManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AlarmActivity extends AppCompatActivity {

    private Ringtone ringtone;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 关键：让屏幕在锁屏状态下也能亮起！
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null) keyguardManager.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }

        setContentView(R.layout.activity_alarm);

        // 2. 显示任务内容
        String content = getIntent().getStringExtra("task_content");
        TextView tvContent = findViewById(R.id.tv_alarm_content);
        tvContent.setText(content != null ? content : "该去完成任务了！");

        // 3. 开始播放闹钟铃声 (循环)
        startAlarm();

        // 4. 点击关闭按钮
        Button btnStop = findViewById(R.id.btn_stop_alarm);
        btnStop.setOnClickListener(v -> {
            stopAlarm();
            finish(); // 关闭页面
        });
    }

    private void startAlarm() {
        try {
            // 获取系统闹钟音效
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

            ringtone = RingtoneManager.getRingtone(getApplicationContext(), alarmUri);

            // 设置为闹钟通道 (即使用户静音了媒体音量，闹钟也会响)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            }
            ringtone.play();

            // 开始震动 (长震动：1秒震，1秒停)
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            long[] pattern = {0, 1000, 1000};
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(pattern, 0); // 0表示无限循环
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopAlarm() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAlarm(); // 防止退出后还在响
    }
}