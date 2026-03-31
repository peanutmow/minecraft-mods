# make_lwjgl_arm64_loader.ps1
# Creates a standalone Fabric mod that provides LWJGL arm64 (aarch64) native libraries.
# Fixes "Failed to locate library: liblwjgl_zstd.so" and liblwjgl_lmdb.so on ARM64 servers.
#
# Usage: .\make_lwjgl_arm64_loader.ps1
# Output: lwjgl-arm64-loader.jar  (drop this in your server mods/ folder)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$lwjglVersion = "3.3.3"
$outJar = "$PSScriptRoot\lwjgl-arm64-loader.jar"

$natives = @(
    [PSCustomObject]@{
        Name     = "lwjgl-zstd-$lwjglVersion-natives-linux-arm64.jar"
        Url      = "https://repo1.maven.org/maven2/org/lwjgl/lwjgl-zstd/$lwjglVersion/lwjgl-zstd-$lwjglVersion-natives-linux-arm64.jar"
        FabricId = "org_lwjgl_zstd_arm64"
        FabricName = "lwjgl-zstd arm64"
    },
    [PSCustomObject]@{
        Name     = "lwjgl-lmdb-$lwjglVersion-natives-linux-arm64.jar"
        Url      = "https://repo1.maven.org/maven2/org/lwjgl/lwjgl-lmdb/$lwjglVersion/lwjgl-lmdb-$lwjglVersion-natives-linux-arm64.jar"
        FabricId = "org_lwjgl_lmdb_arm64"
        FabricName = "lwjgl-lmdb arm64"
    }
)

# --- Step 1: Download native JARs ---
foreach ($n in $natives) {
    $tmp = "$env:TEMP\$($n.Name)"
    if (-not (Test-Path $tmp) -or (Get-Item $tmp).Length -lt 10000) {
        Write-Host "Downloading $($n.Name)..."
        Invoke-WebRequest -Uri $n.Url -OutFile $tmp -UseBasicParsing
    }
    Write-Host "  $($n.Name): $((Get-Item $tmp).Length) bytes"
    $n | Add-Member -NotePropertyName TempPath -NotePropertyValue $tmp
}

# --- Step 2: Wrap each native JAR with fabric.mod.json injected ---
$wrappedJars = @()
foreach ($n in $natives) {
    $wrappedPath = "$env:TEMP\$($n.FabricId)-wrapped.jar"
    if (Test-Path $wrappedPath) { Remove-Item $wrappedPath }

    $innerFmj = [System.Text.Encoding]::UTF8.GetBytes(@"
{
  "schemaVersion": 1,
  "id": "$($n.FabricId)",
  "version": "$lwjglVersion",
  "name": "$($n.FabricName) natives",
  "description": "LWJGL $($n.FabricName) native library for Linux aarch64",
  "license": "BSD-3-Clause",
  "environment": "*"
}
"@)

    Write-Host "Wrapping $($n.Name) with fabric.mod.json..."
    $srcZip = [System.IO.Compression.ZipFile]::OpenRead($n.TempPath)
    $wFs  = [System.IO.File]::Create($wrappedPath)
    $wZip = [System.IO.Compression.ZipArchive]::new($wFs, [System.IO.Compression.ZipArchiveMode]::Create, $false)

    $count = 0
    foreach ($entry in $srcZip.Entries) {
        if ($entry.Length -eq 0 -and $entry.FullName.EndsWith('/')) { continue }
        $ne  = $wZip.CreateEntry($entry.FullName, [System.IO.Compression.CompressionLevel]::Fastest)
        $ne.LastWriteTime = $entry.LastWriteTime
        $dst = $ne.Open(); $src = $entry.Open()
        $src.CopyTo($dst); $src.Dispose(); $dst.Dispose()
        $count++
    }
    $srcZip.Dispose()

    $fmjEntry = $wZip.CreateEntry("fabric.mod.json", [System.IO.Compression.CompressionLevel]::Optimal)
    $fw = $fmjEntry.Open(); $fw.Write($innerFmj, 0, $innerFmj.Length); $fw.Close()

    $wZip.Dispose(); $wFs.Dispose()
    Write-Host "  Wrapped: $count entries + fabric.mod.json ($((Get-Item $wrappedPath).Length) bytes)"

    $wrappedJars += [PSCustomObject]@{ Native = $n; WrappedPath = $wrappedPath }
}

# --- Step 3: Build outer loader mod JAR ---
Write-Host "Building outer lwjgl-arm64-loader.jar..."
if (Test-Path $outJar) { Remove-Item $outJar }

$jarsArrayEntries = ($wrappedJars | ForEach-Object { "    { `"file`": `"META-INF/jars/$($_.Native.Name)`" }" }) -join ",`n"

$outerFmj = [System.Text.Encoding]::UTF8.GetBytes(@"
{
  "schemaVersion": 1,
  "id": "lwjgl_arm64_loader",
  "version": "$lwjglVersion",
  "name": "LWJGL ARM64 Loader",
  "description": "Provides LWJGL zstd and lmdb arm64 natives for Voxy on ARM64 Linux servers.",
  "license": "BSD-3-Clause",
  "environment": "*",
  "depends": { "fabricloader": ">=0.14.0" },
  "jars": [
$jarsArrayEntries
  ]
}
"@)

$oFs  = [System.IO.File]::Create($outJar)
$oZip = [System.IO.Compression.ZipArchive]::new($oFs, [System.IO.Compression.ZipArchiveMode]::Create, $false)

$fmj = $oZip.CreateEntry("fabric.mod.json", [System.IO.Compression.CompressionLevel]::Optimal)
$fw = $fmj.Open(); $fw.Write($outerFmj, 0, $outerFmj.Length); $fw.Close()

foreach ($w in $wrappedJars) {
    $bytes = [System.IO.File]::ReadAllBytes($w.WrappedPath)
    $je = $oZip.CreateEntry("META-INF/jars/$($w.Native.Name)", [System.IO.Compression.CompressionLevel]::NoCompression)
    $je.LastWriteTime = [System.DateTimeOffset]::UtcNow
    $jw = $je.Open(); $jw.Write($bytes, 0, $bytes.Length); $jw.Dispose()
    Write-Host "  Bundled: META-INF/jars/$($w.Native.Name) ($($bytes.Length) bytes)"
}

$oZip.Dispose(); $oFs.Dispose()

Write-Host ""
Write-Host "SUCCESS: Created $outJar ($((Get-Item $outJar).Length) bytes)"
Write-Host ""
Write-Host "Upload lwjgl-arm64-loader.jar to your server mods/ folder and restart."
