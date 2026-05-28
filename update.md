# 项目开发更新日志

## 2026-05-21

### 构建项目初始框架
- **成员**: 蔡奕麟
- **工作内容**:
  - 搭建项目基础框架

## 2026-05-22

### 用户模块完善与数据初始化
- **成员**: 罗勋曜
- **审查**: 蔡奕麟
- **工作内容**:
  
  #### 1. 用户信息管理功能
  - 实现右上角用户名入口，点击可进入个人中心
  - 完成个人中心页面（profile.html）开发
  - 支持用户修改：用户名、密码、学习阶段、学习类型、每日学习目标
  - 实现账号删除功能
  - 后端接口完善：UserController 增加用户更新和删除接口
  
  #### 2. 模拟数据生成
  - 通过 MySQL 直接插入50条模拟用户数据
  - 模拟数据包含不同学习阶段（primary/junior/senior）
  - 模拟数据包含不同用户类型（quiz/memory）
  - 每条数据包含完整的学习计划字段
  - 数据库中共有51条用户数据（1条原有 + 50条模拟）
  
  #### 3. 学习计划功能
  - 完成学习计划页面（study_plan.html）开发
  - 展示用户学习计划相关属性：
    - 每日学习目标（dailyGoal）
    - 已学习单词总数（totalWords）
    - 已掌握单词数（masteredWords）
    - 连续学习天数（studyStreak）
  - 实现学习进度可视化（进度条）
  - 提供学习建议卡片
  - 从 dashboard 首页可点击进入学习计划页面
  
- **修改文件**:
  - `src/main/resources/static/profile.html` - 新建个人中心页面
  - `src/main/resources/static/study_plan.html` - 新建学习计划页面
  - `src/main/resources/static/dashboard.html` - 添加用户名链接到个人中心
  - `src/main/java/cn/edu/cuc/class10/User.java` - 完善用户类
  - `src/main/java/cn/edu/cuc/class10/controller/UserController.java` - 完善用户管理接口
  - `src/main/java/cn/edu/cuc/class10/service/UserService.java` - 完善用户管理服务
  - `src/main/java/cn/edu/cuc/class10/DataInitializer.java` - 优化数据初始化逻辑
  - `数据库 users 表` - 插入50条模拟用户数据


## 2026-05-22

### 单词管理基础搭建
- **成员**: 唐森雨、刘文浩
- **审查**: 蔡奕麟
- **工作内容**:
  
  #### 1. 实体类设计（Entity层）
  - **User.java** - 用户实体类
    - 基础字段：userId（UUID主键）、username（用户名）、password（密码）
    - 学习属性：phase（学习阶段：primary/junior/senior）、userType（学习类型：quiz/memory）
    - 学习计划字段：dailyGoal（每日目标）、totalWords（已学习总数）、masteredWords（已掌握数）、studyStreak（连续学习天数）、lastStudyDate（最后学习日期）
  
  - **Word.java** - 单词实体类
    - 基础字段：wordId（UUID主键）、content（单词内容）、translation（释义）、phonetic（音标）
    - 词性字段：partOfSpeech（词性字符串，支持 "NOUN/VERB" 等多词性）
    - 类型字段：wordType（枚举：SYLLABUS考纲词汇/CUSTOM用户自建）、phase（适用阶段）
    - 学习字段：familiarity（熟悉度，初始值0）
    - 扩展字段：similarMeanings（近义表达）、similarSpellings（相似拼写）、extraAttributes（额外属性）
    - 教学字段：phrases（短语列表）、sentences（例句列表）
  
  - **WordType.java** - 单词类型枚举
    - SYLLABUS：考纲词汇
    - CUSTOM：用户自建词汇
  
  - **PartOfSpeech.java** - 词性枚举
    - 包含8种词性：NOUN（名词）、VERB（动词）、ADJECTIVE（形容词）、ADVERB（副词）
    - PREPOSITION（介词）、CONJUNCTION（连词）、PRONOUN（代词）、INTERJECTION（感叹词）
    - 每种词性提供中文显示名称（displayName）
  
  #### 2. 业务逻辑与控制层
  - 创建 WordService 服务类，处理单词相关业务逻辑
  - 创建 WordController 控制器，提供 RESTful API 接口
  - 添加 Jackson-databind 依赖，支持 JSON 数据处理
  
  #### 3. 词汇数据初始化
  - 添加临时考纲词汇表 vocabulary
  - 创建 DataInitializationService，实现考纲词汇自动导入功能
  - 应用启动时自动初始化词汇数据
  
  #### 4. 前端页面开发
  - 搭建单词学习页面 study.html，并修改主页跳转链接
  - 添加词汇表展示页面 vocabulary.html
  - 添加单词详情界面，显示完整单词信息
  - 词汇表添加分页功能，防止大量数据导致卡顿
  - 修复筛选条件保持问题：确保重新加载后保持当前选择的筛选条件（如"自建词汇"）
  
- **修改文件**:
  - `src/main/java/cn/edu/cuc/class10/entity/User.java` - 完善用户实体类，添加学习计划字段
  - `src/main/java/cn/edu/cuc/class10/entity/Word.java` - 创建单词实体类，包含13个字段
  - `src/main/java/cn/edu/cuc/class10/entity/WordType.java` - 新建单词类型枚举（考纲/自建）
  - `src/main/java/cn/edu/cuc/class10/entity/PartOfSpeech.java` - 新建词性枚举（8种词性）
  - `src/main/java/cn/edu/cuc/class10/repository/UserRepository.java` - 用户数据访问层
  - `src/main/java/cn/edu/cuc/class10/repository/WordRepository.java` - 新建单词数据访问层
  - `src/main/java/cn/edu/cuc/class10/service/UserService.java` - 完善用户管理服务
  - `src/main/java/cn/edu/cuc/class10/service/WordService.java` - 新建单词业务逻辑层
  - `src/main/java/cn/edu/cuc/class10/service/UserDataInitializer.java` - 用户数据初始化（50条模拟数据）
  - `src/main/java/cn/edu/cuc/class10/service/VocabularyInitializer.java` - 词汇表数据初始化（从JSON导入）
  - `src/main/java/cn/edu/cuc/class10/controller/UserController.java` - 完善用户管理接口
  - `src/main/java/cn/edu/cuc/class10/controller/WordController.java` - 新建单词控制器
  - `src/main/resources/static/study.html` - 新建单词学习页面
  - `src/main/resources/static/dashboard.html` - 优化导航链接
  - `src/main/resources/vocabulary.json` - 添加考纲词汇数据
  - `pom.xml` - 添加 jackson-databind 依赖


## 2026-05-23

### 单词测试模块增强：交互记录持久化与熟悉度缓存
- **成员**: 蔡奕麟
- **审查**: 蔡奕麟
- **工作内容**:

  #### 1. 后端新增组件（支持测试结果存储）
  - **实体类**:
    - `Interaction.java` - 交互记录实体（userId、wordId、feedback、timestamp）
    - `UserWordFamiliarity.java` - 用户-单词熟悉度缓存实体（联合主键 userId + wordId，familiarity 0~100，lastUpdate）
    - `UserWordFamiliarityId.java` - 复合主键类
  - **Repository 层**:
    - `InteractionRepository.java` - 提供按用户和单词查询最近交互时间等方法
    - `UserWordFamiliarityRepository.java` - 缓存表 CRUD
  - **Service 层**:
    - `InteractionService.java` - 实现 `recordInteraction` 方法：保存交互记录、增量更新熟悉度（正确+10、错误-15）、超过24小时额外衰减2%，结果存储至 `user_word_familiarity` 表
  - **Controller 层**:
    - `InteractionController.java` - 提供 `POST /api/interaction/record` 接口，接收 userId、wordId、feedback，调用 Service 记录

  #### 2. 前端测试页面改造
  - 完善 `test.html`,做了交互优化
  
  #### 3. 数据库表结构变更
  - 新增表 `interactions`（交互记录）
  - 新增表 `user_word_familiarity`（用户单词熟悉度缓存）
  - 新增表 `test_sessions`（测试会话）
  - 新增表 `test_answer_records`（答题明细记录）

### 测试报告与仪表盘功能开发
- **成员**: 蔡奕麟
- **审查**: 蔡奕麟
- **工作内容**:

  #### 1. 后端报告服务开发
  - **实体类**:
    - `TestSession.java` - 测试会话实体（sessionId, userId, questionType, totalQuestions, correctCount, startTime, endTime）
    - `TestAnswerRecord.java` - 答题明细实体（recordId, sessionId, questionIndex, wordId, questionType, questionContent, correctAnswer, userAnswer, isCorrect, optionsJson）
  - **Repository 层**:
    - `TestSessionRepository.java` - 提供按用户查询最近测试、按时间段查询等方法
    - `TestAnswerRecordRepository.java` - 提供按会话ID查询答题明细的方法
  - **DTO 层**:
    - `DashboardDataResponse.java` - 仪表盘数据响应对象，包含总单词数、掌握数、平均正确率、每日正确率趋势、最近5次测试记录、易错词TOP5
  - **Service 层**:
    - `ReportService.java` - 实现 `getDashboardData()` 方法：
      - 统计用户可访问的单词总数（考纲阶段内+自建词汇）
      - 计算掌握单词数（熟悉度≥70）
      - 计算近7天测试平均正确率
      - 生成每日正确率趋势数据（按天聚合）
      - 获取最近5次测试记录（按结束时间倒序）
      - 使用原生SQL聚合查询易错词TOP5（通过interaction表统计错误次数）
  - **Controller 层**:
    - `ReportController.java` - 提供 `GET /api/report/dashboard` 接口，接收userId，返回完整仪表盘数据

  #### 2. 前端报告页面开发
  - **dashboard.html** - 首页仪表盘
    - 展示学习进度卡片（总单词数、掌握数、进度条）
    - 展示平均正确率和近7日正确率趋势图
    - 展示最近5次测试记录列表，支持点击查看测试详情
    - 展示易错词TOP5列表
  - **learning_report.html** - 学习报告页面
    - 从 dashboard 跳转进入
    - 展示详细的统计数据和分析
    - 点击测试记录可查看该次测试的详细答题情况
  - **report.html** - 测试详情页面
    - 展示单次测试的完整信息（题型、总分、正确数、用时）
    - 逐题展示答题情况（题目内容、正确答案、用户答案、对错标注）
    - 智能返回按钮：根据来源页面（fromPage参数）动态显示“返回首页”或“返回学习报告”
    - 支持“再次测试”功能

  #### 3. 测试数据保存流程
  - 用户在 test.html 完成测试后，调用 `/api/test/saveResult` 保存测试会话
  - 每道题提交时调用 `/api/test/submit` 记录交互并更新熟悉度
  - 可选择实时调用 `/api/test/saveAnswerRecord` 保存每题答题详情
  - 测试完成后跳转到 report.html 查看详细报告
  - 报告数据可通过 learning_report.html 随时查看历史测试

  #### 4. 修改/新增文件清单
  - `src/main/java/cn/edu/cuc/class10/entity/TestSession.java` - 新增
  - `src/main/java/cn/edu/cuc/class10/entity/TestAnswerRecord.java` - 新增
  - `src/main/java/cn/edu/cuc/class10/repository/TestSessionRepository.java` - 新增
  - `src/main/java/cn/edu/cuc/class10/repository/TestAnswerRecordRepository.java` - 新增
  - `src/main/java/cn/edu/cuc/class10/dto/DashboardDataResponse.java` - 新增
  - `src/main/java/cn/edu/cuc/class10/service/ReportService.java` - 新增
  - `src/main/java/cn/edu/cuc/class10/controller/ReportController.java` - 新增
  - `src/main/resources/static/dashboard.html` - 添加仪表盘数据统计展示
  - `src/main/resources/static/learning_report.html` - 新建学习报告页面
  - `src/main/resources/static/report.html` - 新建测试详情页面，支持智能返回逻辑
  - `src/main/java/cn/edu/cuc/class10/entity/Interaction.java` - 新增
  - `src/main/java/cn/edu/cuc/class10/entity/UserWordFamiliarity.java` - 新增
  - `src/main/java/cn/edu/cuc/class10/entity/UserWordFamiliarityId.java` - 新增
  - `src/main/java/cn/edu/cuc/class10/repository/InteractionRepository.java` - 新增
  - `src/main/java/cn/edu/cuc/class10/repository/UserWordFamiliarityRepository.java` - 新增
  - `src/main/java/cn/edu/cuc/class10/service/InteractionService.java` - 新增
  - `src/main/java/cn/edu/cuc/class10/controller/InteractionController.java` - 新增
  - `src/main/resources/static/test.html` - 重构

  #### 5. 建表 SQL（手动执行）
  ```sql
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

  **生词本功能开发**：
  - 创建 MistakeWord 实体类，记录用户标记为“不熟悉”的单词
  - 创建 MistakeWordRepository，支持查询、添加、移除操作
  - 创建 MistakeController，提供 RESTful API（/api/mistake/list, /add, /remove, /count）
  - 创建 mistake_book.html 页面，展示用户的生词列表，支持复习和移除
  - dashboard.html 将“错题本”模块改为“生词本”，链接到 mistake_book.html

## 2026-05-24

### 学习模式和生词本开发
- **成员**: 蔡奕麟
- **审查**: 蔡奕麟
- **工作内容**:
  **学习模式功能开发**：
  - 在 WordService 中添加 getLowFamiliarityWords() 方法，获取熟悉度 ≤70 的单词
  - 添加 updateWordFamiliarity() 方法，根据用户反馈更新熟悉度
  - 添加 addToMistakeBook() 方法，自动将不熟悉的单词加入生词本
  - 在 WordController 中添加 /api/words/study/lowFamiliarity 接口，获取待学习单词
  - 添加 /api/words/study/updateFamiliarity 接口，处理四选项反馈（熟悉/模糊/不熟悉/已掌握）
  - 实现熟悉度调整逻辑：熟悉→90%，模糊→当前80%，不熟悉→30%+加入生词本，已掌握→100%
  - 重构 study.html，在学习控制面板中添加“学习模式”区域
  - 实现词汇域选择（全部/考纲词汇/自建词汇）和学习顺序选择（顺序/乱序）
  - 创建卡片式学习界面，显示单词、音标、释义和四个反馈按钮
  - 实现 Fisher-Yates 洗牌算法支持乱序学习
  - 学习完成后自动刷新单词列表

  **生词本功能开发**：
  - 在 TestService 的 submitAnswer() 方法中添加错题自动收集逻辑
  - 当用户答错题目时，自动调用 addToMistakeBookIfNotExists() 方法
  - 通过 findByUserIdAndWordId() 检查是否已存在，避免重复添加
  - 只有答错的单词才会被加入生词本，答对的不会

## 2026-05-28

### 优化交互体验，修改部分错误
- **成员**: 蔡奕麟
- **审查**: 蔡奕麟
- **工作内容**:

  **相似词群自动计算**：
  - 新增 SimilarityInitializer，启动时自动计算所有单词的相似词义群和相似词样群
  - 相似词样群（similarSpellings）：按首字母分组 + 长度过滤，计算 Levenshtein 编辑距离 ≤2 的形似词
  - 相似词义群（similarMeanings）：按词性分组，计算中文释义汉字重叠率 ≥30% 的近义词
  - 每个单词每类最多保存 8 个相似词

  **测试干扰项优化**：
  - 英译中选择：干扰项优先从该词的相似词样群（形似词）的释义中抽取
  - 中译英选择：干扰项优先从该词的相似词义群（近义词）的拼写中抽取
  - 相似词不够时回退到随机选取

  **熟悉度改为按用户彻底隔离**：
  - 删除 Word 实体类的 familiarity 字段（原为全局共享，不同用户数据会混淆）
  - 学习卡片操作只写入 user_word_familiarity 表（按 userId + wordId 隔离）
  - 学习模式、学习报告、测试系统全部统一读取 UserWordFamiliarity
  - 未学过的单词默认熟悉度 50

  **MySQL 需要手动执行**：
  ```sql
  ALTER TABLE words MODIFY COLUMN similar_meanings TEXT;
  ALTER TABLE words MODIFY COLUMN similar_spellings TEXT;
  ALTER TABLE words DROP COLUMN familiarity;
  ```

## 2026-05-29

### 性能优化和生词本优化
- **成员**: 蔡奕麟
- **审查**: 蔡奕麟
- **工作内容:**

  **修复 N+1 查询性能问题**：
  - getLowFamiliarityWords 在 Service 层预加载 familiarityMap 并直接构建返回数据
  - 移除了 Controller 层对每个单词单独调用 getUserFamiliarity 的 N+1 查询
  - 原来 7000+ 个单词要多查 7000+ 次数据库

  **生词本复习改为卡片模式**
  - 新增 /api/mistake/review/list 接口，返回生词本单词含用户熟悉度
  - 新增 /api/mistake/review/known 接口：移出生词本，熟悉度设为 max(当前,70)
  - 新增 /api/mistake/review/unfamiliar 接口：复习时标记"不认识"，增加复习次数
  - 前端改为全屏卡片复习模式：
    - 在生词本页面点击"开始复习"或单个"复习"按钮弹出逐词卡片
    - 卡片依次显示单词、音标、词性、释义、当前熟悉度
    - "认识" → 移出生词本 + 熟悉度设为 max(当前,70) → 自动下一张
    - "不认识" → 仅记录复习次数，保留在生词本 → 自动下一张
    - 所有词复习完后显示鼓励语和掌握统计
    - 复习过程中已"认识"的词会从列表实时移除

  **学习报告增加单词列表**:
  - 新增 /api/report/wordList?userId=X&filter=all|mastered|unmastered 接口
  - 点击"总词汇量"弹窗显示所有可访问单词列表（词 + 释义 + 熟悉度%）
  - 弹窗支持按全部/已掌握/未掌握筛选切换
  - 点击"已掌握单词"直接跳转到已掌握筛选列表