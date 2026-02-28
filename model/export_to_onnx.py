import os
import onnxruntime as ort
from ultralytics import YOLO

def export_to_onnx(
    weights_path="runs/traffic_sign_yolo/exp1/weights/best.pt",
    output_dir="exports",
    opset=12
):
    os.makedirs(output_dir, exist_ok=True)

    print(f"Loading model from: {weights_path}")
    model = YOLO(weights_path)

    print("Exporting to ONNX...")
    onnx_path = model.export(format="onnx", opset=opset)

    final_onnx_path = os.path.join(output_dir, "model.onnx")
    os.replace(onnx_path, final_onnx_path)

    print(f"ONNX model saved to: {final_onnx_path}")

    # Validate ONNX with ONNX Runtime
    print("Validating ONNX model with ONNX Runtime...")
    session = ort.InferenceSession(final_onnx_path)
    print("ORT loaded the model successfully.")

    print("\nDone!")
    return final_onnx_path


if __name__ == "__main__":
    export_to_onnx()
