package io.github.hunter.recordingenhance;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;

final class RecorderMenuHook {
    private static final int MENU_ID = 0x52454101;

    static void install(XposedModule module, ClassLoader loader) {
        try {
            Class<?> toolbar = loader.loadClass("com.coui.appcompat.toolbar.COUIToolbar");
            Method getMenu = toolbar.getMethod("getMenu");
            module.hook(toolbar.getDeclaredMethod("inflateMenu", int.class)).intercept(chain -> {
                Object result = chain.proceed();
                Context context = ((View) chain.getThisObject()).getContext();
                String resource = context.getResources().getResourceEntryName((int) chain.getArg(0));
                if ("recorder_list_option_browse".equals(resource)) {
                    addEntry((Menu) getMenu.invoke(chain.getThisObject()), context);
                }
                return result;
            });
            Class<?> browse = loader.loadClass("com.soundrecorder.browsefile.home.BrowseFragment");
            Method activity = browse.getMethod("getActivity");
            module.hook(browse.getDeclaredMethod("onOptionsItemSelected", MenuItem.class)).intercept(chain -> {
                if (((MenuItem) chain.getArg(0)).getItemId() != MENU_ID) return chain.proceed();
                Context context = (Context) activity.invoke(chain.getThisObject());
                if (context != null) openSettings(context);
                return true;
            });
            Class<?> recorder = loader.loadClass("com.soundrecorder.record.RecorderActivity");
            module.hook(recorder.getDeclaredMethod("onCreateOptionsMenu", Menu.class)).intercept(chain -> {
                Object result = chain.proceed();
                addEntry((Menu) chain.getArg(0), (Activity) chain.getThisObject());
                return result;
            });
            module.log(Log.INFO, "RecordingEnhance", "录音增强菜单入口 Hook 已加载");
        } catch (ReflectiveOperationException e) {
            module.log(Log.ERROR, "RecordingEnhance", "录音菜单适配失败，保留原菜单", e);
        }
    }

    private static void addEntry(Menu menu, Context context) {
        if (menu.findItem(MENU_ID) != null) return;
        MenuItem item = menu.add(Menu.NONE, MENU_ID, Menu.NONE, "录音增强");
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        item.setOnMenuItemClickListener(clicked -> {
            openSettings(context);
            return true;
        });
    }

    private static void openSettings(Context context) {
        try {
            context.startActivity(new Intent().setClassName("io.github.hunter.recordingenhance",
                    "io.github.hunter.recordingenhance.SettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.e("RecordingEnhance", "无法打开录音增强配置", e);
            Toast.makeText(context, "无法打开录音增强，请检查模块安装或应用隐藏规则", Toast.LENGTH_LONG).show();
        }
    }
}
