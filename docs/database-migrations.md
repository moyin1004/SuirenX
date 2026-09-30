# 数据库开发与恢复

## 开发阶段：只保留 001

当前服务端全部表结构集中在 `services/api/internal/database/migrations/001_init.sql`，包括账号、令牌、资产、用品、同步事件、幂等响应和索引。开发阶段直接维护 001，不新增 002/003。首次上线后，先修改根目录 AGENTS.md，再启用不可变的增量迁移。

此规则只针对服务端 SQL 文件。Android Room 仍使用显式版本迁移，禁止 destructive migration，以保留设备已有数据。

## 启动与校验

SQL 通过 go:embed 打包。启动使用 BEGIN IMMEDIATE，在同一事务中执行结构变更和迁移记录；失败整体回滚并停止启动。schema_migrations 保存文件名、SHA-256 与执行时间，重复启动不重复建表。SQL 内不要写事务控制、VACUUM 或连接级 PRAGMA。

合并 001 后，旧开发库可能出现 checksum/history mismatch；这是预期保护。不得改校验和、清空迁移历史或自动删库来绕过。先使用匹配旧版导出需要保留的数据，再显式建立新数据库并导入。此次代码整理不修改 services/api/data 下的运行数据。

## 备份与恢复

停止写入服务后，用 SQLite 一致性备份（包含 WAL 中已提交的数据），并保留对应旧版程序：

```shell
# 在 services/api 下运行；备份名称必须未被使用
sqlite3 data/suirenx.db ".backup 'data/suirenx-before-upgrade.db'"
```

恢复时使用新的路径，通过 SUIRENX_DATABASE_PATH 指向备份副本，避免混用 WAL/SHM。不要将数据库、备份或凭据提交到 Git。

## 验证

执行 `cd services/api && go test ./...`。保留新库、重复启动、历史不匹配、失败回滚和并发启动测试。迁移器仍具备未来增量升级能力，但开发仓库只允许一个 001 SQL 文件。
