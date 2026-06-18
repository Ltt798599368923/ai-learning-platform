USE exam_system;

INSERT INTO generated_resource (path_node_id, resource_type, title, content_json, difficulty, created_at) VALUES
(0, 'document', 'Java Intro', '{"content":"# Java Intro\n\n## What is Java\nJava is an object-oriented programming language.\n\n## Features\n1. Cross-platform\n2. Object-oriented\n3. Secure"}', 'L1', NOW()),
(0, 'quiz', 'Java Intro', '{"questions":[{"content":"What is the entry method of Java?","type":"CHOICE","difficulty":"EASY","options":[{"content":"main()","isCorrect":true},{"content":"start()","isCorrect":false},{"content":"run()","isCorrect":false},{"content":"init()","isCorrect":false}],"analysis":"The entry point is main method"}]}', 'L1', NOW()),
(0, 'mindmap', 'Java Intro', '{"topic":"Java Intro","children":[{"topic":"Java History"},{"topic":"Java Features"},{"topic":"Setup"}]}', 'L1', NOW());
