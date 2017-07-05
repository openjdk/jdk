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


BUILDDIR = ../..
PACKAGE = sun.jdga
LIBRARY = sunwjdga
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
FILES_c = \
	dgalock.c

#
# Add use of mapfile
#
ifeq ($(ARCH_FAMILY), sparc)
FILES_m = mapfile-vers
include $(BUILDDIR)/common/Mapfile-vers.gmk
endif

#
# Rules.
#
include $(BUILDDIR)/common/Library.gmk

#
# Add to the ambient vpath to pick up files in subdirectories
#
vpath %.c   $(PLATFORM_SRC)/native/$(PKGDIR)

ifneq ($(PLATFORM), windows)
  LDLIBS =  -ldga -lX11 $(LIBDL) -lc

  CPPFLAGS += \
	-I$(SHARE_SRC)/javavm/export \
	-I$(PLATFORM_SRC)/javavm/export \
	-I$(OPENWIN_HOME)/include 
endif

LDFLAGS += -L$(OPENWIN_LIB) -R$(OPENWIN_RUNTIME_LIB)
