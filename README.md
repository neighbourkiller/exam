<div align="center">

# EkuExam

**在线考试系统 / Online Exam System**

[![Java](https://img.shields.io/badge/Java-21-blue?style=flat-square)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.4-green?style=flat-square)](https://spring.io/projects/spring-boot)
[![Vue.js](https://img.shields.io/badge/Vue.js-3.5-brightgreen?style=flat-square)](https://vuejs.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.4-orange?style=flat-square)](https://dev.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](#)

[Features](#features) | [Tech Stack](#tech-stack) | [Getting Started](#getting-started) | [Architecture](#architecture) | [API Docs](#api-documentation) | [中文](README_zh.md)

</div>

---

A full-stack education exam and grading platform with role-based access control (Admin / Teacher / Student). Covers question bank management, intelligent paper assembly, exam monitoring with anti-cheating, auto-grading, and data analytics.

## Features

- **Question Bank** - CRUD for 5 question types (single-choice, multi-choice, true/false, fill-in-the-blank, short-answer) with image upload via MinIO
- **Smart Paper Assembly** - Manual and auto-generated papers based on subject, difficulty, and question type constraints
- **Exam Lifecycle** - Create, publish, start, submit, terminate with class-level targeting and scheduling
- **Real-time Answer Snapshots** - Auto-saves student answers every 30 seconds to Redis, flushed to MySQL on submit or timeout
- **Anti-cheating Proctoring** - Tab-switch detection, screenshot evidence upload, event logging, and teacher-side disposition workflow
- **Auto-grading & Manual Grading** - Objective questions graded automatically; subjective answers queued for teacher review with batch scoring
- **Analytics Dashboard** - Score distribution, class performance trends, wrong-answer analysis, per-student score breakdown with ECharts visualizations
- **Admin Management** - Bulk import users/classes/courses via CSV/Excel, role assignment, operation audit logs
- **Rate Limiting & Security** - JWT auth with refresh tokens, login rate limiting (10/min), CSRF-safe cookie-based refresh

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 4.0.4, Spring Security, Spring Data Redis, Spring AMQP |
| ORM | MyBatis-Plus 3.5.14 |
| Frontend | Vue 3.5, Vite 6, Element-Plus 2.9, ECharts 5.6, Pinia 3, Axios |
| Database | MySQL 8.4, Flyway (schema migration) |
| Cache | Redis (session snapshots, rate limiting) |
| Message Queue | RabbitMQ (async submission processing) |
| Object Storage | MinIO (question images) |
| API Docs | SpringDoc OpenAPI / Swagger UI |
| Auth | JWT (jjwt 0.12.7) - 2h access token, 7d refresh token |

## Getting Started

### Prerequisites

- **Java 21** (JDK)
- **Node.js 18+** and npm
- **MySQL 8.4**
- **Docker** (for Redis, RabbitMQ, MinIO)

### 1. Start infrastructure services

```bash
docker compose up -d
```

This starts Redis (`:16379`), RabbitMQ (`:15673`), and MinIO (`:19000`).

### 2. Set up the database

Create the MySQL database and run the initial migration:

```sql
CREATE DATABASE exam_mvp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Schema and seed data are managed by Flyway and applied automatically on first startup.

### 3. Run the backend

```bash
./mvnw spring-boot:run
```

The API server starts on **http://localhost:16730**.

### 4. Run the frontend

```bash
cd src/main/resources/frontend
npm install
npm run dev
```

The dev server starts on **http://localhost:5173**.

### 5. Log in

| Account | Password | Role |
|---------|----------|------|
| `admin` | `123456` | Administrator |
| `teacher1` | `123456` | Teacher |
| `student1` | `123456` | Student |

> [!NOTE]
> Default passwords are set in `application-dev.yaml`. Production deployments should override via environment variables (`APP_DEFAULT_PASSWORD`, `DB_PASSWORD`, `JWT_SECRET`).

## Architecture

```
com.ekusys.exam/
├── auth/           Authentication (login, register, token refresh)
├── admin/          User, role, course, class management
├── exam/           Exam lifecycle, snapshots, anti-cheating
├── question/       Question bank CRUD
├── paper/          Test paper management & auto-generation
├── grading/        Auto-grading & teacher manual scoring
├── analytics/      Score distribution, trends, wrong-topic analysis
├── teacher/        Teacher-specific features (class management)
└── common/         Security, config, exceptions, audit logging
```

Each module follows `controller/` -> `service/` -> `dto/` structure. Data entities and MyBatis mappers are centralized under `repository/`.

```
frontend/src/
├── api/            Axios API clients
├── views/          Page components (admin/, teacher/, student/, exam/)
├── components/     Reusable Vue components
├── stores/         Pinia state management
├── router/         Vue Router with role-based guards
├── layout/         Sidebar, header layouts
└── utils/          Helper functions
```

### Key design decisions

- **API prefix**: All endpoints under `/api/v1/`
- **Auth flow**: Access token in `Authorization` header, refresh token in HttpOnly cookie (`exam_refresh_token`)
- **RBAC**: Three roles enforced via `@PreAuthorize` on backend and router guards on frontend
- **Schema management**: Flyway migrations in `db/migration/` (V1-V10), dev seeds in `db/dev-seed/` (V1001+)

## API Documentation

Swagger UI is available when the backend is running:

**http://localhost:16730/swagger-ui.html**

Key endpoint groups:

| Group | Path | Description |
|-------|------|-------------|
| Auth | `/api/v1/auth` | Login, logout, token refresh, password change |
| Admin | `/api/v1/admin` | User/role/course/class CRUD, bulk imports, audit logs |
| Exams | `/api/v1/exams` | Exam lifecycle, proctoring, student submissions |
| Questions | `/api/v1/questions` | Question bank management, image upload |
| Papers | `/api/v1/papers` | Paper creation, auto-generation |
| Grading | `/api/v1/grading` | Pending submissions, batch scoring |
| Analytics | `/api/v1/analytics` | Score stats, trends, wrong-answer analysis |
| Classes | `/api/v1/teacher/classes` | Teacher class & student management |

## Environment Variables

All configuration is externalized. Key variables with their defaults:

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `16730` | Backend server port |
| `DB_URL` | `jdbc:mysql://127.0.0.1:3306/exam_mvp` | MySQL connection URL |
| `DB_USERNAME` | `root` | MySQL username |
| `DB_PASSWORD` | _(empty)_ | MySQL password |
| `REDIS_HOST` | `127.0.0.1` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `RABBITMQ_HOST` | `127.0.0.1` | RabbitMQ host |
| `RABBITMQ_PORT` | `5672` | RabbitMQ port |
| `MINIO_ENDPOINT` | `http://127.0.0.1:19000` | MinIO endpoint |
| `JWT_SECRET` | _(dev-only)_ | JWT signing secret |
| `APP_DEFAULT_PASSWORD` | `Exam@2026` | Default password for new users |

## Running Tests

```bash
# All backend tests
./mvnw test

# Specific test class
./mvnw test -Dtest=ExamServiceTest
```

Tests use JUnit 5, Mockito, and Spring Boot Test with `spring-boot-starter-webmvc-test` and `mybatis-spring-boot-starter-test`.
