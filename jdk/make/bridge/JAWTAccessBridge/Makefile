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
# Makefile for building JAWTAccessBridge
#

BUILDDIR = ../..
LIBRARY = JAWTAccessBridge$(ABSUFFIX)
include $(BUILDDIR)/common/Defs.gmk

# Indicate we want the C++ compiler to do the linking.
CPLUSPLUSLIBRARY=true

ifeq ($(ARCH_DATA_MODEL), 64)
  ABSUFFIX = -64
  ACCESSBRIDGE_ARCH = ACCESSBRIDGE_ARCH_64
  ABRIDGE_MACHINE=X64
else
ifeq ($(ARCH_DATA_MODEL), 32)
  ABRIDGE_MACHINE=I386
ifdef ABLEGACY
  ABSUFFIX = 
  ACCESSBRIDGE_ARCH = ACCESSBRIDGE_ARCH_LEGACY
else
  ABSUFFIX = -32
  ACCESSBRIDGE_ARCH = ACCESSBRIDGE_ARCH_32
endif
endif
endif

include FILES_cpp.gmk

VERSIONINFO_RESOURCE = $(CLOSED_PLATFORM_SRC)/native/sun/bridge/AccessBridgeStatusWindow.rc

OTHER_CPPFLAGS += -D$(ACCESSBRIDGE_ARCH) -I "$(INCLUDEDIR)" -I "$(PLATFORM_INCLUDE)"
LDLIBS += kernel32.lib user32.lib gdi32.lib winspool.lib jawt.lib comdlg32.lib advapi32.lib shell32.lib ole32.lib oleaut32.lib \
	uuid.lib odbc32.lib odbccp32.lib /subsystem:windows /dll /incremental:no /machine:$(ABRIDGE_MACHINE) \
	/def:$(CLOSED_PLATFORM_SRC)/native/sun/bridge/JAWTAccessBridge.DEF  /libpath:"$(LIBDIR)"

#
# Rules
#
include $(BUILDDIR)/common/Library.gmk

vpath %.cpp   $(CLOSED_PLATFORM_SRC)/native/sun/bridge
vpath %.RC   $(CLOSED_PLATFORM_SRC)/native/sun/bridge
