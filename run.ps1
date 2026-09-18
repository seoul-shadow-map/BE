param([ValidateSet('build','start','test')][string]$Action='start')
$ErrorActionPreference='Stop'
$env:GRADLE_USER_HOME=Join-Path (Split-Path -Parent $PSScriptRoot) '.local\gradle'
Get-Content -LiteralPath (Join-Path $PSScriptRoot '.env') | ForEach-Object {
    if ($_ -match '^\s*([A-Z_][A-Z0-9_]*)\s*=(.*)$') {
        $key=$Matches[1]; $value=$Matches[2].Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) { $value=$value.Substring(1,$value.Length-2) }
        if ($key -in @('API_DB_PASSWORD','MIGRATION_DB_PASSWORD','API_PORT','DB_HOST','DB_PORT','DB_NAME')) {
            [Environment]::SetEnvironmentVariable($key,$value,'Process')
        }
    }
}
Push-Location $PSScriptRoot
try {
    switch($Action) {
        'build' { & .\gradlew.bat --no-daemon build }
        'test' { & .\gradlew.bat --no-daemon test }
        'start' { & .\gradlew.bat --no-daemon bootRun }
    }
    if ($LASTEXITCODE -ne 0) { throw "Backend $Action failed." }
} finally { Pop-Location }
