package com.galaxysai.saiaccountnote.repository;


/**
 * 一个极简的异步回调接口。
 *
 * <p>Repository 的方法都在后台线程执行,
 * 完成后通过这个回调把结果交回调用方(通常是 Activity)。</p>
 *
 * <p>约定:回调一定在<b>主线程</b>触发,调用方可以直接更新 UI。</p>
 *
 * @param <T> 结果的数据类型,例如 List&lt;Folder&gt;、Boolean
 */
public interface Callback<T>
{
    /**
     * 任务完成时被调用。
     *
     * @param data 结果数据。查询类操作返回数据本身;
     *             写入类操作约定返回 true 表示成功、false 表示失败。
     */
    void onResult(T data);
}
