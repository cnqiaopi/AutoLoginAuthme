param(
    [switch]$Paper26,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
    $mavenArguments = @('-B', '-ntp')
    if ($Offline) { $mavenArguments += '-o' }
    if ($Paper26) { $mavenArguments += '-Ppaper-26.3' }
    $mavenArguments += @('clean', 'verify')

    & (Join-Path $PSScriptRoot 'mvnw.cmd') @mavenArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Maven verification failed (exit code $LASTEXITCODE)."
    }

    [xml]$projectXml = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'pom.xml') -Raw
    $releaseName = '{0}-{1}' -f $projectXml.project.artifactId, $projectXml.project.version
    foreach ($suffix in @('.jar', '-distribution.zip')) {
        $artifactName = $releaseName + $suffix
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot "target\$artifactName") -Destination (Join-Path $PSScriptRoot $artifactName) -Force
    }
    Write-Host "Ready: $releaseName.jar, $releaseName-distribution.zip and libs/"
} finally {
    Pop-Location
}
