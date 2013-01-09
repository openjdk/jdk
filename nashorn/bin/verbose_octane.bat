rem
rem Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
rem DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
rem 
rem This code is free software; you can redistribute it and/or modify it
rem under the terms of the GNU General Public License version 2 only, as
rem published by the Free Software Foundation.
rem 
rem This code is distributed in the hope that it will be useful, but WITHOUT
rem ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
rem FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
rem version 2 for more details (a copy is included in the LICENSE file that
rem accompanied this code).
rem 
rem You should have received a copy of the GNU General Public License version
rem 2 along with this work; if not, write to the Free Software Foundation,
rem Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
rem 
rem Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
rem or visit www.oracle.com if you need additional information or have any
rem questions.
rem
@echo off

if "%JAVA_HOME%" neq "" (
  call :run "%JAVA_HOME%/bin/java"
) else (
  call :run java
)
goto :EOF

:run
setlocal
set NASHORN_JAR=dist/nashorn.jar
set JVM_FLAGS=-Xms2G -Xmx2G -XX:-TieredCompilation -server -esa -ea -jar %NASHORN_JAR%
set JVM_FLAGS7=-Xbootclasspath/p:%NASHORN_JAR% %JVM_FLAGS%
set OCTANE_ARGS=--verbose --iterations 7

%1 -fullversion 2>&1 | findstr /L /C:"version ""1.7"
if %errorlevel% equ 0 (
  set CMD=%1 %JVM_FLAGS7%
) else (
  %1 -fullversion
  set CMD=%1 %JVM_FLAGS%
)

%CMD% test/script/basic/run-octane.js -- test/script/external/octane/box2d.js %OCTANE_ARGS%
%CMD% test/script/basic/run-octane.js -- test/script/external/octane/code-load.js %OCTANE_ARGS%
%CMD% test/script/basic/run-octane.js -- test/script/external/octane/crypto.js %OCTANE_ARGS%
%CMD% test/script/basic/run-octane.js -- test/script/external/octane/deltablue.js %OCTANE_ARGS%
%CMD% test/script/basic/run-octane.js -- test/script/external/octane/gbemu.js %OCTANE_ARGS%
%CMD% test/script/basic/run-octane.js -- test/script/external/octane/navier-stokes.js %OCTANE_ARGS%
%CMD% test/script/basic/run-octane.js -- test/script/external/octane/pdfjs.js %OCTANE_ARGS%
%CMD% test/script/basic/run-octane.js -- test/script/external/octane/raytrace.js %OCTANE_ARGS%
%CMD% test/script/basic/run-octane.js -- test/script/external/octane/regexp.js %OCTANE_ARGS%
%CMD% test/script/basic/run-octane.js -- test/script/external/octane/richards.js %OCTANE_ARGS%
%CMD% test/script/basic/run-octane.js -- test/script/external/octane/splay.js %OCTANE_ARGS%
endlocal
goto :EOF
