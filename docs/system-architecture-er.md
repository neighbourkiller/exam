# 系统总体架构图与数据库 E-R 图

本文档基于当前代码与数据库迁移生成：

- 后端：`src/main/java/com/ekusys/exam`
- 前端：`src/main/resources/frontend/src`
- 运行期数据库结构：`src/main/resources/db/migration/V1__init.sql` 至 `V10__exam_proctoring_policy.sql`
- 配置：`src/main/resources/application.yaml`

> 说明：当前数据库迁移未声明数据库级 `FOREIGN KEY`，E-R 图中的 `FK` 为基于字段命名、唯一键、索引和服务调用推导出的逻辑外键关系。

## 系统总体架构图

```mermaid
flowchart TB
    subgraph Client["客户端"]
        Browser["浏览器"]
        Vue["Vue 3 SPA\nVue Router + Pinia + Element Plus + ECharts"]
        Axios["Axios HTTP 客户端\n/api/v1"]
        Browser --> Vue --> Axios
    end

    subgraph Backend["Spring Boot 4 后端"]
        Security["安全入口\nCORS + JWT Filter + 登录限流 Filter\n方法级角色鉴权"]

        subgraph Controllers["Controller 层"]
            AuthController["AuthController\n登录/刷新/退出/当前用户"]
            AdminController["AdminController\n用户/角色/课程/班级/批量导入/审计/考试监控"]
            QuestionController["QuestionController\n题库/题图上传"]
            PaperController["PaperController\n手动组卷/自动组卷/试卷维护"]
            ExamController["ExamController\n考试发布/开考/快照/交卷/监考/反作弊"]
            GradingController["GradingController\n主观题批阅"]
            AnalyticsController["AnalyticsController\n成绩分析"]
            TeacherClassController["TeacherClassController\n教师班级与学生管理"]
        end

        subgraph Services["业务服务层"]
            AuthService["认证与会话服务"]
            AdminService["基础数据与批量管理服务"]
            QuestionService["题库与题图服务"]
            PaperService["组卷服务"]
            ExamService["考试生命周期服务"]
            ProctoringService["监考与反作弊服务"]
            SubmissionService["交卷接收与异步处理服务"]
            GradingService["阅卷评分服务"]
            AnalyticsService["统计分析服务"]
            AuditAspect["操作审计切面"]
            Schedulers["定时任务\n超时自动交卷/快照刷盘/资源清理"]
        end

        Mappers["Repository Mapper 层\nMyBatis-Plus BaseMapper"]
        Flyway["Flyway 数据库迁移"]

        Security --> Controllers
        Controllers --> Services
        Services --> Mappers
        AuditAspect --> Mappers
        Schedulers --> Services
        Flyway --> MySQL
    end

    subgraph Infra["基础设施"]
        MySQL[("MySQL\n业务数据")]
        Redis[("Redis\n刷新令牌/登录限流/考试快照")]
        RabbitMQ[("RabbitMQ\nexam.submission.process")]
        MinIO[("MinIO\n题图与反作弊证据")]
    end

    Axios --> Security
    Mappers --> MySQL
    AuthService --> Redis
    Security --> Redis
    ExamService --> Redis
    SubmissionService --> RabbitMQ
    RabbitMQ --> SubmissionService
    QuestionService --> MinIO
    ProctoringService --> MinIO
    Schedulers --> Redis
    Schedulers --> MinIO
```

## 角色与功能模块

```mermaid
flowchart LR
    Admin["管理员"]
    Teacher["教师"]
    Student["学生"]

    subgraph Frontend["前端路由"]
        AdminViews["/admin/*\n用户、课程、教学班、批量导入、考试监控、审计日志"]
        TeacherViews["/teacher/*\n题库、组卷、发布考试、监考、班级、阅卷、分析"]
        StudentViews["/student/*\n考试列表、环境检测、考试作答、成绩查看"]
    end

    subgraph Api["后端 API"]
        AuthApi["/auth"]
        AdminApi["/admin"]
        QuestionApi["/questions"]
        PaperApi["/papers"]
        ExamApi["/exams"]
        GradingApi["/grading"]
        AnalyticsApi["/analytics"]
        TeacherClassApi["/teacher/classes"]
    end

    Admin --> AdminViews --> AdminApi
    Teacher --> TeacherViews --> QuestionApi
    TeacherViews --> PaperApi
    TeacherViews --> ExamApi
    TeacherViews --> GradingApi
    TeacherViews --> AnalyticsApi
    TeacherViews --> TeacherClassApi
    Student --> StudentViews --> ExamApi
    AdminViews --> AuthApi
    TeacherViews --> AuthApi
    StudentViews --> AuthApi
```

## 核心考试链路

```mermaid
sequenceDiagram
    participant T as 教师/管理员
    participant S as 学生
    participant API as Spring Boot API
    participant DB as MySQL
    participant R as Redis
    participant MQ as RabbitMQ
    participant O as MinIO

    T->>API: 题库维护、组卷、创建并发布考试
    API->>DB: 写入 question/paper/exam/exam_target_class
    S->>API: 登录并开始考试
    API->>DB: 创建或更新 exam_session
    S->>API: 保存作答快照
    API->>R: 写入考试快照
    S->>API: 上传反作弊事件和证据
    API->>O: 保存证据文件
    API->>DB: 写入 anti_cheat_event
    S->>API: 提交试卷
    API->>DB: 接收 submission/submission_answer
    API->>MQ: 发布交卷处理消息
    MQ-->>API: 消费交卷消息
    API->>DB: 计算客观分并更新提交状态
    T->>API: 批阅主观题
    API->>DB: 写入 subjective_grade 并更新分数
    T->>API: 查看监考与成绩分析
    API->>DB: 聚合 submission/anti_cheat_event/proctoring_disposition
```

## 数据库 E-R 图

```mermaid
erDiagram
    SYS_USER {
        BIGINT id PK
        VARCHAR username UK
        VARCHAR password
        VARCHAR real_name
        TINYINT enabled
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    SYS_ROLE {
        BIGINT id PK
        VARCHAR code UK
        VARCHAR name
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    SYS_USER_ROLE {
        BIGINT id PK
        BIGINT user_id FK
        BIGINT role_id FK
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    STUDENT_PROFILE {
        BIGINT id PK
        BIGINT user_id FK, UK
        VARCHAR student_no
        VARCHAR enrollment_year
        VARCHAR status
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    TEACHER_PROFILE {
        BIGINT id PK
        BIGINT user_id FK, UK
        VARCHAR teacher_no
        VARCHAR title
        VARCHAR status
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    SUBJECT {
        BIGINT id PK
        VARCHAR name
        VARCHAR description
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    TEACHING_CLASS {
        BIGINT id PK
        VARCHAR name
        BIGINT subject_id FK
        BIGINT teacher_id FK
        VARCHAR term
        VARCHAR status
        INT capacity
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    STUDENT_TEACHING_CLASS {
        BIGINT id PK
        BIGINT student_id FK
        BIGINT subject_id FK
        BIGINT teaching_class_id FK
        VARCHAR enroll_status
        DATETIME enrolled_at
        DATETIME dropped_at
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    QUESTION {
        BIGINT id PK
        BIGINT subject_id FK
        VARCHAR type
        VARCHAR difficulty
        TEXT content
        TEXT options_json
        TEXT answer
        TEXT analysis
        INT default_score
        BIGINT creator_id FK
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    QUESTION_ASSET {
        BIGINT id PK
        BIGINT question_id FK
        BIGINT uploader_id FK
        VARCHAR file_type
        VARCHAR url
        VARCHAR object_key
        VARCHAR original_name
        VARCHAR content_type
        BIGINT size
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    PAPER {
        BIGINT id PK
        VARCHAR name
        BIGINT subject_id FK
        VARCHAR description
        INT total_score
        BIGINT teacher_id FK
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    PAPER_QUESTION {
        BIGINT id PK
        BIGINT paper_id FK
        BIGINT question_id FK
        INT score
        INT sort_order
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    EXAM {
        BIGINT id PK
        VARCHAR name
        BIGINT paper_id FK
        DATETIME start_time
        DATETIME end_time
        INT duration_minutes
        INT pass_score
        VARCHAR status
        BIGINT publisher_id FK
        VARCHAR proctoring_level
        TEXT proctoring_config_json
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    EXAM_TARGET_CLASS {
        BIGINT id PK
        BIGINT exam_id FK
        BIGINT class_id FK
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    EXAM_SESSION {
        BIGINT id PK
        BIGINT exam_id FK
        BIGINT student_id FK
        VARCHAR status
        DATETIME start_time
        DATETIME deadline_time
        DATETIME end_time
        DATETIME last_snapshot_time
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    SUBMISSION {
        BIGINT id PK
        BIGINT exam_id FK
        BIGINT student_id FK
        VARCHAR status
        INT objective_score
        INT subjective_score
        INT total_score
        TINYINT pass_flag
        DATETIME submitted_at
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    SUBMISSION_ANSWER {
        BIGINT id PK
        BIGINT submission_id FK
        BIGINT question_id FK
        TEXT answer_text
        TINYINT objective_correct
        INT objective_score
        INT subjective_score
        TINYINT final_answer
        VARCHAR source
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    SUBJECTIVE_GRADE {
        BIGINT id PK
        BIGINT submission_answer_id FK
        BIGINT teacher_id FK
        INT score
        VARCHAR comment
        DATETIME graded_at
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    ANTI_CHEAT_EVENT {
        BIGINT id PK
        BIGINT exam_id FK
        BIGINT student_id FK
        VARCHAR event_type
        DATETIME event_time
        BIGINT duration_ms
        TEXT payload
        TEXT evidence_json
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    PROCTORING_DISPOSITION {
        BIGINT id PK
        BIGINT exam_id FK
        BIGINT student_id FK
        VARCHAR status
        VARCHAR remark
        BIGINT handled_by FK
        DATETIME handled_at
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    OPERATION_AUDIT_LOG {
        BIGINT id PK
        BIGINT operator_id FK
        VARCHAR operator_username
        VARCHAR operator_roles
        VARCHAR action
        VARCHAR target_type
        VARCHAR target_id
        VARCHAR request_method
        VARCHAR request_path
        VARCHAR request_ip
        TEXT detail
        VARCHAR status
        VARCHAR error_message
        DATETIME operate_time
        DATETIME create_time
        DATETIME update_time
        BIGINT create_by
        BIGINT update_by
    }

    SYS_USER ||--o{ SYS_USER_ROLE : user_id
    SYS_ROLE ||--o{ SYS_USER_ROLE : role_id
    SYS_USER ||--o| STUDENT_PROFILE : user_id
    SYS_USER ||--o| TEACHER_PROFILE : user_id

    SUBJECT ||--o{ TEACHING_CLASS : subject_id
    SYS_USER ||--o{ TEACHING_CLASS : teacher_id
    SYS_USER ||--o{ STUDENT_TEACHING_CLASS : student_id
    SUBJECT ||--o{ STUDENT_TEACHING_CLASS : subject_id
    TEACHING_CLASS ||--o{ STUDENT_TEACHING_CLASS : teaching_class_id

    SUBJECT ||--o{ QUESTION : subject_id
    SYS_USER ||--o{ QUESTION : creator_id
    QUESTION ||--o{ QUESTION_ASSET : question_id
    SYS_USER ||--o{ QUESTION_ASSET : uploader_id

    SUBJECT ||--o{ PAPER : subject_id
    SYS_USER ||--o{ PAPER : teacher_id
    PAPER ||--o{ PAPER_QUESTION : paper_id
    QUESTION ||--o{ PAPER_QUESTION : question_id

    PAPER ||--o{ EXAM : paper_id
    SYS_USER ||--o{ EXAM : publisher_id
    EXAM ||--o{ EXAM_TARGET_CLASS : exam_id
    TEACHING_CLASS ||--o{ EXAM_TARGET_CLASS : class_id

    EXAM ||--o{ EXAM_SESSION : exam_id
    SYS_USER ||--o{ EXAM_SESSION : student_id
    EXAM ||--o{ SUBMISSION : exam_id
    SYS_USER ||--o{ SUBMISSION : student_id
    SUBMISSION ||--o{ SUBMISSION_ANSWER : submission_id
    QUESTION ||--o{ SUBMISSION_ANSWER : question_id
    SUBMISSION_ANSWER ||--o{ SUBJECTIVE_GRADE : submission_answer_id
    SYS_USER ||--o{ SUBJECTIVE_GRADE : teacher_id

    EXAM ||--o{ ANTI_CHEAT_EVENT : exam_id
    SYS_USER ||--o{ ANTI_CHEAT_EVENT : student_id
    EXAM ||--o{ PROCTORING_DISPOSITION : exam_id
    SYS_USER ||--o{ PROCTORING_DISPOSITION : student_id
    SYS_USER ||--o{ PROCTORING_DISPOSITION : handled_by
    SYS_USER ||--o{ OPERATION_AUDIT_LOG : operator_id
```

## 关系摘要

- 权限模型：`sys_user` 通过 `sys_user_role` 关联多个 `sys_role`，后端通过 Spring Security 的角色注解控制接口访问。
- 人员档案：学生、教师扩展信息分别落在 `student_profile`、`teacher_profile`，都与 `sys_user` 一对零或一。
- 教学组织：`subject`、`teaching_class`、`student_teaching_class` 共同表达课程、教学班、学生选课关系。
- 题库与试卷：`question` 归属课程，图片等附件存在 `question_asset`；`paper` 通过 `paper_question` 组合题目。
- 考试执行：`exam` 引用试卷，通过 `exam_target_class` 面向教学班发布；学生开始考试后产生 `exam_session`，交卷后形成 `submission` 与 `submission_answer`。
- 评分分析：客观题由交卷处理流程计算，主观题评分写入 `subjective_grade`，统计分析基于提交、答案、试卷和班级关系聚合。
- 监考反作弊：异常行为记录在 `anti_cheat_event`，证据文件存储在 MinIO，处置结果记录在 `proctoring_disposition`。
- 审计：带 `@AuditOperation` 的管理、题库、组卷、考试、阅卷等操作写入 `operation_audit_log`。

## 备注

- `schema.sql` 标注为 legacy reference，运行期以 Flyway 迁移为准。
- 代码中存在 `class_room`、`class_student` 两个遗留实体和 Mapper，但当前 Flyway 迁移未创建对应表，主流程已使用 `teaching_class` 与 `student_teaching_class`，因此未纳入运行期 E-R 图。
