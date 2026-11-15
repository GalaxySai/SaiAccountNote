package com.galaxysai.saiaccountnote.ui.unlock;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.galaxysai.saiaccountnote.data.DatabaseMigration;
import com.galaxysai.saiaccountnote.security.BiometricHelper;
import com.galaxysai.saiaccountnote.security.MasterPasswordManager;
import com.galaxysai.saiaccountnote.security.SessionManager;
import com.galaxysai.saiaccountnote.ui.home.HomeActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import javax.crypto.Cipher;


/**
 * 解锁 / 首次设置主密码页。
 *
 * <p>流程:</p>
 * <ul>
 *     <li>没设置过主密码 → 显示“设置主密码 + 确认”;</li>
 *     <li>设置过 → 显示“输入主密码”;</li>
 *     <li>校验通过后写入 {@link SessionManager},再进入首页。</li>
 * </ul>
 */
public class UnlockActivity extends AppCompatActivity
{
    /** true = 首次设置模式 */
    private boolean setupMode;

    private TextInputLayout tilConfirm;
    private EditText etPassword;
    private EditText etConfirm;
    private TextView tvTitle;
    private TextView tvSubtitle;
    private TextView tvError;
    private MaterialButton btnUnlock;
    private MaterialButton btnBiometric;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // 解锁页同样禁止截屏
        SecureWindow.apply(getWindow());
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_unlock);

        applySystemBarInsets();

        setupMode = !MasterPasswordManager.isInitialized(this);

        tilConfirm = findViewById(R.id.til_confirm);
        etPassword = findViewById(R.id.et_password);
        etConfirm = findViewById(R.id.et_confirm);
        tvTitle = findViewById(R.id.tv_unlock_title);
        tvSubtitle = findViewById(R.id.tv_unlock_subtitle);
        tvError = findViewById(R.id.tv_error);
        btnUnlock = findViewById(R.id.btn_unlock);
        btnBiometric = findViewById(R.id.btn_biometric);

        // 已开启生物识别且设备可用时,显示指纹/面容按钮
        if (!setupMode && BiometricHelper.isEnabled(this) && BiometricHelper.isAvailable(this))
        {
            btnBiometric.setText("使用" + BiometricHelper.getLabel(this) + "解锁");
            btnBiometric.setVisibility(View.VISIBLE);
            btnBiometric.setOnClickListener(v -> unlockWithBiometric());
        }

        if (setupMode)
        {
            tvTitle.setText("设置主密码");
            tvSubtitle.setText("密码只保存在本机,忘记后无法找回");
            tilConfirm.setVisibility(View.VISIBLE);
            btnUnlock.setText("创建");
        }
        else
        {
            tvTitle.setText("解锁密码本");
            tvSubtitle.setText("输入主密码继续");
            tilConfirm.setVisibility(View.GONE);
            btnUnlock.setText("解锁");
        }

        btnUnlock.setOnClickListener(v -> submit());
    }

    /**
     * 处理“创建 / 解锁”按钮。
     */
    private void submit()
    {
        String password = textOf(etPassword);

        if (password.isEmpty())
        {
            showError("请输入主密码");
            return;
        }

        if (setupMode)
        {
            if (password.length() < 6)
            {
                showError("主密码至少 6 位");
                return;
            }

            String confirm = textOf(etConfirm);
            if (!password.equals(confirm))
            {
                showError("两次输入的密码不一致");
                return;
            }

            byte[] key = MasterPasswordManager.createMasterPassword(this, password);
            if (key == null)
            {
                showError("创建失败,请重试");
                return;
            }

            SessionManager.unlock(key);
            migrateThenHome();
        }
        else
        {
            byte[] key = MasterPasswordManager.verify(this, password);
            if (key == null)
            {
                showError("密码错误");
                return;
            }

            SessionManager.unlock(key);
            migrateThenHome();
        }
    }

    /**
     * 用指纹/面容解锁:解出数据库密钥后直接进入。
     */
    private void unlockWithBiometric()
    {
        try
        {
            Cipher cipher = BiometricHelper.createDecryptCipher(this);

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
                                    showError("解锁失败,请用主密码");
                                    return;
                                }

                                byte[] key = resultCipher.doFinal(
                                        BiometricHelper.getEncrypted(UnlockActivity.this));

                                SessionManager.unlock(key);
                                migrateThenHome();
                            }
                            catch (Exception e)
                            {
                                showError("解锁失败,请用主密码");
                            }
                        }

                        @Override
                        public void onAuthenticationError(int errorCode,
                                                          @NonNull CharSequence errString)
                        {
                            // 用户主动取消不算错误
                            if (errorCode != BiometricPrompt.ERROR_CANCELED
                                    && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON)
                            {
                                showError(errString.toString());
                            }
                        }

                        @Override
                        public void onAuthenticationFailed()
                        {
                            // 验证失败,系统会继续等待重试
                        }
                    });

            BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("解锁密码本")
                    .setSubtitle("验证指纹或面容")
                    .setAllowedAuthenticators(BiometricHelper.AUTHENTICATORS)
                    .setNegativeButtonText("使用主密码")
                    .build();

            prompt.authenticate(info, new BiometricPrompt.CryptoObject(cipher));
        }
        catch (Exception e)
        {
            // 典型原因:用户重新录入了指纹,旧密钥失效
            BiometricHelper.disable(this);
            btnBiometric.setVisibility(View.GONE);
            showError("指纹信息已失效,请用主密码解锁");
        }
    }

    /**
     * 解锁后先做一次“明文库 → 加密库”迁移,完成后再进首页。
     */
    private void migrateThenHome()
    {
        btnUnlock.setEnabled(false);
        btnUnlock.setText("正在准备…");

        DatabaseMigration.migrateIfNeeded(this, () ->
        {
            if (isFinishing() || isDestroyed())
            {
                return;
            }
            goHome();
        });
    }

    /**
     * 进入首页,并清空当前任务栈。
     */
    private void goHome()
    {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showError(String message)
    {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private String textOf(EditText editText)
    {
        if (editText == null || editText.getText() == null)
        {
            return "";
        }
        return editText.getText().toString();
    }

    /**
     * 避开状态栏/导航栏,并设置系统栏图标明暗。
     */
    private void applySystemBarInsets()
    {
        View root = findViewById(R.id.root_unlock);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) ->
        {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), bars.bottom);
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
