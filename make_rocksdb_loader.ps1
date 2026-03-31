# make_rocksdb_loader.ps1
# Creates a standalone Fabric mod that properly embeds rocksdbjni with
# a synthetic fabric.mod.json so Fabric Loader puts it on the classpath.
# Upload rocksdbjni-arm64-loader.jar alongside VoxyServer in your mods folder.

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$rocksdbVersion = "10.2.1"
$rocksdbUrl     = "https://repo1.maven.org/maven2/org/rocksdb/rocksdbjni/$rocksdbVersion/rocksdbjni-$rocksdbVersion.jar"
$rocksdbTemp    = "$env:TEMP\rocksdbjni-$rocksdbVersion.jar"
$wrappedTemp    = "$env:TEMP\rocksdbjni-wrapped.jar"
$outJar         = "$PSScriptRoot\rocksdbjni-arm64-loader.jar"

# --- Step 1: Download ---
if (-not (Test-Path $rocksdbTemp) -or (Get-Item $rocksdbTemp).Length -lt 70000000) {
    Write-Host "Downloading rocksdbjni-$rocksdbVersion.jar..."
    Invoke-WebRequest -Uri $rocksdbUrl -OutFile $rocksdbTemp -UseBasicParsing
}
Write-Host "rocksdbjni downloaded: $((Get-Item $rocksdbTemp).Length) bytes"

# --- Step 2: Build inner rocksdbjni jar with synthetic fabric.mod.json injected ---
Write-Host "Building wrapped inner JAR..."
if (Test-Path $wrappedTemp) { Remove-Item $wrappedTemp }

$innerFmj = [System.Text.Encoding]::UTF8.GetBytes(@'
{
  "schemaVersion": 1,
  "id": "org_rocksdb_rocksdbjni",
  "version": "10.2.1",
  "name": "rocksdbjni",
  "description": "RocksDB JNI bindings (all platforms including linux-aarch64)",
  "license": "Apache-2.0",
  "environment": "*"
}
'@)

$innerFs  = [System.IO.File]::Create($wrappedTemp)
$innerZip = [System.IO.Compression.ZipArchive]::new($innerFs, [System.IO.Compression.ZipArchiveMode]::Create, $false)

# Copy all entries from original Maven Central jar
$srcZip = [System.IO.Compression.ZipFile]::OpenRead($rocksdbTemp)
$count = 0
foreach ($entry in $srcZip.Entries) {
    if ($entry.Length -eq 0 -and $entry.FullName.EndsWith('/')) { continue } # skip dir entries
    $ne  = $innerZip.CreateEntry($entry.FullName, [System.IO.Compression.CompressionLevel]::Fastest)
    $ne.LastWriteTime = $entry.LastWriteTime
    $dst = $ne.Open()
    $src = $entry.Open()
    $src.CopyTo($dst)
    $src.Dispose()
    $dst.Dispose()
    $count++
}
$srcZip.Dispose()

# Inject synthetic fabric.mod.json
$fmjEntry = $innerZip.CreateEntry("fabric.mod.json", [System.IO.Compression.CompressionLevel]::Optimal)
$fw = $fmjEntry.Open(); $fw.Write($innerFmj, 0, $innerFmj.Length); $fw.Close()

$innerZip.Dispose()
$innerFs.Dispose()
Write-Host "Inner wrapped JAR: $count entries + fabric.mod.json, size=$((Get-Item $wrappedTemp).Length) bytes"

# --- Step 3: Build outer wrapper mod JAR ---
Write-Host "Building outer wrapper mod JAR..."
if (Test-Path $outJar) { Remove-Item $outJar }

$outerFmj = [System.Text.Encoding]::UTF8.GetBytes(@'
{
  "schemaVersion": 1,
  "id": "rocksdbjni_arm64",
  "version": "10.2.1",
  "name": "RocksDB ARM64 Loader",
  "description": "Provides rocksdbjni for ARM64 servers running Voxy/VoxyServer.",
  "license": "Apache-2.0",
  "environment": "*",
  "depends": { "fabricloader": ">=0.14.0" },
  "jars": [
    { "file": "META-INF/jars/rocksdbjni-10.2.1.jar" }
  ]
}
'@)

$innerBytes = [System.IO.File]::ReadAllBytes($wrappedTemp)

$outerFs  = [System.IO.File]::Create($outJar)
$outerZip = [System.IO.Compression.ZipArchive]::new($outerFs, [System.IO.Compression.ZipArchiveMode]::Create, $false)

$fmj = $outerZip.CreateEntry("fabric.mod.json", [System.IO.Compression.CompressionLevel]::Optimal)
$fw = $fmj.Open(); $fw.Write($outerFmj, 0, $outerFmj.Length); $fw.Close()

$ji = $outerZip.CreateEntry("META-INF/jars/rocksdbjni-10.2.1.jar", [System.IO.Compression.CompressionLevel]::Fastest)
$jw = $ji.Open(); $jw.Write($innerBytes, 0, $innerBytes.Length); $jw.Close()

$outerZip.Dispose()
$outerFs.Dispose()

Write-Host ""
Write-Host "SUCCESS: Created $outJar ($((Get-Item $outJar).Length) bytes)"
Write-Host ""
Write-Host "Upload rocksdbjni-arm64-loader.jar to your server mods/ folder."
Write-Host "You can RESTORE the original voxy-0.2.13-alpha.jar (from backup) - no patching needed."
Write-Host "VoxyServer-1.1.4.jar stays as-is."
Write-Host "Restart and the ARM64 RocksDB issue should be resolved."
