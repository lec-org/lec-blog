# lec-blog

LEC 工作室成员博客聚合服务。成员通过 LecSSO 绑定个人博客，Orbit 使用名片接口渲染头像栏，并接收 RSS 或 Atom 新文事件。

## 本地运行

使用 JDK 17 和 Docker Compose。以下命令适用于 PowerShell。

```powershell
docker compose up -d --wait
$env:SPRING_PROFILES_ACTIVE = 'dev'
.\mvnw.cmd spring-boot:run
```

启动后访问 [健康检查](http://localhost:8080/healthz) 或 [Swagger UI](http://localhost:8080/swagger-ui.html)。在 Swagger 中执行成员接口时，填写 `X-Dev-User-Id`，可选填 `X-Dev-Username`、`X-Dev-Display-Name`、`X-Dev-Avatar`、`X-Dev-Captain`。真实令牌通过 **Authorize** 输入。Mock 仅可在 `dev` 或 `test` profile 启用，`prod` 始终关闭它。

```powershell
$identity = @{ 'X-Dev-User-Id' = 'dev-member'; 'X-Dev-Username' = 'ming' }
$blog = @{
    blogUrl = 'https://example.com'
    blogTitle = 'My Blog'
    tags = @('Java', 'Spring')
    autoDiscoverRss = $true
}
Invoke-RestMethod -Method Put -Uri 'http://localhost:8080/api/v1/blogs/me' `
    -Headers $identity -ContentType 'application/json' -Body ($blog | ConvertTo-Json)
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/blogs' -Headers $identity
```

Linux 和 macOS 使用 `./mvnw`。Windows 也可用以下命令测试、打包和启动。

```powershell
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests
java -jar target/lec-blog.jar
```

## 环境变量

可复制 `.env.example` 为 `.env` 供 Compose 读取。本机启动 Java 应用时，通过当前终端或 IDE 设置应用需要的环境变量。

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `PORT` | `8080` | API 服务端口 |
| `MYSQL_HOST_PORT` | `3306` | 本地 Compose 的 MySQL 端口 |
| `DB_URL` | `jdbc:mysql://127.0.0.1:3306/lec_blog?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true` | MySQL JDBC 地址。变更本地 MySQL 端口时同步修改 |
| `DB_USERNAME`、`DB_PASSWORD` | `root`、`root` | MySQL 账号与密码 |
| `SPRING_PROFILES_ACTIVE` | 镜像默认 `prod` | 本地可选 `dev`，部署使用 `prod` |
| `LEC_SSO_ISSUER` | `https://sso.lecspace.top/oidc` | LecSSO issuer，衍生 `/jwks` 和 `/me` 地址 |
| `LEC_SSO_ORG_ID` | `lec-studio` | 工作室组织 ID |
| `LEC_AUTH_MOCK_ENABLED` | `false`，`dev` 默认 `true` | 开发身份开关 |
| `ORBIT_INTERNAL_URL` | 空 | Orbit 服务根地址 |
| `ORBIT_INTERNAL_TOKEN` | 空 | Orbit 内部事件令牌 |
| `RSS_SYNC_ENABLED` | `true` | 启用定时同步 |
| `RSS_SYNC_INTERVAL_MS` | `1800000` | 定时同步间隔，单位毫秒 |
| `CORS_ALLOWED_ORIGINS` | `*` | 逗号分隔的来源列表，例如 `https://orbit.lecspace.top,*.lecspace.top` |

部署镜像可用 `docker build -t lec-blog .` 构建。Devtron/Kubernetes 探针可指向 `GET /healthz`，端口与 `PORT` 一致。Flyway 在启动时迁移数据库。生产环境的数据库和 Orbit 令牌通过部署环境的 Secret 注入。

## API

除 `/healthz` 外，下表中的业务接口统一返回 `code`、`message`、`data`、`timestamp`，并要求工作室成员或队长的 `Authorization: Bearer <token>`。

| 方法与路径 | 用途 |
| --- | --- |
| `GET /healthz` | 检查数据库连通性，异常时返回 503 |
| `GET /api/v1/blogs` | 活跃名片列表，支持 `q`、`tag` 和 `sort=recent\|updated` |
| `GET /api/v1/blogs/me` | 我的名片与同步状态，未绑定时 `data` 为 `null` |
| `PUT /api/v1/blogs/me` | 绑定或更新名片。设置 `autoDiscoverRss: true` 可在省略 `rssUrl` 时探测订阅源 |
| `DELETE /api/v1/blogs/me` | 解绑并删除该成员已抓取的文章 |
| `POST /api/v1/blogs/me/sync` | 异步请求同步，每个服务实例对同一成员设 5 分钟冷却 |
| `GET /api/v1/blogs/{userId}/posts` | 成员文章分页 |
| `GET /api/v1/posts/recent` | 全工作室文章分页，附作者昵称、头像和博客名 |
| `DELETE /api/v1/admin/blogs/{userId}` | 队长下架指定成员的博客和文章 |

文章分页参数为 `page` 和 `size`，默认 1 和 10，`size` 上限为 50，返回 `page`、`size`、`total` 和 `items`。RSS 条目的时间优先使用发布时间，其次使用条目更新时间，两者都缺失时使用首次抓取时间。订阅地址变更后，服务会清理该成员的旧文章缓存，并以新订阅源重新建立基线。

## Orbit 接入

头像栏读取 `GET /api/v1/blogs`。最近文章字段为 `latestPostTitle`、`latestPostUrl`、`latestPostAt`，`hasRecentPost` 表示最近文章在过去 7 天内发布。成员只绑定主页时，名片仍可通过 `blogUrl` 导航。

配置 `ORBIT_INTERNAL_URL` 和 `ORBIT_INTERNAL_TOKEN` 后，后续同步每次最多推送一条符合时间窗口的最新新增文章。服务向 `POST /internal/v1/events` 发送 `blog.post_published` 事件，使用 `X-Orbit-Service-Token`。首次导入静默完成，通知失败会记录日志，文章照常入库。
