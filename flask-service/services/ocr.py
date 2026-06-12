"""
OCR 照片识词服务
---
使用 PyTorch + TorchVision 加载 TorchScript 模型进行图片分类。
启动时自动检查依赖、自动下载模型，支持跨机器部署。
"""

import os
import sys
import subprocess
import io
import logging
from pathlib import Path
from typing import Optional

logger = logging.getLogger(__name__)

# ======================== 配置（从环境变量读取） ========================

# 模型目录（默认指向项目根目录下的 ocr-models/）
_DEFAULT_MODEL_DIR = str(
    Path(__file__).resolve().parent.parent.parent / "ocr-models"
)
MODEL_DIR = os.getenv("OCR_MODEL_DIR", _DEFAULT_MODEL_DIR)
MODEL_NAME = os.getenv("OCR_MODEL_NAME", "vit_base_in21k")
MODEL_URL = os.getenv("OCR_MODEL_URL", "")
TOP_K = int(os.getenv("OCR_TOP_K", "5"))
CONFIDENCE_THRESHOLD = float(os.getenv("OCR_CONFIDENCE_THRESHOLD", "0.2"))

# 模型预处理参数（与 export_in21k_model.py 导出的 info 文件一致）
INPUT_SIZE = (224, 224)
MEAN = [0.485, 0.456, 0.406]
STD = [0.229, 0.224, 0.225]

# ======================== 全局状态 ========================

_model = None
_class_labels: list[str] = []
_model_ready = False
_model_error: Optional[str] = None


# ======================== 依赖管理（跨机器自动安装） ========================

REQUIRED_PACKAGES = [
    ("torch", "torch"),
    ("torchvision", "torchvision"),
    ("Pillow", "PIL"),
]


def ensure_dependencies():
    """检查必需依赖，缺失时自动安装。

    这是为了跨机器部署设计的——在新机器上 clone 项目后，
    如果没装 torch/torchvision，会自动 pip install。
    """
    missing_pkg = []
    missing_import = []

    for pkg_name, import_name in REQUIRED_PACKAGES:
        try:
            __import__(import_name)
        except ImportError:
            missing_pkg.append(pkg_name)
            missing_import.append(import_name)

    if not missing_pkg:
        return  # 全部就绪

    logger.warning("检测到缺失依赖: %s，开始自动安装 ...", missing_pkg)
    try:
        result = subprocess.run(
            [sys.executable, "-m", "pip", "install"] + missing_pkg,
            capture_output=True, text=True, timeout=300,
        )
        if result.returncode != 0:
            raise RuntimeError(
                f"pip install 失败 (code={result.returncode}):\n"
                f"{result.stderr}"
            )
        logger.info("依赖安装成功: %s", missing_pkg)

        # 安装后重新尝试导入每个模块
        for pkg_name, import_name in zip(missing_pkg, missing_import):
            try:
                __import__(import_name)
            except ImportError as e:
                raise RuntimeError(
                    f"安装后仍无法导入 {pkg_name}: {e}\n"
                    f"请手动执行: pip install {pkg_name}"
                )

    except subprocess.TimeoutExpired:
        raise RuntimeError(
            f"依赖安装超时（5分钟），请手动执行:\n"
            f"  pip install {' '.join(missing_pkg)}"
        )


# ======================== 模型管理（自动下载） ========================

def _model_path() -> str:
    """获取模型文件的完整路径"""
    return os.path.join(MODEL_DIR, f"{MODEL_NAME}.pt")


def load_labels():
    """加载类别标签文件

    优先加载 <model_name>_classes.txt（export_in21k_model.py 导出的格式），
    找不到时尝试加载 synset_21k.txt（Java 时期遗留格式）。
    """
    global _class_labels

    candidates = [
        os.path.join(MODEL_DIR, f"{MODEL_NAME}_classes.txt"),
        os.path.join(MODEL_DIR, "synset_21k.txt"),
    ]

    labels_path = None
    for p in candidates:
        if os.path.exists(p):
            labels_path = p
            logger.info("找到类别标签文件: %s", p)
            break

    if labels_path is None:
        raise FileNotFoundError(
            f"类别标签文件未找到（尝试路径: {candidates}）。\n"
            f"请将 {MODEL_NAME}_classes.txt 或 synset_21k.txt 放入 {MODEL_DIR}"
        )

    with open(labels_path, "r", encoding="utf-8") as f:
        _class_labels = [line.strip() for line in f if line.strip()]

    logger.info("已加载 %d 个类别标签", len(_class_labels))


def download_model():
    """从 MODEL_URL 下载 TorchScript 模型文件"""
    model_path = _model_path()
    os.makedirs(MODEL_DIR, exist_ok=True)

    if not MODEL_URL:
        raise RuntimeError(
            f"模型文件不存在: {model_path}\n"
            f"且未配置 OCR_MODEL_URL 环境变量。\n\n"
            f"有两种方式解决:\n"
            f"  1. 将 {MODEL_NAME}.pt 文件复制到 {MODEL_DIR}/\n"
            f"  2. 在 flask-service/.env 中设置 OCR_MODEL_URL\n"
        )

    import requests
    logger.info("正在下载模型: %s", MODEL_URL)
    logger.info("目标路径: %s", model_path)

    try:
        resp = requests.get(MODEL_URL, stream=True, timeout=30)
        resp.raise_for_status()

        total = int(resp.headers.get("content-length", 0))
        downloaded = 0
        last_log_pct = 0

        with open(model_path, "wb") as f:
            for chunk in resp.iter_content(chunk_size=8192):
                if chunk:
                    f.write(chunk)
                    downloaded += len(chunk)
                    if total > 0:
                        pct = downloaded * 100 // total
                        if pct >= last_log_pct + 10:
                            logger.info("  下载进度: %d%% (%d MB / %d MB)",
                                        pct, downloaded // 1048576, total // 1048576)
                            last_log_pct = pct

        size_mb = os.path.getsize(model_path) // (1024 * 1024)
        logger.info("模型下载完成: %s (%d MB)", model_path, size_mb)

    except requests.exceptions.RequestException as e:
        # 清理不完整的下载文件
        if os.path.exists(model_path):
            os.remove(model_path)
        raise RuntimeError(f"模型下载失败: {e}")


# ======================== 模型加载 ========================

def load_model():
    """加载 TorchScript 模型（启动时调用）"""
    global _model, _model_ready, _model_error

    try:
        # Step 1: 确保 Python 依赖就绪
        ensure_dependencies()

        # 现在 torch/torchvision 已就绪，执行导入
        # pylint: disable=import-outside-toplevel
        import torch
        import torchvision.transforms as transforms  # noqa: F401
        from PIL import Image  # noqa: F401

        # Step 2: 加载类别标签
        load_labels()

        # Step 3: 检查模型文件，不存在则自动下载
        if not os.path.exists(_model_path()):
            download_model()

        # Step 4: 加载 TorchScript 模型
        device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        logger.info("加载模型: %s (device=%s)", _model_path(), device)
        _model = torch.jit.load(_model_path(), map_location=device)
        _model.eval()

        _model_ready = True
        _model_error = None
        logger.info("OCR 模型加载成功 (classes=%d, device=%s)",
                    len(_class_labels), device)

    except Exception as e:
        _model_ready = False
        _model_error = str(e)
        logger.error("OCR 模型加载失败: %s", e)


# ======================== 核心推理 ========================

def recognize(image_bytes: bytes) -> list[dict]:
    """识别图片内容，返回分类结果列表

    Args:
        image_bytes: 图片文件的原始字节（JPEG/PNG/WebP 等）

    Returns:
        按置信度降序排列的识别结果:
        [
            {"label": "tree frog", "confidence": 0.8532, "classIndex": 31},
            {"label": "frog", "confidence": 0.0421, "classIndex": 30},
            ...
        ]

    Raises:
        RuntimeError: 模型未就绪或推理失败
    """
    global _model

    if not _model_ready:
        raise RuntimeError(_model_error or "模型正在加载中，请稍后再试")

    import torch
    import torchvision.transforms as transforms
    from PIL import Image

    try:
        # 1. 读取图片
        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")

        # 2. 预处理（与 export_in21k_model.py 的参数一致）
        transform = transforms.Compose([
            transforms.Resize(INPUT_SIZE,
                              interpolation=transforms.InterpolationMode.BICUBIC),
            transforms.ToTensor(),
            transforms.Normalize(mean=MEAN, std=STD),
        ])
        input_tensor = transform(img).unsqueeze(0)  # [1, 3, 224, 224]

        # 3. 推理
        device = next(_model.parameters()).device
        input_tensor = input_tensor.to(device)

        with torch.no_grad():
            output = _model(input_tensor)

        # 4. Softmax + Top-K
        probs = torch.nn.functional.softmax(output[0], dim=0)
        k = min(TOP_K, len(probs))
        topk_values, topk_indices = torch.topk(probs, k)

        # 5. 构建结果
        results = []
        for i in range(k):
            idx = topk_indices[i].item()
            confidence = topk_values[i].item()

            if confidence < CONFIDENCE_THRESHOLD:
                continue

            label = _class_labels[idx] if idx < len(_class_labels) else f"class_{idx}"

            results.append({
                "label": label,
                "confidence": round(confidence, 4),
                "classIndex": idx,
            })

        results.sort(key=lambda x: x["confidence"], reverse=True)
        return results

    except Exception as e:
        logger.error("图片识别失败: %s", e)
        raise RuntimeError(f"图片识别失败: {e}") from e


# ======================== 状态查询 ========================

def is_ready() -> bool:
    return _model_ready


def get_error() -> Optional[str]:
    return _model_error


def get_label_count() -> int:
    return len(_class_labels)


def get_model_info() -> dict:
    """获取模型状态信息"""
    return {
        "modelReady": _model_ready,
        "modelError": _model_error or "",
        "labelCount": len(_class_labels),
        "modelPath": _model_path(),
        "modelName": MODEL_NAME,
    }


# ======================== 启动加载 ========================

# 模块导入时自动加载模型
load_model()
