#!/usr/bin/env python3
"""
Lane dataset preparation pipeline for YOLO segmentation training.

This script processes lane detection data from videos and creates a properly
structured dataset with train/val splits, scenario classification, and
curve detection labels.
"""

import argparse
import json
import logging
import random
import shutil
import sys
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Dict, List, Optional, Tuple, Any, cast

import cv2
import numpy as np

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)

DEFAULT_FPS = 2.0
DEFAULT_MAX_FRAMES = 300
TRAIN_RATIO = 0.8
BRIGHTNESS_THRESHOLD = 100
CURVE_ANGLE_THRESHOLD = 0.15


class Scenario(Enum):
    """Lighting scenario classification."""
    DAY = "day"
    NIGHT = "night"


class CurveType(Enum):
    """Road curve type classification."""
    STRAIGHT = "straight"
    CURVED = "curved"


@dataclass
class FrameMetadata:
    """Metadata for a processed frame."""
    frame_id: str
    source_video: str
    scenario: Scenario
    curve_type: CurveType
    brightness: float
    angle_variance: float


@dataclass
class DatasetStats:
    """Statistics for the generated dataset."""
    total_frames: int
    train_frames: int
    val_frames: int
    day_frames: int
    night_frames: int
    straight_frames: int
    curved_frames: int
    videos_processed: int
    skipped_frames: int


def parse_args() -> argparse.Namespace:
    """
    Parse command-line arguments.

    Returns:
        Parsed argument namespace.
    """
    parser = argparse.ArgumentParser(
        description="Prepare lane detection dataset with train/val splits and scenario labels."
    )
    parser.add_argument(
        "--input-dir",
        default="exports",
        help="Folder containing device captures or DVR folders",
    )
    parser.add_argument(
        "--output-dir",
        default="exports/lane",
        help="Output dataset root directory",
    )
    parser.add_argument(
        "--fps",
        type=float,
        default=DEFAULT_FPS,
        help="Frame extraction rate (frames per second)",
    )
    parser.add_argument(
        "--max-frames",
        type=int,
        default=DEFAULT_MAX_FRAMES,
        help="Maximum number of frames to extract",
    )
    parser.add_argument(
        "--overwrite",
        action="store_true",
        help="Overwrite existing output files",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=42,
        help="Random seed for reproducible splits",
    )
    parser.add_argument(
        "--balance-scenarios",
        action="store_true",
        help="Balance train/val split across day/night scenarios",
    )
    return parser.parse_args()


def list_videos(root: Path) -> List[Path]:
    """
    Find all video files in the input directory.

    Args:
        root: Root directory to search for videos.

    Returns:
        Sorted list of video file paths.
    """
    videos = []
    videos.extend(root.glob("**/nav_capture_*.mp4"))
    videos.extend(root.glob("**/M_video/*.MP4"))
    return sorted(set(videos))


def region_of_interest(edges: np.ndarray) -> np.ndarray:
    """
    Apply a trapezoidal mask to focus on the road region.

    Args:
        edges: Edge detection output image.

    Returns:
        Masked edge image focusing on road region.
    """
    height, width = edges.shape
    mask = np.zeros_like(edges)
    polygon = np.array(
        [
            [
                (int(width * 0.08), height),
                (int(width * 0.46), int(height * 0.58)),
                (int(width * 0.54), int(height * 0.58)),
                (int(width * 0.92), height),
            ]
        ],
        dtype=np.int32,
    )
    cv2.fillPoly(mask, [polygon], (255,))
    return cv2.bitwise_and(edges, mask)


def fit_lane_line(
    lines: List[Tuple[int, int, int, int]], height: int
) -> Optional[Tuple[Tuple[int, int], Tuple[int, int], float]]:
    """
    Fit a single lane line from multiple line segments.

    Args:
        lines: List of line segments as (x1, y1, x2, y2) tuples.
        height: Image height for extrapolation.

    Returns:
        Tuple of ((x_bottom, y_bottom), (x_top, y_top), angle) or None.
    """
    if not lines:
        return None
    points = []
    for x1, y1, x2, y2 in lines:
        points.append((x1, y1))
        points.append((x2, y2))
    if len(points) < 2:
        return None
    ys = np.array([p[1] for p in points])
    xs = np.array([p[0] for p in points])
    m, b = np.polyfit(ys, xs, 1)
    y_bottom = height
    y_top = int(height * 0.6)
    x_bottom = int(m * y_bottom + b)
    x_top = int(m * y_top + b)
    angle = np.arctan(m)
    return (x_bottom, y_bottom), (x_top, y_top), angle


def build_lane_mask(frame: np.ndarray) -> Optional[Tuple[np.ndarray, float]]:
    """
    Build a lane mask from a frame and compute angle variance.

    Args:
        frame: Input BGR image.

    Returns:
        Tuple of (lane mask, angle variance) or None if no lanes detected.
    """
    height, width = frame.shape[:2]
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (5, 5), 0)
    edges = cv2.Canny(blur, 50, 150)
    masked = region_of_interest(edges)
    lines = cv2.HoughLinesP(
        masked, 1, np.pi / 180, threshold=50, minLineLength=50, maxLineGap=180
    )
    if lines is None:
        return None

    left_lines = []
    right_lines = []
    for x1, y1, x2, y2 in lines[:, 0]:
        if x2 == x1:
            continue
        slope = (y2 - y1) / (x2 - x1)
        if abs(slope) < 0.5:
            continue
        if slope < 0:
            left_lines.append((x1, y1, x2, y2))
        else:
            right_lines.append((x1, y1, x2, y2))

    left = fit_lane_line(left_lines, height)
    right = fit_lane_line(right_lines, height)

    if left is None or right is None:
        return None

    left_angle = left[2]
    right_angle = right[2]
    angle_variance = abs(left_angle - right_angle)

    mask = np.zeros((height, width), dtype=np.uint8)
    cv2.line(mask, left[0], left[1], (255,), thickness=10)
    cv2.line(mask, right[0], right[1], (255,), thickness=10)

    return mask, angle_variance


def mask_to_polygons(mask: np.ndarray) -> List[List[float]]:
    """
    Convert a binary mask to YOLO polygon format.

    Args:
        mask: Binary mask image.

    Returns:
        List of polygons, each as [x1, y1, x2, y2, ...] normalized coordinates.
    """
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    polygons = []
    height, width = mask.shape
    for contour in contours:
        area = cv2.contourArea(contour)
        if area < 200:
            continue
        approx = cv2.approxPolyDP(contour, 2.0, True)
        coords = []
        for point in approx[:, 0, :]:
            x, y = point.tolist()
            coords.extend([x / width, y / height])
        if len(coords) >= 6:
            polygons.append(coords)
    return polygons


def classify_brightness(frame: np.ndarray) -> Tuple[Scenario, float]:
    """
    Classify lighting scenario based on brightness histogram.

    Args:
        frame: Input BGR image.

    Returns:
        Tuple of (Scenario enum, mean brightness value).
    """
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    brightness = float(np.mean(gray))
    scenario = Scenario.DAY if brightness >= BRIGHTNESS_THRESHOLD else Scenario.NIGHT
    return scenario, brightness


def classify_curve(angle_variance: float) -> CurveType:
    """
    Classify road curve type based on lane angle variance.

    Args:
        angle_variance: Difference between left and right lane angles.

    Returns:
        CurveType enum value.
    """
    return CurveType.CURVED if angle_variance > CURVE_ANGLE_THRESHOLD else CurveType.STRAIGHT


def validate_image(image_path: Path) -> bool:
    """
    Validate that an image file is readable and not corrupt.

    Args:
        image_path: Path to image file.

    Returns:
        True if image is valid, False otherwise.
    """
    if not image_path.exists():
        return False
    img = cv2.imread(str(image_path))
    return img is not None and img.size > 0


def validate_label(label_path: Path) -> bool:
    """
    Validate that a label file exists and has valid YOLO format.

    Args:
        label_path: Path to label file.

    Returns:
        True if label is valid, False otherwise.
    """
    if not label_path.exists():
        return False
    try:
        with label_path.open("r", encoding="utf-8") as f:
            for line in f:
                parts = line.strip().split()
                if len(parts) < 7:
                    continue
                coords = [float(x) for x in parts[1:]]
                if not all(0 <= c <= 1 for c in coords):
                    return False
        return True
    except (ValueError, IOError):
        return False


def setup_output_directories(output_root: Path) -> Tuple[Path, Path, Path, Path]:
    """
    Create output directory structure for train/val splits.

    Args:
        output_root: Root output directory.

    Returns:
        Tuple of (train_images, train_labels, val_images, val_labels) paths.
    """
    train_images = output_root / "train" / "images"
    train_labels = output_root / "train" / "labels"
    val_images = output_root / "val" / "images"
    val_labels = output_root / "val" / "labels"

    for directory in [train_images, train_labels, val_images, val_labels]:
        directory.mkdir(parents=True, exist_ok=True)

    return train_images, train_labels, val_images, val_labels


def write_data_yaml(output_root: Path) -> None:
    """
    Write the data.yaml configuration file for YOLO training.

    Args:
        output_root: Root output directory for the dataset.
    """
    data_yaml = output_root / "data.yaml"
    content = "\n".join(
        [
            f"path: {output_root}",
            "train: train/images",
            "val: val/images",
            "names:",
            "  0: lane",
        ]
    )
    data_yaml.write_text(content)
    logger.info(f"Written data.yaml to {data_yaml}")


def write_metadata(output_root: Path, metadata: List[FrameMetadata]) -> None:
    """
    Write frame metadata to JSON file.

    Args:
        output_root: Root output directory.
        metadata: List of frame metadata objects.
    """
    metadata_file = output_root / "metadata.json"
    data = [
        {
            "frame_id": m.frame_id,
            "source_video": m.source_video,
            "scenario": m.scenario.value,
            "curve_type": m.curve_type.value,
            "brightness": round(m.brightness, 2),
            "angle_variance": round(m.angle_variance, 4),
        }
        for m in metadata
    ]
    metadata_file.write_text(json.dumps(data, indent=2))
    logger.info(f"Written metadata for {len(metadata)} frames to {metadata_file}")


def write_summary(output_root: Path, stats: DatasetStats) -> None:
    """
    Write dataset summary statistics to JSON file.

    Args:
        output_root: Root output directory.
        stats: Dataset statistics object.
    """
    summary_file = output_root / "summary.json"
    data = {
        "total_frames": stats.total_frames,
        "train_frames": stats.train_frames,
        "val_frames": stats.val_frames,
        "day_frames": stats.day_frames,
        "night_frames": stats.night_frames,
        "straight_frames": stats.straight_frames,
        "curved_frames": stats.curved_frames,
        "videos_processed": stats.videos_processed,
        "skipped_frames": stats.skipped_frames,
    }
    summary_file.write_text(json.dumps(data, indent=2))
    logger.info(f"Written summary to {summary_file}")


def split_balanced(
    metadata: List[FrameMetadata], train_ratio: float, seed: int
) -> Tuple[List[FrameMetadata], List[FrameMetadata]]:
    """
    Create balanced train/val split across scenarios.

    Args:
        metadata: List of all frame metadata.
        train_ratio: Ratio of training samples (0.0 to 1.0).
        seed: Random seed for reproducibility.

    Returns:
        Tuple of (train_metadata, val_metadata) lists.
    """
    random.seed(seed)

    day_frames = [m for m in metadata if m.scenario == Scenario.DAY]
    night_frames = [m for m in metadata if m.scenario == Scenario.NIGHT]

    random.shuffle(day_frames)
    random.shuffle(night_frames)

    day_split = int(len(day_frames) * train_ratio)
    night_split = int(len(night_frames) * train_ratio)

    train = day_frames[:day_split] + night_frames[:night_split]
    val = day_frames[day_split:] + night_frames[night_split:]

    random.shuffle(train)
    random.shuffle(val)

    return train, val


def split_simple(
    metadata: List[FrameMetadata], train_ratio: float, seed: int
) -> Tuple[List[FrameMetadata], List[FrameMetadata]]:
    """
    Create simple random train/val split.

    Args:
        metadata: List of all frame metadata.
        train_ratio: Ratio of training samples (0.0 to 1.0).
        seed: Random seed for reproducibility.

    Returns:
        Tuple of (train_metadata, val_metadata) lists.
    """
    random.seed(seed)
    metadata_copy = metadata.copy()
    random.shuffle(metadata_copy)
    split_idx = int(len(metadata_copy) * train_ratio)
    return metadata_copy[:split_idx], metadata_copy[split_idx:]


def process_video(
    video: Path,
    fps: float,
    max_frames: int,
    current_count: int,
) -> Tuple[List[Tuple[np.ndarray, List[List[float]], FrameMetadata]], bool]:
    """
    Process a single video and extract lane frames.

    Args:
        video: Path to video file.
        fps: Target frames per second to extract.
        max_frames: Maximum total frames to extract.
        current_count: Current frame count from previous videos.

    Returns:
        Tuple of (frame data list, reached_max_frames flag).
    """
    frames_data = []

    cap = cv2.VideoCapture(str(video))
    if not cap.isOpened():
        logger.warning(f"Could not open video: {video}")
        return frames_data, False

    video_fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    step = max(1, int(round(video_fps / fps)))
    idx = 0

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        if idx % step != 0:
            idx += 1
            continue

        result = build_lane_mask(frame)
        if result is None:
            idx += 1
            continue

        lane_mask, angle_variance = result
        polygons = mask_to_polygons(lane_mask)
        if not polygons:
            idx += 1
            continue

        scenario, brightness = classify_brightness(frame)
        curve_type = classify_curve(angle_variance)

        frame_id = f"{video.stem}_{idx:06d}"
        metadata = FrameMetadata(
            frame_id=frame_id,
            source_video=video.name,
            scenario=scenario,
            curve_type=curve_type,
            brightness=brightness,
            angle_variance=angle_variance,
        )

        frames_data.append((frame, polygons, metadata))

        if current_count + len(frames_data) >= max_frames:
            cap.release()
            return frames_data, True

        idx += 1

    cap.release()
    return frames_data, False


def process_existing_dataset(
    output_root: Path,
    balance_scenarios: bool,
    train_ratio: float,
    seed: int,
    overwrite: bool,
) -> Optional[DatasetStats]:
    """
    Process an existing flat dataset into train/val structure.

    Args:
        output_root: Root directory containing existing dataset.
        balance_scenarios: Whether to balance splits across scenarios.
        train_ratio: Ratio for train split.
        seed: Random seed.
        overwrite: Whether to overwrite existing files.

    Returns:
        DatasetStats if processing succeeded, None otherwise.
    """
    images_dir = output_root / "images"
    labels_dir = output_root / "labels"

    if not images_dir.exists() or not labels_dir.exists():
        return None

    train_images, train_labels, val_images, val_labels = setup_output_directories(
        output_root
    )

    image_files = list(images_dir.glob("*.jpg")) + list(images_dir.glob("*.png"))
    if not image_files:
        logger.warning("No images found in existing dataset")
        return None

    metadata = []
    skipped = 0

    for img_path in image_files:
        label_path = labels_dir / f"{img_path.stem}.txt"

        if not validate_image(img_path):
            skipped += 1
            continue

        if not validate_label(label_path):
            skipped += 1
            continue

        frame = cv2.imread(str(img_path))
        if frame is None:
            skipped += 1
            continue

        scenario, brightness = classify_brightness(frame)

        with label_path.open("r", encoding="utf-8") as f:
            lines = f.readlines()

        angles = []
        for line in lines:
            parts = line.strip().split()
            if len(parts) >= 7:
                coords = [float(x) for x in parts[1:]]
                if len(coords) >= 6:
                    x_coords = coords[::2]
                    if len(x_coords) >= 3:
                        angle = np.arctan2(
                            coords[3] - coords[1], coords[2] - coords[0]
                        )
                        angles.append(angle)

        angle_variance = float(np.var(angles)) if angles else 0.0
        curve_type = classify_curve(angle_variance)

        metadata.append(
            FrameMetadata(
                frame_id=img_path.stem,
                source_video=img_path.stem.split("_")[0]
                if "_" in img_path.stem
                else "unknown",
                scenario=scenario,
                curve_type=curve_type,
                brightness=brightness,
                angle_variance=angle_variance,
            )
        )

    if not metadata:
        logger.warning("No valid frames found in existing dataset")
        return None

    if balance_scenarios:
        train_meta, val_meta = split_balanced(metadata, train_ratio, seed)
    else:
        train_meta, val_meta = split_simple(metadata, train_ratio, seed)

    for m in train_meta:
        src_img = images_dir / f"{m.frame_id}.jpg"
        src_label = labels_dir / f"{m.frame_id}.txt"
        dst_img = train_images / f"{m.frame_id}.jpg"
        dst_label = train_labels / f"{m.frame_id}.txt"

        if src_img.suffix == ".png":
            dst_img = train_images / f"{m.frame_id}.png"

        if overwrite or not dst_img.exists():
            shutil.copy2(src_img, dst_img)
        if overwrite or not dst_label.exists():
            shutil.copy2(src_label, dst_label)

    for m in val_meta:
        src_img = images_dir / f"{m.frame_id}.jpg"
        src_label = labels_dir / f"{m.frame_id}.txt"
        dst_img = val_images / f"{m.frame_id}.jpg"
        dst_label = val_labels / f"{m.frame_id}.txt"

        if src_img.suffix == ".png":
            dst_img = val_images / f"{m.frame_id}.png"

        if overwrite or not dst_img.exists():
            shutil.copy2(src_img, dst_img)
        if overwrite or not dst_label.exists():
            shutil.copy2(src_label, dst_label)

    stats = DatasetStats(
        total_frames=len(metadata),
        train_frames=len(train_meta),
        val_frames=len(val_meta),
        day_frames=sum(1 for m in metadata if m.scenario == Scenario.DAY),
        night_frames=sum(1 for m in metadata if m.scenario == Scenario.NIGHT),
        straight_frames=sum(1 for m in metadata if m.curve_type == CurveType.STRAIGHT),
        curved_frames=sum(1 for m in metadata if m.curve_type == CurveType.CURVED),
        videos_processed=0,
        skipped_frames=skipped,
    )

    write_metadata(output_root, metadata)
    write_data_yaml(output_root)
    write_summary(output_root, stats)

    return stats


def main() -> None:
    """
    Main entry point for dataset preparation pipeline.

    Processes videos or existing dataset to create train/val splits
    with scenario classification and curve detection labels.
    """
    args = parse_args()
    input_root = Path(args.input_dir).resolve()
    output_root = Path(args.output_dir).resolve()

    logger.info(f"Input directory: {input_root}")
    logger.info(f"Output directory: {output_root}")
    logger.info(f"Max frames: {args.max_frames}")

    videos = list_videos(input_root)

    existing_images = output_root / "images"
    existing_labels = output_root / "labels"

    if videos and (not existing_images.exists() or args.max_frames > DEFAULT_MAX_FRAMES):
        logger.info(f"Found {len(videos)} videos to process")

        train_images, train_labels, val_images, val_labels = setup_output_directories(
            output_root
        )

        all_metadata: List[FrameMetadata] = []
        frame_count = 0
        videos_processed = 0
        skipped_frames = 0

        for video in videos:
            logger.info(f"Processing: {video.name}")

            frames_data, reached_max = process_video(
                video, args.fps, args.max_frames, frame_count
            )

            for frame, polygons, meta in frames_data:
                if meta.scenario == Scenario.DAY:
                    img_dir = train_images if frame_count % 5 != 0 else val_images
                    lbl_dir = train_labels if frame_count % 5 != 0 else val_labels
                else:
                    img_dir = train_images if frame_count % 5 != 0 else val_images
                    lbl_dir = train_labels if frame_count % 5 != 0 else val_labels

                image_path = img_dir / f"{meta.frame_id}.jpg"
                label_path = lbl_dir / f"{meta.frame_id}.txt"

                if image_path.exists() and not args.overwrite:
                    skipped_frames += 1
                    continue

                cv2.imwrite(str(image_path), frame)
                with label_path.open("w", encoding="utf-8") as f:
                    for polygon in polygons:
                        f.write("0 " + " ".join(f"{v:.6f}" for v in polygon) + "\n")

                all_metadata.append(meta)
                frame_count += 1

            videos_processed += 1

            if reached_max:
                break

        train_count = len(list(train_images.glob("*.jpg")))
        val_count = len(list(val_images.glob("*.jpg")))

        stats = DatasetStats(
            total_frames=frame_count,
            train_frames=train_count,
            val_frames=val_count,
            day_frames=sum(1 for m in all_metadata if m.scenario == Scenario.DAY),
            night_frames=sum(1 for m in all_metadata if m.scenario == Scenario.NIGHT),
            straight_frames=sum(
                1 for m in all_metadata if m.curve_type == CurveType.STRAIGHT
            ),
            curved_frames=sum(
                1 for m in all_metadata if m.curve_type == CurveType.CURVED
            ),
            videos_processed=videos_processed,
            skipped_frames=skipped_frames,
        )

        write_metadata(output_root, all_metadata)
        write_data_yaml(output_root)
        write_summary(output_root, stats)

        logger.info(f"Processed {frame_count} frames from {videos_processed} videos")
        logger.info(f"Train: {train_count}, Val: {val_count}")
        logger.info(f"Day: {stats.day_frames}, Night: {stats.night_frames}")

    elif existing_images.exists() and existing_labels.exists():
        logger.info("Processing existing dataset into train/val structure")

        stats = process_existing_dataset(
            output_root,
            args.balance_scenarios,
            TRAIN_RATIO,
            args.seed,
            args.overwrite,
        )

        if stats:
            logger.info(f"Processed {stats.total_frames} frames")
            logger.info(f"Train: {stats.train_frames}, Val: {stats.val_frames}")
            logger.info(f"Day: {stats.day_frames}, Night: {stats.night_frames}")
            logger.info(f"Straight: {stats.straight_frames}, Curved: {stats.curved_frames}")
        else:
            logger.error("Failed to process existing dataset")
            sys.exit(1)

    else:
        logger.error(f"No videos found in {input_root} and no existing dataset at {output_root}")
        sys.exit(1)


if __name__ == "__main__":
    main()
