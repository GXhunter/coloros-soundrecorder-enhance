package io.github.hunter.recordingenhance;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Parcel;
import android.service.notification.StatusBarNotification;
import android.os.UserHandle;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.VibratorManager;
import android.util.Log;
import android.view.KeyEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;

import io.github.libxposed.api.XposedModule;

public final class RecordingModule extends XposedModule {
    private static final String TAG = "RecordingEnhance";
    private static final String RECORDER = "com.coloros.soundrecorder";
    private static final String ENTRY = "oplus.multimedia.soundrecorder.slidebar.TransparentActivity";
    private static final String EXTRA = "io.github.hunter.recordingenhance.START_ONLY";
    private static final String DISCARD = "io.github.hunter.recordingenhance.DISCARD";
    private final TriplePress mGesture = new TriplePress();
    private final DiscardSession mDiscardSession = new DiscardSession();
    private SharedPreferences mPreferences;
    private WeakReference<Object> mNotificationController = new WeakReference<>(null);
    private SharedPreferences.OnSharedPreferenceChangeListener mDisplayListener;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        try {
            mPreferences = getRemotePreferences(Settings.GROUP);
        } catch (RuntimeException e) {
            log(Log.ERROR, TAG, "读取配置失败，使用默认设置", e);
        }
        log(Log.INFO, TAG, "Loaded process=" + param.getProcessName() + " system=" + param.isSystemServer());
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        log(Log.INFO, TAG, "System callback entered");
        installNotificationChannelSettings(param.getClassLoader());
        try {
            Class<?> policy = param.getClassLoader().loadClass("com.android.server.policy.PhoneWindowManager");
            Field contextField = policy.getDeclaredField("mContext");
            contextField.setAccessible(true);
            Method intercept = policy.getDeclaredMethod("interceptKeyBeforeQueueing", KeyEvent.class, int.class);
            hook(intercept).intercept(chain -> {
                KeyEvent event = (KeyEvent) chain.getArg(0);
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    try {
                        Context context = (Context) contextField.get(chain.getThisObject());
                        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
                        int shortcut = Settings.shortcut(mPreferences == null ? 2 : mPreferences.getInt(Settings.SHORTCUT, 2));
                        boolean eligible = event.getKeyCode() == Settings.keyCode(shortcut)
                                && !event.isCanceled() && keyguard != null && keyguard.isKeyguardLocked();
                        if (mGesture.release(event.getEventTime(), event.getEventTime() - event.getDownTime(), eligible, shortcut)) {
                            int feedback = Settings.feedback(mPreferences == null ? 0 : mPreferences.getInt(Settings.FEEDBACK, 0));
                            new Handler(Looper.getMainLooper()).post(() -> startRecorder(context, feedback));
                        }
                    } catch (Exception e) {
                        log(Log.ERROR, TAG, "音量快捷键处理失败", e);
                    }
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "锁屏音量快捷键 Hook 已加载");
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "系统版本不兼容，快捷键未启用", e);
        }
    }

    // 在 system_server 中执行，使用系统进程已有的震动权限。
    @SuppressLint("MissingPermission")
    private void startRecorder(Context context, int feedback) {
        try {
            KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
            if (keyguard == null || !keyguard.isKeyguardLocked()) return;
            if (context.getPackageManager().getPackageInfo(RECORDER, 0).getLongVersionCode() != 17008008L) {
                log(Log.ERROR, TAG, "录音 App 已更新，需要重新核对启动接口");
                return;
            }
            int userId = (int) Class.forName("android.app.ActivityManager").getMethod("getCurrentUser").invoke(null);
            UserHandle user = (UserHandle) UserHandle.class.getMethod("of", int.class).invoke(null, userId);
            Intent intent = new Intent(EXTRA)
                    .setClassName(RECORDER, ENTRY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA, true).putExtra("assistant_feedback", feedback);
            Context.class.getMethod("startActivityAsUser", Intent.class, UserHandle.class).invoke(context, intent, user);
            log(Log.INFO, TAG, "已请求原生录音入口");
            VibratorManager vibrator = context.getSystemService(VibratorManager.class);
            if (vibrator != null && feedback != 2) {
                vibrator.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(feedback == 1 ? 250 : 60, VibrationEffect.DEFAULT_AMPLITUDE),
                        new VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK).build());
            }
        } catch (ReflectiveOperationException | PackageManager.NameNotFoundException | RuntimeException e) {
            log(Log.ERROR, TAG, "启动录音失败", e);
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if ("com.android.systemui".equals(param.getPackageName())) {
            PrivacyDotHook.install(this, param.getClassLoader(), mPreferences);
            RecordingNotificationHook.install(this, param.getClassLoader(), mPreferences);
            return;
        }
        if (!RECORDER.equals(param.getPackageName())) return;
        ClassLoader loader = param.getClassLoader();
        RecorderMenuHook.install(this, loader);
        installDiscardAction(loader);
        try {
            Class<?> brightness = loader.loadClass("com.soundrecorder.common.utils.BrightScreenUtil");
            hook(brightness.getDeclaredMethod("acquireWakeLock", Context.class, String.class)).intercept(chain -> {
                Context context = (Context) chain.getArg(0);
                if (!(context instanceof Activity activity) || !ENTRY.equals(activity.getClass().getName())
                        || !activity.getIntent().getBooleanExtra(EXTRA, false)
                        || activity.getIntent().getIntExtra("assistant_feedback", 0) == 2) return chain.proceed();
                PowerManager power = context.getSystemService(PowerManager.class);
                if (power == null) return null;
                // 沿用原入口的释放和超时，仅去掉唤醒屏幕的能力。
                PowerManager.WakeLock wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + ":launch");
                wakeLock.acquire(60_000);
                log(Log.INFO, TAG, "快捷录音使用 CPU WakeLock，屏幕不唤醒");
                return wakeLock;
            });
            log(Log.INFO, TAG, "快捷录音保持熄屏 Hook 已加载");
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "保持熄屏 Hook 加载失败", e);
        }
        try {
            Class<?> entry = loader.loadClass(ENTRY);
            Method active = loader.loadClass("com.soundrecorder.recorderservice.manager.s").getDeclaredMethod("c");
            hook(entry.getDeclaredMethod("handleFromCubeOrLockScreenBefore")).intercept(chain -> {
                Activity activity = (Activity) chain.getThisObject();
                if (!activity.getIntent().getBooleanExtra(EXTRA, false)) return chain.proceed();
                // 原生入口会保存正在录制的文件；快捷键只允许开始，不能隐式结束已有录音。
                try {
                    if ((boolean) active.invoke(null)) {
                        activity.finish();
                        return true;
                    }
                    return false;
                } catch (ReflectiveOperationException e) {
                    log(Log.ERROR, TAG, "读取录音状态失败，取消快捷启动", e);
                    activity.finish();
                    return true;
                }
            });
            // 只有启动保护安装成功后，才把模块专用请求转换成原生快捷键请求。
            hook(entry.getDeclaredMethod("parseDataFromIntent")).intercept(chain -> {
                Activity activity = (Activity) chain.getThisObject();
                Intent intent = activity.getIntent();
                if (EXTRA.equals(intent.getAction()) && intent.getBooleanExtra(EXTRA, false)) {
                    intent.setAction("oplus.intent.action.START_RECORD_FROM_CUBE_BUTTON");
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "录音启动保护 Hook 已加载");
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "录音 App 版本不兼容，启动保护未加载", e);
        }
        try {
            // 使用 App 已有普通通知分支，其暂停、保存按钮和前台服务保持有效。
            hook(NotificationManager.class.getDeclaredMethod("canPostPromotedNotifications"))
                    .intercept(chain -> false);
            Class<?> sdk = loader.loadClass("com.soundrecorder.common.card.api.SeedlingSdkApi");
            for (String name : new String[]{"isSupportFluidCloud", "isSupportSystemSendIntent"}) {
                hook(sdk.getDeclaredMethod(name, Context.class)).intercept(chain -> false);
            }
            log(Log.INFO, TAG, "普通录音通知 Hook 已加载");
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "录音 App 版本不兼容，胶囊适配未完整加载", e);
        }
        try {
            Method dismiss = loader.loadClass("pp.a").getDeclaredMethod("m", Context.class, String.class, boolean.class);
            hook(loader.loadClass("com.soundrecorder.recorderservice.manager.statusbar.h")
                    .getDeclaredMethod("g", Context.class, String.class)).intercept(chain -> {
                if (!hideStatus()) return chain.proceed();
                // 新胶囊不可用时会回退到旧版红色胶囊；沿用原生隐藏广播及状态清理。
                dismiss.invoke(null, chain.getArg(0), chain.getArg(1), true);
                return null;
            });
            log(Log.INFO, TAG, "旧版红色录音胶囊隐藏 Hook 已加载");
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "旧版录音胶囊隐藏 Hook 加载失败", e);
        }
        try {
            Field notificationField = loader.loadClass("cj.c").getDeclaredField("e");
            notificationField.setAccessible(true);
            hook(loader.loadClass("com.soundrecorder.notification.common.d").getDeclaredMethod("k"))
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Notification notification = (Notification) notificationField.get(chain.getThisObject());
                        if ("com.soundrecorder.notification.common.h".equals(chain.getThisObject().getClass().getName())) {
                            mNotificationController = new WeakReference<>(chain.getThisObject());
                        }
                        if (notification != null && hideLock()) {
                            notification.visibility = Notification.VISIBILITY_SECRET;
                            notification.publicVersion = null;
                        }
                        return result;
                    });
            log(Log.INFO, TAG, "录音通知锁屏隐藏 Hook 已加载");
            installDisplayRefresh(loader);
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "录音通知锁屏隐藏 Hook 加载失败", e);
        }
    }

    private void installDisplayRefresh(ClassLoader loader) throws ReflectiveOperationException {
        if (mPreferences == null) return;
        Class<?> controller = loader.loadClass("cj.c");
        Method refresh = controller.getDeclaredMethod("q", boolean.class);
        Field context = controller.getDeclaredField("c");
        context.setAccessible(true);
        Method active = loader.loadClass("com.soundrecorder.recorderservice.manager.s").getDeclaredMethod("c");
        Method capsule = loader.loadClass("com.soundrecorder.recorderservice.manager.statusbar.h")
                .getDeclaredMethod("g", Context.class, String.class);
        Handler main = new Handler(Looper.getMainLooper());
        Runnable update = () -> {
            Object notification = mNotificationController.get();
            if (notification == null) return;
            try {
                if (!(boolean) active.invoke(null)) return;
                refresh.invoke(notification, false);
                capsule.invoke(null, context.get(notification), "assistantSettings");
                log(Log.INFO, TAG, "显示设置已热刷新，录音继续");
            } catch (ReflectiveOperationException | RuntimeException e) {
                log(Log.ERROR, TAG, "显示设置热刷新失败，下次通知更新时重试", e);
            }
        };
        mDisplayListener = (preferences, key) -> {
            if (key == null || Settings.HIDE_STATUS.equals(key) || Settings.HIDE_LOCK.equals(key)) {
                // 合并一次保存的多项变化，给系统进程的远程偏好同步留出时间。
                main.removeCallbacks(update);
                main.postDelayed(update, 500);
            }
        };
        mPreferences.registerOnSharedPreferenceChangeListener(mDisplayListener);
    }

    private boolean hideStatus() {
        return mPreferences == null || mPreferences.getBoolean(Settings.HIDE_STATUS, true);
    }

    private boolean hideLock() {
        return mPreferences == null || mPreferences.getBoolean(Settings.HIDE_LOCK, true);
    }

    private void installNotificationChannelSettings(ClassLoader loader) {
        try {
            Class<?> record = loader.loadClass("com.android.server.notification.NotificationRecord");
            Method getSbn = record.getDeclaredMethod("getSbn");
            getSbn.setAccessible(true);
            hook(record.getDeclaredConstructor(Context.class, StatusBarNotification.class, NotificationChannel.class))
                    .intercept(chain -> {
                        Object[] args = chain.getArgs().toArray();
                        try {
                            args[2] = configuredChannel((StatusBarNotification) args[1], (NotificationChannel) args[2]);
                        } catch (ReflectiveOperationException | RuntimeException e) {
                            log(Log.ERROR, TAG, "通知显示配置失败，保留原渠道", e);
                        }
                        return chain.proceed(args);
                    });
            hook(record.getDeclaredMethod("updateNotificationChannel", NotificationChannel.class)).intercept(chain -> {
                NotificationChannel channel = (NotificationChannel) chain.getArg(0);
                try {
                    channel = configuredChannel((StatusBarNotification) getSbn.invoke(chain.getThisObject()), channel);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    log(Log.ERROR, TAG, "通知渠道刷新配置失败", e);
                }
                return chain.proceed(new Object[]{channel});
            });
            log(Log.INFO, TAG, "录音通知显示配置 Hook 已加载");
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "系统通知渠道适配失败", e);
        }
    }

    private NotificationChannel configuredChannel(StatusBarNotification sbn, NotificationChannel channel)
            throws ReflectiveOperationException {
        if (channel == null || !RECORDER.equals(sbn.getPackageName()) || sbn.getId() != 1024
                || !"RecorderService_new_Channel_id".equals(channel.getId())) return channel;
        // 仅改变当前通知的副本，避免永久覆盖用户在系统设置里的渠道配置。
        Parcel parcel = Parcel.obtain();
        NotificationChannel copy;
        try {
            channel.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            copy = NotificationChannel.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
        Object wrapper = NotificationChannel.class.getMethod("getWrapper").invoke(copy);
        Object extension = Class.forName("android.app.INotificationChannelWrapper").getMethod("getExtImpl").invoke(wrapper);
        Class<?> extensionType = Class.forName("android.app.INotificationChannelExt");
        extensionType.getMethod("setShowIcon", boolean.class).invoke(extension, !hideStatus());
        extensionType.getMethod("setUnimportant", boolean.class).invoke(extension, hideStatus());
        NotificationChannel.class.getMethod("setLockscreenVisibility", int.class)
                .invoke(copy, hideLock() ? Notification.VISIBILITY_SECRET : Notification.VISIBILITY_PUBLIC);
        return copy;
    }

    private void installDiscardAction(ClassLoader loader) {
        try {
            Class<?> state = loader.loadClass("com.soundrecorder.recorderservice.manager.s");
            Class<?> api = loader.loadClass("rk.a");
            Field apiInstance = api.getDeclaredField("a");
            Method cancel = api.getDeclaredMethod("d", boolean.class);
            Method viewModel = loader.loadClass("com.soundrecorder.recorderservice.manager.x").getDeclaredMethod("a");
            Method canDiscard = loader.loadClass("com.soundrecorder.recorderservice.manager.RecorderViewModel")
                    .getDeclaredMethod("M");
            Field contextField = loader.loadClass("cj.c").getDeclaredField("c");
            contextField.setAccessible(true);
            Method actions = loader.loadClass("com.soundrecorder.notification.common.h").getDeclaredMethod("x");
            Method filter = loader.loadClass("bj.e").getDeclaredMethod("e");
            Method receive = loader.loadClass("com.soundrecorder.recorderservice.manager.n").getDeclaredMethod("b",
                    loader.loadClass("com.soundrecorder.recorderservice.RecorderService"), Intent.class, boolean.class);
            hook(state.getDeclaredMethod("a", int.class)).intercept(chain -> {
                mDiscardSession.updateState((int) chain.getArg(0));
                return chain.proceed();
            });
            hook(filter).intercept(chain -> {
                IntentFilter result = (IntentFilter) chain.proceed();
                result.addAction(DISCARD);
                return result;
            });
            hook(receive).intercept(chain -> {
                Intent intent = (Intent) chain.getArg(1);
                if (intent == null || !DISCARD.equals(intent.getAction())) return chain.proceed();
                try {
                    // 只接受当前录音的一次性标识，保存中、旧通知及重复点击均不删除。
                    if (mDiscardSession.consume(intent.getStringExtra("session"),
                            (boolean) canDiscard.invoke(viewModel.invoke(null)))) {
                        cancel.invoke(apiInstance.get(null), true);
                        log(Log.INFO, TAG, "已请求原生废弃当前录音并停止服务");
                    } else {
                        log(Log.INFO, TAG, "忽略失效或不可废弃的录音通知");
                    }
                } catch (ReflectiveOperationException e) {
                    log(Log.ERROR, TAG, "废弃录音失败，保留原录音处理流程", e);
                }
                return null;
            });
            hook(actions).intercept(chain -> {
                String token = mDiscardSession.token();
                if (token == null) return null;
                Context context = (Context) contextField.get(chain.getThisObject());
                Intent intent = new Intent(DISCARD).setPackage(RECORDER)
                        .setIdentifier(token).putExtra("session", token);
                PendingIntent pending = PendingIntent.getBroadcast(context, 104,
                        intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                return new Notification.Action.Builder((Icon) null, "废弃", pending).build();
            });
            log(Log.INFO, TAG, "废弃当前录音通知按钮 Hook 已加载");
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "废弃按钮适配失败，保留原通知", e);
        }
    }
}
