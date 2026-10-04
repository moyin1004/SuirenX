# SuirenX

SuirenX（燧人）是一个个人工具箱项目。第一版个人资产管理开始：记录设备和物品、查看服役状态、计算持有天数与日均成本。

已发布 [v0.1.0-preview 预发布版](https://github.com/moyin1004/SuirenX/releases/tag/v0.1.0-preview)，维护者于 2026-10-03 确认 v0.1.0 正式发布验收完成。Android 使用本地 Room 保存资产与用品，支持离线使用和 JSON 备份恢复；Go/Hertz 服务提供账号与可选增量同步。v0.1.1 包含 Web 客户端与配置管理、Android 注册关闭提示及新版本 APK；Android 提醒和剩余后台可靠性验收顺延。

## 技术栈

- Android：Kotlin、Jetpack Compose、Hilt、Retrofit、Room（本地资产与用品）
- API：Go、Hertz、Protobuf IDL
- 数据库：SQLite、GORM
- 构建：Gradle 9.6、Version Catalog、Convention Plugins
- 暂缓评估：iOS、Web、PostgreSQL 和 C++ 模块，尚未承诺版本

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

成功时返回 `201`。客户端应将 `access_token` 作为 bearer token 保存，并仅发送给签发它的服务器地址。注销接口要求认证，但 JWT 是无状态令牌；注销只确认本地会话结束，不会让已签发令牌立即失效，令牌在过期前仍可被接受。

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
cd services/api && go test ./... && go vet ./...
./gradlew :apps:android:app:assembleDebug
./gradlew :apps:android:feature:assets:testDebugUnitTest :apps:android:feature:expiry:testDebugUnitTest
git diff --check
```

## License

见 [LICENSE](LICENSE)。
