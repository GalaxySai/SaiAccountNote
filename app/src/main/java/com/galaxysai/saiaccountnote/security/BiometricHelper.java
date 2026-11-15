package com.galaxysai.saiaccountnote.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.biometric.BiometricManager;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;


/**
 * 生物识别(指纹/面容)工具。
 *
 * <p>原理:</p>
 * <ol>
 *     <li>在 Android Keystore 里生成一把 AES 密钥,要求“每次使用前必须通过生物识别”;</li>
 *     <li>用这把密钥把数据库密钥加密后存到 SharedPreferences;</li>
 *     <li>解锁时用 {@code BiometricPrompt} 认证,拿到 Cipher 后解出数据库密钥。</li>
 * </ol>
 *
 * <p>Keystore 设置 {@code setInvalidatedByBiometricEnrollment(true)},
 * 用户新增/删除指纹后,旧密钥会失效,需要重新开启。</p>
 */
public final class BiometricHelper
{
    /** 支持的认证方式:强生物识别 */
    public static final int AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG;

    private static final String KEY_ALIAS = "sai_biometric_key";
    private static final String PREFS_NAME = "biometric";
    private static final String KEY_CIPHERTEXT = "ciphertext";
    private static final String KEY_IV = "iv";

    private BiometricHelper()
    {
        // 工具类禁止实例化
    }

    /**
     * 当前设备是否支持可用的生物识别。
     */
    public static boolean isAvailable(Context context)
    {
        return canAuthenticate(context) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /**
     * 查询生物识别状态,返回值是 BiometricManager.BIOMETRIC_* 常量。
     */
    public static int canAuthenticate(Context context)
    {
        return BiometricManager.from(context).canAuthenticate(AUTHENTICATORS);
    }

    /**
     * 根据设备硬件给出文案:指纹 / 面容 / 两者都有。
     * 注意:具体用哪种仍由系统的 BiometricPrompt 决定。
     */
    public static String getLabel(Context context)
    {
        PackageManager pm = context.getPackageManager();
        boolean hasFingerprint = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);
        // FEATURE_FACE 的常量值,避免高版本 API 的 lint 提示
        boolean hasFace = pm.hasSystemFeature("android.hardware.biometrics.face");

        if (hasFingerprint && hasFace)
        {
            return "指纹 / 面容";
        }
        if (hasFace)
        {
            return "面容";
        }
        if (hasFingerprint)
        {
            return "指纹";
        }
        return "生物识别";
    }

    /**
     * 不可用时的具体原因,用于设置页提示。
     */
    public static String getStatusMessage(Context context)
    {
        switch (canAuthenticate(context))
        {
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                return "当前设备没有指纹或面容硬件";

            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                return "生物识别暂时不可用,请稍后再试";

            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                return "请先在系统设置中录入指纹或面容";

            default:
                return "当前设备不支持生物识别";
        }
    }

    /**
     * 是否已经开启生物识别解锁。
     */
    public static boolean isEnabled(Context context)
    {
        return prefs(context).contains(KEY_CIPHERTEXT);
    }

    /**
     * 关闭并清理:删除 Keystore 密钥和保存的密文。
     */
    public static void disable(Context context)
    {
        prefs(context).edit().clear().apply();

        try
        {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS))
            {
                keyStore.deleteEntry(KEY_ALIAS);
            }
        }
        catch (Exception ignored)
        {
            // 删除失败不影响“关闭”状态
        }
    }

    /**
     * 创建用于加密数据库密钥的 Cipher(需要配合 BiometricPrompt 使用)。
     */
    public static Cipher createEncryptCipher() throws Exception
    {
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher;
    }

    /**
     * 创建用于解密数据库密钥的 Cipher。
     */
    public static Cipher createDecryptCipher(Context context) throws Exception
    {
        SecretKey key = getOrCreateKey();

        byte[] iv = Base64.decode(
                prefs(context).getString(KEY_IV, ""), Base64.NO_WRAP);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return cipher;
    }

    /**
     * 保存加密后的数据库密钥和 IV。
     */
    public static void saveEncrypted(Context context, Cipher cipher, byte[] encrypted)
    {
        prefs(context).edit()
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .apply();
    }

    /**
     * 读取保存的密文。
     */
    public static byte[] getEncrypted(Context context)
    {
        return Base64.decode(
                prefs(context).getString(KEY_CIPHERTEXT, ""), Base64.NO_WRAP);
    }

    /**
     * 获取或创建 Keystore 里的 AES 密钥。
     */
    private static SecretKey getOrCreateKey() throws Exception
    {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS))
        {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // 每次使用密钥前必须通过生物识别
                .setUserAuthenticationRequired(true)
                // 指纹/面容变更后旧密钥失效
                .setInvalidatedByBiometricEnrollment(true)
                .build();

        generator.init(spec);
        generator.generateKey();

        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }

    private static SharedPreferences prefs(Context context)
    {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
