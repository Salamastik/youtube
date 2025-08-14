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
        print("🍪 Cookies file found, checking validity...")
        cmd.insert(-1, "--cookies")
        cmd.insert(-1, cookies_file)
    else:
        print("⚠️ No cookies file found - might face authentication issues")
    
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
        
        # בדיקה מיוחדת לcookies פגי תוקף
        if "cookies are no longer valid" in str(e.stderr):
            print("\n🍪 COOKIES EXPIRED! Your cookies are no longer valid.")
            print("YouTube rotates cookies frequently for security.")
            print("Solutions:")
            print("1. Export fresh cookies from your browser")
            print("2. Update the YOUTUBE_COOKIES secret")
            print("3. The system will now try without cookies...")
        elif "Sign in to confirm you're not a bot" in str(e.stderr):
            print("\n🤖 ANTI-BOT PROTECTION detected")
            print("YouTube thinks you're a bot. Trying alternative methods...")
        
        # נסה עם אופציות חלופיות
        print("\n🔄 Trying alternative method...")
        try:
            # שיטה 1: ללא cookies עם דמוי דפדפן
            alt_cmd = [
                "yt-dlp",
                "--no-playlist",
                "--ignore-errors",
                "--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "--add-header", "Accept-Language:en-US,en;q=0.9",
                "--extractor-args", "youtube:skip=dash,hls",
            ]
            
            if download_format == "audio":
                alt_cmd.extend([
                    "--extract-audio",
                    "--audio-format", "mp3",
                    "--audio-quality", "128K",  # איכות נמוכה יותר
                    "--format", "worstaudio/worst",
                    "--output", f"{output_path}/%(id)s.%(ext)s",
                ])
            else:
                alt_cmd.extend([
                    "--format", "worst[height<=360]",  # איכות נמוכה מאוד
                    "--output", f"{output_path}/%(id)s.%(ext)s",
                ])
            
            alt_cmd.append(url)
            
            alt_result = subprocess.run(alt_cmd, check=True, capture_output=True, text=True, timeout=600)
            print("✅ Alternative method 1 succeeded!")
            print(alt_result.stdout)
            
        except Exception as alt_e:
            print("❌ Alternative method 1 failed, trying method 2...")
            
            # שיטה 2: שימוש בcookies מהדפדפן ישירות
            try:
                browser_cmd = [
                    "yt-dlp",
                    "--cookies-from-browser", "chrome",  # נסה לקחת מChrome
                    "--no-playlist",
                    "--ignore-errors",
                ]
                
                if download_format == "audio":
                    browser_cmd.extend([
                        "--extract-audio",
                        "--audio-format", "mp3",
                        "--audio-quality", "96K",  # איכות מינימלית
                        "--format", "worstaudio",
                        "--output", f"{output_path}/%(id)s_browser.%(ext)s",
                    ])
                else:
                    browser_cmd.extend([
                        "--format", "worst[height<=240]",
                        "--output", f"{output_path}/%(id)s_browser.%(ext)s",
                    ])
                
                browser_cmd.append(url)
                
                browser_result = subprocess.run(browser_cmd, check=True, capture_output=True, text=True, timeout=600)
                print("✅ Browser cookies method succeeded!")
                print(browser_result.stdout)
                
            except Exception as browser_e:
                print("❌ Browser method also failed, trying final fallback...")
                
                # שיטה 3: ללא כל אימות, איכות מינימלית
                try:
                    minimal_cmd = [
                        "yt-dlp",
                        "--no-check-certificates",
                        "--user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36",
                        "--add-header", "Accept:*/*",
                        "--socket-timeout", "30",
                    ]
                    
                    if download_format == "audio":
                        minimal_cmd.extend([
                            "--extract-audio", 
                            "--audio-format", "mp3",
                            "--audio-quality", "64K",  # איכות מאוד נמוכה
                            "--format", "worstaudio[abr<=64]/worst",
                            "--output", f"{output_path}/%(uploader)s_%(id)s.%(ext)s"
                        ])
                    else:
                        minimal_cmd.extend([
                            "--format", "worst[height<=144]/worst",  # 144p
                            "--output", f"{output_path}/%(uploader)s_%(id)s.%(ext)s"
                        ])
                    
                    minimal_cmd.append(url)
                    
                    minimal_result = subprocess.run(minimal_cmd, check=True, capture_output=True, text=True, timeout=300)
                    print("✅ Minimal fallback succeeded!")
                    print(minimal_result.stdout)
                    
                except Exception as final_e:
                    print("❌ All methods failed!")
                    print(f"Original error: {e}")
                    print(f"Alternative 1 error: {alt_e}")  
                    print(f"Browser method error: {browser_e}")
                    print(f"Final fallback error: {final_e}")
                    print("\n💡 Suggestions:")
                    print("1. Update your cookies (they expire frequently)")
                    print("2. Try a different video")
                    print("3. Check if the video is region-locked")
                    print("4. The video might require login to view")
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
