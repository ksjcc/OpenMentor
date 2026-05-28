ALTER TABLE interview_session
    ADD COLUMN transcript LONGTEXT NULL COMMENT 'Full interview dialogue transcript';

ALTER TABLE user_profile
    ADD COLUMN resume_text LONGTEXT NULL COMMENT 'Raw resume and project text submitted by the user';
