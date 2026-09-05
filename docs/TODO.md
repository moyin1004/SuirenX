# 未完成任务

本清单按交付优先级排列。完成任务时同步更新复选框；如果工作范围发生变化，先修改本文件再实现。

## P0：完成第一版资产闭环

- [x] 首次启动配置后端域名/IP，设置中保存多个地址并切换；已有配置直接进入资产页，在连接手机上验证。2026-09-05 模拟器（medium\_phone, android-36）端到端验证通过：首启动门禁页配置地址后进入资产页；设置页多地址列表与「当前使用」标记正确；点选切换后网络请求实时指向新地址（无效地址显示错误重试态，切回后数据恢复）；杀进程冷启动凭已存配置直进资产页。

- [x] 将 Hertz handler/router 改为由 `asset.proto` 和 `hz` 生成，避免契约与手写路由漂移；增加固定版本生成脚本与重复生成验证。

- [x] Android 新增资产表单，并接入 `POST /api/v1/assets`；包含金额/日期校验、防重复提交、失败重试和成功刷新。

- [x] 新增资产详情页与真实导航；浮动 `+` 已接入新增表单。2026-09-05 完成：新增 `GET /api/v1/assets/:id`（proto + hz 重生成，repository/service/handler 全链路，404 返回 JSON 错误）；Android 侧 app 模块拥有 NavHost（`assets` 与 `assets/{id}` 两个目的地，路由常量集中定义），卡片点击进入详情、TopAppBar 返回；详情页只读展示名称/状态/价格/购买日期/持有天数/日均成本，含加载与错误重试态。导航内的 ViewModel 改用 `hilt-navigation-compose` 的 `hiltViewModel()`（NavBackStackEntry 无 Hilt 默认工厂，普通 `viewModel()` 会启动崩溃）。验证：`go test ./...`、protoc 描述符、`./gradlew test`（新增 AssetDetailViewModelTest 4 例）、assembleDebug 全绿；模拟器端到端通过列表→详情→返回、离线错误态→恢复后重试成功。

- [ ] 增加编辑资产接口与界面。

- [ ] 增加“服役中 / 已退役”状态切换接口与界面，记录退役日期。

- [ ] 增加删除或归档资产能力，并设计误删恢复策略。

- [ ] 支持资产图片选择、压缩、存储和展示。

- [x] 在真机或模拟器上完成一次安装、启动和 API 联调验证。2026-09-05 已在 medium\_phone（android-36）模拟器完成：Debug APK 安装启动、首启动配置 `10.0.2.2:8888`、`GET /api/v1/assets` 联调返回服务端真实数据并正确渲染持有天数与日均成本。

- [x] 补充 Hertz transport 集成测试，包括 JSON 契约、非法参数、整数精度和真实 SQLite 数据库错误。

- [ ] 用版本化数据库迁移替换生产路径上的 GORM `AutoMigrate`。

## P1：可日常使用

- [ ] 搜索、排序、分类和组合筛选。

- [ ] 资产总额、月度变化和日均成本统计。

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

