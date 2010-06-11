@echo off
REM
REM Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

REM This is common environment settings for all SA
REM windows batch scripts

REM set jre\bin and jre\bin\client (or server) in PATH
REM WINDBG_HOME must point to the Windows Debugging Tools
REM installation directory

if "%SA_JAVA%" == "" goto no_sa_java

goto sa_java_set

:no_sa_java
set SA_JAVA=java

:sa_java_set

set SA_CLASSPATH=..\build\classes;..\src\share\lib\js.jar;sa.jar;lib\js.jar

set SA_LIBPATH=..\src\os\win32\windbg\i386;.\win32\i386

set OPTIONS=-Dsun.jvm.hotspot.debugger.useWindbgDebugger 
set OPTIONS=-Dsun.jvm.hotspot.debugger.windbg.imagePath="%PATH%" %OPTIONS%
set OPTIONS=-Dsun.jvm.hotspot.debugger.windbg.sdkHome="%WINDBG_HOME%" %OPTIONS%

if "%SA_DISABLE_VERS_CHK%" == "" goto vers_chk
set OPTIONS="-Dsun.jvm.hotspot.runtime.VM.disableVersionCheck %OPTIONS%"

:vers_chk
set SA_JAVA_CMD=%SA_JAVA% -showversion -cp %SA_CLASSPATH% -Djava.library.path=%SA_LIBPATH% %OPTIONS% 
