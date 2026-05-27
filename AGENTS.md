# Repository Guidelines

## Project Overview

在线考试系统（Online Exam System）—— 一个前后端分离的教育考试与评测平台。支持管理员、教师、学生三种角色的权限管理，涵盖题库管理、智能组卷、考试监控与防作弊、自动阅卷与数据分析等完整业务流程。

**核心技术栈：**

| 层面       | 技术                                                                                     |
|----------|----------------------------------------------------------------------------------------|
| 后端       | Java 21, Spring Boot 4.0.4, Spring Security, MyBatis-Plus 3.5.14, JWT (jjwt 0.12.7)   |
| 前端       | Vue 3.5 (Composition API), Vite 6, Element-Plus 2.9, ECharts 5.6, Pinia 3, Vue Router 4 |
| 数据库      | MySQL 8.4 (database: `exam_mvp`)                                                       |
| 缓存       | Redis                                                                                  |
| 消息队列     | RabbitMQ（试卷提交异步处理）                                                                     |
| 对象存储     | MinIO（题目图片存储）                                                                           |
| 数据库迁移    | Flyway（`classpath:db/migration`）                                                        |
| API 文档   | SpringDoc OpenAPI（`springdoc-openapi-starter-webmvc-ui`）                                |
| 代码简化     | Lombok                                                                                 |
| 运维监控     | Spring Boot Actuator                                                                   |

## Project Structure & Module Organization

```
exam/
├── pom.xml                              # Maven 构建配置
├── mvnw / mvnw.cmd                      # Maven Wrapper
├── .env                                 # 本地开发环境变量（不提交到 Git）
├── src/main/java/com/ekusys/exam/
│   ├── ExamApplication.java             # Spring Boot 启动类
│   ├── admin/                           # 管理员模块（用户管理、班级管理、批量导入、审计日志）
│   │   ├── controller/ dto/ service/
│   ├── analytics/                       # 数据分析模块（ECharts 可视化数据接口）
│   │   ├── controller/ dto/ service/
│   ├── auth/                            # 认证模块（登录、注册、JWT 刷新）
│   │   ├── config/ controller/ dto/ service/
│   ├── common/                          # 公共基础设施
│   │   ├── api/                         # ApiResponse, PageResponse 统一响应封装
│   │   ├── audit/                       # AOP 操作审计日志
│   │   ├── config/                      # SecurityConfig, RabbitMqConfig, MinioConfig, MybatisPlusConfig 等
│   │   ├── controller/                  # HealthController 健康检查
│   │   ├── enums/                       # 全局枚举 (RoleCode, QuestionType, Difficulty, ExamStatus, SessionStatus, SubmissionStatus)
│   │   ├── exception/                   # BusinessException + GlobalExceptionHandler
│   │   ├── model/                       # BaseEntity 基类
│   │   ├── security/                    # JWT 认证过滤器、令牌提供器、限流过滤器、SecurityUtils
│   │   └── util/                        # AnswerJudgeUtil 等工具类
│   ├── exam/                            # 考试模块（发布考试、考试会话、断线恢复）
│   │   ├── controller/ dto/ service/
│   ├── grading/                         # 阅卷模块（自动批改客观题、教师手动批改主观题）
│   │   ├── controller/ dto/ service/
│   ├── paper/                           # 试卷模块（组卷、试卷管理）
│   │   ├── controller/ dto/ service/
│   ├── question/                        # 题库模块（题目 CRUD、图片上传）
│   │   ├── controller/ dto/ service/
│   ├── repository/                      # 持久层（所有 Entity 和 Mapper 集中存放）
│   │   ├── entity/                      # 23 个 MyBatis-Plus 实体类
│   │   └── mapper/                      # 23 个 Mapper 接口（继承 BaseMapper）
│   └── teacher/                         # 教师模块（班级管理、课程管理）
│       ├── controller/ dto/ service/
├── src/main/resources/
│   ├── application.yaml                 # 主配置（全部使用 ${ENV_VAR:default} 外部化）
│   ├── application-dev.yaml             # dev profile（启用 dev-seed、乱序迁移）
│   ├── schema.sql / data.sql            # 遗留初始化脚本（sql.init.mode=never，仅做参考）
│   ├── db/migration/                    # Flyway 迁移脚本（V1 ~ V10）
│   ├── db/dev-seed/                     # 开发用种子数据（V1001 ~ V1003，仅 dev profile 加载）
│   ├── mapper/                          # MyBatis XML 映射文件目录（当前为空，使用注解映射）
│   └── frontend/                        # Vue 3 前端工程
│       ├── package.json
│       ├── vite.config.js
│       ├── .env.development             # VITE_API_BASE_URL=http://localhost:16730/api/v1
│       └── src/
│           ├── main.js                  # 入口
│           ├── App.vue                  # 根组件
│           ├── styles.css               # 全局样式
│           ├── api/                     # Axios 封装 (http.js) + API 函数 (index.js)
│           ├── composables/             # Vue Composables (useCameraProctoring)
│           ├── layout/                  # MainLayout.vue 主布局
│           ├── router/                  # Vue Router 路由配置
│           ├── stores/                  # Pinia 状态管理 (auth.js)
│           ├── utils/                   # 工具函数 (datetime.js, examDraftStore.js, preExamCheck.js)
│           └── views/                   # 页面视图（按角色分目录）
│               ├── admin/               # UserManage, CourseManage, TeachingClassManage, BulkImport, ExamMonitor, OperationAuditLogs
│               ├── auth/                # 登录/注册页面
│               ├── student/             # ExamList, ExamDoing, ResultView, StudentResults, 考前检查
│               └── teacher/             # QuestionManage, PaperGenerate, ExamPublish, ExamProctoring, GradingView, AnalyticsView, ClassManage
└── src/test/java/com/ekusys/exam/       # 测试代码（结构镜像 main，按领域包组织）
```

## Setup & Prerequisites

开发环境需要以下服务运行：

1. **MySQL 8.4** — 数据库 `exam_mvp`，默认 `root` / 配置在 `.env` 中
2. **Redis** — 默认端口见 `.env`（开发环境 16379）
3. **RabbitMQ** — 默认端口见 `.env`（开发环境 15673）
4. **MinIO** — 默认端口见 `.env`（开发环境 19000），bucket: `question-images`
5. **Java 21** — 后端运行时
6. **Node.js 18+** — 前端构建

所有外部连接参数均通过环境变量配置，默认值在 `application.yaml` 中。本地开发时在项目根目录 `.env` 文件中覆盖。

## Build, Test, and Development Commands

### 后端（Spring Boot）

使用项目根目录的 Maven Wrapper：

```bash
# 启动后端开发服务器（端口 16730）
./mvnw spring-boot:run

# 运行全部测试
./mvnw test

# 构建可部署 JAR
./mvnw clean package -DskipTests
```

> **注意：** Windows 环境使用 `mvnw.cmd` 替代 `./mvnw`。

### 前端（Vue 3 / Vite）

前端工程位于 `src/main/resources/frontend`：

```bash
cd src/main/resources/frontend

# 安装依赖
npm install

# 启动开发服务器（端口 5173，代理 API 到 localhost:16730）
npm run dev

# 构建生产包
npm run build

# 预览生产构建
npm run preview
```

### 前后端联调

1. 启动后端：`./mvnw spring-boot:run`（端口 16730）
2. 启动前端：`cd src/main/resources/frontend && npm run dev`（端口 5173）
3. 前端通过 `VITE_API_BASE_URL` 环境变量（默认 `http://localhost:16730/api/v1`）请求后端 API
4. 后端 CORS 配置允许 `http://localhost:5173` 和 `http://127.0.0.1:5173`

## Database & Migrations

- **Flyway** 管理数据库迁移，脚本位于 `src/main/resources/db/migration/`（V1 ~ V10）
- 迁移脚本命名：`V{number}__{description}.sql`
- `baseline-version: 3`，`baseline-on-migrate: true`
- **dev profile** 额外加载 `db/dev-seed/` 目录（V1001 ~ V1003）中的种子数据，且允许乱序执行
- 添加新迁移时，使用下一个递增版本号
- `schema.sql` 和 `data.sql` 为遗留文件（`sql.init.mode=never`），仅做参考，不会自动执行

## Coding Style & Naming Conventions

### Java 后端

- **缩进：** 4 空格
- **依赖注入：** 构造器注入（Lombok `@RequiredArgsConstructor`）
- **包组织：** 按业务领域划分（package-by-feature），每个领域包含 `controller/`, `dto/`, `service/` 子包
- **实体和 Mapper：** 集中在 `repository/entity/` 和 `repository/mapper/`
- **命名后缀模式：**
  - Controller: `XxxController`
  - Service: `XxxService`
  - DTO: 按用途命名（如 `LoginRequest`, `ExamDetailView`）
  - Entity: 实体名不带后缀（如 `User`, `Exam`, `Question`）
  - Mapper: `XxxMapper`（继承 `BaseMapper<T>`）
- **统一响应：** 所有 API 返回 `ApiResponse<T>` 或 `PageResponse<T>`
- **异常处理：** 业务异常抛 `BusinessException`，由 `GlobalExceptionHandler` 统一处理
- **审计日志：** 使用 `@AuditOperation` 注解 + AOP 切面自动记录操作
- **无格式化器/Linter：** 保持与周围代码一致的风格

### Vue 前端

- **组件命名：** PascalCase（如 `LoginView.vue`, `ExamDoing.vue`）
- **JavaScript 模块：** 小写短名称（如 `auth.js`, `http.js`, `datetime.js`）
- **API 层：** `src/api/http.js` 封装 Axios 实例（含 JWT 拦截器），`src/api/index.js` 集中定义所有 API 函数
- **状态管理：** Pinia store 位于 `src/stores/`
- **Composables：** 位于 `src/composables/`
- **视图按角色分目录：** `views/admin/`, `views/auth/`, `views/student/`, `views/teacher/`
- **UI 框架：** Element-Plus 组件库
- **图表：** ECharts
- **保持 import 整洁**，匹配周围代码风格

## Testing Guidelines

### 后端

- 测试框架：JUnit 5 + Mockito + Spring Boot Test + Spring Security Test + MyBatis Test
- 测试文件位于 `src/test/java/com/ekusys/exam/`，包结构镜像 `main`
- 命名规则：`*Test.java`
- 优先编写 Service 层单元测试覆盖业务规则
- Controller/Mapper 层测试用于跨边界行为验证
- 运行所有测试：`./mvnw test`

### 前端

- 前端暂未配置自动化测试工具
- 前端变更需通过 `npm run dev` 进行手动冒烟测试
- 验证流程：启动开发服务器 → 登录各角色账号 → 验证受影响的页面功能

## API Conventions

- 所有后端 API 路径以 `/api/v1/` 为前缀
- RESTful 风格
- JWT Bearer Token 鉴权（access token 在 Authorization header，refresh token 在 HttpOnly Cookie）
- 认证限流：登录 10 次/分钟，刷新 30 次/分钟
- OpenAPI 文档：启动后端后访问 `/swagger-ui.html`
- 前端通过 `VITE_API_BASE_URL` 环境变量配置 API 地址

## Commit & Pull Request Guidelines

- 提交信息使用简短的祈使句，通常为中文（如 `登录界面美化`、`修复ID策略、新增试卷、查询试卷`）
- 每次提交聚焦一个变更集，描述用户可见的行为，而非实现细节
- Pull Request 应当：
  - 说明受影响的模块
  - 列出配置或数据库 schema 变更
  - 包含测试证据（`./mvnw test` 输出、手动 UI 验证）
  - 前端变更需附截图

## Security & Configuration

- `application.yaml` 中所有敏感配置使用 `${ENV_VAR:default}` 模式外部化
- `.env` 文件包含开发环境的数据库、Redis、RabbitMQ、MinIO、JWT 密钥等配置，**不提交到 Git**
- 默认用户密码通过 `APP_DEFAULT_PASSWORD` 环境变量控制
- 部署前务必通过环境变量覆盖所有敏感值（JWT secret、数据库密码、MinIO 密钥等）
- Spring Security 配置集中在 `common/config/SecurityConfig.java`
- JWT 认证链：`JwtAuthenticationFilter` → `JwtTokenProvider` → `LoginUser`
- 登录限流：`AuthRateLimitFilter` + `AuthRateLimitService`（基于 Redis）

## Debugging & Troubleshooting

- 日志级别：`root: info`, `com.ekusys.exam: debug`
- 健康检查端点：`GET /api/v1/health`（`HealthController`）
- Spring Boot Actuator 已启用，可通过 `/actuator` 端点查看应用状态
- Flyway 迁移冲突：注意 `V8` 版本存在两个脚本（命名冲突），新迁移从当前最大版本号 +1 开始
- RabbitMQ 死信队列：提交失败的考试答案会路由到 `exam.submission.process.dlq`
- MinIO 连接问题：检查 `.env` 中的 `MINIO_ENDPOINT` 和 `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`
