ALTER TABLE interview_session
    ADD COLUMN post_process_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'Interview post-process status: PENDING/PROCESSING/SUCCESS/FAILED',
    ADD COLUMN post_process_retry_count INT NOT NULL DEFAULT 0 COMMENT 'Interview post-process retry count',
    ADD COLUMN post_process_error TEXT NULL COMMENT 'Last interview post-process error message',
    ADD COLUMN post_process_finished_at DATETIME NULL COMMENT 'Last interview post-process finished time';

