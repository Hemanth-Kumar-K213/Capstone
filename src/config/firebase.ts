import { initializeApp, getApps, getApp } from 'firebase/app';
import { getFirestore } from 'firebase/firestore';
import { getAuth, signInAnonymously, onAuthStateChanged, Auth } from 'firebase/auth';

// Environment configuration from Capstone project
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "AIzaSyAZnnC1q4CvsRjpezhKAe_IMCvFAuZfIrA",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "women-safety-system-65893.firebaseapp.com",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "women-safety-system-65893",
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "women-safety-system-65893.firebasestorage.app",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "503599125127",
  appId: import.meta.env.VITE_FIREBASE_APP_ID || "1:503599125127:android:253b41720843c6f8db9e22"
};

// Check if valid Firebase configuration is active
export const isFirebaseConfigured = Boolean(
  firebaseConfig.apiKey && 
  firebaseConfig.projectId
);

// Initialize Firebase safely
export const app = getApps().length > 0 ? getApp() : initializeApp(firebaseConfig);
export const db = getFirestore(app);
export const auth: Auth = getAuth(app);

// Silent Anonymous Sign-In: Satisfies Firestore 'isSignedIn()' security rules
// while keeping the portal completely zero-authentication for guardians (no login prompt)
export async function ensureFirebaseAuth(): Promise<void> {
  if (!isFirebaseConfigured) return;
  return new Promise((resolve) => {
    onAuthStateChanged(auth, async (user) => {
      if (user) {
        resolve();
      } else {
        try {
          await signInAnonymously(auth);
          resolve();
        } catch (err) {
          console.warn('Silent anonymous auth notice:', err);
          resolve();
        }
      }
    });
  });
}

// Automatically initiate silent auth on load
ensureFirebaseAuth();
