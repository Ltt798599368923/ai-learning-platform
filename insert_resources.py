import pymysql

conn = pymysql.connect(
    host='localhost',
    user='root',
    password='12345678',
    database='exam_system',
    charset='utf8mb4'
)

cursor = conn.cursor()

sql = """
INSERT INTO generated_resource (path_node_id, resource_type, title, content_json, difficulty, created_at) VALUES
(0, 'document', 'Java简介', '{"content":"# Java简介\\n\\n## 什么是Java\\nJava是一门面向对象的编程语言。"}', 'L1', NOW()),
(0, 'quiz', 'Java简介', '{"questions":[{"content":"Java程序的入口方法是什么？","type":"CHOICE","difficulty":"EASY","options":[{"content":"main()","isCorrect":true},{"content":"start()","isCorrect":false}],"analysis":"Java程序的入口是main方法"}]}', 'L1', NOW()),
(0, 'mindmap', 'Java简介', '{"topic":"Java简介","children":[{"topic":"Java历史"},{"topic":"Java特点"}]}', 'L1', NOW())
"""

try:
    cursor.execute(sql)
    conn.commit()
    print("预存资源插入成功！")
except Exception as e:
    print(f"插入失败: {e}")
finally:
    conn.close()
