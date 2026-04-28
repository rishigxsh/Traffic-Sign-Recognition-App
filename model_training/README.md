# Traffic Sign Recognition — Model Training

This directory contains all notebooks, configs, and assets used to train the traffic sign detection and classification models. The system uses a two-tier pipeline: a **YOLOv8 object detector** locates signs in a frame, then a **MobileNetV3 cascade classifier** refines the prediction for visually ambiguous sign categories.

---

## Table of Contents

1. [System Architecture Overview](#system-architecture-overview)
2. [Datasets](#datasets)
3. [Dataset Filtering and Preprocessing](#dataset-filtering-and-preprocessing)
4. [Class Selection](#class-selection)
5. [Data Augmentation](#data-augmentation)
6. [YOLO Detectors](#yolo-detectors)
7. [MobileNet Cascade Classifiers](#mobilenet-cascade-classifiers)
8. [Two-Tier Cascade Architecture](#two-tier-cascade-architecture)
9. [Training Results](#training-results)
10. [Speed Performance](#speed-performance)
11. [Model Export](#model-export)

---

## System Architecture Overview

**For newcomers to ML:** Think of the pipeline as a two-step process. The first step (the detector) acts like a pair of eyes scanning the road — it draws boxes around anything that looks like a traffic sign and makes an initial guess about what each sign is. The second step (the classifier) acts like a second opinion — for categories where the detector often gets confused (like telling apart a 25 mph vs 35 mph sign), a dedicated expert model gets a close-up look at the cropped sign and makes a more accurate decision.

```
Input Frame
    │
    ▼
┌─────────────────────────────┐
│  YOLOv8s Detector           │  Locates signs and assigns an initial label
│  (US or EU variant)         │
└────────────┬────────────────┘
             │  detected sign crops
             ▼
    ┌────────────────────┐
    │  Routing Logic     │  Is this a speed limit? A warning? A regulatory sign?
    └──────┬─────────────┘
           │
    ┌──────┴──────────────────────────────────────────────┐
    │              │                                       │
    ▼              ▼                                       ▼
Speed Limit    Warning                             Regulatory
Classifier     Classifier                          Classifier
(8 classes)    (22 classes)                        (19 classes)
    │              │                                       │
    └──────┬───────┘                                       │
           └───────────────────────────────────────────────┘
                             │
                     Final Label Output
```

If the classifier is not confident enough (confidence < 0.30), or predicts `other` (a sign that doesn't belong to the taxonomy), the pipeline falls back to the detector's original label.

---

## Datasets

Training data comes from two sources combined into a single unified dataset.

### Mapillary Traffic Sign Dataset v2 (MTSD)

Mapillary is a large-scale dataset of real street-level photographs collected worldwide by contributors using dashcams and smartphones. Version 2 contains images annotated across **401 traffic sign classes** from many countries, giving the model exposure to highly varied real-world lighting, weather, and road conditions.

- **Source:** Facebook / Mapillary (requires dataset access agreement)
- **Coverage:** Worldwide, with heavy US and European content
- **Annotation format:** JSON per image with bounding boxes and class labels
- **Key characteristic:** Very diverse imagery but also includes noisy panoramic shots, heavily occluded signs, and low-visibility examples that require filtering before use

### LISA (Laboratory for Intelligent and Safe Automobiles)

LISA is a US-specific dataset captured from dashcam footage, focused on driving scenarios relevant to American roads. It was sourced via the Roboflow platform in YOLO format.

- **Coverage:** US signs only, ~40 classes
- **Key characteristic:** Temporal sequences — consecutive frames of the same scene recorded 1-2 seconds apart
- **Split handling:** The Roboflow export was verified to keep each temporal sequence entirely within one split (train, val, or test). This is important to prevent **data leakage**, where the model effectively "sees" a test image during training because a nearly identical frame appeared in the training set.

LISA class labels were manually mapped to their nearest Mapillary equivalent so both datasets share a single unified taxonomy.

---

## Dataset Filtering and Preprocessing

Raw Mapillary data contains a lot of images that would hurt model quality if included as-is. Several filters were applied before training.

### Panoramic Image Removal

**What it is:** Mapillary contains 360° panoramic images captured with fisheye lenses. These images have heavy geometric distortion — straight lines appear curved, and the perspective is completely unlike a forward-facing dashcam.

**Why it matters:** Training on fisheye-distorted images teaches the model patterns that will never appear in real dashcam footage, hurting performance without any benefit.

**How it was done:** Each Mapillary annotation file includes an `ispano` flag. Any image with `ispano=true` was excluded entirely.

### Occluded and Invalid Annotation Removal

Mapillary annotators mark individual bounding boxes with quality flags. Annotations marked `occluded`, `ambiguous`, `out-of-frame`, or `dummy` were dropped from training. Keeping heavily occluded or ambiguous labels teaches the model to recognize partial or unclear signs — adding noise rather than signal.

### Small Annotation Filtering

**What it is:** Annotations where the bounding box is smaller than a minimum pixel threshold in the original image were removed.

**Why it matters:** Extremely small annotations — a sign that is only a handful of pixels — are often unrecognizable even to a human. Including them trains the model on blurry, indistinct patches that don't resemble real sign appearances.

**Thresholds used:**
| Model variant | Min annotation size |
|---------------|---------------------|
| 640px model | 32px |
| 1280px model | 20px (lower to retain more far-distance examples) |

### Split Reassignment

Mapillary withholds ground-truth labels for its official test split (used for a leaderboard competition). To work around this, the original test split was redirected to validation. A proper held-out test set was then carved out of 10% of Mapillary's training split using a fixed random seed, ensuring reproducibility.

---

## Class Selection

Mapillary's 401 classes include signs from every country in the world. For the US model, only classes representing American signs were needed. To select them, a **contact sheet review tool** was used — a grid of example image crops automatically generated for each class, allowing every class to be visually inspected and categorized:

| Label      | Meaning                                                                                                      |
| ---------- | ------------------------------------------------------------------------------------------------------------ |
| **Keep**   | US sign that belongs in the final taxonomy                                                                   |
| **Buffer** | US sign present but not a priority; excluded from detector training, used as an `other` class in classifiers |
| **Skip**   | Non-US or irrelevant sign (European, Asian, construction-specific, etc.)                                     |

After the review, similar variant classes (e.g., the same sign design with minor regional variations labeled `g2`, `g3`, `g4`) were merged into a single canonical label to consolidate training data.

### Sign Categories in the Final Taxonomy

The final US taxonomy covers three main sign families:

- **Regulatory signs** — signs that give legal instructions: stop, yield, speed limits, no-turn signs, one-way, no-entry, lane control, and others
- **Warning signs** — diamond-shaped signs alerting drivers to upcoming hazards: pedestrian crossings, school zones, curves, railroad crossings, merging lanes, traffic signals ahead, slippery roads, and others
- **Complementary signs** — supplemental signs that provide directional guidance: chevrons, keep-left/right panels, obstacle delineators

A separate EU class list was compiled through the same contact-sheet review process, stored in `mapillary_class_review_eu.csv`.

### Final Dataset Statistics (1280px version)

| Split | Images |
| ----- | ------ |
| Train | 11,384 |
| Val   | 1,652  |
| Test  | 1,312  |

**Total classes: 55 — Total annotations: 18,122**
**Mean per class: 329.5 — Median per class: 192.0**

<details>
<summary>Per-class annotation counts</summary>

| Class                                    | Total | Mapillary | LISA  |
| ---------------------------------------- | ----- | --------- | ----- |
| complementary--both-directions--g1       | 56    | 56        | 0     |
| complementary--chevron-left--g1          | 621   | 621       | 0     |
| complementary--chevron-right--g1         | 571   | 571       | 0     |
| complementary--keep-left--g1             | 126   | 126       | 0     |
| complementary--keep-right--g1            | 468   | 24        | 444   |
| complementary--obstacle-delineator--g1   | 97    | 97        | 0     |
| complementary--obstacle-delineator--g2   | 123   | 123       | 0     |
| complementary--one-direction-left--g1    | 71    | 71        | 0     |
| regulatory--keep-left--g2                | 94    | 94        | 0     |
| regulatory--keep-right--g4               | 221   | 221       | 0     |
| regulatory--lane-control--g1             | 94    | 94        | 0     |
| regulatory--left-turn-yield-on-green--g1 | 64    | 64        | 0     |
| regulatory--maximum-speed-limit-25--g2   | 701   | 114       | 587   |
| regulatory--maximum-speed-limit-30--g3   | 250   | 58        | 192   |
| regulatory--maximum-speed-limit-35--g2   | 806   | 84        | 722   |
| regulatory--maximum-speed-limit-40--g3   | 165   | 57        | 108   |
| regulatory--maximum-speed-limit-45--g3   | 231   | 47        | 184   |
| regulatory--maximum-speed-limit-55--g2   | 88    | 84        | 4     |
| regulatory--maximum-speed-limit-65--g2   | 105   | 16        | 89    |
| regulatory--no-entry--g1                 | 819   | 788       | 31    |
| regulatory--no-left-turn--g1             | 348   | 283       | 65    |
| regulatory--no-parking--g2               | 282   | 282       | 0     |
| regulatory--no-right-turn--g1            | 247   | 202       | 45    |
| regulatory--no-turn-on-red--g1           | 205   | 205       | 0     |
| regulatory--no-u-turn--g1                | 301   | 301       | 0     |
| regulatory--one-way-left--g2             | 89    | 89        | 0     |
| regulatory--one-way-right--g2            | 54    | 54        | 0     |
| regulatory--reversible-lanes--g2         | 80    | 80        | 0     |
| regulatory--stop--g1                     | 836   | 836       | 0     |
| regulatory--turn-left--g2                | 121   | 121       | 0     |
| regulatory--turn-right--g3               | 73    | 73        | 0     |
| regulatory--wrong-way--g1                | 38    | 38        | 0     |
| regulatory--yield--g1                    | 1,470 | 1,470     | 0     |
| warning--added-lane-right--g1            | 446   | 30        | 416   |
| warning--children--g2                    | 277   | 277       | 0     |
| warning--crossroads--g3                  | 97    | 97        | 0     |
| warning--curve-left--g2                  | 434   | 385       | 49    |
| warning--curve-right--g2                 | 372   | 316       | 56    |
| warning--divided-highway-ends--g1        | 148   | 148       | 0     |
| warning--double-curve-first-right--g2    | 82    | 82        | 0     |
| warning--pedestrians-crossing--g4        | 2,217 | 777       | 1,440 |
| warning--railroad-crossing--g1           | 57    | 57        | 0     |
| warning--railroad-intersection--g3       | 81    | 81        | 0     |
| warning--road-narrows-left--g2           | 89    | 89        | 0     |
| warning--road-narrows-right--g2          | 369   | 75        | 294   |
| warning--school-zone--g2                 | 341   | 165       | 176   |
| warning--slippery-road-surface--g2       | 192   | 192       | 0     |
| warning--stop-ahead--g9                  | 333   | 91        | 242   |
| warning--texts--g1                       | 143   | 143       | 0     |
| warning--texts--g2                       | 251   | 251       | 0     |
| warning--texts--g3                       | 131   | 131       | 0     |
| warning--traffic-merges-right--g1        | 525   | 160       | 365   |
| warning--traffic-signals--g3             | 1,418 | 171       | 1,247 |
| warning--turn-left--g1                   | 78    | 78        | 0     |
| warning--turn-right--g1                  | 126   | 126       | 0     |

</details>

---

## Data Augmentation

**For newcomers to ML:** Augmentation means artificially creating variations of your training images — changing the brightness, rotating slightly, cutting pieces out — so the model sees a wider variety of conditions than what the original dataset contains. This makes it generalize better to real-world inputs it has never seen before.

The following augmentations were applied during YOLO training:

| Augmentation     | Setting                             | Purpose                                                                                                        |
| ---------------- | ----------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| HSV color jitter | `hsv_h=0.015, hsv_s=0.7, hsv_v=0.4` | Simulates different lighting, weather, and camera color profiles                                               |
| Scale jitter     | `scale=0.7`                         | Simulates signs at different distances                                                                         |
| Rotation         | `degrees=5.0`                       | Simulates slight camera tilt                                                                                   |
| **Mosaic**       | `mosaic=1.0`                        | Combines 4 images into one tile, forcing the model to detect small objects in cluttered scenes                 |
| **Mixup**        | `mixup=0.1`                         | Blends two images together at a low weight, acting as a regularizer                                            |
| **Copy-paste**   | `copy_paste=0.3`                    | Copies annotated sign instances from one image and pastes them into another, increasing sign density variety   |
| Random erasing   | `erasing=0.4`                       | Randomly blacks out rectangular patches, simulating partial occlusion                                          |
| Horizontal flip  | **disabled** (`fliplr=0.0`)         | Deliberately turned off — flipping a one-way or turn sign reverses its meaning, which would corrupt the labels |
| Vertical flip    | **disabled** (`flipud=0.0`)         | Upside-down signs do not appear in real dashcam footage                                                        |

---

## YOLO Detectors

**For newcomers to ML:** YOLO (You Only Look Once) is a family of object detection models known for being fast enough to process video in real time. The detector scans an entire image in a single forward pass and outputs bounding boxes with class labels. "YOLOv8s" refers to the "small" size variant from the 8th generation of the architecture — a good balance between speed and accuracy for mobile deployment.

### Model Choice

All detectors use **YOLOv8s** (11.1M parameters, 28.6 GFLOPs) from the Ultralytics library, pretrained on COCO. The small variant was chosen over the nano variant because it performs meaningfully better on small-object detection (distant signs), while still being exportable to ONNX for Android deployment.

### Training Configuration

| Setting       | Value                   | Notes                                                    |
| ------------- | ----------------------- | -------------------------------------------------------- |
| Optimizer     | SGD                     | Empirically better detection accuracy than Adam for YOLO |
| Learning rate | 0.01                    | Cosine annealing decay schedule                          |
| Epochs        | 150                     | With patience=30 early stopping                          |
| Batch size    | 16 (640px) / 8 (1280px) |                                                          |
| Augmentation  | See table above         |                                                          |

### US Detector

Two training runs were conducted for the US model:

**Baseline — YOLOv8s @ 640px**

- Min annotation size: 32px — Batch: 16 — Stopped at epoch 91

| mAP50  | mAP50-95 | Precision | Recall |
| ------ | -------- | --------- | ------ |
| 0.6919 | 0.5242   | 0.7310    | 0.6213 |

**Experiment — YOLOv8s @ 1280px**

Motivated by poor far-distance detection in the baseline. The dataset was rebuilt with a lower annotation size threshold (20px) to retain more far-distance examples.

- Min annotation size: 20px — Batch: 8 — Stopped at epoch 51

| mAP50  | mAP50-95 | Precision | Recall |
| ------ | -------- | --------- | ------ |
| 0.8823 | 0.6928   | 0.8128    | 0.8115 |

Significant improvement across all metrics. Qualitative testing on dashcam footage confirmed notably better detection of signs at greater distances. However, the higher resolution increases inference time substantially on mobile hardware.

**Experiment — YOLOv8s @ 640px (Tiled)**

An additional experiment tiled full-resolution frames into overlapping 640×640 patches during dataset construction, recovering some of the resolution benefit of the 1280px model without the per-frame inference cost. Despite achieving the best detection metrics, this approach was not used for deployment — the tiling preprocessing makes real-time inference too slow on mid-range Android hardware.

- Min annotation size: 32px — Batch: 16 — Stopped at epoch 90

| mAP50  | mAP50-95 | Precision | Recall |
| ------ | -------- | --------- | ------ |
| 0.9404 | 0.7547   | 0.8978    | 0.8978 |

**The baseline 640px model was selected for Android deployment** as the best balance between detection quality and real-time inference performance (target: 15+ FPS on mid-range hardware).

### EU Detector

A separate detector was trained for European signs using Mapillary only — no LISA equivalent exists for European signs. The EU class list was selected through the same manual contact-sheet review process used for US signs, stored in `mapillary_class_review_eu.csv`.

- **Dataset:** `baseline_eu_v1` — Mapillary only, no LISA
- **Model:** YOLOv8s @ 640px, same training configuration as US models
- **Cascade classifiers:** Not yet implemented — planned as future work pending confusion matrix analysis

Stopped at epoch 121.

| mAP50  | mAP50-95 | Precision | Recall |
| ------ | -------- | --------- | ------ |
| 0.4811 | 0.3559   | 0.6312    | 0.4229 |

---

## MobileNet Cascade Classifiers

**For newcomers to ML:** The detector is trained to recognize all sign classes from the same image in one shot, which means it uses a relatively coarse representation. Within tight visual categories — all speed limit signs look like white circles with numbers; all warning signs are yellow diamonds — the detector sometimes confuses one variant for another. A classifier, by contrast, receives only the cropped sign region and focuses exclusively on distinguishing within that category. Because the input is smaller and the task is narrower, a lightweight model can do this very quickly.

### Model Choice

All classifiers use **MobileNetV3-Small** pretrained on ImageNet. This architecture is specifically designed for fast inference on mobile CPUs, with only 2.5M parameters. It runs at approximately 4ms per crop on a PC GPU, well within the real-time budget.

### Crop Extraction

For each annotated sign, the bounding box is expanded by **20% padding** on all sides and saved as a standalone crop image. This padding ensures the classifier sees a small amount of surrounding context (road surface, sky, neighboring signs) which can help with ambiguous cases. Crops smaller than 20px were discarded.

Crops are organized into three **superclass** directories, one per classifier:

| Classifier    | Classes | Train crops | Val crops | Test crops |
| ------------- | ------- | ----------- | --------- | ---------- |
| `speed_limit` | 8       | 2,419       | 364       | 320        |
| `warning`     | 22      | 9,209       | 1,379     | 983        |
| `regulatory`  | 19      | 7,298       | 1,163     | 854        |

Each superclass also includes an **`other`** class, populated from signs that were marked as `buffer` during the class review — signs that visually resemble kept classes but are not part of the taxonomy. This prevents similar-looking unlabeled signs from being misclassified as a known class. For example, a deer crossing sign shares the same diamond shape and figure silhouette as a pedestrian crossing sign and could otherwise be incorrectly predicted as one. When the classifier predicts `other`, the detection is dropped entirely rather than being assigned a wrong label.

### Classifier Training Configuration

| Setting      | Value                                     |
| ------------ | ----------------------------------------- |
| Architecture | MobileNetV3-Small (ImageNet pretrained)   |
| Final layer  | Replaced: 1280 → num_classes              |
| Loss         | CrossEntropyLoss with label smoothing 0.1 |
| Optimizer    | AdamW, lr=3e-4, weight_decay=1e-4         |
| LR schedule  | CosineAnnealingLR, T_max=30               |
| Epochs       | 30                                        |
| Batch size   | 64                                        |

**Label smoothing** (0.1) slightly softens the one-hot targets during training, discouraging the model from being overconfident on ambiguous training examples — useful here since some sign classes are visually very similar.

### Classifier Results (test set, crop-level accuracy)

| Classifier  | Accuracy |
| ----------- | -------- |
| regulatory  | 98.1%    |
| speed_limit | 88.1%    |
| warning     | 98.5%    |

The speed limit classifier has the lowest accuracy because speed limit signs are visually nearly identical — the only distinguishing feature is the number, and some numbers (e.g., 35 vs 45) look similar at low resolution or when partially blurred by motion.

---

## Two-Tier Cascade Architecture

**For newcomers to ML:** This section walks through the full decision process a single detected sign goes through, from raw detector output to final label.

### Routing Logic

After the detector fires on a frame, each detection is routed to the appropriate classifier based on its initial predicted label:

```
Detector prediction
        │
        ├─ contains "maximum-speed-limit"  →  speed_limit classifier
        │
        ├─ starts with "warning--"         →  warning classifier
        │
        ├─ starts with "regulatory--"      →  regulatory classifier
        │   (excluding speed limits, handled above)
        │
        └─ other                           →  use detector label directly
```

### Confidence Fallback

The classifier always outputs a class and a confidence score. Two conditions cause the pipeline to **ignore the classifier result** and use the detector's original label instead:

1. **Low confidence:** classifier confidence < 0.30
2. **`other` prediction:** the classifier determined the sign does not belong to any known class

This fallback is important — without it, a misrouted detection (e.g., a European sign appearing in a US-model feed) would be forced into a wrong label.

### End-to-End Pipeline Results (test set, `baseline_v3` detector)

| Stage              | Overall Accuracy |
| ------------------ | ---------------- |
| Detector only      | 64.6%            |
| Detector + cascade | 68.9% (+4.3%)    |

The 4.3% overall gain understates the classifier's real impact. The gain is diluted by the complementary sign classes (chevrons, delineators, etc.) which bypass all classifiers entirely. For classes that route through a classifier the improvement is typically **10–30%**.

Notable gains on specific classes:

- `warning--turn-left` **+27%**
- `warning--divided-highway-ends` **+33%**
- `regulatory--stop` **+13%**
- `regulatory--maximum-speed-limit-25` **+14%**

Known regression: `regulatory--maximum-speed-limit-45` **−30%**, caused by insufficient training examples for that class in the speed limit classifier. This is a known issue and a target for data collection.

---

## Training Results

### US Detector Summary

| Run                      | Input size | mAP50  | mAP50-95 | Precision | Recall | Status        |
| ------------------------ | ---------- | ------ | -------- | --------- | ------ | ------------- |
| baseline (640px)         | 640        | 0.6919 | 0.5242   | 0.7310    | 0.6213 | **Deployed**  |
| experiment (1280px)      | 1280       | 0.8823 | 0.6928   | 0.8128    | 0.8115 | Research only |
| experiment (640px tiled) | 640        | 0.9404 | 0.7547   | 0.8978    | 0.8978 | Research only |

### EU Detector Summary

The EU detector (`baseline_eu_v1`) was trained on Mapillary-only data at 640px with the same configuration as the US baseline.

| Run              | Input size | mAP50  | mAP50-95 | Precision | Recall | Status       |
| ---------------- | ---------- | ------ | -------- | --------- | ------ | ------------ |
| baseline (640px) | 640        | 0.4811 | 0.3559   | 0.6312    | 0.4229 | **Deployed** |

### Cascade Classifier Summary

| Classifier  | Classes | Test accuracy |
| ----------- | ------- | ------------- |
| speed_limit | 8       | 88.1%         |
| warning     | 22      | 98.5%         |
| regulatory  | 19      | 98.1%         |

---

## Speed Performance

Timing measured on PC using ONNX Runtime (GPU). Android FPS estimates are approximated based on benchmarks and assume a GPU session — actual performance depends on hardware and driver support.

| Model                | Preprocess | Inference | Postprocess | Total   | FPS (PC) | Android FPS (High-End) | Android FPS (Mid-Range) |
| -------------------- | ---------- | --------- | ----------- | ------- | -------- | ---------------------- | ----------------------- |
| YOLOv8s @ 640px      | 0.7 ms     | 2.0 ms    | 0.7 ms      | 3.4 ms  | 295      | 30–50                  | 15–30                   |
| YOLOv8s @ 1280px     | 3.1 ms     | 7.4 ms    | 0.7 ms      | 11.2 ms | 90       | 10–18                  | 5–10                    |
| YOLOv8s Tiled (SAHI) | —          | —         | —           | 52 ms   | 20       | 4–8                    | 2–4                     |

The tiled model has no per-stage breakdown because SAHI wraps multiple inference passes (one per tile) plus merge logic into a single call. The 52 ms figure covers the full tiling pipeline end-to-end.

Only the **640px model** meets the 15+ FPS target on mid-range Android hardware. The 1280px model is viable on high-end devices only, and the tiled model is below real-time threshold on all Android hardware tested.

---

## Model Export

All models are exported to **ONNX** (Open Neural Network Exchange) format for Android deployment.

### Detector Export

```
yolov8s best.pt  →  detector_us.onnx   (42.7 MB, FP32, static input shape)
                 →  detector_eu.onnx
```

Export settings: `half=False` (FP32 for compatibility), `dynamic=False` (fixed shapes for Android ONNX Runtime), `simplify=True` (graph optimization pass).

### Classifier Export

Each classifier produces two files that **must be kept together**:

- `classifier.onnx` — model graph
- `classifier.onnx.data` — weights stored as an external file by the ONNX exporter

If only the `.onnx` file is present without its `.onnx.data` sibling, the model will fail to load at runtime.

A `classifier_config.json` is generated alongside the model files. It encodes the class list for each classifier and the routing rules used to assign detections to the correct classifier at inference time.

### Android Asset Files

| File                                         | Purpose                            |
| -------------------------------------------- | ---------------------------------- |
| `detector_us.onnx`                           | US YOLOv8s detector                |
| `detector_eu.onnx`                           | EU YOLOv8s detector                |
| `speed_limit_classifier.onnx` + `.onnx.data` | Speed limit cascade classifier     |
| `warning_classifier.onnx` + `.onnx.data`     | Warning sign cascade classifier    |
| `regulatory_classifier.onnx` + `.onnx.data`  | Regulatory sign cascade classifier |
| `classifier_config.json`                     | Routing rules and class lists      |
