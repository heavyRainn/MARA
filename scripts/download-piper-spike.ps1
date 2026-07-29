$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$ModuleDir = Join-Path $Root "piper-spike"
$JniDir = Join-Path $ModuleDir "src\main\jniLibs\arm64-v8a"
$VoiceAssetDir = Join-Path $ModuleDir "src\main\assets\piper\voices\ru_RU-irina-medium"
$LegacyVoiceAssetDir = Join-Path $ModuleDir "src\main\assets\piper\voices\ru_RU-dmitri-medium"
$CacheDir = Join-Path $Root ".gradle\piper-spike-cache"

$SherpaVersion = "1.13.4"
$SherpaArchive = "sherpa-onnx-v$SherpaVersion-android.tar.bz2"
$SherpaUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$SherpaVersion/$SherpaArchive"

$ModelArchive = "vits-piper-ru_RU-irina-medium.tar.bz2"
$ModelUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$ModelArchive"
$ModelDirName = "vits-piper-ru_RU-irina-medium"
$VoiceId = "ru_RU-irina-medium"
$VoiceVersion = "2.0.0"
$ModelSourceCommit = "sherpa-onnx-tts-models"

function Ensure-Dir([string]$Path) {
    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
}

function Download-IfMissing([string]$Url, [string]$Destination) {
    if (Test-Path $Destination) {
        Write-Host "Already cached: $Destination"
        return
    }
    Ensure-Dir (Split-Path $Destination -Parent)
    Write-Host "Downloading $Url"
    Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing
}

function Extract-Archive([string]$ArchivePath, [string]$Destination) {
    Ensure-Dir $Destination
    Write-Host "Extracting $ArchivePath -> $Destination"
    python -c "import tarfile, sys; tarfile.open(sys.argv[1], 'r:bz2').extractall(sys.argv[2])" $ArchivePath $Destination
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to extract $ArchivePath"
    }
}

function Find-FirstFile([string]$RootPath, [string]$FileName) {
    Get-ChildItem -Path $RootPath -Recurse -Filter $FileName -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}

function Write-Sha256Manifest([string]$VoiceDir) {
    $hashScript = @"
import hashlib, json, pathlib, sys
voice = pathlib.Path(sys.argv[1])
files = ['model.onnx', 'model.onnx.json', 'tokens.txt']
sha = {}
for name in files:
    data = (voice / name).read_bytes()
    sha[name] = hashlib.sha256(data).hexdigest()
manifest = {
    'voiceId': '$VoiceId',
    'locale': 'ru-RU',
    'version': '$VoiceVersion',
    'modelFile': 'model.onnx',
    'configFile': 'model.onnx.json',
    'tokensFile': 'tokens.txt',
    'sampleRateHz': 22050,
    'speakerId': 0,
    'sha256': sha,
    'license': 'MIT',
    'datasetLicense': 'Unknown'
}
(voice / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
print('SHA-256:')
for name, digest in sha.items():
    print(f'  {name}: {digest}')
"@

    $tempPy = Join-Path $CacheDir "write_manifest.py"
    Set-Content -Path $tempPy -Value $hashScript -Encoding UTF8
    python $tempPy $VoiceDir
}

Ensure-Dir $CacheDir
Ensure-Dir $JniDir
Ensure-Dir (Split-Path $VoiceAssetDir -Parent)

if (Test-Path $LegacyVoiceAssetDir) {
    Remove-Item $LegacyVoiceAssetDir -Recurse -Force
}

$SherpaCache = Join-Path $CacheDir $SherpaArchive
$ModelCache = Join-Path $CacheDir $ModelArchive

Download-IfMissing $SherpaUrl $SherpaCache
Download-IfMissing $ModelUrl $ModelCache

$SherpaExtract = Join-Path $CacheDir "sherpa-onnx-android"
if (-not (Find-FirstFile $SherpaExtract "libsherpa-onnx-jni.so")) {
    if (Test-Path $SherpaExtract) { Remove-Item $SherpaExtract -Recurse -Force }
    Extract-Archive $SherpaCache $SherpaExtract
}

$JniLib = Find-FirstFile $SherpaExtract "libsherpa-onnx-jni.so"
$OnnxRuntime = Find-FirstFile $SherpaExtract "libonnxruntime.so"
if (-not $JniLib -or -not $OnnxRuntime) {
    throw "Native libraries not found in sherpa archive"
}
Copy-Item $OnnxRuntime $JniDir -Force
Copy-Item $JniLib $JniDir -Force

if (Test-Path $VoiceAssetDir) { Remove-Item $VoiceAssetDir -Recurse -Force }
Ensure-Dir $VoiceAssetDir

$ModelExtract = Join-Path $CacheDir "piper-model-irina"
if (Test-Path $ModelExtract) { Remove-Item $ModelExtract -Recurse -Force }
Extract-Archive $ModelCache $ModelExtract
$ExtractedModelDir = Join-Path $ModelExtract $ModelDirName
if (-not (Test-Path $ExtractedModelDir)) {
    throw "Expected model directory not found: $ExtractedModelDir"
}

Copy-Item (Join-Path $ExtractedModelDir "ru_RU-irina-medium.onnx") (Join-Path $VoiceAssetDir "model.onnx") -Force
Copy-Item (Join-Path $ExtractedModelDir "tokens.txt") $VoiceAssetDir -Force
Copy-Item (Join-Path $ExtractedModelDir "espeak-ng-data") $VoiceAssetDir -Recurse -Force
$sourceConfig = Join-Path $ExtractedModelDir "ru_RU-irina-medium.onnx.json"
if (Test-Path $sourceConfig) {
    Copy-Item $sourceConfig (Join-Path $VoiceAssetDir "model.onnx.json") -Force
} else {
    @'
{
  "audio": { "sample_rate": 22050 },
  "espeak": { "voice": "ru" },
  "inference": { "noise_scale": 0.667, "length_scale": 1.0, "noise_w": 0.8 }
}
'@ | Set-Content -Path (Join-Path $VoiceAssetDir "model.onnx.json") -Encoding UTF8
}

@'
MIT License

Copyright (c) Rhasspy / Piper contributors
'@ | Set-Content -Path (Join-Path $VoiceAssetDir "LICENSE") -Encoding UTF8

@'
# ru_RU-irina-medium

Russian Piper voice (Irina, medium quality).
Bundled for offline TTS in Yasna.

Dataset license: Unknown
'@ | Set-Content -Path (Join-Path $VoiceAssetDir "MODEL_CARD.md") -Encoding UTF8

Write-Sha256Manifest $VoiceAssetDir

Write-Host "Piper Irina assets ready:"
Write-Host "  JNI: $JniDir"
Write-Host "  Voice: $VoiceAssetDir"
Write-Host "  Source tag: $ModelSourceCommit"
