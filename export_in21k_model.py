"""
ImageNet-21k 模型导出脚本
===========================
从 timm 加载预训练模型 -> 导出为 TorchScript (.pt)
供 DJL (Deep Java Library) + PyTorch 引擎加载

用法:
    python export_in21k_model.py                        # 默认导出 ViT-B/16
    python export_in21k_model.py --model swin_base      # 导出 Swin-B
    python export_in21k_model.py --model vit_large      # 导出 ViT-L

输出 (到 ocr-models/):
    vit_base_in21k.pt           (TorchScript 模型文件)
    vit_base_in21k_classes.txt  (类别名称, 按模型输出顺序)
    vit_base_in21k_info.txt     (模型元信息)
"""

import argparse
import os
import re
import sys
import time
from pathlib import Path

import torch
import timm
from timm.data import ImageNetInfo


def parse_args():
    parser = argparse.ArgumentParser(description="导出 ImageNet-21k 模型为 TorchScript")
    parser.add_argument(
        "--model", type=str, default="vit_base_patch16_224_in21k",
        choices=[
            "vit_base_patch16_224_in21k",   # ViT-B/16,  86M, 21843 类
            "vit_large_patch16_224_in21k",  # ViT-L/16, 307M, 21843 类
            "swin_base_patch4_window7_224_in22k",   # Swin-B,  88M, 21841 类
            "swin_large_patch4_window7_224_in22k",  # Swin-L, 197M, 21841 类
        ],
        help="timm 模型名称",
    )
    parser.add_argument(
        "--output-dir", type=str, default="ocr-models",
        help="模型输出目录",
    )
    return parser.parse_args()


def get_clean_class_names(num_classes: int) -> list[str]:
    """从 timm 的 ImageNetInfo 获取 21k 类别名称

    ImageNet-21k 使用 WordNet 同义词集 (synset),
    每个 synset 的描述可能包含多个同义词 (逗号分隔),
    这里取第一个作为类别名称用于图片识别。
    """
    print("  Loading ImageNet-21k class names from timm ...")
    info = ImageNetInfo(subset="imagenet-21k")
    descriptions = info.label_descriptions(detailed=False)

    assert len(descriptions) == num_classes, (
        f"Class count mismatch: ImageNetInfo has {len(descriptions)}, "
        f"model expects {num_classes}"
    )

    names = []
    for desc in descriptions:
        # 取第一个同义词 (逗号前)
        name = desc.split(",")[0].strip()
        # 清理多余空格
        name = re.sub(r"\s+", " ", name).strip()
        names.append(name)

    return names


def export_torchscript(model, dummy_input, output_path: Path) -> bool:
    """导出模型为 TorchScript"""
    print("  Exporting to TorchScript ...")

    # 优先尝试 torch.jit.script (更健壮)
    try:
        scripted = torch.jit.script(model)
        scripted.save(str(output_path))
        print(f"  [OK] Exported via torch.jit.script ({output_path})")
        return True
    except Exception as e:
        print(f"  (script failed: {e}, trying trace ...)")

    # Fallback: torch.jit.trace
    try:
        model.eval()
        traced = torch.jit.trace(model, dummy_input)
        traced.save(str(output_path))
        print(f"  [OK] Exported via torch.jit.trace ({output_path})")
        return True
    except Exception as e:
        print(f"  [FAIL] All export methods failed: {e}")
        return False


def main():
    args = parse_args()
    model_name = args.model
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    # 输出文件名 (去掉 -patch16 等后缀)
    short_name = (
        model_name
        .replace("_patch16", "")
        .replace("_patch4_window7", "")
        .replace("_224", "")
    )
    pt_path = output_dir / f"{short_name}.pt"

    print(f"=== ImageNet-21k Model Export ===")
    print(f"  Model:     {model_name}")
    print(f"  Output:    {pt_path}")
    print()

    # 1. 加载预训练模型
    print("1. Loading pretrained model ...")
    t0 = time.time()
    model = timm.create_model(model_name, pretrained=True)
    model.eval()
    params_m = sum(p.numel() for p in model.parameters()) / 1e6
    print(f"   [OK] {model_name} ({params_m:.0f}M params), "
          f"num_classes={model.num_classes}, "
          f"time={time.time()-t0:.1f}s")
    print()

    # 2. 获取类别名称并保存
    print("2. Getting class names from timm ...")
    t0 = time.time()
    class_names = get_clean_class_names(model.num_classes)
    class_file = output_dir / f"{short_name}_classes.txt"
    with open(class_file, "w", encoding="utf-8") as f:
        for name in class_names:
            f.write(name + "\n")
    print(f"   [OK] {len(class_names)} class names saved to {class_file}")
    print(f"       First 10: {class_names[:10]}")
    print(f"       Last 5:   {class_names[-5:]}")
    print(f"       time={time.time()-t0:.1f}s")
    print()

    # 3. 导出 TorchScript
    print("3. Exporting to TorchScript ...")
    t0 = time.time()
    data_config = timm.data.resolve_model_data_config(model)
    input_size = data_config["input_size"]
    dummy = torch.randn(1, *input_size)

    success = export_torchscript(model, dummy, pt_path)
    if not success:
        sys.exit(1)
    print(f"   time={time.time()-t0:.1f}s")
    print()

    # 4. 保存模型元信息
    info_path = output_dir / f"{short_name}_info.txt"
    with open(info_path, "w") as f:
        f.write(f"model_name: {short_name}\n")
        f.write(f"timm_model: {model_name}\n")
        f.write(f"num_classes: {model.num_classes}\n")
        f.write(f"input_size: {data_config['input_size']}\n")
        f.write(f"mean: {data_config['mean']}\n")
        f.write(f"std: {data_config['std']}\n")
        f.write(f"interpolation: {data_config.get('interpolation', 'bicubic')}\n")
        f.write(f"crop_pct: {data_config.get('crop_pct', 0.875)}\n")
    print(f"   [OK] Model info saved to {info_path}")
    print()

    # 5. 验证 (随机输入)
    print("5. Verification (random input):")
    with torch.no_grad():
        output = model(dummy)
        probs = torch.nn.functional.softmax(output[0], dim=0)
        top5_idx = probs.topk(5).indices.tolist()
        top5_probs = probs.topk(5).values.tolist()
    for i, (idx, prob) in enumerate(zip(top5_idx, top5_probs)):
        name = class_names[idx] if idx < len(class_names) else f"class_{idx}"
        print(f"    {i+1}. [{idx}] {name}: {prob:.4f}")
    print()

    # 文件大小
    pt_mb = os.path.getsize(pt_path) / (1024 * 1024)
    print(f"=== Done! ===")
    print(f"  Model:  {pt_path} ({pt_mb:.0f} MB)")
    print(f"  Labels: {class_file} ({len(class_names)} classes)")
    print()
    print("Next steps:")
    print(f"  1. 确保 src/main/resources/synset_21k.txt 更新为 {len(class_names)} 行")
    print(f"  2. 修改 application.yaml: ocr.model-name = {short_name}")
    print(f"  3. Spring Boot 启动时自动加载 ocr-models/{short_name}.pt")


if __name__ == "__main__":
    main()
