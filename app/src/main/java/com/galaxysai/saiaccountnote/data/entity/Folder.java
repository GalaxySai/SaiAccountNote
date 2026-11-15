package com.galaxysai.saiaccountnote.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;


/**
 * 文件夹实体,对应数据库里的 folders 表。
 *
 * <p>一个文件夹就是首页上的一类账号集合,例如“游戏”“邮箱”“银行”。</p>
 * <p>说明:这个类里的字段名会直接映射到数据库列名,
 * 所以不要随意改名;需要改名时用 {@link ColumnInfo} 显式指定列名。</p>
 */
@Entity(
        tableName = "folders",
        indices = {
                // 文件夹名唯一:同一个名字不允许建两次
                @Index(value = "name", unique = true)
        }
)
public class Folder
{
    /**
     * 主键,自增。
     * autoGenerate = true 表示插入时不用自己赋值,由数据库生成。
     */
    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * 文件夹名称。
     * @NonNull 表示数据库里这一列不允许为 null,默认给空串避免插入时报错。
     */
    @NonNull
    public String name = "";

    /**
     * 排序序号,数字越小越靠前。
     * 现在先都填 0,以后要做“手动排序/拖拽”时就有地方存了。
     */
    @ColumnInfo(name = "sort_order")
    public int sortOrder;

    /**
     * 创建时间,存毫秒时间戳(System.currentTimeMillis())。
     * 用 long 存时间戳,避免 SQLite 没有真正的日期类型带来的歧义。
     */
    @ColumnInfo(name = "created_at")
    public long createdAt;
}
