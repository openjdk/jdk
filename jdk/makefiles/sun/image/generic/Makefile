#
# Copyright (c) 1998, 2011, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building the generic version of medialib
#

BUILDDIR = ../../..
PACKAGE = sun.awt.medialib
LIBRARY = mlib_image
PRODUCT = sun

# Use highest level of optimization on this library
OPTIMIZATION_LEVEL = HIGHEST

include $(BUILDDIR)/common/Defs.gmk

#
# Use mapfile
#
FILES_m = mapfile-vers
include $(BUILDDIR)/common/Mapfile-vers.gmk

#
# Files
#
include FILES_c.gmk

#
# Rules
#
include $(BUILDDIR)/common/Library.gmk

#
# set compile options for solaris 32- or 64-bit build
#
ifeq ($(ARCH_DATA_MODEL), 64)
  CFLAGS += -DMLIB_OS64BIT
endif # ARCH_DATA_MODEL

ifneq ($(PLATFORM), sparc)
      OTHER_CFLAGS += -DMLIB_NO_LIBSUNMATH
endif

CPPFLAGS += \
            -I$(PLATFORM_SRC)/native/$(PKGDIR)/medialib 

OTHER_CFLAGS += -D__USE_J2D_NAMES -D__MEDIALIB_OLD_NAMES

ifneq ($(PLATFORM), windows)
	OTHER_LDLIBS = $(LIBM) $(LIBDL)
endif

