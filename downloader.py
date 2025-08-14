import subprocess
import os
import sys


def main():
    url_file = "video_url.txt"
    output_path = "downloads"
    
    # בדיקה אם קובץ ה-URL קיים
    if not os.path.exists(url_file):
        print(f"❌ Error: {url_file} not found!")
        sys.exit(1)
    
    # יצירת התיקייה אם לא קיימת
    os.makedirs(output_path, exist_ok=True)
    
    # קריאת כתובת הוידאו
    try:
        with open(url_file, "r", encoding='utf-8') as f:
            url = f.read().strip()
    except Exception as e:
        print(f"❌ Error reading {url_file}: {e}")
        sys.exit(1)
    
    # בדיקה אם ה-URL לא ריק
    if not url:
        print(f"❌ Error: {url_file} is empty!")
        sys.exit(1)
    
    print(f"📥 Trying to download video from: {url}")
    
    # הרצת yt-dlp עם אופציות משופרות
    try:
        result = subprocess.run([
            "yt-dlp",
            "--verbose",  # verbose logging
            "--format", "best[height<=720]",  # איכות מוגבלת לחיסכון בזמן ומקום
            "--output", f"{output_path}/%(title)s.%(ext)s",  # שם קובץ עם שם הוידאו
            "--no-playlist",  # מוריד רק וידאו אחד גם אם זה חלק מפלייליסט
            "--ignore-errors",  # ממשיך גם אם יש שגיאות קטנות
            url
        ], check=True, capture_output=True, text=True, timeout=600)  # timeout של 10 דקות
        
        print("✅ yt-dlp completed successfully!")
        print("----- STDOUT -----")
        print(result.stdout)
        
        if result.stderr:
            print("----- STDERR (warnings/info) -----")
            print(result.stderr)
            
    except subprocess.TimeoutExpired:
        print("❌ Download timed out (over 10 minutes)!")
        sys.exit(1)
        
    except subprocess.CalledProcessError as e:
        print("❌ Download failed!")
        print(f"Return code: {e.returncode}")
        print("----- STDOUT -----")
        print(e.stdout if e.stdout else "No stdout")
        print("----- STDERR -----")
        print(e.stderr if e.stderr else "No stderr")
        sys.exit(1)
        
    except Exception as e:
        print(f"❌ Unexpected error: {e}")
        sys.exit(1)
    
    # בדיקה אם יש קבצים בתיקיית ההורדה
    print("\n📂 Checking download folder:")
    try:
        downloaded_files = os.listdir(output_path)
        if downloaded_files:
            print("✅ Files downloaded:")
            total_size = 0
            for filename in downloaded_files:
                filepath = os.path.join(output_path, filename)
                if os.path.isfile(filepath):
                    file_size = os.path.getsize(filepath)
                    total_size += file_size
                    print(f"- {filename} ({file_size / (1024*1024):.2f} MB)")
            print(f"📊 Total size: {total_size / (1024*1024):.2f} MB")
        else:
            print("⚠️ No files found in the downloads folder.")
            sys.exit(1)
            
    except Exception as e:
        print(f"❌ Error checking download folder: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
