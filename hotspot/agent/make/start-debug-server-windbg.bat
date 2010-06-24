@echo off
REM
REM Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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

call saenv.bat

REM check for .\sa.jar, if it does not exist
REM assume that we are in build configuration.

if not exist .\sa.jar goto IN_BUILD_CONF
set SA_CLASSPATH=.\sa.jar
goto EXEC_CMD

:IN_BUILD_CONF
set SA_CLASSPATH=..\build\classes

:EXEC_CMD
%SA_JAVA% -classpath %SA_CLASSPATH% -Djava.rmi.server.codebase=file:/%SA_CLASSPATH% -Djava.security.policy=grantAll.policy -Djava.library.path=%SA_LIBPATH% %OPTIONS% sun.jvm.hotspot.DebugServer %1 %2 %3 %4 %5 %6 %7 %8 %9
