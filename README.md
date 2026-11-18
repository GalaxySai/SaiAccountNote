# 赛的密码本 · SaiAccountNote

> 一个**纯本地、离线、单机**的 Android 账号密码本。
> 用主密码 + SQLCipher 加密，不联网、不上传、不收集任何信息。

[![Platform](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](#)
[![Language](https://img.shields.io/badge/Java-11-orange)](#)
[![Room](https://img.shields.io/badge/Room-2.6.1-blue)](#)
[![SQLCipher](https://img.shields.io/badge/SQLCipher-4.6.1-4B6BFB)](#)
[![License](https://img.shields.io/badge/License-TBD-lightgrey)](#)

---

## 目录

- [项目简介](#项目简介)
- [功能](#功能)
- [技术栈](#技术栈)
- [架构](#架构)
- [安全设计](#安全设计)
- [数据模型](#数据模型)
- [备份格式](#备份格式)
- [项目结构](#项目结构)
- [构建与运行](#构建与运行)
- [签名与发布](#签名与发布)
- [测试](#测试)
- [已知限制与 TODO](#已知限制与-todo)
- [开发注意事项](#开发注意事项)
- [贡献指南](#贡献指南)
- [安全说明与免责声明](#安全说明与免责声明)
- [版本记录](#版本记录)

---

## 项目简介

**赛的密码本**是一个自用的 Android 密码管理 App。目标不是做成商业产品，而是：

- 数据**只存在本机**，不依赖任何服务端；
- 用**主密码 + 数据库加密**保护数据；
- 代码保持简单、可读，适合作为 Android 安全存储的练习项目；
- 能导出加密备份，换手机时不丢数据。

当前版本 **1.1**，已在真机验证主要流程。

> 这是一个个人项目，**没有经过专业安全审计**。重要账号请自行评估风险，或使用成熟密码管理器。

---

## 功能

### 已实现

- 文件夹分类 + 账号增删改查
- 账号字段：标题、用户名、密码、网址、绑定邮箱、绑定手机、备注
- 全局搜索：首页同时搜文件夹名、账号标题、用户名
- 密码默认打码，可点击显示/隐藏
- 一键复制用户名/密码，60 秒后自动清除剪贴板
- 深色 / 浅色模式（侧边栏切换并持久化）
- 主密码设置 / 解锁 / 修改
- SQLCipher 整库加密
- 指纹 / 面容解锁（Android Keystore + BiometricPrompt）
- 后台自动锁定（默认 5 分钟，可配置 1/5/15/30 分钟）
- 防截屏（FLAG_SECURE，含所有弹窗）
- 关闭系统备份（`allowBackup=false`）
- 加密 JSON 备份导出 / 导入
- 可选明文 JSON 导出（带风险提示）
- 导出 CSV 表格（Excel/WPS 可打开），支持直接分享
- 文件夹单独导出、按选中账号导出
- 备份导入为追加模式，文件夹重名自动加 `-1`、`-2`
- 账号可移动到其他文件夹
- 批量多选：批量移动 / 删除 / 导出
- 批量导出表格（选中账号直接 CSV）
- 自适应应用图标 + 多尺寸 PNG

### 未实现（详见 [TODO](#已知限制与-todo)）

- 账号表单内密码生成器
- 删除撤销 / 回收站
- 排序
- 网址点击跳转
- 导入合并/去重
- RecyclerView 重构
- 单元测试 / CI

---

## 技术栈

| 类别 | 选型 | 版本 |
|---|---|---|
| 语言 | Java | 11 |
| 构建 | AGP | 8.8.0 |
| compileSdk / targetSdk | 35 | |
| minSdk | 24（Android 7.0） | |
| UI | AppCompat + Material 3 | material 1.10.0 |
| 数据库 | Room | 2.6.1 |
| 数据库加密 | SQLCipher for Android | `net.zetetic:sqlcipher-android` 4.6.1 |
| 生物识别 | `androidx.biometric` | 1.1.0 |
| 侧边栏 | `androidx.drawerlayout` | 1.2.0 |
| 布局 | ConstraintLayout | 2.1.4 |
| 测试 | JUnit4 / AndroidX Test | 默认模板 |

Room 在 Java 项目中使用 `annotationProcessor`，不需要 kapt/ksp。

---

## 架构

分层结构，单向依赖：

```
UI (Activity / DialogFragment / BottomSheet)
        ↓
Repository (FolderRepository / AccountRepository)
        ↓
Data (Room + SQLCipher)
        ↑
Security (密钥、会话、生物识别)
```

### 线程模型

- 数据库读写统一走 `repository/AppExecutors`；
- `AppExecutors` 只有一个后台线程，保证 SQLite 串行访问；
- Repository 的回调通过主线程 Handler 返回；
- 数据库**不允许**在主线程直接访问。

### 目录职责

| 包 | 职责 |
|---|---|
| `ui/home` | 首页、文件夹弹窗、备份导出/导入入口 |
| `ui/account` | 账号列表、新建/修改账号 BottomSheet、删除确认 |
| `ui/unlock` | 首次设置主密码 / 解锁 |
| `ui/settings` | 修改主密码、生物识别开关 |
| `data` | Room 数据库、实体、DAO、迁移 |
| `repository` | 线程调度 + 数据访问封装 |
| `security` | 主密码、密钥包裹、会话、生物识别、加解密 |
| `backup` | 备份导出/导入 |
| `common` | 防截屏等通用工具 |

---

## 安全设计

### 1. 密钥体系

```
主密码
  │ PBKDF2-HMAC-SHA256, 150,000 次, 16 字节随机盐
  ▼
KEK (256-bit)
  │ AES-256-GCM 解包
  ▼
数据库密钥 K (256-bit)
  │ SQLCipher SupportOpenHelperFactory
  ▼
san.db (整库加密)
```

- 主密码**不保存**，只保存：盐、`SHA-256(K)` 校验值、`AES-GCM(K)` 包裹后的密文、PBKDF2 算法名。
- 首次设置时生成**随机 K**；旧版本用户升级时，首次解锁会把原来的派生密钥 K 自动升级为“包裹”格式。
- **修改主密码不需要重新加密数据库**：只把同一个 K 用新密码派生的 KEK 重新包裹。

### 2. 生物识别

- 在 Android Keystore 生成 AES 密钥，`setUserAuthenticationRequired(true)`；
- 用该密钥加密 K，密文存 SharedPreferences；
- 解锁时 `BiometricPrompt` + `CryptoObject` 解密 K；
- `setInvalidatedByBiometricEnrollment(true)`：新增/删除指纹后旧密钥失效，自动回退主密码；
- 只支持 `BIOMETRIC_STRONG`，弱生物识别（部分 2D 面容）不降级。

### 3. 自动锁定

- `App` 通过 `ActivityLifecycleCallbacks` 统计前台 Activity 数量；
- 全部 stop 后记录时间；
- 回前台时超过设定时间（默认 5 分钟，可在设置里改成 1/5/15/30 分钟）→ `SessionManager.lock()`，Activity 跳转解锁页；
- 密钥只存在内存，进程被杀即清空。

### 4. 防泄露

- 所有 Activity、BottomSheet、AlertDialog 都应用 `FLAG_SECURE`；
- `allowBackup=false`；
- 发布版不要打印密码/密钥/数据库内容；
- 备份文件可选加密，明文导出有明确警告。

### 5. 威胁模型（能防什么 / 不能防什么）

**能防：**

- 手机丢失/被他人拿到后的直接查看；
- 系统截屏、最近任务预览；
- 明文数据库文件被直接读取（SQLCipher）；
- 弱密码暴力破解的成本提升（PBKDF2 15 万次）。

**不能防：**

- Root / 被攻破的系统、内存抓取；
- 恶意输入法、屏幕录制、物理偷窥；
- 用户自己泄露主密码或备份密码；
- 弱备份密码被离线爆破。

---

## 数据模型

Room `@Database(version = 2)`：

```sql
CREATE TABLE folders (
    id         INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    name       TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX index_folders_name ON folders(name);

CREATE TABLE accounts (
    id         INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    folder_id  INTEGER NOT NULL,
    title      TEXT NOT NULL,
    username   TEXT NOT NULL,
    password   TEXT NOT NULL,
    url        TEXT,
    email      TEXT,
    phone      TEXT,
    notes      TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY(folder_id) REFERENCES folders(id)
        ON UPDATE NO ACTION ON DELETE CASCADE
);
```

说明：

- 文件夹名唯一；账号允许重名；
- 删除文件夹会**级联删除**其下账号；
- 时间字段用毫秒时间戳 `long`；
- `app/schemas/` 是 Room 导出的 schema，**建议提交到 Git**，方便写迁移。

### 迁移

- `MigrationV1ToV2`：从“只有 folders”的版本补建 accounts 表；
- `data/DatabaseMigration`：把接入 SQLCipher 之前的**明文 `san.db`** 读出、改名备份、建加密库、按原 id 写回；
- 迁移在解锁成功后、进入首页前执行。

---

## 备份格式

导出文件是一个 JSON 信封：

```json
{
  "format": "sai-account-note-backup",
  "version": 1,
  "encrypted": true,
  "exportedAt": 1763200000000,
  "cipher": "AES-GCM",
  "kdf": {
    "algorithm": "PBKDF2WithHmacSHA256",
    "iterations": 150000,
    "salt": "Base64(...)"
  },
  "data": "Base64(IV + 密文 + 认证标签)"
}
```

- `data` 解密后是：
  ```json
  {
    "appVersion": "1.1",
    "exportedAt": 1763200000000,
    "folders": [ { "id": 1, "name": "游戏", "sortOrder": 0, "createdAt": 0 } ],
    "accounts": [ { "id": 1, "folderId": 1, "title": "...", "username": "...", "password": "...", "url": null, "email": null, "phone": null, "notes": null, "createdAt": 0, "updatedAt": 0 } ]
  }
  ```
- 加密：PBKDF2 派生 KEK → AES-256-GCM（随机 12 字节 IV，128-bit tag）；
- 明文模式：`encrypted=false`，`data` 直接是上面的对象；
- 导入为**追加**：不删除现有数据；文件夹重名加 `-1`、`-2`；账号原样追加。

代码：`backup/BackupManager.java`、`security/CryptoUtils.java`。

> 另提供人类可读的 **CSV 表格导出**（`backup/TableExporter.java`），用于 Excel/WPS 查看或分享；它和加密 JSON 备份相互独立，CSV 是明文。

---

## 项目结构

```
SaiAccountNote/
├── app/
│   ├── build.gradle.kts
│   ├── schemas/                     Room 导出的 schema(建议提交)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/galaxysai/saiaccountnote/
│       │   ├── App.java
│       │   ├── backup/
│       │   │   ├── BackupManager.java
│       │   │   └── TableExporter.java
│       │   ├── common/
│       │   │   ├── ClipboardHelper.java
│       │   │   └── SecureWindow.java
│       │   ├── data/
│       │   │   ├── AppDatabase.java
│       │   │   ├── DatabaseMigration.java
│       │   │   ├── dao/
│       │   │   ├── entity/
│       │   │   └── migration/
│       │   ├── repository/
│       │   ├── security/
│       │   └── ui/
│       │       ├── account/
│       │       ├── home/
│       │       ├── settings/
│       │       └── unlock/
│       └── res/
│           ├── drawable/            图标、背景、矢量图标
│           ├── drawable-nodpi/      自适应图标前景
│           ├── layout/
│           ├── menu/
│           ├── mipmap-*/
│           ├── values/              颜色、主题、字符串
│           └── values-night/        深色主题
├── gradle/libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 构建与运行

### 环境要求

- Android Studio（建议最新稳定版）
- JDK 11 或 17
- Android SDK Platform 35
- `minSdk 24` 的设备或模拟器

### 配置 SDK

项目根目录的 `local.properties`（**不要提交**）：

```properties
sdk.dir=C\:\\path\\to\\Android\\Sdk
```

Android Studio 打开项目后一般会自动生成。

### 构建

Windows：

```bat
gradlew.bat assembleDebug
```

macOS / Linux：

```bash
./gradlew assembleDebug
```

产物：

```
app/build/outputs/apk/debug/app-debug.apk
```

### 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 常见构建问题

- `Cannot resolve symbol 'room'`：Gradle 未同步，点 **Sync Project with Gradle Files**；
- 模拟器启动即崩、日志出现 `TopResumedActivityChangeItem`：部分 Android 16（API 36）镜像的框架问题，换 **API 34/35** 镜像；
- 模拟器无法输入中文：模拟器默认输入法问题，装中文输入法或用宿主机物理键盘。

---

## 签名与发布

当前项目**没有配置正式签名**，Release 构建默认不会用 debug 签名（取决于 Gradle 配置）。发布前请：

1. 生成 keystore：

```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias san
```

2. 在 `app/build.gradle.kts` 配置 `signingConfigs`，或在 Android Studio 的 **Build → Generate Signed Bundle/APK** 中操作；
3. **不要提交** `*.jks` / `*.keystore` / 密码；
4. 每次发版：
   - `versionCode` 加 1；
   - `versionName` 更新（如 `1.0` → `1.1`）；
   - 保留上一版 APK，便于回滚。

---

## 测试

### 手动回归清单

- [ ] 首次启动 → 设置主密码 → 进入首页
- [ ] 新建文件夹 / 重命名 / 删除
- [ ] 新建账号 → 展开详情 → 显示/隐藏密码、复制用户名/密码（60 秒后剪贴板自动清空）
- [ ] 修改账号 → 保存 → 列表刷新
- [ ] 删除账号 → 确认框 → 删除
- [ ] 首页全局搜索：文件夹名、账号标题、用户名
- [ ] 文件夹 `⋮` → 导出此文件夹；账号 `⋮` → 移动到
- [ ] 长按账号 → 多选 → 批量移动 / 删除 / 导出（JSON、CSV）
- [ ] 导出弹窗 → 导出成表格 → Excel/WPS 打开，中文正常
- [ ] 深色/浅色切换（不闪、不关侧边栏）
- [ ] 杀进程重开 → 需要解锁
- [ ] 修改主密码 → 旧密码失效、新密码可解锁
- [ ] 指纹/面容开启 → 杀进程 → 生物识别解锁
- [ ] 设置 → 自动锁定改成 1 分钟 → 后台 1 分钟 → 重新解锁
- [ ] 截屏 → 黑屏/空白
- [ ] 加密导出 → 导入时要求备份密码
- [ ] 明文导出 → 文件可读、导入不要求密码
- [ ] 导入重名文件夹 → 自动 `-1`
- [ ] 旋转屏幕时打开弹窗 → 不崩溃

### 测试注意

- **生物识别、自动锁定、防截屏建议用真机测**；
- 自动锁定默认 5 分钟，测试时在“设置 → 自动锁定”里改成 1 分钟，测完改回；
- 模拟器镜像问题不代表 App 问题，先看崩溃栈是否包含 `com.galaxysai.saiaccountnote`。

---

## 已知限制与 TODO

### P0（发布相关）

- [ ] 正式签名（release keystore）

### P1（体验）

- [ ] RecyclerView 重构列表
- [ ] 删除撤销 / 回收站
- [ ] 排序（时间/字母）
- [ ] 网址点击跳转浏览器
- [ ] 账号表单内密码生成器

### P2（以后）

- [ ] 导入合并/去重
- [ ] TOTP / 两步验证码
- [ ] 标签 / 收藏
- [ ] 自定义 Toast
- [ ] 单元测试 + CI
- [ ] 多设备同步（与“纯本地”定位冲突，暂不做）

### 已知限制

- 搜索是内存过滤，不适合上万条数据；
- 列表是手动 `LinearLayout`，没有 RecyclerView 的复用/动画；
- 生物识别只支持强生物识别；
- 加密备份的密码无法找回；
- 主密码无法找回；
- 明文导出会把密码写在文件里。

---

## 开发注意事项

### 1. 不要随意改加密参数

- PBKDF2 的 **算法、迭代次数** 已写进偏好设置和备份文件；
- 修改会导致旧数据/旧备份无法解密；
- 备份信封里已保存 `algorithm` / `iterations`，读取时应使用文件里的值，而不是写死当前常量。

### 2. 数据库加字段必须升版本

```java
@Database(entities = {Folder.class, Account.class}, version = 3, exportSchema = true)
```

并编写 `Migration(2, 3)`；保留 `app/schemas/` 并提交。不要直接改表结构。

### 3. `AppDatabase.get()` 必须在解锁后调用

- 它依赖 `SessionManager.getDbKey()`；
- 未解锁调用会抛 `IllegalStateException("数据库尚未解锁")`；
- 新页面如果直接访问数据库，记得先检查会话。

### 4. 新页面/弹窗要加防截屏

```java
SecureWindow.apply(getWindow());              // Activity
SecureWindow.apply(dialog.getWindow());       // Dialog
```

### 5. 线程

- 数据库操作不要放主线程；
- 使用 `AppExecutors.get().diskIO(...)`，回调切回主线程；
- Repository 的回调一定在主线程。

### 6. 主题与颜色

- 颜色统一用 `@color/page_bg`、`@color/text_primary` 等语义色；
- 深色在 `values-night/colors.xml`；
- 不要在布局里写死 `#FFFFFF`、`#0F1419`。

### 7. 状态栏 insets

- 每个 Activity 根布局用 `ViewCompat.setOnApplyWindowInsetsListener` 加系统栏 padding；
- 参考 `HomeActivity.applySystemBarInsets()`。

### 8. 备份格式

- 修改备份结构时**升级 `version`**；
- 导入要兼容旧版本；
- 不要在日志里打印明文。

### 9. 版本控制

当前项目**尚未初始化 Git**。建议：

```bash
git init
git add .
git commit -m "v1.0 baseline"
```

`.gitignore` 建议补充：

```gitignore
# 构建产物
/app/release/
/build

# 签名文件
*.jks
*.keystore

# 本地配置
local.properties

# IDE
.idea/
```

**必须提交**：

- `app/schemas/`（Room schema）
- `gradle/wrapper/`
- 所有源码和资源

**不要提交**：

- `local.properties`
- `*.jks` / `*.keystore`
- `app/build/`、`app/release/`
- 任何真实备份文件（含明文密码）
- `_removed_legacy/`（如果放在项目内）

### 10. 不要提交真实数据

调试时不要把自己的真实密码、真实备份文件、`san.db` 提交到仓库或贴到 Issue。

---

## 贡献指南

欢迎 Issue / PR，但请注意：

1. **先看 [已知限制与 TODO](#已知限制与-todo)**，避免重复；
2. Fork → 新建分支（`feature/xxx`）→ 提交 PR；
3. PR 描述里写清楚：
   - 改了什么；
   - 为什么改；
   - 怎么验证（最好附测试步骤）；
4. 涉及加密/数据库迁移的改动，请特别说明兼容性；
5. 代码风格：Java、4 空格缩进、Allman 大括号、中文注释；
6. 不要在 PR 里附带真实密码、备份文件、keystore。

### 代码审查重点

- 加密是否用了成熟库，而不是自造算法；
- 密钥/密码是否可能被日志、崩溃上报、备份泄露；
- 数据库迁移是否可回滚、是否保留旧数据；
- 新页面是否加了 `FLAG_SECURE`；
- 数据库操作是否在后台线程；
- 屏幕旋转/进程重建时是否安全。

---

## 安全说明与免责声明

- 本项目是**个人自建工具**，未经过第三方安全审计；
- 加密实现依赖 SQLCipher、Android Keystore、AES-GCM、PBKDF2，但没有做形式化验证；
- 不建议直接用于银行卡、支付、主邮箱等高风险账号；
- 使用本项目的风险由使用者自行承担；
- 如果发现安全问题，请**私下**联系作者，不要在公开 Issue 里贴真实密码或可利用细节。

---

## 版本记录

| 版本 | 日期 | 说明 |
|---|---|---|
| 1.0 | 2026-11-15 | 首个可用版本：CRUD、加密、主密码、生物识别、自动锁定、备份、深色模式、图标与名称 |
| 1.1 | 2026-11-18 | 文件夹导出、全局搜索、账号迁移、批量操作；复制+自动清剪贴板；CSV 表格导出/分享（含批量表格）；自动锁定时长可配置 |

> 完整更新日志见 [CHANGELOG.md](CHANGELOG.md)。

---

## License

暂未指定。如果你打算公开仓库并允许他人使用/修改，建议补充一份开源协议，例如 **MIT** 或 **Apache-2.0**，并在仓库根目录添加 `LICENSE` 文件。

> 在添加 License 之前，默认保留所有权利。
