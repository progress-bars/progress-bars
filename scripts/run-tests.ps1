param(
    [string] $Maven = 'D:\apache-maven-3.8.8\bin\mvn.cmd'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $env:JAVA_HOME) {
    $fallback = 'D:\IntelliJ IDEA 2026.2.3\jbr'
    if (Test-Path -LiteralPath $fallback) {
        $env:JAVA_HOME = $fallback
    } else {
        throw 'JAVA_HOME is not set and no fallback JDK was found.'
    }
}

if (-not (Test-Path -LiteralPath $Maven)) {
    $Maven = 'mvn'
}

& $Maven -q clean test-compile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& "$env:JAVA_HOME\bin\java.exe" -cp "target\classes;target\test-classes" cn.msscn.progress.ProgressSelfTest
exit $LASTEXITCODE
