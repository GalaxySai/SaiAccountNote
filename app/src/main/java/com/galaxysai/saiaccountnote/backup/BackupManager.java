package com.galaxysai.saiaccountnote.backup;

import android.content.Context;
import android.util.Base64;

import com.galaxysai.saiaccountnote.data.AppDatabase;
import com.galaxysai.saiaccountnote.data.entity.Account;
import com.galaxysai.saiaccountnote.data.entity.Folder;
import com.galaxysai.saiaccountnote.security.CryptoUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;


/**
 * 备份导出 / 导入。
 *
 * <p>文件格式:外层 JSON 信封。</p>
 * <ul>
 *     <li>加密模式:data 是 Base64 密文,内含文件夹和账号;</li>
 *     <li>明文模式:data 直接是 JSON 对象,方便迁移到其它工具。</li>
 * </ul>
 *
 * <p>导入为“追加”:不删除现有数据;文件夹重名会自动加 -1、-2;
 * 账号没有唯一约束,原样追加。</p>
 */
public final class BackupManager
{
    public static final String FORMAT = "sai-account-note-backup";
    public static final int VERSION = 1;

    private static final int ITERATIONS = 150_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;

    private BackupManager()
    {
        // 工具类禁止实例化
    }

    /**
     * 生成备份文件内容。
     *
     * @param encrypt  是否加密
     * @param password 加密密码(encrypt=false 时忽略)
     */
    public static byte[] exportData(Context context, boolean encrypt, String password) throws Exception
    {
        return exportData(context, encrypt, password, null);
    }

    /**
     * 生成指定范围的备份文件内容。
     *
     * @param encrypt  是否加密
     * @param password 加密密码(encrypt=false 时忽略)
     * @param folderId 只导出该文件夹;传 null 表示导出全部
     */
    public static byte[] exportData(Context context, boolean encrypt, String password,
                                    Long folderId) throws Exception
    {
        return exportData(context, encrypt, password, folderId, null);
    }

    /**
     * 生成指定范围的备份文件内容。
     *
     * @param encrypt    是否加密
     * @param password   加密密码(encrypt=false 时忽略)
     * @param folderId   只导出该文件夹;传 null 表示不按文件夹限制
     * @param accountIds 只导出这些账号;传 null 表示不按账号限制
     */
    public static byte[] exportData(Context context, boolean encrypt, String password,
                                    Long folderId, List<Long> accountIds) throws Exception
    {
        JSONObject payload = buildPayload(context, folderId, accountIds);

        JSONObject envelope = new JSONObject();
        envelope.put("format", FORMAT);
        envelope.put("version", VERSION);
        envelope.put("encrypted", encrypt);
        envelope.put("exportedAt", System.currentTimeMillis());

        if (encrypt)
        {
            byte[] salt = randomBytes(SALT_LENGTH);
            String algorithm = chooseAlgorithm();
            byte[] key = derive(password, salt, algorithm);

            byte[] cipher = CryptoUtils.encrypt(
                    payload.toString().getBytes(StandardCharsets.UTF_8), key);

            JSONObject kdf = new JSONObject();
            kdf.put("algorithm", algorithm);
            kdf.put("iterations", ITERATIONS);
            kdf.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP));

            envelope.put("cipher", "AES-GCM");
            envelope.put("kdf", kdf);
            envelope.put("data", Base64.encodeToString(cipher, Base64.NO_WRAP));
        }
        else
        {
            envelope.put("data", payload);
        }

        return envelope.toString(2).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 判断备份内容是否加密。
     */
    public static boolean isEncrypted(String json)
    {
        try
        {
            return new JSONObject(json).optBoolean("encrypted", false);
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /**
     * 导入备份(追加模式)。
     *
     * @param password 加密备份的密码;明文备份忽略
     */
    public static ImportResult importData(Context context, String json, String password) throws Exception
    {
        JSONObject envelope = new JSONObject(json);

        if (!FORMAT.equals(envelope.optString("format")))
        {
            throw new Exception("不是本 App 的备份文件");
        }

        boolean encrypted = envelope.optBoolean("encrypted", false);
        JSONObject payload;

        if (encrypted)
        {
            JSONObject kdf = envelope.optJSONObject("kdf");
            if (kdf == null)
            {
                throw new Exception("备份缺少加密信息");
            }

            byte[] salt = Base64.decode(kdf.getString("salt"), Base64.NO_WRAP);
            String algorithm = kdf.optString("algorithm", "PBKDF2WithHmacSHA256");
            byte[] key = derive(password, salt, algorithm);

            byte[] cipher = Base64.decode(envelope.getString("data"), Base64.NO_WRAP);
            byte[] plain = CryptoUtils.decrypt(cipher, key);

            payload = new JSONObject(new String(plain, StandardCharsets.UTF_8));
        }
        else
        {
            payload = envelope.optJSONObject("data");
            if (payload == null)
            {
                throw new Exception("备份内容为空");
            }
        }

        return appendPayload(context, payload);
    }

    private static ImportResult appendPayload(Context context, JSONObject payload) throws Exception
    {
        AppDatabase db = AppDatabase.get(context);

        JSONArray folderArray = payload.optJSONArray("folders");
        JSONArray accountArray = payload.optJSONArray("accounts");

        if (folderArray == null)
        {
            folderArray = new JSONArray();
        }
        if (accountArray == null)
        {
            accountArray = new JSONArray();
        }

        Map<Long, Long> folderMap = new HashMap<>();
        int folderCount = 0;

        // 旧文件夹 id → 新文件夹 id
        for (int i = 0; i < folderArray.length(); i++)
        {
            JSONObject item = folderArray.getJSONObject(i);

            long oldId = item.optLong("id", 0);
            String baseName = item.optString("name", "未命名");
            String name = uniqueFolderName(db, baseName);

            Folder folder = new Folder();
            folder.name = name;
            folder.sortOrder = item.optInt("sortOrder", 0);
            folder.createdAt = item.optLong("createdAt", System.currentTimeMillis());

            long newId = db.folderDao().insert(folder);
            folderMap.put(oldId, newId);
            folderCount++;
        }

        long unclassifiedId = -1;
        int accountCount = 0;

        for (int i = 0; i < accountArray.length(); i++)
        {
            JSONObject item = accountArray.getJSONObject(i);

            long oldFolderId = item.optLong("folderId", 0);
            Long newFolderId = folderMap.get(oldFolderId);

            if (newFolderId == null)
            {
                if (unclassifiedId < 0)
                {
                    Folder folder = new Folder();
                    folder.name = uniqueFolderName(db, "未分类");
                    folder.createdAt = System.currentTimeMillis();
                    unclassifiedId = db.folderDao().insert(folder);
                }
                newFolderId = unclassifiedId;
            }

            Account account = new Account();
            account.folderId = newFolderId;
            account.title = safeString(item, "title");
            account.username = safeString(item, "username");
            account.password = safeString(item, "password");
            account.url = nullableString(item, "url");
            account.email = nullableString(item, "email");
            account.phone = nullableString(item, "phone");
            account.notes = nullableString(item, "notes");
            account.createdAt = item.optLong("createdAt", System.currentTimeMillis());
            account.updatedAt = item.optLong("updatedAt", System.currentTimeMillis());

            db.accountDao().insert(account);
            accountCount++;
        }

        return new ImportResult(folderCount, accountCount);
    }

    private static JSONObject buildPayload(Context context, Long folderId,
                                           List<Long> accountIds) throws Exception
    {
        AppDatabase db = AppDatabase.get(context);

        List<Folder> folders;
        List<Account> accounts;

        if (accountIds != null && !accountIds.isEmpty())
        {
            // 只导出选中的账号:文件夹也要带上,导入时才能正确归类
            accounts = db.accountDao().getByIds(accountIds);

            Set<Long> folderIds = new LinkedHashSet<>();
            for (Account account : accounts)
            {
                folderIds.add(account.folderId);
            }

            folders = new ArrayList<>();
            for (Long id : folderIds)
            {
                Folder folder = db.folderDao().findById(id);
                if (folder != null)
                {
                    folders.add(folder);
                }
            }

            // 极端情况:选中的账号都查不到,但指定了文件夹,也把文件夹带上
            if (folders.isEmpty() && folderId != null)
            {
                Folder folder = db.folderDao().findById(folderId);
                if (folder != null)
                {
                    folders.add(folder);
                }
            }
        }
        else if (folderId == null)
        {
            // 全部导出
            folders = db.folderDao().getAll();
            accounts = db.accountDao().getAll();
        }
        else
        {
            // 只导出指定文件夹:文件夹本身 + 它下面的账号
            Folder folder = db.folderDao().findById(folderId);

            if (folder == null)
            {
                folders = Collections.emptyList();
                accounts = Collections.emptyList();
            }
            else
            {
                folders = Collections.singletonList(folder);
                accounts = db.accountDao().getByFolder(folderId);
            }
        }

        JSONArray folderArray = new JSONArray();
        for (Folder folder : folders)
        {
            JSONObject item = new JSONObject();
            item.put("id", folder.id);
            item.put("name", folder.name);
            item.put("sortOrder", folder.sortOrder);
            item.put("createdAt", folder.createdAt);
            folderArray.put(item);
        }

        JSONArray accountArray = new JSONArray();
        for (Account account : accounts)
        {
            JSONObject item = new JSONObject();
            item.put("id", account.id);
            item.put("folderId", account.folderId);
            item.put("title", account.title);
            item.put("username", account.username);
            item.put("password", account.password);
            item.put("url", account.url == null ? JSONObject.NULL : account.url);
            item.put("email", account.email == null ? JSONObject.NULL : account.email);
            item.put("phone", account.phone == null ? JSONObject.NULL : account.phone);
            item.put("notes", account.notes == null ? JSONObject.NULL : account.notes);
            item.put("createdAt", account.createdAt);
            item.put("updatedAt", account.updatedAt);
            accountArray.put(item);
        }

        JSONObject payload = new JSONObject();
        payload.put("appVersion", "1.1");
        payload.put("exportedAt", System.currentTimeMillis());
        payload.put("folders", folderArray);
        payload.put("accounts", accountArray);

        return payload;
    }

    /**
     * 文件夹名唯一:重名时依次尝试 name-1、name-2……
     */
    private static String uniqueFolderName(AppDatabase db, String baseName)
    {
        String name = (baseName == null || baseName.trim().isEmpty())
                ? "未命名" : baseName.trim();

        String candidate = name;
        int index = 1;

        while (db.folderDao().findByName(candidate) != null)
        {
            candidate = name + "-" + index;
            index++;
        }

        return candidate;
    }

    private static String safeString(JSONObject item, String key)
    {
        String value = item.optString(key, "");
        return value == null ? "" : value;
    }

    private static String nullableString(JSONObject item, String key)
    {
        if (item.isNull(key))
        {
            return null;
        }
        return item.optString(key, null);
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

    private static byte[] randomBytes(int length)
    {
        byte[] data = new byte[length];
        new SecureRandom().nextBytes(data);
        return data;
    }

    /**
     * 导入结果统计。
     */
    public static final class ImportResult
    {
        public final int folders;
        public final int accounts;

        ImportResult(int folders, int accounts)
        {
            this.folders = folders;
            this.accounts = accounts;
        }
    }
}
