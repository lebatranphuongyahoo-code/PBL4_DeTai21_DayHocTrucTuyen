param([string]$MavenRepository = '')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    Push-Location web
    try { npm run build; if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' } }
    finally { Pop-Location }
    $arguments = @('-f', 'backend/pom.xml', 'verify')
    if ($MavenRepository) { $arguments += "-Dmaven.repo.local=$MavenRepository" }
    mvn @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Verification failed. Stop running JAR before rebuilding on Windows.' }
    Write-Output 'Built backend/target/classroom-web-server-0.1.0.jar with React UI.'
}
finally { Pop-Location }
