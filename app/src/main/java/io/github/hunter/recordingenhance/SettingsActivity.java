package io.github.hunter.recordingenhance;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import io.github.libxposed.service.HookedTarget;
import io.github.libxposed.service.XposedService;

public final class SettingsActivity extends Activity {
    private static final String[] SHORTCUTS = {"两下音量上", "两下音量下", "三下音量上", "三下音量下"};
    private static final String[] FEEDBACKS = {"短震动 · 60 毫秒", "长震动 · 250 毫秒", "亮屏"};
    private AssistantApplication mApplication;
    private TextView mStatus;
    private TextView mRuntime;
    private TextView mShortcutValue;
    private TextView mFeedbackValue;
    private View mShortcutRow;
    private View mFeedbackRow;
    private Switch mHideStatus;
    private Switch mHideLock;
    private Switch mHideLauncher;
    private Switch mHidePrivacyDot;
    private Switch mHideNotification;
    private Button mSave;
    private int mShortcut = 2;
    private int mFeedback;
    private boolean mSaving;
    private boolean mLoaded;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        mApplication = (AssistantApplication) getApplication();
        LinearLayout root = column();
        root.setBackgroundColor(getColor(R.color.page));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = column();
        content.setPadding(dp(22), dp(18), dp(22), dp(16));
        scroll.addView(content);
        TextView eyebrow = text("RECORDING ASSISTANT", 11, R.color.accent, true);
        eyebrow.setLetterSpacing(.14f);
        content.addView(eyebrow);
        TextView title = text("ColorOS录音助手", 27, R.color.ink, true);
        title.setPadding(0, dp(8), 0, dp(5));
        content.addView(title);
        content.addView(text("快捷录音，按你的习惯", 14, R.color.muted, false));

        LinearLayout statusCard = card(content, R.color.accent_soft);
        mStatus = text("正在连接 LSPosed", 15, R.color.accent, true);
        statusCard.addView(mStatus);
        mRuntime = text("检查模块运行状态…", 12, R.color.muted, false);
        mRuntime.setPadding(0, dp(6), 0, 0);
        statusCard.addView(mRuntime);

        section(content, "快捷录音", "锁屏时触发 · 连按总时长 1.2 秒");
        LinearLayout shortcuts = card(content, R.color.surface);
        mShortcutValue = text("", 14, R.color.accent, false);
        mShortcutRow = choice(shortcuts, "快捷键", mShortcutValue, () ->
                new AlertDialog.Builder(this).setTitle("选择快捷键")
                        .setSingleChoiceItems(SHORTCUTS, mShortcut, (dialog, which) -> {
                            mShortcut = which;
                            updateChoices();
                            dialog.dismiss();
                        }).setNegativeButton("取消", null).show());
        divider(shortcuts);
        mFeedbackValue = text("", 14, R.color.accent, false);
        mFeedbackRow = choice(shortcuts, "触发反馈", mFeedbackValue, () ->
                new AlertDialog.Builder(this).setTitle("选择反馈方式")
                        .setSingleChoiceItems(FEEDBACKS, mFeedback, (dialog, which) -> {
                            mFeedback = which;
                            updateChoices();
                            dialog.dismiss();
                        }).setNegativeButton("取消", null).show());

        section(content, "显示与入口", "按需显示录音提示和控制入口");
        LinearLayout display = card(content, R.color.surface);
        mHideNotification = toggle(display, "隐藏录音通知", "开启后，请进入录音 App 暂停或结束录音");
        divider(display);
        mHideStatus = toggle(display, "隐藏状态栏", "隐藏录音胶囊和通知小图标");
        divider(display);
        mHideLock = toggle(display, "隐藏锁屏通知", "只在解锁后的通知栏显示");
        divider(display);
        mHideLauncher = toggle(display, "隐藏桌面图标", "隐藏后，从 LSPosed → 模块设置进入");
        divider(display);
        mHidePrivacyDot = toggle(display, "隐藏录音隐私绿点", "仅原生录音 App 单独使用麦克风时隐藏；其他 App、相机仍显示");

        TextView info = text("生效说明", 14, R.color.ink, true);
        info.setPadding(0, dp(22), 0, dp(8));
        content.addView(info);
        content.addView(text("日常修改无需重启 App 或手机。快捷键与反馈用于下一次触发；显示开关同步刷新当前录音，桌面图标随桌面刷新。", 12, R.color.muted, false));
        TextView upgrade = text("安装新版本或修改作用域时，按上方运行状态提示操作。配置同步不等于替换系统中运行的旧版代码。", 12, R.color.muted, false);
        upgrade.setPadding(0, dp(8), 0, 0);
        content.addView(upgrade);
        TextView privacyNote = text("隐私绿点按使用来源变化更新，不轮询。录音与其他 App 同时使用麦克风或相机时保留提示。关闭录音通知只隐藏通知栏条目，录音前台服务继续运行。", 12, R.color.muted, false);
        privacyNote.setPadding(0, dp(8), 0, 0);
        content.addView(privacyNote);

        LinearLayout footer = column();
        footer.setPadding(dp(22), dp(10), dp(22), dp(12));
        footer.setBackgroundColor(getColor(R.color.surface));
        mSave = new Button(this);
        mSave.setText("保存并立即应用");
        mSave.setAllCaps(false);
        mSave.setTextSize(16);
        mSave.setTypeface(null, Typeface.BOLD);
        mSave.setTextColor(getColor(R.color.surface));
        mSave.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.accent)));
        mSave.setBackground(round(R.color.accent, 16));
        mSave.setOnClickListener(view -> save());
        footer.addView(mSave, new LinearLayout.LayoutParams(-1, dp(52)));
        TextView footnote = text("“废弃”会删除当前录音，重要内容请保存", 11, R.color.muted, false);
        footnote.setGravity(Gravity.CENTER);
        footnote.setPadding(0, dp(9), 0, 0);
        footer.addView(footnote);
        root.addView(footer);
        setContentView(root);
        if (state != null) {
            mShortcut = Settings.shortcut(state.getInt(Settings.SHORTCUT, 2));
            mFeedback = Settings.feedback(state.getInt(Settings.FEEDBACK, 0));
            mHideStatus.setChecked(state.getBoolean(Settings.HIDE_STATUS, true));
            mHideLock.setChecked(state.getBoolean(Settings.HIDE_LOCK, true));
            mHidePrivacyDot.setChecked(state.getBoolean(Settings.HIDE_PRIVACY_DOT, false));
            mHideNotification.setChecked(!state.getBoolean(Settings.SHOW_NOTIFICATION, true));
            mHideLauncher.setChecked(state.getBoolean("hide_launcher", false));
            mLoaded = state.getBoolean("loaded");
        }
        updateChoices();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(getColor(color));
        view.setLineSpacing(dp(2), 1);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(getColor(color));
        background.setCornerRadius(dp(radius));
        return background;
    }

    private LinearLayout card(LinearLayout parent, int color) {
        LinearLayout card = column();
        card.setPadding(dp(18), dp(12), dp(18), dp(12));
        card.setBackground(round(color, 20));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        parent.addView(card, params);
        return card;
    }

    private void section(LinearLayout parent, String title, String subtitle) {
        TextView heading = text(title, 16, R.color.ink, true);
        heading.setPadding(dp(2), dp(22), 0, dp(5));
        parent.addView(heading);
        parent.addView(text(subtitle, 12, R.color.muted, false));
    }

    private View choice(LinearLayout parent, String title, TextView value, Runnable click) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setMinimumHeight(dp(68));
        LinearLayout words = column();
        words.addView(text(title, 15, R.color.ink, true));
        value.setPadding(0, dp(4), 0, 0);
        words.addView(value);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text("›", 26, R.color.muted, false));
        row.setBackground(round(R.color.surface, 8));
        android.util.TypedValue selectable = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectable, true);
        row.setForeground(getDrawable(selectable.resourceId));
        row.setFocusable(true);
        row.setOnClickListener(view -> click.run());
        parent.addView(row);
        return row;
    }

    private Switch toggle(LinearLayout parent, String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(76));
        row.setPadding(0, dp(8), 0, dp(8));
        LinearLayout words = column();
        words.addView(text(title, 15, R.color.ink, true));
        TextView description = text(subtitle, 12, R.color.muted, false);
        description.setPadding(0, dp(4), dp(12), 0);
        words.addView(description);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this);
        toggle.setContentDescription(title + "，" + subtitle);
        toggle.setMinWidth(dp(48));
        toggle.setMinHeight(dp(48));
        row.addView(toggle);
        parent.addView(row);
        return toggle;
    }

    private void divider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(getColor(R.color.line));
        parent.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void updateChoices() {
        mShortcutValue.setText(SHORTCUTS[mShortcut]);
        mFeedbackValue.setText(FEEDBACKS[mFeedback]);
        mShortcutRow.setContentDescription("快捷键，" + SHORTCUTS[mShortcut]);
        mFeedbackRow.setContentDescription("触发反馈，" + FEEDBACKS[mFeedback]);
    }

    @Override protected void onStart() {
        super.onStart();
        mApplication.mOnChanged = this::load;
        load();
    }

    @Override protected void onStop() {
        mApplication.mOnChanged = null;
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt(Settings.SHORTCUT, mShortcut);
        state.putInt(Settings.FEEDBACK, mFeedback);
        state.putBoolean(Settings.HIDE_STATUS, mHideStatus.isChecked());
        state.putBoolean(Settings.HIDE_LOCK, mHideLock.isChecked());
        state.putBoolean(Settings.HIDE_PRIVACY_DOT, mHidePrivacyDot.isChecked());
        state.putBoolean(Settings.SHOW_NOTIFICATION, !mHideNotification.isChecked());
        state.putBoolean("hide_launcher", mHideLauncher.isChecked());
        state.putBoolean("loaded", mLoaded);
    }

    private void load() {
        SharedPreferences preferences = mApplication.mPreferences;
        mStatus.setText(preferences == null ? "尚未连接 LSPosed" : "已连接 · 支持配置热生效");
        enableControls(preferences != null && !mSaving);
        if (preferences != null && !mLoaded) {
            mShortcut = Settings.shortcut(preferences.getInt(Settings.SHORTCUT, 2));
            mFeedback = Settings.feedback(preferences.getInt(Settings.FEEDBACK, 0));
            mHideStatus.setChecked(preferences.getBoolean(Settings.HIDE_STATUS, true));
            mHideLock.setChecked(preferences.getBoolean(Settings.HIDE_LOCK, true));
            mHidePrivacyDot.setChecked(preferences.getBoolean(Settings.HIDE_PRIVACY_DOT, false));
            // 保留旧版 show_notification 存储语义，仅反转界面，兼容已加载的 Hook。
            mHideNotification.setChecked(!preferences.getBoolean(Settings.SHOW_NOTIFICATION, true));
            mHideLauncher.setChecked(getPackageManager().getComponentEnabledSetting(
                    new ComponentName(this, getPackageName() + ".Launcher")) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
            mLoaded = true;
            updateChoices();
        }
        checkRuntime();
    }

    private void enableControls(boolean enabled) {
        for (View view : new View[]{mShortcutRow, mFeedbackRow, mHideStatus, mHideLock, mHideLauncher, mHidePrivacyDot, mHideNotification, mSave}) {
            view.setEnabled(enabled);
            view.setAlpha(enabled ? 1 : .45f);
        }
    }

    private void checkRuntime() {
        XposedService service = mApplication.mService;
        if (service == null) {
            mRuntime.setText("请在 LSPosed 启用模块；首次启用后重启手机。");
            return;
        }
        new Thread(() -> {
            String message;
            try {
                boolean system = false, systemUi = false, restartSystem = false, restartRecorder = false, restartSystemUi = false;
                long version = getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
                for (HookedTarget target : service.getRunningTargets()) {
                    boolean outdated = target.getLoadedVersionCode() != version || target.getState() != HookedTarget.State.UP_TO_DATE;
                    if ("system".equals(target.getProcessName()) || "system_server".equals(target.getProcessName())) {
                        system = true;
                        restartSystem = outdated;
                    } else if ("com.coloros.soundrecorder".equals(target.getProcessName())) {
                        restartRecorder = outdated;
                    } else if ("com.android.systemui".equals(target.getProcessName())) {
                        systemUi = true;
                        restartSystemUi = outdated;
                    }
                }
                message = !system ? "未检测到系统模块，请确认作用域并重启手机一次。"
                        : restartSystem ? "系统仍运行旧版代码：升级完成后需重启手机一次。"
                        : !systemUi || restartSystemUi ? "系统界面尚未载入新版：确认勾选系统界面作用域并重启手机一次。"
                        : restartRecorder ? "系统已就绪；请先结束录音，再重新打开录音 App 以载入新版。"
                        : "运行版本已就绪。日常修改配置无需重启。";
            } catch (Exception e) {
                message = "暂无法确认运行版本。仅升级模块或改作用域后需要重启，普通配置无需重启。";
            }
            String result = message;
            runOnUiThread(() -> { if (!isDestroyed()) mRuntime.setText(result); });
        }, "CheckModuleVersion").start();
    }

    private void save() {
        SharedPreferences preferences = mApplication.mPreferences;
        if (preferences == null || mSaving) return;
        boolean hideLauncher = mHideLauncher.isChecked();
        SharedPreferences.Editor editor = preferences.edit()
                .putInt(Settings.SHORTCUT, mShortcut).putInt(Settings.FEEDBACK, mFeedback)
                .putBoolean(Settings.HIDE_STATUS, mHideStatus.isChecked())
                .putBoolean(Settings.HIDE_LOCK, mHideLock.isChecked())
                .putBoolean(Settings.HIDE_PRIVACY_DOT, mHidePrivacyDot.isChecked())
                .putBoolean(Settings.SHOW_NOTIFICATION, !mHideNotification.isChecked());
        mSaving = true;
        enableControls(false);
        new Thread(() -> {
            boolean saved;
            try { saved = editor.commit(); } catch (RuntimeException e) { saved = false; }
            boolean success = saved;
            runOnUiThread(() -> {
                mSaving = false;
                if (isDestroyed()) return;
                enableControls(mApplication.mPreferences != null);
                if (!success) {
                    mStatus.setText("保存失败，请检查连接后重试");
                    return;
                }
                try {
                    getPackageManager().setComponentEnabledSetting(new ComponentName(this, getPackageName() + ".Launcher"),
                            hideLauncher ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP);
                    mStatus.setText("已保存 · 配置已同步");
                } catch (RuntimeException e) {
                    mStatus.setText("配置已保存，但桌面图标修改失败");
                }
                checkRuntime();
            });
        }, "SaveSettings").start();
    }
}
