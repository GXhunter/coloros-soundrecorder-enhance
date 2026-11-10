package io.github.hunter.recordingenhance;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;

final class RecordingNotificationHook {
    private volatile Object mFilter;
    private SharedPreferences.OnSharedPreferenceChangeListener mListener;

    static void install(XposedModule module, ClassLoader loader, SharedPreferences preferences) {
        new RecordingNotificationHook().installFilter(module, loader, preferences);
    }

    private void installFilter(XposedModule module, ClassLoader loader, SharedPreferences preferences) {
        try {
            Class<?> coordinator = loader.loadClass("com.android.systemui.statusbar.notification.collection.coordinator.RankingCoordinator");
            Field filter = coordinator.getDeclaredField("mSuspendedFilter");
            filter.setAccessible(true);
            Class<?> entry = loader.loadClass("com.android.systemui.statusbar.notification.collection.NotificationEntry");
            Method sbn = entry.getDeclaredMethod("getSbn");
            sbn.setAccessible(true);
            Method invalidate = loader.loadClass("com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.Pluggable")
                    .getDeclaredMethod("invalidateList", String.class);
            invalidate.setAccessible(true);
            module.hook(filter.getType().getDeclaredMethod("shouldFilterOut", entry, long.class)).intercept(chain -> {
                if (chain.getThisObject() == mFilter && preferences != null) {
                    StatusBarNotification notification = (StatusBarNotification) sbn.invoke(chain.getArg(0));
                    if (RecordingVisibility.filterNotification(preferences.getBoolean(Settings.SHOW_NOTIFICATION, true),
                            notification.getPackageName(), notification.getId())) return true;
                }
                return chain.proceed();
            });
            module.hook(coordinator.getDeclaredMethod("attach",
                    loader.loadClass("com.android.systemui.statusbar.notification.collection.NotifPipeline"))).intercept(chain -> {
                mFilter = filter.get(chain.getThisObject());
                return chain.proceed();
            });
            if (preferences != null) {
                Handler main = new Handler(Looper.getMainLooper());
                mListener = (prefs, key) -> {
                    if (key == null || Settings.SHOW_NOTIFICATION.equals(key)) {
                        main.post(() -> {
                            try {
                                if (mFilter != null) invalidate.invoke(mFilter, "recordingAssistantSettings");
                            } catch (ReflectiveOperationException e) {
                                module.log(Log.ERROR, "RecordingEnhance", "录音通知列表刷新失败", e);
                            }
                        });
                    }
                };
                preferences.registerOnSharedPreferenceChangeListener(mListener);
            }
            module.log(Log.INFO, "RecordingEnhance", "录音通知显示过滤 Hook 已加载");
        } catch (ReflectiveOperationException e) {
            module.log(Log.ERROR, "RecordingEnhance", "录音通知过滤适配失败，保留通知", e);
        }
    }
}
