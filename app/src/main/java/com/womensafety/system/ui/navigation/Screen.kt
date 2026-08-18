package com.womensafety.system.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for the app.
 */

// ─── Pre-Auth Routes ─────────────────────────────────────────────
@Serializable object Splash
@Serializable object RoleSelection

// ─── User Auth Routes ────────────────────────────────────────────
@Serializable object UserLogin
@Serializable object UserSignupStep1
@Serializable object UserSignupStep2
@Serializable object UserSignupStep3
@Serializable object UserSignupStep4
@Serializable object UserSignupStep5
@Serializable object UserSignupStep6

// ─── Trusted Contact Auth Routes ─────────────────────────────────
@Serializable object TCLogin
@Serializable object TCSignup

// ─── User Main Routes ────────────────────────────────────────────
@Serializable object UserHome
@Serializable object UserSafety
@Serializable object UserContacts
@Serializable object UserProfile
@Serializable object ChangePhone
@Serializable object PermissionRequests

// ─── Trusted Contact Main Routes ─────────────────────────────────
@Serializable object TCHome
@Serializable object AddUser
@Serializable object TCProfile
