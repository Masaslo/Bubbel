param([Parameter(Mandatory = $true)][string]$RepositoryRoot)

$ErrorActionPreference = 'Stop'
$sdkLine = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'local.properties') |
    Where-Object { $_ -like 'sdk.dir=*' } |
    Select-Object -First 1
if (-not $sdkLine) { throw 'sdk.dir is missing from local.properties' }
$sdkDir = $sdkLine.Substring('sdk.dir='.Length).Replace('\:', ':').Replace('\\', '\')
$adbTool = Join-Path $sdkDir 'platform-tools\adb.exe'
$nativeRoot = Get-ChildItem -LiteralPath (Join-Path $RepositoryRoot 'app\build\intermediates\cxx\Debug') -Directory |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
$objectRoot = Join-Path $nativeRoot.FullName 'obj\x86_64'
$remote = '/data/local/tmp/bubbel_voice_filter_test'

& $adbTool wait-for-device
& $adbTool shell mkdir -p $remote
& $adbTool push (Join-Path $objectRoot 'voice_filter_tests') "$remote/voice_filter_tests"
& $adbTool push (Join-Path $objectRoot 'audio_core_tests') "$remote/audio_core_tests"
& $adbTool push (Join-Path $objectRoot 'libc++_shared.so') "$remote/libc++_shared.so"
& $adbTool push (Join-Path $RepositoryRoot 'app\build\generated\jniLibs\x86_64\libbubbel_libdf.so') "$remote/libbubbel_libdf.so"
& $adbTool push (Join-Path $RepositoryRoot 'app\src\main\assets\models\deepfilternet3\DeepFilterNet3_onnx.tar.gz') "$remote/model.tar.gz"
& $adbTool push (Join-Path $RepositoryRoot 'app\src\main\assets\models\deepfilternet3\metadata.json') "$remote/metadata.json"
& $adbTool push (Join-Path $RepositoryRoot 'app\src\test\resources\deepfilternet3\golden_input_f32le.bin') "$remote/input.bin"
& $adbTool push (Join-Path $RepositoryRoot 'app\src\test\resources\deepfilternet3\golden_output_f32le.bin') "$remote/output.bin"
& $adbTool shell chmod 755 "$remote/voice_filter_tests" "$remote/audio_core_tests"
& $adbTool shell "cd $remote && LD_LIBRARY_PATH=. ./audio_core_tests && LD_LIBRARY_PATH=. ./voice_filter_tests model.tar.gz input.bin output.bin metadata.json"
if ($LASTEXITCODE -ne 0) { throw "native voice filter tests failed with exit code $LASTEXITCODE" }
