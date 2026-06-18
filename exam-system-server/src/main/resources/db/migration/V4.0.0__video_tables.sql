USE exam_system;

-- 视频相关表
CREATE TABLE IF NOT EXISTS `videos` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `title` VARCHAR(200) NOT NULL COMMENT '视频标题',
  `description` TEXT COMMENT '视频描述',
  `category_id` BIGINT COMMENT '分类ID',
  `file_url` VARCHAR(500) COMMENT '视频文件URL',
  `cover_url` VARCHAR(500) COMMENT '封面图片URL',
  `duration` INT COMMENT '视频时长（秒）',
  `file_size` BIGINT COMMENT '文件大小（字节）',
  `uploader_name` VARCHAR(100) COMMENT '上传者名称',
  `uploader_type` INT COMMENT '上传者类型：1-用户投稿，2-管理员上传',
  `user_id` BIGINT COMMENT '上传用户ID',
  `admin_id` BIGINT COMMENT '管理员ID',
  `status` INT DEFAULT 0 COMMENT '状态：0-待审核，1-已发布，2-已拒绝，3-已下架',
  `audit_admin_id` BIGINT COMMENT '审核管理员ID',
  `audit_time` DATETIME COMMENT '审核时间',
  `audit_reason` VARCHAR(500) COMMENT '审核原因',
  `view_count` BIGINT DEFAULT 0 COMMENT '观看次数',
  `like_count` BIGINT DEFAULT 0 COMMENT '点赞次数',
  `tags` VARCHAR(500) COMMENT '标签，逗号分隔',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT '视频信息';

CREATE TABLE IF NOT EXISTS `video_categories` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(100) NOT NULL COMMENT '分类名称',
  `description` VARCHAR(500) COMMENT '分类描述',
  `parent_id` BIGINT DEFAULT 0 COMMENT '父级分类ID，0为顶级分类',
  `sort_order` INT DEFAULT 0 COMMENT '排序权重',
  `status` INT DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT '视频分类';

CREATE TABLE IF NOT EXISTS `video_likes` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `video_id` BIGINT NOT NULL COMMENT '视频ID',
  `user_ip` VARCHAR(50) COMMENT '用户IP地址',
  `user_agent` VARCHAR(500) COMMENT '用户代理信息',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  UNIQUE KEY `uk_video_ip` (`video_id`, `user_ip`),
  INDEX `idx_video` (`video_id`)
) COMMENT '视频点赞记录';

CREATE TABLE IF NOT EXISTS `video_views` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `video_id` BIGINT NOT NULL COMMENT '视频ID',
  `user_ip` VARCHAR(50) COMMENT '用户IP地址',
  `user_agent` VARCHAR(500) COMMENT '用户代理信息',
  `view_duration` INT COMMENT '观看时长（秒）',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '观看时间',
  INDEX `idx_video` (`video_id`)
) COMMENT '视频观看记录';
