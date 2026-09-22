# 路线图执行检查点

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
