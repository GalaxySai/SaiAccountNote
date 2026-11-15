package com.galaxysai.saiaccountnote.ui.home;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.galaxysai.saiaccountnote.R;
import com.galaxysai.saiaccountnote.common.SecureWindow;
import com.galaxysai.saiaccountnote.repository.FolderRepository;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;


/**
 * 重命名文件夹的底部弹窗。
 * 复用 {@code res/layout/dialog_folder_name.xml},只是标题不同、预填原名称。
 */
public class RenameFolderBottomSheet extends BottomSheetDialogFragment
{
    /** Bundle 中保存“旧文件夹名”的 key */
    private static final String ARG_OLD_NAME = "arg_old_name";

    /** 旧名称 */
    private String oldName;

    /** 名称输入框 */
    private EditText etName;

    /** 标题 */
    private TextView tvTitle;

    /**
     * 必须保留的无参构造函数。
     */
    public RenameFolderBottomSheet()
    {
        // 空实现
    }

    /**
     * 推荐的创建方式。
     *
     * @param oldName 被重命名的文件夹原名
     */
    public static RenameFolderBottomSheet newInstance(String oldName)
    {
        RenameFolderBottomSheet sheet = new RenameFolderBottomSheet();

        Bundle args = new Bundle();
        args.putString(ARG_OLD_NAME, oldName);
        sheet.setArguments(args);

        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState)
    {
        oldName = getArguments() == null ? "" : getArguments().getString(ARG_OLD_NAME, "");

        View view = inflater.inflate(R.layout.dialog_folder_name, container, false);
        initView(view);
        return view;
    }

    /**
     * 让键盘弹出时窗口自动缩小。
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState)
    {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        SecureWindow.apply(dialog.getWindow());

        if (dialog.getWindow() != null)
        {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        return dialog;
    }

    @Override
    public void onStart()
    {
        super.onStart();

        if (getDialog() instanceof BottomSheetDialog)
        {
            BottomSheetBehavior<?> behavior = ((BottomSheetDialog) getDialog()).getBehavior();
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        }
    }

    private void initView(View view)
    {
        tvTitle = view.findViewById(R.id.tv_folder_sheet_title);
        tvTitle.setText("重命名文件夹");

        etName = view.findViewById(R.id.et_folder_name);
        etName.setText(oldName);
        etName.setSelection(etName.getText() == null ? 0 : etName.getText().length());

        ImageView close = view.findViewById(R.id.iv_folder_close);
        Button cancel = view.findViewById(R.id.btn_folder_cancel);
        Button save = view.findViewById(R.id.btn_folder_save);

        close.setOnClickListener(v -> dismiss());
        cancel.setOnClickListener(v -> dismiss());
        save.setOnClickListener(v -> saveRename());
    }

    /**
     * 校验新名称并重命名。
     */
    private void saveRename()
    {
        String newName = etName.getText() == null ? "" : etName.getText().toString().trim();

        if (newName.isEmpty())
        {
            Toast.makeText(getContext(), "名称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (newName.length() > 20)
        {
            Toast.makeText(getContext(), "名称不能大于20个字符", Toast.LENGTH_SHORT).show();
            return;
        }

        // 名字没变,直接关掉,不用查库
        if (newName.equals(oldName))
        {
            dismiss();
            return;
        }

        final Context context = requireContext();
        FolderRepository repository = new FolderRepository(context);

        repository.rename(oldName, newName, success ->
        {
            if (!isAdded())
            {
                return;
            }

            Toast.makeText(context,
                    success ? "重命名成功" : "重命名失败:名称已存在",
                    Toast.LENGTH_SHORT).show();

            if (success)
            {
                if (getActivity() instanceof HomeActivity)
                {
                    ((HomeActivity) getActivity()).refresh();
                }
                dismiss();
            }
        });
    }
}
