param([switch]$ImportData, [switch]$DebugBuild, [switch]$ImportCalculator)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (-not $env:JAVA_HOME) {
        $bundledJdk = 'C:\Program Files\Android\Android Studio\jbr'
        if (Test-Path "$bundledJdk\bin\java.exe") { $env:JAVA_HOME = $bundledJdk }
        else { throw '请设置 JAVA_HOME 指向 JDK 17 或更新版本。' }
    }
    if (-not (Test-Path 'local.properties')) {
        $sdkPath = $env:ANDROID_HOME
        if (-not $sdkPath) { $sdkPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
        if (-not (Test-Path $sdkPath)) { throw '请设置 ANDROID_HOME 指向 Android SDK。' }
        $sdkValue = $sdkPath.Replace('\', '/').Replace(':', '\:')
        [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'local.properties'), "sdk.dir=$sdkValue`n")
    }
    if ($ImportData) {
        python scripts/import_data.py
        if ($LASTEXITCODE -ne 0) { throw '数据导入失败。' }
        python -m unittest discover -s scripts -p test_import.py
        if ($LASTEXITCODE -ne 0) { throw '数据合同验证失败。' }
    }
    if ($ImportCalculator -or $ImportData) {
        npm ci --prefix scripts/calculator --ignore-scripts --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw '计算器构建依赖安装失败。' }
        node scripts/calculator/build.cjs
        if ($LASTEXITCODE -ne 0) { throw '计算器导入失败。' }
        node scripts/calculator/test.cjs
        if ($LASTEXITCODE -ne 0) { throw '计算器数据合同验证失败。' }
    }
    $calcSource = Get-Content app/src/main/assets/calculator/provenance.json -Raw | ConvertFrom-Json
    $calcFiles = @{
        'app/src/main/assets/dex.json' = $calcSource.dexSha256
        'app/src/main/assets/calculator/engine.js' = $calcSource.engineSha256
        'app/src/main/assets/calculator/adapter.js' = $calcSource.adapterSha256
        'app/src/main/assets/calculator/catalog.json' = $calcSource.catalogSha256
        'scripts/calculator/adapter.js' = $calcSource.adapterSha256
        'scripts/calculator/form-aliases.json' = $calcSource.formAliasesSha256
    }
    foreach ($source in $calcSource.sources.PSObject.Properties) {
        $calcFiles["vendor/damage-calc/src/$($source.Name)"] = $source.Value
    }
    foreach ($entry in $calcFiles.GetEnumerator()) {
        if ((Get-FileHash -LiteralPath $entry.Key -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.Value) {
            throw "计算器资源与数据版本不匹配：$($entry.Key)。请运行 ./build.ps1 -ImportCalculator。"
        }
    }
    $variant = if ($DebugBuild) { 'Debug' } else { 'Preview' }
    & .\gradlew.bat ":app:assemble$variant" --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Android 构建失败。' }
    $folder = $variant.ToLowerInvariant()
    New-Item -ItemType Directory -Force dist | Out-Null
    $dest = "dist/ChampionsDex-0.1.0-$folder.apk"
    Copy-Item -LiteralPath "app/build/outputs/apk/$folder/app-$folder.apk" -Destination $dest
    $hash = (Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot "$dest.sha256"), "$hash  $([IO.Path]::GetFileName($dest))`n")
    Write-Output "APK: $dest"
    Write-Output "SHA256: $hash"
} finally { Pop-Location }
