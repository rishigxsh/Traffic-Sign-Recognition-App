import os
import sys
import subprocess
import platform
from pathlib import Path

print("Setting up Python virtual environment...\n")

# ---- Check Python version ----
if not (sys.version_info.major == 3 and sys.version_info.minor <= 12 and sys.version_info.minor >=9 ):
    print("ERROR: This script must be run using Python 3.9-3.12.")
    print("Run using:")
    print("    py -3.11 setup.py   (Windows)")
    print("    python3.11 setup.py (Mac/Linux)")
    sys.exit(1)

# ---- Create venv if missing ----
venv_path = Path("venv")
if venv_path.exists():
    print("Virtual environment already exists. Skipping creation.")
else:
    print("Creating virtual environment...")
    subprocess.check_call([sys.executable, "-m", "venv", "venv"])

# ---- Upgrade pip ----
print("\nUpgrading pip...")
subprocess.check_call([str(venv_path / ("Scripts" if os.name == "nt" else "bin") / "python"), "-m", "pip", "install", "--upgrade", "pip"])

# ---- Install dependencies if requirements.txt exists ----
req_file = Path("requirements.txt")
if req_file.exists():
    print("\nInstalling dependencies...")
    subprocess.check_call([str(venv_path / ("Scripts" if os.name == "nt" else "bin") / "python"), "-m", "pip", "install", "-r", "requirements.txt"])
else:
    print("\nrequirements.txt not found, skipping install.")

# ---- Print activation instructions ----
print("\nSetup complete!")

shell = os.environ.get("SHELL", "").lower()
system = platform.system()

print("\nTo activate the virtual environment:")

if system == "Windows":
    # Detect commonly used shells
    if "powershell" in os.environ.get("TERM", "").lower():
        print("    .\\venv\\Scripts\\Activate.ps1   (PowerShell)")
    else:
        print("    .\\venv\\Scripts\\activate.bat    (CMD)")
    print("    source venv/Scripts/activate     (Git Bash / MSYS2)")
else:
    # Unix shells
    print("    source venv/bin/activate         (bash / zsh)")
    print("    . venv/bin/activate              (sh)")
    print("    source venv/bin/activate.fish    (fish shell)")

print("\nDone.")
