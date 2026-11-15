package com.galaxysai.saiaccountnote.ui.account;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.galaxysai.saiaccountnote.common.SecureWindow;
import com.galaxysai.saiaccountnote.repository.AccountRepository;


/**
 * 删除账号的确认对话框(改为 Material 风格)。
 * 只传账号主键,不传实体对象。
 */
public class WarningDialogFragmentOnDropUser extends DialogFragment
{
    /** Bundle 中保存“账号主键”的 key */
    private static final String ARG_ACCOUNT_ID = "arg_account_id";

    /**
     * 必须保留的无参构造函数。
     */
    public WarningDialogFragmentOnDropUser()
    {
        // 空实现
    }

    /**
     * 推荐的创建方式。
     *
     * @param accountId 要删除的账号主键
     */
    public static WarningDialogFragmentOnDropUser newInstance(long accountId)
    {
        WarningDialogFragmentOnDropUser fragment = new WarningDialogFragmentOnDropUser();

        Bundle args = new Bundle();
        args.putLong(ARG_ACCOUNT_ID, accountId);
        fragment.setArguments(args);

        return fragment;
    }

    private long getAccountId()
    {
        Bundle args = getArguments();
        return args == null ? -1L : args.getLong(ARG_ACCOUNT_ID, -1L);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState)
    {
        return new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除账号")
                .setMessage("删除后不可恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteConfirmed())
                .create();
    }

    /**
     * 对话框窗口也加上防截屏。
     */
    @Override
    public void onStart()
    {
        super.onStart();
        if (getDialog() != null)
        {
            SecureWindow.apply(getDialog().getWindow());
        }
    }

    /**
     * 执行删除(异步),成功后刷新列表。
     */
    private void deleteConfirmed()
    {
        long accountId = getAccountId();

        // 防御性判断:id 非法时直接返回
        if (accountId <= 0)
        {
            Toast.makeText(getContext(), "删除失败:账号信息不完整", Toast.LENGTH_SHORT).show();
            return;
        }

        // 先拿 Context:异步回调时 Fragment 可能已经 detach
        final Context context = requireContext();
        AccountRepository repository = new AccountRepository(context);

        repository.delete(accountId, success ->
        {
            if (!isAdded())
            {
                return;
            }

            if (success)
            {
                Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show();

                if (getActivity() instanceof UserActivity)
                {
                    ((UserActivity) getActivity()).refresh();
                }
            }
            else
            {
                Toast.makeText(context, "删除失败,请检查该账号是否存在", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
