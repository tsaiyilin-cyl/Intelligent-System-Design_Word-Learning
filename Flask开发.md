# Flask 开发指南

## 一、为什么需要 Flask？

### 1. 满足课程技术要求

老师明确要求项目需要同时使用 **Vue3+ElementPlus**、**Spring Boot**、**Flask** 三种技术。目前项目中只有 Vue3 前端和 Spring Boot 后端，缺少 Flask。

### 2. 各司其职——合理的架构分层

```

┌─────────────────────────────────────────────────┐
│                  用户浏览器                         │
│         Vue3 + ElementPlus （前端界面）              │
└────────────────────┬────────────────────────────┘
                     │ HTTP (axios)
                     ▼
┌─────────────────────────────────────────────────┐
│              Spring Boot （核心业务层）              │
│                                                   │
│  ● 用户认证    ● 单词库管理    ● 学习计划           │
│  ● 测验系统    ● 错题记录     ● 数据持久化          │
│  ● 学习卡片图片爬取（Unsplash/Bing）               │
└─────────┬───────────────────────────┬─────────────┘
          │ HTTP (RestTemplate)       │ HTTP (RestTemplate)
          ▼                           ▼
┌─────────────────────┐   ┌─────────────────────────────┐
│  Flask（AI 能力层）   │   │   外部服务                   │
│                     │   │                             │
│  ● 大模型学习建议生成│   │  ● Unsplash / Bing API      │
│  ● OCR 照片识词     │   │  （由 Spring Boot 直调）    │
│  ● 未来其他 AI 功能  │   │                             │
└─────────────────────┘   └─────────────────────────────┘
```

### 3. 为什么 AI 层用 Flask 而非 Spring Boot？

| 维度 | Spring Boot | Flask |
|------|------------|-------|
| **AI/LLM 生态** | Java 的 AI 库较少，调用大模型需手动拼 HTTP 请求 | Python 有 `openai`、`requests` 等成熟的 SDK，生态完善 |
| **Prompt 工程** | String 拼接模板，难维护 | Python 的 f-string / Jinja2 模板更方便 |
| **未来扩展** | OCR 模型导出已用 Python，后续 NLP 任务也是 Python 为主 | 与现有 `export_in21k_model.py` 共用一套 Python 环境 |
| **课程评分** | ❌ 不满足"三种技术"要求 | ✅ 凑齐三个技术栈 |

### 4. 总结：Flask 在这个项目中的定位

> **Flask 是项目的"AI 能力层"，负责所有与大模型/机器学习相关的任务。Spring Boot 专注业务逻辑和数据，Flask 专注 AI 推理。**

---

## 二、哪些功能要迁移到 Flask？哪些不要？

### 逐项分析

| 功能 | 当前实现 | 是否 AI/ML | 推荐 | 理由 |
|------|---------|:----------:|:----:|------|
| 大模型生成学习建议 | `ReportService.java` 直接调 DeepSeek API | ✅ 是 | **→ 迁移到 Flask** | LLM 调用，Python 的 `openai` SDK 更方便 |
| OCR 照片识词 | `OcrService.java` 用 DJL 加载 PyTorch 模型推理 | ✅ 是 | **→ 迁移到 Flask** | 模型已用 Python 导出 (`export_in21k_model.py`)，Python 的 `torchvision` 做推理更直接 |
| 学习卡片图片爬取 | `WordImageService.java` 调 Unsplash/Bing API 下载缓存 | ❌ 否 | **← 留在 Spring Boot** | 纯外部 API 调用 + 文件缓存，不是 AI 任务 |
| 其他业务（单词库、测验、用户等） | Controller/Service/Repository | ❌ 否 | **← 留在 Spring Boot** | 标准 CRUD + 持久化，Spring Boot 的强项 |

### 1. OCR 照片识词 → 迁移理由

- **已用 Python 导出模型**：`export_in21k_model.py` 用 PyTorch 导出 TorchScript 模型
- **Java 加载模型太复杂**：`OcrService.java` 有 700 多行，包含模型下载、多策略加载、软最大化等，在 Python 中用 `torch.jit.load()` 三行搞定
- **推理更自然**：Python `torchvision` 的预处理/后处理比 Java DJL 直观得多
- **后续可替换模型**：可以方便地换成 `transformers` 中的最新模型

**迁移后架构：**

```
前端上传图片 → Spring Boot 接收 → 转发图片到 Flask
                                   Flask 推理识别
                                   Flask 回调 Spring Boot 查单词库
← Flask 返回结果 → Spring Boot 返回给前端
```

OCR 中「将单词加入词库」「查数据库匹配单词」等操作仍需调用 Spring Boot 的接口——Flask 不做数据库操作。

### 2. 图片爬取 → 不迁移的理由

- 调用 Unsplash/Bing API 获取图片 URL 并下载缓存，**不含任何 AI/ML 成分**
- Spring Boot 已有的多 Key 轮换、本地缓存机制已经成熟稳定
- 图片文件缓存属于基础设施，放在 Spring Boot 的静态资源路径下更方便
- 迁移到 Flask 反而增加一次网络跳转和文件传输开销

### 迁移优先级

```
第一优先 ▶  大模型学习建议（已在计划中，改动最小）
第二优先 ▶  OCR 照片识词（加分项，体现 Flask 的 ML 能力）
暂不迁移   图片爬取（保留在 Spring Boot）
```

在项目根目录下新建 `flask-service/` 文件夹，与 Spring Boot 的 `src/` 平级：

```
class10/
├── src/                          # Spring Boot 主项目
│   └── main/
│       ├── java/...
│       └── resources/...
│
├── flask-service/                # ← Flask 服务（新建）
│   ├── app.py                    # Flask 应用入口
│   ├── requirements.txt          # Python 依赖
│   ├── .env                      # 环境变量（API Key 等）
│   └── services/
│       └── study_tip.py          # 学习建议生成逻辑
│
├── ocr-models/                   # 已有：OCR 模型文件
├── pom.xml                       # Spring Boot 配置
├── export_in21k_model.py         # 已有：Python 模型导出脚本
└── ...
```

---

## 三、Flask API 设计

### 当前迁移目标：学习建议生成

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/study-tip` | 根据用户数据生成个性化学习建议 |

**请求体：**

```json
{
    "userId": "xxx",
    "studyData": {
        "phase": "senior",
        "userType": "quiz",
        "dailyGoal": 20,
        "totalWords": 350,
        "masteredWords": 180,
        "studyStreak": 15,
        "todayLearned": 8
    }
}
```

**响应：**

```json
{
    "code": 200,
    "data": {
        "title": "巩固薄弱词",
        "content": "您今天还有12个单词未完成，建议优先复习昨日错误较多的词汇。",
        "source": "ai"
    }
}
```

### Flask 兜底策略

和 Spring Boot 一样——大模型调用失败时，Flask 内部随机返回预设建议：

```python
PREDEFINED_TIPS = [
    {"title": "制定合理目标", "content": "根据您的学习阶段和类型，建议每天学习10-20个新单词，循序渐进。"},
    {"title": "定期复习", "content": "利用艾宾浩斯遗忘曲线，在适当的时间间隔复习已学单词，提高记忆效果。"},
    {"title": "保持连续性", "content": "每天坚持学习，即使时间不长，也能形成良好的学习习惯，提升学习效果。"},
    {"title": "多样化学习", "content": "结合单词卡片、测试练习等多种方式，全方位巩固单词记忆。"},
]
```

---

## 四、Spring Boot 调用 Flask 的方式

Spring Boot 的 `ReportService` 不再直接调用外部大模型 API，改为调用本地的 Flask 服务：

```java
// ReportService.java 中原本的 callLlmApi() 改为：
private Map<String, String> callFlaskForTip(User user, Map<String, Object> planData) {
    String flaskUrl = "http://localhost:5000/api/study-tip";

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("userId", user.getUserId());
    requestBody.put("studyData", planData);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

    try {
        ResponseEntity<Map> response = restTemplate.exchange(
            flaskUrl, HttpMethod.POST, entity, Map.class);
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        return Map.of("title", (String) data.get("title"), "content", (String) data.get("content"));
    } catch (Exception e) {
        log.warn("Flask 服务不可用，走本地兜底: {}", e.getMessage());
        return null;  // 外层自动走预设兜底
    }
}
```

Flask 服务地址配置在 `application.yaml` 中：

```yaml
flask:
  base-url: "http://localhost:5000"
```

---

## 五、最小可用 Flask 代码示例

### `flask-service/app.py`

```python
import os
import random
import json
from flask import Flask, request, jsonify
from dotenv import load_dotenv

load_dotenv()

app = Flask(__name__)

# 预设建议（兜底）
PREDEFINED_TIPS = [
    {"title": "制定合理目标", "content": "根据您的学习阶段和类型，建议每天学习10-20个新单词，循序渐进。"},
    {"title": "定期复习", "content": "利用艾宾浩斯遗忘曲线，在适当的时间间隔复习已学单词，提高记忆效果。"},
    {"title": "保持连续性", "content": "每天坚持学习，即使时间不长，也能形成良好的学习习惯，提升学习效果。"},
    {"title": "多样化学习", "content": "结合单词卡片、测试练习等多种方式，全方位巩固单词记忆。"},
]

# 阶段映射
PHASE_MAP = {
    "primary": "小学", "junior": "初中",
    "senior": "高中", "non-student": "非学生（社会学习者）",
}

# 类型映射
TYPE_MAP = {
    "memory": "记忆型（偏好深度记忆）",
    "quiz": "刷题型（偏好快速刷词）",
}


@app.route("/api/study-tip", methods=["POST"])
def study_tip():
    """根据用户学习数据生成个性化建议"""
    data = request.get_json()
    if not data:
        return jsonify({"code": 400, "message": "缺少请求参数"}), 400

    study_data = data.get("studyData", {})
    phase = PHASE_MAP.get(study_data.get("phase"), study_data.get("phase", "未知"))
    user_type = TYPE_MAP.get(study_data.get("userType"), study_data.get("userType", "未知"))

    # 尝试调用大模型 API
    tip = call_llm_api(phase, user_type, study_data)

    # 失败时走预设兜底
    if tip is None:
        tip = random.choice(PREDEFINED_TIPS)
        source = "predefined"
    else:
        source = "ai"

    return jsonify({
        "code": 200,
        "data": {
            "title": tip["title"],
            "content": tip["content"],
            "source": source,
        }
    })


def call_llm_api(phase, user_type, study_data):
    """调用大模型 API 生成建议（当前用模拟数据演示）"""
    api_key = os.getenv("LLM_API_KEY", "")
    api_base = os.getenv("LLM_BASE_URL", "https://api.deepseek.com")

    if not api_key:
        return None  # 无 Key 时走兜底

    # Python 中拼 prompt 比 Java 方便得多
    prompt = f"""你是一位英语学习规划师。请根据以下用户信息，给出一条简短的个性化学习建议。

用户信息：
- 学习阶段：{phase}
- 学习类型：{user_type}
- 每日目标：{study_data.get('dailyGoal')} 词/天
- 已学单词：{study_data.get('totalWords')} 词
- 已掌握：{study_data.get('masteredWords')} 词
- 连续打卡：{study_data.get('studyStreak')} 天
- 今日已学：{study_data.get('todayLearned')} 词

参考格式（标题4字左右，内容一句话）：
{{"title": "制定合理目标", "content": "根据您的学习阶段和类型，建议每天学习10-20个新单词，循序渐进。"}}

要求：
1. 输出严格 JSON，包含 title 和 content 两个字段，不要 markdown 代码块
2. title 限制在 4~6 个字
3. content 一句话，控制在 30~50 字
4. 结合用户情况，有针对性的建议
5. 语气积极鼓励"""

    try:
        import requests
        resp = requests.post(
            f"{api_base.rstrip('/')}/chat/completions",
            headers={
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
            },
            json={
                "model": os.getenv("LLM_MODEL", "deepseek-chat"),
                "messages": [
                    {"role": "system", "content": "你是一位英语学习规划助手。输出严格 JSON 格式。"},
                    {"role": "user", "content": prompt},
                ],
                "temperature": 0.8,
                "max_tokens": 120,
            },
            timeout=10,
        )
        resp.raise_for_status()
        content = resp.json()["choices"][0]["message"]["content"]
        content = content.strip().removeprefix("```json").removeprefix("```").removesuffix("```").strip()
        result = json.loads(content)
        if result.get("title") and result.get("content"):
            return result
    except Exception as e:
        print(f"LLM API 调用失败: {e}")

    return None


@app.route("/api/health", methods=["GET"])
def health():
    """健康检查"""
    return jsonify({"status": "ok", "service": "flask-ai"})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
```

### `flask-service/requirements.txt`

```
flask==3.1.1
requests==2.32.3
python-dotenv==1.1.0
```

### `flask-service/.env`

```
LLM_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxx
LLM_BASE_URL=https://api.deepseek.com
LLM_MODEL=deepseek-chat
```

---

## 六、启动与运行

### 开发时同时启动两个服务

```bash
# 终端1：启动 Spring Boot
cd class10/
./mvnw spring-boot:run

# 终端2：启动 Flask
cd class10/flask-service/
pip install -r requirements.txt
python app.py
```

### 启动顺序

1. Flask 先启动（或后启动均可，Spring Boot 有重试机制）
2. Spring Boot 启动
3. 前端访问 Spring Boot，Spring Boot 按需调用 Flask

### 验证

```bash
curl -X POST http://localhost:5000/api/study-tip \
  -H "Content-Type: application/json" \
  -d '{"userId":"test","studyData":{"phase":"senior","userType":"quiz","dailyGoal":20,"totalWords":350,"masteredWords":180,"studyStreak":15,"todayLearned":8}}'
```

---

## 七、注意事项

1. **不要循环依赖**：Flask 不能反向调用 Spring Boot，Flask 是无状态的 AI 服务层
2. **Flask 挂了不影响核心功能**：Spring Boot 调用 Flask 超时或失败时，自动走预设建议兜底
3. **跨域问题**：前端只请求 Spring Boot（同一端口），Flask 只在服务端被调用，无需配置 CORS
4. **日志**：Flask 的日志应输出到独立文件，方便排查 AI 相关的问题
5. **生产部署**：Flask 应用推荐使用 `gunicorn` 部署，而不是内置的开发服务器
