# Women Safety System (Android App) — Trinetra

A modern, native Android application built with Kotlin, Jetpack Compose, OpenStreetMap (OSMDroid), Firebase, the **2Factor SMS API**, and **Bluetooth Low Energy (BLE) Smartwatch / Health Connect** telemetry that provides an intelligent, proactive safety network for women and their trusted contacts — 100% Free & Open-Source with zero Google Maps billing or international gateway fees.

---

## 📱 Pre-Built APK & Quick Installation

A pre-built, ready-to-test debug APK is committed directly in this repository:

- **File Path**: `apk/WomenSafety-debug.apk`
- **File Size**: ~31.28 MB
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

### 10. Trinetra Safety Modes & Risk Assessment Engine (Phase 6 & Enhanced Safety Modes)
- **Four Distinct Safety Modes**:
  1. 🏠 **REGULAR**: Routine-based spatiotemporal anomaly detection, auto-learning daily patterns, and proactive deviation alerting.
  2. 🚗 **TRAVELLING**: Active route deviation monitoring, stop detection, and high shake sensitivity.
  3. 💼 **WORKING / COLLEGE**: Colleague awareness, peer safety partnerships (Safety Buddy), and automated late-stay transport protection.
  4. 😴 **SLEEPING**: Smart emergency override system allowing critical calls and SOS through while protecting sleep, with 10-second grace-period fall detection.

- **Feature Deep Dive: Strategic Enhanced Safety Modes**:
  - **1. Routine-Based Anomaly Detection (REGULAR Mode — Hybrid Auto-Learn + Manual Entry)**:
    - **30-Day GPS History Auto-Learning Engine (`RoutineAutoLearner.kt`)**: Automatically queries GPS trajectories from `userLocations/{userId}/locations/{timestamp}` for the past 30 days. Uses a spatial proximity clustering algorithm (<100m threshold) to identify recurring anchor locations.
    - **Time Window & Confidence Scoring**: Calculates arrival/departure windows, frequency of visits in 30 days, reverse-geocodes addresses via Android Geocoder, and assigns confidence scores (0.0 to 1.0) based on historical consistency.
    - **Firestore Routine Storage**: Persists learned routines to `routines/{userId}/{dayOfWeek}/{locationId}`.
    - **Daily 06:00 AM Background Execution**: Automatically re-learns and refines routines once per day at 06:00 AM using `AlarmManager` and `RoutineAutoLearnReceiver`.
    - **Manual Routine Entry Form (`RoutineManualEntryDialog.kt`)**: Allows users to manually add, edit, or delete custom anchor locations with GPS auto-fill, radius slider (50–500m), arrival/departure time pickers, and Mon–Sun day checkboxes with immediate Firestore persistence.
    - **3-Tab Production UI (`RoutineMonitoringScreen.kt`)**:
      - **Tab 1: Your Routine**: Real-time list of today's learned/manual locations with confidence, frequency, edit/delete actions, and "+ Add Custom Location" button.
      - **Tab 2: Anomaly History**: Chronological log of detected spatial/temporal deviations with severity badges and response history.
      - **Tab 3: Auto-Learn Status**: Engine status, collection day count, last updated timestamp, next scheduled update, and manual refresh trigger.
    - **Intelligent Anomaly Detection**: Real-time evaluation against today's routine radius and typical time window, alerting only when unexpected deviations occur.

  - **2. Colleague Awareness & Mutual Safety Buddy Pairing (WORKING Mode — Mobile-to-Mobile BLE & Geofence)**:
    - **Hardware BLE Broadcasting (`WorkplaceBeaconManager.kt`)**: Advertises user presence using `BluetoothLeAdvertiser` with service UUID `0000180A-0000-1000-8000-00805F9B34FB`. Automatically checks Bluetooth hardware availability and prompts when disabled.
    - **Continuous BLE Scanning (`WorkplaceColleagueScannerManager.kt`)**: Scans for neighboring colleagues running Trinetra. Decodes payloads and cross-verifies against Firestore `trustedContacts/{currentUserId}/{discoveredUserId}` for strict privacy protection (unapproved beacons are discarded).
    - **Path-Loss Distance Estimation**: Computes precise proximity using the log-distance path loss formula:
      $$\text{Distance} = 10^{\frac{\text{txPower} - \text{RSSI}}{10 \cdot n}}$$
      (where $\text{txPower} = -59\text{ dBm}$ and $n = 2.0$).
    - **Workplace Geofence Fallback (`WorkplaceGeofenceManager.kt`)**: When BLE signals are obstructed, evaluates the colleague's last known location against the user's configured workplace coordinates (<500m geofence) with periodic 30-second re-verification.
    - **Tiered Presence Resolution (`ColleagueDetectionManager.kt`)**: Combines detection layers (Tier 1: BLE 0.95 confidence $\rightarrow$ Tier 2: Firestore Cloud History 0.80 confidence $\rightarrow$ Tier 3: Geofence 0.70 confidence).
    - **Mutual Safety Buddy Bilateral Pairing (`SafetyBuddyRepository.kt`)**:
      - Dispatches buddy requests to `buddyRequests/{targetUserId}/incoming/{requestingUserId}` with real-time Firestore listeners.
      - Target colleague receives in-app pairing card with Accept and Decline actions.
      - Acceptance establishes bilateral active agreements in `safetyBuddies/{userId}/agreements/{buddyId}` with instant status badges ("Paired ✓") and direct calling shortcuts.
    - **Zero Mock Data**: Purged all hardcoded test names (Priya, Ananya, Sneha, Vikram) and removed simulator buttons.

  - **3. Smart Emergency Override System & Fall Detection (SLEEPING Mode)**:
    - **Sensor Manager Application Startup**: Initialized `EmergencyOverrideManager`, `RoutineAnalyzer`, and `ColleagueAwarenessManager` directly in `WomenSafetyApp.kt` upon process creation, ensuring sensor listeners and telephony receivers are always active.
    - **Hardware Accelerometer Diagnostic Screen (`AccelerometerDiagnosticScreen.kt`)**: Live diagnostic tool streaming real-time X/Y/Z acceleration, raw magnitude $\sqrt{X^2 + Y^2 + Z^2}$, gravity-adjusted acceleration $\sqrt{X^2+Y^2+Z^2} - 9.8$, 10-second rolling waveform canvas, and 50ms throttled logcat output.
    - **3-Phase Fall Detection Engine (`HybridFallDetectionManager.kt`)**:
      - **Phase A (Free-Fall)**: Detects near-weightlessness (gravity-adjusted acceleration $< 3.0\text{ m/s}^2$) for 0.5–1.0s.
      - **Phase B (Impact Spike)**: Detects violent impact deceleration ($> 25.0\text{ m/s}^2$) occurring within 800ms of free-fall.
      - **Phase C (Immobility)**: Confirms post-impact resting state ($< 2.0\text{ m/s}^2$).
      - **10-Second Countdown Grace Period**: Triggers an urgent alert dialog with 10-second audible countdown before auto-dispatching SOS with GPS coordinates.
    - **Telephony 3-Call Emergency Override (`EmergencyCallOverrideManager.kt`)**: Hardware `PhoneStateListener` / telephony callback monitoring incoming calls from configured emergency contacts. Detects 3 calls within a 5-minute sliding window, automatically overriding DND/silent mode to `AudioManager.RINGER_MODE_NORMAL` with maximum volume and launching a full-screen wakeup alert.
    - **Trusted Contact Emergency SOS Wakeup**: Listens for active SOS alerts triggered by paired contacts and immediately awakens the sleeping user.

- **Automatic Mode Suggestions**: Suggests mode changes based on saved workplace/home geofences, nighttime hours, and transit speed.
- **RiskAssessmentEngine**: Transparent, rule-based 0-100 risk scoring algorithm evaluating:
  - Route deviation (+30 to +35 pts)
  - No response to deviation alerts (+50 pts)
  - Unexpected prolonged stops (+40 pts)
  - Routine spatiotemporal anomalies (+15 to +40 pts depending on severity)
  - Sensor shake events (+30 pts)
  - Late-night hours 10 PM – 5 AM (+15 pts)
  - Safety Mode sensitivity multiplier (`NORMAL` = 1.0x, `CAUTIOUS` = 1.3x, `HIGH_RISK` = 1.6x)
- **AreYouSafeScreen**: Proactive check-in overlay with pulsating alert glow, detected anomaly breakdown, 30-second audio countdown timer, "I'm Safe" verification, and instant SOS escalation.
- **Persistence**: Firestore collections `/routines`, `/anomalies`, `/colleagues`, `/safetyBuddies`, `/lateStayAlerts`, `/sleepSessions`, `/emergencyOverrideConfig`, `/fallDetections`, and `/riskEvents`.

### 11. Phase 7: BLE Smartwatch Integration & Heart Rate Panic Telemetry
- **Direct BLE Smartwatch GATT Manager (`BleHeartRateManager.kt`)**:
  - Scans, connects, and streams live heart-rate measurements from smartwatches and fitness bands exposing the standard Bluetooth SIG Heart Rate Service (`0x180D`) and Measurement Characteristic (`0x2A37`).
  - Low-latency BLE scanning (`SCAN_MODE_LOW_LATENCY`) with 15-second auto-stop battery optimization.
  - Truthful, diagnosable GATT service and characteristic discovery explorer.
  - Safe disconnect and reconnect handling with automatic state updates (`BleConnectionState`).
- **Google Health Connect Integration (`HealthConnectManager.kt`)**:
  - Dual-source architecture reading continuous resting and background heart rate records via Android Health Connect API (`READ_HEART_RATE`).
  - Data freshness classification: `FRESH` (<10 minutes old) vs. `STALE` (≥10 minutes old).
  - Deduplication and origin attribution across installed health providers (e.g., Google Fit, Samsung Health, Zepp Life).
- **Interactive Health Screen (`HeartRateMonitoringScreen.kt`)**:
  - 3-tab Material 3 design:
    - **Tab 0: Health Connect**: Permission rationale launcher, historical query window selectors (4h, 24h, 7d), query summaries, and recent measurement timeline.
    - **Tab 1: Direct BLE Watch**: Real-time scanner, Bluetooth device list, live connection status, animated pulsating heart indicator, live BPM telemetry card, and discovered GATT services viewer.
    - **Tab 2: Test Harness / Capstone Simulator**: Interactive scenario testing demonstrating panic/tachycardia response.
- **Risk Assessment Integration**:
  - `RiskAssessmentEngine` factors tachycardia and sudden panic heart rate spikes (>120 BPM or abnormal surge) as a high-risk indicator (+4 pts in Capstone scenario), escalating composite risk alongside route deviation and late-night context.
- **Home Screen Telemetry Card**:
  - Dedicated live tile on `UserHomeScreen.kt` displaying current watch connection status, live BPM pulse indicator, and one-tap shortcut to the Health Monitoring Screen.
- **Automated Unit Testing (`HeartRateMonitoringTest.kt`)**:
  - Comprehensive unit test suite covering health data freshness boundaries, deduplication, panic threshold classification, and risk engine scenario evaluations.

### 12. Diagnostic & Testing Tools
- **LocationTestScreen**: Real-time GPS diagnostics showing latitude, longitude, altitude, accuracy, speed, bearing, provider status, OpenStreetMap readiness, and live map preview fix.
- **MapTestScreen**: OpenStreetMap (OSMDroid) rendering validation for markers, camera controls, and zoom levels.
- **BleDiscoveryTestScreen (`BleDiscoveryTestScreen.kt`)**: Dedicated BLE colleague diagnostic screen with 30s scan controls, active beacon advertising status, live discovered colleague cards with RSSI and distance telemetry, and live logcat window.
- **Battery Telemetry Engine (`BatteryMonitoringManager.kt`)**: Periodic 5-minute background telemetry collector monitoring battery percentage, charging state, temperature, voltage, and health, synced to Firestore (`/deviceBatteryStatus/{userId}`).
- **Unified SOS Trigger Pipeline (`SOSTriggerUtils.kt`)**: Centralized emergency escalation handler unifying manual SOS, route deviations, and unacknowledged fall detection events into a single resilient pipeline.
- **All-Mode Fall Detection with Sleep Muting (`HybridFallDetectionManager.kt`)**: Runs 3-phase nocturnal and daytime fall detection continuously across Regular, Working, and Travel modes while automatically muting false positives during Sleep Mode.
- **Dynamic Travel Route Simulator (`ActiveJourneyScreen.kt`)**: Route simulation engine dynamically calculating on-path progression and perpendicular route deviations (>300m) relative to actual navigation waypoints using `MapUtils`.
- **Capstone Scenario Simulator**: In-app test harness simulating composite risk conditions (Route Deviation + Late Night + Heart Rate Panic Spike) to verify automated safety escalation.

### 13. User Profile & Home UI Enhancements
- **UserHomeScreen**: Integrated Safety Mode quick-selector card, active journey banner, Smartwatch / BLE Heart Rate telemetry tile, quick-action tiles (Safe Travel, Shake Settings, Contacts, SOS History), live status indicators, risk score pill.
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
- **SMS Gateway**: 2Factor SMS REST API (Indian Telecom Gateway with dedicated V1 & R1 transactional endpoints)
- **Mapping & Routing**: OpenStreetMap (`org.osmdroid:osmdroid-android:6.1.18`), OSRM Driving Engine, Google Play Services Location (`play-services-location` for device GPS fix)
- **Health & Wearables**: Bluetooth Low Energy (BLE GATT SIG Heart Rate Service `0x180D`), Android Health Connect API (`androidx.health.connect:connect-client:1.1.0-alpha11`)
- **Permissions**: Accompanist Permissions & Android 14 Health Permissions Controller
- **Testing & Tooling**: JUnit 4, Kotlinx Coroutines Test, Automated Build & Diagnostics (`rebuild.bat`, `verify.bat`, `diagnose.ps1`)

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
**Fix**: Use `module=TRANS_SMS`. Both `TwoFactorClient.kt` (direct fallback) and `twoFactorHelper.ts` (Cloud Functions) use `TRANS_SMS`.

### 4. "Invalid API Key or TRANSACTIONAL_SMS Service is inactive" Error
**Root Cause**:
1. The API key contains placeholder characters (e.g., `7e825d24-XXXX-XXXX...` or `YOUR_API_KEY_HERE`).
2. Or the account's custom DLT Sender ID (`WSAFETY`) or Transactional Route is pending approval on 2Factor.
**Fix**:
1. Add your real 2Factor API Key from [https://www.2factor.in/](https://www.2factor.in/) to `local.properties`:
   ```properties
   TWOFACTOR_API_KEY=your_actual_2factor_api_key_here
   TWOFACTOR_SENDER_ID=WSAFETY
   ```
2. For Cloud Functions, configure runtime config:
   ```bash
   firebase functions:config:set twofactor.api_key="your_key" twofactor.sender_id="WSAFETY"
   ```
3. `TwoFactorClient.kt` includes an automatic fallback to 2Factor's Open OTP route (`https://2factor.in/API/V1/{API_KEY}/SMS/{PHONE}/{OTP}`), which delivers OTPs immediately using 2Factor's pre-approved template without waiting for DLT approval!

### 5. Monitoring Function Logs
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
│       │   ├── health/ (HealthConnectManager, HeartRateData models)
│       │   ├── model/ (User, TrustedContact, SOSEvent, JourneyRecord, etc.)
│       │   ├── repository/ (AuthRepository, LocationRepository, RelationshipRepository, etc.)
│       │   └── sms/ (TwoFactorClient direct REST fallback & SIM dispatcher)
│       ├── manager/
│       │   ├── BleHeartRateManager (BLE GATT SIG Heart Rate 0x180D scanner & telemetry)
│       │   ├── DirectionsApiHelper (OSRM road routing & GeoJSON decoding)
│       │   ├── RiskAssessmentEngine (Composite 0-100 risk scoring & Capstone scenarios)
│       │   ├── SafetyMonitoringManager (Background watchdog & anomaly coordinator)
│       │   ├── SOSManager (SOS siren, GPS lock, 2Factor SMS dispatch, auto-call)
│       │   ├── TravelModeManager (Journey state machine & deviation monitor)
│       │   └── TrinetraLocationManager (High-accuracy GPS provider)
│       ├── receiver/ (SafetyNotificationActionReceiver)
│       ├── service/ (LocationTrackingService, ShakeDetectionService)
│       ├── ui/
│       │   ├── auth/ (UserSignupStep*, TCAuthScreen, OTPVerificationScreen)
│       │   ├── health/ (HeartRateMonitoringScreen 3-tab UI)
│       │   ├── home/ (UserHomeScreen with HR telemetry card, TCHomeScreen)
│       │   ├── map/ (SafetyMapTracker OSM wrapper, LiveLocationMapScreen)
│       │   ├── navigation/ (NavGraph, Screen destinations)
│       │   ├── setup/ (5-step Setup Wizard)
│       │   ├── sos/ (SOSAlertScreen, SOSReceivedScreen, SOSEventMapScreen)
│       │   └── travel/ (TravelModeScreen, ActiveJourneyScreen)
│       ├── utils/ (MapUtils, DateUtils, NotificationChannelManager, Validators)
│       └── viewmodel/ (AuthViewModel, OTPViewModel, HeartRateMonitoringViewModel, LocationViewModel, SOSViewModel)
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
├── rebuild.bat / rebuild.sh (automated APK rebuild & verification)
├── verify.bat / verify.sh (syntax & structure verifier)
├── diagnose.ps1 / diagnose.sh (diagnostic scripts)
├── firestore.rules (security rules)
└── README.md
```

---

## 📂 Git Branch Reference

- **`feature/ble-integration-fix`** *(Current Active Branch)*:
  - Direct Bluetooth Low Energy (BLE) Smartwatch & Fitness Band Heart Rate integration (`BleHeartRateManager.kt`).
  - Google Health Connect API integration (`HealthConnectManager.kt`).
  - Interactive 3-tab Health Monitoring UI (`HeartRateMonitoringScreen.kt`) with live BPM pulse, GATT explorer, and Capstone Scenario test harness.
  - Integration with `RiskAssessmentEngine` for composite panic/tachycardia escalation (+4 pts).
  - OpenStreetMap (OSMDroid) & OSRM road routing (100% free, zero Google Maps API key or billing required).
  - 2Factor SMS API Gateway integration (dedicated V1 & R1 transactional endpoints with dual-SIM local fallback).
  - Pre-built debug APK included at `apk/WomenSafety-debug.apk`.
- **`otp-update`**:
  - 2Factor SMS OTP verification & Cloud Functions deployment baseline.
- **`map-update`**:
  - OpenStreetMap (OSMDroid) migration baseline without Google Maps billing.
- **`trail2`**:
  - Clean Architecture consolidation and merged collaborator modules.
- **`main`**:
  - Baseline production branch.

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:
1. Fork the repository and create a feature branch.
2. Commit your changes with clear, descriptive commit messages.
3. Test all mobile flows and Cloud Functions locally.
4. Open a pull request against the `main` branch.

## 📄 License

MIT License — see LICENSE for details.