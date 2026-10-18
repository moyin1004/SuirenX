# 发布准备

维护者于 2026-10-03 确认 Android v0.1.0 正式发布验收完成。以下签名、安装、隐私与分发步骤保留为操作参考；范围见 [本版需求](prd/v0.1.0.md)。阶段 A 后台可靠性补验与阶段 C 提醒功能已顺延，不属于 v0.1.0 发布阻塞项。

## v0.1.3 发布范围与门禁

本版已于 2026-10-18 完成 [GitHub Release](https://github.com/moyin1004/SuirenX/releases/tag/v0.1.3)、Android `0.1.3 / 1000601` 签名附件与服务端迁移 003 部署。以下条目是已执行门禁及保留的验证边界，证据见 [执行记录](history/roadmap-execution.md#v013-正式发布与配套部署)。

2026-10-18：维护者授权发布 `v0.1.3`（非 `v0.1.13`），执行 review/提交、最终门禁、真机升级验收、服务端配套部署和 Android 签名发布。Android/iOS 产品版本统一为 `0.1.3`。本次 GitHub Release 分发 Android 签名 APK，包含 iOS/iPadOS 源码；iOS 继续本机签名安装，不把公开 IPA、TestFlight 或 App Store 分发作为本次门禁。

- 最终源码须通过 Go 全量测试、两个 Proto descriptor、Android 相关单测/升级与 UI 回归、Debug/Release 构建、Swift 核心真实 HTTP 测试、通用 Xcode 构建及 `git diff --check`。
- 实体 Android/iPad 保留原数据覆盖安装并核对实际包版本、启动与界面。当前实体 Android 使用 Debug 签名；正式 v0.1.2→v0.1.3 同签名升级使用隔离设备验收，不卸载个人设备应用以切换签名。
- 服务端新增 `003_asset_sale_price.sql`，上线前必须备份数据库并验证隔离副本升级。旧二进制不接受迁移 003，因此失败回退需要保留迁移后现场，并配套恢复部署前数据库；不可只换旧二进制或删除迁移历史。
- 未升级服务器时，含卖出价（包括零元）的固定批次在上传前被拦截；本地数据与批次保留，但该客户端的后续上传/拉取也会等待。清空本地输入不会改写已冻结批次。先升级服务端，再分发客户端。
- 签名产物须独立核验包版本/构建号、干净源码身份、证书连续性、签名、zipalign 和 SHA-256；验证通过后附加至 GitHub Release。实体 iPhone、后台长期可靠性及既有 Web 补验如实保留边界。

## 版本策略

- Android/iOS 的产品版本统一读取仓库根目录 `version.xcconfig` 的 `MARKETING_VERSION`，当前开发版本为 `0.1.3`；发布时只手动调整这一项。开发版本号变更不代表已发布。
- 本地开发打包运行 `python3 scripts/package.py android` 或 `python3 scripts/package.py ios`。每次调用在忽略的 `.suirenx-build/package-counter` 中加一，两个平台共用此 checkout 的开发序列（从 4 开始）；文件锁防止并发重复，计数状态独立于编译输出，常规 clean 不会重置编号。失败也占用编号，不回收。不要删除计数文件；新电脑/新 checkout 的本地号不保证唯一，结合 Git 与源码摘要区分。
- 普通 Android Studio / Xcode Build 复用本地最近分配的编号（首次为 4），不消耗新编号；会重新采集 Git 提交、未提交状态、源码摘要与 UTC 时间。脚本在忽略的 `.suirenx-build/development.xcconfig` 中同步最近编号，避免打包后再用 IDE 编译退回较低构建号。需要新编号和可保留的安装包时使用打包脚本。
- 正式 Android 包由 GitHub Actions 的现有 Release APK workflow 分配独立编号：`1000000 + (GITHUB_RUN_NUMBER - 1) * 100 + GITHUB_RUN_ATTEMPT`。尝试号仅允许 1–99；为下一次运行保留间隔。开发号限制在 1000000 以下；两个渠道版本号相同，构建号不同。旧运行在已有更新运行时禁止重试，改为新建 workflow run；串行发布构建避免完成顺序反转。编号允许跳号，不要求每次加一。
- 新规则随包含打包脚本的 tag 生效。重建 v0.1.2 等历史 tag 时，应运行该 tag 上固化的历史 workflow，保留当时的版本机制；新版 workflow 对不含脚本的 tag 明确失败，不改写历史源码或 tag。
- 重建 `v0.1.3` 可从当前 main workflow 指定 `release_tag=v0.1.3`：工具从工作流自己的不可变提交读取到 `RUNNER_TEMP`，编译与身份逻辑仍来自干净的目标 tag。资源校验按 aapt 表定位实际构建 JSON，支持 Release 优化后的文件路径；缺失、歧义或内容差异仍拒绝交付。随包的 `suirenx-release-tooling.json` 分别记录工具工作流提交与应用源码提交，不通过移动 tag 修复发布工具。
- Android 的 `versionCode` 使用上述整数；iOS 为满足三段数字格式，将同一逻辑构建号 N 映射为 `(N / 10000 + 1).(N / 100 % 100).(N % 100)`（整数除法），例如 N=4 对应 `1.0.4`，N=1000001 对应 `101.0.1`。包内 JSON 保留原始整数编号。两端不需要手改构建号。
- 每个包内包含 `suirenx-build.json`（Android 为 raw 资源），记录产品版本、构建号、完整 Git SHA、是否含未提交改动、源码 SHA-256 摘要和 UTC 时间。关于页仅在开发包显示「代码」（7 位 SHA，修改中附 `-dirty`）与「打包时间」（设备本地时区，精确到分钟）；正式包只显示版本号 / Build 号。
- 打包脚本先冻结身份，再编译；源码发生变化时拒绝交付。记录保存在忽略的 `build/packages/<平台>-<编号>/`。`dirty` 和摘要只能识别源码状态，不等同于保留未提交源码快照；要重现开发包，应先提交代码。正式打包要求干净工作区、tag 与产品版本一致且指向 HEAD。
- 本地脚本生成 Android Debug APK / 未签名 iOS 模拟器 app；iOS 签名、Archive、App Store 上传仍沿用独立发布门禁，本轮不新增 iOS 云端签名流程。未来 iOS 发布任务应复用同一 GitHub workflow 的编号分配规则，不另开独立计数器。Debug 与正式签名不同，版本号机制不改变原有签名和升级限制。
- Go 服务与 Android 客户端分别标记版本。Proto/HTTP JSON 变更保持向后兼容；破坏性变更需要先定义迁移和双版本支持计划。
- Git tag 使用 `vMAJOR.MINOR.PATCH`。开发构建可带预发布后缀，正式 tag 不复用。
- 变更数据库时遵守 [数据库迁移规则](database-migrations.md)，升级路径须在发布前验证。

## Android 签名与构建

- 本地 Debug 使用默认调试签名；禁止把 keystore、密码、`local.properties` 或签名配置提交到 Git。
- 发布签名材料由维护者保存在受控密钥管理设施中。CI 发布任务只通过受保护的环境 secret 注入，不在 pull request 工作流中签名。
- Release workflow 使用名为 `release` 的 GitHub Environment，并接受四个 Environment secrets：`SUIRENX_ANDROID_KEYSTORE_BASE64`、`SUIRENX_ANDROID_KEYSTORE_PASSWORD`、`SUIRENX_ANDROID_KEY_ALIAS` 和 `SUIRENX_ANDROID_KEY_PASSWORD`。环境已限制为 `v*` tag 与 `main` 分支；密钥与密码在仓库外交付维护者备份。四项均未配置时只生成 unsigned artifact；配置不完整时任务失败；四项齐全时额外校验 zipalign、签名并通过 `apksigner verify`，再上传 APK、SHA-256 与证书摘要。2026-09-30 已按维护者授权生成持久签名密钥并配置四项 Environment secrets；v0.1.0-preview 已验证签名构建，v0.1.1 发布仍需复核新的 Android 版本产物。
- 配置时重新核验 GitHub Environment、保护规则与 secrets 状态；过去查询结果见 [历史记录](history/roadmap-execution.md)，不据此推断当前远端状态。
- 发布前生成并保管签名证书指纹，测试同一签名的升级安装、冷启动、备份/恢复，以及 Android 支持的最低和目标版本。
- 生成 APK/AAB 后记录版本、Git commit、SHA-256 和签名证书指纹；分发渠道和回滚操作由发布者确认。

## 隐私说明待确认项

已由代码确认：资产和用品的主要数据保存在 Android 本机 Room；本机 JSON 备份包括资产、归档记录和用品，不包括登录凭据；启用同步并显式登录后，客户端会将待同步记录发送到用户配置的服务器。服务端保存账号凭据哈希、同步记录和用于版本化同步的元数据；登录/同步使用 bearer JWT。

Android Auto Backup 默认包含 SharedPreferences；登录 token 保存在 `auth.xml`。当前 Android 11 及以下与 Android 12 及以上的备份规则均排除 `auth.xml`，保留其他应用数据备份。既有 Debug / unsigned Release APK 的资源检查见历史记录；云备份和设备转移仍需在支持设备上实测。[Android Auto Backup 官方文档](https://developer.android.com/identity/data/autobackup)

本节是隐私政策核对底稿，列出发布时需要核实的信息。维护者已确认 v0.1.0 正式发布验收完成；未来修改或重新发布时仍应复核运营主体、联系渠道、适用地区、服务器运营者、日志保留、备份删除、账号/同步数据删除、第三方依赖、用户权利请求入口及政策生效日期。本节本身不是面向用户的最终隐私政策。

## 分发前检查（v0.1.0 正式验收已完成）

- [x] 维护者身份、支持渠道、服务器域名与 TLS 已确认。
- [x] 签名密钥、应用商店/分发账号和恢复流程已配置且未进入仓库。
- [x] 支持设备、Android 版本、权限用途和备份行为已实测。
  - 当前代码声明 `minSdk=26`、`targetSdk=36`，Manifest 仅请求 `INTERNET`。提醒的通知权限尚未实现，届时需要补充用途说明与授权/拒绝路径验收。
  - Auto Backup 的凭据排除规则已在 Debug 和 unsigned Release APK 中检查。实体设备云备份、设备转移、干净安装/升级的正式验收已由维护者确认完成（2026-10-03）；本文件未收录逐设备测试记录。
- [x] 隐私政策已由运营者核实并发布，且应用内可访问。
- [x] 正式签名 APK 的干净安装与同签名覆盖安装通过；首次发布无上一正式版本，后续版本必须验证从上一发布版升级。Debug 与正式签名不同，不能直接覆盖；切换前先导出 JSON 备份。
- [x] GitHub Release 发布后，Release APK workflow 成功生成已签名 APK、SHA-256 和证书摘要；下载复核后附加到 Release 下载页。unsigned APK 不作为正式安装包。

## 预发布

2026-09-30：维护者要求将 dev squash 合并到 main，并创建 `v0.1.0-preview` tag 与同名 GitHub Pre-release。已创建 `release` Environment（允许 `v*` tag 与从 `main` 手动重建）并配置四项签名 secrets；此次预发布验证签名构建，设备分发验收继续保留。workflow 仍在 published 事件后构建；无签名 secrets 时只有 unsigned artifact，不作为可安装的正式 APK。

## 正式版发布顺序（历史操作参考）

1. 在 Android Studio 的 `Build → Generate Signed Bundle / APK → APK → Create new` 生成并备份正式 keystore；文件放在仓库外，密码保存在密码管理器中。
2. 在仓库 `Settings → Environments → release` 配置保护规则和上述四项 secrets。Base64 只是 keystore 的编码，仍属敏感材料。
3. 检查根目录 `version.xcconfig` 的产品版本。核对远端没有已发布的同名 tag，发布 commit 必须包含此次范围与文档整理；构建号由 workflow 分配，不手动修改。
4. 构建正式签名 APK 并完成发布前设备验收，核实隐私说明与支持渠道。Release 只允许 HTTPS；不将开发服务器的明文 HTTP 当作正式同步入口。
5. 为已验证 commit 创建 `v0.1.0` tag 并发布 GitHub Release。发布说明包括本版能力、提醒未实现、后台可靠性验收延期和首次切换签名前备份的提示。
6. 当前 `.github/workflows/ci.yml` 在 Release **published** 时触发，需通过环境保护规则；四项 secrets 齐全时自动签名并验签。下载 `suirenx-v0.1.0-signed-apk` artifact，复核后上传 APK、SHA-256 与证书摘要到 Release assets。workflow 当前不会自动上传 Release assets，构建产物保留 90 天。
7. 检查实际下载链接、安装包版本与签名；记录发布 commit、证书指纹与校验和。完成后再更新 TODO 与历史记录为已发布。

本次目标是 Android APK 分发，不自动部署服务端。若同时首次生产上线 Go 服务，必须先按 AGENTS.md 切换为不可变、追加式编号迁移规则，并准备升级与回滚测试；不得继续修改已投产的 001 基线。
