#
# Copyright 2000-2008 Sun Microsystems, Inc.  All Rights Reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Sun designates this
# particular file as subject to the "Classpath" exception as provided
# by Sun in the LICENSE file that accompanied this code.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#

#
# Makefile for building javaw.exe (for windows)
#

BUILDDIR = ../../..
MODULE  = base
PROGRAM = javaw
PRODUCT = java

#
# Statically link javaw to avoid the dependency on msvcrNN.dll.  This
# must be set before Defs.gmk is included.
#
MS_RUNTIME_STATIC = true
#
# Statically link javaw to avoid the dependency on jli.dll.  This
# must be set before Program.gmk is included.
#
STATIC_JLI = true

include $(BUILDDIR)/common/Defs.gmk

OTHER_CPPFLAGS += -DJAVAW
LDLIBS_COMMON +=  user32.lib comctl32.lib

# Override the default version info with our own resource file (see 5106536)
ifeq ($(PLATFORM), windows)
ifdef OPENJDK
    RC_FLAGS += -i "$(PLATFORM_SRC)/resource/icons"
else
    RC_FLAGS += -i "$(CLOSED_SRC)/windows/native/sun/windows"
endif
  VERSIONINFO_RESOURCE = $(PLATFORM_SRC)/resource/java.rc
endif

#
# Rules.
#
include $(BUILDDIR)/common/Program.gmk
OTHER_CPPFLAGS += -DEXPAND_CLASSPATH_WILDCARDS
OTHER_CPPFLAGS += -DLAUNCHER_NAME='"$(LAUNCHER_NAME)"'

