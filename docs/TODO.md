# 未完成任务

本清单按交付优先级排列。完成任务时同步更新复选框；如果工作范围发生变化，先修改本文件再实现。

## P0：完成第一版资产闭环

- [x] 首次启动配置后端域名/IP，设置中保存多个地址并切换；已有配置直接进入资产页，在连接手机上验证。2026-09-05 模拟器（medium\_phone, android-36）端到端验证通过：首启动门禁页配置地址后进入资产页；设置页多地址列表与「当前使用」标记正确；点选切换后网络请求实时指向新地址（无效地址显示错误重试态，切回后数据恢复）；杀进程冷启动凭已存配置直进资产页。

- [x] 将 Hertz handler/router 改为由 `asset.proto` 和 `hz` 生成，避免契约与手写路由漂移；增加固定版本生成脚本与重复生成验证。

- [x] Android 新增资产表单，并接入 `POST /api/v1/assets`；包含金额/日期校验、防重复提交、失败重试和成功刷新。

- [x] 新增资产详情页与真实导航；浮动 `+` 已接入新增表单。2026-09-05 完成：新增 `GET /api/v1/assets/:id`（proto + hz 重生成，repository/service/handler 全链路，404 返回 JSON 错误）；Android 侧 app 模块拥有 NavHost（`assets` 与 `assets/{id}` 两个目的地，路由常量集中定义），卡片点击进入详情、TopAppBar 返回；详情页只读展示名称/状态/价格/购买日期/持有天数/日均成本，含加载与错误重试态。导航内的 ViewModel 改用 `hilt-navigation-compose` 的 `hiltViewModel()`（NavBackStackEntry 无 Hilt 默认工厂，普通 `viewModel()` 会启动崩溃）。验证：`go test ./...`、protoc 描述符、`./gradlew test`（新增 AssetDetailViewModelTest 4 例）、assembleDebug 全绿；模拟器端到端通过列表→详情→返回、离线错误态→恢复后重试成功。

- [x] 增加编辑资产接口与界面。2026-09-05 完成：服务端新增 `PUT /api/v1/assets/:id`（proto + hz 重生成；repository `Update` 用 map 仅更新 name/price_cents/purchase_date，`RowsAffected==0` 映射 404，成功后回读重算派生值；service 抽出共享 `validateEditable`，Create/Update 复用；400/404/500 错误映射走既有 `writeError`）。Android 侧新增 `UpdateAssetUseCase` 与 `AssetChangeNotifier`（@Singleton SharedFlow，跨导航作用域广播资产变更，列表 VM 收到即自动刷新）；表单弹窗通用化为 `AssetFormDialog`，新增/编辑复用；详情页 TopAppBar 右上角铅笔入口，弹窗预填名称/金额（分→元两位小数）/日期，保存中锁定输入与关闭，失败保留输入并提示；编辑范围仅 name/price/date，状态与退役信息不触碰。列表页新增「资产总览」卡：总资产（服役中+已退役价格之和）、日均成本（仅服役中 daily_cost 之和）、服役中/已退役计数与比例条；筛选 chips 改本地过滤（一次拉全量），总览始终全局。验证：`go test ./...`、protoc 描述符、`./gradlew testDebugUnitTest`（新增总览聚合/本地筛选/notifier 刷新/编辑预填保存/失败保留/非法输入不触网等 7 例）、assembleDebug 全绿；curl 冒烟 PUT 200/404/400；模拟器端到端通过总览卡数字勾稽、筛选空态、详情预填、保存落库（updated_at 变更）、返回列表自动刷新。

- [x] 增加“服役中 / 已退役”状态切换接口与界面，记录退役日期。2026-09-05 完成：新增 `PUT /api/v1/assets/:id/status`，`retired_date` 使用日期字符串，服役中显式返回空字符串；退役日期限制在购买日至今天，恢复服役清空退役日期，持有天数包含退役当天。详情页支持填写退役日期、确认恢复、保存防重复、失败保留输入；成功后详情、列表、筛选与总览同步刷新。编辑已退役资产时禁止购买日期晚于退役日期。验证：Go 单元与 HTTP/SQLite 集成测试、Proto 校验与重复生成无差异、Android 资产模块 22 项测试、assembleDebug、git diff --check 通过；模拟器完成退役→总览更新→恢复服役：测试资产持有天数 133→129→133，退役日期保存/清除正确。

- [x] 增加资产归档与恢复。2026-09-05 完成：新增 `PUT /api/v1/assets/:id/archive`（ARCHIVE / RESTORE）及 `scope=CURRENT/ARCHIVED/ALL` 列表范围；归档资产从日常列表和总览排除，在「已归档」筛选中查看并恢复。保留原服役状态和退役日期，归档期间只读，编辑/状态接口返回 409；重复归档保留首次时间，不自动清理或永久删除。Android 详情提供确认、取消、防重复和失败重试，恢复后列表与总览自动更新。验证：Go 服务、HTTP/SQLite 集成测试及旧库新增列/重新打开持久化测试通过；Android 25 项测试、Debug 构建、Proto 校验、重复生成无差异和 git diff --check 通过。模拟器完成归档、归档筛选、恢复，总额 23598 元降至 6599 元再恢复，日均 165.30 元降至 37.49 元再恢复。已备份并更新本地开发数据库与 API，11 件原资产完整。

- [x] 使用内置图标库替代资产图片。2026-09-05 完成：新增/编辑表单提供 12 种 Material 图标（通用、电脑、手机、平板、耳机、手表、相机、游戏机、书籍、键盘、自行车、家居）；通过 `icon_key` 持久化，在卡片、详情和归档中一致展示，不做图片上传、压缩和存储。旧资产默认通用图标，旧客户端编辑未传图标时保留原值；无效标识返回 400，状态切换和归档不改变图标。验证：Go 单元/HTTP 集成及旧库重开持久化测试、Android 27 项测试、Debug 构建、Proto 校验、重复生成无差异和 git diff --check 通过；模拟器确认选择电脑图标后，详情与列表同步更新且接口保存为 laptop。2026-09-06 图标渲染迁移为 Material Symbols Rounded 可变字体：官方全量可变字体（~15 MB，4277 图标）经 fontTools 子集化为 12 个精选字形（~50 KB，保留 FILL/wght 可变轴默认值），置于 feature/assets 的 `res/font`；新增 `MaterialSymbol` 可组合项（BasicText 渲染私有区码点，字号由 dp 尺寸换算、不受字体缩放影响，tint 跟随 LocalContentColor），`icon_key` 契约与后端零改动；assets 模块依赖从 material-icons-extended 收窄为 core；加图标时运行 `apps/android/scripts/subset-material-symbols.sh` 重新子集化。验证：assets 模块编译与 assembleDebug、Android 27 项单测、Go 全量测试、git diff --check 通过；模拟器确认选择器 12 个字形及卡片 34dp / 表单 48dp / 详情 64dp / 选择器 26dp 四种尺寸渲染正常，选择耳机图标保存后详情静默刷新、列表经 AssetChangeNotifier 同步且接口落库为 headphones。同日将 `MaterialSymbol` 与字体子集迁至 `core/ui` 共享模块（子集扩至 16 个字形 ~65 KB），底栏四个 Tab（home/favorite/auto_graph/settings）、占位页与 FAB（add）全部改用 Symbols 字形：选中态通过同一字体资源的 FILL=1 钉轴 Font 条目渲染填充图标，未选中为线性；彻底移除 `material-icons-extended` 依赖（app 与 settings 模块声明、version catalog 别名全部删除，依赖树仅剩 material-icons-core 供搜索/刷新/关闭/确认/编辑等少量动作图标使用）。验证：assembleDebug、assets/settings 单测通过，`debugRuntimeClasspath` 依赖树无 extended；模拟器确认四个 Tab 填充/线性切换、占位页 56dp 字形、FAB 填充 + 及资产列表图标均正常。同日为 12 个图标分类加入 M3 风格低饱和分类色：`AssetIconOption` 扩展 `containerColor`/`contentColor` 柔和色对（通用中性灰、电脑蓝、手机绿、平板靛紫、耳机品红、手表橙棕、相机玫红、游戏机砖红、书籍米棕、键盘钢蓝灰、自行车黄绿、家居青），新增 `AssetCategoryIcon` 圆角底色容器组件，接入列表卡片（46dp 容器/28dp 字形）、详情页头部（64dp）与编辑表单图标按钮（48dp）三处；图标选择器保持中性灰白（surfaceVariant + 选中 secondaryContainer），`icon_key` 契约与后端零改动。验证：assembleDebug、assets 单测、git diff --check 通过；模拟器确认列表/详情/表单三处彩色容器及选择器中性态正常。

- [x] 在真机或模拟器上完成一次安装、启动和 API 联调验证。2026-09-05 已在 medium\_phone（android-36）模拟器完成：Debug APK 安装启动、首启动配置 `10.0.2.2:8888`、`GET /api/v1/assets` 联调返回服务端真实数据并正确渲染持有天数与日均成本。

- [x] 补充 Hertz transport 集成测试，包括 JSON 契约、非法参数、整数精度和真实 SQLite 数据库错误。

- [x] 用版本化数据库迁移替换生产路径上的 GORM `AutoMigrate`。2026-09-05 完成：新增 `internal/database/migrations`（001 建表与状态索引、002 归档列与索引、003 `icon_key`），SQL 经 `go:embed` 打包进二进制；`database.Open` 启动时在单个 `BEGIN IMMEDIATE` 事务内校验并应用待执行版本，`schema_migrations` 记录版本、文件名、SQL 字节的 SHA-256 与 UTC 应用时间；已应用文件改名或改内容、历史缺口、版本比程序新都拒绝启动；任何一步失败回滚整批 DDL/数据/记录并中止启动；迁移阶段 30 秒超时、写锁等待 5 秒，并发启动只应用一次。无迁移记录的旧库经冻结的 legacy 收养器识别三种 AutoMigrate 时代快照（基础表 / +归档 / +归档+图标），逐列校验类型、可空、主键与索引定义、补齐缺失的已知索引后登记基线再应用后续版本，未知列或不兼容定义拒绝收养且不改写任何数据。文档：新增 `docs/database-migrations.md`（备份、升级、失败恢复），`architecture.md`、`README.md`、`AGENTS.md` 同步规则（生产路径禁止 AutoMigrate、已应用迁移不可变、只能追加下一个编号）。验证：`gofmt` 干净、`go test -count=1 ./...` 全绿（全新库、v0–v3 四种旧快照收养与数据保留、部分历史升级、失败回滚 DDL/数据/记录、历史篡改/缺口/未来版本拒绝、未知旧结构拒绝、6 并发启动只应用一次、非法迁移文件拒绝）、`git diff --check` 干净；真实开发库（GORM AutoMigrate 旧结构、11 件资产）副本启动验证：登记 3 个版本、11 列与 11 行资产完整、重复打开不重写历史；已按文档备份 `data/suirenx.db` 为 `data/suirenx-before-migration.db`，下次重启服务即自动收养旧库。2026-09-06 调整：开发初期的 001–003 压缩为单个 `001_init.sql` 基线（资产表含归档列与图标列、状态与归档两个索引），移除 AutoMigrate 旧库收养器——压缩后旧库若被收养会静默缺少列，改为基线 DDL 失败整批回滚、拒绝启动（开发库删除 `services/api/data/` 下数据库文件重建）；版本号放宽为不限位数的连续整数（正则 `[0-9]+`），加载器按数值排序而非文件名字典序（消除 999→1000 后 `1000` 排在 `999` 前的错位），同一数值版本号的重复/补零变体也被连续性校验拒绝。验证：`gofmt` 干净、`go test -count=1 ./...` 全绿（新基线全新库、前基线旧库拒绝且不改写、增量迁移应用、失败回滚 DDL/数据/记录、历史篡改/未来版本拒绝、6 并发只应用一次、非法文件拒绝、1–10 无补零文件名数值排序）、`git diff --check` 干净。

## P1：可日常使用

- [ ] 搜索、排序、分类和组合筛选。

- [ ] 资产总额、月度变化和日均成本统计。（资产总额与日均成本合计已由列表页「资产总览」卡覆盖，2026-09-05；月度变化趋势与图表待做）

- [ ] 价格、购买渠道、保修期、备注和标签字段。

- [ ] 适配深色模式、动态字体和横屏/大屏。

- [ ] 完成 TalkBack、触控尺寸、颜色对比度等无障碍检查。

- [ ] 为 ViewModel、Repository、DTO 映射和 Compose 关键状态增加测试。

- [ ] 增加统一错误模型、请求 ID、结构化日志和超时配置。

- [ ] 明确用户身份、备份和跨设备同步方案。

- [ ] 设计 Room 本地缓存、离线读取和冲突策略后再实现 offline-first。

- [ ] 将 Hilt 注解处理从 kapt 迁移至 KSP，启用 AGP 9 内置 Kotlin 并移除兼容开关。

## P2：工具箱扩展

- [ ] 心愿清单。

- [ ] 趋势与图表页面。

- [ ] 设置、数据导入导出与备份。

- [ ] 原生 iOS 应用（Swift + SwiftUI）。

- [ ] Web 管理界面。

- [ ] SQLite 到 PostgreSQL 的迁移演练。

- [ ] 选择一个边界清晰的计算模块用 C++ 重写，通过契约测试对比 Go 实现。

- [ ] CI：Go 测试、Proto 校验、Android 构建和静态检查。

- [ ] Release 签名、版本策略、隐私说明与分发流程。

## 已完成

- [x] 将 AGP 固定为 9.3.0，匹配当前 Android Studio 支持的最高版本。

- [x] 建立 Monorepo 和 Android 多模块结构。

- [x] 建立 Compose 资产列表、筛选和加载/错误/空状态。

- [x] 建立 Hilt + Retrofit 数据链路和 DTO/领域模型映射。

- [x] 建立 Hertz + Service + Repository + GORM + SQLite 服务。

- [x] 建立资产查询、创建接口和 Protobuf 契约。

- [x] 增加持有天数与日均成本计算及服务层单元测试。

- [x] 补充 Android 表单与保存流程单元测试，并修复极大金额计算日均成本时的整数溢出。

- [x] 生成 Gradle Wrapper，并完成 Android Debug APK 构建。

- [x] 模仿「有数」App 的交互重构：详情/编辑/新建页改为全屏浮起转场（slideInVertically 350ms + fade，底层页面不动），表单为独立全屏页（左上 X 白圆钮、居中标题、右上黑色 ✓ 圆钮，成功后随导航关闭并锁定防重复提交）；首页重构为悬浮胶囊底栏（资产/心愿/趋势/设置 4 Tab + 黑色圆形 FAB，内层 NavHost 用 saveState/restoreState 保持各 Tab 状态与滚动位置），心愿/趋势为占位页，设置并入 Tab；列表总览卡随滚动滚出、筛选 chips 用 stickyHeader 吸顶常驻；资产卡标题字号收敛为 17sp；表单逻辑从详情 VM 拆出为独立 AssetFormViewModel（SavedStateHandle 读取 id 区分新建/编辑），详情 VM 改为监听 AssetChangeNotifier 静默刷新（不闪 loading、失败保留旧数据）；加载/错误/空态内容统一避让悬浮底栏（bottom 120dp）。2026-09-05 模拟器端到端验证：新建落库后总览 11/11、总资产 ¥41,419.99；编辑改名后详情静默刷新、返回列表卡片名同步；Tab 切换后筛选态与滚动位置保持；chips 本地过滤与空态文案正确；App 图标为黑底 #191919 + 荧光绿 #82E600 火苗 adaptive icon。单元测试 18 例（含 AssetFormViewModelTest 5 例、详情静默刷新 2 例）与 assembleDebug 全绿。


- [x] 首页字体与卡片进一步紧凑化：标题 30sp、资产名称 15sp、资产卡高 192dp，总览与筛选同步缩小；底栏四项等宽分配，修复末项设置图标受挤压，并补偿齿轮视觉尺寸。2026-09-05。

- [x] 进一步收紧首页高度：资产卡从 192dp 降至 180dp，图标下间距从 20dp 降至 16dp；底部导航胶囊上下内边距各减少 3dp、图标容器高度减少 4dp，新增按钮从 56dp 降至 52dp。文字和图标尺寸保持不变。2026-09-05。
