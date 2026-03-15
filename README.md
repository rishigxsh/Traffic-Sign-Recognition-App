# Traffic Sign Recognition App

An Android application for real-time traffic sign detection built for people with disabilities.
The app uses the device camera to detect and announce traffic signs (stop, yield, speed limits, etc.)
using on-device YOLOv8 inference — no internet connection required.

---

## Project Structure

```
TrafficSignRecognitionApp/
│
├── app/                        ← Android App
│   └── src/main/
│       ├── java/               ← Kotlin source code
│       ├── cpp/                ← C++ native tensor/logging utilities
│       ├── res/                ← UI layouts, colors, themes
│       └── assets/             ← Place model.onnx here when ready
│
├── build.gradle.kts            ← Root Gradle build file
└── settings.gradle.kts         ← Gradle project settings
```

---

## Frontend (Android App)

Built with Kotlin + CameraX + ONNX Runtime for Android.

**Key files:**
| File | Description |
|------|-------------|
| `app/src/main/java/.../ml/OnnxInferenceEngine.kt` | Loads `model.onnx`, runs YOLOv8 inference, applies NMS |
| `app/src/main/java/.../data/repository/TSRRepository.kt` | Calls inference engine, returns detected signs |
| `app/src/main/java/.../ui/main/MainActivity.kt` | Camera feed, detection overlay, TTS announcements |
| `app/src/main/java/.../ui/main/MainViewModel.kt` | MVVM ViewModel, processes camera frames |
| `app/src/main/java/.../ui/main/DetectionOverlayView.kt` | Draws bounding boxes and labels on screen |
| `app/src/main/java/.../util/SettingsManager.kt` | User settings (confidence threshold, TTS, region) |
| `app/src/main/assets/model.onnx` | **Place exported model here** (not in repo — too large) |

**To run the app:**
1. Open this folder in Android Studio
2. Let Gradle sync complete
3. Install NDK if prompted: SDK Manager → SDK Tools → NDK (Side by side)
4. Place `model.onnx` in `app/src/main/assets/` (see Backend section to generate it)
5. Connect an Android device (API 24+) and hit Run

---

## Backend (ML Pipeline)

ML training pipeline is maintained separately. The exported `model.onnx` file should be placed in `app/src/main/assets/`.

---

## How It Works

```
model.onnx loaded by OnnxInferenceEngine.kt
        ↓
Camera frame → letterbox preprocess → YOLOv8 inference → NMS → TrafficSign list
        ↓
DetectionOverlayView draws boxes + TTS speaks sign name
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Android UI | Kotlin, CameraX, MVVM, LiveData |
| On-device inference | ONNX Runtime for Android 1.16.3 |
| Native utilities | C++ (Tensor class, Logger, JNI) |
| Model | YOLOv8 (39 US traffic sign classes), ONNX format |

---

## Requirements

- **Android**: API 24+ (Android 7.0), physical device with camera recommended
- **NDK**: 25.2.9519653 (install via Android Studio SDK Manager)
