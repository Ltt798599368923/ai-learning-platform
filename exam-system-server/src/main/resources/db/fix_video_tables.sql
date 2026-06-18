-- 修复视频相关表结构，使其与Java实体类匹配
-- 请在MySQL中执行此脚本

USE exam_system;

-- 1. 修复 video_categories 表：create_time -> created_at
ALTER TABLE `video_categories` 
  CHANGE COLUMN `create_time` `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- 2. 修复 video_likes 表：删除旧列，添加新列
ALTER TABLE `video_likes` 
  DROP INDEX `uk_video_user`,
  DROP COLUMN `user_id`,
  DROP COLUMN `create_time`,
  ADD COLUMN `user_ip` VARCHAR(50) COMMENT '用户IP地址' AFTER `video_id`,
  ADD COLUMN `user_agent` VARCHAR(500) COMMENT '用户代理信息' AFTER `user_ip`,
  ADD COLUMN `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间' AFTER `user_agent`,
  ADD UNIQUE KEY `uk_video_ip` (`video_id`, `user_ip`),
  ADD INDEX `idx_video` (`video_id`);

-- 3. 修复 video_views 表：删除旧列，添加新列
ALTER TABLE `video_views` 
  DROP COLUMN `user_id`,
  DROP COLUMN `view_time`,
  ADD COLUMN `user_ip` VARCHAR(50) COMMENT '用户IP地址' AFTER `video_id`,
  ADD COLUMN `user_agent` VARCHAR(500) COMMENT '用户代理信息' AFTER `user_ip`,
  ADD COLUMN `view_duration` INT COMMENT '观看时长（秒）' AFTER `user_agent`,
  ADD COLUMN `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '观看时间' AFTER `view_duration`;

SELECT '表结构修复完成！' AS result;
