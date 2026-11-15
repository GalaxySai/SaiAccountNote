package com.galaxysai.saiaccountnote.ui.home;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.galaxysai.saiaccountnote.common.SecureWindow;
import com.galaxysai.saiaccountnote.repository.FolderRepository;


/**
 * 删除文件夹的确认对话框(改为 Material 风格)。
 * 只传文件夹名,不传其它对象。
 */
public class WarningDialogFragmentOnDropTable extends DialogFragment
{
    /** Bundle 中保存“文件夹名”的 key */
    private static final String ARG_TABLE_NAME = "arg_table_name";

    /**
     * 必须保留的无参构造函数。
     */
    public WarningDialogFragmentOnDropTable()
    {
        // 空实现
    }

    /**
     * 推荐的创建方式。
     *
     * @param tableName 要删除的文件夹名
     */
    public static WarningDialogFragmentOnDropTable newInstance(String tableName)
    {
        WarningDialogFragmentOnDropTable fragment = new WarningDialogFragmentOnDropTable();

        Bundle args = new Bundle();
        args.putString(ARG_TABLE_NAME, tableName);
        fragment.setArguments(args);

        return fragment;
    }

    private String getTableName()
    {
        Bundle args = getArguments();
        return args == null ? "" : args.getString(ARG_TABLE_NAME, "");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState)
    {
        return new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除文件夹")
                .setMessage("该文件夹下的账号也会一起删除,且不可恢复。")
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
     * 执行删除(异步),成功后刷新首页。
     */
    private void deleteConfirmed()
    {
        final String tableName = getTableName();

        if (tableName.isEmpty())
        {
            return;
        }

        // 先拿 Context:异步回调时 Fragment 可能已经 detach
        final Context context = requireContext();
        FolderRepository repository = new FolderRepository(context);

        repository.delete(tableName, success ->
        {
            if (!isAdded())
            {
                return;
            }

            if (success)
            {
                Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show();

                if (getActivity() instanceof HomeActivity)
                {
                    ((HomeActivity) getActivity()).refresh();
                }
            }
            else
            {
                Toast.makeText(context, "删除失败,请检查文件夹是否存在", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
