package com.galaxysai.saiaccountnote.repository;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * 全局线程调度器。
 *
 * <p>为什么需要它?</p>
 * <p>数据库读写是耗时操作,如果放在主线程(UI 线程)执行,
 * 数据一多就可能卡顿甚至 ANR。所以统一用后台线程跑数据库,
 * 再把结果切回主线程更新界面。</p>
 *
 * <p>这里用“单线程池”而不是多线程,是因为 SQLite 本身更适合串行访问,
 * 多线程并发写容易出锁冲突。</p>
 */
public final class AppExecutors
{
    /**
     * 全局唯一实例。
     * 线程池和 Handler 都只需要一份,没必要反复创建。
     */
    private static final AppExecutors INSTANCE = new AppExecutors();

    /**
     * 后台线程池:只开一个线程,保证数据库操作按提交顺序执行。
     */
    private final ExecutorService diskIO = Executors.newSingleThreadExecutor();

    /**
     * 主线程 Handler:后台任务完成后,用它把结果 post 回 UI 线程。
     */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * 私有构造:禁止外部 new,只能通过 {@link #get()} 拿单例。
     */
    private AppExecutors()
    {
        // 空实现
    }

    /**
     * 获取全局唯一实例。
     *
     * @return AppExecutors 单例
     */
    public static AppExecutors get()
    {
        return INSTANCE;
    }

    /**
     * 把任务丢到后台线程执行。
     *
     * @param task 要执行的任务(通常是数据库操作)
     */
    public void diskIO(Runnable task)
    {
        diskIO.execute(task);
    }

    /**
     * 把任务切回主线程执行。
     *
     * @param task 要执行的任务(通常是更新 UI)
     */
    public void main(Runnable task)
    {
        mainHandler.post(task);
    }
}
