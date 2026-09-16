@echo off
setlocal
set PROJECT_ROOT=%~dp0..
set GRADLE_USER_HOME=%PROJECT_ROOT%\.gradle-user
set TEMP=%PROJECT_ROOT%\.tmp
set TMP=%TEMP%
set JAVA_TOOL_OPTIONS=-Duser.language=en -Duser.country=US
set GRADLE_HOME=%USERPROFILE%\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13
set JAVA_EXE=C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot\bin\java.exe
if not exist "%JAVA_EXE%" set JAVA_EXE=java.exe
rem Run Gradle in-process (org.gradle.daemon=false) so no daemon pipe is needed.
"%JAVA_EXE%" -Xmx3G -Djava.io.tmpdir=%PROJECT_ROOT%/.tmp -Dorg.gradle.appname=gradle ^
  -classpath "%GRADLE_HOME%\lib\gradle-gradle-cli-main-8.13.jar" ^
  org.gradle.launcher.GradleMain %* --console=plain
exit /b %ERRORLEVEL%
