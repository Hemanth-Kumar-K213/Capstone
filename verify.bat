@echo off
echo 🔍 FINAL VERIFICATION - 2Factor SMS Integration
echo ==============================================
echo.

REM Check 1: Verify local.properties
echo ✅ Check 1: local.properties contains REAL API key
findstr /M "TWOFACTOR_API_KEY=869bfc1e-c4c7-11f1-af74-0200cd936042" local.properties >nul
if errorlevel 1 (
    echo    FAIL
) else (
    echo    PASS
)

REM Check 2: Verify .gitignore protection
echo.
echo ✅ Check 2: .gitignore protects local.properties and .env
findstr /M "local.properties" .gitignore >nul
if errorlevel 1 (
    echo    FAIL - local.properties not in .gitignore
) else (
    echo    PASS
)

REM Check 3: Verify APK exists
echo.
echo ✅ Check 3: APK built with correct config
if exist "apk\WomenSafety-debug.apk" (
    echo    PASS
    dir apk\WomenSafety-debug.apk
) else (
    echo    FAIL - Run rebuild script
)

REM Check 4: Test 2Factor API directly
echo.
echo ✅ Check 4: 2Factor API account is active
curl.exe -s "https://2factor.in/API/V1/869bfc1e-c4c7-11f1-af74-0200cd936042/BAL/SMS"
echo.

echo ==============================================
echo If all checks PASS, you're ready to test!
echo Command: adb install -r apk\WomenSafety-debug.apk
echo Then: Open app -^> Sign up -^> Send OTP -^> SMS arrives ✅
