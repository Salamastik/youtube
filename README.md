# 🎵 YouTube Video & Audio Downloader

מערכת הורדה אוטומטית של וידאו ואודיו מ-YouTube בעזרת GitHub Actions. תומכת בהורדת וידאו (MP4) ואודיו (MP3) באיכות מותאמת.

## ✨ תכונות

- 📥 **הורדת וידאו** באיכות מותאמת (עד 480p)
- 🎵 **הורדת אודיו** כקובצי MP3 (192K איכות)
- 🖱️ **ממשק ידידותי** - הזנת קישור דרך האתר
- 🍪 **תמיכה ב-cookies** לעקיפת הגבלות
- 📦 **שמירה אוטומטית** כ-artifacts
- 🔄 **שיטות גיבוי** במקרה של כשל

## 🚀 איך להתחיל

### שלב 1: הכנת הרפוזיטורי
1. **Fork** את הרפוזיטורי או העתק את הקבצים
2. ודא שיש לך את הקבצים:
   - `.github/workflows/download.yml`
   - `downloader.py`

### שלב 2: הגדרת Cookies (אופציונלי אך מומלץ)

לעקיפת הגבלות YouTube:

1. **התחבר ל-YouTube** בדפדפן Chrome/Firefox
2. **התקן תוסף** לייצוא cookies (כמו "Get cookies.txt LOCALLY")
3. **ייצא cookies** ל-YouTube
4. **הוסף כ-Secret ברפוזיטורי:**
   - Settings → Secrets and variables → Actions
   - New repository secret
   - **שם:** `YOUTUBE_COOKIES`
   - **ערך:** תוכן קובץ ה-cookies

## 💻 איך להשתמש

### דרך 1: ממשק גרפי (מומלץ) 🖱️

1. **לך לטאב Actions** ברפוזיטורי שלך
2. **לחץ על "Download YouTube Video"** ברשימה משמאל
3. **לחץ על "Run workflow"** מימין
4. **מלא את השדות:**
   - **YouTube URL:** הדבק קישור לוידאו
   - **Download format:** בחר Video או Audio (MP3)
5. **לחץ "Run workflow"**

![איך להפעיל](https://docs.github.com/assets/cb-48389/mw-1440/images/help/actions/manual-workflow-run.webp)

### דרך 2: דרך קובץ (שיטה ישנה) 📝

1. **ערוך את `video_url.txt`:**
   - להורדת וידאו: `https://www.youtube.com/watch?v=VIDEO_ID`
   - להורדת MP3: `https://www.youtube.com/watch?v=VIDEO_ID MP3`
2. **שמור והעלה לרפוזיטורי**
3. **ה-Action יתחיל אוטומטית**

## 📥 קבלת הקבצים

1. **לך לרשימת הרצות** (Actions → הרצה ספציפית)
2. **גלול למטה ל-Artifacts**
3. **לחץ להורדה:**
   - `downloaded-video-XXX` - קובצי וידאו
   - `downloaded-audio-XXX` - קובצי MP3

## ⚙️ הגדרות מתקדמות

### איכות הורדה
- **וידאו:** עד 480p (מותאם לחיסכון)
- **אודיו:** 192K (איכות טובה, נפח סביר)

### מגבלות זמן
- **timeout:** 15 דקות להורדה
- **גיבוי:** נסיון חלופי באיכות נמוכה יותר

## 🔧 פתרון בעיות

### שגיאת "Sign in to confirm you're not a bot"
**פתרון:** הוסף cookies כמתואר בסעיף ההכנה

### שגיאת "No files found"
1. בדוק שהקישור תקין
2. נסה עם וידאו אחר
3. ודא שה-cookies מעודכנים

### ההורדה נכשלת
- **נסה שוב** - לפעמים זה עובד בפעם השנייה
- **בדוק שהוידאו זמין** בארץ שלך
- **עדכן cookies** אם הם ישנים

## 📋 דוגמאות לשימוש

### וידאו רגיל
```
https://www.youtube.com/watch?v=dQw4w9WgXcQ
```

### פלייליסט (יוריד רק הראשון)
```
https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLEXAMPLEplaylist
```

### וידאו קצר
```
https://www.youtube.com/shorts/EXAMPLE123
```

### לינק מקוצר
```
https://youtu.be/dQw4w9WgXcQ
```

## ⚖️ הערות חוקיות

- 📖 **השתמש באחריות** - כבד זכויות יוצרים
- 🎯 **לשימוש אישי** בלבד
- 📋 **בדוק את תקנון YouTube** במדינה שלך

## 🛠️ מפתחים

### קבצים מרכזיים
- **`download.yml`** - הגדרות GitHub Actions
- **`downloader.py`** - לוגיקת ההורדה
- **`video_url.txt`** - קובץ אופציונלי לקישורים

### טכנולוגיות
- **yt-dlp** - ספריית ההורדה
- **ffmpeg** - המרת פורמטים
- **GitHub Actions** - אוטומציה

### תרומה לפרויקט
Pull requests מתקבלים בברכה! 🎉

---

<div align="center">

**נהנית מהכלי?** ⭐ תן כוכב לרפוזיטורי!

Made with ❤️ for the community

</div>
