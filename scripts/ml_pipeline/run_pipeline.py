#!/usr/bin/env python3
import argparse
import json
import subprocess
import sys
import time
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple

MPS_FAST_THRESHOLD = 12.0
MPS_MODERATE_THRESHOLD = 6.0
MPH_TO_MPS = 1 / 2.23694
STATE_FILE = "pipeline_state.json"


@dataclass
class CaptureStats:
    lat: float
    lng: float
    timestamp_ms: int
    time_bucket: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run the local Wayy ML pipeline on export bundles."
    )
    parser.add_argument(
        "--input-dir",
        default="exports",
        help="Folder containing wayy_export_*.zip or export directories."
    )
    parser.add_argument(
        "--output-dir",
        default="ml_pipeline/output",
        help="Folder for pipeline outputs."
    )
    parser.add_argument(
        "--model",
        default="app/src/main/assets/ml/yolov8n.pt",
        help="YOLO model path (.pt)."
    )
    parser.add_argument("--imgsz", type=int, default=640)
    parser.add_argument("--confidence", type=float, default=0.25)
    parser.add_argument("--frame-rate", type=float, default=2.0)
    parser.add_argument("--device", default="cpu")
    parser.add_argument("--watch", action="store_true")
    parser.add_argument("--interval", type=int, default=30)
    parser.add_argument("--max-exports", type=int, default=0)
    return parser.parse_args()


def list_sources(input_dir: Path) -> List[Path]:
    sources: List[Path] = []
    if not input_dir.exists():
        return sources
    for item in sorted(input_dir.iterdir()):
        if item.is_file() and item.suffix == ".zip":
            sources.append(item)
        elif item.is_dir() and (item / "capture").exists():
            sources.append(item)
    return sources


def load_state(output_dir: Path) -> Dict[str, object]:
    state_path = output_dir / STATE_FILE
    if state_path.exists():
        return json.loads(state_path.read_text())
    return {"processed": []}


def save_state(output_dir: Path, state: Dict[str, object]) -> None:
    state_path = output_dir / STATE_FILE
    state_path.write_text(json.dumps(state, indent=2))


def prepare_export(
    script_path: Path,
    source: Path,
    run_dir: Path,
    frame_rate: float
) -> None:
    run_dir.mkdir(parents=True, exist_ok=True)
    command = [
        sys.executable,
        str(script_path),
        "--input",
        str(source),
        "--output",
        str(run_dir),
        "--copy-media",
        "--extract-frames",
        "--frame-rate",
        str(frame_rate)
    ]
    subprocess.run(command, check=True)


def parse_locations(locations_path: Path) -> List[Dict[str, object]]:
    records: List[Dict[str, object]] = []
    if not locations_path.exists():
        return records
    with locations_path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                record = json.loads(line)
            except json.JSONDecodeError:
                continue
            records.append(record)
    return records


def build_capture_stats(records: Iterable[Dict[str, object]]) -> Dict[str, CaptureStats]:
    grouped: Dict[str, List[Dict[str, object]]] = defaultdict(list)
    for record in records:
        stamp = record.get("captureStamp")
        if not stamp:
            continue
        grouped[str(stamp)].append(record)
    stats: Dict[str, CaptureStats] = {}
    for stamp, items in grouped.items():
        lats = [r.get("lat") for r in items if isinstance(r.get("lat"), (int, float))]
        lngs = [r.get("lng") for r in items if isinstance(r.get("lng"), (int, float))]
        timestamps = [r.get("timestamp") for r in items if isinstance(r.get("timestamp"), (int, float))]
        if not lats or not lngs or not timestamps:
            continue
        avg_lat = sum(lats) / len(lats)
        avg_lng = sum(lngs) / len(lngs)
        avg_ts = int(sum(timestamps) / len(timestamps))
        dt = datetime.fromtimestamp(avg_ts / 1000, tz=timezone.utc)
        stats[stamp] = CaptureStats(
            lat=avg_lat,
            lng=avg_lng,
            timestamp_ms=avg_ts,
            time_bucket=dt.hour
        )
    return stats


def time_bucket(timestamp_ms: Optional[float]) -> Optional[int]:
    if timestamp_ms is None:
        return None
    dt = datetime.fromtimestamp(timestamp_ms / 1000, tz=timezone.utc)
    return dt.hour


def build_traffic_segments(records: Iterable[Dict[str, object]]) -> List[Dict[str, object]]:
    grouped: Dict[str, List[Dict[str, object]]] = defaultdict(list)
    for record in records:
        stamp = record.get("captureStamp")
        if not stamp:
            continue
        grouped[str(stamp)].append(record)
    features: List[Dict[str, object]] = []
    for stamp, items in grouped.items():
        items_sorted = sorted(
            items,
            key=lambda r: r.get("timestamp") or 0
        )
        for prev, current in zip(items_sorted, items_sorted[1:]):
            lat1 = prev.get("lat")
            lng1 = prev.get("lng")
            lat2 = current.get("lat")
            lng2 = current.get("lng")
            if not all(isinstance(v, (int, float)) for v in [lat1, lng1, lat2, lng2]):
                continue
            speed_mph = current.get("speedMph") or prev.get("speedMph")
            if not isinstance(speed_mph, (int, float)):
                continue
            speed_mps = float(speed_mph) * MPH_TO_MPS
            if speed_mps >= MPS_FAST_THRESHOLD:
                severity = "fast"
            elif speed_mps >= MPS_MODERATE_THRESHOLD:
                severity = "moderate"
            else:
                severity = "slow"
            bucket = time_bucket(current.get("timestamp"))
            feature = {
                "type": "Feature",
                "geometry": {
                    "type": "LineString",
                    "coordinates": [
                        [float(lng1), float(lat1)],
                        [float(lng2), float(lat2)]
                    ]
                },
                "properties": {
                    "severity": severity,
                    "averageSpeedMps": speed_mps,
                    "timeBucket": bucket,
                    "captureStamp": stamp
                }
            }
            features.append(feature)
    return features


def run_inference(
    model_path: Path,
    frames_root: Path,
    capture_stats: Dict[str, CaptureStats],
    imgsz: int,
    confidence: float,
    device: str
) -> List[Dict[str, object]]:
    try:
        from ultralytics import YOLO
    except ImportError:
        print("Ultralytics not installed. Run: pip install ultralytics")
        sys.exit(1)

    model = YOLO(str(model_path))
    detections: List[Dict[str, object]] = []
    if not frames_root.exists():
        return detections
    images = sorted(
        [p for p in frames_root.rglob("*") if p.suffix.lower() in {".jpg", ".jpeg", ".png"}]
    )
    if not images:
        return detections
    results = model.predict(
        source=[str(p) for p in images],
        imgsz=imgsz,
        conf=confidence,
        device=device,
        stream=True
    )
    for result in results:
        image_path = Path(result.path)
        stamp = image_path.parent.name
        stats = capture_stats.get(stamp)
        if stats is None:
            continue
        for box in result.boxes:
            class_id = int(box.cls[0]) if box.cls is not None else None
            if class_id is None:
                continue
            class_name = result.names.get(class_id, str(class_id))
            conf = float(box.conf[0]) if box.conf is not None else None
            detections.append(
                {
                    "captureStamp": stamp,
                    "frame": image_path.name,
                    "class": class_name,
                    "confidence": conf,
                    "lat": stats.lat,
                    "lng": stats.lng,
                    "timestamp": stats.timestamp_ms,
                    "timeBucket": stats.time_bucket
                }
            )
    return detections


def write_jsonl(path: Path, records: Iterable[Dict[str, object]]) -> None:
    with path.open("w", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record) + "\n")


def write_geojson(path: Path, features: List[Dict[str, object]]) -> None:
    payload = {"type": "FeatureCollection", "features": features}
    path.write_text(json.dumps(payload, indent=2))


def aggregate_road_conditions(detections: Iterable[Dict[str, object]]) -> List[Dict[str, object]]:
    buckets: Dict[Tuple[str, float, float, int], Dict[str, object]] = {}
    for record in detections:
        lat = record.get("lat")
        lng = record.get("lng")
        time_bucket_value = record.get("timeBucket")
        label = record.get("class")
        if not isinstance(lat, (int, float)) or not isinstance(lng, (int, float)):
            continue
        if not isinstance(time_bucket_value, int) or label is None:
            continue
        key = (
            str(label),
            round(float(lat), 4),
            round(float(lng), 4),
            time_bucket_value
        )
        bucket = buckets.setdefault(
            key,
            {
                "label": str(label),
                "lat": round(float(lat), 4),
                "lng": round(float(lng), 4),
                "timeBucket": time_bucket_value,
                "count": 0,
                "confTotal": 0.0
            }
        )
        bucket["count"] += 1
        conf = record.get("confidence")
        if isinstance(conf, (int, float)):
            bucket["confTotal"] += float(conf)

    features: List[Dict[str, object]] = []
    for bucket in buckets.values():
        count = bucket["count"]
        avg_conf = bucket["confTotal"] / count if count else None
        features.append(
            {
                "type": "Feature",
                "geometry": {
                    "type": "Point",
                    "coordinates": [bucket["lng"], bucket["lat"]]
                },
                "properties": {
                    "label": bucket["label"],
                    "count": count,
                    "avgConfidence": avg_conf,
                    "timeBucket": bucket["timeBucket"]
                }
            }
        )
    return features


def summarize(
    detections: List[Dict[str, object]],
    traffic_features: List[Dict[str, object]]
) -> Dict[str, object]:
    class_counts: Dict[str, int] = defaultdict(int)
    for record in detections:
        label = record.get("class")
        if label is None:
            continue
        class_counts[str(label)] += 1
    return {
        "generatedAt": datetime.now(tz=timezone.utc).isoformat(),
        "detections": len(detections),
        "trafficSegments": len(traffic_features),
        "classCounts": dict(sorted(class_counts.items()))
    }


def process_source(
    source: Path,
    run_dir: Path,
    script_path: Path,
    args: argparse.Namespace
) -> None:
    prepare_export(script_path, source, run_dir, args.frame_rate)
    locations_path = run_dir / "wayy_locations.jsonl"
    records = parse_locations(locations_path)
    capture_stats = build_capture_stats(records)
    detections = run_inference(
        model_path=Path(args.model),
        frames_root=run_dir / "frames",
        capture_stats=capture_stats,
        imgsz=args.imgsz,
        confidence=args.confidence,
        device=args.device
    )
    detections_path = run_dir / "detections.jsonl"
    write_jsonl(detections_path, detections)
    road_features = aggregate_road_conditions(detections)
    road_path = run_dir / "road_conditions.geojson"
    write_geojson(road_path, road_features)
    traffic_features = build_traffic_segments(records)
    traffic_path = run_dir / "traffic_segments.geojson"
    write_geojson(traffic_path, traffic_features)
    summary_path = run_dir / "summary.json"
    summary_path.write_text(json.dumps(summarize(detections, traffic_features), indent=2))

    latest_dir = run_dir.parent / "latest"
    latest_dir.mkdir(parents=True, exist_ok=True)
    for artifact in [detections_path, road_path, traffic_path, summary_path]:
        target = latest_dir / artifact.name
        target.write_text(artifact.read_text())


def run_once(args: argparse.Namespace) -> int:
    input_dir = Path(args.input_dir).expanduser()
    output_dir = Path(args.output_dir).expanduser()
    output_dir.mkdir(parents=True, exist_ok=True)
    state = load_state(output_dir)
    processed_paths = {
        item.get("sourcePath")
        for item in state.get("processed", [])
        if isinstance(item, dict) and item.get("sourcePath")
    }
    sources = list_sources(input_dir)
    if not sources:
        print(f"No exports found in {input_dir}")
        return 0
    script_path = Path(__file__).with_name("prepare_wayy_exports.py")
    processed_count = 0
    for source in sources:
        source_path = str(source.resolve())
        if source_path in processed_paths:
            continue
        source_id = source.stem if source.is_file() else source.name
        run_dir = output_dir / source_id
        process_source(source, run_dir, script_path, args)
        state.setdefault("processed", []).append(
            {"sourcePath": source_path, "runDir": str(run_dir)}
        )
        save_state(output_dir, state)
        processed_count += 1
        if args.max_exports and processed_count >= args.max_exports:
            break
    return processed_count


def main() -> None:
    args = parse_args()
    if args.watch:
        while True:
            run_once(args)
            time.sleep(max(args.interval, 5))
    else:
        run_once(args)


if __name__ == "__main__":
    main()
