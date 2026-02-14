#!/usr/bin/env python3
"""
Enhanced lane preprocessing module for improved pseudo-label generation.

This module provides advanced preprocessing techniques for lane detection including:
- Adaptive ROI based on vanishing point detection
- Low-light/night mode enhancement
- Improved lane mask generation with polygon filling
- Curve-aware processing
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import List, Optional, Tuple

import cv2
import numpy as np


@dataclass
class LaneDetectionConfig:
    """Configuration parameters for lane detection."""
    canny_low: int = 50
    canny_high: int = 150
    hough_threshold: int = 50
    hough_min_line_length: int = 50
    hough_max_line_gap: int = 180
    min_slope: float = 0.3
    max_slope: float = 3.0
    brightness_threshold: int = 60
    gamma_low: float = 0.6
    gamma_high: float = 1.2
    clahe_clip_limit: float = 3.0
    clahe_grid_size: Tuple[int, int] = (8, 8)
    morph_kernel_size: int = 5
    temporal_alpha: float = 0.3
    min_line_points: int = 2
    curve_detection_window: int = 50


@dataclass
class VanishingPoint:
    """Detected vanishing point with confidence."""
    x: float
    y: float
    confidence: float


@dataclass
class CurveInfo:
    """Information about detected curve."""
    direction: str
    confidence: float
    left_slope_trend: float
    right_slope_trend: float


class TemporalSmoother:
    """Maintains temporal smoothing for video frames."""
    
    def __init__(self, alpha: float = 0.3):
        self.alpha = alpha
        self.left_line: Optional[Tuple[Tuple[int, int], Tuple[int, int]]] = None
        self.right_line: Optional[Tuple[Tuple[int, int], Tuple[int, int]]] = None
        self.prev_mask: Optional[np.ndarray] = None
    
    def smooth_line(
        self,
        line: Optional[Tuple[Tuple[int, int], Tuple[int, int]]],
        prev_line: Optional[Tuple[Tuple[int, int], Tuple[int, int]]]
    ) -> Optional[Tuple[Tuple[int, int], Tuple[int, int]]]:
        if line is None:
            return prev_line
        if prev_line is None:
            return line
        
        smoothed = (
            (
                int(self.alpha * line[0][0] + (1 - self.alpha) * prev_line[0][0]),
                int(self.alpha * line[0][1] + (1 - self.alpha) * prev_line[0][1])
            ),
            (
                int(self.alpha * line[1][0] + (1 - self.alpha) * prev_line[1][0]),
                int(self.alpha * line[1][1] + (1 - self.alpha) * prev_line[1][1])
            )
        )
        return smoothed
    
    def update(
        self,
        left: Optional[Tuple[Tuple[int, int], Tuple[int, int]]],
        right: Optional[Tuple[Tuple[int, int], Tuple[int, int]]]
    ) -> Tuple[Optional[Tuple[Tuple[int, int], Tuple[int, int]]], Optional[Tuple[Tuple[int, int], Tuple[int, int]]]]:
        self.left_line = self.smooth_line(left, self.left_line)
        self.right_line = self.smooth_line(right, self.right_line)
        return self.left_line, self.right_line
    
    def smooth_mask(self, mask: Optional[np.ndarray]) -> Optional[np.ndarray]:
        if mask is None:
            return self.prev_mask
        if self.prev_mask is None:
            self.prev_mask = mask.copy()
            return mask
        
        blended = cv2.addWeighted(mask, self.alpha, self.prev_mask, 1 - self.alpha, 0)
        self.prev_mask = blended.copy()
        return blended
    
    def reset(self) -> None:
        self.left_line = None
        self.right_line = None
        self.prev_mask = None


def detect_vanishing_point(
    lines: Optional[np.ndarray],
    height: int,
    width: int
) -> Optional[VanishingPoint]:
    """
    Detect vanishing point from line intersections.
    
    Uses RANSAC-like approach to find the point where most lines converge.
    
    Args:
        lines: Hough lines from cv2.HoughLinesP
        height: Image height
        width: Image width
    
    Returns:
        VanishingPoint if detected, None otherwise
    """
    if lines is None or len(lines) < 2:
        return None
    
    intersections: List[Tuple[float, float]] = []
    
    for i in range(len(lines)):
        for j in range(i + 1, len(lines)):
            x1, y1, x2, y2 = lines[i][0]
            x3, y3, x4, y4 = lines[j][0]
            
            denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
            if abs(denom) < 1e-6:
                continue
            
            px = ((x1 * y2 - y1 * x2) * (x3 - x4) - (x1 - x2) * (x3 * y4 - y3 * x4)) / denom
            py = ((x1 * y2 - y1 * x2) * (y3 - y4) - (y1 - y2) * (x3 * y4 - y3 * x4)) / denom
            
            if 0 <= px <= width and 0 <= py <= height * 0.7:
                intersections.append((px, py))
    
    if len(intersections) < 3:
        return None
    
    points = np.array(intersections)
    center_x = np.median(points[:, 0])
    center_y = np.median(points[:, 1])
    
    distances = np.sqrt((points[:, 0] - center_x) ** 2 + (points[:, 1] - center_y) ** 2)
    inliers = np.sum(distances < 50)
    confidence = min(inliers / max(len(intersections), 1), 1.0)
    
    return VanishingPoint(x=center_x, y=center_y, confidence=confidence)


def region_of_interest_adaptive(
    edges: np.ndarray,
    vanishing_point: Optional[VanishingPoint] = None,
    curve_info: Optional[CurveInfo] = None,
    top_offset: float = 0.58,
    bottom_offset: float = 0.08
) -> np.ndarray:
    """
    Create adaptive region of interest mask based on vanishing point and curve info.
    
    Args:
        edges: Edge detection output
        vanishing_point: Detected vanishing point (or None for default)
        curve_info: Curve direction info for width adjustment
        top_offset: Vertical position of top edge (fraction of height)
        bottom_offset: Horizontal inset for bottom corners
    
    Returns:
        Binary mask of the region of interest
    """
    height, width = edges.shape
    mask = np.zeros_like(edges)
    
    if vanishing_point is not None and vanishing_point.confidence > 0.3:
        vp_x = vanishing_point.x
        vp_y = vanishing_point.y
        
        top_width = width * 0.15
        curve_adjustment = 0.0
        if curve_info is not None:
            if curve_info.direction == "left":
                curve_adjustment = -width * 0.05 * curve_info.confidence
            elif curve_info.direction == "right":
                curve_adjustment = width * 0.05 * curve_info.confidence
        
        polygon = np.array([
            [
                (int(width * bottom_offset + curve_adjustment), height),
                (int(vp_x - top_width / 2 + curve_adjustment), int(vp_y)),
                (int(vp_x + top_width / 2 + curve_adjustment), int(vp_y)),
                (int(width * (1 - bottom_offset) + curve_adjustment), height),
            ]
        ], dtype=np.int32)
    else:
        left_adjust = 0.0
        right_adjust = 0.0
        if curve_info is not None:
            if curve_info.direction == "left":
                left_adjust = -width * 0.03 * curve_info.confidence
            elif curve_info.direction == "right":
                right_adjust = width * 0.03 * curve_info.confidence
        
        polygon = np.array([
            [
                (int(width * 0.08 + left_adjust), height),
                (int(width * 0.46 + left_adjust), int(height * top_offset)),
                (int(width * 0.54 + right_adjust), int(height * top_offset)),
                (int(width * 0.92 + right_adjust), height),
            ]
        ], dtype=np.int32)
    
    cv2.fillPoly(mask, polygon, 255)
    return cv2.bitwise_and(edges, mask)


def enhance_low_light(
    frame: np.ndarray,
    config: Optional[LaneDetectionConfig] = None
) -> Tuple[np.ndarray, bool]:
    """
    Enhance image quality for low-light/night conditions.
    
    Applies CLAHE and gamma correction when brightness is below threshold.
    
    Args:
        frame: Input BGR image
        config: Detection configuration parameters
    
    Returns:
        Tuple of (enhanced frame, was_enhanced flag)
    """
    if config is None:
        config = LaneDetectionConfig()
    
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    avg_brightness = np.mean(gray)
    
    if avg_brightness >= config.brightness_threshold:
        return frame.copy(), False
    
    lab = cv2.cvtColor(frame, cv2.COLOR_BGR2LAB)
    l_channel, a_channel, b_channel = cv2.split(lab)
    
    clahe = cv2.createCLAHE(
        clipLimit=config.clahe_clip_limit,
        tileGridSize=config.clahe_grid_size
    )
    l_channel = clahe.apply(l_channel)
    
    lab = cv2.merge([l_channel, a_channel, b_channel])
    enhanced = cv2.cvtColor(lab, cv2.COLOR_LAB2BGR)
    
    gamma = config.gamma_low
    inv_gamma = 1.0 / gamma
    table = np.array([((i / 255.0) ** inv_gamma) * 255 for i in range(256)]).astype(np.uint8)
    enhanced = cv2.LUT(enhanced, table)
    
    return enhanced, True


def detect_curve_direction(
    left_lines: List[Tuple[int, int, int, int]],
    right_lines: List[Tuple[int, int, int, int]],
    height: int,
    width: int
) -> CurveInfo:
    """
    Detect curve direction from lane line slope trends.
    
    Analyzes how slopes change from top to bottom of the frame.
    
    Args:
        left_lines: List of left lane line segments
        right_lines: List of right lane line segments
        height: Image height
        width: Image width
    
    Returns:
        CurveInfo with direction and confidence
    """
    def compute_slope_trend(lines: List[Tuple[int, int, int, int]]) -> float:
        if len(lines) < 2:
            return 0.0
        
        slopes_with_y = []
        for x1, y1, x2, y2 in lines:
            if abs(x2 - x1) < 1e-6:
                continue
            slope = (y2 - y1) / (x2 - x1)
            avg_y = (y1 + y2) / 2
            slopes_with_y.append((slope, avg_y))
        
        if len(slopes_with_y) < 2:
            return 0.0
        
        slopes_with_y.sort(key=lambda x: x[1])
        top_slopes = [s for s, y in slopes_with_y[:len(slopes_with_y) // 2]]
        bottom_slopes = [s for s, y in slopes_with_y[len(slopes_with_y) // 2:]]
        
        if not top_slopes or not bottom_slopes:
            return 0.0
        
        return np.mean(bottom_slopes) - np.mean(top_slopes)
    
    left_trend = compute_slope_trend(left_lines)
    right_trend = compute_slope_trend(right_lines)
    
    combined_trend = (left_trend + right_trend) / 2
    
    if combined_trend > 0.1:
        direction = "right"
        confidence = min(abs(combined_trend) / 0.3, 1.0)
    elif combined_trend < -0.1:
        direction = "left"
        confidence = min(abs(combined_trend) / 0.3, 1.0)
    else:
        direction = "straight"
        confidence = 1.0 - min(abs(combined_trend) / 0.1, 1.0)
    
    return CurveInfo(
        direction=direction,
        confidence=confidence,
        left_slope_trend=left_trend,
        right_slope_trend=right_trend
    )


def fit_lane_line(
    lines: List[Tuple[int, int, int, int]],
    height: int,
    y_top_ratio: float = 0.6,
    use_polyfit: bool = False,
    poly_degree: int = 2
) -> Optional[Tuple[Tuple[int, int], Tuple[int, int]]]:
    """
    Fit a line or polynomial to lane line segments.
    
    Args:
        lines: List of line segments as (x1, y1, x2, y2) tuples
        height: Image height
        y_top_ratio: Ratio for top y coordinate
        use_polyfit: If True, use polynomial fitting (degree 2)
        poly_degree: Polynomial degree for curve fitting
    
    Returns:
        Line endpoints ((x_bottom, y_bottom), (x_top, y_top)) or None
    """
    if not lines:
        return None
    
    points: List[Tuple[int, int]] = []
    for x1, y1, x2, y2 in lines:
        points.append((x1, y1))
        points.append((x2, y2))
    
    if len(points) < 2:
        return None
    
    ys = np.array([p[1] for p in points])
    xs = np.array([p[0] for p in points])
    
    try:
        if use_polyfit and len(points) >= poly_degree + 1:
            coeffs = np.polyfit(ys, xs, poly_degree)
            y_bottom = height
            y_top = int(height * y_top_ratio)
            x_bottom = int(np.polyval(coeffs, y_bottom))
            x_top = int(np.polyval(coeffs, y_top))
        else:
            m, b = np.polyfit(ys, xs, 1)
            y_bottom = height
            y_top = int(height * y_top_ratio)
            x_bottom = int(m * y_bottom + b)
            x_top = int(m * y_top + b)
    except (np.linalg.LinAlgError, ValueError):
        return None
    
    return (x_bottom, y_bottom), (x_top, y_top)


def apply_morphological_cleanup(
    mask: np.ndarray,
    kernel_size: int = 5,
    operation: str = "close"
) -> np.ndarray:
    """
    Apply morphological operations to clean up the lane mask.
    
    Args:
        mask: Binary lane mask
        kernel_size: Size of the morphological kernel
        operation: Type of operation ("close", "open", "both")
    
    Returns:
        Cleaned mask
    """
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (kernel_size, kernel_size))
    
    cleaned = mask.copy()
    
    if operation in ("close", "both"):
        cleaned = cv2.morphologyEx(cleaned, cv2.MORPH_CLOSE, kernel)
    if operation in ("open", "both"):
        cleaned = cv2.morphologyEx(cleaned, cv2.MORPH_OPEN, kernel)
    
    return cleaned


def classify_lines(
    lines: np.ndarray,
    config: Optional[LaneDetectionConfig] = None
) -> Tuple[List[Tuple[int, int, int, int]], List[Tuple[int, int, int, int]]]:
    """
    Classify Hough lines into left and right lane lines.
    
    Args:
        lines: Raw Hough lines from cv2.HoughLinesP
        config: Detection configuration
    
    Returns:
        Tuple of (left_lines, right_lines)
    """
    if config is None:
        config = LaneDetectionConfig()
    
    left_lines: List[Tuple[int, int, int, int]] = []
    right_lines: List[Tuple[int, int, int, int]] = []
    
    for line in lines:
        x1, y1, x2, y2 = line[0]
        
        if abs(x2 - x1) < 1e-6:
            continue
        
        slope = (y2 - y1) / (x2 - x1)
        
        if abs(slope) < config.min_slope or abs(slope) > config.max_slope:
            continue
        
        if slope < 0:
            left_lines.append((x1, y1, x2, y2))
        else:
            right_lines.append((x1, y1, x2, y2))
    
    return left_lines, right_lines


def build_lane_mask_v2(
    frame: np.ndarray,
    config: Optional[LaneDetectionConfig] = None,
    temporal_smoother: Optional[TemporalSmoother] = None,
    night_mode: bool = False,
    curve_detection: bool = True,
    polygon_fill: bool = True,
    use_polyfit: bool = False
) -> Optional[np.ndarray]:
    """
    Generate an improved lane mask with advanced features.
    
    This is the main entry point for lane mask generation with:
    - Adaptive ROI based on vanishing point
    - Night mode enhancement
    - Curve-aware processing
    - Polygon filling between lanes
    - Temporal smoothing for video
    
    Args:
        frame: Input BGR image
        config: Detection configuration parameters
        temporal_smoother: TemporalSmoother instance for video processing
        night_mode: Force night mode enhancement
        curve_detection: Enable curve direction detection
        polygon_fill: Fill polygon between lane lines (vs. thin lines)
        use_polyfit: Use polynomial fitting for curves
    
    Returns:
        Binary lane mask or None if no lanes detected
    """
    if config is None:
        config = LaneDetectionConfig()
    
    height, width = frame.shape[:2]
    
    processed_frame = frame.copy()
    enhanced = False
    
    if night_mode:
        processed_frame, enhanced = enhance_low_light(frame, config)
    else:
        processed_frame, enhanced = enhance_low_light(frame, config)
    
    gray = cv2.cvtColor(processed_frame, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (5, 5), 0)
    edges = cv2.Canny(blur, config.canny_low, config.canny_high)
    
    lines = cv2.HoughLinesP(
        edges,
        1,
        np.pi / 180,
        threshold=config.hough_threshold,
        minLineLength=config.hough_min_line_length,
        maxLineGap=config.hough_max_line_gap
    )
    
    if lines is None:
        return None
    
    vp = detect_vanishing_point(lines, height, width)
    
    left_lines, right_lines = classify_lines(lines, config)
    
    curve_info: Optional[CurveInfo] = None
    if curve_detection and left_lines and right_lines:
        curve_info = detect_curve_direction(left_lines, right_lines, height, width)
    
    masked = region_of_interest_adaptive(edges, vp, curve_info)
    
    lines_in_roi = cv2.HoughLinesP(
        masked,
        1,
        np.pi / 180,
        threshold=config.hough_threshold,
        minLineLength=config.hough_min_line_length,
        maxLineGap=config.hough_max_line_gap
    )
    
    if lines_in_roi is None:
        if temporal_smoother is not None and temporal_smoother.prev_mask is not None:
            return temporal_smoother.prev_mask.copy()
        return None
    
    left_lines, right_lines = classify_lines(lines_in_roi, config)
    
    left = fit_lane_line(left_lines, height, use_polyfit=use_polyfit)
    right = fit_lane_line(right_lines, height, use_polyfit=use_polyfit)
    
    if left is None and right is None:
        if temporal_smoother is not None and temporal_smoother.prev_mask is not None:
            return temporal_smoother.prev_mask.copy()
        return None
    
    if temporal_smoother is not None:
        left, right = temporal_smoother.update(left, right)
    
    mask = np.zeros((height, width), dtype=np.uint8)
    
    if polygon_fill and left is not None and right is not None:
        polygon = np.array([
            [left[0][0], left[0][1]],
            [left[1][0], left[1][1]],
            [right[1][0], right[1][1]],
            [right[0][0], right[0][1]],
        ], dtype=np.int32)
        cv2.fillPoly(mask, [polygon], 255)
    else:
        line_thickness = 10
        if left is not None:
            cv2.line(mask, left[0], left[1], 255, thickness=line_thickness)
        if right is not None:
            cv2.line(mask, right[0], right[1], 255, thickness=line_thickness)
    
    mask = apply_morphological_cleanup(mask, config.morph_kernel_size, operation="both")
    
    if temporal_smoother is not None:
        mask = temporal_smoother.smooth_mask(mask)
    
    return mask


def build_lane_mask_single_lane(
    frame: np.ndarray,
    config: Optional[LaneDetectionConfig] = None,
    is_left: Optional[bool] = None
) -> Optional[np.ndarray]:
    """
    Generate lane mask when only one lane line is detected.
    
    Estimates the missing lane based on typical lane width.
    
    Args:
        frame: Input BGR image
        config: Detection configuration
        is_left: If True, only left lane detected; if False, only right;
                 if None, auto-detect which lane is present
    
    Returns:
        Binary lane mask or None
    """
    if config is None:
        config = LaneDetectionConfig()
    
    height, width = frame.shape[:2]
    
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (5, 5), 0)
    edges = cv2.Canny(blur, config.canny_low, config.canny_high)
    
    default_mask = np.zeros_like(edges)
    polygon = np.array([
        [
            (int(width * 0.08), height),
            (int(width * 0.46), int(height * 0.58)),
            (int(width * 0.54), int(height * 0.58)),
            (int(width * 0.92), height),
        ]
    ], dtype=np.int32)
    cv2.fillPoly(default_mask, polygon, 255)
    masked = cv2.bitwise_and(edges, default_mask)
    
    lines = cv2.HoughLinesP(
        masked,
        1,
        np.pi / 180,
        threshold=config.hough_threshold,
        minLineLength=config.hough_min_line_length,
        maxLineGap=config.hough_max_line_gap
    )
    
    if lines is None:
        return None
    
    left_lines, right_lines = classify_lines(lines, config)
    
    lane_width_pixels = int(width * 0.35)
    
    mask = np.zeros((height, width), dtype=np.uint8)
    
    if left_lines and not right_lines:
        left = fit_lane_line(left_lines, height)
        if left is not None:
            right_estimated = (
                (left[0][0] + lane_width_pixels, left[0][1]),
                (left[1][0] + lane_width_pixels, left[1][1])
            )
            polygon = np.array([
                [left[0][0], left[0][1]],
                [left[1][0], left[1][1]],
                [right_estimated[1][0], right_estimated[1][1]],
                [right_estimated[0][0], right_estimated[0][1]],
            ], dtype=np.int32)
            cv2.fillPoly(mask, [polygon], 255)
    
    elif right_lines and not left_lines:
        right = fit_lane_line(right_lines, height)
        if right is not None:
            left_estimated = (
                (right[0][0] - lane_width_pixels, right[0][1]),
                (right[1][0] - lane_width_pixels, right[1][1])
            )
            polygon = np.array([
                [left_estimated[0][0], left_estimated[0][1]],
                [left_estimated[1][0], left_estimated[1][1]],
                [right[1][0], right[1][1]],
                [right[0][0], right[0][1]],
            ], dtype=np.int32)
            cv2.fillPoly(mask, [polygon], 255)
    
    else:
        return None
    
    return apply_morphological_cleanup(mask, config.morph_kernel_size, operation="both")


def process_frame_with_fallback(
    frame: np.ndarray,
    config: Optional[LaneDetectionConfig] = None,
    temporal_smoother: Optional[TemporalSmoother] = None,
    night_mode: bool = False,
    curve_detection: bool = True,
    polygon_fill: bool = True
) -> Optional[np.ndarray]:
    """
    Process frame with fallback to single-lane detection.
    
    Args:
        frame: Input BGR image
        config: Detection configuration
        temporal_smoother: For video smoothing
        night_mode: Force night mode
        curve_detection: Enable curve detection
        polygon_fill: Fill polygon between lanes
    
    Returns:
        Lane mask or None
    """
    mask = build_lane_mask_v2(
        frame,
        config=config,
        temporal_smoother=temporal_smoother,
        night_mode=night_mode,
        curve_detection=curve_detection,
        polygon_fill=polygon_fill
    )
    
    if mask is not None:
        return mask
    
    return build_lane_mask_single_lane(frame, config)
