package com.galaxysai.saiaccountnote.ui.home;

import com.galaxysai.saiaccountnote.App;
import com.galaxysai.saiaccountnote.R;
import com.galaxysai.saiaccountnote.backup.BackupManager;
import com.galaxysai.saiaccountnote.common.SecureWindow;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.galaxysai.saiaccountnote.data.entity.Folder;
import com.galaxysai.saiaccountnote.repository.AppExecutors;
import com.galaxysai.saiaccountnote.repository.FolderRepository;
import com.galaxysai.saiaccountnote.security.SessionManager;
import com.galaxysai.saiaccountnote.ui.account.UserActivity;
import com.galaxysai.saiaccountnote.ui.settings.SettingsActivity;
import com.galaxysai.saiaccountnote.ui.unlock.UnlockActivity;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;


/**
 * 首页:文件夹列表。
 *
 * <p>布局对应 {@code res/layout/home_activity.xml}(预览版转正)。</p>
 * <p>状态栏处理:根布局通过 WindowInsets 自动避开系统栏,
 * 不再需要用绿色 Toolbar 去“盖住”状态栏。</p>
 */
public class HomeActivity extends AppCompatActivity implements PopupMenu.OnMenuItemClickListener
{
    /** 当前展示的文件夹列表(过滤后的) */
    private List<Folder> folders = new ArrayList<>();

    /** 完整文件夹列表(过滤前的原始数据) */
    private List<Folder> allFolders = new ArrayList<>();

    /** 搜索框 */
    private EditText etSearch;

    /** 文件夹行的容器 */
    private LinearLayout container;

    /** 空状态提示 */
    private TextView tvEmpty;

    /** 点击某个文件夹的菜单时,先记住它的名字 */
    private String tempTableName;

    /** 文件夹数据入口 */
    private FolderRepository folderRepository;

    /** 侧边栏根布局 */
    private DrawerLayout drawerLayout;

    /** 侧边栏菜单 */
    private NavigationView navView;

    /** 导出流程中暂存的备份密码 */
    private String pendingBackupPassword;

    /** 导出流程中暂存的加密选项 */
    private boolean pendingBackupEncrypt;

    /** 选择“导出到哪里” */
    private final ActivityResultLauncher<String> createBackupLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("application/json"),
                    this::onBackupFileChosen);

    /** 选择“从哪个文件导入” */
    private final ActivityResultLauncher<String[]> openBackupLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    this::onBackupFileSelected);

    /** 主题切换会重建 Activity,用这个标记让重建后自动重新打开侧边栏 */
    private static boolean reopenDrawerAfterThemeChange = false;

    public void setTempTableName(String tableName)
    {
        this.tempTableName = tableName;
    }

    public String getTempTableName()
    {
        return tempTableName;
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
        setContentView(R.layout.home_activity);

        // 先处理系统栏,再绑定其它控件
        applySystemBarInsets();

        container = findViewById(R.id.home_table_container);
        tvEmpty = findViewById(R.id.tv_empty);

        // 侧边栏
        drawerLayout = findViewById(R.id.drawer_root);
        navView = findViewById(R.id.nav_view);
        navView.setNavigationItemSelectedListener(this::onDrawerItemSelected);
        findViewById(R.id.home_menu).setOnClickListener(v ->
                drawerLayout.openDrawer(GravityCompat.START));
        applyNavInsets(navView);
        updateThemeMenu(navView);

        // 如果是切换主题导致的重建,重建完成后立即打开(不要滑入动画)
        if (reopenDrawerAfterThemeChange)
        {
            reopenDrawerAfterThemeChange = false;
            drawerLayout.post(() -> drawerLayout.openDrawer(navView, false));
        }

        // 右上角新建文件夹
        findViewById(R.id.home_add).setOnClickListener(v -> showNewFolderDialog());

        // 搜索框:输入即过滤文件夹
        etSearch = findViewById(R.id.et_home_search);
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
                applyFolderFilter();
            }
        });

        folderRepository = new FolderRepository(this);
        loadTables();
    }

    /**
     * 让根布局避开状态栏和导航栏,并设置系统栏图标的明暗。
     *
     * <p>为什么需要这个?targetSdk 35 后 Android 15 强制边到边,
     * 内容默认会画到状态栏下面。给它加上系统栏高度的 padding 就不会被盖住。</p>
     */
    private void applySystemBarInsets()
    {
        View root = findViewById(R.id.root_home);

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
     * 让侧边栏避开状态栏和导航栏。
     */
    private void applyNavInsets(NavigationView navView)
    {
        ViewCompat.setOnApplyWindowInsetsListener(navView, (v, insets) ->
        {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
    }

    /**
     * 根据当前主题状态,勾选/取消“深色模式”菜单项。
     */
    private void updateThemeMenu(NavigationView navView)
    {
        boolean isNight = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        MenuItem themeItem = navView.getMenu().findItem(R.id.drawer_theme);
        if (themeItem != null)
        {
            themeItem.setChecked(isNight);
            // 当前是深色 → 菜单显示“浅色模式”;当前是浅色 → 显示“深色模式”
            themeItem.setTitle(isNight ? "浅色模式" : "深色模式");
        }
    }

    /**
     * 侧边栏菜单点击。
     */
    private boolean onDrawerItemSelected(MenuItem item)
    {
        int id = item.getItemId();

        if (id == R.id.drawer_theme)
        {
            // 不主动关抽屉;重建后用无动画方式重新打开,避免滑动/闪烁
            reopenDrawerAfterThemeChange = true;
            toggleNightMode();
            return true;
        }
        if (id == R.id.drawer_export)
        {
            drawerLayout.closeDrawers();
            showExportDialog();
            return true;
        }
        if (id == R.id.drawer_import)
        {
            drawerLayout.closeDrawers();
            openBackupLauncher.launch(new String[]{
                    "application/json", "application/octet-stream", "text/plain", "*/*"});
            return true;
        }
        if (id == R.id.drawer_settings)
        {
            startActivity(new Intent(this, SettingsActivity.class));
            drawerLayout.closeDrawers();
            return true;
        }
        if (id == R.id.drawer_about)
        {
            drawerLayout.closeDrawers();
            showAbout();
            return true;
        }

        return false;
    }

    /**
     * 切换深浅色,并把选择写进 SharedPreferences。
     * 设置后 Activity 会自动重建,颜色由 values / values-night 自动切换。
     */
    private void toggleNightMode()
    {
        boolean isNight = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        int newMode = isNight
                ? AppCompatDelegate.MODE_NIGHT_NO
                : AppCompatDelegate.MODE_NIGHT_YES;

        SharedPreferences prefs = getSharedPreferences(App.PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putInt(App.KEY_NIGHT_MODE, newMode).apply();

        AppCompatDelegate.setDefaultNightMode(newMode);
    }

    /**
     * 导出备份:先选是否加密、设置密码,再让用户选择保存位置。
     */
    private void showExportDialog()
    {
        View content = getLayoutInflater().inflate(R.layout.dialog_backup_export, null);

        MaterialCheckBox cbEncrypt = content.findViewById(R.id.cb_encrypt);
        View layoutPassword = content.findViewById(R.id.layout_backup_password);
        EditText etPassword = content.findViewById(R.id.et_backup_password);
        EditText etConfirm = content.findViewById(R.id.et_backup_password_confirm);
        TextView tvWarning = content.findViewById(R.id.tv_export_warning);
        TextView tvError = content.findViewById(R.id.tv_export_error);

        // 默认勾选加密,并同步密码区域的显示状态
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
            copyToClipboard(password);
            Toast.makeText(this, "已生成随机密码并复制到剪贴板", Toast.LENGTH_LONG).show();
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("导出备份")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("选择保存位置", null)
                .create();

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                {
                    boolean encrypt = cbEncrypt.isChecked();
                    String password = textOf(etPassword);
                    String confirm = textOf(etConfirm);

                    tvError.setVisibility(View.GONE);

                    // 没勾选加密却填了密码 → 明确提示,避免“密码被忽略”
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

                    pendingBackupEncrypt = encrypt;
                    pendingBackupPassword = password;
                    dialog.dismiss();

                    createBackupLauncher.launch(
                            "sai-backup-" + timestampForFileName() + ".json");
                }));

        dialog.show();
    }

    /**
     * 用户选好保存位置后,在后台把备份写进去。
     */
    private void onBackupFileChosen(Uri uri)
    {
        if (uri == null)
        {
            return;
        }

        final boolean encrypt = pendingBackupEncrypt;
        final String password = pendingBackupPassword;
        pendingBackupPassword = null;

        AppExecutors.get().diskIO(() ->
        {
            String error = null;

            try (OutputStream out = getContentResolver().openOutputStream(uri))
            {
                if (out == null)
                {
                    throw new IllegalStateException("无法写入文件");
                }

                byte[] data = BackupManager.exportData(this, encrypt, password);
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
                if (finalError == null)
                {
                    Toast.makeText(this, "导出成功", Toast.LENGTH_LONG).show();
                }
                else
                {
                    Toast.makeText(this, "导出失败: " + finalError, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /**
     * 用户选好备份文件后:判断是否加密,决定要不要先输密码。
     */
    private void onBackupFileSelected(Uri uri)
    {
        if (uri == null)
        {
            return;
        }

        AppExecutors.get().diskIO(() ->
        {
            String json = null;
            String error = null;

            try (InputStream in = getContentResolver().openInputStream(uri))
            {
                if (in == null)
                {
                    throw new IllegalStateException("无法读取文件");
                }

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = in.read(chunk)) != -1)
                {
                    buffer.write(chunk, 0, read);
                }

                json = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            }
            catch (Exception e)
            {
                error = e.getMessage();
            }

            final String content = json;
            final String finalError = error;

            AppExecutors.get().main(() ->
            {
                if (finalError != null)
                {
                    Toast.makeText(this, "读取失败: " + finalError, Toast.LENGTH_LONG).show();
                    return;
                }

                if (BackupManager.isEncrypted(content))
                {
                    showImportPasswordDialog(content);
                }
                else
                {
                    importBackup(content, null, null, null);
                }
            });
        });
    }

    /**
     * 导入加密备份时,先让用户输入备份密码。
     */
    private void showImportPasswordDialog(String json)
    {
        View content = getLayoutInflater().inflate(R.layout.dialog_backup_import_password, null);

        EditText etPassword = content.findViewById(R.id.et_import_password);
        TextView tvError = content.findViewById(R.id.tv_import_error);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("导入备份")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("解密导入", null)
                .create();

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                {
                    String password = textOf(etPassword);
                    tvError.setVisibility(View.GONE);

                    if (password.isEmpty())
                    {
                        showDialogError(tvError, "请输入备份密码");
                        return;
                    }

                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    importBackup(json, password, tvError, dialog);
                }));

        dialog.show();
    }

    /**
     * 后台导入备份,完成后刷新列表。
     */
    private void importBackup(String json, String password, TextView errorView, AlertDialog dialog)
    {
        AppExecutors.get().diskIO(() ->
        {
            BackupManager.ImportResult result = null;
            String error = null;

            try
            {
                result = BackupManager.importData(this, json, password);
            }
            catch (Exception e)
            {
                error = e.getMessage();
            }

            final BackupManager.ImportResult finalResult = result;
            final String finalError = error;

            AppExecutors.get().main(() ->
            {
                if (finalError != null)
                {
                    if (errorView != null)
                    {
                        showDialogError(errorView, "导入失败: " + finalError);
                    }
                    else
                    {
                        Toast.makeText(this, "导入失败: " + finalError, Toast.LENGTH_LONG).show();
                    }

                    if (dialog != null)
                    {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    }
                    return;
                }

                if (dialog != null)
                {
                    dialog.dismiss();
                }

                Toast.makeText(this,
                        "导入完成:" + finalResult.folders + " 个文件夹、"
                                + finalResult.accounts + " 个账号",
                        Toast.LENGTH_LONG).show();

                loadTables();
            });
        });
    }

    /**
     * 生成 20 位随机强密码。
     */
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

    /**
     * 复制到剪贴板,并在 Android 13+ 标记为敏感内容。
     */
    private void copyToClipboard(String text)
    {
        ClipboardManager manager =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null)
        {
            return;
        }

        ClipData clip = ClipData.newPlainText("backup_password", text);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
        }

        manager.setPrimaryClip(clip);
    }

    private String timestampForFileName()
    {
        return new SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(new Date());
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
     * 关于对话框。
     */
    private void showAbout()
    {
        // 从包信息里读版本号,避免依赖 BuildConfig(AGP 8 里可能未生成)
        String version = "1.0";
        try
        {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        }
        catch (Exception ignored)
        {
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("关于")
                .setMessage("赛的密码本 v" + version
                        + "\n\n本地存储,数据仅保存在这台设备。")
                .setPositiveButton("知道了", null)
                .show();
    }

    /**
     * 从 Room 读取文件夹列表。
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

    private void loadTables()
    {
        folderRepository.getAll(list ->
        {
            allFolders.clear();
            allFolders.addAll(list);
            applyFolderFilter();
        });
    }

    /**
     * 按搜索框内容过滤文件夹,再渲染。
     */
    private void applyFolderFilter()
    {
        String query = etSearch == null ? "" : etSearch.getText().toString().trim();
        String lower = query.toLowerCase(Locale.ROOT);

        folders.clear();
        for (Folder folder : allFolders)
        {
            String name = folder.name == null ? "" : folder.name;
            if (lower.isEmpty() || name.toLowerCase(Locale.ROOT).contains(lower))
            {
                folders.add(folder);
            }
        }

        showTables();
    }

    /**
     * 把文件夹渲染成一行行列表项。
     */
    private void showTables()
    {
        container.removeAllViews();

        // 没有数据时显示空状态(区分“真没有”和“搜索无结果”)
        tvEmpty.setVisibility(folders.isEmpty() ? View.VISIBLE : View.GONE);
        String query = etSearch == null ? "" : etSearch.getText().toString().trim();
        tvEmpty.setText(query.isEmpty()
                ? "还没有文件夹,点右上角 + 新建一个"
                : "没有找到匹配的文件夹");

        for (Folder folder : folders)
        {
            View row = LayoutInflater.from(this).inflate(R.layout.item_folder, container, false);

            TextView name = row.findViewById(R.id.tv_folder_name);
            ImageButton menu = row.findViewById(R.id.iv_folder_menu);

            name.setText(folder.name);

            // 整行点击 → 进入账号列表
            row.setOnClickListener(v ->
            {
                Intent intent = new Intent(this, UserActivity.class);
                intent.putExtra("folderId", folder.id);
                intent.putExtra("folderName", folder.name);
                startActivity(intent);
            });

            // 右侧菜单 → 重命名 / 删除
            menu.setOnClickListener(v ->
            {
                setTempTableName(folder.name);
                showPopupMenu(menu);
            });

            container.addView(row);
        }
    }

    /**
     * 刷新文件夹列表。
     */
    public void refresh()
    {
        loadTables();
    }

    /**
     * 显示文件夹的操作菜单。
     */
    private void showPopupMenu(View view)
    {
        PopupMenu popupMenu = new PopupMenu(this, view);
        popupMenu.getMenuInflater().inflate(R.menu.home_table_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(this);
        popupMenu.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item)
    {
        int itemId = item.getItemId();
        if (itemId == R.id.tableMenuExport)
        {
            Toast.makeText(getApplicationContext(), "点击了导出", Toast.LENGTH_SHORT).show();
            return true;
        }
        else if (itemId == R.id.tableMenuRename)
        {
            showRenameDialog();
            return true;
        }
        else if (itemId == R.id.tableMenuDelete)
        {
            showWarningDialog();
            return true;
        }
        return false;
    }

    /**
     * 弹出“新建文件夹”对话框。
     */
    private void showNewFolderDialog()
    {
        NewFolderBottomSheet sheet = new NewFolderBottomSheet();
        sheet.show(getSupportFragmentManager(), "新建文件夹");
    }

    /**
     * 弹出“重命名文件夹”对话框。
     */
    public void showRenameDialog()
    {
        RenameFolderBottomSheet sheet = RenameFolderBottomSheet.newInstance(getTempTableName());
        sheet.show(getSupportFragmentManager(), "重命名文件夹");
    }

    /**
     * 弹出“删除文件夹”确认框。
     */
    private void showWarningDialog()
    {
        WarningDialogFragmentOnDropTable warningDialog =
                WarningDialogFragmentOnDropTable.newInstance(getTempTableName());
        warningDialog.show(getSupportFragmentManager(), "删除文件夹警告");
    }
}
