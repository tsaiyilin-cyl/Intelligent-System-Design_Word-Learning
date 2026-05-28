# 面向学生的单词管家 - 设计文档

> 版本：1.3  
> 对应需求文档：第五组需求文档 v2.1（终稿）  
> 编写日期：2026-05-17  
> 作者：Group5

---

## 1. 设计概述

### 1.1 设计目标

本设计文档旨在为“面向学生的单词管家”系统提供技术实现方案，满足需求文档中的所有功能与非功能需求。系统采用 **前后端分离** 架构，前端为网页应用，后端提供 RESTful API，数据存储使用本地数据库（IndexedDB），亦支持后续迁移至云端。

实体模型已做精简，仅保留必填字段，但所有业务功能（学习计划、熟悉度计算、测试、报告、照片识词等）均通过算法或关联数据完整实现。

### 1.2 技术栈建议

| 层次       | 技术选型                             |
| ---------- | ------------------------------------ |
| 前端       | Vue 3 + TypeScript + Element Plus    |
| 状态管理   | Pinia                                |
| 本地存储   | IndexedDB（封装为 Dexie）            |
| 后端（可选）| Node.js + Express（如需要云端同步）  |
| 大模型接入 | 调用 OpenAI / 国内大模型 API         |
| 图像识别   | 百度 / 腾讯 OCR 物体识别 API         |

> 注：为满足“多用户数据隔离”与“离线可用”，优先采用纯前端 + IndexedDB 方案，支持多账号本地切换。

---

## 2. 系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────┐
│                       前端网页应用                        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│  │用户管理  │ │单词学习  │ │单词测试  │ │学习计划  │   │
│  │模块      │ │模块      │ │模块      │ │模块      │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘   │
│  ┌──────────┐ ┌──────────┐                              │
│  │个性化模块│ │照片识词  │                              │
│  └──────────┘ └──────────┘                              │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                    统一数据服务层（封装 IndexedDB）        │
│  - 用户数据隔离（按 userID 分库/分表）                    │
│  - 单词表、交互记录、计划、报告存储                       │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                   外部服务（可选）                        │
│  - 大模型 API（生成学习建议）                             │
│  - 图像识别 API（照片识词）                               │
└─────────────────────────────────────────────────────────┘
```

### 2.2 数据隔离设计

- 每个用户拥有唯一 `userId`。
- IndexedDB 中创建以 `user_{userId}` 命名的数据库，或使用同一数据库的不同对象存储，所有查询均携带 `userId` 过滤条件。
- 切换账号时清空前端状态，重新加载新用户数据。

---

## 3. 模块设计（含简化实体）

### 3.1 用户管理模块

#### 3.1.1 功能对应需求

- FR-01-01 ~ FR-01-05

#### 3.1.2 核心实体（简化）

```typescript
interface User {
  userId: string;      // UUID
  username: string;    // 唯一登录名
  phase: 'primary' | 'junior' | 'senior';  // 学习阶段
  userType: 'quiz' | 'memory';             // 刷题型 / 记忆型
}
```

> **功能覆盖说明**：
> - 学习计划目标（如“30天背完考纲词汇”）不在 User 中存储，而是单独保存在 `StudyPlan` 实体中（见 3.4）。
> - 用户配置初始化：根据 `phase` 加载对应考纲词库。
> - 自建单词：通过 `Word.isCustom=true` 实现。

#### 3.1.3 关键流程

- **注册/登录**：前端校验用户名唯一性，存储 User 对象。
- **配置初始化**：根据 `phase` 加载内置考纲词库 JSON，批量写入 `words` 表（`isCustom=false`）。
- **自建单词**：用户输入单词及释义，生成 `wordId`，存入 `words` 表（`isCustom=true`）。

---

### 3.2 单词学习与记录交互模块

#### 3.2.1 数据结构（简化）

```typescript
interface Word {
  wordId: string;
  content: string;        // 单词原文
  translation: string;    // 中文释义
  phase: string;          // 所属考纲阶段（若为自建，phase = 'custom'）
  isCustom: boolean;      // 是否自建
}

interface Interaction {
  interactionId: string;
  userId: string;
  wordId: string;
  feedback: 'known' | 'unknown' | 'vague'; // 认识/不认识/模糊
  timestamp: number;       // 毫秒时间戳
}
```

> **移除字段说明**：
> - `phonetic`, `tags` 可在 UI 中通过额外扩展实现，不作为核心存储。
> - `source`（卡片/测试）不需要区分，因为交互记录已足够用于熟悉度计算。

#### 3.2.2 熟悉度计算

**算法**（与需求一致，动态计算）：

1. 获取单词的所有交互记录（按时间排序）。
2. 计算正确率：`correctRate = (known次数 + vague次数 * 0.5) / 总交互次数`。  
   （注：vague 视为 0.5 分）
3. 计算基础分：`base = correctRate * 100`。
4. 时间衰减：最近一次交互距今 `daysSinceLast`。衰减系数采用艾宾浩斯曲线简化版：  
   `decay = max(0, 1 - daysSinceLast / 30)`（30天内线性衰减，之后为0）。
5. 最终熟悉度：`familiarity = base * decay`，结果截断至 0~100。

> **性能优化**：将每个用户每个单词的熟悉度缓存于 `word_familiarity` 表，每次交互后增量更新，避免实时计算。

#### 3.2.3 卡片浏览

- 展示单词 `content` 及 `translation`。
- 用户点击反馈后立即记录 `Interaction`，并重新计算熟悉度，更新缓存。

---

### 3.3 单词测试模块

#### 3.3.1 题型实现（与原设计相同）

| 题型           | 生成方式                                                                 |
| -------------- | ------------------------------------------------------------------------ |
| 英译中选择     | 题干：单词，选项：1个正确中文 + 3个随机中文（从其他单词抽取）            |
| 中译英选择     | 题干：中文，选项：1个正确英文 + 3个随机英文                              |
| 听音选义       | 播放单词读音（Web Speech API 或预录音频），选择正确中文                   |
| 拼写填空       | 题干：中文 + 英文句子中挖空，用户输入单词                                 |

#### 3.3.2 出题策略（核心算法）

**输入**：用户 `userId`，测试题目数量 `N`。  
**输出**：N 个待测单词。

算法步骤：

1. 获取用户当前激活的学习计划，根据计划拆分出今日应复习的单词候选集（见 3.4）。
2. 为每个候选单词计算权重：
    - 基础权重 = `1 - familiarity/100`（生词权重大）。
    - 若用户类型为 `quiz`（刷题型），对最近 3 天内测试过的单词权重 ×0.5。
    - 若用户类型为 `memory`（记忆型），对熟悉度在 40~70 之间的单词权重 ×1.5。
    - 额外增加遗忘曲线权重：距离上次测试间隔 1/2/4/7/15 天的单词权重 ×(1 + 0.2×间隔天数)。
3. 按权重随机抽样 N 个单词（无放回）。

#### 3.3.3 测试流程

- 逐题生成 → 用户作答 → 判定正确/错误 → 记录交互（正确 => feedback='known'，错误 => 'unknown'）→ 下一题。
- 测试结束后展示正确率、错题列表；错题入库 `temp_mistake_list`（前端存储），支持错题重练。

---

### 3.4 学习计划管理模块

#### 3.4.1 数据结构（简化）

```typescript
interface StudyPlan {
  planId: string;
  userId: string;
  targetDays: number;          // 7/30/365
  startDate: number;           // 起始时间戳
  status: 'active' | 'completed' | 'abandoned';
}

interface DailyTask {
  taskId: string;
  planId: string;
  dayIndex: number;            // 第几天（从0开始）
  wordIds: string[];           // 当日需复习的单词ID
}
```

> **说明**：
> - 不存储 `totalWords`，因为单词总数可根据用户的 `phase` 从 `words` 表中动态统计（考纲词数 + 自建词数）。
> - 不存储 `completed` 标识，每日任务是否完成通过当日交互记录数量（大于等于 `wordIds.length` 的一定比例）判断。

#### 3.4.2 任务拆分算法

1. 根据用户 `phase` 获取所有考纲单词（`isCustom=false`），记总数 `total`。
2. 每日新词数：`newPerDay = ceil(total / targetDays)`。
3. 分配每日新词（按单词 ID 顺序或随机顺序），同时根据艾宾浩斯复习间隔（1,2,4,7,15天）为每个单词安排复习日。
4. 生成 `DailyTask.wordIds = [当天新词] + [按复习间隔需复习的旧词]`。
5. 若某单词熟悉度≥90，可自动减少复习频率（跳过部分复习日）。

#### 3.4.3 进度展示

- 已完成天数：当前日期 ≥ startDate + 第 N 天 → 检查当日任务中超过 80% 的单词有交互记录（视为完成）。
- 已掌握单词数：熟悉度 ≥ 80 的单词数量。
- 前端以进度条和百分比展示。

---

### 3.5 个性化模块

#### 3.5.1 学习报告生成（功能完整）

**触发方式**：每周定时或用户手动点击。

**报告内容**（与原需求一致）：

- **词汇掌握统计**：熟悉词（familiarity ≥70）、模糊词（40~69）、生词（<40）的占比（饼图/柱状图）。
- **遗忘曲线提示**：根据用户近一个月交互时间间隔与正确率的变化，绘制用户遗忘曲线并与艾宾浩斯标准曲线对比。
- **混淆分析**：统计用户经常答错的单词对（错误矩阵）。方法：遍历所有错误交互，提取错误单词的相近词（编辑距离≤2 或同义词），计算共现错误次数。

#### 3.5.2 大模型接入

- 将上述统计数据 + 最近 10 条错题记录 格式化为 Prompt，调用大模型 API。
- Prompt 示例（与需求文档一致）：

```
你是学习助手。用户本周学习数据如下：
- 总掌握率：65%
- 常见混淆词对：abandon <-> abundant
- 用户类型：记忆型
请生成一段鼓励语和 3 条具体复习建议，语气亲切。
```  

- 返回的自然语言文本存储于 `Report` 实体的 `content` 字段并渲染。

#### 3.5.3 报告存储（简化）

```typescript
interface Report {
  reportId: string;
  userId: string;
  createTime: number;
  content: string;            // 大模型生成的自然语言建议
  stats: {                    // 统计摘要，便于历史报告对比
    masteredCount: number;    // 熟悉词数量
    fuzzyCount: number;
    unknownCount: number;
  };
}
```

- 前端可通过时间范围查询历史的 `Report`，展示进步趋势。

---

### 3.6 照片识词模块（保留）

#### 3.6.1 交互流程（与原设计相同）

1. 用户上传/拍摄图片。
2. 调用图像识别 API，返回 top 3 候选物体名称（中文）。
3. 将中文翻译为英文（可使用免费翻译 API 或本地简单映射表）。
4. 展示候选英文单词，用户确认后调用 `addCustomWord` 存入 `words` 表（`isCustom=true`）。
5. 识别不准确时可手动编辑单词或忽略。

#### 3.6.2 数据结构扩展

自建单词 `isCustom=true`，可在前端额外记录 `source: 'photo'` 及图片缩略图路径（作为 UI 展示，不强制存储）。

---

## 4. 数据库设计

| 存储名称         | 主键          | 索引                                          |
| ---------------- | ------------- | --------------------------------------------- |
| `users`          | `userId`      | `username`（唯一）                            |
| `words`          | `wordId`      | `phase`, `isCustom`, `userId`                 |
| `interactions`   | `interactionId` | `userId`, `wordId`, `timestamp`             |
| `study_plans`    | `planId`      | `userId`, `status`                            |
| `daily_tasks`    | `taskId`      | `planId`, `dayIndex`                          |
| `reports`        | `reportId`    | `userId`, `createTime`                        |
| `word_familiarity` | `(wordId, userId)`  | `userId`, `familiarity`, `lastUpdate`  |

> **说明**：
> - `word_familiarity` 为缓存表，每次交互后更新，避免实时扫描所有交互记录。
> - 所有表均通过 `userId` 实现数据隔离。

---

## 5. 关键算法设计

### 5.1 熟悉度增量更新算法（简化版）

```javascript
function updateFamiliarity(wordId, userId, feedback) {
  // 获取当前缓存的熟悉度，若无则计算初始值（基于交互历史）
  let oldF = getCachedFamiliarity(wordId, userId);
  // 增量更新：已知+10，未知-15，模糊+0（但记录次数影响正确率）
  let delta = (feedback === 'known') ? 10 : (feedback === 'unknown') ? -15 : 0;
  let newF = Math.min(100, Math.max(0, oldF + delta));
  
  // 时间衰减：如果距离上次交互超过 24 小时，额外衰减 2%
  let last = getLastInteractionTime(wordId, userId);
  if (last && (Date.now() - last) > 86400000) {
    newF = Math.max(0, newF - 2);
  }
  storeCachedFamiliarity(wordId, userId, newF, Date.now());
}
```

### 5.2 基于 SM-2 的复习间隔（可选，保留原设计）

若需更精准的复习安排，可为每个单词维护 `repetition`（连续正确次数）和 `easiness`，实现 SuperMemo 2 算法。由于实体简化，这些字段可存储于 `word_familiarity` 表的扩展字段中。

---

## 6. 接口设计（前端内部 API 或后端 API）

### 6.1 数据服务接口（统一封装）

```typescript
interface DataService {
  // 用户
  register(user: Omit<User, 'userId'>): Promise<User>;
  login(username: string): Promise<User>;
  
  // 单词
  getWords(userId: string, filter?: {phase?: string, isCustom?: boolean}): Promise<Word[]>;
  addCustomWord(userId: string, word: Omit<Word, 'wordId' | 'isCustom'>): Promise<Word>;
  
  // 交互
  addInteraction(interaction: Omit<Interaction, 'interactionId'>): Promise<void>;
  getInteractions(userId: string, wordId?: string): Promise<Interaction[]>;
  
  // 熟悉度
  getFamiliarity(userId: string, wordId: string): Promise<number>;
  
  // 测试
  generateTestWords(userId: string, count: number): Promise<Word[]>;
  
  // 计划
  createStudyPlan(plan: Omit<StudyPlan, 'planId'>): Promise<StudyPlan>;
  getTodayTasks(userId: string): Promise<DailyTask | null>;
  
  // 报告
  generateReport(userId: string): Promise<Report>;
  getReports(userId: string, startTime?: number, endTime?: number): Promise<Report[]>;
}
```

### 6.2 大模型调用接口

```typescript
async function generateAdvice(stats: any, mistakes: any[]): Promise<string> {
  const response = await fetch(LLM_API_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      prompt: buildPrompt(stats, mistakes)
    })
  });
  return response.text();
}
```

---

## 7. 非功能需求实现

| 需求         | 实现方案                                                              |
| ------------ | --------------------------------------------------------------------- |
| 数据安全     | 前端 IndexedDB 多用户隔离；支持数据导出备份（JSON）                   |
| 性能         | 单词测试响应 <200ms：使用 `word_familiarity` 缓存 + 索引查询          |
| 可扩展性     | Word 对象保留 `otherAttributes: Record<string, any>` 字段（可选扩展） |
| 兼容性       | 使用 Vue 3 支持 ES6 特性，通过 Babel 降级，兼容 Windows 7+ 的 Chrome/Edge |

---

## 8. 待定事项与后续设计

| 事项                 | 计划解决方案                                 |
| -------------------- | -------------------------------------------- |
| 大模型 Prompt 精细化 | 设计多套模板，根据用户类型选择不同风格       |
| 照片识词准确率阈值   | 设置置信度 > 0.6 才展示，低于此值提示重拍    |
| 激励属性（积分/打卡）| 预留 `user_extras` 表，后续扩展              |

---


## 9. 附录
### 9.1 分工
- 每个人负责部分同需求文档
- 审核与校验：蔡奕麟

### 9.2 参考资料

- 艾宾浩斯遗忘曲线参数
- SM-2 算法说明

### 9.3 版本记录

| 版本  | 日期         | 修改内容         | 作者    |
|-----|------------|--------------| ------- |
| 1.1 | 2026-05-12 | 初版设计文档 | Group5  |
| 1.2 | 2026-05-14 | 简化实体，功能完整保留  | Group5  |
| 1.3 | 2026-05-17 | 修正部分实体冗余和缺陷  | Group5  |
