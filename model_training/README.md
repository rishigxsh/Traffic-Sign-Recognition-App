# Traffic Sign Recognition — Model Training Summary

## Dataset

The combined training dataset was built from two sources: **Mapillary Traffic Sign Dataset v2** (MTSD) and **LISA** (sourced via Roboflow). Mapillary provided geographically diverse, worldwide street-level imagery with detailed annotations across 401 classes. LISA provided US-focused dashcam footage with 40 classes. The two datasets were merged into a unified YOLO-format dataset with a shared class taxonomy, with LISA labels manually mapped to their nearest Mapillary equivalent.

### Dataset Filtering (Mapillary)

Several filters were applied to Mapillary before training to improve data quality:

- **Panoramic images removed** — Mapillary contains 360° panoramic images with heavy fisheye distortion that don't resemble dashcam footage. These were excluded entirely using the `ispano` flag in each annotation file.
- **Occluded annotations removed** — annotations marked `occluded`, `ambiguous`, `out-of-frame`, or `dummy` in the properties block were dropped.
- **Small annotations removed** — annotations where the bounding box was smaller than a minimum pixel threshold in the original image were dropped. For the 640px model this threshold was 32px; for the 1280px model it was lowered to 20px to retain more far-distance examples.
- **Mapillary test split redirected** — Mapillary withholds test split labels (competition format), so the test split was redirected to val. A proper held-out test set was carved from 10% of Mapillary's train split using a fixed random seed.

### Split Handling (LISA)

LISA contains temporal sequences — consecutive frames of the same scene taken 1-2 seconds apart. The Roboflow export was verified to have split these sequences correctly so no sequence spans across train/val/test, preventing data leakage.

### Class Selection

401 Mapillary classes were manually reviewed using a contact sheet tool (grid of example crops per class). Each class was marked as:

- **Keep** — US sign included in the final class list
- **Buffer** — US sign present but not a priority class (excluded from baseline, used as `other` in cascade classifiers)
- **Skip** — non-US or irrelevant

After review, merging of low-count variant classes, and dropping classes below viable annotation counts, the final combined dataset contains:

---

## Final Dataset Statistics (1280px version)

| Split | Images |
| ----- | ------ |
| Train | 11,384 |
| Val   | 1,652  |
| Test  | 1,312  |

**Total classes: 55 — Total annotations: 18,122**
**Mean per class: 329.5 — Median per class: 192.0**

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

---

## Model Architecture

All detector models use **YOLOv8s** (11.1M parameters, 28.6 GFLOPs) from the Ultralytics library, pretrained on COCO. YOLOv8s was chosen over the nano variant for better small-object detection while remaining exportable to TFLite for Android deployment. Cascade classifiers use **MobileNetV3-Small** pretrained on ImageNet.

### Detector Training Configuration

| Setting      | Value                                                          | Notes                                                     |
| ------------ | -------------------------------------------------------------- | --------------------------------------------------------- |
| Optimizer    | SGD                                                            | Better detection accuracy than Adam                       |
| lr0          | 0.01                                                           | Cosine decay schedule                                     |
| Augmentation | HSV jitter, mosaic=1.0, mixup=0.1, copy_paste=0.3, erasing=0.4 |                                                           |
| `fliplr`     | 0.0                                                            | Disabled — prevents directional signs from being mirrored |
| `flipud`     | 0.0                                                            | Disabled                                                  |
| Epochs       | 150                                                            | Patience=30 early stopping                                |

---

## US Detector Training Runs

### Baseline — YOLOv8s @ 640px

- **Min annotation size:** 32px — **Batch:** 16 — **Stopped at:** epoch 91

| mAP50 | mAP50-95 | Precision | Recall |
| ----- | -------- | --------- | ------ |
| 0.682 | 0.532    | 0.716     | 0.613  |

### Experiment 1 — YOLOv8s @ 1280px

Motivated by poor far-distance detection in the baseline. Dataset rebuilt with a lower annotation size threshold (20px) to retain more far-distance examples.

- **Min annotation size:** 20px — **Batch:** 8 — **Dataset:** `combined_dataset_1280`

| mAP50 | mAP50-95 | Precision | Recall |
| ----- | -------- | --------- | ------ |
| 0.881 | 0.706    | 0.830     | 0.825  |

Significant improvement across all metrics. Qualitative testing on dashcam footage confirmed improved detection of signs at greater distances.

### Deployed Model — baseline_v3

The model deployed to Android is `baseline_v3`, trained at 640px on `combined_dataset_tiled`. Chosen over the 1280px variant to meet the real-time inference requirement on mid-range Android hardware (target: 15+ FPS).

| mAP50 | mAP50-95 | Precision | Recall |
| ----- | -------- | --------- | ------ |
| 0.646 | —        | —         | —      |

---

## US Cascade Classifiers

The detector alone struggles to distinguish visually similar signs within the same category — most notably speed limit variants (25/35/45 mph) and warning signs with similar diamond shapes. A cascade classifier stage was added to refine detector predictions for three superclasses.

### Architecture

**MobileNetV3-Small** — 2.5M parameters, ~4ms per crop on PC GPU. Designed for fast mobile inference and TFLite deployment on Android.

For each detection the detector label is used to route to the appropriate classifier:

```
"maximum-speed-limit" in label   →  speed_limit classifier
label starts with "warning--"    →  warning classifier
label starts with "regulatory--" →  regulatory classifier
other                             →  use detector label directly
```

If classifier confidence is below the threshold (0.30), or the prediction is `other`, the pipeline falls back to the detector label. The `other` class is populated from buffer-labeled classes in the review CSVs — signs that resemble kept classes but are not in the taxonomy.

### Classifier Datasets

Crops extracted from Mapillary and LISA with 20% bounding box padding. Class merges from the dataset builder are applied so variant labels (g2, g3) resolve to their canonical class folder.

| Classifier  | Classes | Train crops | Val crops | Test crops |
| ----------- | ------- | ----------- | --------- | ---------- |
| regulatory  | 19      | 7,298       | 1,163     | 854        |
| speed_limit | 8       | 2,419       | 364       | 320        |
| warning     | 22      | 9,209       | 1,379     | 983        |

### Classifier Results (test set, crop-level accuracy)

| Classifier  | Accuracy |
| ----------- | -------- |
| regulatory  | 98.1%    |
| speed_limit | 88.1%    |
| warning     | 98.5%    |

### End-to-End Pipeline Results (test set, baseline_v3 detector)

| Stage              | Overall Accuracy |
| ------------------ | ---------------- |
| Detector only      | 64.6%            |
| Detector + cascade | 68.9% (+4.3%)    |

The 4.3% overall gain understates the classifier's impact — it is diluted by complementary sign classes which have no classifier. For classes that route through a classifier the improvement is typically 10–30%. Notable gains: `warning--turn-left` +27%, `warning--divided-highway-ends` +33%, `regulatory--stop` +13%, `regulatory--maximum-speed-limit-25` +14%. One known regression: `regulatory--maximum-speed-limit-45` -30%, caused by limited training examples for that class.

---

## EU Detector

A separate detector was trained for EU signs using Mapillary only (no LISA equivalent exists for European signs). The EU class list was selected through the same manual contact-sheet review process used for US signs, stored in `mapillary_class_review_eu.csv`.

- **Dataset:** `baseline_eu_v1` — Mapillary only, no LISA
- **Model:** YOLOv8s @ 640px, same training config as US models
- **No cascade classifiers** — planned as future work pending confusion matrix analysis

EU detector results will be populated after training completes.

---

## Exported Models

All models are exported to ONNX for Android deployment. Classifier models produce a paired `.onnx` + `.onnx.data` file (weights stored externally by the ONNX exporter). Both files must be bundled together — the `.onnx` file alone will fail to load without the matching `.onnx.data` present in the same directory.

| File                                         | Purpose                        |
| -------------------------------------------- | ------------------------------ |
| `detector_us.onnx`                           | US YOLOv8s detector            |
| `detector_eu.onnx`                           | EU YOLOv8s detector            |
| `speed_limit_classifier.onnx` + `.onnx.data` | Speed limit cascade classifier |
| `warning_classifier.onnx` + `.onnx.data`     | Warning cascade classifier     |
| `regulatory_classifier.onnx` + `.onnx.data`  | Regulatory cascade classifier  |
