# App 内更新（手动检查）

App 里的「规则 → 关于 → 检查更新」会读取一个 HTTPS 上的 `update.json`，比较 `versionCode`，有新版才显示「下载并安装」。下载后会校验 SHA-256 和 APK 签名证书，最后交给系统安装器，**仍需要用户在系统弹窗里点“安装”**。

## 1. 托管位置

当前代码里的清单地址在 `UpdateChecker.MANIFEST_URL`：

```text
https://github.com/AKATSUKI420/tingyixia/releases/latest/download/update.json
```

这个地址要求仓库是 **public**，而且每次 Release 都上传一个固定文件名的 `update.json`。如果仓库是私有的，或者想放在自己的服务器上，只改 `UpdateChecker.MANIFEST_URL` 一个常量即可；APK 地址放在 `apkUrl` 里，同样必须是 HTTPS。

## 2. 每次发版的步骤

1. 升 `versionCode` / `versionName`（每次改动都要升）。
2. 构建 release 包，确认签名还是 `keys/tingyixia-release.jks`（`tingyixia`）。
3. 计算 APK 的 SHA-256。
4. 复制 `update.json.example` 为 `update.json`，改成本次版本、APK 直链和哈希。
5. 在 GitHub 建一个 Release，tag 用 `v1.0.0` 这种格式。
6. 把 APK 和 `update.json` 都上传到该 Release。
7. 用旧版本安装包打开 App，点检查更新，完整走一遍下载 → 校验 → 系统安装。

示例：

```json
{
  "versionCode": 11,
  "versionName": "1.0.0",
  "apkUrl": "https://github.com/AKATSUKI420/tingyixia/releases/download/v1.0.0/tingyixia-v1.0.0.apk",
  "sha256": "425AA3112B16C186DF5D1DF37C79055A326AB04CAF6805E3BB352686DF1FE162",
  "notes": "首个正式版：只遮罩抖音 / 哔哩哔哩；1–600 分钟计时与自动解开；五种遮罩动画和四档速度；白色 iOS 风格界面；支持 App 内检查更新。"
}
```

`versionCode` 必须大于用户当前安装的版本，否则 App 会认为已经是最新。`apkUrl` 必须指向同一个签名的包，否则 Android 会拒绝覆盖安装；App 在下载后也会先校验签名，不一致直接停止。

## 3. 权限与系统限制

- `INTERNET`：只在用户点“检查更新”时访问清单和 APK；App 不做后台轮询、不上传数据。
- `REQUEST_INSTALL_PACKAGES`：Android 8+ 需要；首次安装新版本时，系统会要求允许“安装未知应用”。
- 普通 App 无法静默安装。不要尝试绕过系统安装器；只有设备管理员 / MDM / 系统签名 / root 才能静默更新。
- 正式包和更新包必须使用同一把签名密钥。换了签名，用户必须先卸载旧版，数据会丢。

## 4. 安全约定

- 只接受 HTTPS 的 `apkUrl`。
- 校验 `sha256`（64 位十六进制）。
- 校验下载 APK 的签名证书与当前安装包一致。
- 只接受更高的 `versionCode`，不降级。
- 不做后台自动检查；用户主动点才联网。
