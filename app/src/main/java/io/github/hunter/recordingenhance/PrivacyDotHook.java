package io.github.hunter.recordingenhance;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.util.List;
import io.github.libxposed.api.XposedModule;

final class PrivacyDotHook {
    private final XposedModule mModule;
    private final SharedPreferences mPreferences;
    private final Map<Object, Access> mControllers = new WeakHashMap<>();
    private SharedPreferences.OnSharedPreferenceChangeListener mListener;
    private volatile boolean mOnlyRecorderMicrophone;

    private record Access(Field state, Method shouldShow, Method corner) {}

    private PrivacyDotHook(XposedModule module, SharedPreferences preferences) {
        mModule = module;
        mPreferences = preferences;
    }

    static void install(XposedModule module, ClassLoader loader, SharedPreferences preferences) {
        new PrivacyDotHook(module, preferences).install(loader);
    }

    private void install(ClassLoader loader) {
        try {
            Class<?> item = loader.loadClass("com.android.systemui.privacy.PrivacyItem");
            Method application = item.getDeclaredMethod("getApplication");
            Method type = item.getDeclaredMethod("getPrivacyType");
            Method packageName = application.getReturnType().getDeclaredMethod("getPackageName");
            Class<?> callback = loader.loadClass("com.android.systemui.statusbar.events.SystemEventCoordinator$privacyStateListener$1");
            mModule.hook(callback.getDeclaredMethod("onPrivacyItemsChanged", List.class)).intercept(chain -> {
                boolean onlyRecorder = false;
                try {
                    List<RecordingVisibility.Use> uses = new ArrayList<>();
                    for (Object entry : (List<?>) chain.getArg(0)) {
                        uses.add(new RecordingVisibility.Use((String) packageName.invoke(application.invoke(entry)),
                                ((Enum<?>) type.invoke(entry)).name()));
                    }
                    onlyRecorder = RecordingVisibility.onlyRecorderMicrophone(uses);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    mModule.log(Log.ERROR, "RecordingEnhance", "隐私来源读取失败，保留系统提示", e);
                }
                boolean changed = onlyRecorder != mOnlyRecorderMicrophone;
                mOnlyRecorderMicrophone = onlyRecorder;
                Object result = chain.proceed();
                if (changed) {
                    synchronized (mControllers) {
                        for (var entry : mControllers.entrySet()) update(entry.getKey(), entry.getValue());
                    }
                    mModule.log(Log.INFO, "RecordingEnhance", "隐私来源更新，仅录音麦克风=" + onlyRecorder);
                }
                return result;
            });
        } catch (ReflectiveOperationException e) {
            mModule.log(Log.ERROR, "RecordingEnhance", "隐私来源接口不可用，保留系统提示", e);
        }
        int installed = 0;
        for (String name : new String[]{
                "com.android.systemui.statusbar.events.PrivacyDotViewControllerImpl",
                "com.oplus.systemui.privacy.OplusPrivacyDotViewController"}) {
            try {
                Class<?> controller = loader.loadClass(name);
                Field state = controller.getDeclaredField("currentViewState");
                state.setAccessible(true);
                Class<?> stateType = state.getType();
                Access access = new Access(state, stateType.getDeclaredMethod("shouldShowDot"),
                        stateType.getDeclaredMethod("getDesignatedCorner"));
                mModule.hook(controller.getDeclaredMethod("access$processNextViewState", controller)).intercept(chain -> {
                    Object result = chain.proceed();
                    Object instance = chain.getArg(0);
                    synchronized (mControllers) {
                        mControllers.put(instance, access);
                        update(instance, access);
                    }
                    return result;
                });
                installed++;
            } catch (ReflectiveOperationException e) {
                mModule.log(Log.ERROR, "RecordingEnhance", "隐私圆点视图适配失败：" + name, e);
            }
        }
        if (mPreferences != null) {
            Handler main = new Handler(Looper.getMainLooper());
            mListener = (preferences, key) -> {
                if (key == null || Settings.HIDE_PRIVACY_DOT.equals(key)) {
                    main.post(() -> {
                        synchronized (mControllers) {
                            for (var entry : mControllers.entrySet()) update(entry.getKey(), entry.getValue());
                        }
                    });
                }
            };
            mPreferences.registerOnSharedPreferenceChangeListener(mListener);
        }
        mModule.log(installed == 0 ? Log.ERROR : Log.INFO, "RecordingEnhance",
                "隐私圆点视图 Hook 数量：" + installed);
    }

    private void update(Object controller, Access access) {
        try {
            Object state = access.state().get(controller);
            View corner = (View) access.corner().invoke(state);
            if (corner == null) return;
            int id = corner.getResources().getIdentifier("privacy_dot", "id", "com.android.systemui");
            View dot = id == 0 ? null : corner.findViewById(id);
            if (dot == null) return;
            // 圆点由 ScreenDecorations 线程管理，不能用主线程直接改 View。
            dot.post(() -> {
                try {
                    Object latest = access.state().get(controller);
                    if (corner != access.corner().invoke(latest)) return;
                    boolean nativeVisible = (boolean) access.shouldShow().invoke(latest);
                    boolean hidden = mOnlyRecorderMicrophone && mPreferences != null
                            && mPreferences.getBoolean(Settings.HIDE_PRIVACY_DOT, false);
                    // 不改 shouldShowDot，保留系统旧/新状态比较及停止时的清理。
                    dot.setVisibility(nativeVisible && !hidden ? View.VISIBLE : View.INVISIBLE);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    mModule.log(Log.ERROR, "RecordingEnhance", "隐私圆点视图应用失败", e);
                }
            });
        } catch (ReflectiveOperationException | RuntimeException e) {
            mModule.log(Log.ERROR, "RecordingEnhance", "隐私圆点视图刷新失败", e);
        }
    }
}
