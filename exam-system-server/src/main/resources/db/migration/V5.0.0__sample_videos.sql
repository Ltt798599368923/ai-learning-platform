USE exam_system;

INSERT INTO video_categories (name, description, parent_id, sort_order, status, created_at) VALUES
('Java基础', 'Java编程基础教程', 0, 1, 1, NOW()),
('框架开发', 'Spring等框架教程', 0, 2, 1, NOW()),
('数据库', '数据库相关教程', 0, 3, 1, NOW())
ON DUPLICATE KEY UPDATE name=name;

INSERT INTO videos (title, description, category_id, file_url, cover_url, duration, status, view_count, like_count, tags, create_time) VALUES
('Java零基础入门', 'Java基础教程，适合零基础学习', 1, 'https://www.bilibili.com/video/BV1fh411y7R8', 'https://picsum.photos/300/200?random=1', 3600, 1, 15600, 2300, 'Java,基础,入门', NOW()),
('Java面向对象编程', 'Java面向对象编程教程', 1, 'https://www.bilibili.com/video/BV1Kb411W75N', 'https://picsum.photos/300/200?random=2', 2400, 1, 9800, 1500, 'Java,面向对象,OOP', NOW()),
('Spring Boot实战教程', 'Spring Boot快速入门到实战', 2, 'https://www.bilibili.com/video/BV1PE411i7CV', 'https://picsum.photos/300/200?random=3', 5400, 1, 6700, 1200, 'Spring Boot,框架,后端', NOW()),
('MySQL数据库入门到精通', 'MySQL数据库从入门到精通教程', 3, 'https://www.bilibili.com/video/BV1xW411u7ax', 'https://picsum.photos/300/200?random=4', 4800, 1, 8900, 1800, 'MySQL,数据库,SQL', NOW()),
('数据结构与算法精讲', '数据结构和算法面试必备', 1, 'https://www.bilibili.com/video/BV1Eo4y1q7Mf', 'https://picsum.photos/300/200?random=5', 7200, 1, 13400, 2800, '算法,数据结构,面试', NOW());
