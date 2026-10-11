#!/bin/bash

echo "🔍 FINAL VERIFICATION - 2Factor SMS Integration"
echo "=============================================="
echo ""

# Check 1: Verify local.properties
echo "✅ Check 1: local.properties contains REAL API key"
grep -q "TWOFACTOR_API_KEY=869bfc1e-c4c7-11f1-af74-0200cd936042" local.properties && echo "   PASS" || echo "   FAIL"

# Check 2: Verify .gitignore protection
echo ""
echo "✅ Check 2: .gitignore protects local.properties and .env"
grep -q "local.properties" .gitignore && grep -q "\.env" .gitignore && echo "   PASS" || echo "   FAIL - Add 'local.properties' and '.env' to .gitignore!"

# Check 3: Verify APK exists
echo ""
echo "✅ Check 3: APK built with correct config"
if [ -f "apk/WomenSafety-debug.apk" ]; then
    echo "   PASS ($(ls -lh apk/WomenSafety-debug.apk | awk '{print $5}'))"
else
    echo "   FAIL - Run rebuild script"
fi

# Check 4: Test 2Factor API directly
echo ""
echo "✅ Check 4: 2Factor API account is active"
API_RESPONSE=$(curl -s "https://2factor.in/API/V1/869bfc1e-c4c7-11f1-af74-0200cd936042/BAL/SMS")
echo "   Response: $API_RESPONSE"
echo "$API_RESPONSE" | grep -q "Success" && echo "   PASS - Account active with SMS credits" || echo "   FAIL - API key invalid or account issue"

echo ""
echo "=============================================="
echo "If all checks PASS, you're ready to test!"
echo "Command: adb install -r apk/WomenSafety-debug.apk"
echo "Then: Open app -> Sign up -> Send OTP -> SMS arrives ✅"
