<#
  FamilySafeTube setup wizard (no Android Studio, no command line needed).
  Started by START-HERE.bat (menu) or Rebuild-APK.bat (rebuild).
  Everything is ASCII on purpose: Windows PowerShell 5.1 mangles other characters.
#>
param([ValidateSet('menu', 'rebuild', 'everything')][string]$Mode = 'menu')

# Native tools (git, gh) write progress to stderr; we check exit codes ourselves instead of letting PowerShell throw.
$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
$OutDir  = Join-Path $Root 'APK'
$ErrFile = Join-Path $Root 'build-error.txt'
$KeyDir  = Join-Path $env:USERPROFILE 'FamilySafeTube-signing'
$script:Repo = $null
$script:User = $null

# ---------------------------------------------------------------- small helpers
function Say([string]$t, [string]$c = 'Gray') { Write-Host $t -ForegroundColor $c }
function Step([string]$t) { Write-Host ''; Write-Host "== $t" -ForegroundColor Cyan }
function Ok([string]$t) { Write-Host "   OK  $t" -ForegroundColor Green }

function Ask([string]$q, [string]$default = '') {
    $suffix = ''
    if ($default) { $suffix = " [$default]" }
    $a = Read-Host "$q$suffix"
    if ([string]::IsNullOrWhiteSpace($a)) { return $default }
    return $a.Trim()
}

function YesNo([string]$q, [bool]$defaultYes = $true) {
    $hint = 'y/N'
    if ($defaultYes) { $hint = 'Y/n' }
    $a = Read-Host "$q ($hint)"
    if ([string]::IsNullOrWhiteSpace($a)) { return $defaultYes }
    return $a.Trim().ToLower().StartsWith('y')
}

function Refresh-Path {
    $m = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $u = [Environment]::GetEnvironmentVariable('Path', 'User')
    $env:Path = "$m;$u"
    foreach ($d in @("$env:ProgramFiles\Git\cmd", "$env:ProgramFiles\GitHub CLI")) {
        if ((Test-Path $d) -and ($env:Path -notlike "*$d*")) { $env:Path = "$env:Path;$d" }
    }
}

function Has([string]$cmd) { return [bool](Get-Command $cmd -ErrorAction SilentlyContinue) }

# Runs gh and returns its text output, or $null if it failed.
function GhOut {
    $o = & gh @args 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    return (($o | Out-String).Trim())
}

# ---------------------------------------------------------------- tools
function Ensure-Tool([string]$cmd, [string]$wingetId, [string]$nice) {
    if (Has $cmd) { Ok "$nice is installed"; return }
    Say "   Installing $nice (about a minute, a permission window may appear)..."
    if (-not (Has 'winget')) {
        throw "Windows' installer 'winget' is missing. Open the Microsoft Store, install 'App Installer', then start this again. (Or install $nice by hand.)"
    }
    & winget install --id $wingetId -e --silent --accept-package-agreements --accept-source-agreements
    Refresh-Path
    if (-not (Has $cmd)) { throw "$nice was installed but Windows does not see it yet. Close this window and double-click START-HERE.bat again." }
    Ok "$nice installed"
}

function Ensure-Tools {
    Step 'Step 1 - tools (Git and the GitHub helper)'
    Refresh-Path
    Ensure-Tool 'git' 'Git.Git' 'Git'
    Ensure-Tool 'gh' 'GitHub.cli' 'GitHub CLI'
}

# ---------------------------------------------------------------- GitHub account
function Ensure-Login {
    Step 'Step 2 - sign in to GitHub'
    & gh auth status *> $null
    if ($LASTEXITCODE -ne 0) {
        Say '   A web page opens. Sign in to GitHub there and type the short code that appears in this window.' 'Yellow'
        & gh auth login --hostname github.com --git-protocol https --web --scopes 'repo,workflow'
        if ($LASTEXITCODE -ne 0) { throw 'GitHub sign-in did not finish. Run this again.' }
    }
    & gh auth setup-git 2>$null
    $script:User = GhOut api user --jq .login
    if (-not $script:User) { throw 'Could not read your GitHub name. Run this again.' }
    Ok "Signed in as $($script:User)"
}

function Ensure-GitIdentity {
    $n = (& git config user.name 2>$null)
    $e = (& git config user.email 2>$null)
    if (-not $n) { & git config --global user.name $script:User }
    if (-not $e) {
        $id = GhOut api user --jq .id
        & git config --global user.email "$id+$($script:User)@users.noreply.github.com"
    }
}

# ---------------------------------------------------------------- repository
function Read-RepoFromGit {
    $remote = (& git remote get-url origin 2>$null)
    if ($remote) {
        $script:Repo = ($remote -replace '^.*github\.com[:/]', '' -replace '\.git$', '').Trim()
        return $true
    }
    return $false
}

# Makes this folder's history fit on top of GitHub's, WITHOUT ever merging.
# The files in THIS folder (a fresh unzip) are the truth. If GitHub's latest commit is not already underneath, the branch
# is moved to GitHub's latest commit while every file in the folder stays exactly as it is. The next commit then holds
# exactly the difference, and the push is an ordinary fast-forward. Nothing is merged, so there is nothing to conflict
# (an earlier version used "git pull --rebase", which could leave <<<<<<< conflict markers inside files and push them).
function Sync-Remote {
    & git fetch origin main 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { return }          # GitHub repository is still empty
    & git merge-base --is-ancestor origin/main HEAD 2>$null
    if ($LASTEXITCODE -eq 0) { return }          # already on top of GitHub's history
    Say '   Attaching this folder to the existing GitHub history (the files in this folder are kept exactly as they are).' 'Gray'
    & git reset --soft origin/main
    if ($LASTEXITCODE -ne 0) { throw 'Could not attach this folder to the GitHub history.' }
}

# Safety net: never send a file that still contains merge-conflict markers (they break the build and mean something went wrong).
function Assert-NoConflictMarkers {
    $hits = @(& git grep --no-index --exclude-standard -n -I -E '^(<<<<<<<|>>>>>>>) ' -- . 2>$null)
    if ($hits.Count -gt 0) {
        Say '   These files contain merge-conflict markers and would break the build:' 'Red'
        $hits | Select-Object -First 8 | ForEach-Object { Say "     $_" 'Red' }
        throw 'This folder contains broken files. Unzip the newest FamilySafeTube.zip INTO this folder (replace all files), then run this again.'
    }
}

# Unzipping over a folder never deletes anything, so files from OLDER versions stay behind (an old workflow, old layouts,
# an old Drive engine ...) and break the build. Every zip lists the files it contains in setup\files.txt: files in the
# project's own folders that are NOT on that list are old leftovers. They are shown, and removed after a yes.
$script:StaleChecked = $false
function Remove-StaleFiles {
    if ($script:StaleChecked) { return }
    $script:StaleChecked = $true
    $list = Join-Path $Root 'setup\files.txt'
    if (-not (Test-Path $list)) { return }
    $keep = @{}
    foreach ($line in (Get-Content $list)) { $t = $line.Trim(); if ($t) { $keep[$t.ToLowerInvariant()] = $true } }
    if ($keep.Count -lt 50) { return }          # a damaged list must never delete anything
    $stale = @()
    foreach ($dir in @('app/src', '.github', 'gradle', 'setup', 'tools', 'docs')) {
        $full = Join-Path $Root ($dir -replace '/', '\')
        if (-not (Test-Path $full)) { continue }
        foreach ($f in (Get-ChildItem -Path $full -Recurse -File -Force)) {
            $rel = ($f.FullName.Substring($Root.Length).TrimStart('\', '/')) -replace '\\', '/'
            if (-not $keep.ContainsKey($rel.ToLowerInvariant())) { $stale += $rel }
        }
    }
    if ($stale.Count -eq 0) { return }
    Say "   $($stale.Count) old file(s) from earlier versions are still in this folder and would break the build, for example:" 'Yellow'
    $stale | Select-Object -First 12 | ForEach-Object { Say "     $_" 'Yellow' }
    $answer = Ask '   Remove them? (yes/no)' 'yes'
    if ($answer -notmatch '^(y|yes)$') { Say '   Left in place.' 'Yellow'; return }
    foreach ($rel in $stale) { Remove-Item -LiteralPath (Join-Path $Root ($rel -replace '/', '\')) -Force -ErrorAction SilentlyContinue }
    Ok "$($stale.Count) old file(s) removed"
}

function Commit-All([string]$message) {
    Remove-StaleFiles
    & git add -A
    Assert-NoConflictMarkers
    & git update-index --chmod=+x gradlew 2>$null     # Windows cannot mark it executable; the build machine needs that
    & git diff --cached --quiet
    if ($LASTEXITCODE -ne 0) { & git commit -m $message | Out-Null; return $true }
    return $false
}

function Ensure-Repo {
    Step 'Step 3 - your GitHub repository (where the app is built)'
    if (-not (Test-Path (Join-Path $Root '.git'))) { & git init -b main | Out-Null }
    & git config core.autocrlf false          # no line-ending conversion (and no warning spam)
    Ensure-GitIdentity

    if (Read-RepoFromGit) {
        Ok "Repository: $($script:Repo)"
        Sync-Remote
        [void](Commit-All 'FamilySafeTube')
        & git push -u origin main
        if ($LASTEXITCODE -ne 0) { throw 'Could not send the project to GitHub.' }
        Ok 'Project is on GitHub'
        return
    }

    $name = Ask 'Name for the repository' 'FamilySafeTube'
    Say '   Public: the app finds updates by itself (simplest, recommended).' 'Gray'
    Say '   Private: also works, but each phone then needs a read-only token in Settings > App updates > Advanced.' 'Gray'
    $private = YesNo 'Make it PRIVATE?' $false
    $full = "$($script:User)/$name"

    [void](Commit-All 'FamilySafeTube')
    & git branch -M main

    & gh repo view $full *> $null
    if ($LASTEXITCODE -eq 0) {
        Say "   $full already exists - adding this version on top of it." 'Gray'
        & git remote add origin "https://github.com/$full.git"
        Sync-Remote
        [void](Commit-All 'FamilySafeTube: new version')
        & git push -u origin main
    } else {
        $vis = '--public'
        if ($private) { $vis = '--private' }
        & gh repo create $full $vis --source . --remote origin --push
    }
    if ($LASTEXITCODE -ne 0) { throw 'Could not create or fill the repository on GitHub.' }
    [void](Read-RepoFromGit)
    Ok "Repository ready: $($script:Repo)"
}

# ---------------------------------------------------------------- signing key
function Find-Keytool {
    if (Has 'keytool') { return (Get-Command keytool).Source }
    $roots = @(
        "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft",
        "$env:ProgramFiles\Android\Android Studio\jbr"
    )
    foreach ($r in $roots) {
        if (Test-Path $r) {
            $k = Get-ChildItem $r -Recurse -Filter keytool.exe -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($k) { return $k.FullName }
        }
    }
    return $null
}

function Set-Secret([string]$name, [string]$value) {
    & gh secret set $name --body $value --repo $script:Repo | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not save the secret $name on GitHub." }
}

# Prints the key's SHA-1: Google Cloud needs it for the Android OAuth client (Google sign-in).
function Show-Fingerprint([string]$file, [string]$alias, [string]$storePw) {
    $kt = Find-Keytool
    if (-not $kt) { return }
    $out = (& $kt -list -v -keystore $file -storepass $storePw -alias $alias 2>$null | Out-String)
    if ($out -match 'SHA1:\s*([0-9A-Fa-f:]+)') {
        Say "   Signing key SHA-1 (for the Google sign-in client, see step 5): $($Matches[1])" 'Yellow'
    }
}

function Save-Signing([string]$file, [string]$alias, [string]$storePw, [string]$keyPw) {
    $b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($file))
    Set-Secret 'KEYSTORE_BASE64' $b64
    Set-Secret 'KEYSTORE_PASSWORD' $storePw
    Set-Secret 'KEY_ALIAS' $alias
    Set-Secret 'KEY_PASSWORD' $keyPw
    Ok 'Signing key saved in GitHub (as secrets only you can read)'
    Show-Fingerprint $file $alias $storePw
}

function Ensure-Signing([bool]$force = $false) {
    Step 'Step 4 - signing key (lets the app update itself later)'
    $list = GhOut secret list --repo $script:Repo
    if (($list -match 'KEYSTORE_BASE64') -and (-not $force)) { Ok 'A signing key is already saved'; return }

    Say '   Android only installs an update if it is signed with the SAME key as the app on your phone.' 'Gray'
    $debugKs = Join-Path $env:USERPROFILE '.android\debug.keystore'
    $hasDebug = Test-Path $debugKs
    Write-Host ''
    if ($hasDebug) { Write-Host '   1) Android Studio key found on this PC (keeps the app on your phone, no uninstall)  <- pick this if you ran the app from Android Studio' }
    else { Write-Host '   1) Android Studio key (not found on this PC)' }
    Write-Host '   2) I have my own key file (for example familysafetube-release.jks)'
    Write-Host '   3) Make a brand new key (the old app must be uninstalled once first)'
    $def = '3'
    if ($hasDebug) { $def = '1' }
    $c = Ask '   Which one?' $def

    if ($c -eq '1') {
        if (-not $hasDebug) { throw 'No Android Studio key on this PC. Choose 2 or 3.' }
        Save-Signing $debugKs 'androiddebugkey' 'android' 'android'
    }
    elseif ($c -eq '2') {
        $f = (Ask '   Full path of the key file').Trim('"')
        if (-not (Test-Path $f)) { throw "File not found: $f" }
        $alias = Ask '   Key alias' 'familysafetube'
        $sp = Read-Host '   Key file password'
        $kp = Read-Host '   Key password (Enter = same as the file password)'
        if ([string]::IsNullOrWhiteSpace($kp)) { $kp = $sp }
        $kt = Find-Keytool
        if ($kt) {
            & $kt -list -keystore $f -storepass $sp -alias $alias *> $null
            if ($LASTEXITCODE -ne 0) { throw 'That alias or password does not open the key file. Check them and try again.' }
        }
        Save-Signing $f $alias $sp $kp
    }
    else {
        $kt = Find-Keytool
        if (-not $kt) {
            Ensure-Tool 'keytool' 'EclipseAdoptium.Temurin.21.JDK' 'Java (only used once, to make the key)'
            $kt = Find-Keytool
        }
        if (-not $kt) { throw 'Java is installed but keytool was not found. Close this window and start again.' }
        New-Item -ItemType Directory -Force -Path $KeyDir | Out-Null
        $ks = Join-Path $KeyDir 'familysafetube-release.jks'
        if (Test-Path $ks) { throw "$ks already exists. Use option 2 to use it." }
        $pw = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ })
        & $kt -genkeypair -keystore $ks -alias familysafetube -keyalg RSA -keysize 2048 -validity 10000 `
            -storepass $pw -keypass $pw -dname 'CN=FamilySafeTube, O=Personal, C=AE' | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the key.' }
        Set-Content -Path (Join-Path $KeyDir 'READ-ME-familysafetube-key.txt') -Value @(
            'FamilySafeTube signing key - KEEP A COPY OF THIS FOLDER (cloud / USB).',
            'Without it, no future update can be installed over the app on your phone.',
            'File: familysafetube-release.jks',
            'Alias: familysafetube',
            "Password: $pw"
        )
        Save-Signing $ks 'familysafetube' $pw $pw
        Say "   Key folder: $KeyDir  <- back it up now" 'Yellow'
        Start-Process explorer.exe $KeyDir
    }
}

# ---------------------------------------------------------------- Google Drive API key
function Read-Secret([string]$prompt) {
    $sec = Read-Host $prompt -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

function Show-GoogleSignInSteps {
    Step 'Step 5 - Google sign-in (one-time, in Google Cloud; no key goes into the app)'
    Say '   The app signs in with each person''s own Google account, so there is NO Drive API key any more.' 'Gray'
    Say '   Do this once at https://console.cloud.google.com (use the SHA-1 printed in step 4):' 'Yellow'
    Say '     1. Create a project, then APIs & Services > Library > enable "Google Drive API".' 'Yellow'
    Say '     2. OAuth consent screen: External, add the scopes drive.readonly and drive.file, then Publish app (In production).' 'Yellow'
    Say '        (Do NOT leave it in Testing: sign-ins would expire every 7 days. Up to 100 family and friends can use it.)' 'Yellow'
    Say '     3. Credentials > Create credentials > OAuth client ID > Android:' 'Yellow'
    Say '        package com.example.familysafetube + the SHA-1 of your signing key.' 'Yellow'
    Say '   Nothing to paste here. Full guide: SETUP.md, section "Google sign-in".' 'Gray'
    Say '   Also: delete the OLD Drive API key in Google Cloud (Credentials). It was public.' 'Yellow'
}

# ---------------------------------------------------------------- build + download
function Show-BuildError([string]$runId) {
    $raw = & gh run view $runId --repo $script:Repo --log-failed 2>&1 | Out-String
    $all = $raw -split "`r?`n"
    # Drop the "job<TAB>step<TAB>timestamp" prefix and very long lines (licence texts), keep what matters.
    $short = $all | ForEach-Object { $_ -replace '^[^\t]*\t[^\t]*\t\d{4}-\d\d-\d\dT[\d:.]+Z ?', '' } | Where-Object { $_.Length -lt 300 -and $_.Trim().Length -gt 0 }
    $key  = @($short | Where-Object { $_ -match 'error|Error|ERROR|FAILED|What went wrong|Warning: Failed|^e: |Caused by' } | Select-Object -Last 40)
    $tail = @($short | Select-Object -Last 40)
    $out = @('=== KEY LINES ===') + $key + @('', '=== LAST LINES ===') + $tail
    Set-Content -Path $ErrFile -Value $out
    Write-Host ''
    Say '   The build failed. The error is saved here (short, ready to copy):' 'Red'
    Say "   $ErrFile" 'Yellow'
    Say '   Open it, copy everything, and send it to Claude - it will be fixed and you press Rebuild-APK.bat again.' 'Yellow'
    Start-Process notepad.exe $ErrFile
}

# Ids of the most recent Release runs, newest first (no clocks involved).
function Get-RunIds {
    $json = GhOut run list --repo $script:Repo --workflow release.yml --limit 20 --json databaseId
    $ids = @()
    if ($json) { foreach ($r in ($json | ConvertFrom-Json)) { $ids += "$($r.databaseId)" } }
    return $ids
}

# Waits for a build to finish, then downloads its APK.
function Follow-Run([string]$id) {
    Say "   Build page: https://github.com/$($script:Repo)/actions/runs/$id" 'Gray'
    & gh run watch $id --repo $script:Repo --exit-status
    if ($LASTEXITCODE -ne 0) { Show-BuildError $id; throw 'Build failed (see above).' }

    Step 'Downloading the APK'
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
    Get-ChildItem $OutDir -Filter *.apk -ErrorAction SilentlyContinue | Remove-Item -Force
    & gh release download --repo $script:Repo --pattern '*.apk' --dir $OutDir --clobber
    if ($LASTEXITCODE -ne 0) { throw 'The build worked but the download failed. Open the Releases page (menu 6) and download the APK there.' }
    $apk = Get-ChildItem $OutDir -Filter *.apk | Select-Object -First 1
    Ok "Your APK: $($apk.FullName)"
    Write-Host ''
    Say '   Put it on your phone (USB cable, Telegram "Saved messages", Google Drive...) and open it to install.' 'Green'
    Say "   Or open this page ON THE PHONE and tap the APK: https://github.com/$($script:Repo)/releases/latest" 'Green'
    Say '   After this first install, updates arrive inside the app: Settings (parent PIN) > App updates.' 'Green'
    Start-Process explorer.exe $OutDir
}

function Run-Release([string]$notes) {
    Step 'Building the signed APK on GitHub (about 10 minutes - you can leave this window open)'
    $before = Get-RunIds

    $text = ''
    $started = $false
    for ($i = 0; $i -lt 8 -and -not $started; $i++) {
        $text = (& gh workflow run release.yml --repo $script:Repo --ref main -f "notes=$notes" 2>&1 | Out-String)
        if ($LASTEXITCODE -eq 0) { $started = $true } else { Say '   GitHub is still registering the build recipe, retrying...'; Start-Sleep 6 }
    }
    if (-not $started) { throw 'Could not start the build. Open the repository on github.com > Actions and check that Actions are enabled.' }

    # GitHub prints the address of the new run; otherwise take the first run that did not exist before.
    $id = $null
    if ($text -match 'actions/runs/(\d+)') { $id = $Matches[1] }
    for ($i = 0; $i -lt 30 -and -not $id; $i++) {
        Start-Sleep 3
        foreach ($x in (Get-RunIds)) { if ($before -notcontains $x) { $id = $x; break } }
    }
    if (-not $id) { throw 'The build was started but I could not find it. Choose 7 in the menu to follow the latest build.' }
    Follow-Run $id
}

# ---------------------------------------------------------------- the menu actions
function Do-Everything {
    Ensure-Tools
    Ensure-Login
    Ensure-Repo
    Ensure-Signing $false
    Show-GoogleSignInSteps
    $notes = Ask "What's new? (shown in the app's update screen)" 'First build from GitHub'
    Run-Release $notes
}

function Do-Rebuild {
    Ensure-Tools
    Ensure-Login
    if (-not (Read-RepoFromGit)) { throw 'No repository yet. Run START-HERE.bat and choose 1 first.' }
    Step "Sending your changes to GitHub ($($script:Repo))"
    Ensure-GitIdentity

    $vp = Join-Path $Root 'version.properties'
    if (Test-Path $vp) {
        $text = Get-Content $vp -Raw
        if ($text -match '(?m)^versionName=(.+)$') {
            $cur = $Matches[1].Trim()
            $new = Ask "Visible version number (Enter keeps it)" $cur
            if ($new -ne $cur) { Set-Content -Path $vp -Value ($text -replace '(?m)^versionName=.+$', "versionName=$new") -NoNewline }
        }
    }

    Say "   Working folder: $Root" 'Gray'
    & git config core.autocrlf false
    Sync-Remote
    Remove-StaleFiles
    Assert-NoConflictMarkers

    & git add -A
    $files = @(& git diff --cached --name-only)
    $changed = ($files.Count -gt 0)
    $notes = ''
    if ($changed) {
        Say "   $($files.Count) changed file(s) will be sent, for example:" 'Gray'
        $files | Select-Object -First 8 | ForEach-Object { Say "     $_" 'Gray' }
        $notes = Ask 'What changed? (shown in the update screen)' 'Improvements and fixes'
        [void](Commit-All $notes)
    } else {
        Say '   No changes found in this folder compared with GitHub.' 'Yellow'
        Say '   If you just unzipped a new version, make sure it was unzipped INTO THE FOLDER ABOVE (replace all files).' 'Yellow'
        $notes = Ask 'Notes for this build' 'Rebuild'
    }
    & git push origin main
    if ($LASTEXITCODE -ne 0) { throw 'Could not send the changes to GitHub.' }
    Ok 'Changes sent'
    Run-Release $notes
}

function Do-Signing {
    Ensure-Tools
    Ensure-Login
    if (-not (Read-RepoFromGit)) { throw 'No repository yet. Choose 1 first.' }
    Ensure-Signing $true
}

function Do-GoogleSteps {
    Show-GoogleSignInSteps
}

function Do-Follow {
    Ensure-Tools
    Ensure-Login
    if (-not (Read-RepoFromGit)) { throw 'No repository yet. Choose 1 first.' }
    Step 'Following the latest build'
    $ids = Get-RunIds
    if ($ids.Count -eq 0) { throw 'There is no build yet. Choose 1 or 2 first.' }
    Follow-Run $ids[0]
}

function Show-LastError {
    if (Test-Path $ErrFile) { Start-Process notepad.exe $ErrFile } else { Say '   No build error saved. Good.' 'Green' }
}

function Open-Releases {
    Refresh-Path
    if (-not (Read-RepoFromGit)) { throw 'No repository yet. Choose 1 first.' }
    Start-Process "https://github.com/$($script:Repo)/releases/latest"
}

function Guard([scriptblock]$action) {
    try { & $action }
    catch {
        Write-Host ''
        Write-Host "   !!  $($_.Exception.Message)" -ForegroundColor Red
        Say '   If this keeps happening, copy the red text and send it to Claude.' 'Yellow'
    }
}

# ---------------------------------------------------------------- entry point
Refresh-Path
if ($Mode -eq 'rebuild') { Guard { Do-Rebuild }; return }
if ($Mode -eq 'everything') { Guard { Do-Everything }; return }

while ($true) {
    Write-Host ''
    Write-Host '  ======================  FAMILYSAFETUBE SETUP  ======================' -ForegroundColor Cyan
    Write-Host '   1) First time: set everything up and build my APK'
    Write-Host '   2) Rebuild my APK after changes (same as Rebuild-APK.bat)'
    Write-Host '   3) Change or re-save the signing key'
    Write-Host '   4) Show the Google sign-in set-up steps'
    Write-Host '   5) Show the last build error'
    Write-Host '   6) Open the Releases page'
    Write-Host '   7) A build is already running - wait for it and download the APK'
    Write-Host '   0) Close'
    $c = Read-Host '  Type a number and press Enter'
    switch ($c) {
        '1' { Guard { Do-Everything } }
        '2' { Guard { Do-Rebuild } }
        '3' { Guard { Do-Signing } }
        '4' { Guard { Do-GoogleSteps } }
        '5' { Guard { Show-LastError } }
        '6' { Guard { Open-Releases } }
        '7' { Guard { Do-Follow } }
        '0' { return }
        default { Say '   Please type one of the numbers above.' 'Yellow' }
    }
}
