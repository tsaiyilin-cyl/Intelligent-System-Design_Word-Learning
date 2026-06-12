"""
学习建议生成服务
封装大模型 API 调用和预设兜底逻辑
"""

import os
import json
import random
import logging
from typing import Optional

import requests

logger = logging.getLogger(__name__)

# 预设建议（兜底）
PREDEFINED_TIPS = [
    {"title": "制定合理目标",
     "content": "根据您的学习阶段和类型，建议每天学习10-20个新单词，循序渐进。"},
    {"title": "定期复习",
     "content": "利用艾宾浩斯遗忘曲线，在适当的时间间隔复习已学单词，提高记忆效果。"},
    {"title": "保持连续性",
     "content": "每天坚持学习，即使时间不长，也能形成良好的学习习惯，提升学习效果。"},
    {"title": "多样化学习",
     "content": "结合单词卡片、测试练习等多种方式，全方位巩固单词记忆。"},
    {"title": "攻克薄弱词",
     "content": "针对您常错的单词，建议今天先复习巩固，再学习新词，事半功倍。"},
    {"title": "稳扎稳打",
     "content": "学习贵在坚持，您已连续打卡多天，保持节奏，逐步扩大词汇量。"},
]

# 阶段映射
PHASE_MAP = {
    "primary": "小学",
    "junior": "初中",
    "senior": "高中",
    "non-student": "非学生（社会学习者）",
}

# 类型映射
TYPE_MAP = {
    "memory": "记忆型（偏好深度记忆）",
    "quiz": "刷题型（偏好快速刷词）",
}


def generate_study_tip(study_data: dict) -> dict:
    """
    根据学习数据生成个性化建议

    Args:
        study_data: 包含学习统计数据的字典
            - phase: 学习阶段
            - userType: 学习类型
            - dailyGoal: 每日目标
            - totalWords: 已学单词总数
            - masteredWords: 已掌握单词数
            - studyStreak: 连续打卡天数
            - todayLearned: 今日已学单词数

    Returns:
        {"title": str, "content": str, "source": "ai"|"predefined"}
    """
    phase = PHASE_MAP.get(study_data.get("phase"), study_data.get("phase", "未知"))
    user_type = TYPE_MAP.get(study_data.get("userType"), study_data.get("userType", "未知"))

    # 尝试调用大模型 API
    tip = _call_llm_api(phase, user_type, study_data)

    if tip is not None:
        return {"title": tip["title"], "content": tip["content"], "source": "ai"}

    # 失败时走预设兜底
    fallback = random.choice(PREDEFINED_TIPS)
    logger.info("LLM API 不可用，使用预设建议")
    return {"title": fallback["title"], "content": fallback["content"], "source": "predefined"}


def _call_llm_api(phase: str, user_type: str, study_data: dict) -> Optional[dict]:
    """调用大模型 API 生成建议，失败返回 None"""
    api_key = os.getenv("LLM_API_KEY", "")
    api_base = os.getenv("LLM_BASE_URL", "https://api.deepseek.com")

    if not api_key:
        logger.warning("LLM_API_KEY 未配置，跳过 API 调用")
        return None

    prompt = _build_prompt(phase, user_type, study_data)

    try:
        resp = requests.post(
            f"{api_base.rstrip('/')}/chat/completions",
            headers={
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
            },
            json={
                "model": os.getenv("LLM_MODEL", "deepseek-chat"),
                "messages": [
                    {"role": "system",
                     "content": "你是一位英语学习规划助手。输出严格 JSON 格式，不包含 markdown 代码块。"},
                    {"role": "user", "content": prompt},
                ],
                "temperature": float(os.getenv("LLM_TEMPERATURE", "0.8")),
                "max_tokens": int(os.getenv("LLM_MAX_TOKENS", "120")),
            },
            timeout=10,
        )
        resp.raise_for_status()
        content = resp.json()["choices"][0]["message"]["content"]

        # 清理可能的 markdown 代码块
        content = content.strip()
        content = content.removeprefix("```json").removeprefix("```").removesuffix("```").strip()

        result = json.loads(content)
        if result.get("title") and result.get("content"):
            logger.info("LLM API 成功返回建议: %s", result["title"])
            return result
        else:
            logger.warning("LLM 返回格式不完整: %s", content)

    except requests.exceptions.Timeout:
        logger.error("LLM API 请求超时")
    except requests.exceptions.RequestException as e:
        logger.error("LLM API 请求失败: %s", e)
    except (json.JSONDecodeError, KeyError) as e:
        logger.error("LLM 响应解析失败: %s", e)

    return None


def _build_prompt(phase: str, user_type: str, study_data: dict) -> str:
    """构建大模型 prompt"""
    return f"""你是一位英语学习规划师。请根据以下用户信息，给出一条简短的个性化学习建议。

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
