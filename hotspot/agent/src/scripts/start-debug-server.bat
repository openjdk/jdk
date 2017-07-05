@echo off

REM
REM Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

if "%1" == "-help" goto usage

:JAVA_HOME
if not exist %JAVA_HOME%\bin\java.exe goto BADJAVAHOME
if not exist %JAVA_HOME\lib\sa-jdi.jar goto BADJAVAHOME

start %JAVA_HOME%\bin\java -classpath %JAVA_HOME%\lib\sa-jdi.jar sun.jvm.hotspot.jdi.SADebugServer %1 %2 
goto end

:BADJAVAHOME
echo JAVA_HOME does not point to a working J2SE 1.5 installation.

:usage
echo Usage: start-debug-server [pid]
echo        $0 <java executable> [Dr Watson dump file]
echo  Start the JDI debug server on [pid] or [Dr Watson dump file]
echo  so that it can be debugged from a remote machine.
echo  JAVA_HOME must contain the pathname of a J2SE 1.5
echo  installation.

:end
