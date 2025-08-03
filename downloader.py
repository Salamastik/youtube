
import subprocess
import os

url_file = "video_url.txt"
output_path = "downloads"

os.makedirs(output_path, exist_ok=True)

with open(url_file, "r") as f:
    url = f.read().strip()

print(f"Attempting to download from: {url}")
try:
    subprocess.run([
        "yt-dlp",
        "-o", f"{output_path}/%(title)s.%(ext)s",
        url
    ], check=True)
except subprocess.CalledProcessError as e:
    print("Download failed:", e)
