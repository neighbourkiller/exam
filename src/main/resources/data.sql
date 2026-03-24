INSERT INTO sys_role (id, code, name, create_time, update_time, create_by, update_by)
VALUES
    (1, 'ADMIN', '管理员', NOW(), NOW(), 0, 0),
    (2, 'TEACHER', '教师', NOW(), NOW(), 0, 0),
    (3, 'STUDENT', '学生', NOW(), NOW(), 0, 0)
ON DUPLICATE KEY UPDATE name = VALUES(name), update_time = NOW();

INSERT INTO sys_user (id, username, password, real_name, enabled, create_time, update_time, create_by, update_by)
VALUES
    (1001, 'admin', '{noop}123456', '系统管理员', 1, NOW(), NOW(), 0, 0),
    (1002, 'teacher1', '{noop}123456', '教师A', 1, NOW(), NOW(), 0, 0),
    (1003, 'student1', '{noop}123456', '学生A', 1, NOW(), NOW(), 0, 0)
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), enabled = VALUES(enabled), update_time = NOW();

INSERT INTO sys_user_role (id, user_id, role_id, create_time, update_time, create_by, update_by)
VALUES
    (2001, 1001, 1, NOW(), NOW(), 0, 0),
    (2002, 1002, 2, NOW(), NOW(), 0, 0),
    (2003, 1003, 3, NOW(), NOW(), 0, 0)
ON DUPLICATE KEY UPDATE update_time = NOW();

INSERT INTO class_room (id, name, grade, create_time, update_time, create_by, update_by)
VALUES
    (3001, '软件1班', '2026', NOW(), NOW(), 0, 0)
ON DUPLICATE KEY UPDATE name = VALUES(name), grade = VALUES(grade), update_time = NOW();

INSERT INTO class_student (id, class_id, student_id, create_time, update_time, create_by, update_by)
VALUES
    (4001, 3001, 1003, NOW(), NOW(), 0, 0)
ON DUPLICATE KEY UPDATE update_time = NOW();

INSERT INTO subject (id, name, description, create_time, update_time, create_by, update_by)
VALUES
    (5001, 'Java程序设计', 'Java基础与面向对象', NOW(), NOW(), 0, 0)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), update_time = NOW();
