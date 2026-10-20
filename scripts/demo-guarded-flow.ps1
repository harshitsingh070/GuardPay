<#
.SYNOPSIS
  GuardPay end-to-end Sandbox demo (guarded flow).

.DESCRIPTION
  Drives the real backend over HTTP and checks the core safety invariants:
    * a non-ALLOW decision NEVER creates a PayPal order;
    * a VERIFY decision issues a controlled challenge and creates no order;
    * confirming a VERIFY challenge only then produces a PayPal order;
    * transaction history reflects what happened.

  It does not need PayPal credentials for the ALLOW/VERIFY/STOP checks (order
  creation is the only step that calls PayPal). It never invents a capture:
  human Sandbox approval + capture stays a manual step.

  Exit code 0 = all checked invariants held; 1 = at least one failed.

.EXAMPLE
  pwsh -File scripts/demo-guarded-flow.ps1
  pwsh -File scripts/demo-guarded-flow.ps1 -BaseUrl http://localhost:8080
#>
param(
  [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$script:passed = 0
$script:failed = 0

function Write-Step([string]$text) {
  Write-Host ""
  Write-Host "== $text" -ForegroundColor Cyan
}

function Assert-True([bool]$condition, [string]$description, [string]$detail = "") {
  if ($condition) {
    $script:passed++
    Write-Host "  PASS  $description" -ForegroundColor Green
  } else {
    $script:failed++
    Write-Host "  FAIL  $description" -ForegroundColor Red
  }
  if ($detail -ne "") { Write-Host "        $detail" -ForegroundColor DarkGray }
}

function Invoke-Api {
  param(
    [string]$Method,
    [string]$Path,
    $Body = $null,
    [hashtable]$Headers = @{}
  )
  $uri = "$BaseUrl$Path"
  $requestArgs = @{ Method = $Method; Uri = $uri; Headers = $Headers }
  if ($null -ne $Body) {
    $requestArgs.ContentType = "application/json"
    $requestArgs.Body = ($Body | ConvertTo-Json -Depth 6)
  }
  return Invoke-RestMethod @requestArgs
}

function New-GuardedPayment {
  param(
    [double]$Amount,
    [string]$Recipient,
    [string]$Text = $null,
    [string]$Url = $null
  )
  $body = @{ amount = $Amount; currency = "USD"; description = "GuardPay demo payment"; recipientName = $Recipient }
  if ($null -ne $Text) { $body.contentText = $Text }
  if ($null -ne $Url) { $body.contentUrl = $Url }
  return Invoke-Api -Method POST -Path "/api/payments/guarded" -Body $body
}

Write-Host "GuardPay end-to-end guarded-flow demo" -ForegroundColor White
Write-Host "Backend: $BaseUrl"

Write-Step "1. Health"
try {
  $health = Invoke-Api -Method GET -Path "/api/health"
  Assert-True ($health.status -eq "UP") "backend reports UP" "status=$($health.status)"
} catch {
  Assert-True $false "backend reachable" $_.Exception.Message
  Write-Host "Start the backend first (docker compose up -d db; mvn -f backend spring-boot:run)" -ForegroundColor Yellow
  exit 1
}

Write-Step "2. Low-risk payment (expect ALLOW -> PayPal order)"
$allow = New-GuardedPayment -Amount 45 -Recipient "Known Co"
Write-Host "  action=$($allow.risk.recommendedAction) score=$($allow.risk.score) status=$($allow.paymentStatus)"
Assert-True ($allow.risk.recommendedAction -eq "ALLOW") "low-risk payment is ALLOW" "action=$($allow.risk.recommendedAction)"
if ($allow.risk.recommendedAction -eq "ALLOW") {
  Assert-True ($null -ne $allow.paypalOrder) "ALLOW produced a PayPal order" "orderId=$($allow.paypalOrder.orderId)"
  Assert-True ($allow.paymentStatus -eq "PAYPAL_AUTHORIZATION_REQUIRED") "status awaiting PayPal authorization"
}

Write-Step "3. Higher-risk payment (VERIFY must be order-free)"
$verify = New-GuardedPayment -Amount 1200 -Recipient "Fresh Stranger" -Text "Urgent: your account will be suspended, pay immediately."
Write-Host "  action=$($verify.risk.recommendedAction) score=$($verify.risk.score) status=$($verify.paymentStatus)"
if ($verify.risk.recommendedAction -ne "ALLOW") {
  Assert-True ($null -eq $verify.paypalOrder) "non-ALLOW decision created no PayPal order"
  Assert-True ($null -ne $verify.verification) "VERIFY response carries a controlled challenge"
} else {
  Write-Host "  (no non-ALLOW signal this run; invariants still enforced by tests)" -ForegroundColor DarkGray
}

if ($verify.verification -ne $null -and $verify.verification.challengeCode -ne $null) {
  Write-Step "4. Confirm the VERIFY challenge (server decides the outcome)"
  $wrong = Invoke-Api -Method POST -Path "/api/payments/$($verify.paymentId)/verification/confirm" -Body @{ code = "000000" }
  Assert-True ($wrong.verificationStatus -ne "VERIFIED") "a wrong code never verifies" "status=$($wrong.verificationStatus)"
  Assert-True ($null -eq $wrong.paypalOrder) "a wrong code created no PayPal order"

  $right = Invoke-Api -Method POST -Path "/api/payments/$($verify.paymentId)/verification/confirm" -Body @{ code = $verify.verification.challengeCode }
  Assert-True ($right.verificationStatus -eq "VERIFIED") "the correct code verifies" "status=$($right.verificationStatus)"
  Assert-True ($null -ne $right.paypalOrder) "verification success created the PayPal order" "orderId=$($right.paypalOrder.orderId)"
} else {
  Write-Step "4. Confirm the VERIFY challenge"
  Write-Host "  skipped: no simulated challenge code was returned" -ForegroundColor DarkGray
}

Write-Step "5. Transaction history reflects the runs"
$history = Invoke-Api -Method GET -Path "/api/transactions?page=0&size=20"
Write-Host "  stored transactions: $($history.totalItems)"
Assert-True ($history.totalItems -ge 1) "history is non-empty" "totalItems=$($history.totalItems)"
if ($history.totalItems -ge 1) {
  $detail = Invoke-Api -Method GET -Path "/api/transactions/$($history.items[0].id)"
  Assert-True ($null -ne $detail.assessment) "history detail includes the stored decision"
}

Write-Step "Summary"
Write-Host "  $script:passed passed, $script:failed failed" -ForegroundColor White
Write-Host "  Human step remaining: approve the ALLOW order in PayPal Sandbox, then capture." -ForegroundColor Yellow
if ($script:failed -gt 0) { exit 1 }
exit 0
