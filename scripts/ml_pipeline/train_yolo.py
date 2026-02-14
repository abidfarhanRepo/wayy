#!/usr/bin/env python3
import argparse
import logging
import sys
from pathlib import Path

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S"
)
logger = logging.getLogger(__name__)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train/fine-tune YOLO on Wayy datasets.")
    parser.add_argument("--data", required=True, help="Path to dataset YAML.")
    parser.add_argument("--base-model", default="yolov8n-seg.pt", help="Base model checkpoint (default: yolov8n-seg.pt for segmentation).")
    parser.add_argument("--epochs", type=int, default=50, help="Number of training epochs.")
    parser.add_argument("--imgsz", type=int, default=640, help="Image size for training.")
    parser.add_argument("--batch", type=int, default=None, help="Batch size (default: auto).")
    parser.add_argument("--device", default=None, help="Device to use (cuda/cpu, default: auto-detect).")
    parser.add_argument("--patience", type=int, default=10, help="Early stopping patience.")
    parser.add_argument("--project", default="runs/wayy", help="Project directory for outputs.")
    parser.add_argument("--name", default="wayy_finetune", help="Experiment name.")
    parser.add_argument("--output", default=None, help="Output directory for final models (default: project/name/weights).")
    parser.add_argument("--export-tflite", action="store_true", help="Export model to TFLite format after training.")
    parser.add_argument("--int8", action="store_true", help="Export INT8 quantized model (requires calibration data).")
    return parser.parse_args()


def detect_device() -> str:
    try:
        import torch
        if torch.cuda.is_available():
            device = "cuda"
            logger.info(f"CUDA detected: {torch.cuda.get_device_name(0)}")
        else:
            device = "cpu"
            logger.info("CUDA not available, using CPU")
        return device
    except ImportError:
        logger.warning("PyTorch not installed, defaulting to auto")
        return "auto"


def main() -> None:
    args = parse_args()
    
    logger.info("=" * 60)
    logger.info("YOLO Training Configuration")
    logger.info("=" * 60)
    logger.info(f"Dataset: {args.data}")
    logger.info(f"Base model: {args.base_model}")
    logger.info(f"Epochs: {args.epochs}")
    logger.info(f"Image size: {args.imgsz}")
    logger.info(f"Batch size: {args.batch or 'auto'}")
    logger.info(f"Patience: {args.patience}")
    logger.info(f"Project: {args.project}")
    logger.info(f"Name: {args.name}")
    logger.info(f"Export TFLite: {args.export_tflite}")
    logger.info("=" * 60)
    
    data_path = Path(args.data)
    if not data_path.exists():
        logger.error(f"Dataset config not found: {data_path}")
        sys.exit(1)
    
    try:
        from ultralytics import YOLO
    except ImportError:
        logger.error("Ultralytics not installed. Run: pip install ultralytics")
        sys.exit(1)
    
    device = args.device or detect_device()
    logger.info(f"Using device: {device}")
    
    logger.info(f"Loading base model: {args.base_model}")
    model = YOLO(args.base_model)
    
    train_kwargs = {
        "data": args.data,
        "epochs": args.epochs,
        "imgsz": args.imgsz,
        "project": args.project,
        "name": args.name,
        "device": device,
        "patience": args.patience,
        "verbose": True,
    }
    
    if args.batch:
        train_kwargs["batch"] = args.batch
    
    logger.info("Starting training...")
    results = model.train(**train_kwargs)
    
    weights_dir = Path(args.project) / args.name / "weights"
    best_pt = weights_dir / "best.pt"
    last_pt = weights_dir / "last.pt"
    
    logger.info("Training completed!")
    logger.info(f"Best model: {best_pt}")
    logger.info(f"Last model: {last_pt}")
    
    if hasattr(results, 'results_dict'):
        logger.info("Training metrics:")
        for key, value in results.results_dict.items():
            logger.info(f"  {key}: {value}")
    
    if args.export_tflite:
        logger.info("Exporting to TFLite format...")
        export_args = {"format": "tflite"}
        if args.int8:
            export_args["int8"] = True
            logger.info("Using INT8 quantization")
        
        export_path = model.export(**export_args)
        logger.info(f"TFLite model exported: {export_path}")
    
    if args.output:
        output_dir = Path(args.output)
        output_dir.mkdir(parents=True, exist_ok=True)
        
        import shutil
        if best_pt.exists():
            shutil.copy(best_pt, output_dir / "best.pt")
            logger.info(f"Copied best.pt to {output_dir}")
        if args.export_tflite:
            tflite_src = weights_dir / "best_float16.tflite"
            if tflite_src.exists():
                shutil.copy(tflite_src, output_dir / "best_float16.tflite")
                logger.info(f"Copied best_float16.tflite to {output_dir}")
    
    logger.info("=" * 60)
    logger.info("Training pipeline completed successfully!")
    logger.info("=" * 60)


if __name__ == "__main__":
    main()
