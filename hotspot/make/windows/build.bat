@echo off
REM
REM Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
REM DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
REM
REM This code is free software; you can redistribute it and/or modify it
REM under the terms of the GNU General Public License version 2 only, as
REM published by the Free Software Foundation.
REM
REM This code is distributed in the hope that it will be useful, but WITHOUT
REM ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
REM FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
REM version 2 for more details (a copy is included in the LICENSE file that
REM accompanied this code).
REM
REM You should have received a copy of the GNU General Public License version
REM 2 along with this work; if not, write to the Free Software Foundation,
REM Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
REM
REM Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
REM or visit www.oracle.com if you need additional information or have any
REM questions.
REM  
REM

REM Set HotSpotWorkSpace to the directory two steps above this script
for %%i in ("%~dp0..") do ( set HotSpotWorkSpace=%%~dpi)

REM
REM Since we don't have uname and we could be cross-compiling,
REM Use the compiler to determine which ARCH we are building
REM 
cl 2>&1 1>&3 | findstr x64>NUL
if %errorlevel% == 0 goto amd64
set VCPROJ=%HotSpotWorkSpace%\build\vs-i486\jvm.vcxproj
set PLATFORM=x86
goto testmkshome
:amd64
set VCPROJ=%HotSpotWorkSpace%\build\vs-amd64\jvm.vcxproj
set PLATFORM=x64
goto testmkshome

:testmkshome
if not "%HOTSPOTMKSHOME%" == "" goto testjavahome
if exist c:\cygwin\bin set HOTSPOTMKSHOME=c:\cygwin\bin
if not "%HOTSPOTMKSHOME%" == "" goto testjavahome
if exist c:\cygwin64\bin set HOTSPOTMKSHOME=c:\cygwin64\bin
if not "%HOTSPOTMKSHOME%" == "" goto testjavahome
echo Error: please set variable HOTSPOTMKSHOME to place where 
echo          your MKS/Cygwin installation is
echo.
goto end

:testjavahome
if not "%JAVA_HOME%" == "" goto testbuildversion
echo Error: please set variable JAVA_HOME to a bootstrap JDK 
echo.
goto end

:testbuildversion
if "%1" == "compiler1" goto testdebuglevel
if "%1" == "tiered"    goto testdebuglevel
goto usage

:testdebuglevel
if "%2" == "product"   goto build
if "%2" == "debug"     goto build
if "%2" == "fastdebug" goto build
goto usage

:build
if NOT EXIST %VCPROJ% call %~dp0\create.bat %JAVA_HOME%
msbuild /Property:Platform=%PLATFORM% /Property:Configuration=%1_%2 /v:m %VCPROJ%
goto end

:usage
echo Usage: build version debuglevel
echo.
echo where:
echo version is "compiler1" or "tiered",
echo debuglevel is "product", "debug" or "fastdebug"
exit /b 1

:end
exit /b %errorlevel%
