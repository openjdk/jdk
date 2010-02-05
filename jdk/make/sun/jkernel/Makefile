#
# Copyright 2008 - 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

BUILDDIR = ../..
MODULE  = jkernel
PACKAGE = sun.jkernel
LIBRARY = jkernel
PRODUCT = sun

include $(BUILDDIR)/common/Defs.gmk

#
# Use highest optimization level
#
_OPT = $(CC_HIGHEST_OPT)

#
# Java source files
#
include FILES_java.gmk
AUTO_FILES_JAVA_DIRS = sun/jkernel

LOCALE_SUFFIXES = $(JDK_LOCALES)

#
# Native source files
#

ifeq ($(ARCH_DATA_MODEL), 32) 

ifeq ($(PLATFORM), windows)

include FILES_c_windows.gmk

vpath %.cpp   $(PLATFORM_SRC)/native/sun/jkernel


VERSIONINFO_RESOURCE = $(PLATFORM_SRC)/native/sun/jkernel/kernel.rc

LDLIBS += urlmon.lib wininet.lib shlwapi.lib version.lib comctl32.lib gdi32.lib -def:$(PLATFORM_SRC)/native/sun/jkernel/kernel.def

include $(BUILDDIR)/common/Library.gmk

endif

endif

#
# Resources
#
include $(TOPDIR)/make/common/internal/Resources.gmk

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk

# If extra compression is going to be available in the deploy build enable 
# its use here by defining the JRE-relative pathname of the shared library

ifeq ($(PLATFORM), windows)
  ifdef EXTRA_COMP_INSTALL_PATH
    CPPFLAGS += -DEXTRA_COMP_INSTALL_PATH=$(EXTRA_COMP_INSTALL_PATH)
  endif
endif
