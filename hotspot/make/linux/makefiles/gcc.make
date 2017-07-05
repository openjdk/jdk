#
# Copyright 1999-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#  
#

#------------------------------------------------------------------------
# CC, CPP & AS

CPP = g++
CC  = gcc
AS  = $(CC) -c

# -dumpversion in gcc-2.91 shows "egcs-2.91.66". In later version, it only
# prints the numbers (e.g. "2.95", "3.2.1")
CC_VER_MAJOR := $(shell $(CC) -dumpversion | sed 's/egcs-//' | cut -d'.' -f1)
CC_VER_MINOR := $(shell $(CC) -dumpversion | sed 's/egcs-//' | cut -d'.' -f2)

# check for precompiled headers support
ifneq "$(shell expr \( $(CC_VER_MAJOR) \> 3 \) \| \( \( $(CC_VER_MAJOR) = 3 \) \& \( $(CC_VER_MINOR) \>= 4 \) \))" "0"
USE_PRECOMPILED_HEADER=1
PRECOMPILED_HEADER_DIR=.
PRECOMPILED_HEADER=$(PRECOMPILED_HEADER_DIR)/incls/_precompiled.incl.gch
endif


#------------------------------------------------------------------------
# Compiler flags

# position-independent code
PICFLAG = -fPIC

VM_PICFLAG/LIBJVM = $(PICFLAG)
VM_PICFLAG/AOUT   =
VM_PICFLAG        = $(VM_PICFLAG/$(LINK_INTO))

CFLAGS += $(VM_PICFLAG)
CFLAGS += -fno-rtti
CFLAGS += -fno-exceptions
CFLAGS += -D_REENTRANT
CFLAGS += -fcheck-new

ARCHFLAG = $(ARCHFLAG/$(BUILDARCH))
ARCHFLAG/i486    = -m32 -march=i586
ARCHFLAG/amd64   = -m64
ARCHFLAG/ia64    =
ARCHFLAG/sparc   = -m32 -mcpu=v9
ARCHFLAG/sparcv9 = -m64 -mcpu=v9

CFLAGS     += $(ARCHFLAG)
AOUT_FLAGS += $(ARCHFLAG)
LFLAGS     += $(ARCHFLAG)
ASFLAGS    += $(ARCHFLAG)

# Use C++ Interpreter
ifdef CC_INTERP
  CFLAGS += -DCC_INTERP
endif

# Keep temporary files (.ii, .s)
ifdef NEED_ASM
  CFLAGS += -save-temps
else
  CFLAGS += -pipe
endif

# Compiler warnings are treated as errors
WARNINGS_ARE_ERRORS = -Werror

# Except for a few acceptable ones
# Since GCC 4.3, -Wconversion has changed its meanings to warn these implicit
# conversions which might affect the values. To avoid that, we need to turn
# it off explicitly. 
ifneq "$(shell expr \( $(CC_VER_MAJOR) \> 4 \) \| \( \( $(CC_VER_MAJOR) = 4 \) \& \( $(CC_VER_MINOR) \>= 3 \) \))" "0"
ACCEPTABLE_WARNINGS = -Wpointer-arith -Wsign-compare
else
ACCEPTABLE_WARNINGS = -Wpointer-arith -Wconversion -Wsign-compare
endif

CFLAGS_WARN/DEFAULT = $(WARNINGS_ARE_ERRORS) $(ACCEPTABLE_WARNINGS)
# Special cases
CFLAGS_WARN/BYFILE = $(CFLAGS_WARN/$@)$(CFLAGS_WARN/DEFAULT$(CFLAGS_WARN/$@)) 

# The flags to use for an Optimized g++ build
OPT_CFLAGS += -O3

# Hotspot uses very unstrict aliasing turn this optimization off
OPT_CFLAGS += -fno-strict-aliasing

# The gcc compiler segv's on ia64 when compiling bytecodeInterpreter.cpp 
# if we use expensive-optimizations
ifeq ($(BUILDARCH), ia64)
OPT_CFLAGS += -fno-expensive-optimizations
endif

OPT_CFLAGS/NOOPT=-O0

#------------------------------------------------------------------------
# Linker flags

# statically link libstdc++.so, work with gcc but ignored by g++
STATIC_STDCXX = -Wl,-Bstatic -lstdc++ -Wl,-Bdynamic

# statically link libgcc and/or libgcc_s, libgcc does not exist before gcc-3.x.
ifneq ("${CC_VER_MAJOR}", "2")
STATIC_LIBGCC += -static-libgcc
endif

ifeq ($(BUILDARCH), ia64)
LFLAGS += -Wl,-relax
endif

# Enable linker optimization
LFLAGS += -Xlinker -O1

# If this is a --hash-style=gnu system, use --hash-style=both
#   The gnu .hash section won't work on some Linux systems like SuSE 10.
_HAS_HASH_STYLE_GNU:=$(shell $(CC) -dumpspecs | grep -- '--hash-style=gnu')
ifneq ($(_HAS_HASH_STYLE_GNU),)
  LDFLAGS_HASH_STYLE = -Wl,--hash-style=both
endif
LFLAGS += $(LDFLAGS_HASH_STYLE)

# Use $(MAPFLAG:FILENAME=real_file_name) to specify a map file.
MAPFLAG = -Xlinker --version-script=FILENAME

# Use $(SONAMEFLAG:SONAME=soname) to specify the intrinsic name of a shared obj
SONAMEFLAG = -Xlinker -soname=SONAME

# Build shared library
SHARED_FLAG = -shared

# Keep symbols even they are not used
AOUT_FLAGS += -export-dynamic

#------------------------------------------------------------------------
# Debug flags

# Use the stabs format for debugging information (this is the default
# on gcc-2.91). It's good enough, has all the information about line
# numbers and local variables, and libjvm_g.so is only about 16M.
# Change this back to "-g" if you want the most expressive format.
# (warning: that could easily inflate libjvm_g.so to 150M!)
# Note: The Itanium gcc compiler crashes when using -gstabs.
DEBUG_CFLAGS/ia64  = -g
DEBUG_CFLAGS/amd64 = -g
DEBUG_CFLAGS += $(DEBUG_CFLAGS/$(BUILDARCH))
ifeq ($(DEBUG_CFLAGS/$(BUILDARCH)),)
DEBUG_CFLAGS += -gstabs
endif
