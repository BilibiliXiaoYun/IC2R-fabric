param(
  [string[]]$Tasks = @('compileJava'),
  [string]$Log = 'compile.log'
)
# Same environment preparation as build-fabric.ps1, but writes compiler output to a
# log file so diagnostics can be parsed without the calling shell swallowing them.
$projectRoot = Split-Path $PSScriptRoot -Parent
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user'
$env:TEMP = Join-Path $projectRoot '.tmp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Path $env:TEMP -Force | Out-Null
$java21 = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot'
if (Test-Path $java21) { $env:JAVA_HOME = $java21 }
$env:JAVA_TOOL_OPTIONS = '-Duser.language=en -Duser.country=US'
$options = "-Djava.io.tmpdir=$($env:TEMP.Replace('\','/'))"
if ($env:HTTPS_PROXY) {
  $proxyUri = [uri]$env:HTTPS_PROXY
  $options += " -Dhttps.proxyHost=$($proxyUri.Host) -Dhttps.proxyPort=$($proxyUri.Port) -Dhttp.proxyHost=$($proxyUri.Host) -Dhttp.proxyPort=$($proxyUri.Port)"
}
$env:JAVA_OPTS = $options
$cachedGradle = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.13-bin" -Filter gradle.bat -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
$logPath = Join-Path $projectRoot $Log
Push-Location $projectRoot
try {
  $gradleArgs = @($Tasks) + @('--console=plain', "-Dorg.gradle.jvmargs=-Xmx3G $options")
  if ($cachedGradle) { & $cachedGradle @gradleArgs *> $logPath }
  else { & .\gradlew.bat @gradleArgs *> $logPath }
  Write-Output "gradle-exit=$LASTEXITCODE"
} finally { Pop-Location }
