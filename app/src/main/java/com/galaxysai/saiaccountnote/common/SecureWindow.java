package com.galaxysai.saiaccountnote.common;

import android.view.Window;
import android.view.WindowManager;


/**
 * 窗口安全工具。
 *
 * <p>给窗口加上 {@link WindowManager.LayoutParams#FLAG_SECURE} 后:</p>
 * <ul>
 *     <li>系统截屏会得到空白/黑屏;</li>
 *     <li>“最近任务”里不会显示界面预览;</li>
 *     <li>录屏同理。</li>
 * </ul>
 *
 * <p>密码类 App 的界面和弹窗都应该应用它。</p>
 */
public final class SecureWindow
{
    private SecureWindow()
    {
        // 工具类禁止实例化
    }

    /**
     * 给窗口加上防截屏标记。
     *
     * @param window 目标窗口,可以为 null(内部会判空)
     */
    public static void apply(Window window)
    {
        if (window == null)
        {
            return;
        }

        window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
    }
}
