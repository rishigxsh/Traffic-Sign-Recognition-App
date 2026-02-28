import os
from utils.download import download_gtsrb
from utils.file_ops import ensure_dir
from utils.dataset_yaml import create_dataset_yaml
from utils.dataset_to_yolo import convert_train_annotations, convert_test_annotations, create_val_split
from utils.gtsrb_metadata import GTSRB_TRAIN_DIR, GTSRB_TEST_CSV, GTSRB_TEST_DIR, VAL_RATIO

def main():
    print("\n=== PREPARE DATASET ===")

    # Download GTSRB
    download_gtsrb()

    # Create directories
    ensure_dir("dataset/images/train")
    ensure_dir("dataset/images/val")
    ensure_dir("dataset/images/test")
    ensure_dir("dataset/labels/train")
    ensure_dir("dataset/labels/val")
    ensure_dir("dataset/labels/test")

    # Convert train images
    train_files = convert_train_annotations(
        base_dir=GTSRB_TRAIN_DIR,
        out_img_dir="dataset/images/train",
        out_lbl_dir="dataset/labels/train"
    )

    # Create validation split
    create_val_split(
        all_files=train_files,
        train_img_dir="dataset/images/train",
        train_lbl_dir="dataset/labels/train",
        val_img_dir="dataset/images/val",
        val_lbl_dir="dataset/labels/val",
        val_ratio=VAL_RATIO
)

    # Convert test
    convert_test_annotations(
        csv_path=GTSRB_TEST_CSV,
        img_dir=GTSRB_TEST_DIR,
        out_img_dir="dataset/images/test",
        out_lbl_dir="dataset/labels/test"
    )

    # Generate dataset.yaml
    create_dataset_yaml()

    print("\nDataset preparation complete!")


if __name__ == "__main__":
    main()
