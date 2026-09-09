@echo off
chcp 65001 >nul
title Prayer Times - Autostart Installer
echo ================================================
echo    مواقيت الصلاة - التشغيل التلقائي مع الويندوز
echo ================================================
echo.

set "HTML=%~dp0prayer-times.html"
if not exist "%HTML%" (
  echo [!] ضع هذا الملف في نفس مجلد prayer-times.html ثم أعد المحاولة.
  echo.
  pause
  exit /b
)

set "URLFILE=file:///%HTML:\=/%"
set "VBS=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\prayer-times.vbs"

> "%VBS%" echo On Error Resume Next
>> "%VBS%" echo Set sh = CreateObject("Shell.Application")
>> "%VBS%" echo sh.ShellExecute "chrome.exe", "--kiosk --new-window ""%URLFILE%""", "", "open", 1
>> "%VBS%" echo If Err.Number ^<^> 0 Then
>> "%VBS%" echo   Err.Clear
>> "%VBS%" echo   sh.ShellExecute "msedge.exe", "--kiosk --new-window ""%URLFILE%""", "", "open", 1
>> "%VBS%" echo End If

echo [✓] تم التثبيت بنجاح.
echo.
echo    سيعمل التطبيق تلقائياً بملء الشاشة مع إقلاع الويندوز.
echo    المتصفح المستخدم: كروم، وإن لم يوجد: إيدج.
echo.
echo    للإلغاء لاحقاً: اضغط Win+R ثم اكتب  shell:startup
echo    واحذف الملف prayer-times.vbs
echo.
pause
