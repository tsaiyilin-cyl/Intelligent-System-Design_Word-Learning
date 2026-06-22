"""
Flask AI 服务入口
"""

import os
import logging
import threading
from flask import Flask, request, jsonify
from dotenv import load_dotenv

from services.study_tip import generate_study_tip, warmup_llm
from services import ocr as ocr_service

load_dotenv()

app = Flask(__name__)

# 日志配置
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


@app.route("/api/study-tip", methods=["POST"])
def study_tip():
    """根据用户学习数据生成个性化学习建议

    请求体:
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

    响应:
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
    """
    data = request.get_json(silent=True)
    if not data:
        return jsonify({"code": 400, "message": "缺少请求参数"}), 400

    study_data = data.get("studyData")
    if not study_data:
        return jsonify({"code": 400, "message": "缺少 studyData 字段"}), 400

    logger.info("收到学习建议请求: userId=%s", data.get("userId"))

    tip = generate_study_tip(study_data)

    return jsonify({
        "code": 200,
        "data": tip,
    })


@app.route("/api/health", methods=["GET"])
def health():
    """健康检查（含 OCR 模型状态）"""
    ocr_info = ocr_service.get_model_info()
    return jsonify({
        "status": "ok",
        "service": "flask-ai",
        "ocr": ocr_info,
    })


# ==================== OCR 照片识词 ====================


@app.route("/api/ocr/status", methods=["GET"])
def ocr_status():
    """OCR 模型状态查询"""
    return jsonify({
        "code": 200,
        "message": "查询成功",
        "data": ocr_service.get_model_info(),
    })


@app.route("/api/ocr/recognize", methods=["POST"])
def ocr_recognize():
    """上传图片并识别图中物体

    接收 Spring Boot 转发的图片字节，返回分类候选列表。
    Spring Boot 端负责数据库单词匹配。

    请求: (raw bytes, Content-Type: image/jpeg 等)
    响应:
    ```json
    {
        "code": 200,
        "data": [
            {"label": "tree frog", "confidence": 0.8532, "classIndex": 31},
            {"label": "frog", "confidence": 0.0421, "classIndex": 30}
        ]
    }
    ```
    """
    # 支持两种请求方式：
    # 1. multipart/form-data（前端直接调用）
    # 2. raw bytes（Spring Boot 转发）

    image_bytes = None

    if request.content_type and request.content_type.startswith("multipart/"):
        # 方式 1: 文件上传
        if "file" not in request.files:
            return jsonify({"code": 400, "message": "缺少 file 字段"}), 400
        file = request.files["file"]
        if file.filename == "" or file.filename is None:
            return jsonify({"code": 400, "message": "请选择图片文件"}), 400
        image_bytes = file.read()
    else:
        # 方式 2: raw bytes
        image_bytes = request.get_data()

    if not image_bytes or len(image_bytes) == 0:
        return jsonify({"code": 400, "message": "图片数据为空"}), 400

    logger.info("收到图片识别请求: %d bytes", len(image_bytes))

    try:
        results = ocr_service.recognize(image_bytes)
        return jsonify({
            "code": 200,
            "data": results,
        })
    except RuntimeError as e:
        error_msg = str(e)
        logger.error("OCR 识别失败: %s", error_msg)
        if "未就绪" in error_msg or "加载" in error_msg:
            return jsonify({"code": 503, "message": error_msg}), 503
        return jsonify({"code": 500, "message": f"识别出错: {error_msg}"}), 500
    except Exception as e:
        logger.error("OCR 识别异常: %s", e)
        return jsonify({"code": 500, "message": f"识别出错: {e}"}), 500


if __name__ == "__main__":
    host = os.getenv("FLASK_HOST", "0.0.0.0")
    port = int(os.getenv("FLASK_PORT", "5000"))
    debug = os.getenv("FLASK_DEBUG", "false").lower() == "true"

    logger.info("启动 Flask AI 服务: %s:%d (debug=%s)", host, port, debug)

    # 后台预热 LLM API（解决首次调用冷启动慢的问题）
    # 延迟 3 秒启动，等模型加载完成后再预热
    threading.Timer(3.0, warmup_llm).start()

    # 注意: use_reloader=False 防止 PyTorch 写 module.py 时触发 watchdog 重启
    app.run(host=host, port=port, debug=debug, use_reloader=False)
