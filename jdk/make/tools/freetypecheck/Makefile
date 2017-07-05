#
# Copyright (c) 2007, 2008, Oracle and/or its affiliates. All rights reserved.
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

# Builds and runs test program for freetype sanity check.

BUILDDIR = ../..
include $(BUILDDIR)/common/Defs.gmk

# Default name
PROGRAM = freetype_versioncheck
FT_OBJ = $(BUILDTOOLBINDIR)/$(PROGRAM).$(OBJECT_SUFFIX)
FT_TEST = $(BUILDTOOLBINDIR)/$(PROGRAM)$(EXE_SUFFIX)

# Used on openjdk only
ifeq ($(OPENJDK),true)

# Start with CFLAGS (which gets us the required -xarch setting on solaris)
ifeq ($(PLATFORM), windows)
  FT_OPTIONS  = /nologo /c
  FREETYPE_DLL = $(FREETYPE_LIB_PATH)/freetype.dll
  FT_LD_OPTIONS  = $(FREETYPE_LIB_PATH)/freetype.lib
ifdef MT
  FT_LD_OPTIONS += /manifest
endif
else
  FT_OPTIONS  = $(CFLAGS)
  FT_LD_OPTIONS = -L$(FREETYPE_LIB_PATH)
  # Add runtime lib search path to ensure test will be runnable
  ifeq ($(PLATFORM), solaris)
    FT_LD_OPTIONS += -R $(FREETYPE_LIB_PATH) -lfreetype
  else #linux
      FT_LD_OPTIONS += -Wl,-rpath -Wl,$(FREETYPE_LIB_PATH) -lfreetype
  endif
endif
FT_OPTIONS += -I$(FREETYPE_HEADERS_PATH)
FT_OPTIONS += -I$(FREETYPE_HEADERS_PATH)/freetype2
FT_OPTIONS += -DREQUIRED_FREETYPE_VERSION=$(REQUIRED_FREETYPE_VERSION)
FT_LD_OPTIONS += $(LFLAGS_$(COMPILER_VERSION))

# Create test program
all: $(FT_TEST)
	$(FT_TEST)

# On windows we need to copy dll to test dir to ensure it will be found
#   at runtime
$(FT_TEST): freetypecheck.c
	$(prep-target)
ifeq ($(PLATFORM), windows)
	$(CC) $(FT_OPTIONS) $(CC_OBJECT_OUTPUT_FLAG)$(FT_OBJ) $<
	$(LINK) $(FT_LD_OPTIONS) /OUT:$(FT_TEST) $(FT_OBJ)
	$(CP) $(FREETYPE_DLL) $(@D)/
ifdef MT
	$(CP) $(MSVCRNN_DLL_PATH)/$(MSVCRNN_DLL) $(@D)/
	$(MT) /manifest $(FT_TEST).manifest /outputresource:$(FT_TEST);#1
endif
else
	@$(CC) $(FT_OPTIONS) $(CC_PROGRAM_OUTPUT_FLAG)$@ $< $(FT_LD_OPTIONS)
endif

else

# Inform user this is openjdk only
all:
	@$(ECHO) "The freetype files are only used with OpenJDK"

endif

clean::
	$(RM) $(FT_TEST)

