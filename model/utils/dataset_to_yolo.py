import os
import random
import pandas as pd
from PIL import Image
from utils.file_ops import ensure_dir
from utils.gtsrb_metadata import NUM_CLASSES

def convert_train_annotations(base_dir, out_img_dir, out_lbl_dir):
    """
    Converts all train images from PPM to PNG, writes YOLO labels,
    and saves them in out_img_dir and out_lbl_dir.
    """
    ensure_dir(out_img_dir)
    ensure_dir(out_lbl_dir)

    print("Converting TRAIN bounding boxes to YOLO format...")

    all_files = []

    for class_id in range(NUM_CLASSES):
        class_dir = os.path.join(base_dir, f"{class_id:05d}")
        csv_file = os.path.join(class_dir, f"GT-{class_id:05d}.csv")
        df = pd.read_csv(csv_file, sep=";")

        for _, row in df.iterrows():
            ppm_path = os.path.join(class_dir, row['Filename'])
            # Prefix filename with class_id to make it unique
            png_name = f"{class_id:05d}_{row['Filename'].replace('.ppm', '.png')}"
            all_files.append({
                "ppm_path": ppm_path,
                "png_name": png_name,
                "class_id": class_id,
                "bbox": (row["Roi.X1"], row["Roi.Y1"], row["Roi.X2"], row["Roi.Y2"])
            })

    # Shuffle the list for splitting (seeded for reproducibility)
    random.seed(42)
    random.shuffle(all_files)

    # Save all images and labels to the train folder initially
    for file in all_files:
        with Image.open(file["ppm_path"]) as img:
            w, h = img.size
            img.save(os.path.join(out_img_dir, file["png_name"]), format="PNG")

        x1, y1, x2, y2 = file["bbox"]
        x_center = ((x1 + x2) / 2) / w
        y_center = ((y1 + y2) / 2) / h
        bw = (x2 - x1) / w
        bh = (y2 - y1) / h
        label_path = os.path.join(out_lbl_dir, file["png_name"].replace(".png", ".txt"))
        with open(label_path, "w") as f:
            f.write(f"{file['class_id']} {x_center} {y_center} {bw} {bh}\n")

    print(f"TRAIN conversion complete! Total train images: {len(all_files)}")
    return all_files  # return list of all images for splitting


def create_val_split(all_files, train_img_dir, train_lbl_dir, val_img_dir, val_lbl_dir, val_ratio=0.1):
    """
    Moves a fraction of images from train folders to validation folders.
    """
    ensure_dir(val_img_dir)
    ensure_dir(val_lbl_dir)

    random.seed(42)
    random.shuffle(all_files)
    n_val = int(len(all_files) * val_ratio)
    val_files = all_files[:n_val]

    for file in val_files:
        # move image
        src_img = os.path.join(train_img_dir, file["png_name"])
        dst_img = os.path.join(val_img_dir, file["png_name"])
        os.rename(src_img, dst_img)

        # move label
        src_lbl = os.path.join(train_lbl_dir, file["png_name"].replace(".png", ".txt"))
        dst_lbl = os.path.join(val_lbl_dir, file["png_name"].replace(".png", ".txt"))
        os.rename(src_lbl, dst_lbl)

    print(f"Validation split created: {len(val_files)} images")
    print(f"Remaining train images: {len(all_files) - len(val_files)}")
    print(f"Validation images: {len(val_files)}")

    # Print file counts in each folder
    print(f"Files in train folder: {len(os.listdir(train_img_dir))}")
    print(f"Files in val folder: {len(os.listdir(val_img_dir))}")


def convert_test_annotations(csv_path, img_dir, out_img_dir, out_lbl_dir):
    ensure_dir(out_img_dir)
    ensure_dir(out_lbl_dir)

    print("Converting TEST bounding boxes to YOLO format...")

    df = pd.read_csv(csv_path, sep=";")
    for _, row in df.iterrows():
        filename_ppm = row["Filename"]
        filename_png = filename_ppm.replace(".ppm", ".png")

        img_path = os.path.join(img_dir, filename_ppm)
        img = Image.open(img_path)
        img.save(os.path.join(out_img_dir, filename_png), format="PNG")

        class_id = int(row["ClassId"])
        w, h = float(row["Width"]), float(row["Height"])
        x1, y1, x2, y2 = float(row["Roi.X1"]), float(row["Roi.Y1"]), float(row["Roi.X2"]), float(row["Roi.Y2"])
        x_center = ((x1 + x2) / 2) / w
        y_center = ((y1 + y2) / 2) / h
        bw = (x2 - x1) / w
        bh = (y2 - y1) / h
        label_path = os.path.join(out_lbl_dir, filename_png.replace(".png", ".txt"))
        with open(label_path, "w") as f:
            f.write(f"{class_id} {x_center} {y_center} {bw} {bh}\n")

    print("TEST conversion complete!")
    print(f"Files in test folder: {len(os.listdir(out_img_dir))}")
