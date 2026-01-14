package com.example.mindflow;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.CalendarContract;
import android.speech.tts.TextToSpeech; // ⭐ 1. 引入 TTS 包
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale; // ⭐ 1. 引入 Locale 包
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

// ⭐ 2. 修改类定义，实现 OnInitListener 接口
public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    // UI 变量
    private TextView tvStatus, tvSummary, tvTitle;
    private LinearLayout llTaskContainer;
    private ProgressBar progressBar;
    private Button btnRecord, btnSendText;
    private EditText etInput, etSearch;
    private ImageView ivSettings, ivFilter, ivShare, ivDeleteAll;

    // ⭐ 新增：TTS 变量
    private TextToSpeech tts;
    private ImageView ivTtsSpeak;

    // ⭐ 新增：声波控件
    private VoiceLineView voiceLineView;

    // 数据变量
    private MediaRecorder mediaRecorder;
    private String audioFileName;
    private JSONArray taskList = new JSONArray();
    private SharedPreferences sharedPreferences;
    private String currentFilterDate = "全部";

    // ⭐ 新增：用于刷新声波的 Handler
    private Handler voiceHandler = new Handler(Looper.getMainLooper());
    private Runnable voiceRunnable;

    private static final String DEFAULT_IP = "YOUR_SERVER_IP";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences("MindFlowData", MODE_PRIVATE);

        // 绑定界面控件
        tvTitle = findViewById(R.id.tv_title);
        tvStatus = findViewById(R.id.tv_status);
        tvSummary = findViewById(R.id.tv_summary);
        llTaskContainer = findViewById(R.id.ll_task_container);
        progressBar = findViewById(R.id.progress_bar);
        btnRecord = findViewById(R.id.btn_record);
        etInput = findViewById(R.id.et_input);
        etSearch = findViewById(R.id.et_search);
        btnSendText = findViewById(R.id.btn_send_text);

        ivSettings = findViewById(R.id.iv_settings);
        ivFilter = findViewById(R.id.iv_filter);
        ivShare = findViewById(R.id.iv_share);
        ivDeleteAll = findViewById(R.id.iv_delete_all);

        // ⭐ 绑定小喇叭控件
        ivTtsSpeak = findViewById(R.id.iv_tts_speak);

        // ⭐ 绑定声波控件
        voiceLineView = findViewById(R.id.voice_line_view);

        audioFileName = getExternalCacheDir().getAbsolutePath() + "/demo_audio.m4a";

        // ⭐ 初始化 TTS 引擎
        tts = new TextToSpeech(this, this);

        checkPermissions();
        loadHistory();

        // --- 事件监听设置 ---

        // 设置 IP
        ivSettings.setOnClickListener(v -> showIpSettingDialog());

        // 📅 日期筛选
        ivFilter.setOnClickListener(v -> showDateFilterDialog());

        // 📤 分享
        ivShare.setOnClickListener(v -> shareTasks());

        // 🗑️ 批量删除
        ivDeleteAll.setOnClickListener(v -> showBatchDeleteDialog());

        // 🔊 ⭐ TTS 点击播报事件
        ivTtsSpeak.setOnClickListener(v -> {
            String content = tvSummary.getText().toString();
            if (!content.isEmpty() && !content.equals("暂无内容...") && !content.equals("无总结")) {
                speakText(content);
            } else {
                Toast.makeText(this, "没什么可读的", Toast.LENGTH_SHORT).show();
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { renderTaskList(); }
            public void afterTextChanged(Editable s) {}
        });

        // 🎤 录音按钮
        btnRecord.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                performHapticFeedback();
                startRecording();
                btnRecord.setText("松开 结束");
                voiceLineView.setVisibility(View.VISIBLE);
                startVoiceAnimation();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                performHapticFeedback();
                stopRecording();
                btnRecord.setText("按住 录音");
                stopVoiceAnimation();
                voiceLineView.setVisibility(View.GONE);
                voiceLineView.clear();
                uploadAudio();
            }
            return true;
        });

        btnSendText.setOnClickListener(v -> {
            String content = etInput.getText().toString().trim();
            if (content.isEmpty()) return;
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            uploadText(content);
        });
    }

    // ==========================================
    // TTS 语音功能模块核心代码
    // ==========================================

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            // 设置语言为中文
            int result = tts.setLanguage(Locale.CHINESE);

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "您的手机不支持中文语音包", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "语音引擎启动失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 2. 朗读方法
    private void speakText(String text) {
        if (tts != null) {
            // QUEUE_FLUSH 表示：打断正在说的话，立即说新的
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            Toast.makeText(this, "正在播报...", Toast.LENGTH_SHORT).show();
        }
    }

    // 3. 销毁资源 (防止退出 App 后还在占用后台)
    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    // ==========================================
    // 震动反馈方法
    // ==========================================
    private void performHapticFeedback() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(50);
        }
    }

    // ==========================================
    // 声波刷新逻辑
    // ==========================================
    private void startVoiceAnimation() {
        if (voiceRunnable == null) {
            voiceRunnable = new Runnable() {
                @Override
                public void run() {
                    if (mediaRecorder != null) {
                        try {
                            int maxAmplitude = mediaRecorder.getMaxAmplitude();
                            int volume = (int) (Math.sqrt(maxAmplitude) / 2);
                            if (volume > 100) volume = 100;
                            voiceLineView.addVolume(volume);
                        } catch (Exception e) {}
                    }
                    voiceHandler.postDelayed(this, 100);
                }
            };
        }
        voiceHandler.post(voiceRunnable);
    }

    private void stopVoiceAnimation() {
        if (voiceRunnable != null) {
            voiceHandler.removeCallbacks(voiceRunnable);
        }
    }

    private void scheduleAlarm(String taskContent, String timestampStr) {
        if (timestampStr == null || timestampStr.isEmpty()) return;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = sdf.parse(timestampStr);
            long triggerTime = date.getTime();
            if (triggerTime <= System.currentTimeMillis()) return;
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.putExtra("task_content", taskContent);
            int requestCode = taskContent.hashCode();
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    try { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent); Toast.makeText(this, "⏰ 精确提醒已设置", Toast.LENGTH_SHORT).show(); } catch (SecurityException e) { alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent); }
                } else { alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent); }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent); }
            else { alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent); }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void handleServerResponse(Response response, String responseBody) {
        runOnUiThread(() -> { progressBar.setVisibility(View.GONE); tvStatus.setText("灵感流"); });
        if (response.isSuccessful()) {
            try {
                JSONObject jsonObject = new JSONObject(responseBody);
                JSONObject data = jsonObject.getJSONObject("data");
                String summary = data.optString("smart_summary", "无总结");
                JSONArray newActions = data.optJSONArray("action_items");
                runOnUiThread(() -> {
                    tvSummary.setText(summary);
                    if (newActions != null) {
                        for (int i = 0; i < newActions.length(); i++) {
                            try {
                                JSONObject newItem = newActions.getJSONObject(i);
                                taskList.put(newItem);
                                String timestamp = newItem.optString("timestamp", "");
                                String task = newItem.optString("task", "");
                                if (!timestamp.isEmpty()) scheduleAlarm(task, timestamp);
                            } catch (Exception e) {}
                        }
                    }
                    saveHistory(); renderTaskList();
                });
            } catch (Exception e) { e.printStackTrace(); }
        } else { runOnUiThread(() -> Toast.makeText(this, "服务器错误", Toast.LENGTH_SHORT).show()); }
    }

    private void renderTaskList() {
        llTaskContainer.removeAllViews();
        if (currentFilterDate.equals("全部")) tvTitle.setText("灵感流");
        else tvTitle.setText("📅 " + currentFilterDate);

        String keyword = etSearch.getText().toString().trim();
        boolean hasItem = false;

        for (int i = taskList.length() - 1; i >= 0; i--) {
            try {
                JSONObject item = taskList.getJSONObject(i);
                final int index = i;

                String task = item.optString("task", "无内容");
                String time = item.optString("time", "");
                String location = item.optString("location", "");
                String category = item.optString("category", "其他");
                boolean isDone = item.optBoolean("is_done", false);

                String sysDate = item.optString("sys_date", "");
                if (sysDate.isEmpty()) {
                    Calendar calendar = Calendar.getInstance();
                    sysDate = calendar.get(Calendar.YEAR) + "年" + (calendar.get(Calendar.MONTH) + 1) + "月";
                    item.put("sys_date", sysDate); saveHistory();
                }

                if (!currentFilterDate.equals("全部") && !sysDate.equals(currentFilterDate)) continue;
                if (!keyword.isEmpty()) {
                    if (!task.contains(keyword) && !location.contains(keyword) && !category.contains(keyword) && !time.contains(keyword)) continue;
                }

                hasItem = true;

                View taskView = LayoutInflater.from(this).inflate(R.layout.item_task, llTaskContainer, false);
                LinearLayout rootLayout = taskView.findViewById(R.id.root_layout);
                CheckBox cbDone = taskView.findViewById(R.id.cb_done);
                TextView tvContent = taskView.findViewById(R.id.tv_task_content);
                TextView tvTime = taskView.findViewById(R.id.tv_task_time);
                TextView tvLocation = taskView.findViewById(R.id.tv_task_location);
                TextView tvCategory = taskView.findViewById(R.id.tv_category);
                TextView tvSystemDate = taskView.findViewById(R.id.tv_system_date);
                ImageView ivEdit = taskView.findViewById(R.id.iv_edit);
                ImageView ivCalendar = taskView.findViewById(R.id.iv_calendar);
                LinearLayout llSubTasks = taskView.findViewById(R.id.ll_sub_tasks_container);

                tvContent.setText(task);
                tvCategory.setText(category);
                tvSystemDate.setText(sysDate);

                llSubTasks.removeAllViews();
                JSONArray subTasks = item.optJSONArray("sub_tasks");
                if (subTasks != null && subTasks.length() > 0) {
                    llSubTasks.setVisibility(View.VISIBLE);
                    for (int k = 0; k < subTasks.length(); k++) {
                        TextView tvSub = new TextView(this);
                        tvSub.setText("• " + subTasks.optString(k));
                        tvSub.setTextSize(13); tvSub.setTextColor(Color.parseColor("#666666")); tvSub.setPadding(0, 4, 0, 4);
                        llSubTasks.addView(tvSub);
                    }
                } else { llSubTasks.setVisibility(View.GONE); }

                int bgColor, textColor;
                switch (category) {
                    case "工作": bgColor=ContextCompat.getColor(this, R.color.tag_work_bg); textColor=ContextCompat.getColor(this, R.color.tag_work_text); break;
                    case "学习": bgColor=ContextCompat.getColor(this, R.color.tag_study_bg); textColor=ContextCompat.getColor(this, R.color.tag_study_text); break;
                    case "生活": bgColor=ContextCompat.getColor(this, R.color.tag_life_bg); textColor=ContextCompat.getColor(this, R.color.tag_life_text); break;
                    case "紧急": bgColor=ContextCompat.getColor(this, R.color.tag_urgent_bg); textColor=ContextCompat.getColor(this, R.color.tag_urgent_text); break;
                    default: bgColor=ContextCompat.getColor(this, R.color.tag_other_bg); textColor=ContextCompat.getColor(this, R.color.tag_other_text); break;
                }
                android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
                drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                drawable.setColor(bgColor);
                drawable.setCornerRadius(12);

                tvCategory.setBackground(drawable);
                tvCategory.setTextColor(textColor);
                tvCategory.setPadding(12, 4, 12, 4);

                if (!time.isEmpty()) { tvTime.setText("⏰ " + time); tvTime.setVisibility(View.VISIBLE); } else { tvTime.setVisibility(View.GONE); }
                if (!location.isEmpty()) { tvLocation.setText("📍 " + location); tvLocation.setVisibility(View.VISIBLE); } else { tvLocation.setVisibility(View.GONE); }

                cbDone.setChecked(isDone);
                updateTaskStyle(tvContent, isDone);

                cbDone.setOnClickListener(v -> {
                    performHapticFeedback();
                    boolean checked = ((CheckBox) v).isChecked();
                    try { item.put("is_done", checked); saveHistory(); updateTaskStyle(tvContent, checked); } catch (JSONException e) {}
                });

                ivCalendar.setOnClickListener(v -> addCalendarEvent(task, location, time));
                ivEdit.setOnClickListener(v -> showEditDialog(index, item));

                if (rootLayout != null) {
                    rootLayout.setOnClickListener(v -> showEditDialog(index, item));
                    rootLayout.setOnLongClickListener(v -> { showDeleteDialog(index); return true; });
                }

                llTaskContainer.addView(taskView);
            } catch (JSONException e) { e.printStackTrace(); }
        }

        if (!hasItem) {
            LinearLayout emptyLayout = new LinearLayout(this);
            emptyLayout.setOrientation(LinearLayout.VERTICAL);
            emptyLayout.setGravity(Gravity.CENTER);
            emptyLayout.setPadding(0, 150, 0, 0);
            ImageView imageView = new ImageView(this);
            imageView.setImageResource(R.drawable.ic_empty_state);
            imageView.setLayoutParams(new LinearLayout.LayoutParams(250, 250));
            imageView.setAlpha(0.5f); imageView.setColorFilter(Color.LTGRAY);
            TextView textView = new TextView(this);
            if (!keyword.isEmpty()) textView.setText("未找到相关任务 🔍");
            else if (!currentFilterDate.equals("全部")) textView.setText(currentFilterDate + " 无记录 📅");
            else textView.setText("暂无待办，享受当下 ☕️");
            textView.setTextColor(Color.parseColor("#999999"));
            textView.setTextSize(16); textView.setGravity(Gravity.CENTER); textView.setPadding(0, 30, 0, 0);
            emptyLayout.addView(imageView); emptyLayout.addView(textView);
            llTaskContainer.addView(emptyLayout);
        }
    }

    private void showBatchDeleteDialog() {
        if (taskList.length() == 0) {
            Toast.makeText(this, "列表是空的", Toast.LENGTH_SHORT).show();
            return;
        }

        int completedCount = 0;
        for (int i = 0; i < taskList.length(); i++) {
            if (taskList.optJSONObject(i).optBoolean("is_done")) {
                completedCount++;
            }
        }

        String[] options = {
                "🧹 删除所有已完成任务 (" + completedCount + "个)",
                "🧨 清空当前显示的所有任务"
        };

        new AlertDialog.Builder(this)
                .setTitle("批量管理")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        deleteCompletedTasks();
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("高能预警")
                                .setMessage("确定要清空当前列表吗？此操作无法恢复！")
                                .setPositiveButton("确定清空", (d, w) -> deleteAllVisibleTasks())
                                .setNegativeButton("取消", null)
                                .show();
                    }
                })
                .show();
    }

    private void deleteCompletedTasks() {
        JSONArray newTaskList = new JSONArray();
        boolean hasDeleted = false;

        for (int i = 0; i < taskList.length(); i++) {
            JSONObject item = taskList.optJSONObject(i);
            if (!item.optBoolean("is_done")) {
                newTaskList.put(item);
            } else {
                hasDeleted = true;
            }
        }

        if (hasDeleted) {
            String backup = taskList.toString();
            taskList = newTaskList;
            saveHistory();
            renderTaskList();
            showUndoSnackbar(backup, "已清理所有完成任务");
        } else {
            Toast.makeText(this, "没有已完成的任务", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteAllVisibleTasks() {
        JSONArray newTaskList = new JSONArray();
        String keyword = etSearch.getText().toString().trim();

        for (int i = 0; i < taskList.length(); i++) {
            JSONObject item = taskList.optJSONObject(i);
            String sysDate = item.optString("sys_date", "");
            String task = item.optString("task");
            String location = item.optString("location");
            String category = item.optString("category");

            boolean shouldDelete = true;

            if (!currentFilterDate.equals("全部") && !sysDate.equals(currentFilterDate)) {
                shouldDelete = false;
            }
            if (!keyword.isEmpty()) {
                if (!task.contains(keyword) && !location.contains(keyword) && !category.contains(keyword)) {
                    shouldDelete = false;
                }
            }
            if (!shouldDelete) {
                newTaskList.put(item);
            }
        }

        String backup = taskList.toString();
        taskList = newTaskList;
        saveHistory();
        renderTaskList();
        showUndoSnackbar(backup, "已清空当前列表");
    }

    private void showUndoSnackbar(String backupJson, String message) {
        Snackbar.make(llTaskContainer, message, Snackbar.LENGTH_LONG)
                .setAction("撤销", v -> {
                    try {
                        taskList = new JSONArray(backupJson);
                        saveHistory();
                        renderTaskList();
                    } catch (JSONException e) { e.printStackTrace(); }
                })
                .show();
    }

    private void showDateFilterDialog() {
        Set<String> dateSet = new HashSet<>();
        for (int i = 0; i < taskList.length(); i++) {
            JSONObject item = taskList.optJSONObject(i);
            if (item != null) dateSet.add(item.optString("sys_date", "未知日期"));
        }
        List<String> dateList = new ArrayList<>(dateSet);
        Collections.sort(dateList, Collections.reverseOrder());
        dateList.add(0, "全部");
        String[] options = dateList.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("筛选月份").setItems(options, (dialog, which) -> {
            currentFilterDate = options[which];
            renderTaskList();
            Toast.makeText(this, "已切换为: " + currentFilterDate, Toast.LENGTH_SHORT).show();
        }).show();
    }

    private void showDeleteDialog(int index) { new AlertDialog.Builder(this).setTitle("删除提醒").setMessage("确定删除？").setPositiveButton("删除", (dialog,which)->{try{JSONObject del=taskList.getJSONObject(index); taskList.remove(index); saveHistory(); renderTaskList(); Snackbar.make(llTaskContainer,"已删除",Snackbar.LENGTH_LONG).setAction("撤销",v->{try{taskList.put(del);saveHistory();renderTaskList();}catch(Exception e){}}).show();}catch(Exception e){}}).setNegativeButton("取消",null).show(); }
    private void showEditDialog(int index, JSONObject item) { AlertDialog.Builder b=new AlertDialog.Builder(this); View v=LayoutInflater.from(this).inflate(R.layout.dialog_edit,null); EditText t1=v.findViewById(R.id.et_edit_task),t2=v.findViewById(R.id.et_edit_time),t3=v.findViewById(R.id.et_edit_location); t1.setText(item.optString("task"));t2.setText(item.optString("time"));t3.setText(item.optString("location")); b.setView(v).setPositiveButton("保存",(d,w)->{try{item.put("task",t1.getText().toString());item.put("time",t2.getText().toString());item.put("location",t3.getText().toString());taskList.put(index,item);saveHistory();renderTaskList();}catch(Exception e){}}).setNegativeButton("取消",null).show(); }
    private void addCalendarEvent(String t, String l, String d) { Intent i=new Intent(Intent.ACTION_INSERT); i.setData(CalendarContract.Events.CONTENT_URI); i.putExtra(CalendarContract.Events.TITLE,t); i.putExtra(CalendarContract.Events.EVENT_LOCATION,l); i.putExtra(CalendarContract.Events.DESCRIPTION,d); try{startActivity(i);}catch(Exception e){Toast.makeText(this,"无日历",Toast.LENGTH_SHORT).show();} }
    private void showIpSettingDialog() { EditText et=new EditText(this); et.setText(sharedPreferences.getString("server_ip",DEFAULT_IP)); new AlertDialog.Builder(this).setView(et).setTitle("设置IP").setPositiveButton("保存",(d,w)->{sharedPreferences.edit().putString("server_ip",et.getText().toString().trim()).apply();}).show(); }
    private String getApiUrl(String endpoint) {
        String ip = sharedPreferences.getString("server_ip", DEFAULT_IP);
        if (ip.startsWith("192") || ip.startsWith("10") || ip.startsWith("172")) {
            return "http://" + ip + ":8000/api/v1/meeting/" + endpoint;
        } else {
            return "http://" + ip + "/api/v1/meeting/" + endpoint;
        }
    }
    private void updateTaskStyle(TextView tv, boolean isDone) { if(isDone){tv.setTextColor(Color.LTGRAY);tv.setPaintFlags(tv.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG);}else{tv.setTextColor(Color.parseColor("#333333"));tv.setPaintFlags(tv.getPaintFlags()&(~Paint.STRIKE_THRU_TEXT_FLAG));} }
    private void saveHistory() { sharedPreferences.edit().putString("history_tasks",taskList.toString()).apply(); }
    private void loadHistory() { try{taskList=new JSONArray(sharedPreferences.getString("history_tasks","[]"));renderTaskList();}catch(Exception e){} }
    private void uploadAudio() { progressBar.setVisibility(View.VISIBLE); File f=new File(audioFileName); if(!f.exists())return; OkHttpClient c=new OkHttpClient(); RequestBody b=new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("audio_file",f.getName(),RequestBody.create(f,MediaType.parse("audio/mp4"))).build(); c.newCall(new Request.Builder().url(getApiUrl("analyze")).post(b).build()).enqueue(new Callback(){public void onFailure(Call c,IOException e){runOnUiThread(()->progressBar.setVisibility(View.GONE));}public void onResponse(Call c,Response r)throws IOException{handleServerResponse(r,r.body().string());}}); }
    private void uploadText(String t) { progressBar.setVisibility(View.VISIBLE); etInput.setText(""); OkHttpClient c=new OkHttpClient(); JSONObject j=new JSONObject(); try{j.put("text",t);}catch(Exception e){} RequestBody b=RequestBody.create(j.toString(),MediaType.parse("application/json")); c.newCall(new Request.Builder().url(getApiUrl("analyze_text")).post(b).build()).enqueue(new Callback(){public void onFailure(Call c,IOException e){runOnUiThread(()->progressBar.setVisibility(View.GONE));}public void onResponse(Call c,Response r)throws IOException{handleServerResponse(r,r.body().string());}}); }
    private void startRecording() {
        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(audioFileName);
            mediaRecorder.setAudioSamplingRate(16000);
            mediaRecorder.setAudioEncodingBitRate(64000);
            mediaRecorder.setAudioChannels(1);
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "录音启动失败", Toast.LENGTH_SHORT).show();
        }
    }
    private void stopRecording() { if(mediaRecorder!=null){try{mediaRecorder.stop();mediaRecorder.release();}catch(Exception e){};mediaRecorder=null;} }
    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.RECORD_AUDIO);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.VIBRATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.POST_NOTIFICATIONS); }
        if (!permissions.isEmpty()) ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), 1);
    }
    // ==========================================
    // 补全：分享/导出功能
    // ==========================================
    private void shareTasks() {
        if (taskList.length() == 0) {
            Toast.makeText(this, "列表为空，没啥好分享的", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        String keyword = etSearch.getText().toString().trim();

        // 1. 构建头部信息
        sb.append("📝 灵感流笔记 - 任务清单\n");
        sb.append("📅 导出时间：").append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())).append("\n");
        if (!currentFilterDate.equals("全部")) {
            sb.append("📂 筛选月份：").append(currentFilterDate).append("\n");
        }
        sb.append("--------------------------------\n\n");

        boolean hasExportItem = false;

        // 2. 遍历并构建任务文本
        for (int i = taskList.length() - 1; i >= 0; i--) {
            JSONObject item = taskList.optJSONObject(i);
            if (item == null) continue;

            // 过滤逻辑（只导出当前筛选和搜索可见的任务）
            String sysDate = item.optString("sys_date", "");
            if (!currentFilterDate.equals("全部") && !sysDate.equals(currentFilterDate)) continue;

            String task = item.optString("task");
            String time = item.optString("time");
            String location = item.optString("location");

            if (!keyword.isEmpty()) {
                if (!task.contains(keyword) && !location.contains(keyword) && !time.contains(keyword)) continue;
            }

            hasExportItem = true;
            boolean isDone = item.optBoolean("is_done");

            // 拼接单条任务
            sb.append(isDone ? "✅ " : "🔲 ").append(task).append("\n");
            if (!time.isEmpty()) sb.append("   ⏰ ").append(time);
            if (!location.isEmpty()) sb.append("   📍 ").append(location);
            sb.append("\n\n");
        }

        if (!hasExportItem) {
            Toast.makeText(this, "当前筛选条件下无数据导出", Toast.LENGTH_SHORT).show();
            return;
        }

        sb.append("--------------------------------\nGenerated by MindFlow AI");

        // 3. 调用系统分享
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(shareIntent, "分享清单到..."));
    }
}
