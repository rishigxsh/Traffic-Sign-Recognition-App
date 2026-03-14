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
- **Buffer** — US sign present but not a priority class (excluded from baseline)
- **Skip** — non-US or irrelevant

After review, merging of low-count variant classes, and dropping classes below viable annotation counts, the final combined dataset contains:

---

## Final Dataset Statistics (1280px version)

| Split | Images |
| ----- | ------ |
| Train | 11,384 |
| Val   | 1,652  |
| Test  | 1,312  |

**Total classes: 55**
**Total annotations: 18,122**
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

Both models use **YOLOv8s** (small variant, 11.1M parameters, 28.6 GFLOPs) from the Ultralytics library, pretrained on COCO. YOLOv8s was chosen over the nano variant for better small-object detection while remaining exportable to TFLite for Android deployment.

### Training Configuration

Both runs used the following shared settings:

- **Optimizer:** SGD - better for object detection accuracy although slower than Adam
- **Augmentation:** HSV jitter, scale=0.5, mosaic=1.0, mixup=0.1, copy_paste=0.1
- **`fliplr=0.0`** — horizontal flipping disabled to prevent directional sign classes (no-left-turn, one-way, etc.) from being mirrored into incorrect labels
- **Epochs:** 100 with early stopping (patience=20)

---

## Training Runs

### Baseline — YOLOv8s @ 640px

- **Min annotation size:** 32px
- **Batch size:** 16
- **Early stopped at:** epoch 91
- **Training time:** ~3 hours

| Metric    | Score  |
| --------- | ------ |
| mAP50     | 0.6816 |
| mAP50-95  | 0.5318 |
| Precision | 0.7162 |
| Recall    | 0.6132 |

### Experiment 1 — YOLOv8s @ 1280px

Motivated by poor far-distance detection in the baseline. The dataset was rebuilt with a lower annotation size threshold (20px) to retain more far-distance examples that were previously discarded.

- **Min annotation size:** 20px (lowered from 32px)
- **Batch size:** 8 (reduced to fit VRAM at 1280px)
- **Dataset:** rebuilt with lower annotation threshold into `combined_dataset_1280`

| Metric    | Score  |
| --------- | ------ |
| mAP50     | 0.8805 |
| mAP50-95  | 0.7062 |
| Precision | 0.8296 |
| Recall    | 0.8248 |

The 1280px model represents a significant improvement across all metrics. Qualitative testing on dashcam footage confirmed improved detection of signs at greater distances and better classification of visually similar signs compared to the 640px baseline.

---

## Next Steps

The current 1280px model is the detector component of a planned two-stage cascade architecture. The next phase involves training lightweight image classifiers (MobileNetV3 or EfficientNet-B0) on cropped sign patches per superclass (speed limit, warning, regulatory) to improve fine-grained classification. The cascade will be evaluated against the single-stage 1280px baseline to quantify the improvement in classification accuracy.
