package com.galaxysai.saiaccountnote;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import com.galaxysai.saiaccountnote.security.SessionManager;


/**
 * 全局 Application。
 *
 * <p>职责:</p>
 * <ul>
 *     <li>启动时应用上次选择的深浅色模式;</li>
 *     <li>监听 App 前后台切换,驱动自动锁定计时。</li>
 * </ul>
 */
public class App extends Application
{
    public static final String PREFS_NAME = "settings";
    public static final String KEY_NIGHT_MODE = "night_mode";

    @Override
    public void onCreate()
    {
        super.onCreate();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int mode = prefs.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);

        registerActivityLifecycleCallbacks(new AppLifecycleCallbacks());
    }

    /**
     * 用 Activity 的启动/停止计数判断 App 是否在前台。
     */
    private static final class AppLifecycleCallbacks implements Application.ActivityLifecycleCallbacks
    {
        /** 当前处于 started 状态的 Activity 数量 */
        private int startedCount = 0;

        @Override
        public void onActivityStarted(@NonNull Activity activity)
        {
            if (startedCount == 0)
            {
                // App 回到前台
                SessionManager.onAppForeground();
            }
            startedCount++;
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity)
        {
            startedCount--;

            if (startedCount <= 0)
            {
                startedCount = 0;
                // App 进入后台
                SessionManager.onAppBackground();
            }
        }

        @Override
        public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState)
        {
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity)
        {
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity)
        {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState)
        {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity)
        {
        }
    }
}
