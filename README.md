# lec-blog

LEC 工作室成员博客服务。已有博客的成员可以绑定外部站点并同步 RSS 或 Atom。其他成员可开通托管博客，在 Markdown 写作台保存草稿和发布文章。Orbit 通过成员名片打开博客，并接收首次绑定和新文通知。

## 本地运行

使用 JDK 17、Node 22、pnpm 10 和 Docker Compose。以下命令适用于 PowerShell。

```powershell
docker compose up -d --wait
$env:JAVA_HOME = 'C:\Users\admin\.jdks\graalvm-jdk-17.0.12'
$env:SPRING_PROFILES_ACTIVE = 'dev'
.\mvnw.cmd spring-boot:run
```

启动后访问 [健康检查](http://localhost:8080/healthz) 或 [Swagger UI](http://localhost:8080/swagger-ui.html)。在 Swagger 中执行成员接口时，填写 `X-Dev-User-Id`，可选填 `X-Dev-Username`、`X-Dev-Display-Name`、`X-Dev-Avatar`、`X-Dev-Captain`。真实令牌通过 **Authorize** 输入。Mock 仅可在 `dev` 或 `test` profile 启用，`prod` 始终关闭它。

在另一个终端进入 `web` 目录执行 `pnpm install --frozen-lockfile` 和 `pnpm dev`。Vite 默认把 API 和托管 RSS 请求转发至 `http://localhost:8080`，可用 `VITE_BLOG_API_PROXY` 覆盖。打开 `/write` 时可用 ASCII 测试身份登录。生产 Docker 镜像已内置前端，从 Spring Boot 端口即可访问整个网站。

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
.\mvnw.cmd -B test
.\mvnw.cmd package -DskipTests
java -jar target/lec-blog.jar
```

## 环境变量

可复制 `.env.example` 为 `.env` 供 Compose 读取。本机启动 Java 应用时，通过当前终端或 IDE 设置应用需要的环境变量。

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `PORT` | `8080` | API 服务端口 |
| `BLOG_PUBLIC_URL` | `http://localhost:8080` | 托管博客、文章与 RSS 的公开基础地址 |
| `MYSQL_HOST_PORT` | `3306` | 本地 Compose 的 MySQL 端口 |
| `DB_URL` | `jdbc:mysql://127.0.0.1:3306/lec_blog?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true` | MySQL JDBC 地址。变更本地 MySQL 端口时同步修改 |
| `DB_USERNAME`、`DB_PASSWORD` | `root`、`root` | MySQL 账号与密码 |
| `SPRING_PROFILES_ACTIVE` | 镜像默认 `prod` | 本地可选 `dev`，部署使用 `prod` |
| `LEC_SSO_ISSUER` | `https://sso.lecspace.top/oidc` | LecSSO issuer，衍生 `/jwks` 和 `/me` 地址 |
| `LEC_SSO_ORG_ID` | `lec-studio` | 工作室组织 ID |
| `LEC_SSO_WEB_CLIENT_ID` | 空 | LecSSO 公共 SPA 应用的 Client ID |
| `LEC_AUTH_MOCK_ENABLED` | `false`，`dev` 默认 `true` | 开发身份开关 |
| `ORBIT_INTERNAL_URL` | 空 | Orbit 服务根地址 |
| `ORBIT_INTERNAL_TOKEN` | 空 | Orbit 内部事件令牌 |
| `RSS_SYNC_ENABLED` | `true` | 启用定时同步 |
| `RSS_SYNC_INTERVAL_MS` | `1800000` | 定时同步间隔，单位毫秒 |
| `LEC_S3_ENDPOINT`、`LEC_S3_PUBLIC_URL` | 空 | RustFS 内部 S3 地址与公开桶基础地址。公开地址应指向可读取此桶对象的 URL |
| `LEC_S3_BUCKET`、`LEC_S3_REGION`、`LEC_S3_KEY_PREFIX` | `lec-public`、`us-east-1`、`blog` | 图片桶、签名区域和对象路径前缀 |
| `LEC_S3_ACCESS_KEY`、`LEC_S3_SECRET_KEY` | 空 | 图床写入凭据，使用部署环境 Secret 注入 |
| `CORS_ALLOWED_ORIGINS` | `*` | 逗号分隔的来源列表。**注意**：Orbit 正式打包版通过 `file://` 加载渲染进程，请求头 `Origin` 为 `null`；因本服务仅用 `Authorization: Bearer` 鉴权且未启用 Cookie/Credentials，**生产环境推荐保持 `CORS_ALLOWED_ORIGINS=*`**（若需收紧白名单，务必同时包含 `null`，例如 `null,https://orbit.lecspace.top,*.lecspace.top`） |
| `SWAGGER_ENABLED` | `prod` 默认 `false` | 是否在生产环境暴露 `/swagger-ui.html` 与 `/v3/api-docs` |

部署镜像可用 `docker build -t lec-blog .` 构建。Node 22 阶段编译 Web，JDK 17 阶段将 `web/dist` 打进 Spring Boot JAR。
- 前端默认使用 npm 国内镜像，可传 `--build-arg NPM_REGISTRY=https://registry.npmjs.org`。
- `Dockerfile` 默认内置国内阿里云 Maven 镜像（`ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public`，海外构建可传 `--build-arg MAVEN_MIRROR_URL=` 回退 Maven Central），并在打包时默认执行单元测试（`ARG SKIP_TESTS=false`，若 CI 前置已跑完单测可传 `--build-arg SKIP_TESTS=true`）。
- Devtron/Kubernetes 探针可指向 `GET /healthz`，端口与 `PORT` 一致。Flyway 在启动时迁移数据库。生产环境的数据库和 Orbit 令牌通过部署环境的 Secret 注入。

### LecSSO 和图床

在 LecSSO 创建公共 SPA 应用，将生产入口的完整 `/callback` URL 配置为 Redirect URI，例如 `https://blog.lecspace.top/callback`。启用 Authorization Code + PKCE，并允许 `openid profile email urn:logto:scope:organizations urn:logto:scope:organization_roles`。完成配置后，将应用 ID 写入 `LEC_SSO_WEB_CLIENT_ID`。浏览器访问令牌保存于当前标签页的 `sessionStorage`。

图床允许不超过 10 MB 的 PNG、JPEG、GIF、WebP。公开策略仅授予对象读取权限，禁用匿名列桶。知道图片地址的人可以访问该图片。未配置图床时可在编辑器中使用图片外链。

## API

除 `/healthz` 外，下表中的业务接口统一返回 `code`、`message`、`data`、`timestamp`，并要求工作室成员或队长的 `Authorization: Bearer <token>`。

| 方法与路径 | 用途 |
| --- | --- |
| `GET /healthz` | 检查数据库连通性，异常时返回 503 |
| `GET /api/v1/blogs` | 活跃名片列表，支持 `q`、`tag` 和 `sort=recent\|updated` |
| `GET /api/v1/blogs/me` | 我的名片与同步状态，未绑定时 `data` 为 `null` |
| `PUT /api/v1/blogs/me` | 绑定或更新名片。设置 `autoDiscoverRss: true` 可在省略 `rssUrl` 时探测订阅源 |
| `DELETE /api/v1/blogs/me` | 解绑并移除外部抓取缓存，保留原生文章 |
| `POST /api/v1/blogs/me/enable-hosted` | 开通或配置托管博客，切换模式时保留原生文章 |
| `GET/POST /api/v1/posts/me` | 按状态分页、搜索与新建自己的原生文章 |
| `GET/PUT/DELETE /api/v1/posts/me/{id}` | 获取、编辑、发布、撤回与删除自己的文章 |
| `POST /api/v1/uploads/image` | 上传通过魔数校验的图片，表单字段为 `file` |
| `POST /api/v1/blogs/me/sync` | 异步请求同步，每个服务实例对同一成员设 5 分钟冷却 |
| `GET /api/v1/blogs/{userId}/posts` | 成员文章分页 |
| `GET /api/v1/posts/recent` | 全工作室文章分页，附作者昵称、头像和博客名 |
| `GET /api/v1/admin/blogs/hidden` | 队长查看已被下架的成员博客列表 |
| `DELETE /api/v1/admin/blogs/{userId}` | 队长下架指定成员的博客和文章 |
| `POST /api/v1/admin/blogs/{userId}/restore` | 队长恢复已下架的成员博客 |

文章分页参数为 `page` 和 `size`，默认 1 和 10，`size` 上限为 50，返回 `page`、`size`、`total` 和 `items`。RSS 条目的时间优先使用发布时间，其次使用条目更新时间，两者都缺失时使用首次抓取时间。订阅地址变更后，服务会清理该成员的旧文章缓存，并以新订阅源重新建立基线。

公开接口 `/api/v1/public/config`、`/api/v1/public/blogs`、`/api/v1/public/posts/recent`、`/api/v1/public/u/{slug}` 与 `/api/v1/public/u/{slug}/posts/{postSlug}` 可匿名读取。托管博客的 RSS 地址为 `/u/{slug}/rss.xml`。公开主页、文章和 RSS 只显示活跃作者已发布的文章。

## Orbit 接入

头像栏读取 `GET /api/v1/blogs`。最近文章字段为 `latestPostTitle`、`latestPostUrl`、`latestPostAt`，`hasRecentPost` 表示最近文章在过去 7 天内发布。成员只绑定主页时，名片仍可通过 `blogUrl` 导航。

配置 `ORBIT_INTERNAL_URL` 和 `ORBIT_INTERNAL_TOKEN` 后，后续同步每次最多推送一条符合时间窗口的最新新增文章。服务向 `POST /internal/v1/events` 发送 `blog.post_published` 事件，使用 `X-Orbit-Service-Token`。首次导入静默完成，通知失败会记录日志，文章照常入库。

托管文章首次发布同样推送 `blog.post_published`。成员首次加入博客目录时，在建档提交后异步推送 `blog.member_joined`，其中 `payload.postUrl` 等于 `payload.blogUrl`。迁移前已有记录的成员视为已加入。编辑配置、切换模式及解绑重绑均不会再次推送加入通知。
