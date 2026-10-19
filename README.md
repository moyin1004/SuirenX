# SuirenX

SuirenX（燧人）是一个个人工具箱项目。第一版个人资产管理开始：记录设备和物品、查看服役状态、计算持有天数与日均成本。

最新发布 [v0.1.2](https://github.com/moyin1004/SuirenX/releases/tag/v0.1.2)（源码与 Android 正式签名 APK），此前版本见 [v0.1.1](https://github.com/moyin1004/SuirenX/releases/tag/v0.1.1) 和 [v0.1.0-preview](https://github.com/moyin1004/SuirenX/releases/tag/v0.1.0-preview)。Android 使用本地 Room 保存资产与用品，支持离线使用和 JSON 备份恢复；Go/Hertz 服务提供账号与可选增量同步。v0.1.2 改善资产筛选、排版、离线图标、设置与密码输入，包含 Web 表单修复、审计日志、认证限流与配置大小边界修复。GitHub Release 不自动部署正式服务器；剩余交互验收和到期提醒见 [当前待办](docs/TODO.md)。

## 技术栈

- Android：Kotlin、Jetpack Compose、Hilt、Retrofit、Room（本地资产与用品）
- iOS / iPadOS：SwiftUI（iOS 17+）、Swift Concurrency、SQLite、Keychain；v0.1.3 已完成模拟器验收，实体设备与发布边界见 TODO
- API：Go、Hertz、Protobuf IDL
- 数据库：SQLite、GORM
- 构建：Gradle 9.6、Version Catalog、Convention Plugins
- Web：Go 内嵌原生 HTML/CSS/JavaScript；PostgreSQL 和 C++ 模块仍留待评估

Proto 描述的是 HTTP + JSON API 契约，并不强制使用 gRPC 或二进制 Protobuf。这样未来将 Go 模块替换为 C++ 时，移动端契约可以保持稳定。

## 目录结构

```text
.
├── api/proto/                       Protobuf API 契约
├── apps/android/
│   ├── app/                         Android 入口与应用装配
│   ├── core/model/                  纯 Kotlin 领域模型
│   ├── core/domain/                 仓储接口与用例
│   ├── core/data/                   Retrofit、DTO 与仓储实现
│   ├── core/ui/                     Compose 主题与共享 UI
│   ├── feature/assets/              资产列表与新增/编辑表单
│   ├── feature/tools/               工具入口
│   └── feature/expiry/              日用品保质期管理
├── build-logic/convention/          Gradle 约定插件
├── apps/ios/                        SwiftUI 通用 iPhone/iPad 工程与可独立测试的 Swift 核心
├── docs/                            架构说明与任务清单
└── services/api/                    Hertz + GORM + SQLite 服务
```

文档入口见 [docs](docs/README.md)；[v0.1.0 范围](docs/prd/v0.1.0.md)、[发布准备](docs/release-readiness.md)与[当前待办](docs/TODO.md)分别记录本版需求、发布步骤和剩余工作。

## 环境要求

- macOS Apple Silicon
- Android Studio 2026.1 或兼容版本
- Android CLI（`android` 命令，用于 SDK、模拟器管理及应用安装与运行）
- Android SDK Platform 37、Build Tools 36、Platform Tools
- Android Studio 自带 JDK
- iOS 开发：Xcode 16+ 与对应 iOS Simulator Runtime，实体设备运行需自行配置签名
- Go 1.25+
- `hz` 0.9.7（代码生成固定版本）
- `protoc`

Gradle、Android Gradle Plugin 和应用依赖由项目 Wrapper 自动管理，不需要单独通过 Homebrew 安装 Gradle。

## 启动后端

```shell
cd services/api
export SUIRENX_JWT_SECRET='replace-with-at-least-32-random-bytes'
go run ./cmd/server
```

默认监听 `http://localhost:8888`。`SUIRENX_JWT_SECRET` 是账号 API 的 HS256 签名密钥，至少 32 字节；首次启动会自动创建 `services/api/data/suirenx.db`，不会创建无主的匿名演示资产；该目录不会提交到 Git。生产环境应通过部署平台的 secret store 注入密钥，并为每个环境使用独立密钥。

验证健康接口：

```shell
curl --fail http://localhost:8888/healthz
```

资产和用品在 Android 本机增删改查。服务器仅负责账号与跨设备同步，详见 [数据与同步](docs/data-sync.md)。

## 数据库升级

API 启动时按编号执行内置 SQL 迁移，迁移记录保存在 `schema_migrations`。
开发阶段只维护唯一的 `001_init.sql`；旧库不自动重建，先备份或导出。上线后先修改 AGENTS.md，再启用不可变的增量迁移。
详见[迁移与恢复说明](docs/database-migrations.md)。

## 运行 Android

1. 用 Android Studio 打开仓库根目录。
2. 等待 Gradle Sync 完成。
3. 在终端启动 Go API。
4. 选择 `app` 配置和 Android 设备，点击 Run。

模拟器通过 `http://10.0.2.2:8888/` 访问 Mac 上的 API。

点击右下角 `+` 可录入资产名称、购买金额（元，最多两位小数）和购买日期。
保存成功后返回资产列表；保存不等待网络，同步失败可以单独重试。

首次启动直接使用本地 Room，无需服务器。设置中可选开启同步、配置账号与地址，选择每次修改后同步或定时同步；始终可手动同步。后台失败保留本机数据和待发送批次。备份/恢复始终针对本机完整数据，恢复前自动备份。

模拟器通过 `http://10.0.2.2:8888/` 连接开发服务；明文 HTTP 仅 Debug 开放。首次同步前应用会备份并整理当前账号旧缓存；更换账号不会自动上传已绑定的数据集。真实多设备验收范围见 [TODO](docs/TODO.md)。

直接构建 APK：

```shell
./gradlew :apps:android:app:assembleDebug
```

输出位于 `apps/android/app/build/outputs/apk/debug/app-debug.apk`。

## 运行 iPhone / iPad

使用带 iOS SDK 的 Xcode 打开 `apps/ios/SuirenX.xcodeproj`，选择 `SuirenX` scheme 与 iPhone 或 iPad 模拟器运行。部署到实体设备需要维护者选择自己的签名 Team；仓库不包含证书或描述文件。最低系统为 iOS / iPadOS 17，支持 iPad 横竖屏、分屏与宽屏侧栏；窄窗口使用底部导航。

```shell
swift test --package-path apps/ios
xcodebuild -project apps/ios/SuirenX.xcodeproj -scheme SuirenX \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

核心测试默认跳过真实 HTTP 用例；如需验证，先启动独立临时数据库的本机 Go 服务，再设置 `SUIRENX_IOS_TEST_SERVER=http://127.0.0.1:8888/` 运行 `swift test`。用例会注册合成账号并写入测试记录，不能指向生产服务。UI 回归通过 Xcode Test 运行，在独立目录创建数据，不覆盖日常使用数据。测试运行结果和未覆盖范围见 [当前待办](docs/TODO.md)。

模拟器运行使用 Xcode 默认的本机 ad-hoc 签名；`Simulator.entitlements` 仅向模拟器二进制提供本应用身份以访问 Keychain，不用于实体设备。`CODE_SIGNING_ALLOWED=NO` 适用于编译检查，运行账号流程时应保留签名。CLI 运行隔离 HTTP UI 用例时，以 `TEST_RUNNER_SUIRENX_IOS_TEST_SERVER` 环境变量传入测试服务地址；普通 UI 用例不需要服务器。

iPadOS 26 的真实窗口调整用例需在专用模拟器开启“窗口化 App”，并以 `TEST_RUNNER_SUIRENX_IOS_WINDOWING_TESTS=1` 显式运行；它拖动系统窗口控制柄，检查窄窗口中的草稿和导航，默认跳过以免改动日常设备的窗口状态。最大字号回归在 iPad 使用原生 Command-S 保存入口，普通字号另有触控回归。请复用 Xcode 已安装的运行时。没有配置签名的设备目标构建、模拟器运行和实体机验收应分别记录。

数据保存在设备 SQLite；账号令牌仅保存在不可跨设备迁移的 Keychain 条目。备份采用 Android 相同的本地 JSON v1，只恢复业务数据；恢复前和切换同步目标前先保存安全备份。iOS 后台同步由系统决定运行时机，定时选项为期望频率，始终保留立即同步入口。Debug 可连接开发 HTTP 服务，Release 只接受 HTTPS 地址。

## 当前 API

修改 Proto 后，在仓库根目录运行 `services/api/scripts/generate.sh`。
脚本从 `m5.proto` 使用固定版本 `hz` 更新 `biz/model`、`biz/router` 和 handler 骨架，并校验 Proto。
已有 handler 中的业务适配实现由开发者维护，`hz update` 会保留；不要手改生成的模型和路由。
生成器安装命令：`go install github.com/cloudwego/hertz/cmd/hz@v0.9.7`。

HTTP JSON 使用 snake_case 字段名、整数金额和 `ACTIVE` / `RETIRED` 字符串状态；
零值字段保留，空资产列表返回 `[]`。此格式不是 Protobuf 的 canonical JSON 编码。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/healthz` | 健康检查 |
| `POST` | `/api/v1/auth/register` | 注册账号并返回 bearer token |
| `POST` | `/api/v1/auth/login` | 登录并返回 bearer token |
| `POST` | `/api/v1/auth/logout` | 确认本地会话结束（JWT 不即时撤销） |
| `POST` | `/api/v1/sync/assets` | 版本化资产/用品批量同步与增量拉取 |

### API 示例

注册与登录都接受同一请求结构。密码只在 HTTPS 传输中发送，服务端保存经哈希处理的凭据；不要把真实密码写入 shell 历史或日志。

```http
POST /api/v1/auth/register
Content-Type: application/json

{"username":"alice","password":"a-long-password"}
```

成功时返回 `201`。客户端应将 `access_token` 作为 bearer token 保存，并仅发送给签发它的服务器地址。注销接口要求认证，但 JWT 是无状态令牌；注销只确认本地会话结束，不会让已签发令牌立即失效，令牌在过期前仍可被接受。**账号停用（`PATCH /api/v1/admin/accounts/{id}/status`）同样只阻止后续登录，不会撤销已签发的令牌**；需要在凭据泄漏后立即切断访问时，必须轮换服务端 `SUIRENX_JWT_SECRET` 并重启，使全部令牌签名校验失败。

注册与登录在服务层按"被请求的账号名"和"连接对端地址"分别限流：超限时返回 `429`，`code` 为 `too_many_attempts`，且该窗口内的正确密码同样被拒绝。限流键不取自 `X-Forwarded-For` 等客户端可控头；在 loopback 反向代理后所有请求共用同一个对端预算，这属于 fail-closed 的预期行为。配置文件正文上限为 10 MiB（10485760 字节），HTTP 请求体上限为 12 MiB。

同步请求使用稳定的幂等键；客户端必须先持久化整个批次，网络重试时复用完全相同的键和内容。下面是空批次增量拉取示例：

```http
POST /api/v1/sync/assets
Authorization: Bearer <access_token>
Content-Type: application/json

{"cursor":0,"expiry_cursor":0,"idempotency_key":"<stable-unique-key>","changes":[],"expiry_changes":[]}
```

响应包含已应用批次、增量变化、冲突及下一游标。资产与用品 CRUD 仍由 Android 本机 Room 管理；同步接口不提供通用远端 CRUD。字段和验证规则以 [`m5.proto`](api/proto/suirenx/m5/v1/m5.proto) 与[数据同步说明](docs/data-sync.md)为准。

服务端响应会回传 `X-Request-ID`；错误响应保留可读的 `error` 字符串，并附带稳定的 `code` 与 `request_id`，便于客户端展示错误和维护者关联结构化服务日志。服务端默认读、写、空闲超时分别为 10、30、60 秒；Android 普通请求超时为连接 10 秒、总调用 30 秒，健康探测使用较短的 5/8 秒预算。

## 开发约定

- 金额统一存储为整数“分”。
- 日期使用 `YYYY-MM-DD`，时间使用 RFC 3339。
- Android 领域层不依赖 Android Framework。
- 服务端业务层不依赖 GORM 实现。
- API 变更先修改 `api/proto`，再更新服务和客户端。
- 不提交 APK、构建目录、本地 SQLite、签名文件或 `local.properties`。

## 验证

```shell
go test ./...
(cd services/api && go test ./... && go vet ./...)
./gradlew :apps:android:app:assembleDebug
./gradlew :apps:android:feature:assets:testDebugUnitTest :apps:android:feature:expiry:testDebugUnitTest
git diff --check
```

## License

见 [LICENSE](LICENSE)。

Android 资产立体图标复用 [Microsoft Fluent Emoji](https://github.com/microsoft/fluentui-emoji/tree/1ffb34c752ecf5d402f04cfb4b392c77f57c54bc)，固定提交 `1ffb34c752ecf5d402f04cfb4b392c77f57c54bc`，采用 MIT 许可。官方 `assets/<名称>/3D/` 下共 28 张 PNG 原样内置于资产模块的 `drawable-nodpi`（文件名前加 `fluent_`），包含通用物品（Package 箱子，默认）、电脑、手机、耳机、手表、相机、游戏设备、键盘、书籍、房屋、自行车，以及鼠标、打印机、电视、音箱、麦克风、电池、椅子、沙发、灯泡、眼镜、运动鞋、扳手、背包、行李箱、汽车、摩托车。平板使用项目原创的独立横屏矢量图标 `drawable/suiren_tablet.xml`，与原稿 `assets/suiren/tablet.svg` 保持相同路径和渐变。应用随包保留[版权与许可声明](apps/android/feature/assets/src/main/assets/licenses/FluentEmoji-MIT.txt)。

消费电子产品另有 16 个项目原创矢量图标：显示器、电源、硬盘、空气净化器、扫地机器人、路由器、充电器、充电宝、插线板、投影仪、NAS、迷你主机、扩展坞、风扇、加湿器、吹风机。Android `drawable/suiren_<icon_key>.xml` 与原稿 `assets/suiren/<icon_key>.svg` 使用相同路径、描边及渐变；连同平板共 17 个原创矢量，遵循项目许可。图标库总计 45 项（28 PNG + 17 矢量），全部离线可用，默认物品和已有标识保持兼容。
