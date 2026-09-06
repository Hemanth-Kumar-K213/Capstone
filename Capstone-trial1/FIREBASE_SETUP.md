# Firebase Setup Guide — Women Safety System

## 1. Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click **Add project**
3. Name it `WomenSafetySystem`
4. Enable Google Analytics (optional)
5. Click **Create project**

## 2. Register Android App

1. In Firebase Console → **Project settings** → **Add app** → **Android**
2. Package name: `com.womensafety.system`
3. App nickname: `Women Safety System`
4. Get your **SHA-1** fingerprint:
   ```bash
   cd android-project-root
   ./gradlew signingReport
   ```
   Or from Android Studio: Gradle → app → Tasks → android → signingReport
5. Download `google-services.json`
6. Place it in `app/google-services.json`

## 3. Enable Authentication Providers

In Firebase Console → **Authentication** → **Sign-in method**:

### Email/Password
- Enable **Email/Password**
- Enable **Email link (passwordless sign-in)** for OTP login

### Phone
- Enable **Phone**
- Add test phone numbers under **Phone numbers for testing**:

| Phone Number | Verification Code |
|---|---|
| +91 6505550101 | 123456 |
| +91 6505550102 | 123456 |
| +91 6505550103 | 123456 |
| +91 6505550104 | 123456 |
| +91 6505550105 | 123456 |

> **IMPORTANT**: These test numbers are ONLY configured in Firebase Console. They are NOT hardcoded in the app.

## 4. Set Up Cloud Firestore

1. Firebase Console → **Firestore Database** → **Create database**
2. Start in **production mode**
3. Choose nearest region
4. Deploy the security rules from `firestore.rules`

## 5. Deploy Cloud Functions

```bash
cd firebase/functions
npm install
cd ..
firebase deploy --only functions
```

This deploys:
- `onUserCreated` — generates first weekly UID on signup
- `rotateWeeklyUids` — scheduled weekly UID rotation

## 6. Enable Firebase Cloud Messaging (Future)

1. Firebase Console → **Cloud Messaging**
2. FCM is auto-enabled; no action needed for V1
3. Will be activated in V2 for permission-request notifications

## 7. Firebase App Check (Future)

1. Firebase Console → **App Check**
2. Register your app with Play Integrity (Android)
3. Will be enforced in production deployment

## 8. SHA-1 / SHA-256 Fingerprints

Phone Auth requires SHA fingerprints. Add both debug and release:

```bash
# Debug SHA-1
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android

# Release SHA-1 (when ready)
keytool -list -v -keystore your-release-key.keystore -alias your-alias
```

Add these in Firebase Console → **Project settings** → **Your apps** → **SHA certificate fingerprints**
