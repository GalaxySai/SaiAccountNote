package com.galaxysai.saiaccountnote.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;


/**
 * 主密码管理(密钥包裹版)。
 *
 * <p>存储内容:</p>
 * <ul>
 *     <li>salt:随机盐;</li>
 *     <li>verifier:SHA-256(数据库密钥),用来校验密码;</li>
 *     <li>wrappedKey:数据库密钥用“密码派生的 KEK”加密后的密文;</li>
 *     <li>algorithm:PBKDF2 算法名。</li>
 * </ul>
 *
 * <p>这样改密码只需要用新密码重新包裹同一个数据库密钥,
 * <b>不用重新加密整个数据库</b>,生物识别保存的密钥也继续有效。</p>
 */
public final class MasterPasswordManager
{
    private static final String PREFS_NAME = "vault";

    private static final String KEY_SALT = "salt";
    private static final String KEY_VERIFIER = "verifier";
    private static final String KEY_WRAPPED = "wrapped_key";
    private static final String KEY_ALGORITHM = "algorithm";
    private static final String KEY_INITIALIZED = "initialized";

    private static final int ITERATIONS = 150_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final int DB_KEY_LENGTH = 32;

    private MasterPasswordManager()
    {
        // 工具类禁止实例化
    }

    public static boolean isInitialized(Context context)
    {
        return prefs(context).getBoolean(KEY_INITIALIZED, false);
    }

    /**
     * 首次设置主密码:生成随机数据库密钥,用密码派生的 KEK 包裹后保存。
     *
     * @return 数据库密钥;失败返回 null
     */
    public static byte[] createMasterPassword(Context context, String password)
    {
        try
        {
            byte[] salt = randomBytes(SALT_LENGTH);
            String algorithm = chooseAlgorithm();
            byte[] kek = derive(password, salt, algorithm);

            byte[] dbKey = randomBytes(DB_KEY_LENGTH);
            byte[] verifier = sha256(dbKey);
            byte[] wrapped = CryptoUtils.encrypt(dbKey, kek);

            save(context, salt, verifier, wrapped, algorithm);
            return dbKey;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 校验主密码并返回数据库密钥。
     *
     * @return 正确时返回数据库密钥;错误返回 null
     */
    public static byte[] verify(Context context, String password)
    {
        try
        {
            SharedPreferences prefs = prefs(context);

            byte[] salt = Base64.decode(prefs.getString(KEY_SALT, ""), Base64.NO_WRAP);
            byte[] expected = Base64.decode(prefs.getString(KEY_VERIFIER, ""), Base64.NO_WRAP);
            String algorithm = prefs.getString(KEY_ALGORITHM, "PBKDF2WithHmacSHA256");
            String wrappedText = prefs.getString(KEY_WRAPPED, null);

            byte[] kek = derive(password, salt, algorithm);

            byte[] dbKey;

            if (wrappedText != null && !wrappedText.isEmpty())
            {
                // 新格式:解出数据库密钥
                byte[] wrapped = Base64.decode(wrappedText, Base64.NO_WRAP);
                dbKey = CryptoUtils.decrypt(wrapped, kek);
            }
            else
            {
                // 兼容旧格式:数据库密钥就是密码派生的密钥
                dbKey = kek;
            }

            byte[] actual = sha256(dbKey);
            if (!MessageDigest.isEqual(expected, actual))
            {
                return null;
            }

            // 旧格式顺手升级成“密钥包裹”格式
            if (wrappedText == null || wrappedText.isEmpty())
            {
                byte[] wrapped = CryptoUtils.encrypt(dbKey, kek);
                prefs.edit()
                        .putString(KEY_WRAPPED, Base64.encodeToString(wrapped, Base64.NO_WRAP))
                        .apply();
            }

            return dbKey;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 修改主密码:数据库密钥不变,只重新包裹。
     *
     * @return true 表示成功;false 表示原密码错误或写入失败
     */
    public static boolean changePassword(Context context, String oldPassword, String newPassword)
    {
        byte[] dbKey = verify(context, oldPassword);
        if (dbKey == null)
        {
            return false;
        }

        try
        {
            byte[] salt = randomBytes(SALT_LENGTH);
            String algorithm = chooseAlgorithm();
            byte[] kek = derive(newPassword, salt, algorithm);

            byte[] verifier = sha256(dbKey);
            byte[] wrapped = CryptoUtils.encrypt(dbKey, kek);

            save(context, salt, verifier, wrapped, algorithm);
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private static void save(Context context, byte[] salt, byte[] verifier,
                             byte[] wrapped, String algorithm)
    {
        prefs(context).edit()
                .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_VERIFIER, Base64.encodeToString(verifier, Base64.NO_WRAP))
                .putString(KEY_WRAPPED, Base64.encodeToString(wrapped, Base64.NO_WRAP))
                .putString(KEY_ALGORITHM, algorithm)
                .putBoolean(KEY_INITIALIZED, true)
                .apply();
    }

    private static String chooseAlgorithm()
    {
        try
        {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return "PBKDF2WithHmacSHA256";
        }
        catch (Exception e)
        {
            return "PBKDF2WithHmacSHA1";
        }
    }

    private static byte[] derive(String password, byte[] salt, String algorithm) throws Exception
    {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(algorithm);
        SecretKey key = factory.generateSecret(spec);
        return key.getEncoded();
    }

    private static byte[] sha256(byte[] data) throws Exception
    {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private static byte[] randomBytes(int length)
    {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private static SharedPreferences prefs(Context context)
    {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
