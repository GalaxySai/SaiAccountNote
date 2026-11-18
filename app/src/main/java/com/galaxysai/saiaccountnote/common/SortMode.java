package com.galaxysai.saiaccountnote.common;


/**
 * 列表排序模式。
 *
 * <p>对话框里的选项顺序要和这里的常量顺序一致。</p>
 */
public final class SortMode
{
    /** 名称 A → Z(默认) */
    public static final int NAME_ASC = 0;

    /** 名称 Z → A */
    public static final int NAME_DESC = 1;

    /** 时间:新 → 旧(账号用 updated_at,文件夹用 created_at) */
    public static final int TIME_DESC = 2;

    /** 时间:旧 → 新 */
    public static final int TIME_ASC = 3;

    private SortMode()
    {
        // 工具类禁止实例化
    }
}
