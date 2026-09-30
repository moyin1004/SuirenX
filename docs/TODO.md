# 当前待办

本文件只保留尚未完成或需要明确外部条件的事项。已经完成的阶段记录、验证证据和历史决策统一保留在[路线图执行检查点](roadmap-execution.md)；数据与同步规则见[数据与同步](data-sync.md)。

## 阶段 A：可靠性收尾

- [ ] 完成后台长期调度、进程被系统回收后的恢复与 Doze/厂商省电策略验收。
  - 2026-09-24：WorkManager Android 仪器测试在 API 36 AVD 与 Android 16 实体设备各 2 项通过，验证同步周期偏好在 Scheduler 重建后恢复、周期恢复任务保持唯一、按修改任务合并且切换到周期模式后不再新排按修改任务。测试使用唯一任务名和仅测试生效的计量网络约束，避免在测试 APK 中执行 Hilt Worker；未等待周期 worker 执行。
  - 2026-09-24：新增两阶段隔离测试；第一阶段退出后确认测试进程已结束，第二阶段 PID 变化且 WorkManager 周期任务记录和周期偏好仍持久化，Scheduler 初始化后任务仍唯一。AVD 与 Android 16 实体设备各 1/1 通过。该试验没有执行生产 App worker。
  - 2026-09-24：另用 15 秒延迟的隔离 Worker 探测 instrumentation 进程退出后的后台执行。AVD 观察到完成标记；实体设备约 30 秒后仍未完成，尽管系统已登记调度任务。该 Worker 只写测试标记，不代表生产同步执行；实体设备后台行为尚未确认。
  - 2026-09-24：在 API 36 AVD 的本地模式下，断网排入生产 `LocalSyncWorker`，确认 WorkManager 状态为 `ENQUEUED`；通过应用 UID 结束后台进程后，PID 消失且任务仍持久化。恢复网络后出现新 PID，生产 Worker 执行并返回 `SUCCESS`；本地模式未发起服务器请求。
  - 2026-09-28：重跑 `:apps:android:core:data:connectedDebugAndroidTest`，API 36 `medium_phone` AVD 的 XML 结果为 40 tests、0 failures、0 errors、2 skipped。跳过项是要求分两阶段重启测试进程的恢复测试；本次单次 Gradle invocation 不构成其进程恢复验收。
  - 2026-09-28：在 API 36 AVD 将隔离的 15 秒 Worker 探针分两阶段运行；强制 deep Doze 期间等待 20 秒仍无完成标记，解除 Doze 后新 instrumentation 阶段确认 Worker 完成且 WorkInfo 为 `SUCCEEDED`。这只证明测试 Worker 在 AVD 强制 Doze 下延后并恢复，不代表生产 `LocalSyncWorker`、周期任务、系统内存回收或实体设备厂商策略。
  - 2026-09-28：API 36 `medium_phone` AVD 的生产 app 处于 `StorageMode=Local`。Android ActivityManager 于 11:48:18 以 `kill background` 结束 PID 4096；WorkManager 的 JobScheduler 周期作业仍保留。11:59:48 系统以新 PID 4556 启动 `SystemJobService`，生产 `LocalSyncWorker` 返回 `SUCCESS`，随后注册下一周期作业。此为一次 AVD ActivityManager 后台终止后的生产周期恢复证据；本地模式分支未调用远端同步。
  - 2026-09-28：同一 API 36 `medium_phone` AVD 保留 Local/OnChange 设置并正常启动 app 后按 Home 退到后台；12:14:48 与 12:30:11 两次生产 `LocalSyncWorker` 均返回 `SUCCESS`，第二次发生在应用后台约 15 分钟后，JobScheduler 随后登记下一周期。两次运行使用同一 app PID 3067；这补充了单次后台周期执行证据，但不证明进程回收、长期稳定性、LMK 或实体设备/OEM 策略。
  - 2026-09-28：在相同 Local/OnChange AVD 上再次用 `am kill` 结束后台 PID 3067；`ApplicationExitInfo` 记录 `USER REQUESTED / KILL BACKGROUND`，当时 PID 消失且 JobScheduler 作业仍在。12:46:07 系统以新 PID 4211 启动 `SystemJobService`，生产 `LocalSyncWorker` 返回 `SUCCESS`，随后进程被冻结、下一周期仍登记。此为受控后台终止后的生产任务恢复，不是 LMK 自动回收。
  - 2026-09-28：在未修改已保存 AVD 配置（`hw.ramSize=2048`）的前提下，以 `-memory 1536 -lowram -no-snapshot` 启动 API 36 `medium_phone`，保留原 app 数据与 Local/OnChange 设置。向 guest 注入不可压缩内存页后，lmkd 记录 74 次 low-watermark 回收；当 swap 仅剩 224 KiB、thrashing 为 108% 时，lmkd 以 `LOW_MEMORY` 回收 SuirenX PID 3167（oom_adj 900），`ApplicationExitInfo` 原因一致。WorkManager 周期作业在进程退出时仍登记；停止临时探针、内存恢复后，13:01:41 ActivityManager 用新 PID 4786 启动 `SystemJobService`，生产 `LocalSyncWorker` 返回 `SUCCESS` 并登记下一周期。Room 偏好仍为 Local/OnChange，worker 本地模式分支在同步前返回，不会上传数据。当前源码 `:apps:android:app:assembleDebug` 成功（UP-TO-DATE）。这验证 API 36 AVD guest 中真实 lmkd 回收与生产 worker 恢复，不替代实体设备测试。
  - 2026-09-28：上述成功后再观察下一周期至 13:32:27（距成功约 31 分钟）；JobScheduler 的作业已超过最早运行时间约 16 分钟，报告 `Ready=true`、网络约束已满足、设备为 `ACTIVE`，但仍未看到第二次 Worker 启动。此单次 AVD 观测记录为周期执行延迟/尚未验证，不能据此区分系统批处理与应用调度问题。
  - 2026-09-28：复核 Android 官方 WorkManager 文档：周期间隔是重复运行的最小间隔，实际时点取决于约束和系统优化；`ExistingPeriodicWorkPolicy.UPDATE` 在周期未变时保留原入队时间。JobScheduler 显示 Ready 及单次超时都不能单独证明应用调度缺陷，因此暂不盲目改策略。当前无连接设备；尝试启动 `medium_phone` 时本机 Emulator 37.1.11 退出并报 Qt 构建需要 NEON，后续周期实测需恢复兼容的 Emulator runtime 或连接设备。
  - 2026-09-28：定位到冷启动恢复竞态：`Application.onCreate()` 在 JobScheduler 启动生产 Worker 时也会调用 `SyncScheduler.initialize()`；其周期任务使用 `UPDATE` 会替换 WorkSpec generation，造成正在恢复的 Worker 被取消并立即重启。现已将初始化改为 `KEEP`，用户明确选择周期时仍用 `UPDATE`；新增测试断言初始化保持 Work ID 与 generation。API 36 AVD 数据层仪器测试 XML 为 41 tests、0 failures、0 errors、2 skipped（两阶段进程恢复测试需单独运行），Debug APK 构建成功。重装后冷启动 generation 保持不变，13:55 与 14:10 两次生产 `LocalSyncWorker` 自然返回 `SUCCESS`。随后结束后台 PID 4668，JobScheduler 周期项仍保留；到期后约 4 分钟仍未自然启动，`Ready=true` 且约束满足。使用 `cmd jobscheduler run -n androidx.work.systemjobscheduler -s io.suirenx.app 73` 受控触发后，系统以新 PID 6029 启动生产 Worker 并返回 `SUCCESS`。结束 PID 6029 后，下一周期同样在最早时间后约 5 分钟仍未自然启动；受控触发 job 74 后，新 PID 6737 的生产 Worker 于 14:48:56 返回 `SUCCESS` 并登记 job 75。两次均为受控进程恢复，不算自然周期恢复验收；实体设备/OEM Doze 与长期稳定性仍待验收。
  - 尚未验证实体设备低内存回收、长期多周期稳定性，以及实体设备 Doze/厂商省电策略。AVD 的 LMK 证据不代表不同设备厂商的后台策略。

## 阶段 C：提醒

- [ ] 按[到期提醒实施规格](reminders-spec.md)先更新 OpenDesign，再实现用品到期通知、保修提醒和首页待处理入口。
  - 规格已明确权限、频率、去重、修改后撤销和时区边界；通知功能尚未实现。
  - 2026-09-22 产品决策：用品当前只做期限管理，不扩展库存、消耗或补货预测；提醒采用规格中的默认方案（默认关闭，用户主动开启后每日 09:00 本地时间摘要，用品复用现有临期天数）。
  - 2026-09-24：已发出 OpenDesign Cloud 原型需求卡，等待平台/流程范围/完成度选择；确认后再更新既有设计稿并实现提醒。
  - 2026-09-24：OpenDesign 0.24.0 已重新启动；本机 stdio 握手与 22 项工具发现通过，但当前任务宿主仍返回 `Transport closed`，提醒原型仍须在可用任务连接和既有 brief 选择后更新，未绕过设计确认实现提醒。
  - 2026-09-28：依据 Android 官方 AlarmManager、WorkManager 与通知权限文档，规格已确定每日使用非精确本地闹钟触发 Worker；不申请精确闹钟权限，并补充 API 33+ 通知授权与 API 26+ 通知渠道要求。设计稿与通知实现仍待 brief 选择和可用的 OpenDesign 任务连接。
  - 2026-09-28：重新尝试收集 brief 仍收到 `Transport closed`。本机已注册 0.24.0 stdio runtime，但 `/Applications/Open Design.app` 的严格签名验证失败（Authority unavailable）；依 AGENTS.md 保留提醒 UI/实现待设计，不运行或改写无效签名的应用包。
  - 2026-09-28：本轮对 OpenDesign Cloud 再次调用当前项目和项目列表只读接口，仍返回 `Transport closed`；未能恢复设计上下文或更新既有原型。
  - 2026-09-28：已从官方 0.24.1 Apple Silicon 发布包恢复 OpenDesign。本机安装包通过严格代码签名验证和 macOS notarization assessment，应用可启动；已保留原应用、用户数据和配置备份，并注册随附 web runtime。当前任务的 OpenDesign 工具仍返回 `Transport closed`，不能核验握手、工具列表、SuirenX 项目读取或待处理 brief；Codex 需要新任务加载修复后的 MCP 配置，再继续设计流程。
  - 2026-09-29：当前任务仍未加载 OpenDesign 工具；本机 `open-design` MCP 注册为启用并指向 0.24.1 runtime，但本轮无法读取项目或收集阶段 C brief。按设计工作流需在新任务加载该连接后先更新既有原型；提醒 UI 与实现继续待办。

## 阶段 D：根据反馈决定

- [ ] 根据真实使用反馈决定是否做复制记录、CSV、标签管理和位置管理；不自动扩大本轮范围。
  - 2026-09-24：本轮没有新的真实使用反馈，因此暂不把 CSV 导入导出提升为实现承诺；现有本地 JSON 备份/恢复保持现状。

## 工程 backlog

- [ ] 配置并验收 Release 签名、运营者隐私说明与分发流程。
  - 已新增 [发布准备清单](release-readiness.md)，记录版本、签名和隐私待确认项；正式签名、运营者信息、隐私政策发布与分发账号仍需维护者提供并验收。
  - 2026-09-28：Release workflow 已增加 `release` Environment secrets 的条件签名、zipalign 检查、`apksigner verify`、SHA-256 与证书摘要 artifact；未配置 secrets 时保留 unsigned artifact，部分配置会失败。Environment 保护规则、真实维护者密钥、签名安装升级、运营者隐私信息和分发验收仍未完成。
  - 2026-09-28：发现 Android Auto Backup 默认会包含保存登录 token 的 SharedPreferences `auth.xml`。已添加 Android 11 及以下 `fullBackupContent`、Android 12 及以上 `dataExtractionRules`，两者都排除该文件，同时保留其他应用数据备份。Debug 与 unsigned Release APK 均构建成功；`aapt dump xmltree` 确认两个 APK 的云备份、设备转移和旧版完整备份均含排除规则。实际云备份与换机恢复仍待支持设备验收。
  - 2026-09-28：本地 `:apps:android:app:assembleRelease` 成功且 unsigned APK 的 zipalign 校验通过；workflow YAML 与签名 shell 语法检查通过。此构建仍是 unsigned，不代表 GitHub Release runner 或真实签名验收。
  - 2026-09-28：使用仅存于 `/private/tmp` 的一次性测试 keystore，实际执行 workflow 的配置检查和签名脚本；配置分支识别、zipalign、签名 APK 生成、v2/v3 验签和 SHA-256 文件生成均通过。临时材料已清理；这只验证脚本路径，不代表真实维护者签名、APK 升级或 GitHub Release 验收。
  - 2026-09-28：又实际执行配置检查脚本的无密钥与部分密钥分支；四项空值输出 `configured=false`，仅设置一项时以错误退出并拒绝部分配置。
  - 2026-09-28：通过当前 Admin 身份只读查询 GitHub，`viewerPermission=ADMIN`，但 `/environments/release` 返回 404，说明该 Environment 尚未创建。保护审阅人、允许部署的分支和正式签名 secrets 仍需维护者确定与配置。

## 已从当前路线移除

心愿清单、独立趋势页、月度趋势图表、新工具类别和 speculative AI/OCR 不在当前路线；已有资产总览保留。已完成事项不再在本文件重复列出，详见 `roadmap-execution.md` 的阶段记录。
