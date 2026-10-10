#!/bin/bash

echo "=== FIREBASE & 2FACTOR SYSTEM DIAGNOSTICS ==="

echo -e "\n1. Firebase CLI & Node.js Tools:"
which node || echo "Node.js not installed in PATH"
which npm || echo "npm not installed in PATH"
which firebase || echo "Firebase CLI not installed in PATH"

echo -e "\n2. Active Firebase Project:"
cat .firebaserc 2>/dev/null || echo ".firebaserc not found"

echo -e "\n3. App google-services.json Project ID:"
grep -i "project_id" app/google-services.json 2>/dev/null || echo "google-services.json not found"

echo -e "\n4. Cloud Functions lib/ files:"
ls -la firebase/functions/lib/ 2>/dev/null || echo "lib/ directory not found"

echo -e "\n5. Probing Cloud Functions Live Endpoint:"
curl -s -o /dev/null -w "us-central1 HTTP status: %{http_code}\n" https://us-central1-women-safety-system-65893.cloudfunctions.net/sendOTPFunction
curl -s -o /dev/null -w "asia-south1 HTTP status: %{http_code}\n" https://asia-south1-women-safety-system-65893.cloudfunctions.net/sendOTPFunction

echo -e "\n6. Probing 2Factor SMS Gateway:"
curl -s -o /dev/null -w "2Factor Gateway HTTP status: %{http_code}\n" https://2factor.in/API/R1/

echo -e "\n=== END DIAGNOSTICS ==="
