# Test the trained model using results.csv

import argparse
import pandas as pd
import numpy as np
from sklearn.metrics import confusion_matrix, accuracy_score, ConfusionMatrixDisplay
import matplotlib.pyplot as plt


def compute_iou(box_pred, box_true):
    xA = max(box_pred[0], box_true[0])
    yA = max(box_pred[1], box_true[1])
    xB = min(box_pred[2], box_true[2])
    yB = min(box_pred[3], box_true[3])

    inter = max(0, xB - xA) * max(0, yB - yA)
    pred_area = (box_pred[2] - box_pred[0]) * (box_pred[3] - box_pred[1])
    true_area = (box_true[2] - box_true[0]) * (box_true[3] - box_true[1])
    union = pred_area + true_area - inter + 1e-6
    return inter / union


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--pred", default="results.csv")
    parser.add_argument("--gt", default="dataset/GTSRB/GT-final_test.csv")
    args = parser.parse_args()

    preds = pd.read_csv(args.pred)
    preds["Filename"] = preds["Filename"].str.replace(".png", ".ppm")

    gt = pd.read_csv(args.gt, sep=";")
    merged = preds.merge(gt, on="Filename")

    ious = []
    tc, pc = [], []
    TP = FP = FN = 0

    for _, row in merged.iterrows():
        iou = compute_iou(
            (row["x1"], row["y1"], row["x2"], row["y2"]),
            (row["Roi.X1"], row["Roi.Y1"], row["Roi.X2"], row["Roi.Y2"])
        )
        ious.append(iou)

        pred_cls = row["Label"]
        true_cls = row["ClassId"]
        tc.append(true_cls)
        pc.append(pred_cls)

        if iou >= 0.5:
            if pred_cls == true_cls: TP += 1
            else: FP += 1
        else:
            FN += 1

    print(f"Accuracy: {accuracy_score(tc, pc)*100:.2f}%")
    print(f"Mean IoU: {np.mean(ious):.3f}")
    print(f"TP={TP}, FP={FP}, FN={FN}")

    cm = confusion_matrix(tc, pc)
    ConfusionMatrixDisplay(cm).plot(cmap="viridis")
    plt.show()


if __name__ == "__main__":
    main()
