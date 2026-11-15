package com.galaxysai.saiaccountnote.repository;

import android.content.Context;

import com.galaxysai.saiaccountnote.data.AppDatabase;
import com.galaxysai.saiaccountnote.data.entity.Account;

import java.util.List;


/**
 * 账号相关的数据入口(仓储层)。
 *
 * <p>和 FolderRepository 一样:异步执行数据库操作,
 * 结果通过 {@link Callback} 在主线程回调。</p>
 */
public class AccountRepository
{
    /**
     * 数据库实例,构造时初始化(原因同 FolderRepository)。
     */
    private final AppDatabase db;

    /**
     * @param context 任意 Context,内部转成 ApplicationContext
     */
    public AccountRepository(Context context)
    {
        this.db = AppDatabase.get(context.getApplicationContext());
    }

    /**
     * 查询某个文件夹下的全部账号。
     *
     * @param folderId 文件夹主键
     * @param callback 结果回调:账号列表,没有数据时是空列表
     */
    public void getByFolder(long folderId, Callback<List<Account>> callback)
    {
        AppExecutors.get().diskIO(() ->
        {
            List<Account> accounts = db.accountDao().getByFolder(folderId);
            AppExecutors.get().main(() -> callback.onResult(accounts));
        });
    }

    /**
     * 按主键查询一个账号。
     *
     * @param accountId 账号主键
     * @param callback  结果回调:找到返回对象,找不到返回 null
     */
    public void findById(long accountId, Callback<Account> callback)
    {
        AppExecutors.get().diskIO(() ->
        {
            Account account = db.accountDao().findById(accountId);
            AppExecutors.get().main(() -> callback.onResult(account));
        });
    }

    /**
     * 新建一个账号。
     * 创建时间、修改时间在这里统一赋值,调用方不用自己填。
     *
     * @param account  要新建的账号(至少要有 folderId、title 等)
     * @param callback 结果回调:true 表示成功
     */
    public void create(Account account, Callback<Boolean> callback)
    {
        AppExecutors.get().diskIO(() ->
        {
            long now = System.currentTimeMillis();
            account.createdAt = now;
            account.updatedAt = now;

            db.accountDao().insert(account);
            AppExecutors.get().main(() -> callback.onResult(true));
        });
    }

    /**
     * 修改一个账号。
     * 只自动更新 updatedAt,createdAt 保持原值。
     *
     * @param account  要修改的账号(必须带 id)
     * @param callback 结果回调:true 表示成功
     */
    public void update(Account account, Callback<Boolean> callback)
    {
        AppExecutors.get().diskIO(() ->
        {
            account.updatedAt = System.currentTimeMillis();

            db.accountDao().update(account);
            AppExecutors.get().main(() -> callback.onResult(true));
        });
    }

    /**
     * 按主键删除一个账号。
     * UI 层必须先弹确认框,再调用它。
     *
     * @param accountId 要删除的账号主键
     * @param callback  结果回调:true 表示已删除,false 表示找不到该账号
     */
    public void delete(long accountId, Callback<Boolean> callback)
    {
        AppExecutors.get().diskIO(() ->
        {
            Account account = db.accountDao().findById(accountId);
            boolean success = false;

            if (account != null)
            {
                db.accountDao().delete(account);
                success = true;
            }

            final boolean result = success;
            AppExecutors.get().main(() -> callback.onResult(result));
        });
    }
}
