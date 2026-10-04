# 架构

SuirenX 使用 monorepo，Android/iOS 保持原生。Android 模块依赖向内：app → feature → domain/model；data 实现 domain 接口，domain/model 不依赖 android.*。服务端为 Go/Hertz 模块化单体，transport → service → repository；服务只依赖仓储接口，SQLite/GORM 细节留在基础设施与仓储层。

v0.1.1 Web 用户端使用原生 HTML/CSS/JavaScript，由 `services/api/internal/transport/http/webui` 嵌入 Go API 二进制并通过同源 HTTPS 提供。用户无需配置服务器地址；浏览器以相对路径访问同源 Web API，由 Go Web 后端代理数据访问并执行鉴权。当前没有独立 Node 构建链或跨域会话；浏览器只持有当前标签页会话级 JWT，并通过 Bearer header 调用 Web 专用 API。新增依赖或拆分部署须另行记录架构决策。

## 本地数据与同步

```text
Compose / ViewModel → 领域仓储 → Room 业务表 + 同步日志（同一事务）
                                     ↕
                              后台同步协调器
                                     ↕ HTTP JSON
                 Hertz → 同步/认证服务 → repository → SQLite
```

资产和用品的唯一事实来源是 Room。服务器地址、认证、断网和同步开关不改变列表数据源。旧 StorageMode 名称仅作为持久化兼容值，Local 表示暂停同步、Remote 表示开启同步。

服务端公开健康、认证、Android 增量同步、版本化 Web 业务 API 和超管管理 API。Web 业务路由与 Android 同步路由分开授权；Web 写入通过现有版本化同步服务提交，资产/用品行、版本和同步事件在同一事务内更新。IDL 在 api/proto，services/api/scripts/generate.sh 固定 hz v0.9.7 从 m5.proto 生成路由和模型；业务 handler 适配到服务实例。

超管由 `SUIRENX_ADMIN_USERNAME` / `SUIRENX_ADMIN_PASSWORD` 首次引导，账号密码散列保存在 SQLite。配置文件以永久 opaque key 标识，正文存于服务端数据库；API token 仅保存安全散列，并通过关联表限制可读配置 key。token 内容 API 返回 UTF-8 原文并禁止缓存。Web 部署和日志脱敏要求见 [web-deployment.md](web-deployment.md)。

协议采用 snake_case HTTP JSON、整数 cents、YYYY-MM-DD 日期、RFC 3339 时间、字符串状态、显式零值和空数组，不使用 canonical protojson。资产与用品独立游标，版本号来自服务器，设备时间不用于决定覆盖顺序。

业务写入与同步 journal 同事务提交。同步批次在 HTTP 前持久化，响应确认、游标和业务变更同事务应用。在途网络不占用 Room 事务；新修订不会被旧确认覆盖。详细的冲突、迁移、备份、后台调度与验收见 [data-sync.md](data-sync.md)。

## 业务约定

- 金额为整数分，持有天数包含购买日与今天；退役后截止退役日并包含当天。重新服役清空退役日，从原购买日继续计算。
- 退役日期介于购买日和今天之间；编辑购买日不得晚于退役日。日均成本在领域层计算，不作为存储事实。
- 归档独立于服役状态，可恢复，默认列表与总览排除归档项；归档后需恢复才能编辑。删除需明确确认，并保留同步 tombstone。
- 用品独立于资产金额统计。实际到期日取包装到期日与开封期限中较早者；开封日/有效天数成对填写，到期强提示持续可见。
- 图标用稳定 icon_key，具体矢量/字体只由 UI 模块映射。保持已有资产页视觉和内置图标，不引入图片上传。
- Hilt 注入依赖，StateFlow 表示界面状态，route 下的 Compose 无状态；协程工作可取消且数据层主线程安全。

## 数据库策略

001_init.sql 是不可变服务端基线；v0.1.0 正式发布后新增结构使用只追加编号迁移，包含升级与事务回滚测试。校验失败仍停止启动，不自动删除或改写旧库。Android Room 始终显式迁移。详见 [database-migrations.md](database-migrations.md)。
