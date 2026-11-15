package com.galaxysai.saiaccountnote.data.migration;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;


/**
 * 数据库版本 1 → 2 的迁移。
 *
 * <p>背景:</p>
 * <ul>
 *     <li>版本 1 只建了 folders 表(做文件夹迁移那一步);</li>
 *     <li>版本 2 新增 accounts 表。</li>
 * </ul>
 *
 * <p>这个迁移只负责“补建新表”,不涉及旧库 note.db 的数据搬迁。
 * 如果手机上还没有 san.db,Room 会直接按版本 2 建库,不会走这里。</p>
 */
public class MigrationV1ToV2
{
    /**
     * 从版本 1 升级到版本 2。
     * 注意:建表 SQL 必须和 Room 根据实体生成的完全一致,
     * 否则迁移结束后校验会失败(报 Migration didn't properly handle)。
     */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2)
    {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db)
        {
            db.execSQL("CREATE TABLE IF NOT EXISTS `accounts` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`folder_id` INTEGER NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`username` TEXT NOT NULL, " +
                    "`password` TEXT NOT NULL, " +
                    "`url` TEXT, " +
                    "`email` TEXT, " +
                    "`phone` TEXT, " +
                    "`notes` TEXT, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`folder_id`) REFERENCES `folders`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)");

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_folder_id` " +
                    "ON `accounts` (`folder_id`)");

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_title` " +
                    "ON `accounts` (`title`)");
        }
    };
}
