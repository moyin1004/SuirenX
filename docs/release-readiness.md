# 发布准备

当前项目仍处于发布前开发阶段。此文档记录需要由项目维护者完成的发布决策，不代表已经签名、发布或完成法律审查。

## 版本策略

- Android 使用单调递增的 `versionCode` 和面向用户的 `versionName`；每个可分发构建使用唯一 `versionCode`。
- Go 服务与 Android 客户端分别标记版本。Proto/HTTP JSON 变更保持向后兼容；破坏性变更需要先定义迁移和双版本支持计划。
- Git tag 使用 `vMAJOR.MINOR.PATCH`。开发构建可带预发布后缀，正式 tag 不复用。
- 变更数据库时遵守 [数据库迁移规则](database-migrations.md)，升级路径须在发布前验证。

## Android 签名与构建

- 本地 Debug 使用默认调试签名；禁止把 keystore、密码、`local.properties` 或签名配置提交到 Git。
- 发布签名材料由维护者保存在受控密钥管理设施中。CI 发布任务只通过受保护的环境 secret 注入，不在 pull request 工作流中签名。
- Release workflow 使用名为 `release` 的 GitHub Environment，并接受四个 Environment secrets：`SUIRENX_ANDROID_KEYSTORE_BASE64`、`SUIRENX_ANDROID_KEYSTORE_PASSWORD`、`SUIRENX_ANDROID_KEY_ALIAS` 和 `SUIRENX_ANDROID_KEY_PASSWORD`。维护者仍需在 GitHub 配置该 Environment 的保护规则并保存密钥。四项均未配置时只生成 unsigned artifact；配置不完整时任务失败；四项齐全时额外校验 zipalign、签名并通过 `apksigner verify`，再上传 APK、SHA-256 与证书摘要。此处仅定义消费方式，尚未配置维护者密钥或验证真实签名产物。
- 2026-09-28 通过 GitHub API 复核：当前账户有仓库 Admin 权限，但 `release` Environment 查询返回 404，环境尚未创建；正式配置需维护者确认审批人与部署分支策略。
- 发布前生成并保管签名证书指纹，测试同一签名的升级安装、冷启动、备份/恢复，以及 Android 支持的最低和目标版本。
- 生成 APK/AAB 后记录版本、Git commit、SHA-256 和签名证书指纹；分发渠道和回滚操作由发布者确认。

## 隐私说明待确认项

已由代码确认：资产和用品的主要数据保存在 Android 本机 Room；本机 JSON 备份包括资产、归档记录和用品，不包括登录凭据；启用同步并显式登录后，客户端会将待同步记录发送到用户配置的服务器。服务端保存账号凭据哈希、同步记录和用于版本化同步的元数据；登录/同步使用 bearer JWT。

Android Auto Backup 默认包含 SharedPreferences；登录 token 保存在 `auth.xml`。当前 Android 11 及以下与 Android 12 及以上的备份规则均排除 `auth.xml`，保留其他应用数据备份。资源已进入 Debug APK 并通过 `aapt dump xmltree` 检查；本次 AVD 的 Backup Manager 处于 disabled 状态，未对现有数据启用或触发备份，云备份和设备转移仍需在支持设备上实测。[Android Auto Backup 官方文档](https://developer.android.com/identity/data/autobackup)

发布前维护者仍需确认并补充：运营主体与联系渠道、适用地区、服务器运营者、日志中实际保留的字段和期限、数据库备份保留与删除策略、账号/同步数据删除方式、第三方依赖及其数据处理、用户权利请求入口和隐私政策生效日期。未经这些信息确认，不应把本节当作面向用户的最终隐私政策。

## 分发前检查

- [ ] 维护者身份、支持渠道、服务器域名与 TLS 已确认。
- [ ] 签名密钥、应用商店/分发账号和恢复流程已配置且未进入仓库。
- [ ] 支持设备、Android 版本、权限用途和备份行为已实测。
  - 当前代码声明 `minSdk=26`、`targetSdk=36`，Manifest 仅请求 `INTERNET`。提醒的通知权限尚未实现，届时需要补充用途说明与授权/拒绝路径验收。
  - Auto Backup 的凭据排除规则已在 Debug 和 unsigned Release APK 中检查；实体设备云备份、设备转移、干净安装/升级恢复仍待实测。
- [ ] 隐私政策已由运营者核实并发布，且应用内可访问。
- [ ] 从干净安装和上一发布版本升级均完成验收。
- [ ] GitHub Release 发布后，Release APK workflow 成功生成 APK artifact；由维护者完成签名、校验和记录与分发。
