# Women Safety System (Android App)

A modern, native Android application built with Kotlin, Jetpack Compose, and Firebase that provides a secure safety network for women and their trusted contacts.

---

## 🏗 Architecture & Design Principles

The application strictly follows Clean Architecture and the Separation of Concerns principle:

```
UI Layer (Jetpack Compose Screens)
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

---

## ✅ Completed Features (Currently Implemented)

### 1. Authentication System
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
- **Add User via UID**: Trusted contacts can look up a User using their 12-digit Safety UID.
- **Request Approval / Denial / Revocation**: Users receive requests from Trusted Contacts and can approve, deny, or revoke access at any time.
- **Deterministic Document IDs**: Firestore relationships use `{userId}_{trustedContactId}` document IDs complying with security rules.
- **Permission Requests Screen**: Dedicated screen to review and manage incoming TC connection requests.

### 4. Dashboards & Profiles
- **User Home Dashboard**:
  - Displays active Safety UID, status, and expiry countdown
  - SOS button placeholder and quick actions
- **Trusted Contact Home Dashboard**:
  - Overview of connected users and their safety status
  - Add User by UID entry point
- **User Profile**:
  - View profile information (Name, Email, Phone)
  - Change phone number with in-place OTP verification
  - Trigger password reset email
  - View approved Trusted Contacts and pending requests
- **Trusted Contact Profile**:
  - View TC profile info and account status
  - Logout support

### 5. UI & Design System
- **Material 3 Dark Theme**:
  - Rich gradients (`GradientDeepPurple`, `GradientAmber`, `GradientRose`, `GradientGold`, `GradientMagenta`)
  - Glassmorphic card surfaces (`GlassCard`)
  - Consistent typography and status badges (`StatusApproved`, `StatusPending`, `StatusDenied`)
- **Navigation**:
  - Type-safe navigation routes using `@Serializable` objects (`Screen.kt`)
  - Bottom navigation bars (`BottomNavBar` for Users, `TCBottomNavBar` for Trusted Contacts)

---

### 6. Initial Setup Wizard (5 Screens)
- **Automatic Post-Signup Routing**: Shows immediately after User Signup Step 6 or upon first login when `setupCompleted == false`.
- **Screen 1: WelcomeSetupScreen**:
  - Hero card presentation of safety pillars (Location Tracking, Emergency SMS/Call, Smart SOS & Shake Detection).
  - "Get Started" and "Skip for Now" options.
- **Screen 2: PermissionsSetupScreen**:
  - Accompanist runtime permissions handling for Location, Background Location, SMS, Phone Call, and Notifications.
  - Interactive status badges (Granted / Denied) for each required permission.
- **Screen 3: EmergencyContactsSetupScreen**:
  - Add and delete emergency contacts with in-line dialog and phone number validation.
  - Enforces minimum 1 emergency contact before proceeding.
- **Screen 4: SOSPreferencesScreen**:
  - Location tracking toggle (default: true).
  - Shake detection toggle (default: false).
  - Shake sensitivity selector (LOW, MEDIUM, HIGH, default: MEDIUM).
  - Auto-call top emergency contact toggle (default: false).
- **Screen 5: SetupCompleteScreen**:
  - Animated checkmark and full configuration summary.
  - Commits all settings to Firestore, sets `setupCompleted = true`, and navigates to `UserHomeScreen`.

### 7. Emergency Contacts Management (3 Screens)
- **Screen 1: EmergencyContactsListScreen**:
  - Dedicated screen accessible from User Profile.
  - View all emergency contacts with addition dates, quick edit, and delete with confirmation dialog.
  - Floating Action Button (FAB) to add contacts.
- **Screen 2: AddEmergencyContactScreen**:
  - Clean form with full name and validated phone input (`Validators.isValidPhone`).
  - Generates UUID and current timestamp for each contact.
- **Screen 3: EditEmergencyContactScreen**:
  - Pre-filled form with existing contact details.
  - Real-time update in Firestore and delete capability.

### 8. User Profile Enhancements
- **Emergency Contacts Card**: Live count of SMS contacts with a direct navigation button to manage them.
- **Safety Preferences Section**: Direct toggles for Location Tracking, Shake Detection, Sensitivity dropdown, and Auto-Call that persist to Firestore.
- **Re-Run Setup Wizard Button**: Allows users to reconfigure their entire safety system anytime.

### 9. Phase 2: Location Tracking Service & Live Maps
- **LocationTrackingService**: Foreground service using FusedLocationProviderClient with WakeLock protection.
- **Dual Update Intervals**: 30 seconds for normal tracking, automatically boosted to 5 seconds during active SOS alerts.
- **Firestore Streaming**: Streams GPS coordinates, accuracy, speed, and addresses to `userLocations/{userId}/locations/{locationId}`.
- **LocationSettingsScreen**: Status card, update interval customization (5s-60s), and tracking control toggles.
- **LocationHistoryScreen**: Historical timeline of past location updates with SOS filtering and data cleanup options.
- **LiveLocationMapScreen**: Google Maps Compose interface for Trusted Contacts to view real-time location markers, accuracy rings, reverse geocoded addresses, and external navigation intents.

### 10. Phase 3: Shake Detection Service & Sensor Management
- **ShakeDetector**: High-precision accelerometer sensor listener with gravity subtraction and debounce filtering.
- **ShakeDetectionService**: Background foreground service with persistent status notification and 3-burst haptic vibration feedback.
- **Configurable Sensitivity**: LOW (20 m/s²), MEDIUM (15 m/s²), HIGH (12 m/s²).
- **ShakeDetectionSettingsScreen**: Interactive testing mode allowing users to safely test shake sensitivity without triggering actual SOS.

### 11. Phase 4: Complete SOS Alert System (Full Integration)
- **SOSManager Core Orchestration**:
  1. Requests immediate high-accuracy GPS fix with fallback to last known location.
  2. Creates `SOSEvent` in Firestore `sosEvents/{eventId}`.
  3. Activates loud siren audio on `STREAM_ALARM` (bypassing silent mode) and urgent vibration patterns.
  4. Switches location tracking service to 5-second interval.
  5. Formats and dispatches cellular SMS with live Google Maps coordinate links to all Emergency Contacts.
  6. Automatically places phone call to the primary emergency contact if `sosAutoCall` is enabled.
  7. Records delivery stats (FCM count, SMS count, Call status).
- **SafetyScreen**: Dedicated main tab in User navigation featuring a large circular SOS button with a 3-second countdown confirmation dialog.
- **SOSAlertScreen**: Full-screen emergency UI with pulsing red gradient, 5-minute auto-resolve countdown timer, siren mute toggle, real-time location snapshot, and "I'M SAFE" / "FALSE ALARM" resolution buttons.
- **SOSHistoryScreen & SOSHistoryDetailScreen**: Complete audit trail of past emergency events with event timelines, participant logs, and report export.
- **SOSReceivedScreen & FCM Messaging**: Handles high-priority push notifications on Trusted Contact devices with loud alerts and direct navigation to live maps.
- **Cloud Function**: Automated FCM multicast trigger on new `sosEvents/{eventId}` documents.

---

## 🔒 Security & Firestore Rules

All data access is guarded by strict Firestore security rules:
- Users can read/write their own profile and location history.
- Approved Trusted Contacts (verified via `relationships/{userId}_{tcId}`) can read location streams and active SOS events.
- Client cannot perform collection-wide scans or modify other users' emergency data.