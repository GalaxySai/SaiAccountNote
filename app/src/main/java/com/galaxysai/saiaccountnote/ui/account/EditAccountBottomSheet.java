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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.galaxysai.saiaccountnote.data.entity.Account;
import com.galaxysai.saiaccountnote.repository.AccountRepository;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;


/**
 * 修改账号的底部弹窗。
 *
 * <p>复用新建账号的布局 {@code res/layout/dialog_new_account.xml},
 * 只是把标题改成“修改账号”,并把原数据预填进去。</p>
 *
 * <p>参数通过 {@link #newInstance(long)} 传主键,不传整个 Account 对象,
 * 这样屏幕旋转/重建后也能恢复。</p>
 */
public class EditAccountBottomSheet extends BottomSheetDialogFragment
{
    /** Bundle 中保存“账号主键”的 key */
    private static final String ARG_ACCOUNT_ID = "arg_account_id";

    /** 当前要修改的账号(加载完成后才有值) */
    private Account account;

    /** 账号主键 */
    private long accountId;

    /** 弹窗标题 */
    private TextView tvTitle;

    /** 标题输入框 */
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

    /**
     * 必须保留的无参构造函数。
     */
    public EditAccountBottomSheet()
    {
        // 空实现:参数走 newInstance + Bundle
    }

    /**
     * 推荐的创建方式。
     *
     * @param accountId 要修改的账号主键
     * @return 配置好参数的弹窗实例
     */
    public static EditAccountBottomSheet newInstance(long accountId)
    {
        EditAccountBottomSheet sheet = new EditAccountBottomSheet();

        Bundle args = new Bundle();
        args.putLong(ARG_ACCOUNT_ID, accountId);
        sheet.setArguments(args);

        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState)
    {
        View view = inflater.inflate(R.layout.dialog_new_account, container, false);

        accountId = getArguments() == null ? -1L : getArguments().getLong(ARG_ACCOUNT_ID, -1L);

        initView(view);
        loadAccount();

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
     * 展开到最大高度。
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
     * 绑定控件、把标题改成“修改账号”、绑定按钮事件。
     */
    private void initView(View view)
    {
        tvTitle = view.findViewById(R.id.tv_sheet_title);
        tvTitle.setText("修改账号");

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
     * 从数据库按 id 读出账号,再预填到表单。
     */
    private void loadAccount()
    {
        if (accountId <= 0)
        {
            Toast.makeText(getContext(), "账号信息异常", Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        AccountRepository repository = new AccountRepository(requireContext());

        repository.findById(accountId, loaded ->
        {
            if (!isAdded())
            {
                return;
            }

            if (loaded == null)
            {
                Toast.makeText(getContext(), "账号不存在", Toast.LENGTH_SHORT).show();
                dismiss();
                return;
            }

            this.account = loaded;
            fillForm(loaded);
        });
    }

    /**
     * 把账号原值填进输入框。
     */
    private void fillForm(Account a)
    {
        etTitle.setText(a.title);
        etUsername.setText(a.username);
        etPassword.setText(a.password);
        etEmail.setText(a.email);
        etPhone.setText(a.phone);
        etUrl.setText(a.url);
        etNotes.setText(a.notes);
    }

    /**
     * 读取输入、校验、更新。
     */
    private void saveAccount()
    {
        // 数据还没加载完就点了保存,直接忽略
        if (account == null)
        {
            return;
        }

        String title = textOf(etTitle);
        String username = textOf(etUsername);
        String password = textOf(etPassword);
        String email = textOf(etEmail);
        String phone = textOf(etPhone);
        String url = textOf(etUrl);
        String notes = textOf(etNotes);

        if (title.isEmpty())
        {
            Toast.makeText(getContext(), "标题不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
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

        // 在原对象上改字段,id / folderId / createdAt 都会保留
        account.title = title;
        account.username = username;
        account.password = password;
        account.email = email;
        account.phone = phone;
        account.url = url;
        account.notes = notes;

        final Context context = requireContext();
        AccountRepository repository = new AccountRepository(context);

        repository.update(account, success ->
        {
            if (!isAdded())
            {
                return;
            }

            if (success)
            {
                Toast.makeText(context, "修改成功", Toast.LENGTH_SHORT).show();

                if (getActivity() instanceof UserActivity)
                {
                    ((UserActivity) getActivity()).refresh();
                }

                dismiss();
            }
            else
            {
                Toast.makeText(context, "修改失败", Toast.LENGTH_SHORT).show();
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
