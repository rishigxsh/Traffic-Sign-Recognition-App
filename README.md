# Traffic Sign Recognition App

An Android application for real-time traffic sign detection built for people with disabilities.
The app uses the device camera to detect and announce traffic signs (stop, yield, speed limits, etc.)
using on-device YOLOv8 inference — no internet connection required.

---

## Project Structure

```
TrafficSignRecognitionApp/
│
├── app/                        ← FRONTEND (Android App)
│   └── src/main/
│       ├── java/               ← Kotlin source code
│       ├── cpp/                ← C++ native tensor/logging utilities
│       ├── res/                ← UI layouts, colors, themes
│       └── assets/             ← Place model.onnx here when ready
│
├── model/                      ← BACKEND (Python ML Pipeline)
│   ├── train.py                ← Train YOLOv8 on GTSRB dataset
│   ├── inference.py            ← Run inference on test images
│   ├── test.py                 ← Evaluate model (accuracy, IoU, confusion matrix)
│   ├── prepare_dataset.py      ← Download + convert GTSRB dataset to YOLO format
│   ├── export_to_onnx.py       ← Export trained model to ONNX for Android
│   └── utils/                  ← Dataset helpers (download, conversion, YAML gen)
│
├── requirements.txt            ← BACKEND — Python dependencies (pinned versions)
├── setup.py                    ← BACKEND — Auto-creates Python venv + installs deps
├── build.gradle.kts            ← FRONTEND — Root Gradle build file
└── settings.gradle.kts         ← FRONTEND — Gradle project settings
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

## Backend (Python ML Pipeline)

Trains a YOLOv8 nano model on the GTSRB dataset (43 German traffic sign classes).

**Setup:**
```bash
python3.11 setup.py          # Creates venv + installs all dependencies
source venv/bin/activate     # Mac/Linux
# OR
.\venv\Scripts\activate      # Windows
```

**Run the full pipeline:**
```bash
# 1. Download and prepare the GTSRB dataset
python model/prepare_dataset.py

# 2. Train the YOLOv8 model (50 epochs, resumable)
python model/train.py

# 3. Evaluate the trained model
python model/test.py

# 4. Export to ONNX for Android
python model/export_to_onnx.py
```

**After export**, copy `exports/model.onnx` → `app/src/main/assets/model.onnx`

---

## How Frontend + Backend Connect

```
Python training pipeline
        ↓
  exports model.onnx
        ↓
  placed in assets/
        ↓
OnnxInferenceEngine.kt loads model
        ↓
Camera frame → preprocess → YOLOv8 inference → NMS → TrafficSign list
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
| Model training | Python, YOLOv8 (Ultralytics), PyTorch |
| Dataset | GTSRB — 43 traffic sign classes |
| Model format | ONNX (exported from YOLOv8) |

---

## Requirements

- **Android**: API 24+ (Android 7.0), physical device with camera recommended
- **Python**: 3.9 – 3.12
- **NDK**: 25.2.9519653 (install via Android Studio SDK Manager)
