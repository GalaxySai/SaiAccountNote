package com.galaxysai.saiaccountnote.ui.account;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.galaxysai.saiaccountnote.R;
import com.galaxysai.saiaccountnote.common.SecureWindow;
import com.galaxysai.saiaccountnote.data.entity.Account;
import com.galaxysai.saiaccountnote.repository.AccountRepository;
import com.galaxysai.saiaccountnote.security.SessionManager;
import com.galaxysai.saiaccountnote.ui.unlock.UnlockActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


/**
 * 账号列表页(某个文件夹下的所有账号)。
 *
 * <p>布局对应 {@code res/layout/user_activity.xml}(预览版转正),
 * 单条账号对应 {@code res/layout/item_account.xml}。</p>
 */
public class UserActivity extends AppCompatActivity implements PopupMenu.OnMenuItemClickListener
{
    /** 当前文件夹主键 */
    private long folderId;

    /** 当前文件夹名字,只用于标题展示 */
    private String folderName;

    /** 账号列表容器 */
    private LinearLayout container;

    /** 空状态提示 */
    private TextView tvEmpty;

    /** 账号数据入口 */
    private AccountRepository accountRepository;

    /** 当前展示的账号列表(过滤后的) */
    private List<Account> accounts = new ArrayList<>();

    /** 完整账号列表(过滤前的原始数据) */
    private List<Account> allAccounts = new ArrayList<>();

    /** 搜索框 */
    private EditText etSearch;

    /** 当前选中的账号,供修改/删除使用 */
    private Account tempAccount;

    public long getFolderId()
    {
        return folderId;
    }

    public String getFolderName()
    {
        return folderName;
    }

    public void setTempAccount(Account account)
    {
        this.tempAccount = account;
    }

    public Account getTempAccount()
    {
        return tempAccount;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        SecureWindow.apply(getWindow());

        // 未解锁 → 先去解锁页
        if (!SessionManager.isUnlocked())
        {
            startActivity(new Intent(this, UnlockActivity.class));
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.user_activity);

        applySystemBarInsets();

        // 接收 HomeActivity 传来的文件夹信息
        Intent intent = getIntent();
        folderId = intent.getLongExtra("folderId", -1L);
        folderName = intent.getStringExtra("folderName");
        if (folderName == null)
        {
            folderName = "";
        }

        TextView title = findViewById(R.id.user_activity_table_name);
        title.setText(folderName);

        container = findViewById(R.id.user_container);
        tvEmpty = findViewById(R.id.tv_account_empty);

        // 返回
        findViewById(R.id.account_back).setOnClickListener(v -> finish());

        // 右上角新建账号
        findViewById(R.id.account_add).setOnClickListener(v -> showNewUserDialog());

        // 搜索框:输入即过滤账号
        etSearch = findViewById(R.id.et_account_search);
        etSearch.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
            }

            @Override
            public void afterTextChanged(Editable s)
            {
                applyAccountFilter();
            }
        });

        accountRepository = new AccountRepository(this);
        loadAccounts();
    }

    /**
     * 避开状态栏/导航栏,并设置系统栏图标明暗(和首页一致)。
     */
    private void applySystemBarInsets()
    {
        View root = findViewById(R.id.root_account);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) ->
        {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        // 深色主题下用浅色图标,浅色主题下用深色图标
        boolean isNight = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        controller.setAppearanceLightStatusBars(!isNight);
        controller.setAppearanceLightNavigationBars(!isNight);
    }

    /**
     * 从 Room 读取当前文件夹下的账号。
     */
    /**
     * 回到前台时检查会话是否已因超时被锁定。
     */
    @Override
    protected void onStart()
    {
        super.onStart();

        if (!SessionManager.isUnlocked() && !isFinishing())
        {
            startActivity(new Intent(this, UnlockActivity.class));
            finish();
        }
    }

    private void loadAccounts()
    {
        if (folderId <= 0)
        {
            showAccounts();
            return;
        }

        accountRepository.getByFolder(folderId, list ->
        {
            allAccounts.clear();
            allAccounts.addAll(list);
            applyAccountFilter();
        });
    }

    /**
     * 按搜索框内容过滤账号(标题或用户名包含关键字),再渲染。
     */
    private void applyAccountFilter()
    {
        String query = etSearch == null ? "" : etSearch.getText().toString().trim();
        String lower = query.toLowerCase(Locale.ROOT);

        accounts.clear();
        for (Account account : allAccounts)
        {
            String title = account.title == null ? "" : account.title;
            String username = account.username == null ? "" : account.username;

            if (lower.isEmpty()
                    || title.toLowerCase(Locale.ROOT).contains(lower)
                    || username.toLowerCase(Locale.ROOT).contains(lower))
            {
                accounts.add(account);
            }
        }

        showAccounts();
    }

    /**
     * 渲染账号列表。
     */
    private void showAccounts()
    {
        container.removeAllViews();
        tvEmpty.setVisibility(accounts.isEmpty() ? View.VISIBLE : View.GONE);

        // 区分“真没有账号”和“搜索无结果”
        String query = etSearch == null ? "" : etSearch.getText().toString().trim();
        tvEmpty.setText(query.isEmpty()
                ? "还没有账号,点右上角 + 新建一个"
                : "没有找到匹配的账号");

        for (Account account : accounts)
        {
            View row = LayoutInflater.from(this).inflate(R.layout.item_account, container, false);

            LinearLayout header = row.findViewById(R.id.account_header);
            LinearLayout detail = row.findViewById(R.id.account_detail);
            ImageButton menu = row.findViewById(R.id.account_menu);

            TextView avatar = row.findViewById(R.id.account_avatar);
            TextView title = row.findViewById(R.id.account_title);
            TextView subtitle = row.findViewById(R.id.account_subtitle);

            TextView tvUsername = row.findViewById(R.id.tv_detail_username);
            TextView tvPassword = row.findViewById(R.id.tv_detail_password);
            TextView tvToggle = row.findViewById(R.id.tv_toggle_password);
            TextView tvEmail = row.findViewById(R.id.tv_detail_email);
            TextView tvPhone = row.findViewById(R.id.tv_detail_phone);
            TextView tvUrl = row.findViewById(R.id.tv_detail_url);
            TextView tvNotes = row.findViewById(R.id.tv_detail_notes);

            // 头部:首字母头像 + 标题 + 用户名
            avatar.setText(firstLetter(account.title));
            title.setText(account.title);
            subtitle.setText(account.username == null || account.username.isEmpty()
                    ? "无用户名" : account.username);

            // 详情
            tvUsername.setText(safe(account.username));
            tvEmail.setText(safe(account.email));
            tvPhone.setText(safe(account.phone));
            tvUrl.setText(safe(account.url));
            tvNotes.setText(safe(account.notes));

            // 密码默认打码
            final boolean[] passwordVisible = {false};
            if (account.password == null || account.password.isEmpty())
            {
                tvPassword.setText("—");
                tvToggle.setVisibility(View.GONE);
            }
            else
            {
                tvPassword.setText("••••••••");
                tvToggle.setOnClickListener(v ->
                {
                    passwordVisible[0] = !passwordVisible[0];
                    tvPassword.setText(passwordVisible[0] ? account.password : "••••••••");
                    tvToggle.setText(passwordVisible[0] ? "隐藏" : "显示");
                });
            }

            // 点击头部展开/收起详情
            header.setOnClickListener(v ->
                    detail.setVisibility(detail.getVisibility() == View.VISIBLE
                            ? View.GONE : View.VISIBLE));

            // 菜单:修改 / 删除
            menu.setOnClickListener(v ->
            {
                setTempAccount(account);
                showPopupMenu(menu);
            });

            container.addView(row);
        }
    }

    /**
     * 刷新账号列表。
     */
    public void refresh()
    {
        loadAccounts();
    }

    private void showPopupMenu(View view)
    {
        PopupMenu popupMenu = new PopupMenu(this, view);
        popupMenu.getMenuInflater().inflate(R.menu.user_user_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(this);
        popupMenu.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item)
    {
        int itemId = item.getItemId();
        if (itemId == R.id.userMenuRevise)
        {
            showReviseDialog();
            return true;
        }
        else if (itemId == R.id.userMenuDelete)
        {
            showWarningDialog();
            return true;
        }
        return false;
    }

    private void showNewUserDialog()
    {
        NewAccountBottomSheet sheet = new NewAccountBottomSheet();
        sheet.show(getSupportFragmentManager(), "新建账号");
    }

    private void showReviseDialog()
    {
        Account temp = getTempAccount();
        if (temp == null)
        {
            Toast.makeText(getApplicationContext(), "未选择账号", Toast.LENGTH_SHORT).show();
            return;
        }

        EditAccountBottomSheet sheet = EditAccountBottomSheet.newInstance(temp.id);
        sheet.show(getSupportFragmentManager(), "修改账号");
    }

    private void showWarningDialog()
    {
        Account temp = getTempAccount();
        if (temp == null)
        {
            Toast.makeText(getApplicationContext(), "未选择账号", Toast.LENGTH_SHORT).show();
            return;
        }

        WarningDialogFragmentOnDropUser warningDialog =
                WarningDialogFragmentOnDropUser.newInstance(temp.id);
        warningDialog.show(getSupportFragmentManager(), "删除账号警告");
    }

    /**
     * 取标题首字母做头像;空标题用问号。
     */
    private String firstLetter(String text)
    {
        if (text == null || text.trim().isEmpty())
        {
            return "?";
        }
        return text.trim().substring(0, 1).toUpperCase();
    }

    /**
     * 空值统一显示成短横线。
     */
    private String safe(String text)
    {
        if (text == null || text.trim().isEmpty())
        {
            return "—";
        }
        return text;
    }
}
