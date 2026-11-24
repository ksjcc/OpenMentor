-- 简历库 + 面试上下文 + 逐轮评分
--
-- 这个项目没有引入 Flyway 运行时，迁移脚本需要手工执行；因此这里全部写成
-- 幂等语句：重复执行、或库里已存在某列时都不会报错。

CREATE TABLE IF NOT EXISTS resume (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT 'Owner user id',
    title VARCHAR(120) NOT NULL COMMENT 'Resume name shown in the library',
    content MEDIUMTEXT NOT NULL COMMENT 'Resume plain text, extracted from pdf/docx or pasted',
    parsed_json MEDIUMTEXT NULL COMMENT 'Structured profile JSON parsed from the resume',
    file_name VARCHAR(255) NULL COMMENT 'Original uploaded file name',
    file_type VARCHAR(16) NULL COMMENT 'TEXT / PDF / DOCX / TXT',
    target_role VARCHAR(120) NULL COMMENT 'Target role this resume is prepared for',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '1 = default resume picked by default when starting an interview',
    created_at DATETIME NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_resume_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'User resume library';

-- ---------------------------------------------------------------------------
-- 下面的 add_column_if_missing 用 information_schema 判断，避免 "Duplicate column"。
-- ---------------------------------------------------------------------------

SET @ddl := (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE interview_session ADD COLUMN topic VARCHAR(255) NULL AFTER interview_type',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_session' AND COLUMN_NAME = 'topic');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE interview_session ADD COLUMN resume_id BIGINT NULL AFTER topic',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_session' AND COLUMN_NAME = 'resume_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE interview_session ADD COLUMN project_summary TEXT NULL AFTER resume_id',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_session' AND COLUMN_NAME = 'project_summary');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 会话总分的列早就存在（实体里一直有），这里只保证它一定在
SET @ddl := (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE interview_session ADD COLUMN score INT NULL',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_session' AND COLUMN_NAME = 'score');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE interview_message ADD COLUMN score INT NULL AFTER content',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_message' AND COLUMN_NAME = 'score');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE interview_message ADD COLUMN score_detail TEXT NULL AFTER score',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_message' AND COLUMN_NAME = 'score_detail');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
