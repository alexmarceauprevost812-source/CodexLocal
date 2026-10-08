# Recupere les sources de llama.cpp au bon endroit.
#
# Elles ne sont pas dans le depot (169 Mo) : ce script les clone a la place
# exacte attendue par lib/src/main/cpp/CMakeLists.txt.

$ErrorActionPreference = 'Stop'

$version = 'v0.6.0'
$dest    = Join-Path $PSScriptRoot 'lib\src\main\cpp\llama.cpp'

if (Test-Path (Join-Path $dest 'CMakeLists.txt')) {
    Write-Host "llama.cpp est deja present :" -ForegroundColor Green
    Write-Host "  $dest"
    exit 0
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw "git est requis. Installe-le depuis https://git-scm.com/download/win"
}

Write-Host "Clone de llama.cpp $version (environ 170 Mo)..." -ForegroundColor Cyan
& git clone --depth 1 --branch $version https://github.com/ggml-org/llama.cpp.git $dest

if (-not (Test-Path (Join-Path $dest 'CMakeLists.txt'))) {
    throw "Le clone a echoue."
}

Write-Host "`nTermine. Tu peux compiler :" -ForegroundColor Green
Write-Host "  gradle assembleDebug"
