# Women Safety System (Android App) — Trinetra

A modern, native Android application built with Kotlin, Jetpack Compose, OpenStreetMap (OSMDroid), Firebase, and the **2Factor SMS API** that provides an intelligent, proactive safety network for women and their trusted contacts — 100% Free & Open-Source with zero Google Maps billing or international gateway fees.

---

## 📱 Pre-Built APK & Quick Installation

A pre-built, ready-to-test debug APK is committed directly in this repository:

- **File Path**: `apk/WomenSafety-debug.apk`
- **File Size**: ~28.14 MB
- **Package Name**: `com.womensafety.system`
- **Target SDK**: Android 14 (API 34) | **Min SDK**: Android 8.0 (API 26)
- **App Version**: `v1.0.1` (versionCode `2`)

### How to Install the APK

#### Option 1: Direct Transfer to Phone (Sideloading)
1. Download or copy `apk/WomenSafety-debug.apk` from this repository to your Android device via USB, Google Drive, or WhatsApp.
2. On your phone, tap the `.apk` file to install.
3. If prompted, enable **"Install unknown apps"** in your device settings.

#### Option 2: Install via ADB (Android Debug Bridge)
Connect your Android phone via USB with USB Debugging enabled, then run:
```bash
adb install -r apk/WomenSafety-debug.apk
```

#### Option 3: Build from Source
Ensure your environment is using **Java 21** (`jbr-21`):
```bash
# Set Java 21 environment
export JAVA_HOME=/path/to/jdk21    # Linux / macOS
# Or on Windows PowerShell:
# $env:JAVA_HOME = "C:\Users\<user>\.jdks\jbr-21.0.11"

./gradlew assembleDebug
```
The output APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🏗 Architecture & Design Principles

The application strictly adheres to Clean Architecture and the Separation of Concerns principle:

```
UI Layer (Jetpack Compose Screens & ViewModels)
       │
       ▼
ViewModel Layer (StateFlow & Coroutines)
       │
       ▼
Repository Layer (Business Logic & Data Access)
       │
       ▼
Firebase Managers (FirebaseAuthManager, FirestoreManager)
       │
       ▼
Backend & Cloud Functions (Firebase Auth, Cloud Firestore, 2Factor SMS REST API)
```

### Architectural Constraints
- **Screens** never touch Firebase directly; they only interact with **ViewModels**.
- **ViewModels** never touch Firebase directly; they only interact with **Repositories**.
- **Repositories** never touch Firebase SDKs directly; they interact only with **FirebaseAuthManager** and **FirestoreManager**.
- **FirebaseAuthManager** and **FirestoreManager** are the **ONLY** mobile classes that import Firebase SDKs.
- Background operations, location tracking, and sensors are coordinated via dedicated Managers and Foreground Services (`SafetyMonitoringManager`, `TravelModeManager`, `SOSManager`, `LocationTrackingService`, `ShakeDetectionService`).

---

## 🌟 Comprehensive Feature Overview

### 1. Authentication & Onboarding
- **User Authentication**:
  - 6-step guided signup flow:
    - Step 1: Full Name & Primary Email
    - Step 2: Password creation & confirmation (with strength checks)
    - Step 3: Phone Number input
    - Step 4: Phone OTP verification via 2Factor SMS API (`OTPVerificationScreen`)
    - Step 5: Email verification check
    - Step 6: Confirmation & completion
  - Email + Password login with error handling and password reset support
- **Trusted Contact (TC) Authentication**:
  - Phone OTP-based signup and login (passwordless)
  - Profile setup with phone verification
- **Role Selection & Splash**:
  - Role selection screen (User vs. Trusted Contact)
  - Animated Splash screen with session check and auto-routing

### 2. Weekly Rotating Safety UID System
- **Format**: 12-digit format (`YYYYMMDDXXXX`)
- **Rotation & Expiry**: Generates a 7-day valid UID for privacy preservation
- **Firestore Collections**:
  - `devUidMappings` for Spark-plan dev/testing mode
  - `weeklyUids` for Blaze-plan with Cloud Functions
- **Sharing**: One-tap share intent to share the Safety UID with trusted contacts

### 3. Relationship & Contact Management
- **Add User via UID**: Trusted contacts look up a User using their 12-digit Safety UID.
- **Request Approval / Denial / Revocation**: Users receive requests from Trusted Contacts and can approve, deny, or revoke access at any time.
- **Deterministic Document IDs**: Firestore relationships use `{userId}_{trustedContactId}` document IDs complying with security rules.
- **Permission Requests Screen**: Dedicated screen to review and manage incoming TC connection requests.

### 4. Initial Setup Wizard (5 Screens)
- **Automatic Post-Signup Routing**: Shows immediately after User Signup Step 6 or upon first login when `setupCompleted == false`.
- **Screen 1: WelcomeSetupScreen**: Hero card presentation of safety pillars.
- **Screen 2: PermissionsSetupScreen**: Accompanist runtime permissions handling for Location, Background Location, SMS, Phone Call, and Notifications.
- **Screen 3: EmergencyContactsSetupScreen**: In-line dialog to add emergency SMS contacts (minimum 1 contact enforced).
- **Screen 4: SOSPreferencesScreen**: Tracking, shake detection, sensitivity, and auto-call preferences.
- **Screen 5: SetupCompleteScreen**: Animated summary committing all settings to Firestore.

### 5. Emergency Contacts Management
- **EmergencyContactsListScreen**: Manage SMS emergency contacts with priority, addition dates, and deletion.
- **AddEmergencyContactScreen**: Form with full name, phone validation, and primary contact toggle.
- **EditEmergencyContactScreen**: Update contact details with real-time Firestore synchronization.

### 6. Phase 2: Location Tracking Service & Live Maps
- **LocationTrackingService**: Background foreground service with WakeLock protection.
- **Dual Update Intervals**: 30 seconds for normal tracking, automatically boosted to 5 seconds during active SOS alerts.
- **Firestore Streaming**: Streams GPS coordinates, accuracy, speed, and addresses to `userLocations/{userId}/locations/{locationId}`.
- **LocationSettingsScreen**: Update interval customization (5s-60s) and tracking toggles.
- **LocationHistoryScreen**: Historical timeline of past location updates with compact OpenStreetMap interactive previews and SOS filtering.
- **LiveLocationMapScreen**: OpenStreetMap (OSMDroid) interface for Trusted Contacts to view real-time location markers, accuracy rings, reverse geocoded addresses, and external navigation intents. Powered by `SafetyMapTracker` with zero API key or billing required.

### 7. Phase 3: Shake Detection Service & Sensor Management
- **ShakeDetector**: High-precision accelerometer sensor listener with gravity subtraction and debounce filtering.
- **ShakeDetectionService**: Background foreground service with persistent status notification and 3-burst haptic vibration feedback.
- **Configurable Sensitivity**: LOW (20 m/s²), MEDIUM (15 m/s²), HIGH (12 m/s²).
- **ShakeDetectionSettingsScreen**: Interactive testing mode allowing users to safely test shake sensitivity without triggering actual SOS.

### 8. Phase 4: Complete SOS Alert System
- **SOSManager Core Orchestration**:
  1. Requests immediate high-accuracy GPS fix with fallback to last known location.
  2. Creates `SOSEvent` in Firestore `sosEvents/{eventId}`.
  3. Activates loud siren audio on `STREAM_ALARM` (bypassing silent mode) and urgent vibration patterns.
  4. Triggers background Cloud Function `onSOSEventCreated` which dispatches high-priority SMS alerts via the **2Factor SMS API** to all registered emergency contacts with live GPS coordinates and Google Maps link.
  5. Auto-places phone call to primary contact if enabled.
- **SOSAlertScreen**: Full-screen emergency UI with pulsing red glow, cancel timer, countdown, siren toggle, flashlight toggle, and manual cancellation PIN.
- **SOSReceivedScreen**: Receiver UI for Trusted Contacts displaying user location via OpenStreetMap `SafetyMapTracker`, trigger method, and direct calling.
- **SOSEventMapScreen**: Dedicated incident visualization screen showing exact alert GPS coordinates on OpenStreetMap with SMS delivery audit logs.
- **SOSHistoryScreen & SOSHistoryDetailScreen**: Review past SOS events and audit logs.

### 9. Proactive Safe Travel Mode (Phase 5)
- **DirectionsApiHelper**: OpenStreetMap / OSRM (Open Source Routing Machine) road routing with GeoJSON waypoint decoding. Fully free and open-source, with automatic realistic road curve fallback when offline.
- **TravelModeManager**: Autonomous journey state machine (`NOT_STARTED`, `ACTIVE`, `DEVIATION_DETECTED`, `USER_CONFIRMED`, `NO_RESPONSE`, `ESCALATED`, `COMPLETED`).
- **Route Deviation & Anomaly Detection**:
  - Real-time cross-track distance calculations against polyline waypoints (orthodromic math using `GeoPoint`).
  - Configurable deviation tolerance (100m, 200m, 300m, 500m, 1000m).
  - Prolonged stop and inactivity detection during active travel.
  - Automatic 30-second "Are You Safe?" countdown prompt upon detected deviation.
  - Escalation to emergency alert and notification if prompt is unanswered.
- **Travel Screens**:
  - `TravelModeScreen`: Journey setup with address search, geocoding, and OpenStreetMap pin picker for source and destination.
  - `ActiveJourneyScreen`: Real-time OpenStreetMap navigation showing expected route polyline, live speedometer, deviation metrics, stop counters, and emergency quick-access.
  - `TravelHistoryScreen`: Trip logs and journey archives.
- **Persistence**: Firestore collection `/journeys/{journeyId}` with `JourneyRecord` data model and `JourneyRepository`.

### 10. Trinetra Safety Modes & Risk Assessment Engine (Phase 6)
- **Four Distinct Safety Modes**:
  1. 🏠 **REGULAR**: Balanced everyday monitoring.
  2. 🚗 **TRAVELLING**: Active route deviation monitoring, stop detection, and high shake sensitivity.
  3. 💼 **WORKING / COLLEGE**: Context-aware monitoring near saved workplace/college with reduced false-alarm sensitivity.
  4. 😴 **SLEEPING**: Silent mode disabling passive GPS broadcasts and shake triggers while preserving SOS and contact accessibility.
- **Automatic Mode Suggestions**: Suggests mode changes based on saved workplace/home geofences, nighttime hours, and transit speed.
- **RiskAssessmentEngine**: Transparent, rule-based 0-100 risk scoring algorithm evaluating:
  - Route deviation (+30 to +35 pts)
  - No response to deviation alerts (+50 pts)
  - Unexpected prolonged stops (+40 pts)
  - Sensor shake events (+30 pts)
  - Late-night hours 10 PM – 5 AM (+15 pts)
  - Safety Mode sensitivity multiplier (`NORMAL` = 1.0x, `CAUTIOUS` = 1.3x, `HIGH_RISK` = 1.6x)
- **AreYouSafeScreen**: Proactive check-in overlay with pulsating alert glow, detected anomaly breakdown, 30-second audio countdown timer, "I'm Safe" verification, and instant SOS escalation.
- **Persistence**: Firestore collection `/riskEvents/{eventId}` with `RiskEvent` data model and `RiskRepository`.

### 11. Diagnostic & Testing Tools
- **LocationTestScreen**: Real-time GPS diagnostics showing latitude, longitude, altitude, accuracy, speed, bearing, provider status, OpenStreetMap readiness, and live map preview fix.
- **MapTestScreen**: OpenStreetMap (OSMDroid) rendering validation for markers, camera controls, and zoom levels.

### 12. User Profile & Home UI Enhancements
- **UserHomeScreen**: Integrated Safety Mode quick-selector card, active journey banner, quick-action tiles (Safe Travel, Shake Settings, Contacts, SOS History), live status indicators, risk score pill.
- **UserProfileScreen**:
  - Workplace/College saved coordinates & address configuration.
  - Automatic mode suggestions toggle.
  - Proactive safety monitoring switches.
  - Safety mode threshold multiplier dropdown (`NORMAL`, `CAUTIOUS`, `HIGH_RISK`).
  - Route deviation tolerance selector.
  - Password reset and phone change flows.

---

## 🔐 Phone OTP Verification & 2Factor SMS Gateway

To ensure 100% reliable SMS delivery on Indian mobile networks (+91), the project uses the **2Factor SMS API** (`https://2factor.in/API/R1/`), replacing Twilio and unreliable FCM messaging.

### Why 2Factor?
- **Designed for India (+91)**: Instant delivery across Indian telecom operators without international carrier blocks or DND drops.
- **Free Trial**: Includes 250 free SMS credits out-of-the-box.
- **Zero Mobile SDK Bloat**: Uses simple HTTPS REST endpoints with `axios` on Cloud Functions; no heavy native SMS SDKs required in the Android app.
- **Robust Verification**: Server-side OTP generation and Firestore-backed lockout protection.

### Backend Cloud Functions Architecture

```
Mobile App (OTPVerificationScreen / AuthViewModel)
       │
       ├── HTTPS Callable (functions.onCall)
       ▼
sendOTPFunction (Cloud Function in firebase/functions/src/sendOTP.ts)
       │
       ├── 1. Validates & normalizes phone (+91 / 10-digit -> 91XXXXXXXXXX)
       ├── 2. Generates secure 6-digit numeric OTP
       ├── 3. Stores OTP in Firestore `/otps/{phoneNumber}` (10-min expiry)
       └── 4. Calls 2Factor API (module: 'OTP') via Axios
       │
       ▼
2Factor SMS Gateway (https://2factor.in/API/R1/)
       │
       ▼
User Mobile Phone (Receives SMS: "Women Safety OTP: 123456. Valid for 10 minutes.")
```

#### Functions Implemented:

1. **`sendOTPFunction`** (`firebase/functions/src/sendOTP.ts`):
   - **Input**: `{ phoneNumber: string, userName?: string }`
   - **Output**: `{ success: true, message: string, expiresIn: 600, messageId: string }`
   - Generates 6-digit OTP, checks lockout status, dispatches SMS via 2Factor, and records metadata in Firestore `/otps/{phoneNumber}`.

2. **`verifyOTPFunction`** (`firebase/functions/src/sendOTP.ts`):
   - **Input**: `{ phoneNumber: string, otp: string }`
   - **Output**: `{ success: true, message: string, phoneNumber: string }`
   - Validates OTP against Firestore document.
   - Enforces rate limiting: Locks phone for 15 minutes after 5 consecutive failed attempts.

3. **`onSOSEventCreated`** (`firebase/functions/src/sendSOSAlert.ts`):
   - Background Firestore trigger on `sosEvents/{eventId}` creation.
   - Automatically queries user's emergency contacts from `users/{userId}`.
   - Dispatches emergency SOS SMS alerts with live GPS coordinates and Google Maps link via 2Factor (`module: 'TRANSACTIONAL_SMS'`).
   - Updates `sosEvents/{eventId}` with delivery results and timestamps.

4. **`twoFactorHelper.ts`** (`firebase/functions/src/twoFactorHelper.ts`):
   - Phone number normalizer (converts `+919876543210` or `9876543210` to `919876543210`).
   - Generic HTTP wrapper for `OTP` and `TRANSACTIONAL_SMS` modules with timeout and error handling.

### Mobile App Integration
- **`OTPVerificationScreen.kt`**: Material 3 dark-themed UI featuring 6 single-digit auto-advancing text fields, 60-second resend countdown timer, loading spinner, and error alert dialogs.
- **`OTPViewModel.kt`**: StateFlow-driven architecture calling Cloud Functions via `Firebase.functions.getHttpsCallable(...)` with helpful error diagnostics.
- **Navigation**: Registered route `OTP_VERIFICATION/{phoneNumber}/{userName}` in `NavGraph.kt`.

### 2Factor Configuration & DLT Setup
1. Sign up at [https://www.2factor.in/](https://www.2factor.in/) and obtain your API Key from the dashboard.
2. In the 2Factor dashboard, request Sender ID **`WSAFETY`** with template:
   `Women Safety OTP: {#var#}. Valid for 10 minutes.`
3. Set your credentials in Firebase Functions:
   ```bash
   firebase functions:config:set \
     twofactor.api_key="YOUR_2FACTOR_API_KEY" \
     twofactor.sender_id="WSAFETY"
   ```
4. Local testing: Add keys to `firebase/functions/.env`:
   ```ini
   TWOFACTOR_API_KEY=your_key_here
   TWOFACTOR_SENDER_ID=WSAFETY
   TWOFACTOR_API_URL=https://2factor.in/API/R1/
   ```

---

## 🗺️ 100% Free Open-Source Mapping (OSMDroid & OSRM)

All mapping and navigation capabilities use **OpenStreetMap (OSMDroid)** and **OSRM Routing Engine**:
- **Zero API Key Requirement**: No Google Cloud Console billing setup, credit card, or proprietary Maps SDKs.
- **Components**:
  - `SafetyMapTracker.kt`: Jetpack Compose `AndroidView` wrapper over OSMDroid `MapView` with custom user markers, accuracy radius circles, and auto-camera panning.
  - `DirectionsApiHelper.kt`: OSRM routing engine with GeoJSON polyline decoding and offline curved bezier calculation fallback.
  - `MapUtils.kt`: Haversine distance, GeoPoint conversions, and generic external navigation intents.
  - Full removal of `play-services-maps` and `maps-compose`.

---

## 🔒 Firebase Security Rules Summary

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users & Trusted Contacts: Protected access with relationship read grants
    // Weekly UIDs & Dev UIDs: Time-bounded and authenticated access
    // Relationships: Deterministic {userId}_{trustedContactId}
    // Location History: userLocations/{userId}/locations/{locationId}
    // SOS Events: sosEvents/{eventId}
    // Risk Events: riskEvents/{eventId}
    // Journeys: journeys/{journeyId}
    // OTPs: otps/{phoneNumber} (Write locked to Admin SDK / Cloud Functions only)
  }
}
```

---

## 🛠 Tech Stack & Dependencies

- **Language**: Kotlin 2.1.20 (JDK 21)
- **UI Framework**: Jetpack Compose with Material 3 Dark Theme
- **Architecture**: Clean Architecture (UI $\rightarrow$ ViewModel $\rightarrow$ Repository $\rightarrow$ Firebase Managers)
- **Async & State**: Kotlin Coroutines, StateFlow, Flow
- **Navigation**: Jetpack Navigation Compose (Type-Safe Routes)
- **Backend & Database**: Firebase Auth, Cloud Firestore, Cloud Functions (Node.js 18 TypeScript with `axios`)
- **SMS Gateway**: 2Factor SMS REST API (Indian Telecom Gateway)
- **Mapping & Routing**: OpenStreetMap (`org.osmdroid:osmdroid-android:6.1.18`), OSRM Driving Engine, Google Play Services Location (`play-services-location` for device GPS fix)
- **Permissions**: Accompanist Permissions

---

## 🐛 Troubleshooting Guide

### 1. "NOT_FOUND" Error on Mobile When Sending OTP
**Root Cause**: The mobile app calls `sendOTPFunction` via `functions.getHttpsCallable("sendOTPFunction")`. If Cloud Functions have **not been deployed** to your Firebase project (`women-safety-system-65893`), Google Cloud returns an HTTP 404, which the Firebase Android SDK surfaces as `NOT_FOUND`.

**Step-by-Step Fix**:
```bash
# 1. Switch to functions directory
cd firebase/functions

# 2. Install dependencies
npm install

# 3. Configure 2Factor credentials
firebase functions:config:set \
  twofactor.api_key="YOUR_2FACTOR_API_KEY" \
  twofactor.sender_id="WSAFETY"

# 4. Deploy functions to Firebase
firebase deploy --only functions:sendOTPFunction,verifyOTPFunction,onSOSEventCreated

# 5. Verify deployment
firebase functions:list
```

### 2. Testing 2Factor API Directly via cURL
To verify your API key and sender ID independently of Firebase:
```bash
curl -X POST "https://2factor.in/API/R1/" \
  -d "module=TRANS_SMS" \
  -d "apikey=YOUR_2FACTOR_API_KEY" \
  -d "to=919876543210" \
  -d "from=WSAFETY" \
  -d "msg=Women Safety OTP: 123456. Valid for 10 minutes."
```
> **Note on `module` parameter**: 2Factor's `API/R1/` endpoint requires `module=TRANS_SMS` for transactional OTP and alert messages. Specifying `module=OTP` or `module=TRANSACTIONAL_SMS` will return `{"Status":"Error","Details":"Invalid module name passed"}`.

**Expected Response**:
```json
{
  "Status": "Success",
  "Details": "09cfe5b29d000018a3acb36000000001"
}
```

### 3. "Invalid module name passed" Error
**Root Cause**: Passing `module=OTP` or `module=TRANSACTIONAL_SMS` to `https://2factor.in/API/R1/`.
**Fix**: Use `module=TRANS_SMS`. Both `TwoFactorClient.kt` (direct fallback) and `twoFactorHelper.ts` (Cloud Functions) have been updated to use `TRANS_SMS`.

### 4. Monitoring Function Logs
Check real-time execution logs when clicking "Send OTP" on your mobile device:
```bash
firebase functions:log
```

---

## ⚙️ Development, Build & Deployment Guide

### 1. JDK Requirement (Android Studio)
> **IMPORTANT:** Ensure your Gradle JDK in Android Studio is set to **Java 21**:
> - Open **Settings** (`Ctrl + Alt + S`) $\rightarrow$ **Build, Execution, Deployment** $\rightarrow$ **Build Tools** $\rightarrow$ **Gradle**.
> - Set **Gradle JDK** to **Java 21** (`jbr-21`).
> - *(Avoid using Android Studio's bundled JDK 25 with Gradle 8.9 to prevent `Unsupported class file major version 69`).*

### 2. Building the APK
```powershell
# Set JAVA_HOME and compile debug APK
$env:JAVA_HOME = "C:\Users\<username>\.jdks\jbr-21.0.11"
.\gradlew assembleDebug --no-daemon
```
The compiled APK will be at:
`app/build/outputs/apk/debug/app-debug.apk` and copied to `apk/WomenSafety-debug.apk`.

### 3. Deploying Cloud Functions
```bash
cd firebase/functions
npm install
firebase deploy --only functions
```

---

## 📂 Project Structure

```
Capstone/
├── app/
│   └── src/main/java/com/womensafety/system/
│       ├── data/
│       │   ├── firebase/ (FirebaseAuthManager, FirestoreManager)
│       │   ├── model/ (User, TrustedContact, SOSEvent, JourneyRecord, etc.)
│       │   └── repository/ (AuthRepository, LocationRepository, etc.)
│       ├── service/ (LocationTrackingService, ShakeDetectionService)
│       ├── ui/
│       │   ├── auth/ (UserSignupStep*, TCAuthScreen, OTPVerificationScreen)
│       │   ├── home/ (UserHomeScreen, TCHomeScreen)
│       │   ├── map/ (SafetyMapTracker OSM wrapper, LiveLocationMapScreen)
│       │   ├── navigation/ (NavGraph, Screen destinations)
│       │   ├── setup/ (5-step Setup Wizard)
│       │   ├── sos/ (SOSAlertScreen, SOSReceivedScreen, SOSEventMapScreen)
│       │   └── travel/ (TravelModeScreen, ActiveJourneyScreen)
│       ├── utils/ (MapUtils, DirectionsApiHelper, RiskAssessmentEngine, Validators)
│       └── viewmodel/ (AuthViewModel, OTPViewModel, LocationViewModel, SOSViewModel)
├── firebase/
│   └── functions/
│       ├── src/
│       │   ├── index.ts (Cloud Functions entry point)
│       │   ├── sendOTP.ts (sendOTPFunction, verifyOTPFunction)
│       │   ├── sendSOSAlert.ts (onSOSEventCreated background trigger)
│       │   └── twoFactorHelper.ts (2Factor SMS REST API wrapper)
│       ├── package.json
│       ├── tsconfig.json
│       └── README-2Factor.md
├── apk/
│   └── WomenSafety-debug.apk (pre-built testing APK)
├── firestore.rules (security rules)
└── README.md
```

---

## 📂 Git Branch Reference

- **`otp-update`** *(Current Active Branch)*:
  - Full 2Factor SMS API integration (replacing Twilio & FCM).
  - Phone OTP verification screen & ViewModel (`OTPVerificationScreen`, `OTPViewModel`).
  - Cloud Functions: `sendOTPFunction`, `verifyOTPFunction`, `onSOSEventCreated`.
  - OpenStreetMap (OSMDroid) migration.
  - Pre-built debug APK included at `apk/WomenSafety-debug.apk`.
- **`map-update`**:
  - OpenStreetMap (OSMDroid) migration baseline without Google Maps billing.
- **`main`**:
  - Baseline project branch.

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:
1. Fork the repository and create a feature branch.
2. Commit your changes with clear, descriptive commit messages.
3. Test all mobile flows and Cloud Functions locally.
4. Open a pull request against the `main` branch.

## 📄 License

MIT License — see LICENSE for details.