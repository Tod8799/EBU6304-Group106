$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
Set-Location $root

$tessExe = Join-Path $root "vendor\tesseract\tesseract.exe"
if (-not (Test-Path $tessExe)) {
    Write-Host "First run: setting up portable OCR runtime..."
    powershell -ExecutionPolicy Bypass -File (Join-Path $root "scripts\setup-portable-ocr.ps1")
}

$libCp = (Get-ChildItem (Join-Path $root "lib\*.jar") -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike "pdfbox-app-*" } |
    ForEach-Object { $_.FullName }) -join ';'
if ([string]::IsNullOrWhiteSpace($libCp)) {
    throw "lib/*.jar missing. Run scripts/setup-portable-ocr.ps1 first."
}

$cp = "$libCp;$root\out"
Write-Host "Compiling..."
javac -encoding UTF-8 -cp $cp -d out src\model\*.java src\dao\*.java src\ResumePdfOcr.java src\Main.java src\WebServer.java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Starting WebServer..."
java -cp $cp WebServer
