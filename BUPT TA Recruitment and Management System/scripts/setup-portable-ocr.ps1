$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$libDir = Join-Path $root "lib"
$vendorDir = Join-Path $root "vendor"
$tessDir = Join-Path $vendorDir "tesseract"
$tessDataDir = Join-Path $tessDir "tessdata"
New-Item -ItemType Directory -Force -Path $libDir, $tessDataDir | Out-Null

function Download-File($Url, $OutPath) {
    if (Test-Path $OutPath) {
        Write-Host "Exists: $OutPath"
        return
    }
    Write-Host "Downloading: $Url"
    Invoke-WebRequest -Uri $Url -OutFile $OutPath -UseBasicParsing -TimeoutSec 180
}

$jars = @(
    @{ File = "pdfbox-3.0.3.jar"; Url = "https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox/3.0.3/pdfbox-3.0.3.jar" },
    @{ File = "fontbox-3.0.3.jar"; Url = "https://repo1.maven.org/maven2/org/apache/pdfbox/fontbox/3.0.3/fontbox-3.0.3.jar" },
    @{ File = "pdfbox-io-3.0.3.jar"; Url = "https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox-io/3.0.3/pdfbox-io-3.0.3.jar" },
    @{ File = "commons-logging-1.3.0.jar"; Url = "https://repo1.maven.org/maven2/commons-logging/commons-logging/1.3.0/commons-logging-1.3.0.jar" },
    @{ File = "tess4j-5.11.0.jar"; Url = "https://repo1.maven.org/maven2/net/sourceforge/tess4j/tess4j/5.11.0/tess4j-5.11.0.jar" },
    @{ File = "jna-5.14.0.jar"; Url = "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.14.0/jna-5.14.0.jar" },
    @{ File = "jna-platform-5.14.0.jar"; Url = "https://repo1.maven.org/maven2/net/java/dev/jna/jna-platform/5.14.0/jna-platform-5.14.0.jar" },
    @{ File = "lept4j-1.19.1.jar"; Url = "https://repo1.maven.org/maven2/net/sourceforge/lept4j/lept4j/1.19.1/lept4j-1.19.1.jar" },
    @{ File = "slf4j-api-2.0.13.jar"; Url = "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.13/slf4j-api-2.0.13.jar" },
    @{ File = "slf4j-simple-2.0.13.jar"; Url = "https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.13/slf4j-simple-2.0.13.jar" },
    @{ File = "jai-imageio-core-1.4.0.jar"; Url = "https://repo1.maven.org/maven2/com/github/jai-imageio/jai-imageio-core/1.4.0/jai-imageio-core-1.4.0.jar" },
    @{ File = "commons-io-2.16.1.jar"; Url = "https://repo1.maven.org/maven2/commons-io/commons-io/2.16.1/commons-io-2.16.1.jar" }
)
foreach ($jar in $jars) {
    Download-File $jar.Url (Join-Path $libDir $jar.File)
}

$trained = @(
    @{ File = "eng.traineddata"; Url = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata" },
    @{ File = "chi_sim.traineddata"; Url = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/chi_sim.traineddata" }
)
foreach ($data in $trained) {
    $out = Join-Path $tessDataDir $data.File
    if (-not (Test-Path $out)) {
        Download-File $data.Url $out
    }
}

# Prefer language files bundled with Windows Tesseract install (more reliable than GitHub download).
$installedData = "C:\Program Files\Tesseract-OCR\tessdata"
if (Test-Path $installedData) {
    Copy-Item -Path (Join-Path $installedData "*.traineddata") -Destination $tessDataDir -Force -ErrorAction SilentlyContinue
}

function Copy-TesseractInstall($SourceDir) {
    if (-not (Test-Path (Join-Path $SourceDir "tesseract.exe"))) {
        return $false
    }
    Write-Host "Copying OCR runtime from: $SourceDir"
    New-Item -ItemType Directory -Force -Path $tessDir | Out-Null
    Copy-Item -Path (Join-Path $SourceDir "*") -Destination $tessDir -Recurse -Force
    return (Test-Path (Join-Path $tessDir "tesseract.exe"))
}

$ready = Test-Path (Join-Path $tessDir "tesseract.exe")
if (-not $ready) {
    $ready = Copy-TesseractInstall "C:\Program Files\Tesseract-OCR"
}
if (-not $ready -and (Get-Command winget -ErrorAction SilentlyContinue)) {
    Write-Host "Installing Tesseract-OCR via winget (one-time, no manual steps)..."
    winget install -e --id UB-Mannheim.TesseractOCR --accept-package-agreements --accept-source-agreements --silent | Out-Host
    $ready = Copy-TesseractInstall "C:\Program Files\Tesseract-OCR"
}

if (-not $ready) {
    throw "tesseract.exe is still missing. Please install UB-Mannheim.TesseractOCR once, then rerun this script."
}

if (-not (Test-Path (Join-Path $tessDataDir "eng.traineddata"))) {
    $installedData = Join-Path $tessDir "tessdata"
    if (Test-Path (Join-Path $installedData "eng.traineddata")) {
        $tessDataDir = $installedData
    }
}

Write-Host "Portable OCR setup complete."
Write-Host "Tesseract: $(Join-Path $tessDir 'tesseract.exe')"
Write-Host "Tessdata : $tessDataDir"
Write-Host "JARs     : $libDir"
