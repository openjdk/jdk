@echo off
set vcvarscmd=%1
set output=%2

call %vcvarscmd% %3 %4 %5 %6 %7 %8 %9
if exist %output% del %output%

call :extract "%PATH%", VS_PATH
call :extract "%INCLUDE%", VS_INCLUDE
call :extract "%LIB%", VS_LIB
call :extract "%VCINSTALLDIR%", VCINSTALLDIR
call :extract "%VCToolsRedistDir%", VCToolsRedistDir
call :extract "%WindowsSdkDir%", WindowsSdkDir
call :extract "%WINDOWSSDKDIR%", WINDOWSSDKDIR

exit /b 0

:extract
echo %~2=$($BASH $TOPDIR/make/scripts/fixpath.sh import "%~1 ") >> %output%
exit /b 0
