package com.galaxysai.saiaccountnote.ui.account;

import android.content.ClipData;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.galaxysai.saiaccountnote.R;
import com.galaxysai.saiaccountnote.backup.BackupManager;
import com.galaxysai.saiaccountnote.backup.TableExporter;
import com.galaxysai.saiaccountnote.common.ClipboardHelper;
import com.galaxysai.saiaccountnote.common.SecureWindow;
import com.galaxysai.saiaccountnote.data.entity.Account;
import com.galaxysai.saiaccountnote.data.entity.Folder;
import com.galaxysai.saiaccountnote.repository.AccountRepository;
import com.galaxysai.saiaccountnote.repository.AppExecutors;
import com.galaxysai.saiaccountnote.repository.FolderRepository;
import com.galaxysai.saiaccountnote.security.SessionManager;
import com.galaxysai.saiaccountnote.ui.unlock.UnlockActivity;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


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

    /** 文件夹数据入口,移动账号时用来选目标文件夹 */
    private FolderRepository folderRepository;

    /** 当前展示的账号列表(过滤后的) */
    private List<Account> accounts = new ArrayList<>();

    /** 完整账号列表(过滤前的原始数据) */
    private List<Account> allAccounts = new ArrayList<>();

    /** 搜索框 */
    private EditText etSearch;

    /** 当前选中的账号,供修改/删除使用 */
    private Account tempAccount;

    /** 是否处于多选模式 */
    private boolean selectionMode;

    /** 当前选中的账号 id */
    private final Set<Long> selectedIds = new LinkedHashSet<>();

    /** 多选相关控件 */
    private View topBar;
    private View searchBar;
    private View selectionBar;
    private View selectionActionsDivider;
    private View selectionActions;
    private TextView selectionCount;
    private TextView selectionAll;

    /** 批量导出:暂存的加密选项和密码 */
    private boolean pendingBatchEncrypt;
    private String pendingBatchPassword;

    /** 批量导出保存位置 */
    private final ActivityResultLauncher<String> createBatchBackupLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("application/json"),
                    this::onBatchBackupFileChosen);

    /** 批量导出表格:暂存选中的账号 id */
    private List<Long> pendingBatchCsvIds;

    /** 批量导出表格保存位置 */
    private final ActivityResultLauncher<String> createBatchCsvLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("text/csv"),
                    this::onBatchCsvFileChosen);

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
        folderRepository = new FolderRepository(this);

        // 多选相关控件
        topBar = findViewById(R.id.account_top_bar);
        searchBar = findViewById(R.id.account_search_bar);
        selectionBar = findViewById(R.id.selection_bar);
        selectionActionsDivider = findViewById(R.id.selection_actions_divider);
        selectionActions = findViewById(R.id.selection_actions);
        selectionCount = findViewById(R.id.selection_count);
        selectionAll = findViewById(R.id.selection_all);

        findViewById(R.id.selection_close).setOnClickListener(v -> exitSelectionMode());
        selectionAll.setOnClickListener(v -> toggleSelectAll());
        findViewById(R.id.btn_batch_move).setOnClickListener(v -> batchMove());
        findViewById(R.id.btn_batch_delete).setOnClickListener(v -> batchDelete());
        findViewById(R.id.btn_batch_export).setOnClickListener(v -> batchExport());

        updateSelectionUi();
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
            CheckBox check = row.findViewById(R.id.account_check);

            TextView avatar = row.findViewById(R.id.account_avatar);
            TextView title = row.findViewById(R.id.account_title);
            TextView subtitle = row.findViewById(R.id.account_subtitle);

            TextView tvUsername = row.findViewById(R.id.tv_detail_username);
            TextView tvPassword = row.findViewById(R.id.tv_detail_password);
            TextView tvToggle = row.findViewById(R.id.tv_toggle_password);
            TextView tvCopyUsername = row.findViewById(R.id.tv_copy_username);
            TextView tvCopyPassword = row.findViewById(R.id.tv_copy_password);
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

            // 一键复制用户名/密码,60 秒后自动清剪贴板
            boolean hasUsername = account.username != null && !account.username.isEmpty();
            tvCopyUsername.setVisibility(hasUsername ? View.VISIBLE : View.GONE);
            tvCopyUsername.setOnClickListener(v ->
                    ClipboardHelper.copySensitive(this, "用户名", account.username));

            boolean hasPassword = account.password != null && !account.password.isEmpty();
            tvCopyPassword.setVisibility(hasPassword ? View.VISIBLE : View.GONE);
            tvCopyPassword.setOnClickListener(v ->
                    ClipboardHelper.copySensitive(this, "密码", account.password));

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

            // 多选模式:显示勾选框、隐藏菜单、收起详情
            check.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
            check.setChecked(selectedIds.contains(account.id));
            menu.setVisibility(selectionMode ? View.GONE : View.VISIBLE);
            if (selectionMode)
            {
                detail.setVisibility(View.GONE);
            }

            // 普通模式点头部展开详情;多选模式点一下切换选中
            header.setOnClickListener(v ->
            {
                if (selectionMode)
                {
                    toggleSelection(account.id, check);
                }
                else
                {
                    detail.setVisibility(detail.getVisibility() == View.VISIBLE
                            ? View.GONE : View.VISIBLE);
                }
            });

            // 长按进入多选;已多选时长按也切换选中
            header.setOnLongClickListener(v ->
            {
                if (selectionMode)
                {
                    toggleSelection(account.id, check);
                }
                else
                {
                    enterSelectionMode(account.id);
                }
                return true;
            });

            // 菜单:修改 / 删除
            menu.setOnClickListener(v ->
            {
                setTempAccount(account);
                showPopupMenu(menu);
            });

            container.addView(row);
        }
    }

    // ==================== 多选与批量操作 ====================

    /**
     * 进入多选模式,并选中长按的那个账号。
     */
    private void enterSelectionMode(long accountId)
    {
        selectionMode = true;
        selectedIds.clear();
        selectedIds.add(accountId);
        updateSelectionUi();
        showAccounts();
    }

    /**
     * 退出多选模式。
     */
    private void exitSelectionMode()
    {
        selectionMode = false;
        selectedIds.clear();
        updateSelectionUi();
        showAccounts();
    }

    /**
     * 切换一个账号的选中状态。
     * 直接更新当前行的勾选框,避免在渲染循环里重建整个列表。
     */
    private void toggleSelection(long accountId, CheckBox checkBox)
    {
        if (selectedIds.contains(accountId))
        {
            selectedIds.remove(accountId);
        }
        else
        {
            selectedIds.add(accountId);
        }

        if (selectedIds.isEmpty())
        {
            exitSelectionMode();
            return;
        }

        checkBox.setChecked(selectedIds.contains(accountId));
        updateSelectionUi();
    }

    /**
     * 全选当前列表 / 取消全选。
     */
    private void toggleSelectAll()
    {
        if (!selectionMode)
        {
            return;
        }

        if (!accounts.isEmpty() && selectedIds.size() >= accounts.size())
        {
            exitSelectionMode();
            return;
        }

        selectedIds.clear();
        for (Account account : accounts)
        {
            selectedIds.add(account.id);
        }

        updateSelectionUi();
        showAccounts();
    }

    /**
     * 根据多选状态切换顶部栏、底部操作栏的显示。
     */
    private void updateSelectionUi()
    {
        int visible = selectionMode ? View.VISIBLE : View.GONE;
        int normalVisible = selectionMode ? View.GONE : View.VISIBLE;

        selectionBar.setVisibility(visible);
        selectionActionsDivider.setVisibility(visible);
        selectionActions.setVisibility(visible);

        topBar.setVisibility(normalVisible);
        searchBar.setVisibility(normalVisible);

        if (selectionMode)
        {
            selectionCount.setText("已选 " + selectedIds.size() + " 个");

            boolean allSelected = !accounts.isEmpty()
                    && selectedIds.size() >= accounts.size();
            selectionAll.setText(allSelected ? "取消全选" : "全选");
        }
    }

    private List<Long> selectedIdList()
    {
        return new ArrayList<>(selectedIds);
    }

    /**
     * 批量移动:选目标文件夹,把选中的账号一起移过去。
     */
    private void batchMove()
    {
        if (selectedIds.isEmpty())
        {
            return;
        }

        final List<Long> ids = selectedIdList();

        folderRepository.getAll(folders ->
        {
            if (isFinishing() || isDestroyed())
            {
                return;
            }

            List<Folder> targets = new ArrayList<>();
            for (Folder folder : folders)
            {
                if (folder.id != folderId)
                {
                    targets.add(folder);
                }
            }

            if (targets.isEmpty())
            {
                Toast.makeText(this, "没有其他文件夹可移动", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] names = new String[targets.size()];
            for (int i = 0; i < targets.size(); i++)
            {
                names[i] = targets.get(i).name;
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle("移动 " + ids.size() + " 个账号到")
                    .setItems(names, (dialog, which) ->
                    {
                        Folder target = targets.get(which);

                        accountRepository.moveAll(ids, target.id, count ->
                        {
                            if (isFinishing() || isDestroyed())
                            {
                                return;
                            }

                            if (count > 0)
                            {
                                Toast.makeText(this,
                                        "已移动 " + count + " 个账号到「" + target.name + "」",
                                        Toast.LENGTH_SHORT).show();
                                exitSelectionMode();
                                loadAccounts();
                            }
                            else
                            {
                                Toast.makeText(this, "移动失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    /**
     * 批量删除,先弹确认框。
     */
    private void batchDelete()
    {
        if (selectedIds.isEmpty())
        {
            return;
        }

        final List<Long> ids = selectedIdList();

        new MaterialAlertDialogBuilder(this)
                .setTitle("删除账号")
                .setMessage("确定删除选中的 " + ids.size() + " 个账号吗?此操作不可恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) ->
                {
                    accountRepository.deleteAll(ids, count ->
                    {
                        if (isFinishing() || isDestroyed())
                        {
                            return;
                        }

                        Toast.makeText(this,
                                "已删除 " + count + " 个账号",
                                Toast.LENGTH_SHORT).show();
                        exitSelectionMode();
                        loadAccounts();
                    });
                })
                .show();
    }

    /**
     * 批量导出选中的账号。
     */
    private void batchExport()
    {
        if (selectedIds.isEmpty())
        {
            return;
        }

        showBatchExportDialog();
    }

    /**
     * 批量导出的密码/保存位置弹窗,和首页的导出弹窗保持一致。
     */
    private void showBatchExportDialog()
    {
        View content = getLayoutInflater().inflate(R.layout.dialog_backup_export, null);

        MaterialCheckBox cbEncrypt = content.findViewById(R.id.cb_encrypt);
        View layoutPassword = content.findViewById(R.id.layout_backup_password);
        EditText etPassword = content.findViewById(R.id.et_backup_password);
        EditText etConfirm = content.findViewById(R.id.et_backup_password_confirm);
        TextView tvWarning = content.findViewById(R.id.tv_export_warning);
        TextView tvError = content.findViewById(R.id.tv_export_error);

        cbEncrypt.setChecked(true);
        layoutPassword.setVisibility(View.VISIBLE);
        tvWarning.setVisibility(View.GONE);

        cbEncrypt.setOnCheckedChangeListener((buttonView, checked) ->
        {
            layoutPassword.setVisibility(checked ? View.VISIBLE : View.GONE);
            tvWarning.setVisibility(checked ? View.GONE : View.VISIBLE);
        });

        content.findViewById(R.id.btn_generate_password).setOnClickListener(v ->
        {
            String password = generateRandomPassword();
            etPassword.setText(password);
            etConfirm.setText(password);
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("导出选中的 " + selectedIds.size() + " 个账号")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("选择保存位置", null)
                .create();

        // 和首页的导出弹窗一致:这里也提供“导出成表格”
        content.findViewById(R.id.btn_export_table).setOnClickListener(v ->
        {
            dialog.dismiss();
            showBatchCsvWarning();
        });

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                {
                    boolean encrypt = cbEncrypt.isChecked();
                    String password = textOf(etPassword);
                    String confirm = textOf(etConfirm);

                    tvError.setVisibility(View.GONE);

                    if (!encrypt && (!password.isEmpty() || !confirm.isEmpty()))
                    {
                        showDialogError(tvError,
                                "未勾选“使用密码加密”,密码会被忽略。请勾选加密,或清空密码后再导出。");
                        return;
                    }

                    if (encrypt)
                    {
                        if (password.length() < 6)
                        {
                            showDialogError(tvError, "备份密码至少 6 位");
                            return;
                        }
                        if (!password.equals(confirm))
                        {
                            showDialogError(tvError, "两次输入的密码不一致");
                            return;
                        }
                    }

                    pendingBatchEncrypt = encrypt;
                    pendingBatchPassword = password;
                    dialog.dismiss();

                    createBatchBackupLauncher.launch(
                            "sai-selected-" + System.currentTimeMillis() + ".json");
                }));

        dialog.show();
    }

    /**
     * 用户选好保存位置后,只把选中的账号写进备份。
     */
    private void onBatchBackupFileChosen(Uri uri)
    {
        if (uri == null)
        {
            pendingBatchPassword = null;
            return;
        }

        final boolean encrypt = pendingBatchEncrypt;
        final String password = pendingBatchPassword;
        final List<Long> ids = selectedIdList();
        final long currentFolderId = folderId;
        pendingBatchPassword = null;

        AppExecutors.get().diskIO(() ->
        {
            String error = null;

            try (OutputStream out = getContentResolver().openOutputStream(uri))
            {
                if (out == null)
                {
                    throw new IllegalStateException("无法写入文件");
                }

                byte[] data = BackupManager.exportData(
                        this, encrypt, password, currentFolderId, ids);
                out.write(data);
                out.flush();
            }
            catch (Exception e)
            {
                error = e.getMessage();
            }

            final String finalError = error;
            AppExecutors.get().main(() ->
            {
                if (isFinishing() || isDestroyed())
                {
                    return;
                }

                if (finalError == null)
                {
                    Toast.makeText(this,
                            "已导出 " + ids.size() + " 个账号",
                            Toast.LENGTH_LONG).show();
                    exitSelectionMode();
                }
                else
                {
                    Toast.makeText(this,
                            "导出失败: " + finalError,
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /**
     * 批量导出表格前,先确认明文风险。
     */
    private void showBatchCsvWarning()
    {
        final List<Long> ids = selectedIdList();

        new MaterialAlertDialogBuilder(this)
                .setTitle("导出表格")
                .setMessage("表格是明文,包含选中 " + ids.size()
                        + " 个账号的密码。任何人打开都能看到,请只发给你信任的人,用完删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("继续", (dialog, which) ->
                {
                    pendingBatchCsvIds = ids;
                    createBatchCsvLauncher.launch(
                            "sai-selected-" + System.currentTimeMillis() + ".csv");
                })
                .show();
    }

    /**
     * 用户选好位置后,只把选中的账号写进 CSV。
     */
    private void onBatchCsvFileChosen(Uri uri)
    {
        if (uri == null)
        {
            pendingBatchCsvIds = null;
            return;
        }

        final List<Long> ids = pendingBatchCsvIds;
        pendingBatchCsvIds = null;

        if (ids == null || ids.isEmpty())
        {
            return;
        }

        AppExecutors.get().diskIO(() ->
        {
            String error = null;

            try (OutputStream out = getContentResolver().openOutputStream(uri))
            {
                if (out == null)
                {
                    throw new IllegalStateException("无法写入文件");
                }

                byte[] data = TableExporter.exportCsv(this, folderId, ids);
                out.write(data);
                out.flush();
            }
            catch (Exception e)
            {
                error = e.getMessage();
            }

            final String finalError = error;
            AppExecutors.get().main(() ->
            {
                if (isFinishing() || isDestroyed())
                {
                    return;
                }

                if (finalError == null)
                {
                    Toast.makeText(this,
                            "已导出 " + ids.size() + " 个账号",
                            Toast.LENGTH_LONG).show();
                    exitSelectionMode();
                    showBatchCsvShareDialog(uri);
                }
                else
                {
                    Toast.makeText(this,
                            "导出失败: " + finalError,
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void showBatchCsvShareDialog(Uri uri)
    {
        new MaterialAlertDialogBuilder(this)
                .setTitle("表格已导出")
                .setMessage("是否现在分享到微信/QQ?")
                .setNegativeButton("完成", null)
                .setPositiveButton("分享", (dialog, which) -> shareCsv(uri))
                .show();
    }

    private void shareCsv(Uri uri)
    {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/csv");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.setClipData(ClipData.newRawUri("accounts.csv", uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "分享表格"));
    }

    private String generateRandomPassword()
    {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*";
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder(20);

        for (int i = 0; i < 20; i++)
        {
            builder.append(chars.charAt(random.nextInt(chars.length())));
        }

        return builder.toString();
    }

    private String textOf(EditText editText)
    {
        return editText.getText() == null ? "" : editText.getText().toString();
    }

    private void showDialogError(TextView view, String message)
    {
        view.setText(message);
        view.setVisibility(View.VISIBLE);
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
        else if (itemId == R.id.userMenuMove)
        {
            showMoveDialog();
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

    /**
     * 把当前账号移动到另一个文件夹。
     */
    private void showMoveDialog()
    {
        Account temp = getTempAccount();
        if (temp == null)
        {
            Toast.makeText(getApplicationContext(), "未选择账号", Toast.LENGTH_SHORT).show();
            return;
        }

        folderRepository.getAll(folders ->
        {
            if (isFinishing() || isDestroyed())
            {
                return;
            }

            // 当前文件夹不用出现在“移动到”列表里
            List<Folder> targets = new ArrayList<>();
            for (Folder folder : folders)
            {
                if (folder.id != temp.folderId)
                {
                    targets.add(folder);
                }
            }

            if (targets.isEmpty())
            {
                Toast.makeText(this, "没有其他文件夹可移动", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] names = new String[targets.size()];
            for (int i = 0; i < targets.size(); i++)
            {
                names[i] = targets.get(i).name;
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle("移动到")
                    .setItems(names, (dialog, which) ->
                    {
                        Folder target = targets.get(which);

                        accountRepository.move(temp.id, target.id, success ->
                        {
                            if (isFinishing() || isDestroyed())
                            {
                                return;
                            }

                            if (success)
                            {
                                Toast.makeText(this,
                                        "已移动到「" + target.name + "」",
                                        Toast.LENGTH_SHORT).show();
                                loadAccounts();
                            }
                            else
                            {
                                Toast.makeText(this, "移动失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
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
