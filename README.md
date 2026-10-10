# Women Safety System (Android App) — Trinetra

A modern, native Android application built with Kotlin, Jetpack Compose, OpenStreetMap (OSMDroid), and Firebase that provides an intelligent, proactive safety network for women and their trusted contacts — 100% Free & Open-Source with zero billing or API keys required.

---

## 🏗 Architecture & Design Principles

The application strictly follows Clean Architecture and the Separation of Concerns principle:

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
Firebase SDKs (Auth, Firestore, Cloud Functions, Messaging)
```

### Architectural Constraints
- **Screens** never touch Firebase directly; they only interact with **ViewModels**.
- **ViewModels** never touch Firebase directly; they only interact with **Repositories**.
- **Repositories** never touch Firebase SDKs directly; they interact only with **FirebaseAuthManager** and **FirestoreManager**.
- **FirebaseAuthManager** and **FirestoreManager** are the **ONLY** classes that import Firebase SDKs.
- Background operations, location tracking, and sensors are coordinated via dedicated Managers and Foreground Services (`SafetyMonitoringManager`, `TravelModeManager`, `SOSManager`, `LocationTrackingService`, `ShakeDetectionService`).

---

## 🌟 Comprehensive Feature Overview

### 1. Authentication & Onboarding
- **User Authentication**:
  - 6-step guided signup flow:
    - Step 1: Full Name & Primary Email
    - Step 2: Password creation & confirmation (with strength checks)
    - Step 3: Phone Number input
    - Step 4: Phone OTP verification via Firebase Phone Auth
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
  4. Cellular SMS fallback with OpenStreetMap coordinates link dispatched to emergency contacts.
  5. Auto-places phone call to primary contact if enabled.
  6. Sends Firebase Cloud Messaging (FCM) high-priority push notifications to approved Trusted Contacts.
- **SOSAlertScreen**: Full-screen emergency UI with pulsing red glow, cancel timer, countdown, siren toggle, flashlight toggle, and manual cancellation PIN.
- **SOSReceivedScreen**: Receiver UI for Trusted Contacts displaying user location via OpenStreetMap `SafetyMapTracker`, trigger method, and direct calling.
- **SOSEventMapScreen**: Dedicated incident visualization screen showing exact alert GPS coordinates on OpenStreetMap with FCM, SMS, and call delivery logs.
- **SOSHistoryScreen & SOSHistoryDetailScreen**: Review past SOS events and audit logs.

---

## 🚀 Recent Updates & Enhancements (Collaborator Updates & OSM Migration)

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

## 🗺️ 100% Free Open-Source Mapping (OSMDroid & OSRM)

All mapping and navigation capabilities use **OpenStreetMap (OSMDroid)** and **OSRM Routing Engine**:
- **Zero API Key Requirement**: No Google Cloud Console billing setup or credit card required.
- **Components**:
  - `SafetyMapTracker.kt`: Jetpack Compose `AndroidView` wrapper over OSMDroid `MapView` with animated markers and auto-camera panning.
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
- **Backend & Database**: Firebase Auth (Email/Password + Phone OTP), Cloud Firestore, Firebase Cloud Messaging, Cloud Functions (TypeScript)
- **Mapping & Routing**: OpenStreetMap (`org.osmdroid:osmdroid-android:6.1.18`), OSRM Driving Engine, Google Play Services Location (`play-services-location` for device GPS fix)
- **Permissions**: Accompanist Permissions

---

## ⚙️ Development & IDE Setup Note

> **JDK Requirement:** Ensure your Gradle JDK in Android Studio is set to **Java 21**:
> - Open **Settings** (`Ctrl + Alt + S`) $\rightarrow$ **Build, Execution, Deployment** $\rightarrow$ **Build Tools** $\rightarrow$ **Gradle**.
> - Set **Gradle JDK** to **Java 21** (`jbr-21` located at `C:\Users\Amogh\.jdks\jbr-21.0.11`).
> - *(Avoid using Android Studio's bundled JDK 25 with Gradle 8.9 to prevent `Unsupported class file major version 69`).*