# Traffic Sign Recognition App

An Android application for real-time traffic sign detection built for people with disabilities.
The app uses the device camera to detect and announce traffic signs (stop, yield, speed limits, etc.)
using on-device YOLOv8 inference — no internet connection required.

---

## Demo

<table>
  <tr>
    <td align="center"><b>US Model</b></td>
    <td align="center"><b>EU Model</b></td>
  </tr>
  <tr>
    <td><img src=".github/readme_assets/us_demo.gif" width="400"/></td>
    <td><img src=".github/readme_assets/eu_demo.gif" width="400"/></td>
  </tr>
</table>

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
│       └── assets/             ← Place model files here when ready
│
├── model_training/             ← ML training notebooks & experiment results
│   ├── baseline_yolo.ipynb     ← YOLOv8 training notebook
│   ├── download_datasets.ipynb ← Dataset download (Mapillary + LISA)
│   ├── experiments/            ← Training results, confusion matrices, curves
│   └── *.ipynb / *.csv         ← Class review notebooks and data
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
4. Download all assets from https://drive.google.com/drive/folders/1FukHfx1dAQjarpm8qcw3o0uh9AlFDOXW?usp=sharing and place them in `app/src/main/assets/`
5. Connect an Android device (API 24+) and hit Run

---

## ML Pipeline

The app uses a two-tier pipeline: a **YOLOv8s detector** locates signs in the camera frame, then lightweight **MobileNetV3 cascade classifiers** refine predictions for visually similar sign categories (e.g. distinguishing 25 mph from 35 mph). Separate models are trained for US and EU signs, using the Mapillary Traffic Sign Dataset v2 combined with LISA traffic sign dataset. All models are exported to ONNX for on-device inference.

For full details on training methodology, datasets, model selection, and performance benchmarks, see [model_training/README.md](model_training/README.md).

### Running the ML Code

All training and data preparation code lives in `model_training/` as Jupyter notebooks. Notebooks are run directly in VS Code using the [Jupyter extension](https://marketplace.visualstudio.com/items?itemName=ms-toolsai.jupyter). To set up the environment:

**Prerequisites**

- Python 3.10+
- VS Code with the [Jupyter extension](https://marketplace.visualstudio.com/items?itemName=ms-toolsai.jupyter) installed
- A CUDA-capable GPU is strongly recommended for training.

**Install PyTorch**

PyTorch is not included in `requirements.txt` because the correct build depends on your CUDA version. Use the [PyTorch installation selector](https://pytorch.org/get-started/locally/) to generate the right install command for your system, then run it before the next step.

For CPU

```bash
pip3 install torch torchvision
```

For GPU

```bash
pip3 install torch torchvision --index-url https://download.pytorch.org/whl/{cuda_version}
```

**Install remaining dependencies**

```bash
pip install -r model_training/requirements.txt
```

**Notebook overview**

| Notebook                         | Purpose                                                  |
| -------------------------------- | -------------------------------------------------------- |
| `download_datasets.ipynb`        | Download and extract Mapillary MTSD v2 and LISA datasets |
| `mapillary_classes_review.ipynb` | Review and label Mapillary classes for the US taxonomy   |
| `baseline_yolo.ipynb`            | Train the US YOLOv8s detector                            |
| `baseline_eu_yolo.ipynb`         | Train the EU YOLOv8s detector                            |
| `cascade_classifier.ipynb`       | Train the MobileNetV3 cascade classifiers                |
| `test_videos.ipynb`              | Run inference on video files and review results          |

Run `download_datasets.ipynb` first to populate the dataset before running any training notebooks. **Mapillary is extremely large — expect to need ~100 GB of free disk space.**

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

| Layer               | Technology                                             |
| ------------------- | ------------------------------------------------------ |
| Android UI          | Kotlin, CameraX, MVVM, LiveData                        |
| On-device inference | ONNX Runtime for Android 1.16.3                        |
| Native utilities    | C++ (Tensor class, Logger, JNI)                        |
| Model               | YOLOv8 (up to 55 US traffic sign classes), ONNX format |

---

## Requirements

- **Android**: API 24+ (Android 7.0), physical device with camera recommended
- **NDK**: 25.2.9519653 (install via Android Studio SDK Manager)
