#
# Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
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

#------------------------------------------------------------------------
# CC, CPP & AS

CPP = CC
CC  = cc
AS  = $(CC) -c

ARCHFLAG = $(ARCHFLAG/$(BUILDARCH))
ARCHFLAG/i486    = -m32
ARCHFLAG/amd64   = -m64

CFLAGS     += $(ARCHFLAG)
AOUT_FLAGS += $(ARCHFLAG)
LFLAGS     += $(ARCHFLAG)
ASFLAGS    += $(ARCHFLAG)

#------------------------------------------------------------------------
# Compiler flags

# position-independent code
PICFLAG = -KPIC

CFLAGS += $(PICFLAG)
# no more exceptions
CFLAGS += -features=no%except
# Reduce code bloat by reverting back to 5.0 behavior for static initializers
CFLAGS += -features=no%split_init
# allow zero sized arrays
CFLAGS += -features=zla

# Use C++ Interpreter
ifdef CC_INTERP
  CFLAGS += -DCC_INTERP
endif

# We don't need libCstd.so and librwtools7.so, only libCrun.so
CFLAGS += -library=Crun
LIBS += -lCrun

CFLAGS += -mt
LFLAGS += -mt

# Compiler warnings are treated as errors
#WARNINGS_ARE_ERRORS = -errwarn=%all
CFLAGS_WARN/DEFAULT = $(WARNINGS_ARE_ERRORS) 
# Special cases
CFLAGS_WARN/BYFILE = $(CFLAGS_WARN/$@)$(CFLAGS_WARN/DEFAULT$(CFLAGS_WARN/$@)) 

# The flags to use for an Optimized build
OPT_CFLAGS+=-xO4
OPT_CFLAGS/NOOPT=-xO0

# Flags for creating the dependency files.
ifeq ($(shell expr $(COMPILER_REV_NUMERIC) \>= 509), 1)
DEPFLAGS = -xMMD -xMF $(DEP_DIR)/$(@:%=%.d)
endif

# -DDONT_USE_PRECOMPILED_HEADER will exclude all includes in precompiled.hpp.
CFLAGS += -DDONT_USE_PRECOMPILED_HEADER

#------------------------------------------------------------------------
# Linker flags

# Use $(MAPFLAG:FILENAME=real_file_name) to specify a map file.
MAPFLAG = -Wl,--version-script=FILENAME

# Use $(SONAMEFLAG:SONAME=soname) to specify the intrinsic name of a shared obj
SONAMEFLAG = -h SONAME

# Build shared library
SHARED_FLAG = -G

#------------------------------------------------------------------------
# Debug flags
DEBUG_CFLAGS += -g
FASTDEBUG_CFLAGS = -g0

