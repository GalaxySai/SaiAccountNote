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
 * 新建文件夹的底部弹窗。
 * 对应布局:{@code res/layout/dialog_folder_name.xml}
 */
public class NewFolderBottomSheet extends BottomSheetDialogFragment
{
    /** 文件夹名输入框 */
    private EditText etName;

    /** 标题 */
    private TextView tvTitle;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState)
    {
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

    /**
     * 展开到合适高度,并允许键盘压缩布局。
     */
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
        tvTitle.setText("新建文件夹");

        etName = view.findViewById(R.id.et_folder_name);

        ImageView close = view.findViewById(R.id.iv_folder_close);
        Button cancel = view.findViewById(R.id.btn_folder_cancel);
        Button save = view.findViewById(R.id.btn_folder_save);

        close.setOnClickListener(v -> dismiss());
        cancel.setOnClickListener(v -> dismiss());
        save.setOnClickListener(v -> saveFolder());
    }

    /**
     * 校验名称并创建。
     */
    private void saveFolder()
    {
        String name = etName.getText() == null ? "" : etName.getText().toString().trim();

        if (name.isEmpty())
        {
            Toast.makeText(getContext(), "名称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (name.length() > 20)
        {
            Toast.makeText(getContext(), "名称不能大于20个字符", Toast.LENGTH_SHORT).show();
            return;
        }

        final Context context = requireContext();
        FolderRepository repository = new FolderRepository(context);

        repository.create(name, success ->
        {
            if (!isAdded())
            {
                return;
            }

            if (success)
            {
                Toast.makeText(context, "新建成功", Toast.LENGTH_SHORT).show();

                if (getActivity() instanceof HomeActivity)
                {
                    ((HomeActivity) getActivity()).refresh();
                }

                dismiss();
            }
            else
            {
                Toast.makeText(context, "该分类已经创建过了", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
