Set-Location $PSScriptRoot

$FX  = "libs\javafx"
$CP  = "$FX\javafx-base-24-win.jar;$FX\javafx-graphics-24-win.jar;$FX\javafx-controls-24-win.jar"
$MOD = "--module-path `"$FX`""
$ADD = "--add-modules javafx.controls,javafx.base,javafx.graphics"

# ── Compile ───────────────────────────────────────────────────────────────────
Write-Host "Compiling..." -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path "build\classes" | Out-Null
Get-ChildItem -Path src -Recurse -Filter "*.java" |
    ForEach-Object { $_.FullName } |
    Out-File -Encoding ASCII "build\filelist_compile.txt"

$result = & javac $ADD.Split() $MOD.Split() -cp $CP -d build\classes -encoding UTF-8 "@build\filelist_compile.txt" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "BUILD FAILED:" -ForegroundColor Red
    $result | ForEach-Object { Write-Host $_ }
    exit 1
}
Write-Host "Build OK." -ForegroundColor Green

# ── Run ───────────────────────────────────────────────────────────────────────
Write-Host "Launching..." -ForegroundColor Cyan
& java $ADD.Split() $MOD.Split() -cp "$CP;build\classes" simuladorminipc.MainFrame
