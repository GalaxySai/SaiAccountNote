package com.galaxysai.saiaccountnote.security;


/**
 * 当前会话状态。
 *
 * <p>只存在内存里:进程一旦被杀死,状态就清空,下次必须重新解锁。</p>
 *
 * <p>自动锁定:App 进入后台时记录时间,超过 5 分钟再回来
 * 就清除密钥,要求重新解锁。前台使用期间不会锁。</p>
 */
public final class SessionManager
{
    /** 后台超过这个时间就自动锁定(5 分钟) */
    private static final long AUTO_LOCK_TIMEOUT_MS = 5 * 60 * 1000L;

    /** 派生出的数据库密钥(之后给 SQLCipher 用) */
    private static byte[] dbKey;

    /** 是否已解锁 */
    private static boolean unlocked = false;

    /** 进入后台的时间;0 表示当前在前台或未解锁 */
    private static long backgroundAt = 0L;

    private SessionManager()
    {
        // 工具类禁止实例化
    }

    /**
     * 解锁并保存本次会话的密钥。
     */
    public static void unlock(byte[] key)
    {
        dbKey = key;
        unlocked = key != null;
        backgroundAt = 0L;
    }

    /**
     * 是否已解锁。
     * 如果后台停留超时,会在这里自动锁定并返回 false。
     */
    public static boolean isUnlocked()
    {
        if (!unlocked || dbKey == null)
        {
            return false;
        }

        if (backgroundAt > 0
                && System.currentTimeMillis() - backgroundAt >= AUTO_LOCK_TIMEOUT_MS)
        {
            lock();
            return false;
        }

        return true;
    }

    /**
     * 获取数据库密钥(供 SQLCipher 使用)。
     */
    public static byte[] getDbKey()
    {
        return dbKey;
    }

    /**
     * 锁定:清除密钥。
     */
    public static void lock()
    {
        dbKey = null;
        unlocked = false;
        backgroundAt = 0L;
    }

    /**
     * App 进入后台(所有 Activity 都 stop 时调用)。
     */
    public static void onAppBackground()
    {
        if (unlocked)
        {
            backgroundAt = System.currentTimeMillis();
        }
    }

    /**
     * App 回到前台:超过阈值则锁定,否则清除后台时间。
     */
    public static void onAppForeground()
    {
        if (unlocked && backgroundAt > 0
                && System.currentTimeMillis() - backgroundAt >= AUTO_LOCK_TIMEOUT_MS)
        {
            lock();
        }

        backgroundAt = 0L;
    }
}
