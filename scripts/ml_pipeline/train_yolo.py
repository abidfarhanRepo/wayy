#!/usr/bin/env python3
import argparse
import sys


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train/fine-tune YOLO on Wayy datasets.")
    parser.add_argument("--data", required=True, help="Path to dataset YAML.")
    parser.add_argument("--base-model", default="yolov8n.pt", help="Base model checkpoint.")
    parser.add_argument("--epochs", type=int, default=50)
    parser.add_argument("--imgsz", type=int, default=640)
    parser.add_argument("--project", default="runs/wayy")
    parser.add_argument("--name", default="wayy_finetune")
    parser.add_argument("--export-tflite", action="store_true")
    parser.add_argument("--int8", action="store_true", help="Export INT8 quantized model (requires calibration data).")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    try:
        from ultralytics import YOLO
    except ImportError:
        print("Ultralytics not installed. Run: pip install ultralytics")
        sys.exit(1)

    model = YOLO(args.base_model)
    model.train(
        data=args.data,
        epochs=args.epochs,
        imgsz=args.imgsz,
        project=args.project,
        name=args.name
    )
    if args.export_tflite:
        export_args = {"format": "tflite"}
        if args.int8:
            export_args["int8"] = True
        model.export(**export_args)


if __name__ == "__main__":
    main()
