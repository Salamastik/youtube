import subprocess
import os
import sys

def main():
    url_file = "video_url.txt"
    output_path = "downloads"
    cookies_file = "cookies.txt"  # קובץ cookies אופציונלי
    
    # בדיקה אם קובץ ה-URL קיים
    if not os.path.exists(url_file):
        print(f"❌ Error: {url_file} not found!")
        sys.exit(1)
    
    # יצירת התיקייה אם לא קיימת
    os.makedirs(output_path, exist_ok=True)
    
    # קריאת כתובת הוידאו ופורמט
    try:
        with open(url_file, "r", encoding='utf-8') as f:
            content = f.read().strip()
    except Exception as e:
        print(f"❌ Error reading {url_file}: {e}")
        sys.exit(1)
    
    # בדיקה אם התוכן לא ריק
    if not content:
        print(f"❌ Error: {url_file} is empty!")
        sys.exit(1)
    
    # פיצול התוכן לURL ופורמט
    parts = content.split()
    url = parts[0]
    
    # בדיקה אם יש בקשה לMP3
    download_format = "video"  # default
    if len(parts) > 1:
        format_request = parts[1].upper()
        if format_request == "MP3":
            download_format = "audio"
            print("📋 MP3 format requested")
        else:
            print(f"📋 Unknown format '{parts[1]}', defaulting to video")
    else:
        print("📋 No format specified, defaulting to video")
    
    print(f"📥 Trying to download {'audio' if download_format == 'audio' else 'video'} from: {url}")
    
    # בניית פקודת yt-dlp
    cmd = [
        "yt-dlp",
        "--verbose",
        "--no-playlist",
        "--ignore-errors",
        "--no-check-certificates",  # מתעלם מבעיות SSL
        "--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebTools/537.36",
        "--referer", "https://www.google.com/",  # מראה שהגעת מגוגל
        "--sleep-interval", "1",  # המתנה בין בקשות
        "--max-sleep-interval", "3",
    ]
    
    # הגדרות לפי פורמט
    if download_format == "audio":
        cmd.extend([
            "--extract-audio",
            "--audio-format", "mp3",
            "--audio-quality", "192K",  # איכות סבירה
            "--output", f"{output_path}/%(title)s.%(ext)s",
            "--format", "bestaudio/best",  # מעדיף audio בלבד
        ])
    else:  # video
        cmd.extend([
            "--format", "best[height<=480]",  # איכות נמוכה יותר כדי להקל
            "--output", f"{output_path}/%(title)s.%(ext)s",
        ])
    
    cmd.append(url)
    
    # אם יש קובץ cookies, הוסף אותו
    if os.path.exists(cookies_file):
        print("🍪 Using cookies file")
        cmd.insert(-1, "--cookies")
        cmd.insert(-1, cookies_file)
    else:
        print("⚠️ No cookies file found - this might cause authentication issues")
    
    # הרצת yt-dlp
    try:
        result = subprocess.run(cmd, check=True, capture_output=True, text=True, timeout=900)  # 15 דקות
        
        print("✅ yt-dlp completed successfully!")
        print("----- STDOUT -----")
        print(result.stdout)
        
        if result.stderr:
            print("----- STDERR (warnings/info) -----")
            print(result.stderr)
            
    except subprocess.TimeoutExpired:
        print("❌ Download timed out (over 15 minutes)!")
        sys.exit(1)
        
    except subprocess.CalledProcessError as e:
        print("❌ Download failed!")
        print(f"Return code: {e.returncode}")
        
        # נסה עם אופציות חלופיות
        print("\n🔄 Trying alternative method...")
        try:
            alt_cmd = [
                "yt-dlp",
                "--no-playlist",
                "--ignore-errors",
                "--extractor-args", "youtube:skip=dash",  # דילוג על DASH formats
            ]
            
            if download_format == "audio":
                alt_cmd.extend([
                    "--extract-audio",
                    "--audio-format", "mp3",
                    "--audio-quality", "128K",  # איכות נמוכה יותר
                    "--format", "worst",
                    "--output", f"{output_path}/%(id)s.%(ext)s",
                ])
            else:
                alt_cmd.extend([
                    "--format", "worst",  # איכות הכי נמוכה
                    "--output", f"{output_path}/%(id)s.%(ext)s",  # שם פשוט יותר
                ])
            
            alt_cmd.append(url)
            
            alt_result = subprocess.run(alt_cmd, check=True, capture_output=True, text=True, timeout=600)
            print("✅ Alternative method succeeded!")
            print(alt_result.stdout)
            
        except Exception as alt_e:
            print("❌ Alternative method also failed!")
            print("----- ORIGINAL STDOUT -----")
            print(e.stdout if e.stdout else "No stdout")
            print("----- ORIGINAL STDERR -----")
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
