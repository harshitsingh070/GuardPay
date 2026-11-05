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
  pwsh -File scripts/demo-guarded-flow.ps1 -RecipientEmail sb-merchant@business.example.com
#>
param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$RecipientEmail = $env:DEMO_RECIPIENT_EMAIL,
  [string]$Email = $env:DEMO_EMAIL,
  [string]$Password = $env:DEMO_PASSWORD
)

if ([string]::IsNullOrWhiteSpace($RecipientEmail)) {
  $RecipientEmail = "sb-merchant@business.example.com"
}
if ([string]::IsNullOrWhiteSpace($Email)) {
  $Email = "guardpay-demo-runner@guardpay.local"
}
if ([string]::IsNullOrWhiteSpace($Password)) {
  $Password = "GuardPay-demo-123"
}

$ErrorActionPreference = "Stop"
$script:passed = 0
$script:failed = 0
$script:session = $null

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
  if ($null -ne $script:session) { $requestArgs.WebSession = $script:session }
  if ($null -ne $Body) {
    $requestArgs.ContentType = "application/json"
    $requestArgs.Body = ($Body | ConvertTo-Json -Depth 6)
  }
  return Invoke-RestMethod @requestArgs
}

function Connect-DemoAccount {
  $registerBody = @{
    email = $Email
    password = $Password
    displayName = "GuardPay Demo Runner"
  } | ConvertTo-Json

  try {
    $null = Invoke-RestMethod -Method POST -Uri "$BaseUrl/api/auth/register" `
      -ContentType "application/json" -Body $registerBody -SessionVariable session
    $script:session = $session
    return "registered"
  } catch {
    $loginBody = @{ email = $Email; password = $Password } | ConvertTo-Json
    try {
      $null = Invoke-RestMethod -Method POST -Uri "$BaseUrl/api/auth/login" `
        -ContentType "application/json" -Body $loginBody -SessionVariable session
      $script:session = $session
      return "signed in"
    } catch {
      throw "Authentication failed. Set DEMO_EMAIL/DEMO_PASSWORD to an existing account or clear the demo database. $($_.Exception.Message)"
    }
  }
}

function New-GuardedPayment {
  param(
    [double]$Amount,
    [string]$Recipient,
    [string]$RecipientEmail,
    [string]$Text = $null,
    [string]$Url = $null
  )
  $body = @{ amount = $Amount; currency = "USD"; description = "GuardPay demo payment"; recipientName = $Recipient; recipientEmail = $RecipientEmail }
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

Write-Step "2. Account (private workspace)"
try {
  $accountResult = Connect-DemoAccount
  Assert-True ($null -ne $script:session) "authenticated demo account" "$accountResult as $Email"
} catch {
  Assert-True $false "authenticated demo account" $_.Exception.Message
  exit 1
}

Write-Step "3. Low-risk payment (expect ALLOW -> PayPal order)"
$allow = New-GuardedPayment -Amount 45 -Recipient "Known Co" -RecipientEmail $RecipientEmail
Write-Host "  action=$($allow.risk.recommendedAction) score=$($allow.risk.score) status=$($allow.paymentStatus)"
Assert-True ($allow.risk.recommendedAction -eq "ALLOW") "low-risk payment is ALLOW" "action=$($allow.risk.recommendedAction)"
if ($allow.risk.recommendedAction -eq "ALLOW") {
  Assert-True ($null -ne $allow.paypalOrder) "ALLOW produced a PayPal order" "orderId=$($allow.paypalOrder.orderId)"
  Assert-True ($allow.paymentStatus -eq "PAYPAL_AUTHORIZATION_REQUIRED") "status awaiting PayPal authorization"
}

Write-Step "4. Higher-risk payment (VERIFY must be order-free)"
$verify = New-GuardedPayment -Amount 1200 -Recipient "Fresh Stranger" -RecipientEmail $RecipientEmail -Text "Urgent: your account will be suspended, pay immediately."
Write-Host "  action=$($verify.risk.recommendedAction) score=$($verify.risk.score) status=$($verify.paymentStatus)"
if ($verify.risk.recommendedAction -ne "ALLOW") {
  Assert-True ($null -eq $verify.paypalOrder) "non-ALLOW decision created no PayPal order"
  Assert-True ($null -ne $verify.verification) "VERIFY response carries a controlled challenge"
} else {
  Write-Host "  (no non-ALLOW signal this run; invariants still enforced by tests)" -ForegroundColor DarkGray
}

if ($verify.verification -ne $null) {
  Write-Step "5. Confirm the VERIFY challenge (server decides the outcome)"
  $challenge = $null
  try {
    $challenge = Invoke-Api -Method GET -Path "/api/dev/verifications/$($verify.paymentId)/code"
  } catch {
    Write-Host "  could not read the server-side dev outbox: $($_.Exception.Message)" -ForegroundColor DarkGray
  }
  if ($challenge -ne $null -and $challenge.code -ne $null) {
    $wrong = Invoke-Api -Method POST -Path "/api/payments/$($verify.paymentId)/verification/confirm" -Body @{ code = "000000" }
    Assert-True ($wrong.verificationStatus -ne "VERIFIED") "a wrong code never verifies" "status=$($wrong.verificationStatus)"
    Assert-True ($null -eq $wrong.paypalOrder) "a wrong code created no PayPal order"

    $right = Invoke-Api -Method POST -Path "/api/payments/$($verify.paymentId)/verification/confirm" -Body @{ code = $challenge.code }
    Assert-True ($right.verificationStatus -eq "VERIFIED") "the correct code verifies" "status=$($right.verificationStatus)"
    Assert-True ($null -ne $right.paypalOrder) "verification success created the PayPal order" "orderId=$($right.paypalOrder.orderId)"
  } else {
    Write-Host "  skipped: run this script on the backend host (or enable GUARDPAY_DEV_VERIFICATION_OUTBOX) so the loopback-only outbox can deliver the code." -ForegroundColor DarkGray
  }
} else {
  Write-Step "5. Confirm the VERIFY challenge"
  Write-Host "  skipped: this run produced no controlled challenge" -ForegroundColor DarkGray
}

Write-Step "6. Transaction history reflects the runs"
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

