package com.galaxysai.saiaccountnote.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.galaxysai.saiaccountnote.data.dao.AccountDao;
import com.galaxysai.saiaccountnote.data.dao.FolderDao;
import com.galaxysai.saiaccountnote.data.entity.Account;
import com.galaxysai.saiaccountnote.data.entity.Folder;
import com.galaxysai.saiaccountnote.data.migration.MigrationV1ToV2;
import com.galaxysai.saiaccountnote.security.SessionManager;

import net.zetetic.database.sqlcipher.SupportOpenHelperFactory;


/**
 * Room 数据库入口。
 *
 * <p>当前登记了 {@link Folder} 和 {@link Account} 两个实体。
 * 数据库文件先用新名字 san.db,刻意和旧代码的 note.db 分开,
 * 这样在迁移完成前,新旧两套数据互不干扰。</p>
 */
@Database(
        entities = {Folder.class, Account.class},
        version = 2,
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase
{
    /**
     * 提供 folders 表的 DAO。
     * 由 Room 在编译期生成实现,这里只声明抽象方法。
     */
    public abstract FolderDao folderDao();

    /**
     * 提供 accounts 表的 DAO。
     * 同样由 Room 在编译期生成实现。
     */
    public abstract AccountDao accountDao();

    /**
     * 单例实例。
     * volatile 保证多线程下能及时看到最新的写入值。
     */
    private static volatile AppDatabase instance;

    /**
     * 获取数据库单例。
     *
     * <p>用“双重检查锁”保证只创建一次,避免多个线程同时建库。</p>
     *
     * @param context 任意 Context,内部会转成 ApplicationContext,不会持有 Activity 泄漏
     * @return 全局唯一的数据库实例
     */
    public static AppDatabase get(Context context)
    {
        if (instance == null)
        {
            synchronized (AppDatabase.class)
            {
                if (instance == null)
                {
                    // 数据库密钥来自解锁时派生的主密钥;未解锁不允许打开
                    byte[] key = SessionManager.getDbKey();
                    if (key == null)
                    {
                        throw new IllegalStateException("数据库尚未解锁");
                    }

                    // 加载 SQLCipher 原生库
                    System.loadLibrary("sqlcipher");
                    SupportOpenHelperFactory factory = new SupportOpenHelperFactory(key);

                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "san.db")
                            // 用 SQLCipher 加密数据库
                            .openHelperFactory(factory)
                            // 从版本 1 升到 2 时补建 accounts 表
                            .addMigrations(MigrationV1ToV2.MIGRATION_1_2)
                            .build();
                }
            }
        }
        return instance;
    }
}
