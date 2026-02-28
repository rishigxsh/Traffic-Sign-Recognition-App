import os

def ensure_dir(path: str):
    os.makedirs(path, exist_ok=True)

def list_images(directory, ext=(".ppm", ".png", ".jpg")):
    return sorted([f for f in os.listdir(directory) if f.endswith(ext)])
