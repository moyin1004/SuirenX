# 路线图执行记录

本文保留各日期的实施、测试和阻塞证据，不代表当前待办或当前环境状态。当前范围以 [版本规划](../prd/roadmap.md) 和 [TODO](../TODO.md) 为准。文中旧路径为当时记录。

2026-09-15。用户已授权按路线图实施。阶段 A 部分完成，B/C UI 与功能待设计工具恢复；D 为根据反馈决定的候选，不自动扩大范围。

## 已完成

- 复现 Go `TestM5ConcurrentEditorsAndDatabaseRestore` 失败：两个编辑者的 deferred 事务升级写锁时出现 `database is locked`，HTTP 返回 500 而非保留冲突版本。
- 数据库基础设施使用 URL 安全编码的 SQLite 文件 DSN，所有连接设置 `_txlock=immediate` 与 `_busy_timeout=5000`；在读版本前保留写锁，保留迁移校验与回滚。高争用时最多等待 5 秒，仍可能超时，不能将此修复理解为无限吞吐。
- 并发测试增加同时起跑与 HTTP 状态检查。Go 全包测试通过，该并发/恢复测试连续 20 次通过。
- 新增生产迁移链 v1/v2→v3 仪器测试，验证购入金额、退役日期、归档、备注/标签与用品数量保留，Room 验证最终结构。
- Debug 数据测试包补齐 INTERNET 与本地 HTTP 配置；账号拒绝二次登录测试补初始化步骤。未放宽 release 网络配置。
- 提醒产品规则落到 `reminders-spec.md`，尚未实现通知。

## 当前验证

- Debug APK 构建通过；历史记录中的资产 27、设置 11、数据 5 项单元测试任务通过；2026-09-23 已按文末命令强制补跑资产 32、设置 11、数据 5 项，共 48 项全部通过。
- `medium_phone` API 36 模拟器直接运行独立测试 APK：25 项全部通过，包括新增 2 项升级测试。
- Gradle connected 任务因离线缓存缺少 UTP 依赖未能运行，改用同一已编译 APK 的 AndroidJUnitRunner，结果 `OK (25 tests)`。
- asset/m5 Proto 描述符校验、`git diff --check` 通过。
- 以上 Android 同步主要使用受控 transport；健康探测测试使用设备内回环 HTTP。Go transport 验收不等于两个真实 Android 客户端联网验收。
- 未连接实体手机，未部署服务端，未改变用户运行数据库；未安装业务 APK到用户设备，未提交 Git。

## 2026-09-23：修复 Gradle 单测执行器环境并补跑

### 问题边界与修复

- 先前 `GradleWorkerMain` 失败发生在测试进程启动阶段：`Could not find or load main class worker.org.gradle.process.internal.worker.GradleWorkerMain`，不是断言失败。
- 直接使用系统 shell 时另有 `Unable to locate a Java Runtime`；设置项目既定 Android Studio JBR 后，受限沙箱首次启动又被 Gradle 本地锁服务的 `java.net.SocketException: Operation not permitted` 拦截。这两次均归类为环境失败，未进入业务测试断言。
- 复现命令统一显式设置 JBR、隔离 Gradle 缓存、停止旧 daemon，并使用已有离线依赖；没有修改业务代码、Gradle 配置或测试语义：

  ```shell
  cd /Users/bytedance/Desktop/moyin/suirenx
  JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  GRADLE_USER_HOME=/private/tmp/suirenx-gradle \
  PATH='/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin':$PATH \
  ./gradlew --stop

  JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  GRADLE_USER_HOME=/private/tmp/suirenx-gradle \
  PATH='/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin':$PATH \
  ./gradlew :apps:android:feature:assets:testDebugUnitTest \
    :apps:android:feature:settings:testDebugUnitTest \
    :apps:android:core:data:testDebugUnitTest \
    -Pkotlin.compiler.execution.strategy=in-process --offline --no-daemon --rerun-tasks

  JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  GRADLE_USER_HOME=/private/tmp/suirenx-gradle \
  PATH='/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin':$PATH \
  ./gradlew :apps:android:app:assembleDebug \
    :apps:android:app:createDebugApkListingFileRedirect \
    -Pkotlin.compiler.execution.strategy=in-process --offline --no-daemon --rerun-tasks
  ```

### 真实结果与验收边界

- 单测命令实际执行成功：资产 32 项、设置 11 项、数据 5 项，共 48 项；`skipped=0`、`failures=0`、`errors=0`，未再出现 `GradleWorkerMain`。
- Debug 构建命令实际执行成功：`BUILD SUCCESSFUL`；`assembleDebug` 与 `createDebugApkListingFileRedirect` 均完成。仅有 AGP/Kotlin 弃用及 native strip warning，没有断言或编译失败。
- 本轮未运行 connected/instrumentation 测试、未新增模拟器证据，也未连接实体设备或部署远程服务。既有 API 36 模拟器记录仍明确只是模拟器验收，不能写成真机、后台长期调度、历史安装库或远程部署验收。

## 阻塞与恢复入口

- OpenDesign MCP 项目列表返回 `Transport closed`。
- 已确认安装和 MCP 注册；`codesign --verify --deep --strict` 对本机 Open Design.app 返回 `invalid signature (code or signature have been modified)`。未启动该签名无效的应用，也未下载/重装。
- 已请用户修复可信安装。遵循 AGENTS.md：设计阻塞时记录差异，依赖设计的 UI 保持待办。不能用手写替代原稿绕过。
- 没有可验证的目标部署入口，也没有两台实体设备，真实双端、后台持续运行和历史安装数据库验收仍未完成。

## 初始后续计划（最新进度见文末）

1. 恢复 OpenDesign 后读取原 `suirenx-asset-redesign.html`，先更新 A：同步/冲突直达设置、读取错误、购入总额统计口径，以及用品真实能力文案。
2. 对齐 Android 并验证导航返回、空/错误状态与统计；保留当前唯一账号和服务器独立规则。
3. 更新设计后实施 B：稳定刷新、表单/日期/离开保护、排序组合筛选和用品搜索。
4. 依据 reminders-spec.md 核对 Android 官方文档，更新设计再实现 C：用品通知、保修提醒与待处理入口。
5. 获得真实环境后补双端/后台/历史库验收和目标实例部署验证。CI 与发布工程门禁继续待做。

本轮涉及数据库与新增测试/规格。工作树中已有账号、地址、同步及文档改动为进入任务时存在的用户工作，继续保留；本轮仅在 AccountFlowTest 的既有修改上补了一行初始化。

## 2026-09-22：主页面切换稳定性与待办清理

- 主容器统一持有资产和用品 ViewModel，避免底部 Tab 切换重建页面状态并短暂显示初始 loading 文案。
- 资产和用品刷新改为保留已成功读取的内容；只有首次读取显示 loading，后台刷新通过独立状态表达，不再把已有文字替换为“读取中…”或“—”。
- 新增资产刷新期间保持旧内容可见的 ViewModel 回归测试；其余验证以本轮构建结果为准。
- `docs/TODO.md` 已收敛为当前未完成事项，重复的历史完成记录移至本文件的阶段记录，不再与 `data-sync.md` 和提醒规格重复维护。

## 2026-09-22：阶段 B 未闭环项收口与设备验收

- 在既有 OpenDesign 原稿和既有 `suirenx-asset-ui-redesign` 项目基础上完成本轮实现，不另起一套 UI。阶段 B 的资产/用品表单、日期选择器、日期关系校验、未保存退出确认、资产排序/标签筛选和用品搜索均已落到 Android 原生页面。
- API 36 `medium_phone` / `emulator-5554` 已安装最新 Debug APK。连续切换总览、资产、工具页面后，已加载空态和业务文案持续存在，没有回落到“正在读取”或占位文本；这对共享 ViewModel 的文字闪烁修复完成了设备级验收。
- 设备语义验收确认：资产表单可打开购买日期选择器，填写临时内容后关闭会显示“放弃未保存修改？”；用品详情可打开包装到期日选择器；临时资产/用品均未保存，列表仍为空。
- 先前单测执行被临时 Gradle 缓存缺少 `gradle-worker.jar` 阻塞；补齐同版本缓存后，`:apps:android:feature:assets:testDebugUnitTest` 执行通过，用品模块无单测源码因而为 `NO-SOURCE`。资产/用品 Kotlin 编译、`assembleDebug` 和 `git diff --check` 均通过。
- Local Codex 预览已恢复：在原稿 Studio 中实际打开设置页、资产列表、资产表单和购买日期选择器；预览截图与语义树均确认页面正常渲染、文字保持水平、日期入口可操作、未保存修改确认可达。仍未执行 PDF/图片导出，但这不影响 HTML 原型与 Android 设备验收。

## 2026-09-22：阶段 B 表单与检索体验

- 沿用既有 OpenDesign 项目 `SuirenX 资产管理 UI 重设计` 和入口稿 `suirenx-asset-redesign.html`，在原视觉语言上补齐资产/用品分组表单、日期入口、日期关系校验、未保存修改确认，以及首次读取、刷新中、读取失败、空结果状态。
- Android 资产表单增加日期选择器；购买日期禁止未来日期，保修截止日不能早于购买日期。用品表单增加包装到期日/开封日选择器，保留开封日与有效天数成对规则，并拒绝开封日晚于包装到期日。
- Android 资产列表增加购买日期、金额、日均成本、持有天数排序和升降序切换；状态筛选与多标签筛选可组合，仍在本机列表上完成，不触发重复读取。
- Android 用品列表增加名称、分类、位置搜索和清除入口；搜索、排序、筛选期间保留本机数据口径，不引入库存、补货预测或估值含义。
- 资产和用品表单离开时，系统返回、关闭和取消都会在有修改时进入“继续编辑 / 放弃修改”确认；保存成功直接返回。
- 验证：`assembleDebug`、资产/用品 Kotlin 编译通过；新增资产日期校验与排序/标签组合测试已编译。Gradle 单测执行仍被环境缺失 `worker.org.gradle.process.internal.worker.GradleWorkerMain` 阻塞，不是断言失败；实体设备验收尚未在本轮重跑。

## 2026-09-16：OpenDesign 启动链修复

- 已将 `/Applications/Open Design.app` 从 0.21.1 替换为本机官方更新目录中签名校验通过的 0.22.2，并执行新版 `--headless --mcp-install codex` 重新注册。
- 修复前后台报 `SidecarFactory.create() requires a supervised sidecar context` 并退出，Codex MCP 30 秒启动超时。新版注册使用受监督服务端点；修复后直接 stdio 握手、22 项工具发现、原项目和文件列表读取均成功，启动后签名复查通过。
- 旧应用、Codex 配置和项目数据备份在 `/Users/bytedance/Library/Application Support/OpenDesign-repair-20260916`。三个原设计文件与备份 SHA-256 一致。
- 插件/技能仍为官方当前 0.5.3，无需升级。当前任务没有热加载恢复后的工具，下一任务加载 MCP 后继续阶段 A 设计更新。
- 阶段 A 已补资产/用品创建及更新时间戳预览校验，避免损坏时间戳直到恢复阶段才报错。Debug 和测试 APK 编译通过。
- 2026-09-16 在 API 36 模拟器 emulator-5554 安装独立数据测试包并直接运行 AndroidJUnitRunner：`OK (28 tests)`。新增三项验证：响应丢失后恢复再重试仍复用旧批次且不覆盖恢复版本；恢复中途插入失败同时回滚资产、用品及同步日志并留下有效安全备份，重试成功保留用品归档与空位置；无效时间戳在预览与恢复时均被拒绝且数据不变。
- 此轮没有安装业务 APK；同步仍采用受控 transport，真实双设备 HTTP、后台调度、真实历史安装库及目标服务器部署验收仍待完成。

## 2026-09-17：阶段 A 页面实现与导航验收

### 原稿与实现

- 使用既有 Local Codex 工作流更新原项目 `suirenx-asset-ui-redesign` 的 `suirenx-asset-redesign.html`。运行 `f3b6b131-d3cf-4b84-bea4-608cf48faa3c` 终态 succeeded，实际产出 1 个 HTML；已读取产物，并在浏览器验证冲突入口、返回总览和独立本机读取错误页面。
- 原生总览根据真实同步状态显示待发送、网络等待、认证失败、同步失败或冲突入口；资产与用品冲突合计后直达冲突页，其余直达同步页。设置支持上下文入口和返回原总览，保留主导航状态。
- 资产读取失败始终显示“无法读取本机数据，请重试”，不会因存在同步冲突而被隐藏；重试只读取本机。新增回归测试覆盖失败与冲突并存、读取恢复后冲突保留及没有触发上传。
- 总览/资产页改为“购入总额”“未归档资产购入总额”“服役中日均成本”；未改变金额算法。用品页明确记录期限、在应用内查看临期与过期项目，数据来源显示“本机数据”。

### 验证证据

- Debug APK 构建通过；资产模块 28 项、设置模块 11 项单元测试全部通过。最后一次构建日志：`/private/tmp/suirenx-phase-a-build.log`。
- 在 API 36 `medium_phone` 模拟器覆盖安装 Debug APK，保留已有数据。用合成资产 `PhaseA-Nav-Test12.34`、金额 12.34 元验证：资产页和总览统计正确；首页出现 1 项待发送；点击进入“同步未开启”；顶部“总览”和 Android 系统返回均回到总览且金额/待发送状态保留。
- 设备语义读取确认用品页显示“本机数据”和应用内期限说明。验收记录随后通过应用正常删除，资产列表恢复 0 项；按同步协议保留删除 tombstone，未登录、未开启同步、未上传测试数据。此轮没有实体设备验收。
- 原生冲突入口代码已完成，但设备没有冲突状态，因此未声称完成原生冲突页往返、长列表滚动恢复或真实冲突解决验收。浏览器原型交互与 ViewModel 测试不能替代这些设备验收。

### 尚未完成与恢复位置

- 原稿仍需补“包含未归档的已退役资产”的明确统计说明；提示中的冲突示例数量与列表不一致，归档示例也未驱动列表/统计更新。待原稿修正后再同步依赖的 Android 说明，不将生成成功视为全部设计验收通过。
- 本轮原稿生成后 OpenDesign MCP 工具不可用；直接 MCP 启动诊断报缺少 `@open-design/sidecar`，严格代码签名检查再次失败。此前 09-16 的修复成功记录仅代表当时状态，当前需恢复可信安装及 MCP 后继续原稿修正。
- 真实双设备 HTTP、后台调度、真实历史安装库、目标实例用品空位置修复部署仍未验证。阶段 A 保持部分完成，尚未进入 B/C 实现。
- 所有既有账号、服务器、数据与 Go 修改保留；本轮未提交 Git。

## 2026-09-18：OpenDesign 再次修复与冷启动验收

- 诊断区分两项问题：`/Applications` 副本中 18 个依赖包被 pnpm 移至 `.ignored`，462 个缺失文件哈希全部匹配签名清单；自动更新副本内另有 18 个新增 Next.js 磁盘缓存文件。pnpm 11 的执行前自动安装行为已在临时目录复现。没有历史父进程审计，未声称确定当时的具体触发调用。
- 使用官方 0.22.2 缓存安装包恢复，SHA-256 与官方在线校验和一致，Apple 公证与完整签名通过。损坏更新副本移出活动版本目录，正式启动入口统一为 `/Applications/Open Design.app`，旧备份应用取消 LaunchServices 注册。
- 保留原签名代码：通过应用已有环境配置，将网页运行目录复制至 `~/Library/Application Support/Open Design/local-repair/web-0.22.2`；用户 LaunchAgent 在登录时设置两个 OpenDesign 专用变量。MCP 单独配置 `pnpm_config_verify_deps_before_run=false`，启动参数传递相同防护及外置网页目录，其他 Codex/MCP 配置保持原样。
- 验收：原项目预览正常；停止后台后由 MCP 冷启动成功；握手、22 个工具、原项目和文件读取通过；本任务直接调用 OpenDesign `list_files` 成功；预览与冷启动后严格签名仍通过，5 个原项目文件哈希未变。
- 此修复是针对 0.22.2 的本机绕行配置，不是厂商源码修复；将来升级需复查外置网页版本与应用版本一致，并重新验证签名与 MCP，不能直接假定新版本仍适用。
- 2026-09-18 已在主原稿 `suirenx-asset-redesign.html` 实际补齐统计口径、冲突数量和归档恢复交互；通过浏览器直接加载验证无脚本错误，归档筛选 → 详情 → 恢复会更新列表与统计，冲突状态统计为 3 项并与冲突列表一致。本轮未改 Android 或业务数据边界。诊断与验收记录保存在 `~/Library/Application Support/Open Design/local-repair/repair-verification.json`。

## 2026-09-18：阶段 A 原生设备收尾验收

- API 36 `medium_phone` / `emulator-5554` 直接运行数据层 AndroidJUnitRunner：`OK (28 tests)`；覆盖冲突保留双方、删除与远程编辑冲突、丢响应重试、恢复回滚与安全备份等受控 transport 场景。
- 最新 `app-debug.apk` 已构建并安装。原生 UI 启动成功；总览显示本机统计与待发送状态；设置 → 同步设置可达，页面显示“资产和用品始终存本机”；系统返回键从同步设置回到设置页。
- 本轮没有人为注入真实冲突状态，因此不把原生冲突直达、冲突解决往返和长列表滚动位置恢复写成已验收；真实双设备 HTTP、后台调度、历史安装数据库和目标实例部署仍待真实环境。

## 2026-09-21：真实双模拟器 HTTP 双端同步验收（部分完成）

### 环境与证据边界

- 使用本地 Go 服务、`10.0.2.2:8888` 模拟器地址、合成账号和合成数据；服务使用 `/private/tmp` 隔离 SQLite 数据库。两端是同一 `medium_phone` AVD 的两个只读实例：`emulator-5554` 与 `emulator-5556`，不是实体设备。
- Debug APK 已安装到两端并清空为新安装数据后验收。服务健康检查、账号注册/登录、首次同步和后续手动同步均通过；本记录不代表远程部署、用户运行数据库或历史安装库升级。

### 已实际覆盖

- A 端新增 `dual-asset-A`，B 端真实拉取；B 端编辑为 `dual-asset-B-edited` 后 A 端拉取；A 端删除后 B 端拉取，Room 版本、同步游标、批次和跨端列表均确认删除 tombstone 生效。
- 以共同版本的合成资产制造三次同记录并发编辑。先提交的一端形成服务端新版本，另一端收到真实冲突并进入原生冲突页：
  - 第一次选择“保留本机”，B 端本机版本被发送为新版本，A 端随后拉取；
  - 第二次选择“保留另一端”，冲突页确认后两端收敛到远端版本；
  - 第三次选择“两份都保留”，原生确认文案为创建修改版副本；B 端 Room 中出现 `conflict-B3（副本）` 与原记录，原记录和副本的版本/dirty 状态符合后续发送。
- B 端创建用品 `expiry-empty-location`，不填写可选存放位置；详情页没有位置占位文本，数据库 `location` 为空。B 端提交后 A 端真实拉取，A 端原生详情仍为空，确认没有伪造位置值。
- A 端在断网状态提交用品编辑，原生同步页进入“重试同步”；断网期间数据库保留 `dirty=1` 和冻结批次（实测批次长度 493）。强制停止并冷启动后，待发送入口仍存在；恢复网络并点击同步后批次清空、记录变为 `dirty=0`，用品名称更新为 `expiry-offline-retry`，空位置保持不变。
- A 端通过 Android 系统文件保存器导出本机 JSON 到 Downloads；将导出文件转入 B 端 Downloads 后，B 端通过系统文件选择器打开并进入原生“恢复预览”。预览显示 2 项资产、1 项用品，提示文件校验通过、确认后替换本机数据且恢复前自动备份。
- B 端确认恢复后原生页面显示“已恢复 2 件资产、1 件用品”；B 端原有合成资产 `restore_sentinel_B` 从列表移除，`conflict-B3（副本）`、`conflict-A3` 与 `expiry-offline-retry` 恢复。强制停止并冷启动后数据仍在；`app_backups/pre-restore-*` 已生成，内容包含恢复前的 sentinel 且不含 token/password/auth 字段。

### 仍未覆盖

- 后台长期调度、进程被系统回收后的调度恢复和省电策略未作持续运行验收；本轮的冷启动证据是显式重试冻结批次，不是后台调度验收。
- 未连接实体设备；未使用已安装历史数据库验证升级；未在远程或目标实例部署服务；未覆盖损坏文件或恢复事务失败时的原生 UI 链路。
- 因本轮没有发现可复现的实现缺陷，没有修改业务代码，也没有新增回归测试。Go 全包、Proto 描述符和 `git diff --check` 通过；验收前构建的 Debug APK 已安装到两端并完成上述真实链路。Android Gradle 单测重跑在测试进程启动阶段被环境错误阻塞：找不到 `worker.org.gradle.process.internal.worker.GradleWorkerMain`，不是断言失败，待修复测试执行器/缓存后补跑。阶段 A 总项保持部分完成。

## 2026-09-23：阶段 B 第一批搜索入口校正与表单回归

- 既有 OpenDesign 主原稿 `suirenx-asset-redesign.html` 已在 Local Codex 中继续修改：资产列表默认只显示右上角搜索按钮；资产排序改为标题行内轻量下拉，不再占独立整行或使用厚边框；用品列表同样默认隐藏搜索输入，点击后才展开紧凑搜索行。Studio 预览实际点验了资产/用品默认态、搜索展开/收起和资产排序菜单。
- Android 原生同步该交互：资产列表复用现有 `LazyListState`，搜索/排序/刷新仍在本地状态和 Room 读取边界内；用品列表改为按需展开搜索并保留原刷新入口。未改变 Room 数据模型、同步边界或远端 API。
- 资产原有日期选择、手输日期关系校验、未保存离开保护、保存失败保留输入与刷新旧内容保持不变；用品补齐严格日期手输校验的纯函数和最小回归测试，覆盖无效格式、开封日关系与成对字段规则。
- 验证：`:apps:android:feature:assets:testDebugUnitTest` 与 `:apps:android:feature:expiry:testDebugUnitTest` 通过；`:apps:android:app:assembleDebug` 通过；`git diff --check` 通过。Debug APK 已安装到 `medium_phone` 并启动，Activity 前台运行且无崩溃日志，但本次模拟器无障碍树仍返回启动器节点，因此不把原生搜索/排序点按写成设备级完成。

## 2026-09-23：工具页与设置页切换闪烁修复

- 根因定位为内外层 `NavHost` 虽已禁用进出场动画，但仍使用 Navigation 2.10 的默认 `sizeTransform`；工具页“把日常工具放在一起”和设置页标题在 Tab 切换时会经历一次尺寸变换，表现为文字闪烁。
- 在 `MainActivity` 的外层与 Tab 内层 `NavHost` 统一设置 `sizeTransform = { null }`，不改变 Room、同步边界、Tab 状态保存或页面数据逻辑。
- 验证：修复后 `:apps:android:app:assembleDebug` 通过；Debug APK 已重新安装并启动到 `medium_phone`。模拟器无障碍树当前仍无法读取 Compose 页面节点，因此连续切换后的视觉验收保留在 TODO，未过度宣称设备级完成。

- 2026-09-24 追加检查：API 36 `medium_phone` AVD 实际切换并查看工具页、设置页截图，两页均有完整可见内容；屏幕录制无法在本机工具链可靠抽帧，不能据此确认切换瞬间无闪烁。实体设备验收仍开放。

## 2026-09-23：资产排序控件对齐 OpenDesign

- 依据现有主稿实际预览，将 Android 资产列表排序区从裸文字/分离箭头调整为浅灰圆角胶囊：当前排序字段、下拉箭头与升降序切换合并为一个操作组；列表行移除重复刷新按钮，页面顶部刷新入口保持不变。
- 未改变 `AssetSort`、升降序逻辑、筛选组合、Room 数据来源或同步边界。
- 验证：资产 feature 单测通过，`:apps:android:app:assembleDebug` 通过，`git diff --check` 通过。

## 2026-09-24：工程待办与构建迁移

- README 补充注册与增量同步示例，并澄清 JWT 注销为本地会话结束确认，不会即时撤销无状态令牌。新增发布准备清单，记录版本、签名、隐私信息和分发流程的维护者确认边界。
- 新增 GitHub Actions：Go 全包测试与 `go vet`、资产和 M5 Proto 描述符校验、Android data/assets/expiry 单测、app lint 与 Debug APK 构建。远端 runner 尚未实际执行，需观察首次 CI 运行。
- Android 完成 AGP 9 内置 Kotlin 与 KSP 迁移：移除 `org.jetbrains.kotlin.android`、kapt 及 Gradle 兼容开关；Hilt/Room 处理器使用 KSP 2.3.12。同步更新 AGENTS.md 构建约束。
- Go API 增加校验过的 `X-Request-ID`、错误响应 `error/code/request_id` 字段、JSON 结构化请求日志及读/写/空闲超时。Android 网络请求附带新请求 ID，常规请求与健康探测超时集中定义；业务路由、认证规则与持久化语义未变。
- 新增用品编辑器和同步 DTO 映射测试、HTTP 错误响应/请求 ID 测试。
- 验证：Go 全包测试、`go vet` 通过；`core:data`、资产和用品单测、app `lintDebug`、`assembleDebug` 通过；`git diff --check` 通过。真实远端 CI 尚未执行。

## 2026-09-24：备份/恢复设置页 Compose 仪器验收

- 设置 feature 增加 Compose UI 仪器测试配置与 `BackupRestoreScreenTest`：损坏备份提示在备份页可见且导入入口可重试；恢复失败提示在预览页保持可见，数据替换需再次显式确认；取消预览不会调用确认回调。
- 新增 `DefaultRepositoryScheduleTest`，通过 Room 验证资产及用品写入成功时请求调度、失败写入和只读查询均不触发调度。
- 资产 feature 增加 `AssetsScreenTest`，覆盖搜索默认收起及输入过滤、排序菜单回调、本机读取失败重试。
- 用品 feature 增加 `ExpiryScreenTest`，覆盖搜索默认收起及位置过滤、期限筛选回调、本机读取失败重试。
- 用品 feature 新增 `ExpiryViewModelTest` 3 项 JVM 测试，覆盖加载时保留现有列表、读取失败后重试清除错误、位置搜索仅过滤内存状态且不重新读取仓储。Gradle XML 报告记录 3 tests、0 skipped、0 failures、0 errors。
- 执行 `:apps:android:core:model:test :apps:android:core:data:testDebugUnitTest :apps:android:feature:assets:testDebugUnitTest :apps:android:feature:expiry:testDebugUnitTest :apps:android:feature:settings:testDebugUnitTest`，全部通过；CI Android JVM 测试步骤已同步加入 `core:model` 与 `feature:settings`。
- 在 API 36 `medium_phone` AVD 执行 `:apps:android:feature:settings:connectedDebugAndroidTest`，3 项全部通过（`Finished 3 tests on medium_phone(AVD) - 16`）。
- GitHub Actions Android job 增加 Android 36 Google APIs x86_64 模拟器启动，并执行数据层、资产、用品与设置 feature 的 connected 仪器测试。
- 在 API 36 `medium_phone` AVD 执行 `:apps:android:core:data:connectedDebugAndroidTest :apps:android:feature:assets:connectedDebugAndroidTest :apps:android:feature:expiry:connectedDebugAndroidTest :apps:android:feature:settings:connectedDebugAndroidTest`：数据层 29 项及三个 feature 各 3 项全部通过，共 38 项。
- 设置测试对真实 Compose 页面组件直接提供状态与回调，不经过设置路由的系统文件选择器，也未注入 Room 恢复事务错误；损坏文件至原生提示和恢复失败整条 UI 链路仍保留在阶段 A 待办。
- 资产和用品列表测试都是组件级状态验证；实体设备操作、真实刷新后的滚动保持仍保留在阶段 B 待办。

## 2026-09-24：损坏备份与 Room 恢复失败原生链路验收

- 使用最新 Debug APK 在 API 36 `medium_phone` AVD 通过 Android DocumentsUI 选择共享存储中的损坏 JSON 文件，确认应用接收文件后仍留在备份页并显示错误；原先页面暴露 kotlinx.serialization 解析细节，现已统一为“备份文件格式无效或内容损坏”。
- 在该 AVD 的 Room 数据库副本注入临时 SQLite 触发器拒绝恢复插入，通过 DocumentsUI 选择一份包含 1 项合成资产的有效备份，完成校验预览、显式替换确认，再观察到恢复失败时预览保留、页面显示“恢复失败，原数据未改变”。
- 从停止运行的调试应用中读取数据库，确认失败后 `assets`、`expiry_items` 和 `local_sync_records` 均保持 0 行；随后移除临时触发器并还原验收前数据库副本。既有 Room 集成测试也覆盖事务回滚和恢复前安全备份。
- `LocalBackupRepositoryImpl` 为序列化损坏输入映射稳定错误文案；`BackendViewModel` 对 Room/基础设施恢复失败统一显示“恢复失败，原数据未改变”，新增格式错误 Android 集成测试与 ViewModel 错误状态单测。
- 验证：data AndroidJUnitRunner 30 项、settings Compose 仪器测试 3 项、settings ViewModel JVM 测试和 Debug APK 构建全部通过。当前 CI 仪器测试总数由 38 项增至 39 项。此记录基于 AVD，不代表实体设备验收。

## 2026-09-24：WorkManager 调度注册回归

- 为 `SyncScheduler` 增加仅供测试使用的唯一任务名后缀和计量网络约束；测试在隔离 WorkManager 名称空间中检查任务注册，不会取消或替换正式周期恢复任务，也不会在非计量 AVD 网络上启动 Hilt Worker。
- 新增 2 项 API 36 AndroidJUnitRunner 测试：重新创建 `SyncScheduler` 后从 SharedPreferences 恢复周期并保持唯一周期任务；应用初始化后连续本机写入合并为唯一按修改任务，切换到每日周期后原任务 ID 保持不变，确保没有替换出新任务。
- 执行 `:apps:android:core:data:connectedDebugAndroidTest`，32 项通过。CI 仪器测试配置总数增至 41 项。
- 复核逐项 logcat 后发现周期任务可在注册后立即执行；测试 APK 使用普通 `Application`，会导致 Hilt Worker 初始化异常。增加仅测试计量网络约束后重跑 32 项通过，逐项日志不再出现 Worker 启动或该异常；生产网络约束与 Worker 注入行为未改变。
- 这是调度持久化注册与策略回归证据，不模拟系统杀进程、不等待后台触发，也不覆盖 Doze/厂商省电策略；阶段 A 对真实进程回收和长期调度的验收仍开放。

## 2026-09-24：平台与存储候选项技术评估

- 对照产品规划、`api/proto`、服务端 SQLite 初始化、Room 本地优先约束，以及 Android/Go 资产计算实现，完成原生 iOS、Web 管理界面、SQLite→PostgreSQL 与 C++ 计算模块评估。
- 当前均暂缓：尚无多平台/桌面管理需求证据、数据库瓶颈数据或计算热点 profile。Web 还缺少业务 CRUD 合同；数据库演练应等待部署/并发需求触发；C++ 会引入跨平台绑定成本。
- 在 `docs/platform-and-storage-evaluation.md` 记录每项触发条件和首轮验证边界。没有实施平台扩张、生产数据迁移或性能模块抽取。

## 2026-09-24：OpenDesign 本地 MCP 恢复

- `get_active_context` / `list_projects` 返回 `Transport closed`；只读检查发现已安装 0.22.2 应用包签名校验失败。按既有维修授权先备份旧应用、OpenDesign 数据和 Codex MCP 配置。
- 从官方 GitHub 发布资产取得 0.24.0 Apple Silicon DMG，核对长度并通过 `hdiutil verify`；以沙箱外 `codesign --verify --deep --strict` 和 Gatekeeper 验证官方包及安装后的应用均有效，状态为已公证 Developer ID。
- 保留 `/Applications/Open Design.app` 与 MCP 路径，重注册本地 stdio 服务，配置外置 0.24.0 web standalone 根目录与依赖校验保护，避免运行产物写回已签名应用包。既有 OpenDesign 数据目录未更换。
- 直接 stdio JSON-RPC 握手成功，发现 22 个工具；`get_project`、`list_files` 成功读取 `suirenx-asset-ui-redesign` 及既有三个设计文件，服务启动后应用签名仍有效。
- 当前 Codex 任务加载的旧 MCP 快照仍返回 `Transport closed`；设计工作须在新任务加载修复后的连接后继续。阶段 C 原 brief 的平台/流程/完成度仍待维护者选择，未生成或实现提醒 UI。

## 2026-09-24：Release CI 范围与设备/OpenDesign 复核

- 按维护者确认，将 GitHub Actions 改为仅在 GitHub Release 发布时构建 Android release APK，并保存为 unsigned APK artifact；不部署服务端。运行 `:apps:android:app:assembleRelease` 本地构建成功，产物为 `app-release-unsigned.apk`。远端流程需下一次发布 Release 才触发，签名仍待维护者配置。
- 按维护者要求从 TODO 移除用品空位置修复的目标实例部署事项。
- 重新启动 OpenDesign 0.24.0 桌面应用；重注册时恢复外置 web standalone 根目录和依赖校验保护。签名/Gatekeeper 仍通过；本机 stdio MCP 握手成功并发现 22 个工具。当前 Codex 任务宿主连接仍返回 `Transport closed`，Reminder 原 brief 选择尚未提供，未生成设计或提醒 UI。
- Android 16/API 36 实体设备原安装版本为 0.1.0；新 APK 的 `versionCode`/`versionName` 仍为 1/0.1.0，因此本次是同版本原地重装，不是版本升级。先将本地 Room 主库、WAL 与远端缓存数据库归档到权限为 700 的临时目录，再以同签名 `adb install -r` 安装，未清除应用数据；冷启动后原有页面数据仍显示，数据库主文件与 WAL 保持存在。安装前后 signer certificate 一致。
- 实体设备上实际操作资产/用品搜索展开与筛选、资产排序菜单并恢复原排序；刷新前后可见节点数和文字/边界哈希相同，支持刷新期间列表内容与位置保持。用品搜索已测试可选位置搜索入口；未保存的资产编辑离开会弹出放弃确认；资产与用品日期选择器均已打开；用品表单明确显示位置选填。未保存表单均已退出，没有新增或修改记录。
- 工具页与设置页连续交替切换并在每次切换后采集截图；采样画面均显示完整标题和内容，未见闪烁。此证据是抽样截图观察，不是逐帧录屏分析。
- 在原设备数据副本上只读取 SQLite schema 元数据：本地库 `user_version=3`、远端缓存 `user_version=5`，与当前版本相同。此次同签名原地重装验证了原数据保留，没有实际执行版本升级或 Room schema migration；从历史 schema 升级仍待旧库副本验收。
- 保存失败后保留表单输入没有在实体设备上注入故障验收；应在测试仓储或隔离数据库中验证，不对个人本机数据制造失败。后台长期调度、系统回收恢复和 Doze/厂商省电策略也仍待验收。
- 本机 `gh auth status` 当前显示 `moyin1004` 的 GitHub token 无效；重新认证命令为 `gh auth login -h github.com`。该 CLI 登录仅供本机 `gh` 操作，GitHub Actions 的 Release workflow 不依赖开发机登录态。

## 2026-09-24：Room 历史 schema 与保存失败保留验收

- 复跑资产表单 `AssetFormViewModelTest.failedSaveKeepsInputAndCanRetry`：失败仓储返回错误后，姓名、金额、图标仍在 UI state，未发送成功关闭事件；关闭故障后重试成功。Gradle 结果 `BUILD SUCCESSFUL`。
- 在 `LocalDatabaseUpgradeTest` 之外新增 `RemoteDatabaseUpgradeTest`，分别由 v1、v2、v3、v4 经正式 `RemoteDatabaseModule` migration chain 升到 v5；检查资产与 outbox 内容、sync cursor、既有冲突/用品行和 v5 新增的 `lastSyncedAt` 字段。fixture 在唯一命名的测试数据库内创建并清理。
- 定向执行本地库 v1/v2 升级测试：API 36 `medium_phone` AVD 2/2、Android 16 实体设备 2/2 通过。执行远端缓存 v1-v4 升级测试：AVD 4/4、实体设备 4/4 通过。测试使用 Room 生产迁移类，不触碰实体设备个人应用数据库。
- 这覆盖代码支持的历史 schema 迁移路径；该检查点记录时，实体设备原装数据库已是 Local v3 / Remote v5，来源可确认的旧版整包验收仍未完成。后续进展见本文件“历史安装包 Room 整包升级”。
- 初次连接检查时默认 sandbox 无法启动 ADB/Gradle 本地 socket；改用受控 elevated runner 后测试执行成功。测试 runner 输出提示未能回收部分 logcat，但 instrumentation 结果均明确为 0 failed、0 skipped。
- 同一受控 AndroidJUnitRunner 执行 `SyncSchedulerTest` 两项：API 36 AVD 与 Android 16 实体设备各 2/2 通过。只证明测试 WorkManager 名称空间内的调度注册、偏好恢复与去重，不证明生产进程实际被系统杀死后 worker 已执行，也不覆盖长期运行或 Doze。

## 2026-09-24：WorkManager 进程停止后任务持久性

- 新增 [SyncSchedulerProcessRecoveryTest.kt](/Users/bytedance/Desktop/moyin/suirenx/apps/android/core/data/src/androidTest/kotlin/io/suirenx/core/data/sync/SyncSchedulerProcessRecoveryTest.kt)，分两阶段运行：排入随机唯一名的 1 小时周期任务并等偏好落盘、记录当前 PID；第一阶段测试进程退出且 PID 不再存在；第二阶段启动新 PID，先检查 WorkManager 已恢复任务和周期偏好，再初始化 Scheduler 并确认没有重复任务。
- 通过直接 ADB 运行 instrumentation 两阶段，避开 Gradle 每次 connected-test 调用结束后卸载测试包并清理其状态的问题。API 36 AVD 与 Android 16 实体设备各两阶段 1/1 通过；完成后卸载隔离测试包，Scheduler 也已删除唯一测试任务与偏好。
- 该试验证明 WorkManager 持久化记录经测试进程退出后仍可恢复；周期约为一小时，网络约束为仅测试用 METERED，因此没有触发 Hilt `LocalSyncWorker`。测试进程正常退出不等同于系统回收；不证明生产 App 自动恢复执行、长时间周期触发或 Doze/厂商省电行为。

## 2026-09-24：延迟 Worker 退出后执行探针

- 新增 `WorkManagerBackgroundExecutionTest` 两阶段隔离探针：第一阶段安排 15 秒延迟的测试 Worker 并退出 instrumentation 进程；第二阶段只在 PID 改变后检查完成标记及 WorkInfo。常规 connected instrumentation 构建成功，3 项中 1 项跳过跨进程阶段，符合该测试的保护条件。
- 直接在 AVD 和 Android 16 实体设备运行第一阶段后，AVD 在进程退出后写出测试完成标记；实体设备约 30 秒观察期内未写出，JobScheduler 已显示任务登记，设备未处于 Doze。之后卸载测试包清理了测试任务与标记。
- 这是测试 Worker 的受控探针；实体设备未能在观察窗口内执行，且没有触发生产 `LocalSyncWorker`。不得据此关闭后台调度 TODO，也不能推断 Doze/OEM 限制的根因。

## 2026-09-24：历史安装包 Room 整包升级

- 从历史提交 `15d0f31` 导出独立源码并构建旧版 Debug APK；该 APK 包 ID 为 `io.suirenx.app`、Local Room v2。与当前工作树 APK 的签名相同，二者均为 versionCode 1 / versionName 0.1.0；此为源码版本替换和数据库升级验收，不证明正式递增版本号发布。
- 仅在 API 36 `emulator-5554` AVD 安装旧 APK，选择本地模式并通过旧版 UI 创建合成资产 `upgrade_probe_v2`（123.45 元，包含备注）；未对 Android 16 实体设备执行卸载、安装、清数据或改库。
- 覆盖安装当前工作树 APK，保留应用数据并冷启动。总览与资产页显示原资产及金额；停止应用后只读 SQLite 元数据显示 Local `user_version=3`，合成资产 ID、名称、金额和备注均保留，且 `local_sync_records` 与 `local_sync_session` 已由迁移创建。再次冷启动成功。
- 该历史 APK 来自可追溯源码提交且 Room 数据库由实际安装应用创建，满足历史安装数据库整包升级验收；同一 versionCode 的事实作为限制保留，正式发布时仍须使用唯一递增 versionCode。

## 2026-09-24：生产 LocalSyncWorker 进程终止后恢复

- 在 API 36 AVD 保持本地模式，断开 AVD 网络后经当前应用 UI 提交合成资产，WorkManager 中 `suirenx-sync-change` 的 `LocalSyncWorker` 处于 `ENQUEUED`，网络约束为 CONNECTED。
- 记录原进程 PID 后将应用送到后台，并以应用 UID 发送 SIGKILL；PID 消失。应用仍停止且网络隔离时，WorkManager 数据库中的同一项任务保持 `ENQUEUED`。
- 恢复 AVD 原有网络开关后，Android 启动新的应用 PID；logcat 明确记录生产 `io.suirenx.core.data.sync.LocalSyncWorker` 开始运行并返回 `SUCCESS`。本地存储模式下该 Worker 不调用远端同步，不读取或发送个人数据。
- 该项证实 AVD 上生产 Worker 的单次本地模式任务可在进程被强制结束后恢复；SIGKILL 不是系统内存回收，且未覆盖远端服务、周期触发、Doze 或实体设备厂商限制，因此阶段 A 保持开放。

## 2026-09-28：数据层 Android instrumentation 复核

- 在 API 36 `medium_phone` AVD 重跑 `:apps:android:core:data:connectedDebugAndroidTest`，Gradle `BUILD SUCCESSFUL`；XML 汇总为 40 tests、0 failures、0 errors、2 skipped。
- 两个 skipped 项分别是 `SyncSchedulerProcessRecoveryTest` 和 `WorkManagerBackgroundExecutionTest` 的第二阶段，需第一阶段退出后由新进程启动；一次 connected-test invocation 按测试保护条件跳过它们。调度/迁移等单进程仪器覆盖通过，但本次不新增进程重启证据，也不覆盖系统回收、周期实际触发、Doze 或实体设备厂商策略。

## 2026-09-28：AVD 强制 Doze 下的 WorkManager 延迟探针

- 在 API 36 `medium_phone` AVD 分两阶段直接运行现有 `WorkManagerBackgroundExecutionTest`。第一阶段安排 15 秒测试 Worker 并退出；随后进入 deep Doze，20 秒后确认系统仍处于 `IDLE` 且测试 SharedPreferences 没有完成标记。
- 调用 `dumpsys deviceidle unforce` 解除强制 Doze后，等待 20 秒并运行第二阶段；测试通过，断言完成标记与 WorkInfo `SUCCEEDED`。此结果只验证 AVD 上的隔离测试 Worker 在强制 Doze 下延后、退出后恢复，不验证生产 `LocalSyncWorker`、周期任务触发、LMK 回收或实体设备/OEM 省电策略。

## 2026-09-28：生产周期 Worker 经 ActivityManager 终止后恢复

- 在 API 36 `medium_phone` AVD 启动当前 Debug app，确认 `StorageMode=Local` 和 `SyncSchedule=OnChange`，其生产周期恢复任务由 Android JobScheduler 登记，最小周期为 15 分钟。
- 将 app 退到后台后，Android ActivityManager 于 11:48:18 记录 `Killing 4096:io.suirenx.app ... kill background`；PID 消失，周期 Job 仍保留。11:59:48 系统以新 PID 4556 启动 `SystemJobService`，记录到 `LocalSyncWorker` 开始和 `Worker result SUCCESS`，随后 JobScheduler 注册下一周期。
- 这是 AVD 一次系统 ActivityManager 后台终止后的生产周期恢复证据。因为没有制造系统低内存压力，它不证明 LMK 自动回收；单次周期也不能证明长期稳定性，实体 Doze/OEM 策略仍未覆盖。Local 模式 worker 在调用远端同步前直接返回成功。
- 在同一 AVD 后续保留 `StorageMode=Local` / `SyncSchedule=OnChange`，启动 app 后按 Home 退到后台：生产 `LocalSyncWorker` 于 12:14:48 与 12:30:11 两次返回 `SUCCESS`；第二次约在后台 15 分钟后运行，之后 JobScheduler 再登记下一周期。两次使用 PID 3067。此观察补充生产 worker 在后台周期中的一次重复执行，不覆盖进程被回收、长期稳定性、LMK 或实体设备/OEM 行为。
- 再以 `am kill io.suirenx.app` 结束后台 PID 3067，`ApplicationExitInfo` 记录 `USER REQUESTED / KILL BACKGROUND`；PID 消失而 JobScheduler 作业保留。11 分钟后 ActivityManager 以新 PID 4211 为 `SystemJobService` 启动 app，日志显示生产 `LocalSyncWorker` 于 12:46:07.886 开始并在 12:46:07.904 返回 `SUCCESS`，随后 JobScheduler 保留下一周期。该结果证明一次受控 ActivityManager 后台终止后的真实生产 worker 恢复；手动 `am kill` 仍不是 LMK，亦不证明长期稳定性或实体设备/OEM 策略。

## 2026-09-28：低 RAM AVD 下 lmkd 回收与生产 Worker 恢复

- 在不改动 `~/.android/avd/medium_phone.avd/config.ini`（保存配置 `hw.ramSize=2048`）的情况下，以 `-memory 1536 -lowram -no-snapshot` 启动 Android 16/API 36 AVD。现有 SuirenX 安装、Room 数据、`StorageMode=Local` 与 `SyncSchedule=OnChange` 均保留。
- 临时内存探针用不可压缩页施加 guest 内存压力。`dumpsys activity lmk` 显示共 74 次 LMK；lmkd 日志明确记录 swap 余量仅 224 KiB、thrashing 108% 时回收 `io.suirenx.app` PID 3167（oom_adj 900）。`ApplicationExitInfo` 对应记录为 `reason=LOW_MEMORY`。随后停止全部临时探针，guest 可用内存恢复；临时源码、JAR 和设备内探针文件已清理。
- 进程退出时 WorkManager 周期作业仍在 JobScheduler。13:01:41 ActivityManager 以新 PID 4786 为 `SystemJobService` 启动 app，生产 `LocalSyncWorker` 开始并于 13:01:41.378 返回 `SUCCESS`，之后登记下一周期。偏好仍为 Local/OnChange；按 `LocalSyncWorker` 的本地模式分支，本次未调用远端同步。
- 当前源码 `:apps:android:app:assembleDebug` 构建成功，Gradle 判定输出 `UP-TO-DATE`。该结果覆盖 API 36 AVD guest 的真实 lmkd 回收及生产任务恢复；不替代实体设备、OEM 策略或长期稳定性验收。
- 继续观察至 13:32:27，距该成功约 31 分钟。JobScheduler 的下一周期已超过最早运行时间约 16 分钟，报告 `Ready=true`、网络约束满足、设备为 `ACTIVE`，但日志没有第二次 Worker 启动。单次结果可能反映系统批处理延迟，也可能需要继续排查，不能宣称周期稳定性通过。
- 查阅 [WorkManager `ExistingPeriodicWorkPolicy`](https://developer.android.com/reference/androidx/work/ExistingPeriodicWorkPolicy) 与 [周期任务定义](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)：周期时长是最小重复间隔，执行时点还会受约束和系统优化影响；同周期的 `UPDATE` 保留原入队时间。由此不能仅凭 JobScheduler `Ready=true` 或一次超过最早时点的等待认定 app 有调度缺陷，当前证据不支持直接改策略。
- 本轮没有连接的 AVD/实体设备。通过 Android SDK Emulator 37.1.11 启动 `medium_phone` 时进程退出，报告 Qt 构建需要 NEON；周期后续实测待兼容的 Emulator runtime 或实体设备可用。

## 2026-09-28：Release 签名 workflow 接线

- Release workflow 增加四项 GitHub Actions secrets 的条件签名路径。四项均缺省时继续产出 unsigned APK；只配置部分值时失败；全部配置时写入临时 keystore、生成签名 APK、运行 `apksigner verify` 并上传 SHA-256 和证书摘要。
- 未读取、生成或提交任何签名密钥。真实维护者 secrets、签名 APK 的安装升级验收、运营主体/隐私政策和分发账号仍待维护者配置与确认。
- Release job 使用 GitHub `release` Environment；维护者需在其中配置保护规则与 secrets。复核 `:apps:android:app:assembleRelease` 成功生成 unsigned release APK，`zipalign -c -v 4` 校验成功；YAML 解析和从 workflow 提取的两段 shell `bash -n` 检查通过。GitHub Release 尚未触发，真实签名材料未配置。
- 随后以仅用于本地验证的一次性测试 keystore 执行 workflow 的配置检查和签名脚本：`configured=true`，unsigned APK zipalign 检查通过，签名 APK 成功生成，`apksigner verify` 确认 v2/v3 签名有效，SHA-256 文件生成成功。测试 keystore、签名产物及提取脚本均在 `/private/tmp`，验证后清理；不代表真实维护者密钥、GitHub Actions runner 或签名升级验收。
- 另实际执行签名配置检查脚本的无密钥与部分密钥分支：四项空值写出 `configured=false`，只设置一项会以错误退出并拒绝继续。该检查使用空值和虚构占位字符串，没有读取真实 secrets。

## 2026-09-28：OpenDesign brief 连接复核

- 通过 Codex MCP 注册检查确认 `open-design` 仍启用并指向本机 0.24.0 stdio runtime；Brief 收集调用返回 `Transport closed`，没有创建可确认的 draft/workflow。
- `/Applications/Open Design.app` 的 `codesign --verify --deep --strict` 返回 `invalid signature (code or signature have been modified)`，签名 Authority 显示 unavailable。未执行该签名无效的应用包，也未绕过 OpenDesign 直接改提醒 UI；按 AGENTS.md 保留设计依赖任务待处理。
- 本轮再调用当前设计上下文与项目列表的只读接口，均返回 `Transport closed`；尚未读取或改写提醒原型。

## 2026-09-28：周期 Worker 恢复竞态修复

- 冷启动日志显示，JobScheduler 通过 `RescheduleReceiver` 恢复生产周期 Worker 时，应用 `onCreate()` 又调用 `SyncScheduler.initialize()`。原有 `UPDATE` 替换了相同周期 WorkSpec 的 generation，恢复中的 worker 随即被取消并重启。
- 将 `initialize()` 的唯一周期任务策略改为 `KEEP`；用户明确切换周期时继续使用 `UPDATE`。新增仪器测试 `initializeKeepsExistingPeriodicWorkGeneration`，断言初始化不改变任务 ID 或 generation。
- 在 API 36 `medium_phone` AVD 运行 `:apps:android:core:data:connectedDebugAndroidTest`：Gradle 成功，XML 为 41 tests、0 failures、0 errors、2 skipped；跳过项要求两个独立 instrumentation 阶段。`:apps:android:app:assembleDebug` 成功。
- 重装 APK 保留已有应用数据后冷启动，WorkManager generation 维持 23；生产 `LocalSyncWorker` 于 13:55:02 与 14:10:02 两次自然返回 `SUCCESS`。StorageMode 为 Local，本次不会调用远端同步。
- 随后把 app 退到后台并 `am kill`，`ApplicationExitInfo` 记录 `USER REQUESTED / KILL BACKGROUND`，PID 4668 消失，JobScheduler 周期项仍存在。最早运行时间过后约 4 分钟，作业 `Ready=true` 且约束满足，但系统没有自然启动 worker。用 `cmd jobscheduler run -n androidx.work.systemjobscheduler -s io.suirenx.app 73` 保留当前约束受控触发；ActivityManager 以新 PID 6029 启动生产 `LocalSyncWorker`，于 14:28:52 返回 `SUCCESS`，JobScheduler 随后登记下一周期（job id 74）。
- 对 PID 6029 再次执行后台 `am kill` 后，第二个周期 Job 74 到达最早运行窗口后约 5 分钟仍为 `Ready=true`，未自然启动；通过同样方式受控触发后，系统以新 PID 6737 启动 worker，14:48:56 返回 `SUCCESS`，随后登记 job 75。两次受控运行均证明进程死亡后的 JobScheduler/Worker 初始化链路可以成功，但 AVD 自然周期在到期后数分钟未执行，不能据此通过自然恢复或长期稳定性验收。实体设备/OEM Doze 仍未覆盖。

## 2026-09-28：OpenDesign 本机签名恢复

- 从 [OpenDesign 0.24.1 官方发布页](https://github.com/nexu-io/open-design/releases/tag/open-design-v0.24.1) 获取 Apple Silicon DMG，验证 DMG 完整性；候选应用和安装后应用均通过 `codesign --verify --deep --strict`，`spctl --assess --type execute` 接受为 Developer ID notarized app。
- 更换前备份了原应用、约 1.9 GB 的用户数据目录、Codex MCP 配置和环境启动脚本；旧应用仍保留在备份目录。注册 0.24.1 随附 web standalone runtime，恢复应用可以正常启动。
- Codex 当前任务里的 `get_active_context` 和 `list_projects` 仍返回 `Transport closed`；手工启动 stdio 探针在等待握手时超时。不能据此宣称 MCP 握手、工具发现或 SuirenX 既有项目读取成功，故未重提 brief，也未修改提醒原型或实现 UI。待当前任务连接加载修复后的 MCP 后再按既有设计流程继续。

## 2026-09-28：排除 Android 平台备份中的认证凭据

- Android manifest 原先开启 Auto Backup，但未提供排除规则；`AuthTokenStore` 将 bearer token 写入 `sharedpref/auth.xml`，而 Android 默认会备份 SharedPreferences。
- 新增 Android 11 及以下 `fullBackupContent` 规则，以及 Android 12 及以上 `dataExtractionRules`；云备份和设备转移均排除 `auth.xml`，保留 Room 和其他应用数据备份规则。
- `:apps:android:app:assembleDebug` 与 `:apps:android:app:assembleRelease` 均成功。合并 Manifest 指向两套规则；`aapt dump xmltree` 确认 Debug 与 unsigned Release APK 的云备份、设备转移及旧版完整备份均包含 `auth.xml` 排除项（Release 构建会缩短 XML 资源路径）。AVD 的 Backup Manager 为 disabled；为避免触碰已有应用数据，本轮未启用或触发平台备份。真实云备份/换机恢复尚未在实体设备上验证。

## 2026-09-28：GitHub Release Environment 状态核查

- `gh repo view` 显示当前身份为仓库 Admin；只读请求 `GET /repos/moyin1004/SuirenX/environments/release` 返回 404，因此 `release` Environment 尚不存在。
- 没有创建无保护的空环境，也没有读取或写入任何签名 secrets。维护者需提供保护审阅人/部署分支策略，并在正式配置中保存签名材料。


## 2026-09-30：从旧 TODO 归档的验收记录

以下为截至 2026-09-29 的历史证据补充；未完成验收仍见当前 TODO，不能将归档理解为验收完成。

### 阶段 A：可靠性收尾

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

### 阶段 C：提醒

- 规格已明确权限、频率、去重、修改后撤销和时区边界；通知功能尚未实现。
- 2026-09-22 产品决策：用品当前只做期限管理，不扩展库存、消耗或补货预测；提醒采用规格中的默认方案（默认关闭，用户主动开启后每日 09:00 本地时间摘要，用品复用现有临期天数）。
- 2026-09-24：已发出 OpenDesign Cloud 原型需求卡，等待平台/流程范围/完成度选择；确认后再更新既有设计稿并实现提醒。
- 2026-09-24：OpenDesign 0.24.0 已重新启动；本机 stdio 握手与 22 项工具发现通过，但当前任务宿主仍返回 `Transport closed`，提醒原型仍须在可用任务连接和既有 brief 选择后更新，未绕过设计确认实现提醒。
- 2026-09-28：依据 Android 官方 AlarmManager、WorkManager 与通知权限文档，规格已确定每日使用非精确本地闹钟触发 Worker；不申请精确闹钟权限，并补充 API 33+ 通知授权与 API 26+ 通知渠道要求。设计稿与通知实现仍待 brief 选择和可用的 OpenDesign 任务连接。
- 2026-09-28：重新尝试收集 brief 仍收到 `Transport closed`。本机已注册 0.24.0 stdio runtime，但 `/Applications/Open Design.app` 的严格签名验证失败（Authority unavailable）；依 AGENTS.md 保留提醒 UI/实现待设计，不运行或改写无效签名的应用包。
- 2026-09-28：本轮对 OpenDesign Cloud 再次调用当前项目和项目列表只读接口，仍返回 `Transport closed`；未能恢复设计上下文或更新既有原型。
- 2026-09-28：已从官方 0.24.1 Apple Silicon 发布包恢复 OpenDesign。本机安装包通过严格代码签名验证和 macOS notarization assessment，应用可启动；已保留原应用、用户数据和配置备份，并注册随附 web runtime。当前任务的 OpenDesign 工具仍返回 `Transport closed`，不能核验握手、工具列表、SuirenX 项目读取或待处理 brief；Codex 需要新任务加载修复后的 MCP 配置，再继续设计流程。
- 2026-09-29：当前任务仍未加载 OpenDesign 工具；本机 `open-design` MCP 注册为启用并指向 0.24.1 runtime，但本轮无法读取项目或收集阶段 C brief。按设计工作流需在新任务加载该连接后先更新既有原型；提醒 UI 与实现继续待办。

### 阶段 D：根据反馈决定

- 2026-09-24：本轮没有新的真实使用反馈，因此暂不把 CSV 导入导出提升为实现承诺；现有本地 JSON 备份/恢复保持现状。

### 工程 backlog

- 已新增 [发布准备清单](../release-readiness.md)，记录版本、签名和隐私待确认项；正式签名、运营者信息、隐私政策发布与分发账号仍需维护者提供并验收。
- 2026-09-28：Release workflow 已增加 `release` Environment secrets 的条件签名、zipalign 检查、`apksigner verify`、SHA-256 与证书摘要 artifact；未配置 secrets 时保留 unsigned artifact，部分配置会失败。Environment 保护规则、真实维护者密钥、签名安装升级、运营者隐私信息和分发验收仍未完成。
- 2026-09-28：发现 Android Auto Backup 默认会包含保存登录 token 的 SharedPreferences `auth.xml`。已添加 Android 11 及以下 `fullBackupContent`、Android 12 及以上 `dataExtractionRules`，两者都排除该文件，同时保留其他应用数据备份。Debug 与 unsigned Release APK 均构建成功；`aapt dump xmltree` 确认两个 APK 的云备份、设备转移和旧版完整备份均含排除规则。实际云备份与换机恢复仍待支持设备验收。
- 2026-09-28：本地 `:apps:android:app:assembleRelease` 成功且 unsigned APK 的 zipalign 校验通过；workflow YAML 与签名 shell 语法检查通过。此构建仍是 unsigned，不代表 GitHub Release runner 或真实签名验收。
- 2026-09-28：使用仅存于 `/private/tmp` 的一次性测试 keystore，实际执行 workflow 的配置检查和签名脚本；配置分支识别、zipalign、签名 APK 生成、v2/v3 验签和 SHA-256 文件生成均通过。临时材料已清理；这只验证脚本路径，不代表真实维护者签名、APK 升级或 GitHub Release 验收。
- 2026-09-28：又实际执行配置检查脚本的无密钥与部分密钥分支；四项空值输出 `configured=false`，仅设置一项时以错误退出并拒绝部分配置。
- 2026-09-28：通过当前 Admin 身份只读查询 GitHub，`viewerPermission=ADMIN`，但 `/environments/release` 返回 404，说明该 Environment 尚未创建。保护审阅人、允许部署的分支和正式签名 secrets 仍需维护者确定与配置。

## 2026-09-30：v0.1.0-preview 发布准备

- 保留并整理当前需求、待办和历史文档；Android versionName 为 `0.1.0-preview`，versionCode 为 1。
- Go `go test ./...`、asset/m5 Proto descriptor 编译、Android Debug 构建、core:model / core:data / feature:settings 单元测试通过；本地 Markdown 链接与 diff 空白检查通过。
- 按维护者授权生成长期 RSA 4096 位 JKS 签名密钥，存放仓库外并以仅当前用户可读文件交付；未将密钥或密码写入仓库。
- 已创建 GitHub `release` Environment，仅允许 `v*` tag，四项签名 secrets 已配置。维护者仍需另行安全备份密钥与密码；设备安装、同签名升级和隐私验收仍待完成。

- PR #1 已 squash 合并到 main（`adcb022`），`v0.1.0-preview` tag 与 GitHub Pre-release 已发布。首次云端构建在 setup-android 默认请求已不可用的 `tools` 包时失败，未进入编译/签名。修复为只安装 `platform-tools`，增加 workflow_dispatch 对既有发布 tag 重建；不移动已发布 tag。为从 main 发起重建，release 环境额外允许 main 分支。

- 第二次云端构建确认 SDK 包名应为 `platforms;android-37.0`（与本地 package.xml 一致），并固定新版 Android command-line tools 15859902；仅修复工具安装，编译 API 37 和既有发布 tag 不变。

### v0.1.0-preview publication verified (2026-09-30)

- Published Pre-release: https://github.com/moyin1004/SuirenX/releases/tag/v0.1.0-preview
- Immutable source tag: `adcb0228783a6d2489f1d1f5eac9aa899f2df51e` (PR #1 squash).
- Successful signed build: https://github.com/moyin1004/SuirenX/actions/runs/36688875719 (fixed workflow on main, original tagged source).
- Signed APK, SHA-256 and public certificate report uploaded. Independent local apksigner verification passed v2/v3; versionName=0.1.0-preview, versionCode=1, minSdk=26, targetSdk=36.
- APK SHA-256: `1fde84e2c4bb359cbc6727389b22f7b4c15867da43c19b291e7dec5fab23bdd3`, matching the GitHub Release asset digest.
- Certificate SHA-256: `AF:B8:A5:7C:C8:6D:FC:D5:65:90:E2:E7:14:4F:B5:55:24:1D:85:0F:F6:B8:EB:29:17:58:15:32:41:85:4F:55`, matching the delivered JKS.
- Signed-device installation, upgrade, cloud backup and privacy acceptance remain pending. Private signing files remain outside this repository.

## 2026-10-03：v0.1.1 需求规划与设计连接复核

- 按维护者本轮决定，将用品/保修提醒、首页待处理入口，以及 Web 资产/用品、超管账号管理、数据库配置文件和受限 API token 纳入 [v0.1.1 PRD](../prd/v0.1.1.md)。Web 与 Android 共用同步账号数据，超管凭据由服务端 secret/environment 配置。
- 审阅当前 TODO：v0.1.0 正式签名/设备/隐私/分发验收与阶段 A 实体设备策略验收仍需要维护者材料或实体设备，无法在本仓库内完成，继续作为外部验收门。
- 本任务初始配置没有 `open-design` MCP 条目。受限沙箱中的签名检查报告失败，但在完整 macOS 签名服务下验证 `/Applications/Open Design.app` 为 `valid on disk`，Gatekeeper 接受为 Notarized Developer ID；因此没有替换应用。只读验证本机留存的 0.24.1 DMG，其内部校验通过，挂载后的应用签名也通过。
- 修复前备份 Codex 配置和完整 OpenDesign 用户数据至 `/Users/bytedance/Library/Application Support/OpenDesign-repair-20261003-before-registration`；配置副本与原件一致，OpenDesign `app.sqlite` 副本 `PRAGMA quick_check` 返回 `ok`。未更改或删除原数据。
- 使用签名应用恢复 Codex MCP 注册，并仅为该服务设置 `pnpm_config_verify_deps_before_run=false` 与外置 Web runtime 环境。直接启动 stdio MCP 执行 `initialize` / `tools/list` 成功，发现 22 个工具；冷启动后应用签名与 Gatekeeper 仍通过。当前 Codex 任务未热加载新增 MCP，需在新任务中读取既有项目/原稿；尚未启动归因 brief、生成设计或更改原稿。
- OpenDesign MCP 恢复与工具层验证已完成；提醒与 Web 实现仍待新任务加载连接后更新设计稿。此轮没有实现提醒、Web/API 或数据库代码；`git diff --check` 通过。

### v0.1.0 发布验收状态更正

- 2026-10-03：维护者确认 v0.1.0 正式发布验收已完成。同步更新 PRD、路线图和 TODO；阶段 A 剩余后台与实体设备可靠性验收继续延期，不阻塞 v0.1.1。

## 2026-10-03：v0.1.1 服务端基础实现

- 保留 `001_init.sql` 原样，新增 `002_web_admin.sql` 管理账号角色/停用状态、文本配置、受限 API token、全局配置大小设置和审计表。升级失败时 002 与后续迁移保持事务回滚；升级测试验证旧账号数据保留及默认值。
- 新增超管环境引导、账号停用/恢复、禁止停用最后一个超管、显式标准输入密码轮换命令。密码轮换不会由普通重启触发；现存 JWT 按 PRD 继续有效直到过期。
- 新增 Web 资产和用品读写 API，写操作经同步服务提交版本事件、幂等记录和 tombstone；版本不一致返回服务器快照供前端手动处理。普通账号按账号隔离，超管可指定目标账号，管理写入记录审计。
- Web 更新归档中的资产/用品只允许提交无其他字段变化的恢复；恢复后才能编辑。显式删除仍允许创建 tombstone，原 Android 同步协议未改变；API 用例覆盖资产归档默认隐藏但可读、归档用品恢复前拒绝修改。
- 新增 UTF-8 配置文件完整覆盖 CRUD 与大小设置；配置 key 不复用。受限 token 只保存散列，创建时明文仅返回一次，按配置 key 授权、限定 Bearer/Query 传输方式，并提供有效期、撤销、最近使用时间、速率限制和 `no-store` 原文读取。
- `GOCACHE=/private/tmp/suirenx-go-cache go test ./...`、资产 Proto descriptor 编译、`git diff --check` 和 `:apps:android:app:assembleDebug` 通过；Android 构建使用本机已缓存的 Gradle 9.6、Android Studio JBR 和在线获取缺失依赖。集成用例覆盖账号边界、停用登录、审计 100 条上限、配置与 token 权限及稳定错误码、Web 幂等/版本冲突、归档恢复前保护、Android 同步读取事件和删除 tombstone。
- OpenDesign 当前上下文调用返回 `Transport closed`；设计原稿更新及依赖它的 Web UI 尚未开始，不能宣称 v0.1.1 完成或发布验收通过。
- 维护者随后通过 OpenDesign 用户端确认 brief：发布产品、小型多页网站、精致科技感。当前任务重试活动上下文与项目列表仍返回 `Transport closed`，无法据此读取或更新既有原稿；已将 brief 和连接状态同步到 PRD/TODO，依赖原稿的 UI 实现继续等待连接恢复。
- 2026-10-03 继续验收：服务端全量 Go 测试、两份 Proto descriptor、Web JavaScript 语法检查、`git diff --check` 和 Android Debug 构建通过。发现 Android 未识别服务端稳定错误 `registration_closed`，补充明确提示“服务器已关闭新账号注册，请联系管理员”及单测；Android 数据层单测与 Debug 构建通过。Web 管理 UI 依赖 OpenDesign 原稿的三项交互仍待设计更新及端到端验收。
- 2026-10-03 OpenDesign 连接复核：桌面 app、daemon health 与预览页均可读取且显示运行，MCP 配置为 enabled，但活动上下文调用仍返回 `Transport closed`。只读比较登记的本地 discovery socket 与进程实际持有的 Unix socket，daemon/desktop 发现端点均只有 1/2 匹配，MCP client endpoint 仍存在且由 daemon 持有。这说明网页可用与 MCP 桥接分离，并有部分登记端点过期的迹象；尚未证明具体关闭原因，也未重注册或重启用户进程。依赖原稿的管理 UI 继续待桥接恢复。
- 随后使用已安装的签名 OpenDesign app 刷新 Codex MCP 注册，注册查询可读，但当前任务里的活动上下文调用仍为 `Transport closed`，发现端点匹配数未变；按 OpenDesign 工作流，当前任务不能热加载新 MCP 状态，需新任务验证连接后继续 UI 设计与验收。没有重启 OpenDesign 桌面进程。
