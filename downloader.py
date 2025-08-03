import subprocess
import os

url_file = "video_url.txt"
output_path = "downloads"

# יצירת התיקייה אם לא קיימת
os.makedirs(output_path, exist_ok=True)

# קריאת כתובת הוידאו
with open(url_file, "r") as f:
    url = f.read().strip()

print(f"📥 Trying to download video from: {url}")

# הרצת yt-dlp עם דיבאג
try:
    result = subprocess.run([
        "yt-dlp",
        "-v",  # verbose logging
        "-o", f"{output_path}/video.%(ext)s",  # שם קובץ פשוט
        url
    ], check=True, capture_output=True, text=True)

    print("✅ yt-dlp completed.")
    print("----- STDOUT -----")
    print(result.stdout)
    print("----- STDERR -----")
    print(result.stderr)

except subprocess.CalledProcessError as e:
    print("❌ Download failed!")
    print("----- STDOUT -----")
    print(e.stdout)
    print("----- STDERR -----")
    print(e.stderr)

# בדיקה אם יש קבצים בתיקיית ההורדה
print("\n📂 Checking download folder:")
downloaded_files = os.listdir(output_path)
if downloaded_files:
    print("✅ Files downloaded:")
    for f in downloaded_files:
        print(f"- {f}")
else:
    print("⚠️ No files found in the downloads folder.")
