param([ValidateSet('start','stop','status','logs')][string]$Action = 'status')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$linuxRoot = (& wsl -d Ubuntu-24.04 --exec wslpath -u $projectRoot.Replace('\','/')).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Ubuntu-24.04 WSL is required.' }
$composeArgs = @('-d','Ubuntu-24.04','-u','root','--','docker','compose','--env-file',"$linuxRoot/backend/.env",'-f',"$linuxRoot/backend/compose.yaml")
switch ($Action) {
    'start' {
        if (-not (Test-Path -LiteralPath "$projectRoot/backend/.env")) {
            throw 'Create backend/.env with POSTGRES_PASSWORD before starting.'
        }
        Start-Process -FilePath wsl.exe -ArgumentList @('-d','Ubuntu-24.04','-u','root','--exec','sh',"$linuxRoot/backend/database/keepalive.sh") -WindowStyle Hidden
        & wsl -d Ubuntu-24.04 -u root -- systemctl start docker
        if ($LASTEXITCODE -ne 0) { throw 'Docker Engine did not start.' }
        & wsl @composeArgs up -d --wait
    }
    'stop' {
        & wsl @composeArgs stop
        if ($LASTEXITCODE -ne 0) { throw 'DB container did not stop.' }
        & wsl -d Ubuntu-24.04 -u root --exec sh "$linuxRoot/backend/database/keepalive.sh" stop
    }
    'status' { & wsl @composeArgs ps }
    'logs' { & wsl @composeArgs logs --tail 60 postgres }
}
if ($LASTEXITCODE -ne 0) { throw "Docker compose $Action failed." }
