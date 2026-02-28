# Utility file to download datasets with torchvision

from torchvision.datasets import GTSRB

def download_gtsrb(root="dataset"):
    print("Downloading GTSRB via torchvision...")
    train = GTSRB(root=root, split="train", download=True)
    test = GTSRB(root=root, split="test", download=True)
    return train, test
