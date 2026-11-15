package com.galaxysai.saiaccountnote.ui.settings;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.galaxysai.saiaccountnote.R;
import com.galaxysai.saiaccountnote.common.SecureWindow;
import com.galaxysai.saiaccountnote.repository.AppExecutors;
import com.galaxysai.saiaccountnote.security.BiometricHelper;
import com.galaxysai.saiaccountnote.security.MasterPasswordManager;
import com.galaxysai.saiaccountnote.security.SessionManager;
import com.galaxysai.saiaccountnote.ui.unlock.UnlockActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import javax.crypto.Cipher;


/**
 * 设置页。
 *
 * <p>当前包含:</p>
 * <ul>
 *     <li>修改主密码(下一版实现);</li>
 *     <li>开启/关闭指纹或面容解锁。</li>
 * </ul>
 */
public class SettingsActivity extends AppCompatActivity
{
    private SwitchMaterial switchBiometric;

    /** 防止代码设置开关状态时触发监听 */
    private boolean updatingSwitch = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        SecureWindow.apply(getWindow());

        if (!SessionManager.isUnlocked())
        {
            startActivity(new Intent(this, UnlockActivity.class));
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        applySystemBarInsets();

        findViewById(R.id.settings_back).setOnClickListener(v -> finish());

        // 修改主密码
        findViewById(R.id.row_change_password).setOnClickListener(v -> showChangePasswordDialog());

        switchBiometric = findViewById(R.id.switch_biometric);

        // 根据设备硬件显示“指纹 / 面容 / 生物识别”,并处理不支持的情况
        TextView tvBiometricTitle = findViewById(R.id.tv_biometric_title);
        TextView tvBiometricSubtitle = findViewById(R.id.tv_biometric_subtitle);

        boolean available = BiometricHelper.isAvailable(this);
        tvBiometricTitle.setText(BiometricHelper.getLabel(this) + "解锁");
        tvBiometricSubtitle.setText(available
                ? "用系统生物识别快速解锁"
                : BiometricHelper.getStatusMessage(this));
        switchBiometric.setEnabled(available);

        // 如果之前开启过生物识别,先检查密钥是否仍然有效(录新指纹后会失效)
        if (BiometricHelper.isEnabled(this))
        {
            try
            {
                BiometricHelper.createDecryptCipher(this);
            }
            catch (Exception e)
            {
                BiometricHelper.disable(this);
            }
        }

        setSwitchSilently(BiometricHelper.isEnabled(this) && BiometricHelper.isAvailable(this));

        switchBiometric.setOnCheckedChangeListener((buttonView, isChecked) ->
        {
            if (updatingSwitch)
            {
                return;
            }

            if (!isChecked)
            {
                BiometricHelper.disable(this);
                Toast.makeText(this, "已关闭指纹/面容解锁", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!BiometricHelper.isAvailable(this))
            {
                setSwitchSilently(false);
                Toast.makeText(this, "当前设备不支持生物识别", Toast.LENGTH_SHORT).show();
                return;
            }

            enableBiometric();
        });
    }

    /**
     * 用生物识别确认后,把当前数据库密钥用 Keystore 加密保存。
     */
    private void enableBiometric()
    {
        byte[] dbKey = SessionManager.getDbKey();
        if (dbKey == null)
        {
            setSwitchSilently(false);
            return;
        }

        try
        {
            Cipher cipher = BiometricHelper.createEncryptCipher();

            BiometricPrompt prompt = new BiometricPrompt(
                    this,
                    ContextCompat.getMainExecutor(this),
                    new BiometricPrompt.AuthenticationCallback()
                    {
                        @Override
                        public void onAuthenticationSucceeded(
                                @NonNull BiometricPrompt.AuthenticationResult result)
                        {
                            try
                            {
                                Cipher resultCipher =
                                        result.getCryptoObject() == null
                                                ? null
                                                : result.getCryptoObject().getCipher();

                                if (resultCipher == null)
                                {
                                    setSwitchSilently(false);
                                    return;
                                }

                                byte[] encrypted = resultCipher.doFinal(dbKey);
                                BiometricHelper.saveEncrypted(
                                        SettingsActivity.this, resultCipher, encrypted);

                                Toast.makeText(SettingsActivity.this,
                                        "已开启指纹/面容解锁", Toast.LENGTH_SHORT).show();
                            }
                            catch (Exception e)
                            {
                                setSwitchSilently(false);
                                Toast.makeText(SettingsActivity.this,
                                        "开启失败,请重试", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onAuthenticationError(int errorCode,
                                                          @NonNull CharSequence errString)
                        {
                            setSwitchSilently(false);
                        }

                        @Override
                        public void onAuthenticationFailed()
                        {
                            // 验证失败,BiometricPrompt 会继续等待用户重试
                        }
                    });

            BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("开启指纹/面容解锁")
                    .setSubtitle("验证后即可用生物识别解锁密码本")
                    .setAllowedAuthenticators(BiometricHelper.AUTHENTICATORS)
                    .setNegativeButtonText("取消")
                    .build();

            prompt.authenticate(info, new BiometricPrompt.CryptoObject(cipher));
        }
        catch (Exception e)
        {
            setSwitchSilently(false);
            Toast.makeText(this, "当前设备无法使用生物识别", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 代码设置开关状态,但不触发监听。
     */
    private void setSwitchSilently(boolean checked)
    {
        if (switchBiometric == null)
        {
            return;
        }

        updatingSwitch = true;
        switchBiometric.setChecked(checked);
        updatingSwitch = false;
    }

    /**
     * 修改主密码对话框。
     *
     * <p>数据库密钥保持不变,只重新用新密码包裹,所以不需要重建数据库,
     * 生物识别保存的密钥也继续有效。</p>
     */
    private void showChangePasswordDialog()
    {
        View content = getLayoutInflater().inflate(R.layout.dialog_change_password, null);

        EditText etOld = content.findViewById(R.id.et_old_password);
        EditText etNew = content.findViewById(R.id.et_new_password);
        EditText etConfirm = content.findViewById(R.id.et_confirm_password);
        TextView tvError = content.findViewById(R.id.tv_pwd_error);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("修改主密码")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                {
                    String oldPwd = textOf(etOld);
                    String newPwd = textOf(etNew);
                    String confirm = textOf(etConfirm);

                    tvError.setVisibility(View.GONE);

                    if (oldPwd.isEmpty())
                    {
                        showPwdError(tvError, "请输入原主密码");
                        return;
                    }
                    if (newPwd.length() < 6)
                    {
                        showPwdError(tvError, "新主密码至少 6 位");
                        return;
                    }
                    if (!newPwd.equals(confirm))
                    {
                        showPwdError(tvError, "两次输入的新密码不一致");
                        return;
                    }
                    if (newPwd.equals(oldPwd))
                    {
                        showPwdError(tvError, "新密码不能与原密码相同");
                        return;
                    }

                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

                    // PBKDF2 较慢,放到后台线程
                    AppExecutors.get().diskIO(() ->
                    {
                        boolean success = MasterPasswordManager.changePassword(
                                SettingsActivity.this, oldPwd, newPwd);

                        AppExecutors.get().main(() ->
                        {
                            if (isFinishing() || isDestroyed())
                            {
                                return;
                            }

                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);

                            if (success)
                            {
                                dialog.dismiss();
                                Toast.makeText(SettingsActivity.this,
                                        "主密码已修改", Toast.LENGTH_SHORT).show();
                            }
                            else
                            {
                                showPwdError(tvError, "原主密码错误");
                            }
                        });
                    });
                }));

        dialog.show();
    }

    private void showPwdError(TextView view, String message)
    {
        view.setText(message);
        view.setVisibility(View.VISIBLE);
    }

    private String textOf(EditText editText)
    {
        return editText.getText() == null ? "" : editText.getText().toString();
    }

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

    private void applySystemBarInsets()
    {
        View root = findViewById(R.id.root_settings);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) ->
        {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        boolean isNight = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        controller.setAppearanceLightStatusBars(!isNight);
        controller.setAppearanceLightNavigationBars(!isNight);
    }
}
