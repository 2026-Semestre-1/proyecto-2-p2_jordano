# setup.ps1
# Downloads the JavaFX 24 Windows JARs required by simuladorMiniPC.
# Run once per machine. The libs/ folder is .gitignored so it stays local.

$dest = "$PSScriptRoot\simuladorMiniPC\libs\javafx"
$base = "https://repo1.maven.org/maven2/org/openjfx"
$ver  = "24"

$jars = @(
    "javafx-base/$ver/javafx-base-$ver-win.jar",
    "javafx-graphics/$ver/javafx-graphics-$ver-win.jar",
    "javafx-controls/$ver/javafx-controls-$ver-win.jar"
)

if (-not (Test-Path $dest)) {
    New-Item -ItemType Directory -Path $dest | Out-Null
}

foreach ($jar in $jars) {
    $url      = "$base/$jar"
    $filename = Split-Path $jar -Leaf
    $outPath  = Join-Path $dest $filename

    if (Test-Path $outPath) {
        Write-Host "  [skip] $filename already exists"
        continue
    }

    Write-Host "  [download] $filename ..."
    Invoke-WebRequest -Uri $url -OutFile $outPath -UseBasicParsing
    Write-Host "  [ok] $filename"
}

Write-Host ""
Write-Host "Done. Open simuladorMiniPC in NetBeans and run Clean and Build (Shift+F11), then Run (F6)."
