# 2Factor SMS Integration Guide

## Overview
This document guides you through deploying and configuring the **2Factor SMS API** backend for the **Women Safety System**. 2Factor replaces Twilio, providing direct, reliable delivery to Indian mobile networks (+91) with zero SDK bloat and 250 free trial credits.

---

## 1. 2Factor Setup

### 1.1 Account Creation
1. Go to [https://www.2factor.in/](https://www.2factor.in/)
2. Sign up with your Email ID and Indian mobile number (+91).
3. Upon registration, you receive **250 free SMS credits**.
4. Copy your **API Key** from the 2Factor dashboard.

### 1.2 Sender ID & Template Approval
1. In the 2Factor Dashboard, navigate to:
   **Transactional SMS > Manage Sender IDs > Request Sender ID/Template**
2. Configure:
   - **Sender ID (Header)**: `WSAFETY` (6 uppercase letters)
   - **Template Text**: `Women Safety OTP: {#var#}. Valid for 10 minutes.`
3. Approval typically completes within 30–60 minutes during business hours.

---

## 2. Configuration

### Option A: Firebase Functions Runtime Config (Recommended for Production)
```bash
firebase functions:config:set \
  twofactor.api_key="YOUR_2FACTOR_API_KEY" \
  twofactor.sender_id="WSAFETY"
```

Verify your configuration:
```bash
firebase functions:config:get
```

### Option B: Local `.env` (Emulator & Local Dev)
In `firebase/functions/.env`:
```ini
TWOFACTOR_API_KEY=your_actual_api_key_here
TWOFACTOR_SENDER_ID=WSAFETY
TWOFACTOR_API_URL=https://2factor.in/API/R1/
```

---

## 3. Deployment

From the `firebase/functions` directory:
```bash
# 1. Install dependencies (axios)
npm install

# 2. Deploy OTP and SOS Cloud Functions
firebase deploy --only functions:sendOTPFunction,functions:verifyOTPFunction,functions:onSOSEventCreated
```

---

## 4. Endpoints & Architecture

### Callable Cloud Functions

| Function Name | Type | Input Parameters | Output | Description |
|---|---|---|---|---|
| `sendOTPFunction` | HTTPS Callable (`onCall`) | `{ phoneNumber: string, userName?: string }` | `{ success: boolean, message: string, expiresIn: 600, messageId: string }` | Generates 6-digit OTP, stores securely in Firestore `/otps/{phone}`, dispatches SMS via 2Factor |
| `verifyOTPFunction` | HTTPS Callable (`onCall`) | `{ phoneNumber: string, otp: string }` | `{ success: boolean, message: string, phoneNumber: string }` | Verifies submitted OTP against Firestore, handles rate limiting & lockouts (5 failed attempts = 15m lockout) |

### Background Triggers

| Trigger | Event | Action |
|---|---|---|
| `onSOSEventCreated` | Firestore `sosEvents/{eventId}.onCreate` | Automatically queries user's emergency contacts and sends immediate emergency SMS with GPS coordinates and live Google Maps link |

---

## 5. Mobile App Integration

The Android application (`com.womensafety.system`) interacts seamlessly with these functions via `FirebaseFunctions`:

- **OTP Screen**: `OTPViewModel` invokes `sendOTPFunction` and `verifyOTPFunction`.
- **SOS Button**: `SOSManager` creates a Firestore record in `sosEvents/{eventId}`, triggering `onSOSEventCreated` on the server automatically.
- No client-side SMS permissions or SMS Gateway credentials are needed on the phone!

---

## 6. Troubleshooting

- **"Invalid phone number"**: Phone number must be 10 digits (`9876543210`) or E.164 (`+919876543210`). The helper automatically normalizes it to `919876543210`.
- **"2Factor API credentials not configured"**: Run `firebase functions:config:set twofactor.api_key="..." twofactor.sender_id="WSAFETY"`.
- **"Sender ID not approved"**: Ensure your DLT-approved sender ID matches the `TWOFACTOR_SENDER_ID` variable.
- **Check Logs**:
  ```bash
  firebase functions:log
  ```
