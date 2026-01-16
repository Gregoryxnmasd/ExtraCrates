@echo off
setlocal

if exist "gradle\wrapper\gradle-wrapper.jar" (
  echo Gradle wrapper jar already exists.
  exit /b 0
)

gradle wrapper
if errorlevel 1 (
  echo Gradle is not installed. Install Gradle or use your IDE's Gradle tooling to generate the wrapper.
  exit /b 1
)

echo Gradle wrapper generated.
