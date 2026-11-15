package com.galaxysai.saiaccountnote.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.galaxysai.saiaccountnote.data.entity.Folder;

import java.util.List;


/**
 * folders 表的数据访问接口(DAO)。
 *
 * <p>Room 会在编译期根据这些注解生成实现类,所以这里只写“接口 + 注解”,
 * 不需要自己写 SQL 的实现。</p>
 *
 * <p>命名约定:</p>
 * <ul>
 *     <li>getAll / findById 这类是查询;</li>
 *     <li>insert / update / delete 是写操作。</li>
 * </ul>
 */
@Dao
public interface FolderDao
{
    /**
     * 查询所有文件夹,按 sort_order 升序、同序号再按名字升序。
     *
     * @return 文件夹列表;没有数据时返回空列表(不是 null)
     */
    @Query("SELECT * FROM folders ORDER BY sort_order ASC, name ASC")
    List<Folder> getAll();

    /**
     * 按主键查一个文件夹。
     *
     * @param id 文件夹主键
     * @return 找到返回对象,找不到返回 null
     */
    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    Folder findById(long id);

    /**
     * 按名称查一个文件夹,主要用于“新建时判断重名”。
     *
     * @param name 文件夹名
     * @return 找到返回对象,找不到返回 null
     */
    @Query("SELECT * FROM folders WHERE name = :name LIMIT 1")
    Folder findByName(String name);

    /**
     * 插入一个文件夹。
     *
     * @param folder 要插入的对象
     * @return 新记录的主键
     */
    @Insert
    long insert(Folder folder);

    /**
     * 迁移专用:按原主键插入,保留原来的 id。
     * 用于把明文库的数据搬到加密库,保证 accounts.folder_id 的引用不乱。
     */
    @Query("INSERT INTO folders (id, name, sort_order, created_at) " +
            "VALUES (:id, :name, :sortOrder, :createdAt)")
    void insertWithId(long id, String name, int sortOrder, long createdAt);

    /**
     * 按主键更新一个文件夹(整行覆盖)。
     *
     * @param folder 需要带 id 的对象
     */
    @Update
    void update(Folder folder);

    /**
     * 按主键删除一个文件夹。
     *
     * @param folder 需要带 id 的对象
     */
    @Delete
    void delete(Folder folder);
}
