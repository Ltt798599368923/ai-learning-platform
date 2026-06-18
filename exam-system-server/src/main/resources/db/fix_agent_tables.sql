-- 修复 Agent 会话表（如果 V2.0.0 迁移未执行）
-- 请在 MySQL 中执行此脚本

USE exam_system;

-- 创建 agent_session 表（如果不存在）
CREATE TABLE IF NOT EXISTS `agent_session` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '会话ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `title` VARCHAR(200) COMMENT '会话标题',
    `status` ENUM('active','completed') DEFAULT 'active' COMMENT '状态：active-进行中，completed-已完成',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user` (`user_id`),
    INDEX `idx_status` (`status`),
    CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent对话会话';

-- 创建 agent_message 表（如果不存在）
CREATE TABLE IF NOT EXISTS `agent_message` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    `session_id` BIGINT NOT NULL COMMENT '会话ID',
    `role` ENUM('user','assistant','system') NOT NULL COMMENT '角色：user-用户，assistant-助手，system-系统',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `phase` VARCHAR(50) COMMENT '消息所属阶段（profiling/planning/generating等）',
    `metadata` JSON COMMENT '附加元数据',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_session` (`session_id`),
    INDEX `idx_role` (`role`),
    CONSTRAINT `fk_message_session` FOREIGN KEY (`session_id`) REFERENCES `agent_session` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息记录';

SELECT 'Agent 会话表创建完成！' AS result;
