# SuirenX

SuirenX（燧人）是一个个人工具箱项目。第一版从“有数”式个人资产管理开始：记录设备和物品、查看服役状态、计算持有天数与日均成本。

当前已经打通第一条端到端链路：Android 原生界面通过 HTTP 访问 Go/Hertz 服务，服务使用 GORM 将资产保存在 SQLite 中。

## 技术栈

- Android：Kotlin、Jetpack Compose、Hilt、Retrofit
- API：Go、Hertz、Protobuf IDL
- 数据库：SQLite、GORM
- 构建：Gradle 9.6、Version Catalog、Convention Plugins
- 未来：原生 Swift/SwiftUI iOS、Web、PostgreSQL、渐进式 C++ 核心

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
│   └── feature/assets/              资产列表与新增表单
├── build-logic/convention/          Gradle 约定插件
├── docs/                            架构说明与任务清单
└── services/api/                    Hertz + GORM + SQLite 服务
```

更详细的边界见 [架构说明](docs/architecture.md)，待办事项见 [未完成任务](docs/TODO.md)。

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
go run ./cmd/server
```

默认监听 `http://localhost:8888`。首次启动会自动创建 `services/api/data/suirenx.db` 并加入两条演示资产；该目录不会提交到 Git。

验证接口：

```shell
curl http://localhost:8888/healthz
curl http://localhost:8888/api/v1/assets
```

创建资产示例：

```shell
curl -X POST http://localhost:8888/api/v1/assets \
  -H 'Content-Type: application/json' \
  -d '{"name":"机械键盘","price_cents":89900,"purchase_date":"2026-09-05","image_url":""}'
```

## 运行 Android

1. 用 Android Studio 打开仓库根目录。
2. 等待 Gradle Sync 完成。
3. 在终端启动 Go API。
4. 选择 `app` 配置和 Android 设备，点击 Run。

模拟器通过 `http://10.0.2.2:8888/` 访问 Mac 上的 API。

点击右下角 `+` 可录入资产名称、购买金额（元，最多两位小数）和购买日期。
保存成功后返回全部资产列表；保存失败会保留输入，修复连接后可以重试。

如果使用真机，可让 ADB 转发端口：

```shell
adb reverse tcp:8888 tcp:8888
./gradlew :apps:android:app:installDebug \
  -PSUIRENX_API_BASE_URL=http://127.0.0.1:8888/
```

也可以将构建属性 `SUIRENX_API_BASE_URL` 设置为 Mac 的局域网地址。明文 HTTP 只在 Debug 构建中开放。

直接构建 APK：

```shell
./gradlew :apps:android:app:assembleDebug
```

输出位于 `apps/android/app/build/outputs/apk/debug/app-debug.apk`。

## 当前 API

修改 Proto 后，在仓库根目录运行 `services/api/scripts/generate.sh`。
脚本使用固定版本 `hz` 更新 `biz/model`、`biz/router` 和 handler 骨架，并校验 Proto。
已有 handler 中的业务适配实现由开发者维护，`hz update` 会保留；不要手改生成的模型和路由。
生成器安装命令：`go install github.com/cloudwego/hertz/cmd/hz@v0.9.7`。

HTTP JSON 使用 snake_case 字段名、整数金额和 `ACTIVE` / `RETIRED` 字符串状态；
零值字段保留，空资产列表返回 `[]`。此格式不是 Protobuf 的 canonical JSON 编码。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/healthz` | 健康检查 |
| `GET` | `/api/v1/assets` | 查询资产，可用 `status=ACTIVE/RETIRED` 筛选 |
| `POST` | `/api/v1/assets` | 创建资产 |

## 开发约定

- 金额统一存储为整数“分”。
- 日期使用 `YYYY-MM-DD`，时间使用 RFC 3339。
- Android 领域层不依赖 Android Framework。
- 服务端业务层不依赖 GORM 实现。
- API 变更先修改 `api/proto`，再更新服务和客户端。
- 不提交 APK、构建目录、本地 SQLite、签名文件或 `local.properties`。

## 验证

```shell
cd services/api && go test ./...
./gradlew :apps:android:app:assembleDebug
git diff --check
```

## License

见 [LICENSE](LICENSE)。
