# QRaft (کیورفت) — Pro QR Studio & Animated Air-Gapped Transfer

<p align="center">
  <b>یک اپلیکیشن حرفه‌ای و مدرن اندروید برای تولید، اسکن، پردازش دسته‌ای و انتقال داده از طریق QR متحرک</b><br/>
  <b>A modern, offline-first Android application built with Jetpack Compose & Material Design 3.</b>
</p>

---

## ✨ Features & Capabilities (امکانات و قابلیت‌ها)

### 🎨 1. QR Studio & Advanced Customization (استودیو ساخت QR)
- **14 Content Types (انواع محتوا)**:
  - 🌐 **URL / وب‌سایت**: لینک مستقیم همراه با پیشوند خودکار
  - 📝 **Plain Text / متن ساده**: با پشتیبانی کامل از کاراکترهای یونیکد و فارسی
  - 👤 **vCard / مخاطب**: نام، نام خانوادگی، شماره همراه، ایمیل، شرکت و آدرس
  - 📶 **Wi-Fi / وای‌فای**: نام شبکه (SSID)، رمز عبور، نوع رمزگذاری (WPA/WPA2, WEP, Open) و شبکه‌های مخفی (Hidden)
  - ✉️ **Email / ایمیل**: گیرنده، موضوع و متن پیام
  - 📞 **Phone / تماس**: شماره‌گیری مستقیم
  - 💬 **SMS / پیامک**: شماره تماس و متن پیام پیش‌فرض
  - 📍 **Location / موقعیت مکانی**: مختصات جغرافیایی (Latitude/Longitude) یا نام مکان/آدرس
  - 📅 **Calendar Event / رویداد تقویم**: عنوان، توضیحات، موقعیت، زمان شروع و پایان با فرمت استاندارد iCalendar
  - 🟢 **WhatsApp / واتساپ**: ارسال مستقیم پیام به شماره همراه
  - ✈️ **Telegram / تلگرام**: لینک مستقیم به نام کاربری (Username)
  - 📸 **Instagram / اینستاگرام**: لینک مستقیم به پروفایل اینستاگرام
  - 💰 **Cryptocurrency / ارز دیجیتال**: پشتیبانی از Bitcoin (BTC), Ethereum (ETH), Tether (USDT), BNB, Solana (SOL), Dogecoin (DOGE) با قابلیت ثبت آدرس، مبلغ و پیام
  - 💳 **PayPal / پی‌پال**: پرداخت مستقیم با نام کاربری یا لینک اختصاصی
- **Deep Styling Matrix (شخصی‌سازی ظاهری پیشرفته)**:
  - 8 پالت رنگی حرفه‌ای (Cyber Indigo, Emerald Mint, Sunset Crimson, Midnight Gold, Forest Pine, Neon Violet, Ocean Deep, Monochrome Slate)
  - پشتیبانی از گرادیانت (افقی، عمودی، مورب)، پس‌زمینه شفاف (Transparent)، و رنگ‌های اختصاصی
  - انواع اشکال نقطه‌ای (Square, Rounded, Dots) و فریم‌های گوشه (Eye Frames & Inners)
  - ۴ سطح تصحیح خطا (L ~7%, M ~15%, Q ~25%, H ~30%)
  - تعبیه لوگو در مرکز بارکد با تنظیم ابعاد و حاشیه
  - واترمارک و برچسب‌های راهنما (مانند SCAN ME)
- **Multi-Format Vector & Document Exporter (خروجی در فرمت‌های متنوع)**:
  - خروجی باکیفیت **PNG** با رزولوشن انتخابی (تا 4096px)
  - خروجی وکتور خالص **SVG** بدون افت کیفیت
  - تولید سند چاپی رسمی **PDF** حاوی بارکد و توضیحات
  - اشتراک‌گذاری مستقیم از طریق Android Share Sheet

### 📷 2. Real-Time Camera Scanner (اسکنر دوربین لحظه‌ای)
- سرعت اسکن فوق‌العاده با تلفیق **CameraX** و **ZXing Core**
- دکمه فعال‌سازی فلاش دوربین (Torch) برای محیط‌های کم‌نور
- کارت هوشمند بعد از اسکن: باز کردن لینک در مرورگر، کپی در کلیپ‌بورد، و انتقال مستقیم به صفحه ویرایش (Edit in Studio)

### ⚡ 3. Batch CSV Generator & ZIP Archiver (تولید دسته‌ای QR)
- ساخت ده‌ها یا صدها کد QR به صورت همزمان از طریق فایل CSV یا متن ورودی
- اعتبارسنجی زنده ردیف‌های ورودی (Valid/Invalid Rows)
- تولید و دانلود فایل فشرده **ZIP** حاوی تمام کدهای تولید شده به همراه نام‌گذاری مرتب
- ذخیره سابقه پردازش‌های دسته‌ای در دیتابیس محلی

### 🎞️ 4. Animated QR Optical Air-Gapped Transfer (انتقال داده با QR متحرک)
- انتقال امن تصاویر و داده‌ها بین دو دستگاه بدون نیاز به اینترنت، وای‌فای یا بلوتوث (Air-Gapped Optical Diode)
- پروتکل اختصاصی قطعه‌بندی با فشرده‌سازی خودکار و اعتبارسنجی دوبل **CRC32**
- پخش‌کننده روان با کنترل سرعت فریم (FPS)، مکث/پخش و اسلایدر جابجایی بین فریم‌ها
- خروجی به صورت فایل متحرک **GIF** یا آرشیو زیپ فریم‌ها
- دریافت و بازسازی تصویر از طریق دوربین یا بارگذاری فایل GIF

### 🔒 5. Privacy, Local Storage & Multi-Language (امنیت و حریم خصوصی)
- **100% آفلاین**: بدون نیاز به اینترنت و بدون ارسال کوچکترین داده به سرورهای خارجی
- **پایگاه داده داخلی Room**: ذخیره تاریخچه، کدهای نشان‌شده، یادداشت‌های اختصاصی و قالب‌های طراحی (Presets)
- **داشبورد آمار تحلیلی محلی**: نمودارهای تفکیک انواع بارکدهای تولید شده
- **دو زبانه و پشتیبانی کامل از فارسی و راست‌چین (RTL & LTR)**
- **پشتیبانی از حالت تاریک و روشن (Dark / Light / System Mode)**

---

## 🛠️ Architecture & Technologies (معماری و فناوری‌ها)

- **UI & Design**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material Design 3 (M3)
- **Language**: Kotlin 2.x, Coroutines & Kotlin StateFlow
- **Architecture Pattern**: MVVM + Clean Architecture with Repository Pattern
- **Local Database**: AndroidX Room with SQLite
- **Camera & Scanning**: CameraX + ZXing Core
- **Export Engines**: Android `PdfDocument`, XML Vector Drawables, Native SVG Generator
- **Storage**: Android FileProvider for secure intent sharing

---

## 🚀 How to Build & Run (نحوه اجرا و کامپایل)

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/qraft-android.git
   ```
2. Open the project in **Android Studio Ladybug (or newer)**.
3. Sync Gradle dependencies.
4. Run the project:
   ```bash
   ./gradlew :app:assembleDebug
   ```

---

## 📄 License

This project is licensed under the **MIT License**.
