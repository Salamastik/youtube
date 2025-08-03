from pytube import YouTube
import os

url_file = "video_url.txt"
output_path = "downloads"

if not os.path.exists(url_file):
    raise FileNotFoundError("video_url.txt לא נמצא")

with open(url_file, "r") as f:
    url = f.read().strip()

print(f"Attempting to download from: {url}")
yt = YouTube(url)
print(f" from: {yt}")
stream = yt.streams.get_highest_resolution()

os.makedirs(output_path, exist_ok=True)
print(f"מוריד את: {yt.title}")
stream.download(output_path=output_path)
print("סיום.")
