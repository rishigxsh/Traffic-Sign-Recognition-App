# Runs inference on dataset/images/test/ and saves to results.csv

import os
import csv
from ultralytics import YOLO

def main():
    model = YOLO("runs/traffic_sign_yolo/exp1/weights/best.pt")

    image_dir = "dataset/images/test"
    image_files = sorted([f for f in os.listdir(image_dir) if f.endswith(".png")])

    results = model.predict(source=image_dir, save=False, verbose=False)

    with open("results.csv", "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["Filename", "x1", "y1", "x2", "y2", "Label", "Confidence"])

        for file, res in zip(image_files, results):
            if len(res.boxes) == 0:
                writer.writerow([file, 0, 0, 0, 0, -1, 0.0])
                continue

            for box in res.boxes:
                x1, y1, x2, y2 = box.xyxy[0].tolist()
                cls_id = int(box.cls[0])
                conf = float(box.conf[0])
                writer.writerow([file, x1, y1, x2, y2, cls_id, conf])

    print("Saved results.csv")


if __name__ == "__main__":
    main()
