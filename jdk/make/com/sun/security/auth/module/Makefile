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

# Makefile for building auth modules.

BUILDDIR = ../../../../..
MODULE  = security-auth
PACKAGE = com.sun.security.auth.module
PRODUCT = sun

# LIBRARY name changed later below, but it is important to tell Defs some name
#    upfront so that it knows it is building a library.
LIBRARY = jaas  

# Force name of temp directory (needed due to LIBRARY change below)
LIBRARY_OUTPUT = jaas  

include $(BUILDDIR)/common/Defs.gmk

#
# Files to compile.
#

include FILES_java.gmk


ifeq ($(PLATFORM), windows)

LIBRARY = jaas_nt
  include FILES_export_windows.gmk
  include FILES_c_windows.gmk
# We need some extra libs for windows 
  ifneq ($(LIBRARY),fdlibm)
    EXTRA_LIBS += netapi32.lib user32.lib mpr.lib
  endif #fdlibm
  # code generates errors when compiled at warning level 3 and warnings are fatal
endif # windows

ifeq ($(PLATFORM), solaris)
  LIBRARY = jaas_unix
  FILES_m = mapfile-vers
  include $(BUILDDIR)/common/Mapfile-vers.gmk
  include FILES_export_unix.gmk
  include FILES_export_solaris.gmk
  include FILES_c_unix.gmk
  include FILES_c_solaris.gmk
endif # solaris

ifeq ($(PLATFORM), linux)
  LIBRARY = jaas_unix
  include FILES_export_unix.gmk
  include FILES_c_unix.gmk
endif # linux 

#
# Rules.
#
include $(BUILDDIR)/common/Library.gmk

#
# JVMDI implementation lives in the VM.
#
OTHER_LDLIBS = $(JVMLIB)
