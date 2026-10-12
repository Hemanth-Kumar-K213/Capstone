#!/bin/bash

echo "🔧 Women Safety System - APK Rebuild with Correct API Key"
echo "=========================================================="
echo ""

# Step 1: Verify local.properties has REAL key
echo "📋 Checking local.properties..."
if grep -q "869bfc1e-c4c7-11f1-af74-0200cd936042" local.properties; then
    echo "✅ CORRECT API key found in local.properties"
else
    echo "❌ ERROR: Real API key NOT found in local.properties"
    echo "Please fix local.properties with: TWOFACTOR_API_KEY=869bfc1e-c4c7-11f1-af74-0200cd936042"
    exit 1
fi

# Step 2: Clean build
echo ""
echo "🧹 Cleaning previous builds..."
./gradlew clean

# Step 3: Build APK
echo ""
echo "🔨 Building debug APK with correct API key..."
export JAVA_HOME="${JAVA_HOME:-C:/Users/Amogh/.jdks/jbr-21.0.11}"
./gradlew assembleDebug --no-daemon

# Step 4: Copy to apk/ folder
echo ""
echo "📦 Copying APK to apk/ directory..."
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    cp app/build/outputs/apk/debug/app-debug.apk apk/WomenSafety-debug.apk
    echo "✅ APK copied successfully"
    ls -lh apk/WomenSafety-debug.apk
else
    echo "❌ ERROR: APK not found after build"
    exit 1
fi

# Step 5: Git status
echo ""
echo "📋 Git status:"
git status

echo ""
echo "✅ BUILD COMPLETE"
echo ""
echo "Next steps:"
echo "1. Review changes: git diff"
echo "2. Commit: git add . && git commit -m 'fix(sms): restore real 2Factor API key 869bfc1e-c4c7-11f1-af74-0200cd936042'"
echo "3. Push: git push origin otp-update"
echo "4. Install: adb install -r apk/WomenSafety-debug.apk"
echo "5. Test: Open app, sign up, send OTP - SMS should arrive in 5-10 seconds"
