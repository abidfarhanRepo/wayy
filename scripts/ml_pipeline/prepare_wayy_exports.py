#!/usr/bin/env python3
import argparse
import json
import os
import re
import shutil
import sys
import tempfile
import zipfile
from datetime import datetime
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple


STAMP_PATTERN = re.compile(r".+_(\d{8}_\d{6})\.")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Prepare Wayy export bundles for training."
    )
    parser.add_argument(
        "--input",
        required=True,
        nargs="+",
        help="Export zip file(s) or directories containing exports."
    )
    parser.add_argument(
        "--output",
        required=True,
        help="Output directory for prepared dataset."
    )
    parser.add_argument(
        "--copy-media",
        action="store_true",
        help="Copy capture videos/metadata into the output directory."
    )
    parser.add_argument(
        "--extract-frames",
        action="store_true",
        help="Extract frames using ffmpeg (requires ffmpeg installed)."
    )
    parser.add_argument(
        "--frame-rate",
        type=float,
        default=2.0,
        help="Frame extraction rate (fps) when --extract-frames is set."
    )
    return parser.parse_args()


def collect_exports(inputs: Iterable[str]) -> List[Tuple[str, Path]]:
    exports: List[Tuple[str, Path]] = []
    for item in inputs:
        path = Path(item).expanduser()
        if path.is_file() and path.suffix == ".zip":
            exports.append(("zip", path))
            continue
        if path.is_dir():
            zips = sorted(path.glob("*.zip"))
            if zips:
                exports.extend([("zip", z) for z in zips])
                continue
            if (path / "capture").exists():
                exports.append(("dir", path))
                continue
        print(f"Skipping unsupported input: {path}")
    return exports


def extract_export(source: Tuple[str, Path]) -> Tuple[Path, Optional[tempfile.TemporaryDirectory]]:
    kind, path = source
    if kind == "dir":
        return path, None
    temp_dir = tempfile.TemporaryDirectory()
    extract_path = Path(temp_dir.name)
    with zipfile.ZipFile(path, "r") as zf:
        zf.extractall(extract_path)
    return extract_path, temp_dir


def stamp_from_name(name: str) -> Optional[str]:
    match = STAMP_PATTERN.match(name)
    return match.group(1) if match else None


def build_video_index(capture_dir: Path) -> Dict[str, Path]:
    index: Dict[str, Path] = {}
    for video in capture_dir.glob("nav_capture_*.mp4"):
        stamp = stamp_from_name(video.name)
        if stamp:
            index[stamp] = video
    return index


def extract_locations(metadata_file: Path) -> List[Dict[str, object]]:
    records: List[Dict[str, object]] = []
    with metadata_file.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue
            if event.get("type") != "location":
                continue
            payload = event.get("payload") or {}
            records.append(
                {
                    "timestamp": event.get("timestamp"),
                    "lat": payload.get("lat"),
                    "lng": payload.get("lng"),
                    "speedMph": payload.get("speedMph"),
                    "bearing": payload.get("bearing"),
                    "accuracy": payload.get("accuracy"),
                    "arMode": payload.get("arMode"),
                }
            )
    return records


def copy_media_files(
    capture_dir: Path,
    output_dir: Path,
    stamps: Iterable[str]
) -> Dict[str, Dict[str, str]]:
    output_dir.mkdir(parents=True, exist_ok=True)
    mapping: Dict[str, Dict[str, str]] = {}
    for stamp in stamps:
        video = capture_dir / f"nav_capture_{stamp}.mp4"
        metadata = capture_dir / f"metadata_{stamp}.jsonl"
        entry: Dict[str, str] = {}
        if video.exists():
            dest = output_dir / video.name
            shutil.copy2(video, dest)
            entry["video"] = str(dest)
        if metadata.exists():
            dest = output_dir / metadata.name
            shutil.copy2(metadata, dest)
            entry["metadata"] = str(dest)
        if entry:
            mapping[stamp] = entry
    return mapping


def extract_frames(output_dir: Path, video_path: Path, frame_rate: float) -> None:
    if shutil.which("ffmpeg") is None:
        print("ffmpeg not found; skipping frame extraction.")
        return
    output_dir.mkdir(parents=True, exist_ok=True)
    output_pattern = output_dir / "frame_%06d.jpg"
    os.system(
        f"ffmpeg -hide_banner -loglevel error -i \"{video_path}\" "
        f"-vf fps={frame_rate} \"{output_pattern}\""
    )


def main() -> None:
    args = parse_args()
    output_root = Path(args.output).expanduser()
    output_root.mkdir(parents=True, exist_ok=True)
    manifest_path = output_root / "wayy_locations.jsonl"
    summary_path = output_root / "summary.json"
    all_records: List[Dict[str, object]] = []
    capture_mapping: Dict[str, Dict[str, str]] = {}

    exports = collect_exports(args.input)
    if not exports:
        print("No valid exports found.")
        sys.exit(1)

    for export in exports:
        extract_path, temp_dir = extract_export(export)
        try:
            capture_dir = extract_path / "capture"
            if not capture_dir.exists():
                print(f"Missing capture directory: {extract_path}")
                continue
            video_index = build_video_index(capture_dir)
            metadata_files = sorted(capture_dir.glob("metadata_*.jsonl"))
            for metadata_file in metadata_files:
                stamp = stamp_from_name(metadata_file.name)
                records = extract_locations(metadata_file)
                for record in records:
                    record["captureStamp"] = stamp
                    record["metadataFile"] = str(metadata_file)
                    video = video_index.get(stamp) if stamp else None
                    record["videoFile"] = str(video) if video else None
                all_records.extend(records)

                if args.copy_media and stamp:
                    mapping = copy_media_files(capture_dir, output_root / "captures", [stamp])
                    capture_mapping.update(mapping)
                    if args.extract_frames and stamp in mapping and "video" in mapping[stamp]:
                        extract_frames(
                            output_root / "frames" / stamp,
                            Path(mapping[stamp]["video"]),
                            args.frame_rate
                        )
        finally:
            if temp_dir is not None:
                temp_dir.cleanup()

    with manifest_path.open("w", encoding="utf-8") as handle:
        for record in all_records:
            handle.write(json.dumps(record) + "\n")

    summary = {
        "generatedAt": datetime.utcnow().isoformat() + "Z",
        "records": len(all_records),
        "capturesCopied": len(capture_mapping),
    }
    summary_path.write_text(json.dumps(summary, indent=2))
    print(f"Wrote {len(all_records)} records to {manifest_path}")


if __name__ == "__main__":
    main()
