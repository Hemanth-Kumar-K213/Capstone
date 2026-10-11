# 🌐 Women Safety System — Public Safety UID Web Dashboard

An ultra-secure, privacy-first, real-time safety and location monitoring dashboard for **Trusted Contacts / Guardians**. Accessible **ONLY using a rotating 12-digit Safety UID** (`YYYYMMDDXXXX`) — **zero logins, zero account passwords, zero registration overhead**.

---

## 🚀 Key Features

- 🔑 **Zero-Authentication Access**: Guardians simply enter the 12-digit UID shared by the user.
- 🔄 **7-Day Auto-Rotating UIDs**: Format `YYYYMMDDXXXX` ensures temporary, revocable access.
- 📍 **Real-Time GPS Tracking**: Powered by **Leaflet.js** and **OpenStreetMap** with dynamic pulsing accuracy rings.
- 🚨 **Instant SOS Beacon & Siren Alerts**: Live audio-visual feedback when panic triggers (voice keywords, falls, rapid power buttons, shake gestures) occur.
- 🚗 **Live Commute & Route Deviation Engine**: Monitors source, destination, estimated arrival time (ETA), and path deviations in real-time.
- 🛡️ **Situational Risk Assessment Score (0-100)**: Dynamically calculated based on battery levels, speed, night hours, and sensor alerts.
- 📞 **Direct Guardian Hotlines**: One-click dial and WhatsApp shortcuts plus national 112 emergency services.
- 🍞 **Breadcrumb Location History**: Inspect timestamped waypoints and speed telemetry.
- ⚡ **Offline & Demo Simulator Mode**: Built-in test UIDs to test active tracking, SOS alarms, and expiration without waiting for Firebase setup.

---

## 🛠️ Tech Stack

- **Frontend**: React 18, TypeScript, Vite, TailwindCSS
- **Mapping**: Leaflet.js, OpenStreetMap (Standard & Dark/Night cartography)
- **Icons**: Lucide React
- **Backend & Real-time Stream**: Firebase Firestore (`onSnapshot` listeners)
- **Deployment**: Firebase Hosting (SPA Rewrites enabled)

---

## 📦 Getting Started

### 1. Install Dependencies
```bash
npm install
```

### 2. Run Local Development Server
```bash
npm run dev
```
The app will be live at `http://localhost:3000`.

### 3. Build for Production
```bash
npm run build
```

---

## 🧪 Interactive Testing UIDs (Built-in Demo Suite)

You can immediately test the dashboard on the home page using these pre-configured Safety UIDs:

| UID | Status | Scenario |
|---|---|---|
| `202501153742` | 🟢 Active | Live Bengaluru Commute (`Ananya Sharma`), 78% battery, moving route |
| `202610109999` | 🔴 SOS Triggered | Urgent Panic Beacon (`Priya Nair`), route deviation detected |
| `202401010000` | ⚪ Expired | Demonstrates 7-day expiration error message |

---

## 🔒 Firestore Security Rules

Deploy the included `firestore.rules` to secure your Firestore collections:

```bash
firebase deploy --only firestore:rules
```

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function isValidUID(uid) {
      return exists(/databases/$(database)/documents/weeklyUids/$(uid)) &&
             get(/databases/$(database)/documents/weeklyUids/$(uid)).data.active == true &&
             get(/databases/$(database)/documents/weeklyUids/$(uid)).data.expiresAt > request.time.toMillis();
    }
    // ...
  }
}
```

---

## 🚢 Deploy to Firebase Hosting

```bash
# 1. Install Firebase CLI
npm install -g firebase-tools

# 2. Login & initialize
firebase login
firebase init hosting

# 3. Build & Deploy
npm run build
firebase deploy --only hosting
```

Your dashboard will be live on `https://your-project.web.app`!
