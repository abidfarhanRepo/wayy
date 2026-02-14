#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

DATA_YAML="${PROJECT_ROOT}/exports/lane/data.yaml"
BASE_MODEL="yolov8n-seg.pt"
EPOCHS=50
IMGSZ=640
BATCH=4
PATIENCE=10
PROJECT="${PROJECT_ROOT}/runs/lane_segmentation"
NAME="lane_v1"
OUTPUT="${PROJECT_ROOT}/models/lane"

check_cuda() {
    if python3 -c "import torch; assert torch.cuda.is_available()" 2>/dev/null; then
        echo "cuda"
    else
        echo "cpu"
    fi
}

DEVICE=$(check_cuda)
echo "=============================================="
echo "Lane Segmentation Training"
echo "=============================================="
echo "Dataset: $DATA_YAML"
echo "Base Model: $BASE_MODEL"
echo "Epochs: $EPOCHS"
echo "Image Size: $IMGSZ"
echo "Batch: $BATCH"
echo "Patience: $PATIENCE"
echo "Device: $DEVICE"
echo "Output: $OUTPUT"
echo "=============================================="

if [ ! -f "$DATA_YAML" ]; then
    echo "ERROR: Dataset config not found at $DATA_YAML"
    exit 1
fi

mkdir -p "$OUTPUT"

TRAIN_CMD="python3 ${SCRIPT_DIR}/ml_pipeline/train_yolo.py \
    --data \"$DATA_YAML\" \
    --base-model \"$BASE_MODEL\" \
    --epochs $EPOCHS \
    --imgsz $IMGSZ \
    --device $DEVICE \
    --patience $PATIENCE \
    --project \"$PROJECT\" \
    --name \"$NAME\" \
    --output \"$OUTPUT\" \
    --export-tflite"

if [ "$BATCH" != "auto" ]; then
    TRAIN_CMD="$TRAIN_CMD --batch $BATCH"
fi

echo "Running training command..."
eval $TRAIN_CMD

echo "=============================================="
echo "Training Complete!"
echo "=============================================="
echo "Model outputs:"
ls -la "$OUTPUT/" 2>/dev/null || echo "Output directory: $OUTPUT"
echo "Full training results: $PROJECT/$NAME/"
echo "=============================================="
