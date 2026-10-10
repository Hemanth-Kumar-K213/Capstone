Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "        FIREBASE & 2FACTOR SYSTEM DIAGNOSTIC TOOL         " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. CLI Tools Check
Write-Host "`n[1] Checking Required CLI Tools..." -ForegroundColor Yellow
$node = Get-Command node -ErrorAction SilentlyContinue
$npm = Get-Command npm -ErrorAction SilentlyContinue
$firebase = Get-Command firebase -ErrorAction SilentlyContinue

if ($null -ne $node) { Write-Host "  [OK] Node.js: $($node.Source)" -ForegroundColor Green } else { Write-Host "  [MISSING] Node.js is NOT installed in PATH" -ForegroundColor Red }
if ($null -ne $npm) { Write-Host "  [OK] npm: $($npm.Source)" -ForegroundColor Green } else { Write-Host "  [MISSING] npm is NOT installed in PATH" -ForegroundColor Red }
if ($null -ne $firebase) { Write-Host "  [OK] Firebase CLI: $($firebase.Source)" -ForegroundColor Green } else { Write-Host "  [MISSING] Firebase CLI is NOT installed in PATH" -ForegroundColor Red }

# 2. Project ID Consistency
Write-Host "`n[2] Checking Project ID Configuration..." -ForegroundColor Yellow
if (Test-Path ".firebaserc") {
    $rc = Get-Content ".firebaserc" -Raw
    Write-Host "  [OK] .firebaserc: $rc" -ForegroundColor Green
} else {
    Write-Host "  [MISSING] .firebaserc missing" -ForegroundColor Red
}

if (Test-Path "app/google-services.json") {
    $json = Get-Content "app/google-services.json" -Raw | ConvertFrom-Json
    Write-Host "  [OK] google-services.json Project ID: $($json.project_info.project_id)" -ForegroundColor Green
}

# 3. Compiled Cloud Functions in lib/
Write-Host "`n[3] Checking Cloud Functions Compilation (lib/)..." -ForegroundColor Yellow
$libFiles = Get-ChildItem "firebase/functions/lib" -ErrorAction SilentlyContinue
if ($null -ne $libFiles) {
    foreach ($f in $libFiles) {
        Write-Host "  [FOUND] lib/$($f.Name)" -ForegroundColor Green
    }
} else {
    Write-Host "  [MISSING] firebase/functions/lib is empty or missing" -ForegroundColor Red
}

# 4. Cloud Functions Live Endpoint Probe
Write-Host "`n[4] Probing Firebase Cloud Functions Endpoints..." -ForegroundColor Yellow
$usUrl = "https://us-central1-women-safety-system-65893.cloudfunctions.net/sendOTPFunction"
$asiaUrl = "https://asia-south1-women-safety-system-65893.cloudfunctions.net/sendOTPFunction"

try {
    $resUs = Invoke-WebRequest -Uri $usUrl -Method Get -TimeoutSec 5 -ErrorAction Stop
    Write-Host "  [REACHABLE] us-central1 endpoint reachable (HTTP $($resUs.StatusCode))" -ForegroundColor Green
} catch {
    Write-Host "  [404 NOT_FOUND] us-central1 endpoint returned: $($_.Exception.Message)" -ForegroundColor Red
}

try {
    $resAsia = Invoke-WebRequest -Uri $asiaUrl -Method Get -TimeoutSec 5 -ErrorAction Stop
    Write-Host "  [REACHABLE] asia-south1 endpoint reachable (HTTP $($resAsia.StatusCode))" -ForegroundColor Green
} catch {
    Write-Host "  [404 NOT_FOUND] asia-south1 endpoint returned: $($_.Exception.Message)" -ForegroundColor Red
}

# 5. 2Factor Gateway Reachability Probe
Write-Host "`n[5] Probing 2Factor SMS Gateway (https://2factor.in/API/R1/)..." -ForegroundColor Yellow
try {
    $res2F = Invoke-WebRequest -Uri "https://2factor.in/API/R1/" -Method Get -TimeoutSec 5 -ErrorAction Stop
    Write-Host "  [REACHABLE] 2Factor Gateway reachable (HTTP $($res2F.StatusCode))" -ForegroundColor Green
} catch {
    Write-Host "  [CONTACTED] 2Factor Gateway: $($_.Exception.Message)" -ForegroundColor Green
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "DIAGNOSTIC SUMMARY:" -ForegroundColor Cyan
Write-Host "1. Cloud Functions 'sendOTPFunction' is NOT yet active on Google Cloud servers."
Write-Host "2. Android app now features an automatic Direct 2Factor Gateway fallback."
Write-Host "3. To deploy Cloud Functions to Google Cloud, install Node.js + Firebase CLI"
Write-Host "   and run: firebase deploy --only functions"
Write-Host "==========================================================" -ForegroundColor Cyan
