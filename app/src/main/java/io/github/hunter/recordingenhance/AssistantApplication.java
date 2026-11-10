package io.github.hunter.recordingenhance;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class AssistantApplication extends Application {
    SharedPreferences mPreferences;
    XposedService mService;
    String mConnection = "等待 LSPosed 连接；请启用模块并重启手机";
    Runnable mOnChanged;

    @Override public void onCreate() {
        super.onCreate();
        Handler main = new Handler(Looper.getMainLooper());
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override public void onServiceBind(XposedService service) {
                main.post(() -> {
                    try {
                        mService = service;
                        mPreferences = service.getRemotePreferences(Settings.GROUP);
                        mConnection = "已连接 LSPosed · 设置可同步";
                    } catch (RuntimeException e) {
                        mService = null;
                        mPreferences = null;
                        mConnection = "无法读取设置，请检查 LSPosed 后重新打开";
                    }
                    if (mOnChanged != null) mOnChanged.run();
                });
            }
            @Override public void onServiceDied(XposedService service) {
                main.post(() -> {
                    mService = null;
                    mPreferences = null;
                    mConnection = "LSPosed 已断开，请重新打开应用";
                    if (mOnChanged != null) mOnChanged.run();
                });
            }
        });
    }
}
