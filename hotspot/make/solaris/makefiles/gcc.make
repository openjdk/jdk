#
# Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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
# CC, CXX & AS

# If a SPEC is not set already, then use these defaults.
ifeq ($(SPEC),)
  CXX = g++
  CC  = gcc
  AS  = $(CC) -c
  MCS = /usr/ccs/bin/mcs
endif

Compiler = gcc

# -dumpversion in gcc-2.91 shows "egcs-2.91.66". In later version, it only
# prints the numbers (e.g. "2.95", "3.2.1")
CC_VER_MAJOR := $(shell $(CC) -dumpversion | sed 's/egcs-//' | cut -d'.' -f1)
CC_VER_MINOR := $(shell $(CC) -dumpversion | sed 's/egcs-//' | cut -d'.' -f2)
CC_VER_MICRO := $(shell $(CC) -dumpversion | sed 's/egcs-//' | cut -d'.' -f3)

# Check for the versions of C++ and C compilers ($CXX and $CC) used.

# Get the last thing on the line that looks like x.x+ (x is a digit).
COMPILER_REV := \
$(shell $(CXX) -dumpversion | sed 's/egcs-//' | cut -d'.' -f1)
CC_COMPILER_REV := \
$(shell $(CC) -dumpversion | sed 's/egcs-//' | cut -d'.' -f2)


# check for precompiled headers support
ifneq "$(shell expr \( $(CC_VER_MAJOR) \> 3 \) \| \( \( $(CC_VER_MAJOR) = 3 \) \& \( $(CC_VER_MINOR) \>= 4 \) \))" "0"
# Allow the user to turn off precompiled headers from the command line.
ifneq ($(USE_PRECOMPILED_HEADER),0)
PRECOMPILED_HEADER_DIR=.
PRECOMPILED_HEADER_SRC=$(GAMMADIR)/src/share/vm/precompiled/precompiled.hpp
PRECOMPILED_HEADER=$(PRECOMPILED_HEADER_DIR)/precompiled.hpp.gch
endif
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

ARCHFLAG/sparc   = -m32 -mcpu=v9
ARCHFLAG/sparcv9 = -m64 -mcpu=v9
ARCHFLAG/i486    = -m32 -march=i586
ARCHFLAG/amd64   = -m64 -march=k8


# Optional sub-directory in /usr/lib where BUILDARCH libraries are kept.
ISA_DIR=$(ISA_DIR/$(BUILDARCH))
ISA_DIR/amd64=/amd64
ISA_DIR/i486=
ISA_DIR/sparcv9=/64


CFLAGS     += $(ARCHFLAG)
AOUT_FLAGS += $(ARCHFLAG)
LFLAGS     += $(ARCHFLAG)
ASFLAGS    += $(ARCHFLAG)

ifeq ($(BUILDARCH), amd64)
ASFLAGS += -march=k8  -march=amd64
LFLAGS += -march=k8
endif


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
WARNINGS_ARE_ERRORS ?= -Werror

# Enable these warnings. See 'info gcc' about details on these options
WARNING_FLAGS = -Wpointer-arith -Wconversion -Wsign-compare -Wundef -Wformat=2
CFLAGS_WARN/DEFAULT = $(WARNINGS_ARE_ERRORS) $(WARNING_FLAGS)

# Special cases
CFLAGS_WARN/BYFILE = $(CFLAGS_WARN/$@)$(CFLAGS_WARN/DEFAULT$(CFLAGS_WARN/$@))

# optimization control flags (Used by fastdebug and release variants)
OPT_CFLAGS/NOOPT=-O0
OPT_CFLAGS/DEBUG=-O0
OPT_CFLAGS/SIZE=-Os
OPT_CFLAGS/SPEED=-O3

OPT_CFLAGS_DEFAULT ?= SPEED

# Hotspot uses very unstrict aliasing turn this optimization off
# This option is added to CFLAGS rather than OPT_CFLAGS
# so that OPT_CFLAGS overrides get this option too.
CFLAGS += -fno-strict-aliasing

ifdef OPT_CFLAGS
  ifneq ("$(origin OPT_CFLAGS)", "command line")
    $(error " Use OPT_EXTRAS instead of OPT_CFLAGS to add extra flags to OPT_CFLAGS.")
  endif
endif

OPT_CFLAGS = $(OPT_CFLAGS/$(OPT_CFLAGS_DEFAULT)) $(OPT_EXTRAS)

# The gcc compiler segv's on ia64 when compiling bytecodeInterpreter.cpp
# if we use expensive-optimizations
# Note: all ia64 setting reflect the ones for linux
# No actial testing was performed: there is no Solaris on ia64 presently
ifeq ($(BUILDARCH), ia64)
OPT_CFLAGS/bytecodeInterpreter.o += -fno-expensive-optimizations
endif

# Work around some compiler bugs.
ifeq ($(USE_CLANG), true)
  ifeq ($(shell expr $(CC_VER_MAJOR) = 4 \& $(CC_VER_MINOR) = 2), 1)
    OPT_CFLAGS/loopTransform.o += $(OPT_CFLAGS/NOOPT)
  endif
else
  # Do not allow GCC 4.1.1
  ifeq ($(shell expr $(CC_VER_MAJOR) = 4 \& $(CC_VER_MINOR) = 1 \& $(CC_VER_MICRO) = 1), 1)
    $(error "GCC $(CC_VER_MAJOR).$(CC_VER_MINOR).$(CC_VER_MICRO) not supported because of https://gcc.gnu.org/bugzilla/show_bug.cgi?id=27724")
  endif
  # 6835796. Problem in GCC 4.3.0 with mulnode.o optimized compilation.
  ifeq ($(shell expr $(CC_VER_MAJOR) = 4 \& $(CC_VER_MINOR) = 3), 1)
    OPT_CFLAGS/mulnode.o += $(OPT_CFLAGS/NOOPT)
  endif
endif

# Flags for generating make dependency flags.
ifneq ($(CC_VER_MAJOR), 2)
DEPFLAGS = -fpch-deps -MMD -MP -MF $(DEP_DIR)/$(@:%=%.d)
endif

# -DDONT_USE_PRECOMPILED_HEADER will exclude all includes in precompiled.hpp.
ifeq ($(USE_PRECOMPILED_HEADER),0)
CFLAGS += -DDONT_USE_PRECOMPILED_HEADER
endif

#------------------------------------------------------------------------
# Linker flags

# statically link libstdc++.so, work with gcc but ignored by g++
STATIC_STDCXX = -Wl,-Bstatic -lstdc++ -Wl,-Bdynamic


ifdef USE_GNULD
  # statically link libgcc and/or libgcc_s, libgcc does not exist before gcc-3.x.
  ifneq ($(CC_VER_MAJOR), 2)
    STATIC_LIBGCC += -static-libgcc
  endif

  # Enable linker optimization
  LFLAGS += -Xlinker -O1

  ifneq (, findstring(debug,$(BUILD_FLAVOR)))
    # for relocations read-only
    LFLAGS += -Xlinker -z -Xlinker relro

    ifeq ($(BUILD_FLAVOR), debug)
      # disable incremental relocations linking
      LFLAGS += -Xlinker -z -Xlinker now
    endif
  endif

  ifeq ($(BUILDARCH), ia64)
    # Note: all ia64 setting reflect the ones for linux
    # No actual testing was performed: there is no Solaris on ia64 presently
    LFLAGS += -Wl,-relax
  endif

  # Use $(MAPFLAG:FILENAME=real_file_name) to specify a map file.
  MAPFLAG = -Xlinker --version-script=FILENAME
else
  MAPFLAG = -Xlinker -M -Xlinker FILENAME
endif

# Use $(SONAMEFLAG:SONAME=soname) to specify the intrinsic name of a shared obj
SONAMEFLAG = -Xlinker -soname=SONAME

# Build shared library
SHARED_FLAG = -shared

#------------------------------------------------------------------------
# Debug flags

# Allow no optimizations.
DEBUG_CFLAGS=-O0

# Enable debug symbols
DEBUG_CFLAGS += -g

# Enable bounds checking.
ifeq "$(shell expr \( $(CC_VER_MAJOR) \> 3 \) )" "1"
  # stack smashing checks.
  DEBUG_CFLAGS += -fstack-protector-all --param ssp-buffer-size=1
endif
