# 架构

SuirenX 使用 monorepo，Android/iOS 保持原生。Android 模块依赖向内：app → feature → domain/model；data 实现 domain 接口，domain/model 不依赖 android.*。服务端为 Go/Hertz 模块化单体，transport → service → repository；服务只依赖仓储接口，SQLite/GORM 细节留在基础设施与仓储层。

## 本地数据与同步

```text
Compose / ViewModel → 领域仓储 → Room 业务表 + 同步日志（同一事务）
                                     ↕
                              后台同步协调器
                                     ↕ HTTP JSON
                 Hertz → 同步/认证服务 → repository → SQLite
```

资产和用品的唯一事实来源是 Room。服务器地址、认证、断网和同步开关不改变列表数据源。旧 StorageMode 名称仅作为持久化兼容值，Local 表示暂停同步、Remote 表示开启同步。

服务端只公开健康、认证、增量同步端点。IDL 在 api/proto，services/api/scripts/generate.sh 固定 hz v0.9.7 从 m5.proto 生成路由、模型和 handler 骨架，维护的 handler 适配到服务实例。业务 CRUD 不通过 HTTP。

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

开发阶段服务端唯一 SQL 文件为 001_init.sql；首次上线后先修改 AGENTS.md，再启用不可变增量迁移。校验失败仍停止启动，不自动删除或改写旧库。Android Room 始终显式迁移。详见 [database-migrations.md](database-migrations.md)。
