# v0.1.0 发布准备

当前先发布 `v0.1.0-preview` GitHub 预发布版，Android v0.1.0 正式分发仍待后续验收，尚未完成正式签名与分发验收。范围见 [本版需求](prd/v0.1.0.md)。阶段 A 后台可靠性补验、阶段 C 提醒功能已顺延到下一版本，不再作为本版发布前置条件；发布说明应披露这些边界。

## 版本策略

- Android 使用单调递增的 `versionCode` 和面向用户的 `versionName`；每个可分发构建使用唯一 `versionCode`。
- Go 服务与 Android 客户端分别标记版本。Proto/HTTP JSON 变更保持向后兼容；破坏性变更需要先定义迁移和双版本支持计划。
- Git tag 使用 `vMAJOR.MINOR.PATCH`。开发构建可带预发布后缀，正式 tag 不复用。
- 变更数据库时遵守 [数据库迁移规则](database-migrations.md)，升级路径须在发布前验证。

## Android 签名与构建

- 本地 Debug 使用默认调试签名；禁止把 keystore、密码、`local.properties` 或签名配置提交到 Git。
- 发布签名材料由维护者保存在受控密钥管理设施中。CI 发布任务只通过受保护的环境 secret 注入，不在 pull request 工作流中签名。
- Release workflow 使用名为 `release` 的 GitHub Environment，并接受四个 Environment secrets：`SUIRENX_ANDROID_KEYSTORE_BASE64`、`SUIRENX_ANDROID_KEYSTORE_PASSWORD`、`SUIRENX_ANDROID_KEY_ALIAS` 和 `SUIRENX_ANDROID_KEY_PASSWORD`。环境已限制为 `v*` tag；密钥与密码在仓库外交付维护者备份。四项均未配置时只生成 unsigned artifact；配置不完整时任务失败；四项齐全时额外校验 zipalign、签名并通过 `apksigner verify`，再上传 APK、SHA-256 与证书摘要。2026-09-30 已按维护者授权生成持久签名密钥并配置四项 Environment secrets；实际签名产物将在本次预发布中验证。
- 配置时重新核验 GitHub Environment、保护规则与 secrets 状态；过去查询结果见 [历史记录](history/roadmap-execution.md)，不据此推断当前远端状态。
- 发布前生成并保管签名证书指纹，测试同一签名的升级安装、冷启动、备份/恢复，以及 Android 支持的最低和目标版本。
- 生成 APK/AAB 后记录版本、Git commit、SHA-256 和签名证书指纹；分发渠道和回滚操作由发布者确认。

## 隐私说明待确认项

已由代码确认：资产和用品的主要数据保存在 Android 本机 Room；本机 JSON 备份包括资产、归档记录和用品，不包括登录凭据；启用同步并显式登录后，客户端会将待同步记录发送到用户配置的服务器。服务端保存账号凭据哈希、同步记录和用于版本化同步的元数据；登录/同步使用 bearer JWT。

Android Auto Backup 默认包含 SharedPreferences；登录 token 保存在 `auth.xml`。当前 Android 11 及以下与 Android 12 及以上的备份规则均排除 `auth.xml`，保留其他应用数据备份。既有 Debug / unsigned Release APK 的资源检查见历史记录；云备份和设备转移仍需在支持设备上实测。[Android Auto Backup 官方文档](https://developer.android.com/identity/data/autobackup)

发布前维护者仍需确认并补充：运营主体与联系渠道、适用地区、服务器运营者、日志中实际保留的字段和期限、数据库备份保留与删除策略、账号/同步数据删除方式、第三方依赖及其数据处理、用户权利请求入口和隐私政策生效日期。未经这些信息确认，不应把本节当作面向用户的最终隐私政策。

## 分发前检查

- [ ] 维护者身份、支持渠道、服务器域名与 TLS 已确认。
- [ ] 签名密钥、应用商店/分发账号和恢复流程已配置且未进入仓库。
- [ ] 支持设备、Android 版本、权限用途和备份行为已实测。
  - 当前代码声明 `minSdk=26`、`targetSdk=36`，Manifest 仅请求 `INTERNET`。提醒的通知权限尚未实现，届时需要补充用途说明与授权/拒绝路径验收。
  - Auto Backup 的凭据排除规则已在 Debug 和 unsigned Release APK 中检查；实体设备云备份、设备转移、干净安装/升级恢复仍待实测。
- [ ] 隐私政策已由运营者核实并发布，且应用内可访问。
- [ ] 正式签名 APK 的干净安装与同签名覆盖安装通过；首次发布无上一正式版本，后续版本必须验证从上一发布版升级。Debug 与正式签名不同，不能直接覆盖；切换前先导出 JSON 备份。
- [ ] GitHub Release 发布后，Release APK workflow 成功生成已签名 APK、SHA-256 和证书摘要；下载复核后附加到 Release 下载页。unsigned APK 不作为正式安装包。

## 预发布

2026-09-30：维护者要求将 dev squash 合并到 main，并创建 `v0.1.0-preview` tag 与同名 GitHub Pre-release。已创建 `release` Environment（仅允许 `v*` tag）并配置四项签名 secrets；此次预发布验证签名构建，设备分发验收继续保留。workflow 仍在 published 事件后构建；无签名 secrets 时只有 unsigned artifact，不作为可安装的正式 APK。

## 正式版发布顺序

1. 在 Android Studio 的 `Build → Generate Signed Bundle / APK → APK → Create new` 生成并备份正式 keystore；文件放在仓库外，密码保存在密码管理器中。
2. 在仓库 `Settings → Environments → release` 配置保护规则和上述四项 secrets。Base64 只是 keystore 的编码，仍属敏感材料。
3. 检查待发布源码与版本：当前 Android 为 `versionCode=1`、`versionName=0.1.0-preview`。核对远端没有已发布的同名 tag，发布 commit 必须包含此次范围与文档整理。
4. 构建正式签名 APK 并完成发布前设备验收，核实隐私说明与支持渠道。Release 只允许 HTTPS；不将开发服务器的明文 HTTP 当作正式同步入口。
5. 为已验证 commit 创建 `v0.1.0` tag 并发布 GitHub Release。发布说明包括本版能力、提醒未实现、后台可靠性验收延期和首次切换签名前备份的提示。
6. 当前 `.github/workflows/ci.yml` 在 Release **published** 时触发，需通过环境保护规则；四项 secrets 齐全时自动签名并验签。下载 `suirenx-v0.1.0-signed-apk` artifact，复核后上传 APK、SHA-256 与证书摘要到 Release assets。workflow 当前不会自动上传 Release assets，构建产物保留 90 天。
7. 检查实际下载链接、安装包版本与签名；记录发布 commit、证书指纹与校验和。完成后再更新 TODO 与历史记录为已发布。

本次目标是 Android APK 分发，不自动部署服务端。若同时首次生产上线 Go 服务，必须先按 AGENTS.md 切换为不可变、追加式编号迁移规则，并准备升级与回滚测试；不得继续修改已投产的 001 基线。
