#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

#
# Makefile for building jabswitch.exe
#

BUILDDIR = ../..
PROGRAM = jabswitch
include $(BUILDDIR)/common/Defs.gmk

# Indicate we want the C++ compiler to do the linking.
CPLUSPLUSLIBRARY=true

VERSIONINFO_RESOURCE = $(CLOSED_PLATFORM_SRC)/native/sun/bridge/AccessBridgeStatusWindow.rc
VERSIONRES = $(TEMPDIR)/AccessBridgeStatusWindow.res

JAB_EXE= $(TEMPDIR)/jabswitch.exe

JAB_SRC = $(CLOSED_PLATFORM_SRC)/native/sun/bridge/jabswitch.cpp

JAB_MANIFEST_INP = $(CLOSED_PLATFORM_SRC)/native/sun/bridge/jabswitch.manifest
JAB_MANIFEST_OUT = $(TEMPDIR)/jabswitch.exe.intermediate.manifest

RC_FLAGS += /fo "$(VERSIONRES)"
OTHER_CPPFLAGS += /MD /Fo"$(TEMPDIR)/" /Fd"$(TEMPDIR)/" /analyze- /Od /Gd /nologo /D "WIN32" /D "_WINDOWS" /D "_UNICODE" /D "UNICODE" /RTC1 /W3 /ZI /Zc:wchar_t /EHsc 
LDDFLAGS += Advapi32.lib Version.lib User32.lib

all: buildexe copyfilejab

buildexe : 
	$(CD) $(TEMPDIR)  
	$(RC) $(RC_FLAGS) $(VERSIONINFO_RESOURCE) 
	$(CC) $(CPPFLAGS) $(JAB_SRC) $(LDDFLAGS) $(VERSIONRES) -o $(JAB_EXE) 
	$(MT) /nologo /verbose /manifest $(JAB_MANIFEST_INP) /outputresource:$(JAB_EXE)  

copyfilejab : 
	$(CP) $(JAB_EXE) $(BINDIR)

vpath %.cpp   $(CLOSED_PLATFORM_SRC)/native/sun/bridge
vpath %.rc   $(CLOSED_PLATFORM_SRC)/native/sun/bridge
