package com.galaxysai.saiaccountnote.backup;

import android.content.Context;
import android.content.SharedPreferences;

import com.galaxysai.saiaccountnote.data.AppDatabase;
import com.galaxysai.saiaccountnote.data.entity.Account;
import com.galaxysai.saiaccountnote.data.entity.Folder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;


/**
 * 备份状态:记录上次完整备份时间,并判断之后数据有没有变。
 *
 * <p>只统计“完整 JSON 备份”(全部文件夹 + 全部账号)。
 * 文件夹导出、批量导出、Excel 表格都不算,因为它们不能恢复全部数据。</p>
 *
 * <p>判断变更不靠改数据库,而是比较:</p>
 * <ul>
 *     <li>账号数量;</li>
 *     <li>文件夹数量;</li>
 *     <li>账号的最大 updated_at;</li>
 *     <li>文件夹名字的签名(用于发现改名)。</li>
 * </ul>
 */
public final class BackupStatus
{
    private static final String PREFS = "backup_status";
    private static final String KEY_LAST_AT = "last_backup_at";
    private static final String KEY_ACCOUNT_COUNT = "account_count";
    private static final String KEY_FOLDER_COUNT = "folder_count";
    private static final String KEY_MAX_UPDATED = "max_updated";
    private static final String KEY_FOLDER_SIGNATURE = "folder_signature";

    private BackupStatus()
    {
        // 工具类禁止实例化
    }

    /**
     * 完整备份成功后调用:记录时间 + 当前数据快照。
     * 需要在后台线程调用(内部会查库)。
     */
    public static void markBackedUp(Context context)
    {
        try
        {
            AppDatabase db = AppDatabase.get(context);

            List<Folder> folders = db.folderDao().getAll();
            List<Account> accounts = db.accountDao().getAll();

            long maxUpdated = 0L;
            for (Account account : accounts)
            {
                if (account.updatedAt > maxUpdated)
                {
                    maxUpdated = account.updatedAt;
                }
            }

            SharedPreferences prefs = context.getSharedPreferences(
                    PREFS, Context.MODE_PRIVATE);

            prefs.edit()
                    .putLong(KEY_LAST_AT, System.currentTimeMillis())
                    .putInt(KEY_ACCOUNT_COUNT, accounts.size())
                    .putInt(KEY_FOLDER_COUNT, folders.size())
                    .putLong(KEY_MAX_UPDATED, maxUpdated)
                    .putLong(KEY_FOLDER_SIGNATURE, signature(folders))
                    .apply();
        }
        catch (Exception ignored)
        {
            // 备份文件本身已经写成功,状态记录失败不影响导出结果
        }
    }

    /**
     * 根据当前数据计算状态文案。
     */
    public static Result evaluate(Context context, List<Folder> folders,
                                  List<Account> accounts)
    {
        SharedPreferences prefs = context.getSharedPreferences(
                PREFS, Context.MODE_PRIVATE);

        long lastAt = prefs.getLong(KEY_LAST_AT, 0L);
        if (lastAt <= 0L)
        {
            return new Result("还没有备份,点此导出", true);
        }

        int accountCount = accounts == null ? 0 : accounts.size();
        int folderCount = folders == null ? 0 : folders.size();

        int storedAccountCount = prefs.getInt(KEY_ACCOUNT_COUNT, 0);
        int storedFolderCount = prefs.getInt(KEY_FOLDER_COUNT, 0);
        long storedMaxUpdated = prefs.getLong(KEY_MAX_UPDATED, 0L);
        long storedSignature = prefs.getLong(KEY_FOLDER_SIGNATURE, 0L);

        long maxUpdated = 0L;
        if (accounts != null)
        {
            for (Account account : accounts)
            {
                if (account.updatedAt > maxUpdated)
                {
                    maxUpdated = account.updatedAt;
                }
            }
        }

        long currentSignature = signature(folders);

        boolean accountCountChanged = accountCount != storedAccountCount;
        boolean folderCountChanged = folderCount != storedFolderCount;
        boolean contentChanged = maxUpdated > storedMaxUpdated;
        boolean folderRenamed = currentSignature != storedSignature;

        boolean changed = accountCountChanged || folderCountChanged
                || contentChanged || folderRenamed;

        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(lastAt));

        if (!changed)
        {
            return new Result("上次备份 " + time + " · 已是最新", false);
        }

        StringBuilder text = new StringBuilder("上次备份 ")
                .append(time)
                .append(" · ");

        if (accountCountChanged)
        {
            text.append("账号 ")
                    .append(accountCount)
                    .append("(上次 ")
                    .append(storedAccountCount)
                    .append(")");
        }
        else if (folderCountChanged || folderRenamed)
        {
            text.append("文件夹有变更");
        }
        else
        {
            text.append("账号有变更");
        }

        text.append(",建议备份");

        return new Result(text.toString(), true);
    }

    /**
     * 文件夹名字签名:改名后签名会变化。
     */
    private static long signature(List<Folder> folders)
    {
        long sum = 0L;
        if (folders != null)
        {
            for (Folder folder : folders)
            {
                if (folder.name != null)
                {
                    sum += folder.name.hashCode();
                }
            }
        }
        return sum;
    }

    /**
     * 状态文案 + 是否需要提醒备份。
     */
    public static final class Result
    {
        public final String text;
        public final boolean needsBackup;

        Result(String text, boolean needsBackup)
        {
            this.text = text;
            this.needsBackup = needsBackup;
        }
    }
}
