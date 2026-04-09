# Snap Mark

An Android app that captures screenshots via a floating button and automatically watermarks them with the source app name and timestamp.

## Core Concept

**Floating button = screen capture permission granted. Tap = screenshot.**

- Open the app → grant overlay, usage stats, and screen capture permissions
- Once screen capture is authorized, a floating button appears on screen
- Tap the floating button anytime to take a screenshot — no extra dialogs
- Each screenshot is automatically watermarked with the source app name and timestamp
- Screenshots are saved to `Pictures/SnapMark/<date>/`
- If the screen capture permission is revoked, the floating button disappears automatically

## Features

- One-tap screenshot via floating button overlay
- Auto-detect foreground app name (e.g., "WeChat", "Settings")
- Watermark format: `Source: <app name> | <date time>` (displayed in Chinese)
- Screenshots organized by date in `Pictures/SnapMark/`
- Floating button is draggable and stays on top of all apps
- Floating button is hidden during capture so it doesn't appear in screenshots

## Permissions

| Permission | Purpose |
|---|---|
| Overlay (SYSTEM_ALERT_WINDOW) | Display floating button on top of other apps |
| Usage Stats (PACKAGE_USAGE_STATS) | Detect foreground app name for watermark |
| Screen Capture (MediaProjection) | Capture screenshot content |
| Storage (API 26-28 only) | Save screenshots to Pictures directory |

## Build

```bash
JAVA_HOME=/path/to/jdk17 ./gradlew build
```

Requires Android SDK and JDK 17. Min SDK: 26 (Android 8.0), Target SDK: 34 (Android 14).
