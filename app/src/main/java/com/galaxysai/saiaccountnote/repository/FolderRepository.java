package com.galaxysai.saiaccountnote.repository;

import android.content.Context;

import com.galaxysai.saiaccountnote.data.AppDatabase;
import com.galaxysai.saiaccountnote.data.entity.Folder;

import java.util.List;


/**
 * 文件夹相关的数据入口(仓储层)。
 *
 * <p>职责:把“线程调度 + 调用 DAO”包起来,让 Activity 只关心结果,
 * 不必自己写 Executor,也不直接碰 DAO。</p>
 *
 * <p>约定:所有方法都是异步的,结果通过 {@link Callback} 在主线程回调。</p>
 */
public class FolderRepository
{
    /**
     * 数据库实例。
     * 在构造函数里初始化,不能写成字段初始化,
     * 因为字段初始化时 context 参数还没赋值,会拿到 null。
     */
    private final AppDatabase db;

    /**
     * @param context 任意 Context,内部会取 ApplicationContext,不会泄漏 Activity
     */
    public FolderRepository(Context context)
    {
        this.db = AppDatabase.get(context.getApplicationContext());
    }

    /**
     * 查询全部文件夹。
     *
     * @param callback 结果回调:返回文件夹列表,没有数据时是空列表
     */
    public void getAll(Callback<List<Folder>> callback)
    {
        AppExecutors.get().diskIO(() ->
        {
            // 在后台线程查库
            List<Folder> folders = db.folderDao().getAll();

            // 切回主线程再回调,保证调用方能安全更新 UI
            AppExecutors.get().main(() -> callback.onResult(folders));
        });
    }

    /**
     * 新建一个文件夹。
     *
     * @param name     文件夹名称(调用方需先做非空/长度校验)
     * @param callback 结果回调:true 表示成功,false 表示重名或写入失败
     */
    public void create(String name, Callback<Boolean> callback)
    {
        AppExecutors.get().diskIO(() ->
        {
            boolean success;

            // 先查重名:数据库里 folders.name 是唯一索引,
            // 这里提前判断,能给出更友好的提示
            if (db.folderDao().findByName(name) != null)
            {
                success = false;
            }
            else
            {
                Folder folder = new Folder();
                folder.name = name;
                folder.sortOrder = 0;
                folder.createdAt = System.currentTimeMillis();

                db.folderDao().insert(folder);
                success = true;
            }

            AppExecutors.get().main(() -> callback.onResult(success));
        });
    }

    /**
     * 按名称重命名一个文件夹。
     *
     * @param oldName  原来的文件夹名
     * @param newName  新名字(调用方需先做非空/长度校验)
     * @param callback 结果回调:true 表示成功;
     *                 false 表示原名不存在,或新名字已被别的文件夹占用
     */
    public void rename(String oldName, String newName, Callback<Boolean> callback)
    {
        AppExecutors.get().diskIO(() ->
        {
            boolean success = false;

            // 原名和新名相同:当作成功,避免被误判成“重名”
            if (oldName != null && oldName.equals(newName))
            {
                success = true;
            }
            else
            {
                Folder folder = db.folderDao().findByName(oldName);

                // 原名存在、且新名字没有被其他文件夹占用,才允许改
                if (folder != null && db.folderDao().findByName(newName) == null)
                {
                    folder.name = newName;
                    db.folderDao().update(folder);
                    success = true;
                }
            }

            // lambda 里只能用 effectively final 的变量,所以再转存一次
            final boolean result = success;
            AppExecutors.get().main(() -> callback.onResult(result));
        });
    }

    /**
     * 按名称删除一个文件夹。
     * 注意:accounts 表设置了级联删除,删文件夹会把它下面的账号一起删掉,
     * 所以 UI 层必须先弹确认框。
     *
     * @param name     要删除的文件夹名
     * @param callback 结果回调:true 表示已删除,false 表示找不到该文件夹
     */
    public void delete(String name, Callback<Boolean> callback)
    {
        AppExecutors.get().diskIO(() ->
        {
            Folder folder = db.folderDao().findByName(name);
            boolean success = false;

            if (folder != null)
            {
                db.folderDao().delete(folder);
                success = true;
            }

            final boolean result = success;
            AppExecutors.get().main(() -> callback.onResult(result));
        });
    }
}
