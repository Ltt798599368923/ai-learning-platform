USE exam_system;

INSERT INTO generated_resource (path_node_id, resource_type, title, content_json, difficulty, created_at) VALUES
(0, 'document', 'Java简介', '{"content":"# Java简介\\n\\n## 什么是Java\\nJava是一门面向对象的编程语言。\\n\\n## 特点\\n1. 跨平台\\n2. 面向对象\\n3. 安全性高"}', 'L1', NOW()),
(0, 'quiz', 'Java简介', '{"questions":[{"content":"Java入口方法？","type":"CHOICE","options":[{"content":"main()","isCorrect":true},{"content":"start()","isCorrect":false}],"analysis":"main方法"}]}', 'L1', NOW()),
(0, 'mindmap', 'Java简介', '{"topic":"Java","children":[{"topic":"历史"},{"topic":"特点"}]}', 'L1', NOW()),
(0, 'document', '变量与数据类型', '{"content":"# 变量\\n\\n变量是存储数据的容器。\\n\\n## 类型\\n- int\\n- double\\n- String"}', 'L1', NOW()),
(0, 'quiz', '变量与数据类型', '{"questions":[{"content":"int占几字节？","type":"CHOICE","options":[{"content":"4","isCorrect":true},{"content":"8","isCorrect":false}],"analysis":"int是32位"}]}', 'L1', NOW()),
(0, 'mindmap', '变量与数据类型', '{"topic":"变量","children":[{"topic":"基本类型"},{"topic":"引用类型"}]}', 'L1', NOW()),
(0, 'document', '运算符', '{"content":"# 运算符\\n\\n## 算术\\n+ - * / %\\n\\n## 比较\\n== != > <"}', 'L1', NOW()),
(0, 'quiz', '运算符', '{"questions":[{"content":"5%2=?","type":"CHOICE","options":[{"content":"1","isCorrect":true},{"content":"2","isCorrect":false}],"analysis":"取模"}]}', 'L1', NOW()),
(0, 'mindmap', '运算符', '{"topic":"运算符","children":[{"topic":"算术"},{"topic":"比较"}]}', 'L1', NOW()),
(0, 'document', '控制流', '{"content":"# 控制流\\n\\n## if-else\\n## for循环\\n## while循环"}', 'L1', NOW()),
(0, 'quiz', '控制流', '{"questions":[{"content":"for循环顺序？","type":"CHOICE","options":[{"content":"初始化-判断-执行-更新","isCorrect":true},{"content":"判断-初始化","isCorrect":false}],"analysis":"先初始化"}]}', 'L1', NOW()),
(0, 'mindmap', '控制流', '{"topic":"控制流","children":[{"topic":"条件"},{"topic":"循环"}]}', 'L1', NOW()),
(0, 'document', '方法', '{"content":"# 方法\\n\\n定义和调用方法\\n\\n## 重载\\n方法名相同，参数不同"}', 'L1', NOW()),
(0, 'quiz', '方法', '{"questions":[{"content":"重载条件？","type":"CHOICE","options":[{"content":"方法名相同参数不同","isCorrect":true},{"content":"返回值不同","isCorrect":false}],"analysis":"参数列表不同"}]}', 'L1', NOW()),
(0, 'mindmap', '方法', '{"topic":"方法","children":[{"topic":"定义"},{"topic":"重载"}]}', 'L1', NOW()),
(0, 'document', '数组', '{"content":"# 数组\\n\\n## 定义\\nint[] arr = new int[5];\\n\\n## 访问\\narr[0]"}', 'L1', NOW()),
(0, 'quiz', '数组', '{"questions":[{"content":"数组下标从？","type":"CHOICE","options":[{"content":"0","isCorrect":true},{"content":"1","isCorrect":false}],"analysis":"从0开始"}]}', 'L1', NOW()),
(0, 'mindmap', '数组', '{"topic":"数组","children":[{"topic":"定义"},{"topic":"遍历"}]}', 'L1', NOW()),
(0, 'document', '面向对象', '{"content":"# 面向对象\\n\\n## 类和对象\\n## 封装继承多态"}', 'L1', NOW()),
(0, 'quiz', '面向对象', '{"questions":[{"content":"三大特性？","type":"CHOICE","options":[{"content":"封装继承多态","isCorrect":true},{"content":"抽象封装继承","isCorrect":false}],"analysis":"OOP三大特性"}]}', 'L1', NOW()),
(0, 'mindmap', '面向对象', '{"topic":"OOP","children":[{"topic":"封装"},{"topic":"继承"},{"topic":"多态"}]}', 'L1', NOW());
