# 如何运行
终端执行

.\mvnw.cmd spring-boot:run

启动服务成功后，打开浏览器输入网址访问：

http://localhost:8080


## 数据
会通过[UserDataInitializer.java](src/main/java/cn/edu/cuc/class10/service/UserDataInitializer.java)
和[VocabularyInitializer.java](src/main/java/cn/edu/cuc/class10/service/VocabularyInitializer.java)
生成模拟用户和真实考纲词汇

## 端口被占用如何解决
netstat -ano | findstr :8080
用于找到占用端口

taskkill /F /PID xxx
将找到的端口写到xxx的位置，把它关掉

# 他人机器使用注意事项
1、数据库使用mysql,可以在终端用mysql命令添加用户

2、外部库至少需要满足：java17：graalvm-ce-17能够提供的功能

3、建表命令：
其他建表 SQL（手动执行）
  ```sql
  
  CREATE TABLE `users` (
  `user_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phase` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `daily_goal` int DEFAULT NULL,
  `last_study_date` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `mastered_words` int DEFAULT NULL,
  `study_streak` int DEFAULT NULL,
  `total_words` int DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`)
  );
  
  CREATE TABLE `words` (
  `word_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `extra_attributes` text COLLATE utf8mb4_unicode_ci,
  `familiarity` int DEFAULT NULL,
  `part_of_speech` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phase` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phonetic` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phrases` text COLLATE utf8mb4_unicode_ci,
  `sentences` text COLLATE utf8mb4_unicode_ci,
  `similar_meanings` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `similar_spellings` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `translation` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `word_type` enum('CUSTOM','SYLLABUS') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`word_id`)
  );

  CREATE TABLE `interactions` (
      `interaction_id` VARCHAR(36) PRIMARY KEY,
      `user_id` VARCHAR(36) NOT NULL,
      `word_id` VARCHAR(36) NOT NULL,
      `feedback` ENUM('known','unknown') NOT NULL,
      `timestamp` BIGINT NOT NULL,
      INDEX idx_user_word_time (`user_id`, `word_id`, `timestamp`)
  );

  CREATE TABLE `user_word_familiarity` (
      `user_id` VARCHAR(36) NOT NULL,
      `word_id` VARCHAR(36) NOT NULL,
      `familiarity` INT NOT NULL,
      `last_update` BIGINT NOT NULL,
      PRIMARY KEY (`user_id`, `word_id`)
  );

  CREATE TABLE `test_sessions` (
      `session_id` VARCHAR(36) PRIMARY KEY,
      `user_id` VARCHAR(36) NOT NULL,
      `question_type` VARCHAR(50),
      `total_questions` INT NOT NULL,
      `correct_count` INT NOT NULL,
      `start_time` BIGINT NOT NULL,
      `end_time` BIGINT NOT NULL,
      `create_time` BIGINT,
      INDEX idx_user_end_time (`user_id`, `end_time`)
  );

  CREATE TABLE `test_answer_records` (
      `record_id` VARCHAR(36) PRIMARY KEY,
      `session_id` VARCHAR(36) NOT NULL,
      `question_index` INT NOT NULL,
      `word_id` VARCHAR(36),
      `question_type` VARCHAR(50),
      `question_content` TEXT,
      `correct_answer` TEXT,
      `user_answer` TEXT,
      `is_correct` BOOLEAN NOT NULL,
      `options_json` TEXT,
      INDEX idx_session_id (`session_id`)
  );

  CREATE TABLE `mistake_words` (
      `record_id` VARCHAR(36) PRIMARY KEY,
      `user_id` VARCHAR(36) NOT NULL,
      `word_id` VARCHAR(36) NOT NULL,
      `create_time` BIGINT NOT NULL,
      `review_count` INT NOT NULL DEFAULT 0,
      `last_review_time` BIGINT,
      INDEX idx_user_id (`user_id`),
      UNIQUE KEY uk_user_word (`user_id`, `word_id`)
  );


# 开发者事项

对项目进行修改之后，务必做好审查并在update.md中按照原有格式添加修改