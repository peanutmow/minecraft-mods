# fix_voxy_arm64.ps1
# Windows PowerShell script to patch Voxy jar with ARM64 RocksDB native support.

param(
    [string]$ModsFolder = "$(Resolve-Path .)"
)

$rocksdbVersion = '10.2.1'
$rocksdbUrl = "https://repo1.maven.org/maven2/org/rocksdb/rocksdbjni/$rocksdbVersion/rocksdbjni-$rocksdbVersion.jar"

$lwjglVersion = '3.3.3'
$lwjglZstdArm64Name = "lwjgl-zstd-$lwjglVersion-natives-linux-arm64.jar"
$lwjglLmdbArm64Name = "lwjgl-lmdb-$lwjglVersion-natives-linux-arm64.jar"
$lwjglZstdArm64Url = "https://repo1.maven.org/maven2/org/lwjgl/lwjgl-zstd/$lwjglVersion/lwjgl-zstd-$lwjglVersion-natives-linux-arm64.jar"
$lwjglLmdbArm64Url = "https://repo1.maven.org/maven2/org/lwjgl/lwjgl-lmdb/$lwjglVersion/lwjgl-lmdb-$lwjglVersion-natives-linux-arm64.jar"

Write-Host "Mods folder: $ModsFolder"

# Look for voxy jar by name first (voxy-*.jar, excluding VoxyServer)
Add-Type -AssemblyName System.IO.Compression.FileSystem
Write-Host "Looking for voxy jar in $ModsFolder..."
$voxyJar = Get-ChildItem -Path $ModsFolder -Filter 'voxy-*.jar' -File | Where-Object { $_.Name -notlike 'VoxyServer*' } | Select-Object -First 1
if (-not $voxyJar) {
    # Fall back: scan all JARs to find whichever one embeds rocksdbjni in META-INF/jars
    Write-Host "No voxy-*.jar found. Scanning all JARs for embedded rocksdbjni..."
    foreach ($jar in (Get-ChildItem -Path $ModsFolder -Filter '*.jar' -File)) {
        try {
            $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
            $hasRocksdb = $zip.Entries | Where-Object { $_.FullName -match 'META-INF[/\\]jars[/\\]rocksdbjni' } | Select-Object -First 1
            $zip.Dispose()
            if ($hasRocksdb) {
                Write-Host "Found rocksdbjni inside: $($jar.Name)"
                $voxyJar = $jar
                break
            }
        } catch { }
    }
}
if (-not $voxyJar) {
    Write-Host "No local JAR contains rocksdbjni. Auto-downloading Voxy from Modrinth (1.21.11 / Fabric)..."
    $modrinthApi = "https://api.modrinth.com/v2/project/fxxUqruK/version?game_versions=%5B%221.21.11%22%5D&loaders=%5B%22fabric%22%5D"
    $versions = Invoke-RestMethod -Uri $modrinthApi -UseBasicParsing
    $latest = $versions | Select-Object -First 1
    $file = $latest.files | Where-Object { $_.primary -eq $true } | Select-Object -First 1
    if (-not $file) { $file = $latest.files | Select-Object -First 1 }
    $voxyJarPath = Join-Path -Path $ModsFolder -ChildPath $file.filename
    Write-Host "Downloading $($file.filename) -> $voxyJarPath"
    Invoke-WebRequest -Uri $file.url -OutFile $voxyJarPath -UseBasicParsing
    $voxyJar = Get-Item $voxyJarPath
    Write-Host "Downloaded: $($voxyJar.Name)"
    Write-Host ""
    Write-Host "NOTE: After patching, upload this file to your server's mods folder as well (replacing the existing voxy jar there)."
    Write-Host ""
}

Write-Host "Target JAR to patch: $($voxyJar.FullName)"

$downloadedRocksdbJar = Join-Path -Path $env:TEMP -ChildPath "rocksdbjni-$rocksdbVersion.jar"
Write-Host "Downloading RocksDB jar (full) -> $downloadedRocksdbJar"
Invoke-WebRequest -Uri $rocksdbUrl -OutFile $downloadedRocksdbJar -UseBasicParsing
if (-not (Test-Path $downloadedRocksdbJar)) {
    Write-Error "Failed to download rocksdbjni jar."
    exit 1
}

$downloadedLwjglZstdArm64 = Join-Path -Path $env:TEMP -ChildPath $lwjglZstdArm64Name
Write-Host "Downloading LWJGL zstd arm64 native -> $downloadedLwjglZstdArm64"
Invoke-WebRequest -Uri $lwjglZstdArm64Url -OutFile $downloadedLwjglZstdArm64 -UseBasicParsing
if (-not (Test-Path $downloadedLwjglZstdArm64)) { Write-Error "Failed to download LWJGL zstd arm64."; exit 1 }

$downloadedLwjglLmdbArm64 = Join-Path -Path $env:TEMP -ChildPath $lwjglLmdbArm64Name
Write-Host "Downloading LWJGL lmdb arm64 native -> $downloadedLwjglLmdbArm64"
Invoke-WebRequest -Uri $lwjglLmdbArm64Url -OutFile $downloadedLwjglLmdbArm64 -UseBasicParsing
if (-not (Test-Path $downloadedLwjglLmdbArm64)) { Write-Error "Failed to download LWJGL lmdb arm64."; exit 1 }

$backupJar = "$($voxyJar.FullName).backup"
Copy-Item -Path $voxyJar.FullName -Destination $backupJar -Force
Write-Host "Backed up original JAR to $backupJar"

Write-Host "Building fixed JAR (forward-slash paths + full ARM64 RocksDB + LWJGL arm64 natives)..."
$rocksdbBytes        = [System.IO.File]::ReadAllBytes($downloadedRocksdbJar)
$lwjglZstdArm64Bytes = [System.IO.File]::ReadAllBytes($downloadedLwjglZstdArm64)
$lwjglLmdbArm64Bytes = [System.IO.File]::ReadAllBytes($downloadedLwjglLmdbArm64)
$tmpOut = Join-Path $env:TEMP "voxy-fixed-$(Get-Random).jar"

$srcStream  = [System.IO.File]::OpenRead($voxyJar.FullName)
$srcArchive = [System.IO.Compression.ZipArchive]::new($srcStream, [System.IO.Compression.ZipArchiveMode]::Read, $false)
$dstStream  = [System.IO.File]::Open($tmpOut, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
$dstArchive = [System.IO.Compression.ZipArchive]::new($dstStream, [System.IO.Compression.ZipArchiveMode]::Create, $false)

try {
    $count = 0
    foreach ($entry in $srcArchive.Entries) {
        $fixedName = $entry.FullName.Replace('\', '/')
        $newEntry  = $dstArchive.CreateEntry($fixedName, [System.IO.Compression.CompressionLevel]::NoCompression)
        $newEntry.LastWriteTime = $entry.LastWriteTime
        $dst = $newEntry.Open()
        if ($fixedName -match 'rocksdbjni.*\.jar$') {
            $dst.Write($rocksdbBytes, 0, $rocksdbBytes.Length)
            Write-Host "  Replaced: $fixedName ($($rocksdbBytes.Length) bytes, includes aarch64)"
        } elseif ($fixedName -eq 'fabric.mod.json') {
            $src = $entry.Open()
            $reader = [System.IO.StreamReader]::new($src)
            $jsonText = $reader.ReadToEnd()
            $reader.Dispose(); $src.Dispose()
            # Append arm64 entries directly after the last existing jars entry.
            # Targets the literal closing of the xz-1.10.jar entry (the last real entry)
            # so we never need to parse or re-serialize the whole JSON.
            if ($jsonText -notmatch [regex]::Escape($lwjglZstdArm64Name)) {
                $oldTail = '"META-INF/jars/xz-1.10.jar"' + "`n    }" + "`n  ]"
                $newTail = '"META-INF/jars/xz-1.10.jar"' + "`n    }," `
                    + "`n    { `"file`": `"META-INF/jars/$lwjglZstdArm64Name`" }," `
                    + "`n    { `"file`": `"META-INF/jars/$lwjglLmdbArm64Name`" }" `
                    + "`n  ]"
                $jsonText = $jsonText.Replace($oldTail, $newTail)
            }
            $jsonBytes = [System.Text.Encoding]::UTF8.GetBytes($jsonText)
            $dst.Write($jsonBytes, 0, $jsonBytes.Length)
            Write-Host "  Updated: fabric.mod.json (added arm64 LWJGL entries)"
        } else {
            $src = $entry.Open()
            $src.CopyTo($dst)
            $src.Dispose()
        }
        $dst.Dispose()
        $count++
    }
    Write-Host "Wrote $count entries total."

    # Inject LWJGL arm64 natives that are absent from the original JAR
    foreach ($pair in @(
        [PSCustomObject]@{ Name = "META-INF/jars/$lwjglZstdArm64Name"; Bytes = $lwjglZstdArm64Bytes },
        [PSCustomObject]@{ Name = "META-INF/jars/$lwjglLmdbArm64Name"; Bytes = $lwjglLmdbArm64Bytes }
    )) {
        $ne = $dstArchive.CreateEntry($pair.Name, [System.IO.Compression.CompressionLevel]::NoCompression)
        $ne.LastWriteTime = [System.DateTimeOffset]::UtcNow
        $dw = $ne.Open(); $dw.Write($pair.Bytes, 0, $pair.Bytes.Length); $dw.Dispose()
        Write-Host "  Added:    $($pair.Name) ($($pair.Bytes.Length) bytes)"
    }
}
finally {
    $srcArchive.Dispose(); $srcStream.Dispose()
    $dstArchive.Dispose(); $dstStream.Dispose()
}

# Verify before replacing
Add-Type -AssemblyName System.IO.Compression.FileSystem
$verify = [System.IO.Compression.ZipFile]::OpenRead($tmpOut)
$metaJars = $verify.Entries | Where-Object { $_.FullName -match 'META-INF/jars/.+\.jar$' }
Write-Host "META-INF/jars entries in output: $($metaJars.Count)"
$metaJars | ForEach-Object { "  $($_.FullName) [$($_.Length) bytes]" }
$hasRocks    = ($metaJars | Where-Object { $_.FullName -match 'rocksdbjni' }         | Measure-Object).Count -gt 0
$hasZstdArm  = ($metaJars | Where-Object { $_.FullName -match 'lwjgl-zstd.*arm64' }    | Measure-Object).Count -gt 0
$hasLmdbArm  = ($metaJars | Where-Object { $_.FullName -match 'lwjgl-lmdb.*arm64' }    | Measure-Object).Count -gt 0
$verify.Dispose()

if (-not $hasRocks)   { Write-Error "rocksdbjni missing from output! Aborting.";       Remove-Item $tmpOut; exit 1 }
if (-not $hasZstdArm) { Write-Error "lwjgl-zstd arm64 missing from output! Aborting."; Remove-Item $tmpOut; exit 1 }
if (-not $hasLmdbArm) { Write-Error "lwjgl-lmdb arm64 missing from output! Aborting."; Remove-Item $tmpOut; exit 1 }

Move-Item -Path $tmpOut -Destination $voxyJar.FullName -Force
$oldSize = (Get-Item $backupJar).Length
$newSize = (Get-Item $voxyJar.FullName).Length
Write-Host ""
Write-Host "SUCCESS: $($voxyJar.Name) patched."
Write-Host "  Original: $oldSize bytes  |  Patched: $newSize bytes"
Write-Host "  Backup:   $backupJar"
Write-Host ""
Write-Host "Upload $($voxyJar.Name) to the server mods folder and restart."
