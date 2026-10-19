# Web 服务通用部署说明

本文说明如何将 SuirenX Go 服务部署到 Linux 主机。示例使用 systemd 和 Nginx；也可以使用其他进程管理器或反向代理，关键要求是持久化 SQLite 数据、妥善注入密钥，并通过 HTTPS 对外提供 Web/API。

## 部署前准备

- 一台受支持的 Linux 主机，以及 Go、C 编译器和 SQLite CGO 构建所需工具链。部署已构建的二进制时，目标系统和架构须与构建目标匹配。
- 一个稳定的服务运行目录和专用低权限系统用户。该用户需要读写数据库目录，并能读取环境配置；不要以 root 身份运行 API。
- 一个域名及 TLS 终止点。可由 Nginx、负载均衡器或其他反向代理提供 HTTPS。
- 持久化、受访问控制的备份位置。数据库包含业务数据和凭据散列，应按敏感数据保护。

## 构建与文件布局

Android/iOS/Web 共用仓库根目录 `version.xcconfig` 的 `MARKETING_VERSION`。Go 编译将该文件嵌入二进制，Web 页头读取服务端渲染的版本元数据；升级时只改这一处并重新构建部署，不需要手动改 JavaScript。源码必须保留根目录的 `go.mod`、`version.go` 和 `version.xcconfig`，以供 API 模块的本地依赖使用，运行时不需要这些文件。

在完整源码仓库的 `services/api` 目录中构建服务和密码轮换工具：

```sh
mkdir -p ./bin
go build -o ./bin/suirenx-api ./cmd/server
go build -o ./bin/suirenx-admin-rotate ./cmd/rotate-superadmin-password
```

将二进制部署到主机的应用目录，并为运行数据单独准备目录。下面路径仅为可替换示例：

```text
/opt/suirenx/bin/suirenx-api
/opt/suirenx/bin/suirenx-admin-rotate
/var/lib/suirenx/data/suirenx.db
/etc/suirenx/suirenx.env
```

服务默认使用相对路径 `data/suirenx.db`；生产环境建议显式设置 `SUIRENX_DATABASE_PATH` 为持久化目录中的绝对路径。启动时服务会自动创建数据库父目录并应用仓库内的编号迁移。不要删除数据库或修改已发布迁移来绕过启动失败。

## 配置与管理员初始化

服务进程需要以下配置：

- `SUIRENX_JWT_SECRET`：至少 32 字节的随机密钥。首次部署时生成，后续重启和升级必须保持不变；更换它会使此前签发的 JWT 无法通过签名验证。
- `SUIRENX_ADMIN_USERNAME` 与 `SUIRENX_ADMIN_PASSWORD`：初始超级管理员引导配置。密码至少 12 字节且不超过 72 字节。
- `SUIRENX_DATABASE_PATH`：持久化 SQLite 文件路径。
- `SUIRENX_HTTP_ADDRESS`：监听地址。若由同机反向代理接入，通常只绑定 loopback；若代理位于其他主机，则应绑定受防火墙保护的私有接口。

通过系统 secret manager、受限权限的环境文件或等效机制提供秘密，不要把真实值提交到仓库或写进构建命令。示例环境文件应由部署者创建并按主机安全策略限制读取权限：

```text
SUIRENX_JWT_SECRET=<至少32字节的随机值>
SUIRENX_ADMIN_USERNAME=<初始管理员用户名>
SUIRENX_ADMIN_PASSWORD=<初始管理员密码>
SUIRENX_DATABASE_PATH=/var/lib/suirenx/data/suirenx.db
SUIRENX_HTTP_ADDRESS=127.0.0.1:8888
```

服务首次启动时读取管理员用户名和密码。账号不存在时，应用在内存中用 bcrypt 生成密码散列，并将用户名、角色和散列写入 SQLite `accounts` 表的 `username`、`role`、`password_hash` 字段；数据库不保存该初始密码的明文。后续普通重启不会用环境变量覆盖已存在的管理员密码。若用户名已被普通账号占用，引导失败并拒绝管理员登录。

仓库代码不会读取或创建名为 `initial-admin-credentials.txt` 的文件。若部署人员在主机上另行创建此类文件，它只是部署侧保存初始明文凭据的副本，不是数据库迁移或应用初始化所需文件。应由部署流程安全保管，并在完成登录和显式改密后删除；不要把它放进代码仓库或普通备份。若此类文件丢失，数据库中的 bcrypt 散列不能还原原密码，可使用下文的显式密码轮换命令。

密码轮换示例（在可读取同一数据库、且已设置 `SUIRENX_JWT_SECRET` 的受控终端执行）：

```sh
/opt/suirenx/bin/suirenx-admin-rotate -database /var/lib/suirenx/data/suirenx.db -username <管理员用户名>
```

命令会从标准输入读取一行新密码，不接受命令行密码参数。新密码限制与初始管理员密码相同。密码轮换不会撤销已经签发的 JWT；这些 token 仍会在自然过期前有效。

账号停用（`PATCH /api/v1/admin/accounts/{id}/status`）与注销一样只阻止后续登录，不会使已签发 token 失效。JWT 有效期固定 30 天，服务端不保存会话状态。凭据泄漏或设备丢失后需要立即切断访问时，唯一手段是轮换 `SUIRENX_JWT_SECRET` 并重启服务：此后所有旧 token 都会因签名校验失败被拒绝，代价是所有用户（含管理员）都必须重新登录。轮换密钥前先确认现有用户仍持有可用密码，不要把密钥轮换当作日常操作。

## 进程管理

将服务配置为主机启动时自动运行，并在异常退出后重启。以 systemd 为例，按实际用户和路径调整服务单元：

```ini
[Unit]
Description=SuirenX API and Web service
After=network.target

[Service]
User=suirenx
Group=suirenx
WorkingDirectory=/var/lib/suirenx
EnvironmentFile=/etc/suirenx/suirenx.env
ExecStart=/opt/suirenx/bin/suirenx-api
Restart=on-failure
RestartSec=3
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

限制数据库、配置文件、日志及备份的读取权限，并定期检查服务状态和主机磁盘空间。`GET /healthz` 可用于本机和反向代理后的存活检查；健康响应只表示服务可达，不表示备份或管理员登录验收完成。

## HTTPS 反向代理

对公网部署时，由受信任的 TLS 终止点提供 HTTPS，并将请求转发到服务监听地址。以下是 Nginx HTTPS `server` 块中的核心代理指令示例；证书、域名和 TLS 策略应使用部署环境已批准的配置：

```nginx
location / {
    client_max_body_size 62m;
    proxy_pass http://127.0.0.1:8888;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

防火墙应限制 API 监听端口只允许必要的代理访问。生产环境浏览器只应通过 HTTPS 同源访问 Web 与 API。API token 优先通过 `Authorization: Bearer` 发送；Query token 可能进入反向代理错误日志。即使没有外部 WAF/APM，也不能据此排除反向代理本身的日志风险。只有在所有相关日志和追踪系统都已对查询参数 `token` 脱敏、且该路径不缓存时，才启用 Query token。若无法确认日志脱敏，应使用 Bearer 方式。

应用请求日志不记录查询字符串、`Authorization`、配置正文或 token，但这不能代替对代理和外围设施日志的检查。

### macOS 源码包与 SQLite 迁移文件

从 macOS 打包或解包源码时，应阻止 AppleDouble `._*` 和 Finder `.DS_Store` 元数据进入 Linux 构建目录。这些文件可能被迁移加载器误认为 SQL 迁移文件，导致服务启动失败。打包时可禁用 AppleDouble 并排除本地数据和元数据：

```sh
COPYFILE_DISABLE=1 tar -czf /tmp/suirenx-api-source.tar.gz \
  --exclude='services/api/data' \
  --exclude='._*' --exclude='*/._*' \
  --exclude='.DS_Store' --exclude='*/.DS_Store' \
  -C /path/to/repository go.mod version.go version.xcconfig services/api
```

将源码解压到新的构建目录，并令 `BUILD_ROOT` 指向解压后的 `services/api` 目录；如果必须复用目录，先清理旧的 AppleDouble 文件。编译前检查迁移目录，并确认其中只有预期的编号 SQL 迁移：

```sh
find "$BUILD_ROOT" -type f -name '._*' -delete
find "$BUILD_ROOT" -type f -name '.DS_Store' -delete
find "$BUILD_ROOT/internal/database/migrations" -maxdepth 1 -type f -print
```

不要为了跳过无效迁移文件而放宽迁移校验。新二进制首次启动应使用隔离的临时 SQLite 数据库；确认启动与健康检查成功后再升级正式服务。

Web 客户端和统一产品版本由 Go `embed` 打包，`app.js`/`app.css`/根 `version.xcconfig` 的改动只有进入归档并重新编译才会生效。打包后、编译前核对归档内容与工作树的 SHA-256，可以发现陈旧归档或打包与本地写入的竞态：

```sh
tar -xzOf /tmp/suirenx-api-source.tar.gz \
  services/api/internal/transport/http/webui/app.js | sha256sum
sha256sum /path/to/repository/services/api/internal/transport/http/webui/app.js
```

两者不一致时必须重新打包，不要继续构建。编译完成后再用新二进制在隔离端口请求一次 `/web/app.js`，比对该哈希，并请求 `/` 核对 `<meta name="suirenx-version" content="…">` 与归档的 `MARKETING_VERSION` 一致，确认二进制提供的确实是本次源码的资源和版本。生产上线后重复核对外部 HTTPS 响应。

## 认证限流与请求体上限

注册、登录与配置内容读取共用 `services/api/internal/ratelimit` 的键控令牌桶（基于 `golang.org/x/time/rate`），额度按"突发次数 + 匀速恢复"表达：

| 端点 | 维度 | 突发额度 | 恢复速度 |
| --- | --- | --- | --- |
| 登录 | 被请求的账号名 | 20 | 每 15 秒 1 次 |
| 登录 | 连接对端地址 | 120 | 每 2.5 秒 1 次 |
| 注册 | 被请求的账号名 | 5 | 每 12 分钟 1 次 |
| 注册 | 连接对端地址 | 10 | 每 6 分钟 1 次 |
| 配置内容读取 | API token 散列 | 60 | 每秒 1 次 |
| 配置内容读取 | 连接对端地址 | 240 | 每 0.25 秒 1 次 |

计数包含成功与失败：认证端点在后端比对之前计数，配置内容读取在查库之前计数，因此错误 token、越权 token 和过期 token 与成功读取消耗同一份额度，被拒绝的尝试不会再产生数据库查询。只有不携带 token 的畸形请求（既无 token 又同时给出两种 token）不计入，它们在校验阶段就被拒绝、没有可猜测的秘密。超限返回 `429`（登录/注册的 `code` 为 `too_many_attempts`，配置读取为 `rate_limited`），且额度耗尽时即使凭据正确也会被拒绝。配置读取按 token 与其对端地址两个维度计数：token 散列由调用方选择，只按它计数会让攻击者每换一个猜测值就得到一份新额度；对端维度因此才是猜测量的上界。因为是令牌桶，额度不会在"窗口边界"重复发放整份：突发用完后再想尝试必须等令牌恢复，平均速率与上表一致。预算是进程内状态，重启后清零；它只用于拖慢在线猜测，不替代强密码和 HTTPS。

对端地址取自 TCP 连接本身，不读取 `X-Forwarded-For` 或 `X-Real-IP`：hertz 默认信任所有来源的这些头（等价于 `0.0.0.0/0` 与 `::/0`），若据此限流，攻击者只要轮换该头即可重置自己的预算。因此反向代理（尤其同机 loopback 代理）之后，所有请求会共用同一个对端预算，等价于全局限流。这是 fail-closed 的预期行为；若要按真实客户端地址限流，需要在代码中显式配置受信代理网段，而不是放宽当前实现。

限流器只保留调用方键的固定 32 字节 SHA-256 摘要，最多 4096 个桶，键内容合计最多 128 KiB（桶状态和 map 本身另计）。超长无效账号名仍会消耗预算，但原始字符串不会留在限流缓存中。

配置文件正文上限为 10 MiB（10485760 字节），按 JSON 解码后的 UTF-8 字节数校验，由超管通过 `/api/v1/admin/settings/config-max-bytes` 在 1 至 10485760 之间调整；旧版本写入的更大值会被服务端按 10 MiB 收敛。JSON 字符串单字节最坏可编码为 6 字节（例如 NUL 编码为 `\u0000`），所以 HTTP 请求体上限由 `6 × domain.MaxConfigContentBytes + 2 MiB` 推导，当前为 **62 MiB（65011712 字节）**，额外 2 MiB 留给 JSON 信封。解码后超出配置正文上限仍返回 `413 config_too_large`；编码后超过 HTTP 上限则由传输层直接拒绝。**HTTP 上限对全部端点生效，包括认证前缓冲请求**；反向代理的请求体限制也必须至少允许该大小，否则合法配置会先被代理拒绝。调整配置正文常量时，须一并复核派生的传输上限、代理限制及相关边界测试。

## 升级、备份与恢复

1. 升级前通过 SQLite 在线备份能力创建一致性备份，并确认备份文件的访问权限和保留策略。
2. 部署新二进制后正常启动，由应用按顺序执行编号迁移并校验 checksum。迁移失败时保留数据库和迁移文件，检查错误并恢复到一致状态；不要删除数据库、重写旧迁移或绕过 checksum。
3. 检查服务进程状态、`/healthz`、Web 首页和静态资源，再执行管理员登录及关键业务验收。
4. 定期在隔离环境执行数据库恢复演练。备份包括账号密码散列、配置正文、token 散列及业务数据，应按含敏感资料处理；JWT 密钥和外部部署秘密需按各自的密钥备份策略保管。

SQLite 数据库包含账号密码散列、资产/用品、同步事件、配置正文、token 散列、授权范围和管理员审计记录。当前版本不启用数据库静态加密；主机存储和备份介质需要适当的访问控制与加密保护。

## 部署验收

- 服务以专用低权限用户运行，监听范围符合网络边界设计。
- 重启后数据库和管理员登录状态可用；密钥配置保持不变。
- 外部入口使用 HTTPS，Web 与 API 同源可访问，`/healthz` 返回成功。
- 反向代理和错误日志不会记录密码、token 或配置正文；如部署了 WAF/APM，也检查其日志。Query token 可能暴露在反向代理错误日志中；无法确认相关日志已脱敏时，使用 Bearer 方式，且确认配置响应不可缓存。
- 已建立数据库备份，并在隔离环境验证过恢复。
- 已确认认证端点限流符合预期（反复失败登录返回 `429 too_many_attempts`），并接受反向代理后按对端地址聚合为全局预算的行为。
- 已知悉账号停用与注销都不撤销已签发 JWT；已把 `SUIRENX_JWT_SECRET` 轮换列为凭据泄漏时的应急步骤。
- 已用初始管理员登录并显式轮换密码；安全保存初始凭据的部署侧副本已删除。
