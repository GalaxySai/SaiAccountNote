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

    /** 自动锁定时间(分钟),默认 5 分钟 */
    public static final String KEY_AUTO_LOCK_MINUTES = "auto_lock_minutes";

    /** 文件夹排序模式 */
    public static final String KEY_FOLDER_SORT = "folder_sort";

    /** 账号排序模式 */
    public static final String KEY_ACCOUNT_SORT = "account_sort";

    @Override
    public void onCreate()
    {
        super.onCreate();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int mode = prefs.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);

        // 自动锁定时间,默认 5 分钟
        int lockMinutes = prefs.getInt(KEY_AUTO_LOCK_MINUTES, 5);
        SessionManager.setTimeoutMs(lockMinutes * 60_000L);

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
