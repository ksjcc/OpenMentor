-- 复习训练：闪卡主题 + 每日练习记录
-- 全部幂等，重复执行安全

-- 1) flashcard.topic：闪卡所属知识点主题（来自面试主题/知识图谱实体）
SET @ddl := (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE flashcard ADD COLUMN topic VARCHAR(120) NULL COMMENT ''知识点主题'' AFTER answer',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flashcard' AND COLUMN_NAME = 'topic');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) review_record.topic：答题时把主题快照下来，后面做"关联知识点"历史题
SET @ddl := (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE review_record ADD COLUMN topic VARCHAR(120) NULL AFTER result',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review_record' AND COLUMN_NAME = 'topic');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) review_record.practice_date：这道题归属哪一天的练习
SET @ddl := (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE review_record ADD COLUMN practice_date DATE NULL AFTER topic',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review_record' AND COLUMN_NAME = 'practice_date');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) 按 (user_id, practice_date) 统计每天正确率
SET @ddl := (SELECT IF(COUNT(*) = 0,
        'CREATE INDEX idx_review_record_user_date ON review_record(user_id, practice_date)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review_record' AND INDEX_NAME = 'idx_review_record_user_date');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
