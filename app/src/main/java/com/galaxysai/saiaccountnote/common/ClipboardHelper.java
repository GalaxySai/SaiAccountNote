package com.galaxysai.saiaccountnote.common;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.widget.Toast;


/**
 * 剪贴板工具。
 *
 * <p>复制密码等敏感内容时:</p>
 * <ul>
 *     <li>Android 13+ 标记为敏感内容,避免在剪贴板预览里明文显示;</li>
 *     <li>60 秒后把系统剪贴板替换成占位文本,而不是只做 clear;</li>
 *     <li>部分输入法(如搜狗)会自己保存剪贴板历史,App 无法清除,
 *         介意的话可在输入法设置里关闭剪贴板历史/同步。</li>
 * </ul>
 */
public final class ClipboardHelper
{
    /** 自动清除时间(毫秒) */
    private static final long CLEAR_DELAY_MS = 60_000L;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    private ClipboardHelper()
    {
        // 工具类禁止实例化
    }

    /**
     * 复制敏感文本,并安排 60 秒后清除。
     *
     * @param context 任意 Context
     * @param label   剪贴板标签,例如“密码”
     * @param text    要复制的文本;为空时不处理
     */
    public static void copySensitive(Context context, String label, String text)
    {
        if (text == null || text.isEmpty())
        {
            return;
        }

        final Context appContext = context.getApplicationContext();
        final ClipboardManager manager =
                (ClipboardManager) appContext.getSystemService(Context.CLIPBOARD_SERVICE);

        if (manager == null)
        {
            return;
        }

        ClipData clip = ClipData.newPlainText(label, text);

        // Android 13+ 标记为敏感内容,系统不会在剪贴板浮层里直接展示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
        }

        manager.setPrimaryClip(clip);

        Toast.makeText(appContext,
                "已复制," + (CLEAR_DELAY_MS / 1000) + " 秒后自动清除",
                Toast.LENGTH_SHORT).show();

        // 到点后先确认剪贴板里还是这次复制的内容,避免误删用户后来复制的东西
        HANDLER.postDelayed(() ->
        {
            ClipData current = manager.getPrimaryClip();
            if (current == null || current.getItemCount() == 0)
            {
                return;
            }

            CharSequence currentText = current.getItemAt(0).coerceToText(appContext);
            if (!text.contentEquals(currentText))
            {
                return;
            }

            // 用占位文本替换,而不是单纯 clear:
            // 部分输入法会保存自己的剪贴板历史,clear 对它们无效;
            // 替换后至少系统剪贴板里不再保留密码。
            manager.setPrimaryClip(ClipData.newPlainText("", "已自动清除"));
        }, CLEAR_DELAY_MS);
    }
}
