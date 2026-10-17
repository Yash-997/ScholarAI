@REM ----------------------------------------------------------------------------
@REM Maven Startup Batch script for Windows
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set ERROR_CODE=0

@REM Check configured MAVEN_HOME or local user dists
if "%MAVEN_HOME%"=="" (
  for /r "%USERPROFILE%\.m2\wrapper\dists" %%d in (bin) do (
    if exist "%%d\mvn.cmd" (
      set "MAVEN_HOME=%%~dpd"
      goto :found_maven
    )
  )
)

:found_maven
if not "%MAVEN_HOME%"=="" (
  if "%MAVEN_HOME:~-1%"=="\" set "MAVEN_HOME=%MAVEN_HOME:~0,-1%"
  "%MAVEN_HOME%\bin\mvn.cmd" %*
  exit /b %ERRORLEVEL%
)

@REM Fallback to system mvn
where mvn >nul 2>nul
if %ERRORLEVEL% equ 0 (
  mvn %*
  exit /b %ERRORLEVEL%
)

echo [ERROR] Apache Maven was not found on PATH.
exit /b 1
