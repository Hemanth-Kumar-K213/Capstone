@echo off
echo 🔧 Women Safety System - APK Rebuild with Correct API Key
echo ===========================================================
echo.

REM Step 1: Verify local.properties has REAL key
echo 📋 Checking local.properties...
findstr /M "869bfc1e-c4c7-11f1-af74-0200cd936042" local.properties >nul
if errorlevel 1 (
    echo ❌ ERROR: Real API key NOT found in local.properties
    echo Please fix local.properties with: TWOFACTOR_API_KEY=869bfc1e-c4c7-11f1-af74-0200cd936042
    exit /b 1
)
echo ✅ CORRECT API key found in local.properties

set JAVA_HOME=C:\Users\Amogh\.jdks\jbr-21.0.11

REM Step 2: Clean build
echo.
echo 🧹 Cleaning previous builds...
call gradlew.bat clean

REM Step 3: Build APK
echo.
echo 🔨 Building debug APK with correct API key...
call gradlew.bat assembleDebug --no-daemon

REM Step 4: Copy to apk/ folder
echo.
echo 📦 Copying APK to apk/ directory...
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    copy /y "app\build\outputs\apk\debug\app-debug.apk" "apk\WomenSafety-debug.apk"
    echo ✅ APK copied successfully
    dir apk\WomenSafety-debug.apk
) else (
    echo ❌ ERROR: APK not found after build
    exit /b 1
)

REM Step 5: Git status
echo.
echo 📋 Git status:
git status

echo.
echo ✅ BUILD COMPLETE
echo.
echo Next steps:
echo 1. Review changes: git diff
echo 2. Commit: git add . ^&^& git commit -m "fix(sms): restore real 2Factor API key 869bfc1e-c4c7-11f1-af74-0200cd936042"
echo 3. Push: git push origin otp-update
echo 4. Install: adb install -r apk\WomenSafety-debug.apk
echo 5. Test: Open app, sign up, send OTP - SMS should arrive in 5-10 seconds
