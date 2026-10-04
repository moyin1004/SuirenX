# 数据库开发与恢复

## 不可变迁移顺序

`services/api/internal/database/migrations/001_init.sql` 是服务端不可变基线，包含基础账号、资产、用品、同步事件、幂等响应和索引。v0.1.0 发布后，`002_web_admin.sql` 追加了管理角色与状态、文本配置、受限 API token、设置和审计表。后续结构变更必须使用不可变、只追加的编号迁移；每项迁移都需要升级与失败回滚测试。禁止修改或删除已发布迁移来绕过 checksum。

每个产品版本在首次部署前将该版本所有数据库结构变更合并到一份编号 SQL 文件中。文件首次部署后立即视为不可变；同版本后续的纯运行时设置优先使用已有的 key/value 表，不改写已应用迁移，也不改迁移历史校验和。确需新增结构时，应放入后续版本的新迁移。

此规则只针对服务端 SQL 文件。Android Room 仍使用显式版本迁移，禁止 destructive migration，以保留设备已有数据。

## 启动与校验

SQL 通过 go:embed 打包。启动使用 BEGIN IMMEDIATE，在同一事务中执行结构变更和迁移记录；失败整体回滚并停止启动。schema_migrations 保存文件名、SHA-256 与执行时间，重复启动不重复建表。SQL 内不要写事务控制、VACUUM 或连接级 PRAGMA。

若数据库出现 checksum/history mismatch，这是保护信号。不得改校验和、清空迁移历史或自动删库来绕过。先使用匹配版本导出需要保留的数据，再显式建立新数据库并导入。升级失败必须保持迁移事务回滚；既有数据库不得自动删除或重写。

## 备份与恢复

停止写入服务后，用 SQLite 一致性备份（包含 WAL 中已提交的数据），并保留对应旧版程序：

```shell
# 在 services/api 下运行；备份名称必须未被使用
sqlite3 data/suirenx.db ".backup 'data/suirenx-before-upgrade.db'"
```

恢复时使用新的路径，通过 SUIRENX_DATABASE_PATH 指向备份副本，避免混用 WAL/SHM。不要将数据库、备份或凭据提交到 Git。

## 验证

执行 `cd services/api && go test ./...`。保留新库、重复启动、历史不匹配、升级失败事务回滚和并发启动测试。每个新增编号迁移都要增加升级与失败回滚覆盖。
