#
# Copyright (c) 1999, 2012, Oracle and/or its affiliates. All rights reserved.
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

OS_VENDOR = $(shell uname -s)

#------------------------------------------------------------------------
# CC, CXX & AS

# If a SPEC is not set already, then use these defaults.
ifeq ($(SPEC),)
  # When cross-compiling the ALT_COMPILER_PATH points
  # to the cross-compilation toolset
  ifdef CROSS_COMPILE_ARCH
    CXX = $(ALT_COMPILER_PATH)/g++
    CC  = $(ALT_COMPILER_PATH)/gcc
    HOSTCXX = g++
    HOSTCC  = gcc
  else ifneq ($(OS_VENDOR), Darwin)
    CXX = g++
    CC  = gcc
    HOSTCXX = $(CXX)
    HOSTCC  = $(CC)
  endif

  # i486 hotspot requires -mstackrealign on Darwin.
  # llvm-gcc supports this in Xcode 3.2.6 and 4.0.
  # gcc-4.0 supports this on earlier versions.
  # Prefer llvm-gcc where available.
  ifeq ($(OS_VENDOR), Darwin)
    ifeq ($(origin CXX), default)
      CXX = llvm-g++
    endif
    ifeq ($(origin CC), default)
      CC  = llvm-gcc
    endif

    ifeq ($(ARCH), i486)
      LLVM_SUPPORTS_STACKREALIGN := $(shell \
       [ "0"`llvm-gcc -v 2>&1 | grep LLVM | sed -E "s/.*LLVM build ([0-9]+).*/\1/"` -gt "2333" ] \
       && echo true || echo false)

      ifeq ($(LLVM_SUPPORTS_STACKREALIGN), true)
        CXX32 ?= llvm-g++
        CC32  ?= llvm-gcc
      else
        CXX32 ?= g++-4.0
        CC32  ?= gcc-4.0
      endif
      CXX = $(CXX32)
      CC  = $(CC32)
    endif

    HOSTCXX = $(CXX)
    HOSTCC  = $(CC)
  endif

  AS   = $(CC) -c -x assembler-with-cpp
endif


# -dumpversion in gcc-2.91 shows "egcs-2.91.66". In later version, it only
# prints the numbers (e.g. "2.95", "3.2.1")
CC_VER_MAJOR := $(shell $(CC) -dumpversion | sed 's/egcs-//' | cut -d'.' -f1)
CC_VER_MINOR := $(shell $(CC) -dumpversion | sed 's/egcs-//' | cut -d'.' -f2)

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

ifeq ($(JVM_VARIANT_ZERO), true)
  CFLAGS += $(LIBFFI_CFLAGS)
endif
ifeq ($(JVM_VARIANT_ZEROSHARK), true)
  CFLAGS += $(LIBFFI_CFLAGS)
  CFLAGS += $(LLVM_CFLAGS)
endif
CFLAGS += $(VM_PICFLAG)
CFLAGS += -fno-rtti
CFLAGS += -fno-exceptions
CFLAGS += -pthread
CFLAGS += -fcheck-new
# version 4 and above support fvisibility=hidden (matches jni_x86.h file)
# except 4.1.2 gives pointless warnings that can't be disabled (afaik)
ifneq "$(shell expr \( $(CC_VER_MAJOR) \> 4 \) \| \( \( $(CC_VER_MAJOR) = 4 \) \& \( $(CC_VER_MINOR) \>= 3 \) \))" "0"
CFLAGS += -fvisibility=hidden
endif

ARCHFLAG = $(ARCHFLAG/$(BUILDARCH))
ARCHFLAG/i486    = -m32 -march=i586
ARCHFLAG/amd64   = -m64
ARCHFLAG/ia64    =
ARCHFLAG/sparc   = -m32 -mcpu=v9
ARCHFLAG/sparcv9 = -m64 -mcpu=v9
ARCHFLAG/zero    = $(ZERO_ARCHFLAG)

# Darwin-specific build flags
ifeq ($(OS_VENDOR), Darwin)
  # Ineffecient 16-byte stack re-alignment on Darwin/IA32
  ARCHFLAG/i486 += -mstackrealign
endif

CFLAGS     += $(ARCHFLAG)
AOUT_FLAGS += $(ARCHFLAG)
LFLAGS     += $(ARCHFLAG)
ASFLAGS    += $(ARCHFLAG)

ifdef E500V2
CFLAGS += -DE500V2
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
ifneq ($(COMPILER_WARNINGS_FATAL),false)
  WARNINGS_ARE_ERRORS = -Werror
endif

# Except for a few acceptable ones
# Since GCC 4.3, -Wconversion has changed its meanings to warn these implicit
# conversions which might affect the values. To avoid that, we need to turn
# it off explicitly. 
ifneq "$(shell expr \( $(CC_VER_MAJOR) \> 4 \) \| \( \( $(CC_VER_MAJOR) = 4 \) \& \( $(CC_VER_MINOR) \>= 3 \) \))" "0"
WARNING_FLAGS = -Wpointer-arith -Wsign-compare -Wundef
else
WARNING_FLAGS = -Wpointer-arith -Wconversion -Wsign-compare -Wundef
endif

CFLAGS_WARN/DEFAULT = $(WARNINGS_ARE_ERRORS) $(WARNING_FLAGS)
# Special cases
CFLAGS_WARN/BYFILE = $(CFLAGS_WARN/$@)$(CFLAGS_WARN/DEFAULT$(CFLAGS_WARN/$@)) 
# XXXDARWIN: for _dyld_bind_fully_image_containing_address
ifeq ($(OS_VENDOR), Darwin)
  CFLAGS_WARN/os_bsd.o = $(CFLAGS_WARN/DEFAULT) -Wno-deprecated-declarations
endif

OPT_CFLAGS/SIZE=-Os
OPT_CFLAGS/SPEED=-O3

# Hotspot uses very unstrict aliasing turn this optimization off
# This option is added to CFLAGS rather than OPT_CFLAGS
# so that OPT_CFLAGS overrides get this option too.
CFLAGS += -fno-strict-aliasing

# The flags to use for an Optimized g++ build
ifeq ($(OS_VENDOR), Darwin)
  # use -Os by default, unless -O3 can be proved to be worth the cost, as per policy
  # <http://wikis.sun.com/display/OpenJDK/Mac+OS+X+Port+Compilers>
  OPT_CFLAGS_DEFAULT ?= SIZE
else
  OPT_CFLAGS_DEFAULT ?= SPEED
endif

ifdef OPT_CFLAGS
  ifneq ("$(origin OPT_CFLAGS)", "command line")
    $(error " Use OPT_EXTRAS instead of OPT_CFLAGS to add extra flags to OPT_CFLAGS.")
  endif
endif

OPT_CFLAGS = $(OPT_CFLAGS/$(OPT_CFLAGS_DEFAULT)) $(OPT_EXTRAS)

# The gcc compiler segv's on ia64 when compiling bytecodeInterpreter.cpp
# if we use expensive-optimizations
ifeq ($(BUILDARCH), ia64)
OPT_CFLAGS += -fno-expensive-optimizations
endif

OPT_CFLAGS/NOOPT=-O0

# 6835796. Problem in GCC 4.3.0 with mulnode.o optimized compilation. 
ifneq "$(shell expr \( \( $(CC_VER_MAJOR) = 4 \) \& \( $(CC_VER_MINOR) = 3 \) \))" "0"
OPT_CFLAGS/mulnode.o += -O0
endif

# Flags for generating make dependency flags.
ifneq ("${CC_VER_MAJOR}", "2")
DEPFLAGS = -fpch-deps -MMD -MP -MF $(DEP_DIR)/$(@:%=%.d)
endif

# -DDONT_USE_PRECOMPILED_HEADER will exclude all includes in precompiled.hpp.
ifeq ($(USE_PRECOMPILED_HEADER),0)
CFLAGS += -DDONT_USE_PRECOMPILED_HEADER
endif

ifeq ($(OS_VENDOR), Darwin)
  # Setting these parameters makes it an error to link to macosx APIs that are 
  # newer than the given OS version and makes the linked binaries compatible even
  # if built on a newer version of the OS.
  # The expected format is X.Y.Z
  ifeq ($(MACOSX_VERSION_MIN),)
    MACOSX_VERSION_MIN=10.7.0
  endif
  # The macro takes the version with no dots, ex: 1070
  CFLAGS += -DMAC_OS_X_VERSION_MAX_ALLOWED=$(subst .,,$(MACOSX_VERSION_MIN)) \
            -mmacosx-version-min=$(MACOSX_VERSION_MIN)
  LDFLAGS += -mmacosx-version-min=$(MACOSX_VERSION_MIN)
endif

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

# Use $(MAPFLAG:FILENAME=real_file_name) to specify a map file.
MAPFLAG = -Xlinker --version-script=FILENAME

#
# Shared Library
#
ifeq ($(OS_VENDOR), Darwin)
  # Standard linker flags
  LFLAGS +=

  # Darwin doesn't use ELF and doesn't support version scripts
  LDNOMAP = true

  # Use $(SONAMEFLAG:SONAME=soname) to specify the intrinsic name of a shared obj
  SONAMEFLAG =

  # Build shared library
  SHARED_FLAG = -Wl,-install_name,@rpath/$(@F) -dynamiclib -compatibility_version 1.0.0 -current_version 1.0.0 $(VM_PICFLAG)

  # Keep symbols even they are not used
  #AOUT_FLAGS += -Xlinker -export-dynamic
else
  # Enable linker optimization
  LFLAGS += -Xlinker -O1

  # Use $(SONAMEFLAG:SONAME=soname) to specify the intrinsic name of a shared obj
  SONAMEFLAG = -Xlinker -soname=SONAME

  # Build shared library
  SHARED_FLAG = -shared $(VM_PICFLAG)

  # Keep symbols even they are not used
  AOUT_FLAGS += -Xlinker -export-dynamic
endif

#------------------------------------------------------------------------
# Debug flags

# Use the stabs format for debugging information (this is the default
# on gcc-2.91). It's good enough, has all the information about line
# numbers and local variables, and libjvm.so is only about 16M.
# Change this back to "-g" if you want the most expressive format.
# (warning: that could easily inflate libjvm.so to 150M!)
# Note: The Itanium gcc compiler crashes when using -gstabs.
DEBUG_CFLAGS/ia64  = -g
DEBUG_CFLAGS/amd64 = -g
DEBUG_CFLAGS/arm   = -g
DEBUG_CFLAGS/ppc   = -g
DEBUG_CFLAGS += $(DEBUG_CFLAGS/$(BUILDARCH))
ifeq ($(DEBUG_CFLAGS/$(BUILDARCH)),)
DEBUG_CFLAGS += -gstabs
endif

# DEBUG_BINARIES overrides everything, use full -g debug information
ifeq ($(DEBUG_BINARIES), true)
  DEBUG_CFLAGS = -g
  CFLAGS += $(DEBUG_CFLAGS)
endif

# If we are building HEADLESS, pass on to VM
# so it can set the java.awt.headless property
ifdef HEADLESS
CFLAGS += -DHEADLESS
endif

# We are building Embedded for a small device
# favor code space over speed
ifdef MINIMIZE_RAM_USAGE
CFLAGS += -DMINIMIZE_RAM_USAGE
endif
