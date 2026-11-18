package com.galaxysai.saiaccountnote.common;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;


/**
 * 分享工具。
 *
 * <p>表格分享不直接使用 SAF 返回的 URI,而是把内容写到应用缓存,
 * 再用 FileProvider 生成一个稳定的 content:// URI 分享出去。</p>
 *
 * <p>如果用户选择“只分享、不保存本地”,就直接走这里,不经过文件选择器。</p>
 */
public final class ShareHelper
{
    /** Excel (.xlsx) 的 MIME 类型 */
    public static final String MIME_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final String PROVIDER_SUFFIX = ".fileprovider";
    private static final String EXPORT_DIR = "exports";

    private ShareHelper()
    {
        // 工具类禁止实例化
    }

    /**
     * 把 XLSX 内容写入缓存并分享。
     *
     * @param activity 当前 Activity
     * @param data     XLSX 字节
     * @param fileName 分享时显示的文件名
     */
    public static void shareXlsx(Activity activity, byte[] data, String fileName)
    {
        shareFile(activity, data, fileName, MIME_XLSX);
    }

    private static void shareFile(Activity activity, byte[] data,
                                  String fileName, String mimeType)
    {
        if (data == null || data.length == 0)
        {
            Toast.makeText(activity, "表格内容为空,无法分享", Toast.LENGTH_SHORT).show();
            return;
        }

        try
        {
            File dir = new File(activity.getCacheDir(), EXPORT_DIR);
            if (!dir.exists() && !dir.mkdirs())
            {
                throw new IllegalStateException("无法创建缓存目录");
            }

            // 清掉上一次分享留下的临时文件
            File[] oldFiles = dir.listFiles();
            if (oldFiles != null)
            {
                for (File old : oldFiles)
                {
                    //noinspection ResultOfMethodCallIgnored
                    old.delete();
                }
            }

            String safeName = safeFileName(fileName, "xlsx");
            File file = new File(dir, safeName);
            try (FileOutputStream out = new FileOutputStream(file))
            {
                out.write(data);
                out.flush();
            }

            Uri uri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + PROVIDER_SUFFIX,
                    file);

            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(mimeType);
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.setClipData(ClipData.newRawUri(safeName, uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            activity.startActivity(Intent.createChooser(send, "分享表格"));
        }
        catch (Exception e)
        {
            Toast.makeText(activity,
                    "分享失败: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 去掉文件名里的路径/非法字符,并保证以指定扩展名结尾。
     */
    private static String safeFileName(String name, String extension)
    {
        if (name == null || name.trim().isEmpty())
        {
            return "accounts." + extension;
        }

        String safe = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!safe.toLowerCase(Locale.ROOT).endsWith("." + extension))
        {
            safe += "." + extension;
        }
        return safe;
    }
}
