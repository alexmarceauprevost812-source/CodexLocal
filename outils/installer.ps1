# Ti-Labo - installe Python, Ollama et Git DIRECTEMENT sur la cle USB-C.
# Rien n'est installe sur le PC.
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"   # accelere enormement Invoke-WebRequest
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Racine = Split-Path -Parent $PSScriptRoot
$Outils = Join-Path $Racine "outils"
$Tmp    = Join-Path $Racine "donnees\telechargements"
New-Item -ItemType Directory -Force -Path $Tmp, (Join-Path $Racine "projets"), (Join-Path $Racine "ia\modeles") | Out-Null

function Titre($t) { Write-Host ""; Write-Host "== $t" -ForegroundColor DarkYellow }
function Ok($t)    { Write-Host "   OK  $t" -ForegroundColor Green }

Write-Host ""
Write-Host "  TI-LABO - installation sur la cle" -ForegroundColor DarkYellow
Write-Host "  Dossier : $Racine"

# --- Verifie le format de la cle (FAT32 bloque les fichiers de plus de 4 Go)
try {
    $lettre = (Split-Path -Qualifier $Racine).TrimEnd(":")
    $vol = Get-Volume -DriveLetter $lettre -ErrorAction Stop
    Write-Host "  Format : $($vol.FileSystem) - libre : $([math]::Round($vol.SizeRemaining/1GB,1)) Go"
    if ($vol.FileSystem -eq "FAT32") {
        Write-Host ""
        Write-Host "  ATTENTION : ta cle est en FAT32. Les IA de plus de 4 Go ne pourront pas" -ForegroundColor Red
        Write-Host "  y etre copiees. Formate-la en exFAT (clic droit > Formater) pour tout utiliser." -ForegroundColor Red
    }
} catch { }

# --- 1. Python portable (version embarquee officielle)
Titre "1/3 Python portable"
$py = Join-Path $Outils "python\python.exe"
if (Test-Path $py) { Ok "deja present" } else {
    $v = "3.12.10"
    $zip = Join-Path $Tmp "python.zip"
    Write-Host "   Telechargement de Python $v ..."
    Invoke-WebRequest "https://www.python.org/ftp/python/$v/python-$v-embed-amd64.zip" -OutFile $zip
    Expand-Archive $zip -DestinationPath (Join-Path $Outils "python") -Force
    Remove-Item $zip
    Ok "Python $v"
}

# --- 2. Ollama portable (moteur d'IA locale)
Titre "2/3 Ollama (moteur d'IA locale)"
$ol = Join-Path $Outils "ollama\ollama.exe"
if (Test-Path $ol) { Ok "deja present" } else {
    $zip = Join-Path $Tmp "ollama.zip"
    Write-Host "   Telechargement d'Ollama (environ 1 a 2 Go, sois patient) ..."
    Invoke-WebRequest "https://github.com/ollama/ollama/releases/latest/download/ollama-windows-amd64.zip" -OutFile $zip
    Write-Host "   Decompression sur la cle ..."
    Expand-Archive $zip -DestinationPath (Join-Path $Outils "ollama") -Force
    Remove-Item $zip
    Ok "Ollama"
}

# --- 3. Git portable (MinGit officiel de Git for Windows)
Titre "3/3 Git portable"
$git = Join-Path $Outils "git\cmd\git.exe"
if (Test-Path $git) { Ok "deja present" } else {
    $rel = Invoke-RestMethod "https://api.github.com/repos/git-for-windows/git/releases/latest" -Headers @{ "User-Agent" = "Ti-Labo" }
    $asset = $rel.assets | Where-Object { $_.name -match '^MinGit-[\d.]+-64-bit\.zip$' } | Select-Object -First 1
    if (-not $asset) { throw "MinGit introuvable dans la derniere version de Git for Windows" }
    $zip = Join-Path $Tmp "mingit.zip"
    Write-Host "   Telechargement de $($asset.name) ..."
    Invoke-WebRequest $asset.browser_download_url -OutFile $zip
    Expand-Archive $zip -DestinationPath (Join-Path $Outils "git") -Force
    Remove-Item $zip
    Ok "Git"
}

Write-Host ""
Write-Host "  Tout est sur la cle ! Lance maintenant Demarrer-Ti-Labo.bat" -ForegroundColor Green
Write-Host "  Puis dans le Ti-Labo : IA locales > Telecharger (ex. qwen2.5-coder:7b, gemma3:4b, nomic-embed-text)."
Write-Host ""
