CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(64) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME,
    update_time DATETIME,
    create_by BIGINT,
    update_by BIGINT
);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    create_time DATETIME,
    update_time DATETIME,
    create_by BIGINT,
    update_by BIGINT
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_time DATETIME,
    update_time DATETIME,
    create_by BIGINT,
    update_by BIGINT,
    UNIQUE KEY uk_user_role (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS class_room (
    id BIGINT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    grade VARCHAR(32),
    create_time DATETIME,
    update_time DATETIME,
    create_by BIGINT,
    update_by BIGINT
);

CREATE TABLE IF NOT EXISTS class_student (
    id BIGINT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    create_time DATETIME,
    update_time DATETIME,
    create_by BIGINT,
    update_by BIGINT,
    UNIQUE KEY uk_class_student (class_id, student_id)
);

CREATE TABLE IF NOT EXISTS subject (
    id BIGINT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    create_time DATETIME,
    update_time DATETIME,
    create_by BIGINT,
    update_by BIGINT
);

CREATE TABLE IF NOT EXISTS question (
    id BIGINT PRIMARY KEY,
    subject_id BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    options_json TEXT,
    answer TEXT NOT NULL,
    analysis TEXT,
    default_score INT NOT NULL DEFAULT 0,
    creator_id BIGINT,
    create_time DATETIME,
    update_time DATETIME,
    create_by BIGINT,
    update_by BIGINT
);

CREATE TABLE IF NOT EXISTS paper (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    subject_id BIGINT NOT NULL,
    description VARCHAR(255),
    total_score INT NOT NULL DEFAULT 0,
    teacher_id BIGINT,
    create_time DATETIME,
    update_time DATETIME,
    create_by BIGINT,
    update_by BIGINT
);

CREATE TABLE IF NOT EXISTS paper_question (
    id BIGINT PRIMARY KEY,
    paper_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    score INT NOT NULL,
    sort_order INT NOT NULL,
    create_time DATETIME,
    update_time DATETIME,
    create_by BIGINT,
    update_by BIGINT,
    UNIQUE KEY uk_paper_question (paper_id, question_id)
);

CREATE TABLE IF NOT EXISTS exam (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    paper_id BIGINT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    duration_minutes INT NOT NULL,
    pass_score INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    publisher_id BIGINT,
    create_time DATETIME,
    update_time DATETIME,
    create_by BIGINT,
    update_by BIGINT
);

CREATE TABLE IF NOT EXISTS exam_target_class (
    id BIGINT PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    create_time DATETIME,
    update_time DATETIME,
    create_by BIGINT,
    update_by BIGINT,
    UNIQUE KEY uk_exam_class (exam_id, class_id)
);

CREATE TABLE IF NOT EXISTS exam_session (
    id BIGINT PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    start_time DATETIME,
    end_time DATETIME,
    last_snapshot_time DATETIME,
    create_time DATETIME,
    update_time DATETIME,
    create_by BIGINT,
    update_by BIGINT,
    UNIQUE KEY uk_exam_student_session (exam_id, student_id)
);

CREATE TABLE IF NOT EXISTS submission (
    id BIGINT PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    objective_score INT,
    subjective_score INT,
    total_score INT,
    pass_flag TINYINT,
    submitted_at DATETIME,
    create_time DATETIME,
    update_time DATETIME,
    create_by BIGINT,
    update_by BIGINT,
    UNIQUE KEY uk_exam_student_submission (exam_id, student_id)
);

CREATE TABLE IF NOT EXISTS submission_answer (
    id BIGINT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    answer_text TEXT,
    objective_correct TINYINT,
    objective_score INT,
    subjective_score INT,
    final_answer TINYINT,
    source VARCHAR(32),
    create_time DATETIME,
    update_time DATETIME,
    create_by BIGINT,
    update_by BIGINT
);

CREATE TABLE IF NOT EXISTS subjective_grade (
    id BIGINT PRIMARY KEY,
    submission_answer_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    score INT NOT NULL,
    comment VARCHAR(255),
    graded_at DATETIME,
    create_time DATETIME,
    update_time DATETIME,
    create_by BIGINT,
    update_by BIGINT
);

CREATE TABLE IF NOT EXISTS anti_cheat_event (
    id BIGINT PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_time DATETIME NOT NULL,
    duration_ms BIGINT,
    payload TEXT,
    create_time DATETIME,
    update_time DATETIME,
    create_by BIGINT,
    update_by BIGINT
);
