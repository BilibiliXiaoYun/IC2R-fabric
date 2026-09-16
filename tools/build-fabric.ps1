param([string[]]$Tasks = @('compileJava'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user'
$env:TEMP = Join-Path $projectRoot '.tmp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Path $env:TEMP -Force | Out-Null
$java21 = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot'
if (Test-Path $java21) { $env:JAVA_HOME = $java21 }
$options = "-Djava.io.tmpdir=$($env:TEMP.Replace('\','/'))"
if ($env:HTTPS_PROXY) {
  $proxyUri = [uri]$env:HTTPS_PROXY
  $options += " -Dhttps.proxyHost=$($proxyUri.Host) -Dhttps.proxyPort=$($proxyUri.Port) -Dhttp.proxyHost=$($proxyUri.Host) -Dhttp.proxyPort=$($proxyUri.Port)"
}
$env:JAVA_OPTS = $options
$cachedGradle = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.13-bin" -Filter gradle.bat -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
Push-Location $projectRoot
try {
  if ($cachedGradle) { & $cachedGradle @Tasks --console=plain "-Dorg.gradle.jvmargs=-Xmx3G $options" }
  else { & .\gradlew.bat @Tasks --console=plain "-Dorg.gradle.jvmargs=-Xmx3G $options" }
  exit $LASTEXITCODE
} finally { Pop-Location }
