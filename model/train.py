import os
from ultralytics import YOLO
import pandas as pd

def main():
    project_dir = "runs/traffic_sign_yolo"
    run_name = "exp1"
    weights_dir = os.path.join(project_dir, run_name, "weights")
    last_weights = os.path.join(weights_dir, "last.pt")

    total_epochs = 50
    patience = 10  # if no improvements after this many epochs then stop training

    if os.path.exists(last_weights):
        print(f"Resuming training from {last_weights}")
        weights = last_weights

        # Try to read the last epoch from the results file if it exists
        results_file = os.path.join(project_dir, run_name, "results.csv")
        if os.path.exists(results_file):
            df = pd.read_csv(results_file)
            if "epoch" in df.columns:
                last_epoch = int(df["epoch"].max()) + 1
                remaining_epochs = int(max(total_epochs - last_epoch, 1))
            else:
                remaining_epochs = total_epochs
        else:
            remaining_epochs = total_epochs
    else:
        print("No checkpoint found. Training from yolov8n.pt")
        weights = "yolov8n.pt"
        remaining_epochs = total_epochs

    remaining_epochs = int(remaining_epochs)

    model = YOLO(weights)

    print(f"Training for {remaining_epochs} more epochs...")
    model.train(
        data="dataset.yaml",
        epochs=remaining_epochs,
        imgsz=640,
        batch=16,
        project=project_dir,
        name=run_name,
        patience=patience
    )

    print("Training complete!")

if __name__ == "__main__":
    main()
