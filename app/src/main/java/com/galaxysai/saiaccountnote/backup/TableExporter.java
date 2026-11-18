package com.galaxysai.saiaccountnote.backup;

import android.content.Context;

import com.galaxysai.saiaccountnote.data.AppDatabase;
import com.galaxysai.saiaccountnote.data.entity.Account;
import com.galaxysai.saiaccountnote.data.entity.Folder;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


/**
 * 把账号导出成人类可读的 CSV 表格。
 *
 * <p>可以用 Excel、WPS、Google Sheets 直接打开。</p>
 *
 * <p>注意:CSV 是明文,包含密码,分享前请确认对象可信。</p>
 */
public final class TableExporter
{
    private TableExporter()
    {
        // 工具类禁止实例化
    }

    /**
     * 生成 CSV 内容(UTF-8 带 BOM,Excel 打开中文不乱码)。
     *
     * @param folderId   只导出该文件夹;传 null 表示全部
     * @param accountIds 只导出这些账号;传 null 表示不按账号限制
     */
    public static byte[] exportCsv(Context context, Long folderId,
                                   List<Long> accountIds) throws Exception
    {
        AppDatabase db = AppDatabase.get(context);

        List<Folder> folders;
        List<Account> accounts;

        if (accountIds != null && !accountIds.isEmpty())
        {
            accounts = db.accountDao().getByIds(accountIds);

            Set<Long> folderIds = new LinkedHashSet<>();
            for (Account account : accounts)
            {
                folderIds.add(account.folderId);
            }

            folders = new ArrayList<>();
            for (Long id : folderIds)
            {
                Folder folder = db.folderDao().findById(id);
                if (folder != null)
                {
                    folders.add(folder);
                }
            }
        }
        else if (folderId != null)
        {
            Folder folder = db.folderDao().findById(folderId);

            if (folder == null)
            {
                folders = Collections.emptyList();
                accounts = Collections.emptyList();
            }
            else
            {
                folders = Collections.singletonList(folder);
                accounts = db.accountDao().getByFolder(folderId);
            }
        }
        else
        {
            folders = db.folderDao().getAll();
            accounts = db.accountDao().getAll();
        }

        Map<Long, String> folderNames = new HashMap<>();
        for (Folder folder : folders)
        {
            folderNames.put(folder.id, folder.name);
        }

        // 按文件夹名、账号标题排序,方便人阅读
        List<Account> sorted = new ArrayList<>(accounts);
        Collections.sort(sorted, (a, b) ->
        {
            String folderA = folderNames.get(a.folderId);
            String folderB = folderNames.get(b.folderId);
            if (folderA == null)
            {
                folderA = "";
            }
            if (folderB == null)
            {
                folderB = "";
            }

            int result = folderA.compareTo(folderB);
            if (result != 0)
            {
                return result;
            }

            String titleA = a.title == null ? "" : a.title;
            String titleB = b.title == null ? "" : b.title;
            return titleA.compareTo(titleB);
        });

        SimpleDateFormat dateFormat =
                new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        StringBuilder builder = new StringBuilder();

        // UTF-8 BOM:Excel 打开时中文不乱码
        builder.append('\uFEFF');
        builder.append("文件夹,标题,用户名,密码,网址,邮箱,手机,备注,创建时间,更新时间\r\n");

        for (Account account : sorted)
        {
            String folderName = folderNames.get(account.folderId);
            if (folderName == null)
            {
                folderName = "";
            }

            builder.append(csv(folderName)).append(',')
                    .append(csv(account.title)).append(',')
                    .append(csv(account.username)).append(',')
                    .append(csv(account.password)).append(',')
                    .append(csv(account.url)).append(',')
                    .append(csv(account.email)).append(',')
                    .append(csv(account.phone)).append(',')
                    .append(csv(account.notes)).append(',')
                    .append(csv(formatDate(dateFormat, account.createdAt))).append(',')
                    .append(csv(formatDate(dateFormat, account.updatedAt))).append("\r\n");
        }

        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String formatDate(SimpleDateFormat format, long millis)
    {
        if (millis <= 0)
        {
            return "";
        }
        return format.format(new Date(millis));
    }

    /**
     * CSV 转义:字段里有逗号、引号、换行时用双引号包起来,内部引号翻倍。
     */
    private static String csv(String value)
    {
        if (value == null)
        {
            return "";
        }

        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"")
                || escaped.contains("\n") || escaped.contains("\r"))
        {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
