package com.galaxysai.saiaccountnote.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.galaxysai.saiaccountnote.data.entity.Account;

import java.util.List;


/**
 * accounts 表的数据访问接口(DAO)。
 *
 * <p>同样只写“接口 + 注解”,实现由 Room 在编译期生成。</p>
 *
 * <p>本阶段只做最小集合:按文件夹查、按 id 查、增、改、删、计数。
 * 关键字搜索、跨文件夹查询留到后面的迭代再加。</p>
 */
@Dao
public interface AccountDao
{
    /**
     * 查询某个文件夹下的所有账号,最近修改的排在最前面。
     *
     * @param folderId 文件夹主键
     * @return 账号列表;没有数据时返回空列表
     */
    @Query("SELECT * FROM accounts WHERE folder_id = :folderId ORDER BY updated_at DESC")
    List<Account> getByFolder(long folderId);

    /**
     * 查询全部账号,导出备份时用。
     */
    @Query("SELECT * FROM accounts ORDER BY id ASC")
    List<Account> getAll();

    /**
     * 按主键查一个账号。
     *
     * @param id 账号主键
     * @return 找到返回对象,找不到返回 null
     */
    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    Account findById(long id);

    /**
     * 统计某个文件夹下有多少条账号。
     * 用于以后展示数量,或者做“删除文件夹前的提示”。
     *
     * @param folderId 文件夹主键
     * @return 记录条数
     */
    @Query("SELECT COUNT(*) FROM accounts WHERE folder_id = :folderId")
    int countByFolder(long folderId);

    /**
     * 插入一个账号。
     *
     * @param account 要插入的对象(folderId 必须指向一个已存在的文件夹)
     * @return 新记录的主键
     */
    @Insert
    long insert(Account account);

    /**
     * 迁移专用:按原主键插入,保留原来的 id。
     */
    @Query("INSERT INTO accounts " +
            "(id, folder_id, title, username, password, url, email, phone, notes, created_at, updated_at) " +
            "VALUES (:id, :folderId, :title, :username, :password, :url, :email, :phone, :notes, " +
            ":createdAt, :updatedAt)")
    void insertWithId(long id, long folderId, String title, String username, String password,
                      String url, String email, String phone, String notes,
                      long createdAt, long updatedAt);

    /**
     * 按主键更新一个账号(整行覆盖)。
     *
     * @param account 需要带 id 的对象
     */
    @Update
    void update(Account account);

    /**
     * 按主键删除一个账号。
     *
     * @param account 需要带 id 的对象
     */
    @Delete
    void delete(Account account);
}
