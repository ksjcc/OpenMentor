-- 面试是否"下沉到长期记忆"（简历/复盘/复习卡是否更新）
-- 幂等写法：列已存在则跳过

SET @ddl := (SELECT IF(COUNT(*) = 0,
        'ALTER TABLE interview_session ADD COLUMN remember_in_memory TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''1 = 本场结果写入长期记忆/画像/复习卡''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_session' AND COLUMN_NAME = 'remember_in_memory');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
