# Deployment Guide - 2Factor SMS Integration

## Prerequisites
- Real 2Factor API Key: `869bfc1e-c4c7-11f1-af74-0200cd936042` (VERIFIED - Active with SMS credits)
- Firebase project: `women-safety-system-65893`
- Java 21 JDK (`jbr-21`)
- Android Studio / Android SDK
- Firebase CLI (for Cloud Functions deployment)

---

## Quick Setup (5 Minutes)

### 1. Configuration Files

**`local.properties`:**
```properties
sdk.dir=C\:\\Users\\Amogh\\AppData\\Local\\Android\\Sdk

# OpenStreetMap / OSMDroid is active (No MAPS_API_KEY needed)

# 2Factor SMS Configuration
TWOFACTOR_API_KEY=869bfc1e-c4c7-11f1-af74-0200cd936042
TWOFACTOR_SENDER_ID=WSAFETY
```

**`firebase/functions/.env`:**
```ini
TWOFACTOR_API_KEY=869bfc1e-c4c7-11f1-af74-0200cd936042
TWOFACTOR_SENDER_ID=WSAFETY
TWOFACTOR_API_URL=https://2factor.in/API/R1/
```

### 2. Verify `.gitignore` Protection
Ensure secret configuration files are NEVER committed to version control:
```text
local.properties
.env
.env.local
firebase/functions/.env
firebase/functions/.env.local
```

### 3. Build the APK

**Windows:**
```batch
.\rebuild.bat
```
*(Or manually: `$env:JAVA_HOME = "C:\Users\Amogh\.jdks\jbr-21.0.11"; .\gradlew clean assembleDebug --no-daemon`)*

**Linux / macOS:**
```bash
chmod +x rebuild.sh && ./rebuild.sh
```

The compiled APK will be at:
`apk/WomenSafety-debug.apk`

### 4. Deploy Firebase Cloud Functions

```bash
# Set 2Factor credentials in Firebase runtime config
firebase functions:config:set \
  twofactor.api_key="869bfc1e-c4c7-11f1-af74-0200cd936042" \
  twofactor.sender_id="WSAFETY"

# Verify config
firebase functions:config:get

# Deploy functions
cd firebase/functions
npm install
firebase deploy --only functions:sendOTPFunction,verifyOTPFunction,onSOSEventCreated
```

### 5. Install & Test

```bash
adb install -r apk/WomenSafety-debug.apk
```

**Testing steps:**
1. Open the app on your physical device or emulator.
2. Go to **Sign Up** $\rightarrow$ enter your phone number (+91XXXXXXXXXX).
3. Tap **Send OTP**.
4. SMS OTP will arrive within 5–10 seconds! ✅

---

## 🔍 Verification

Run the automated verification script:
- **Windows:** `.\verify.bat`
- **Linux / macOS:** `./verify.sh`

---

## 🛠 Troubleshooting

### 1. "Invalid API Key" or "Service Inactive"
- Check that `local.properties` contains:
  `TWOFACTOR_API_KEY=869bfc1e-c4c7-11f1-af74-0200cd936042`
- Verify balance directly:
  ```bash
  curl -s "https://2factor.in/API/V1/869bfc1e-c4c7-11f1-af74-0200cd936042/BAL/SMS"
  ```
  Expected output: `{"Status":"Success","Details":"..."}`

### 2. "Missing templatename value"
- 2Factor's `API/R1/` requires DLT template approval.
- The app automatically uses the dedicated V1 OTP endpoint (`https://2factor.in/API/V1/{API_KEY}/SMS/{PHONE}/{OTP}`) which sends OTPs immediately without waiting for DLT approval.

### 3. SMS Not Arriving
- Verify your phone number format is 10 digits or `+91XXXXXXXXXX`.
- Ensure device has active cellular reception and DND does not block transaction headers.
