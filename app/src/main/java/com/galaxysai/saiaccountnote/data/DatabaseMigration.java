package com.galaxysai.saiaccountnote.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.galaxysai.saiaccountnote.data.entity.Account;
import com.galaxysai.saiaccountnote.data.entity.Folder;
import com.galaxysai.saiaccountnote.repository.AppExecutors;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * 明文库 → 加密库的一次性迁移。
 *
 * <p>背景:接入 SQLCipher 之前,{@code san.db} 是普通的明文 SQLite 库。
 * 直接用 SQLCipher 打开会报“file is not a database”,所以需要:</p>
 * <ol>
 *     <li>用系统 SQLite 读出现有数据;</li>
 *     <li>把明文库改名备份;</li>
 *     <li>用 SQLCipher 建新的加密库;</li>
 *     <li>把数据按原 id 写回去;</li>
 *     <li>成功后删除备份。</li>
 * </ol>
 */
public final class DatabaseMigration
{
    private static final String TAG = "DatabaseMigration";
    private static final String DB_NAME = "san.db";

    /** 明文 SQLite 文件头 */
    private static final byte[] SQLITE_HEADER =
            "SQLite format 3\u0000".getBytes(StandardCharsets.US_ASCII);

    private DatabaseMigration()
    {
        // 工具类禁止实例化
    }

    /**
     * 如果存在明文库就迁移,完成后回调。
     * 整个过程在后台线程执行。
     *
     * @param context 任意 Context
     * @param onDone  迁移结束(无论成功失败)后的主线程回调
     */
    public static void migrateIfNeeded(Context context, Runnable onDone)
    {
        final Context app = context.getApplicationContext();

        AppExecutors.get().diskIO(() ->
        {
            try
            {
                doMigrate(app);
            }
            catch (Exception e)
            {
                Log.e(TAG, "明文库迁移失败,保留原文件", e);
            }

            AppExecutors.get().main(onDone);
        });
    }

    private static void doMigrate(Context context) throws Exception
    {
        File dbFile = context.getDatabasePath(DB_NAME);

        // 不存在或已经是加密库 → 不需要迁移
        if (!dbFile.exists() || !isPlaintext(dbFile))
        {
            return;
        }

        SQLiteDatabase db = null;
        List<Folder> folders = new ArrayList<>();
        List<Account> accounts = new ArrayList<>();

        try
        {
            db = SQLiteDatabase.openDatabase(
                    dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);

            // 只有表存在时才读,兼容“曾经建过空库”的情况
            if (tableExists(db, "folders"))
            {
                folders = readFolders(db);
            }
            if (tableExists(db, "accounts"))
            {
                accounts = readAccounts(db);
            }
        }
        finally
        {
            if (db != null)
            {
                db.close();
            }
        }

        // 明文库改名备份,腾出 san.db 给加密库
        File backup = new File(dbFile.getPath() + ".plain.bak");
        if (backup.exists())
        {
            backup.delete();
        }
        if (!dbFile.renameTo(backup))
        {
            throw new IllegalStateException("无法重命名明文数据库");
        }
        deleteSidecars(dbFile);

        // 建加密库并写回数据(保留原 id)
        AppDatabase database = AppDatabase.get(context);

        for (Folder folder : folders)
        {
            database.folderDao().insertWithId(
                    folder.id, folder.name, folder.sortOrder, folder.createdAt);
        }

        for (Account account : accounts)
        {
            database.accountDao().insertWithId(
                    account.id, account.folderId, account.title, account.username,
                    account.password, account.url, account.email, account.phone,
                    account.notes, account.createdAt, account.updatedAt);
        }

        // 全部成功才删除备份
        backup.delete();
    }

    /**
     * 判断文件是否是明文 SQLite 库:看文件头。
     * SQLCipher 加密后文件头会被打乱,不会匹配。
     */
    private static boolean isPlaintext(File file)
    {
        try (FileInputStream in = new FileInputStream(file))
        {
            byte[] header = new byte[SQLITE_HEADER.length];
            int read = in.read(header);
            return read == header.length && Arrays.equals(header, SQLITE_HEADER);
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private static boolean tableExists(SQLiteDatabase db, String table)
    {
        Cursor cursor = null;
        try
        {
            cursor = db.rawQuery(
                    "SELECT count(*) FROM sqlite_master WHERE type='table' AND name=?",
                    new String[]{table});
            return cursor.moveToFirst() && cursor.getInt(0) > 0;
        }
        catch (Exception e)
        {
            return false;
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
    }

    private static List<Folder> readFolders(SQLiteDatabase db)
    {
        List<Folder> list = new ArrayList<>();
        Cursor cursor = null;

        try
        {
            cursor = db.rawQuery(
                    "SELECT id, name, sort_order, created_at FROM folders", null);

            while (cursor.moveToNext())
            {
                Folder folder = new Folder();
                folder.id = cursor.getLong(0);
                folder.name = cursor.getString(1);
                folder.sortOrder = cursor.getInt(2);
                folder.createdAt = cursor.getLong(3);
                list.add(folder);
            }
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }

        return list;
    }

    private static List<Account> readAccounts(SQLiteDatabase db)
    {
        List<Account> list = new ArrayList<>();
        Cursor cursor = null;

        try
        {
            cursor = db.rawQuery(
                    "SELECT id, folder_id, title, username, password, url, email, phone, " +
                            "notes, created_at, updated_at FROM accounts", null);

            while (cursor.moveToNext())
            {
                Account account = new Account();
                account.id = cursor.getLong(0);
                account.folderId = cursor.getLong(1);
                account.title = safeString(cursor, 2);
                account.username = safeString(cursor, 3);
                account.password = safeString(cursor, 4);
                account.url = nullableString(cursor, 5);
                account.email = nullableString(cursor, 6);
                account.phone = nullableString(cursor, 7);
                account.notes = nullableString(cursor, 8);
                account.createdAt = cursor.getLong(9);
                account.updatedAt = cursor.getLong(10);
                list.add(account);
            }
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }

        return list;
    }

    private static String safeString(Cursor cursor, int index)
    {
        if (cursor.isNull(index))
        {
            return "";
        }
        return cursor.getString(index);
    }

    private static String nullableString(Cursor cursor, int index)
    {
        if (cursor.isNull(index))
        {
            return null;
        }
        return cursor.getString(index);
    }

    /**
     * 删除 SQLite 的附属文件(-wal / -shm / -journal)。
     */
    private static void deleteSidecars(File dbFile)
    {
        String path = dbFile.getPath();
        new File(path + "-wal").delete();
        new File(path + "-shm").delete();
        new File(path + "-journal").delete();
    }
}
