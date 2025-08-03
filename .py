from pytube import YouTube
import sys
import os

def download_video(url, output_path="downloads"):
    try:
        yt = YouTube(url)
        stream = yt.streams.get_highest_resolution()
        os.makedirs(output_path, exist_ok=True)
        print(f"הורדת הסרטון: {yt.title}")
        stream.download(output_path=output_path)
        print(f"ההורדה הושלמה לתיקייה '{output_path}'")
    except Exception as e:
        print(f"שגיאה בהורדת הסרטון: {e}")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("שימוש: python downloader.py <youtube_video_url>")
    else:
        download_video(sys.argv[1])


