<div align="center">

# EkuExam

**在线考试系统**

[![Java](https://img.shields.io/badge/Java-21-blue?style=flat-square)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.4-green?style=flat-square)](https://spring.io/projects/spring-boot)
[![Vue.js](https://img.shields.io/badge/Vue.js-3.5-brightgreen?style=flat-square)](https://vuejs.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.4-orange?style=flat-square)](https://dev.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](#)

[功能特性](#功能特性) | [技术栈](#技术栈) | [快速开始](#快速开始) | [项目架构](#项目架构) | [接口文档](#接口文档) | [English](README.md)

</div>

---

前后端分离的在线考试与成绩评定平台，支持管理员、教师、学生三种角色，涵盖题库管理、智能组卷、考试监控与防作弊、自动阅卷、数据统计分析等完整业务流程。

## 功能特性

- **题库管理** - 支持单选、多选、判断、填空、简答五种题型的增删改查，支持通过 MinIO 上传题目图片
- **智能组卷** - 支持手动组卷和按科目、难度、题型约束自动生成试卷
- **考试全生命周期** - 创建、发布、开考、交卷、终止，支持按班级分配和定时调度
- **实时答题快照** - 每 30 秒自动将学生作答保存至 Redis，交卷或超时时刷入 MySQL
- **防作弊监控** - 切屏检测、截图证据上传、事件日志记录，教师端可进行处置决策
- **自动阅卷与人工批阅** - 客观题自动评分；主观题进入待批阅队列，支持按题批量评分
- **数据分析看板** - 成绩分布、班级均分趋势、高频错题分析、学生成绩明细，基于 ECharts 可视化
- **后台管理** - 支持 CSV/Excel 批量导入用户/班级/课程，角色分配，操作审计日志
- **安全机制** - JWT 认证 + 刷新令牌、登录频率限制（10 次/分钟）、基于 HttpOnly Cookie 的安全刷新

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 21, Spring Boot 4.0.4, Spring Security, Spring Data Redis, Spring AMQP |
| ORM | MyBatis-Plus 3.5.14 |
| 前端 | Vue 3.5, Vite 6, Element-Plus 2.9, ECharts 5.6, Pinia 3, Axios |
| 数据库 | MySQL 8.4, Flyway（数据库版本管理） |
| 缓存 | Redis（答题快照、频率限制） |
| 消息队列 | RabbitMQ（异步答卷处理） |
| 对象存储 | MinIO（题目图片） |
| 接口文档 | SpringDoc OpenAPI / Swagger UI |
| 认证 | JWT (jjwt 0.12.7) — 访问令牌 2 小时，刷新令牌 7 天 |

## 快速开始

### 环境要求

- **Java 21**（JDK）
- **Node.js 18+** 和 npm
- **MySQL 8.4**
- **Docker**（用于 Redis、RabbitMQ、MinIO）

### 1. 启动基础设施服务

```bash
docker compose up -d
```

将启动 Redis（`:16379`）、RabbitMQ（`:15673`）和 MinIO（`:19000`）。

### 2. 创建数据库

在 MySQL 中创建数据库：

```sql
CREATE DATABASE exam_mvp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

数据库 Schema 和初始数据由 Flyway 管理，首次启动时自动执行。

### 3. 启动后端

```bash
./mvnw spring-boot:run
```

API 服务启动于 **http://localhost:16730**。

### 4. 启动前端

```bash
cd src/main/resources/frontend
npm install
npm run dev
```

开发服务器启动于 **http://localhost:5173**。

### 5. 登录系统

| 账号 | 密码 | 角色 |
|------|------|------|
| `admin` | `123456` | 管理员 |
| `teacher1` | `123456` | 教师 |
| `student1` | `123456` | 学生 |

> [!NOTE]
> 开发环境默认密码配置在 `application-dev.yaml`。生产部署时请通过环境变量覆盖（`APP_DEFAULT_PASSWORD`、`DB_PASSWORD`、`JWT_SECRET`）。

## 项目架构

### 后端模块结构

```
com.ekusys.exam/
├── auth/           认证模块（登录、注册、令牌刷新）
├── admin/          管理模块（用户、角色、课程、班级管理）
├── exam/           考试模块（考试生命周期、答题快照、防作弊）
├── question/       题库模块（题目增删改查）
├── paper/          试卷模块（试卷管理与自动组卷）
├── grading/        阅卷模块（自动评分与教师人工批阅）
├── analytics/      统计模块（成绩分布、趋势分析、错题分析）
├── teacher/        教师模块（教学班级管理）
└── common/         公共模块（安全、配置、异常处理、审计日志）
```

每个业务模块采用 `controller/` -> `service/` -> `dto/` 分层结构，数据实体和 MyBatis Mapper 统一置于 `repository/`。

### 前端结构

```
frontend/src/
├── api/            Axios 接口封装
├── views/          页面组件（admin/、teacher/、student/、exam/）
├── components/     通用 Vue 组件
├── stores/         Pinia 状态管理
├── router/         Vue Router 路由守卫（基于角色）
├── layout/         侧边栏、顶部导航布局
└── utils/          工具函数
```

### 关键设计

- **接口前缀**：所有 API 统一使用 `/api/v1/`
- **认证流程**：访问令牌置于 `Authorization` 请求头，刷新令牌存于 HttpOnly Cookie（`exam_refresh_token`）
- **权限控制**：三种角色（ADMIN / TEACHER / STUDENT），后端通过 `@PreAuthorize` 注解、前端通过路由守卫双重校验
- **数据库版本管理**：Flyway 迁移脚本位于 `db/migration/`（V1-V10），开发环境种子数据位于 `db/dev-seed/`（V1001+）

## 接口文档

后端运行时可访问 Swagger UI：

**http://localhost:16730/swagger-ui.html**

核心接口分组：

| 模块 | 路径 | 说明 |
|------|------|------|
| 认证 | `/api/v1/auth` | 登录、登出、令牌刷新、修改密码 |
| 管理 | `/api/v1/admin` | 用户/角色/课程/班级增删改查、批量导入、审计日志 |
| 考试 | `/api/v1/exams` | 考试全生命周期、监考、学生答卷 |
| 题库 | `/api/v1/questions` | 题目管理、图片上传 |
| 试卷 | `/api/v1/papers` | 试卷创建、自动组卷 |
| 阅卷 | `/api/v1/grading` | 待批阅列表、批量评分 |
| 统计 | `/api/v1/analytics` | 成绩统计、趋势分析、错题分析 |
| 班级 | `/api/v1/teacher/classes` | 教师班级与学生管理 |

## 环境变量

所有配置均已外部化，支持通过环境变量覆盖。主要变量及默认值：

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `SERVER_PORT` | `16730` | 后端服务端口 |
| `DB_URL` | `jdbc:mysql://127.0.0.1:3306/exam_mvp` | MySQL 连接地址 |
| `DB_USERNAME` | `root` | MySQL 用户名 |
| `DB_PASSWORD` | _（空）_ | MySQL 密码 |
| `REDIS_HOST` | `127.0.0.1` | Redis 主机 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `RABBITMQ_HOST` | `127.0.0.1` | RabbitMQ 主机 |
| `RABBITMQ_PORT` | `5672` | RabbitMQ 端口 |
| `MINIO_ENDPOINT` | `http://127.0.0.1:19000` | MinIO 地址 |
| `JWT_SECRET` | _（仅开发）_ | JWT 签名密钥 |
| `APP_DEFAULT_PASSWORD` | `Exam@2026` | 新用户默认密码 |

## 运行测试

```bash
# 运行全部后端测试
./mvnw test

# 运行指定测试类
./mvnw test -Dtest=ExamServiceTest
```

测试框架使用 JUnit 5、Mockito 和 Spring Boot Test，包含 `spring-boot-starter-webmvc-test` 和 `mybatis-spring-boot-starter-test`。
