package com.galaxysai.saiaccountnote.security;

import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;


/**
 * 简单的 AES-GCM 加解密工具。
 *
 * <p>格式:密文 = [12 字节 IV] + [加密数据 + 16 字节认证标签]。</p>
 * <p>用于“用密码派生的 KEK 包裹数据库密钥”。</p>
 */
public final class CryptoUtils
{
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private CryptoUtils()
    {
        // 工具类禁止实例化
    }

    /**
     * 加密。
     *
     * @param plaintext 明文
     * @param key       16/24/32 字节的 AES 密钥
     * @return IV + 密文
     */
    public static byte[] encrypt(byte[] plaintext, byte[] key) throws Exception
    {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BITS, iv));

        byte[] ciphertext = cipher.doFinal(plaintext);

        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

        return result;
    }

    /**
     * 解密。
     *
     * @param data IV + 密文
     * @param key  AES 密钥
     * @return 明文;认证失败会抛异常
     */
    public static byte[] decrypt(byte[] data, byte[] key) throws Exception
    {
        if (data == null || data.length <= IV_LENGTH)
        {
            throw new IllegalArgumentException("密文长度不合法");
        }

        byte[] iv = Arrays.copyOfRange(data, 0, IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(data, IV_LENGTH, data.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BITS, iv));

        return cipher.doFinal(ciphertext);
    }
}
