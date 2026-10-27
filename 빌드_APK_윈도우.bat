@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo =========================================
echo AutoControl Monitor v0.2 - APK build
 echo =========================================
echo Requires Android SDK and Java 17 or later.
echo.
call "%~dp0gradlew.bat" :app:assembleDebug
if errorlevel 1 (
  echo.
  echo [ERROR] Build failed. Read the messages above.
  echo Open the project in Android Studio and let SDK Sync finish.
  pause
  exit /b 1
)
set "APK=%~dp0app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" (
  echo [ERROR] Build completed but APK file not found.
  pause
  exit /b 2
)
copy /Y "%APK%" "%~dp0AutoControl_v02_Monitor.apk" >nul
if errorlevel 1 (
  echo [ERROR] Unable to copy APK.
  pause
  exit /b 3
)
echo.
echo [SUCCESS] AutoControl_v02_Monitor.apk
explorer.exe /select,"%~dp0AutoControl_v02_Monitor.apk"
pause
