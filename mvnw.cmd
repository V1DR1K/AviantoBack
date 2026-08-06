@echo off
setlocal
set BASE_DIR=%~dp0
set VERSION=3.9.9
set MAVEN=%BASE_DIR%.mvn\wrapper\dists\apache-maven-%VERSION%\apache-maven-%VERSION%\bin\mvn.cmd
if not exist "%MAVEN%" (
  echo Run the wrapper from a Unix shell once to bootstrap Maven, or install Maven 3.9.9.
  exit /b 1
)
call "%MAVEN%" %*
