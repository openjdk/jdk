#
# Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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

# Builds test program for freetype sanity check.
#
# Makefile should be called with following input parameters
#   FT_TEST    - full name of test program
#   FT_HEADERS - path to freetype headers
#   FT_LIB     - location of directory with library
#   XARCH      - xarch option if required

BUILDDIR = ../..
include $(BUILDDIR)/common/Defs.gmk

#test program is expected in the TEMPDIR
FT_TEST_PATH = $(TEMPDIR)/$(FT_TEST)

all: $(FT_TEST_PATH)

# Start with CFLAGS (which gets us the required -xarch setting on solaris)
ifeq ($(PLATFORM), windows)
  FT_OPTIONS  =
else
  FT_OPTIONS  = $(CFLAGS)
endif

FT_OPTIONS += -I$(FT_HEADERS) -I$(FT_HEADERS)/freetype2
FT_OPTIONS += $(XARCH)

#add runtime library search path
ifeq ($(PLATFORM), windows)
  FREETYPE_LIB=$(FT_LIB)/freetype.lib
  FREETYPE_DLL=$(FT_LIB)/freetype.dll
  DFLAG=/D
else
  FT_OPTIONS += -L$(FT_LIB)
  DFLAG = -D

  #add runtime lib search path to ensure test will be runnable
  ifeq ($(PLATFORM), solaris)
    FT_OPTIONS += -R $(FT_LIB) -lfreetype
  else #linux
      FT_OPTIONS += -Wl,-rpath -Wl,$(FT_LIB) -lfreetype
  endif
endif

FT_OPTIONS += $(DFLAG)REQUIRED_FREETYPE_VERSION=$(REQUIRED_FREETYPE_VERSION)

# On windows we need to copy dll to test dir
#  ti ensure it will be found in runtime
$(FT_TEST_PATH): freetypecheck.c
	@$(CC) $(FT_OPTIONS) $(CC_PROGRAM_OUTPUT_FLAG)$@ $< $(FREETYPE_LIB) $(LFLAGS_$(COMPILER_VERSION))
ifeq ($(PLATFORM), windows)
	@$(CP) $(FREETYPE_DLL) `dirname $@`
endif
