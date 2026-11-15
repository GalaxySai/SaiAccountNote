package com.galaxysai.saiaccountnote.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;


/**
 * 账号实体,对应数据库里的 accounts 表。
 *
 * <p>一条记录就是一个账号,例如“某游戏的登录账号”。</p>
 *
 * <p>和外键的关系:每个账号必须属于一个文件夹,
 * 删除文件夹时,它下面的账号会跟着被删除(CASCADE)。</p>
 */
@Entity(
        tableName = "accounts",
        foreignKeys = @ForeignKey(
                entity = Folder.class,                 // 关联的父表实体
                parentColumns = "id",                  // 父表的主键列
                childColumns = "folder_id",            // 本表的外键列
                onDelete = ForeignKey.CASCADE          // 父记录删除时,子记录级联删除
        ),
        indices = {
                // 外键列建索引:Room 会警告没有索引的外键,而且按文件夹查会更快
                @Index("folder_id"),
                // 标题建索引:以后做搜索/排序用得上
                @Index("title")
        }
)
public class Account
{
    /**
     * 主键,自增。
     */
    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * 所属文件夹的主键,对应 folders.id。
     */
    @ColumnInfo(name = "folder_id")
    public long folderId;

    /**
     * 标题,例如“Steam”“QQ 邮箱”。
     * 允许为空串,但不允许 null。
     */
    @NonNull
    public String title = "";

    /**
     * 登录用户名。
     */
    @NonNull
    public String username = "";

    /**
     * 登录密码。
     * 注意:当前阶段仍是明文存在库里,加密会在后面的迭代统一做。
     */
    @NonNull
    public String password = "";

    /**
     * 登录网址,可以为空。
     * 存成 String,展示时再决定要不要做成可点击链接。
     */
    public String url;

    /**
     * 绑定的邮箱,可以为空。
     */
    public String email;

    /**
     * 绑定的手机号,可以为空。
     */
    public String phone;

    /**
     * 备注,可以为空。
     */
    public String notes;

    /**
     * 创建时间,毫秒时间戳。
     */
    @ColumnInfo(name = "created_at")
    public long createdAt;

    /**
     * 最后修改时间,毫秒时间戳。
     * 列表排序会用到它。
     */
    @ColumnInfo(name = "updated_at")
    public long updatedAt;
}
