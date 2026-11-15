package com.galaxysai.saiaccountnote.ui.account;

import com.galaxysai.saiaccountnote.R;
import com.galaxysai.saiaccountnote.common.SecureWindow;

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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.galaxysai.saiaccountnote.data.entity.Account;
import com.galaxysai.saiaccountnote.repository.AccountRepository;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;


/**
 * 新建账号的底部弹窗(BottomSheet)。
 *
 * <p>对应布局:{@code res/layout/dialog_new_account.xml}</p>
 * <p>职责:收集表单 → 校验长度 → 组装 Account → 调 AccountRepository.create。</p>
 */
public class NewAccountBottomSheet extends BottomSheetDialogFragment
{
    /** 标题输入框(必填) */
    private EditText etTitle;

    /** 用户名输入框 */
    private EditText etUsername;

    /** 密码输入框 */
    private EditText etPassword;

    /** 绑定邮箱输入框 */
    private EditText etEmail;

    /** 绑定手机输入框 */
    private EditText etPhone;

    /** 登录网址输入框 */
    private EditText etUrl;

    /** 备注输入框 */
    private EditText etNotes;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState)
    {
        View view = inflater.inflate(R.layout.dialog_new_account, container, false);
        initView(view);
        return view;
    }

    /**
     * 让弹窗窗口支持边到边,并监听键盘高度。
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
     * 让弹窗默认展开到最大高度。
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
            // 禁止下滑关闭,避免填表时误触;关闭请用 X / 取消 / 返回键
            behavior.setHideable(false);
        }
    }

    /**
     * 绑定控件和点击事件。
     */
    private void initView(View view)
    {
        etTitle = view.findViewById(R.id.et_new_title);
        etUsername = view.findViewById(R.id.et_new_username);
        etPassword = view.findViewById(R.id.et_new_password);
        etEmail = view.findViewById(R.id.et_new_email);
        etPhone = view.findViewById(R.id.et_new_phone);
        etUrl = view.findViewById(R.id.et_new_url);
        etNotes = view.findViewById(R.id.et_new_notes);

        ImageView close = view.findViewById(R.id.iv_close);
        Button save = view.findViewById(R.id.btn_new_save);
        Button cancel = view.findViewById(R.id.btn_new_cancel);

        close.setOnClickListener(v -> dismiss());
        cancel.setOnClickListener(v -> dismiss());
        save.setOnClickListener(v -> saveAccount());
    }

    /**
     * 读取输入、校验、保存。
     */
    private void saveAccount()
    {
        String title = textOf(etTitle);
        String username = textOf(etUsername);
        String password = textOf(etPassword);
        String email = textOf(etEmail);
        String phone = textOf(etPhone);
        String url = textOf(etUrl);
        String notes = textOf(etNotes);

        // 标题必填,否则列表里会出现一行空白
        if (title.isEmpty())
        {
            Toast.makeText(getContext(), "标题不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        // 长度校验(XML 里也有 maxLength 兜底,这里再校验一次更稳)
        if (title.length() > 32)
        {
            Toast.makeText(getContext(), "标题长度不能大于32", Toast.LENGTH_SHORT).show();
            return;
        }
        if (username.length() > 32)
        {
            Toast.makeText(getContext(), "用户名长度不能大于32", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() > 128)
        {
            Toast.makeText(getContext(), "密码长度不能大于128", Toast.LENGTH_SHORT).show();
            return;
        }
        if (email.length() > 64)
        {
            Toast.makeText(getContext(), "邮箱长度不能大于64", Toast.LENGTH_SHORT).show();
            return;
        }
        if (phone.length() > 32)
        {
            Toast.makeText(getContext(), "手机号长度不能大于32", Toast.LENGTH_SHORT).show();
            return;
        }
        if (url.length() > 256)
        {
            Toast.makeText(getContext(), "网址长度不能大于256", Toast.LENGTH_SHORT).show();
            return;
        }
        if (notes.length() > 255)
        {
            Toast.makeText(getContext(), "备注长度不能大于255", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!(getActivity() instanceof UserActivity))
        {
            return;
        }

        UserActivity activity = (UserActivity) getActivity();
        if (activity.getFolderId() <= 0)
        {
            Toast.makeText(getContext(), "文件夹信息异常,无法新建", Toast.LENGTH_SHORT).show();
            return;
        }

        // 组装实体
        Account account = new Account();
        account.folderId = activity.getFolderId();
        account.title = title;
        account.username = username;
        account.password = password;
        account.email = email;
        account.phone = phone;
        account.url = url;
        account.notes = notes;

        // 先拿 Context,避免异步回调时 Fragment 已经 detach
        final Context context = requireContext();
        AccountRepository repository = new AccountRepository(context);

        repository.create(account, success ->
        {
            if (!isAdded())
            {
                return;
            }

            if (success)
            {
                Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show();

                if (getActivity() instanceof UserActivity)
                {
                    ((UserActivity) getActivity()).refresh();
                }

                dismiss();
            }
            else
            {
                Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 安全地读取输入框文本:去首尾空格,空值返回空串。
     */
    private String textOf(EditText editText)
    {
        if (editText == null || editText.getText() == null)
        {
            return "";
        }
        return editText.getText().toString().trim();
    }
}
