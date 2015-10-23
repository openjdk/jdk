#
# Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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
  # When cross-compiling the ALT_COMPILER_PATH points
  # to the cross-compilation toolset
  ifdef CROSS_COMPILE_ARCH
    CXX = $(ALT_COMPILER_PATH)/g++
    CC  = $(ALT_COMPILER_PATH)/gcc
    HOSTCXX = g++
    HOSTCC  = gcc
    STRIP = $(ALT_COMPILER_PATH)/strip
  else
    ifeq ($(USE_CLANG), true)
      CXX = clang++
      CC  = clang
    else
      CXX = g++
      CC  = gcc
    endif

    HOSTCXX = $(CXX)
    HOSTCC  = $(CC)
    STRIP = strip
  endif
  AS  = $(CC) -c
endif


ifeq ($(USE_CLANG), true)
  CC_VER_MAJOR := $(shell $(CC) -v 2>&1 | grep version | sed "s/.*version \([0-9]*\.[0-9]*\).*/\1/" | cut -d'.' -f1)
  CC_VER_MINOR := $(shell $(CC) -v 2>&1 | grep version | sed "s/.*version \([0-9]*\.[0-9]*\).*/\1/" | cut -d'.' -f2)
else
  # -dumpversion in gcc-2.91 shows "egcs-2.91.66". In later version, it only
  # prints the numbers (e.g. "2.95", "3.2.1")
  CC_VER_MAJOR := $(shell $(CC) -dumpversion | sed 's/egcs-//' | cut -d'.' -f1)
  CC_VER_MINOR := $(shell $(CC) -dumpversion | sed 's/egcs-//' | cut -d'.' -f2)
  CC_VER_MICRO := $(shell $(CC) -dumpversion | sed 's/egcs-//' | cut -d'.' -f3)
endif

ifeq ($(USE_CLANG), true)
  # Clang has precompiled headers support by default, but the user can switch
  # it off by using 'USE_PRECOMPILED_HEADER=0'.
  ifdef LP64
    ifeq ($(USE_PRECOMPILED_HEADER),)
      USE_PRECOMPILED_HEADER=1
    endif
  else
    # We don't support precompiled headers on 32-bit builds because there some files are
    # compiled with -fPIC while others are compiled without (see 'NONPIC_OBJ_FILES' rules.make)
    # Clang produces an error if the PCH file was compiled with other options than the actual compilation unit.
    USE_PRECOMPILED_HEADER=0
  endif

  ifeq ($(USE_PRECOMPILED_HEADER),1)

    ifndef LP64
      $(error " Precompiled Headers only supported on 64-bit platforms!")
    endif

    PRECOMPILED_HEADER_DIR=.
    PRECOMPILED_HEADER_SRC=$(GAMMADIR)/src/share/vm/precompiled/precompiled.hpp
    PRECOMPILED_HEADER=$(PRECOMPILED_HEADER_DIR)/precompiled.hpp.pch

    PCH_FLAG = -include precompiled.hpp
    PCH_FLAG/DEFAULT = $(PCH_FLAG)
    PCH_FLAG/NO_PCH = -DNO_PCH
    PCH_FLAG/BY_FILE = $(PCH_FLAG/$@)$(PCH_FLAG/DEFAULT$(PCH_FLAG/$@))

    VM_PCH_FLAG/LIBJVM = $(PCH_FLAG/BY_FILE)
    VM_PCH_FLAG/AOUT =
    VM_PCH_FLAG = $(VM_PCH_FLAG/$(LINK_INTO))

    # We only use precompiled headers for the JVM build
    CFLAGS += $(VM_PCH_FLAG)

    # There are some files which don't like precompiled headers
    # The following files are build with 'OPT_CFLAGS/NOOPT' (-O0) in the opt build.
    # But Clang doesn't support a precompiled header which was compiled with -O3
    # to be used in a compilation unit which uses '-O0'. We could also prepare an
    # extra '-O0' PCH file for the opt build and use it here, but it's probably
    # not worth the effort as long as only two files need this special handling.
    PCH_FLAG/loopTransform.o = $(PCH_FLAG/NO_PCH)
    PCH_FLAG/sharedRuntimeTrig.o = $(PCH_FLAG/NO_PCH)
    PCH_FLAG/sharedRuntimeTrans.o = $(PCH_FLAG/NO_PCH)

  endif
else # ($(USE_CLANG), true)
  # check for precompiled headers support
  ifneq "$(shell expr \( $(CC_VER_MAJOR) \> 3 \) \| \( \( $(CC_VER_MAJOR) = 3 \) \& \( $(CC_VER_MINOR) \>= 4 \) \))" "0"
    # Allow the user to turn off precompiled headers from the command line.
    ifneq ($(USE_PRECOMPILED_HEADER),0)
      PRECOMPILED_HEADER_DIR=.
      PRECOMPILED_HEADER_SRC=$(GAMMADIR)/src/share/vm/precompiled/precompiled.hpp
      PRECOMPILED_HEADER=$(PRECOMPILED_HEADER_DIR)/precompiled.hpp.gch
    endif
  endif
endif

# -DDONT_USE_PRECOMPILED_HEADER will exclude all includes in precompiled.hpp.
ifeq ($(USE_PRECOMPILED_HEADER),0)
  CFLAGS += -DDONT_USE_PRECOMPILED_HEADER
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
CFLAGS += -D_REENTRANT
ifeq ($(USE_CLANG),)
  CFLAGS += -fcheck-new
  # version 4 and above support fvisibility=hidden (matches jni_x86.h file)
  # except 4.1.2 gives pointless warnings that can't be disabled (afaik)
  ifneq "$(shell expr \( $(CC_VER_MAJOR) \> 4 \) \| \( \( $(CC_VER_MAJOR) = 4 \) \& \( $(CC_VER_MINOR) \>= 3 \) \))" "0"
    CFLAGS += -fvisibility=hidden
  endif
else
  CFLAGS += -fvisibility=hidden
endif

ifeq ($(USE_CLANG), true)
  # Before Clang 3.1, we had to pass the stack alignment specification directly to llvm with the help of '-mllvm'
  # Starting with version 3.1, Clang understands the '-mstack-alignment' (and rejects '-mllvm -stack-alignment')
  ifneq "$(shell expr \( $(CC_VER_MAJOR) \> 3 \) \| \( \( $(CC_VER_MAJOR) = 3 \) \& \( $(CC_VER_MINOR) \>= 1 \) \))" "0"
    STACK_ALIGNMENT_OPT = -mno-omit-leaf-frame-pointer -mstack-alignment=16
  else
    STACK_ALIGNMENT_OPT = -mno-omit-leaf-frame-pointer -mllvm -stack-alignment=16
  endif
endif

ARCHFLAG = $(ARCHFLAG/$(BUILDARCH))
ARCHFLAG/i486    = -m32 -march=i586
ARCHFLAG/amd64   = -m64 $(STACK_ALIGNMENT_OPT)
ARCHFLAG/aarch64 =
ARCHFLAG/ia64    =
ARCHFLAG/sparc   = -m32 -mcpu=v9
ARCHFLAG/sparcv9 = -m64 -mcpu=v9
ARCHFLAG/zero    = $(ZERO_ARCHFLAG)
ARCHFLAG/ppc64   =  -m64

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

ifeq ($(USE_CLANG), true)
  # However we need to clean the code up before we can unrestrictedly enable this option with Clang
  WARNINGS_ARE_ERRORS += -Wno-logical-op-parentheses -Wno-parentheses-equality -Wno-parentheses
  WARNINGS_ARE_ERRORS += -Wno-switch -Wno-tautological-constant-out-of-range-compare -Wno-tautological-compare
  WARNINGS_ARE_ERRORS += -Wno-delete-non-virtual-dtor -Wno-deprecated -Wno-format -Wno-dynamic-class-memaccess
  WARNINGS_ARE_ERRORS += -Wno-return-type -Wno-empty-body
endif

WARNING_FLAGS = -Wpointer-arith -Wsign-compare -Wundef -Wunused-function -Wunused-value -Wformat=2 -Wreturn-type -Woverloaded-virtual

ifeq ($(USE_CLANG),)
  # Since GCC 4.3, -Wconversion has changed its meanings to warn these implicit
  # conversions which might affect the values. Only enable it in earlier versions.
  ifeq "$(shell expr \( $(CC_VER_MAJOR) \> 4 \) \| \( \( $(CC_VER_MAJOR) = 4 \) \& \( $(CC_VER_MINOR) \>= 3 \) \))" "0"
    # GCC < 4.3
    WARNING_FLAGS += -Wconversion
  endif  
  ifeq "$(shell expr \( $(CC_VER_MAJOR) \> 4 \) \| \( \( $(CC_VER_MAJOR) = 4 \) \& \( $(CC_VER_MINOR) \>= 8 \) \))" "1"
    # GCC >= 4.8
    # This flag is only known since GCC 4.3. Gcc 4.8 contains a fix so that with templates no
    # warnings are issued: https://gcc.gnu.org/bugzilla/show_bug.cgi?id=11856
    WARNING_FLAGS += -Wtype-limits
    # GCC < 4.8 don't accept this flag for C++.
    WARNING_FLAGS += -Wno-format-zero-length
  endif
endif

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
ifeq ($(BUILDARCH), ia64)
OPT_CFLAGS += -fno-expensive-optimizations
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
DEPFLAGS = -MMD -MP -MF $(DEP_DIR)/$(@:%=%.d)
ifeq ($(USE_CLANG),)
  ifneq ($(CC_VER_MAJOR), 2)
    DEPFLAGS += -fpch-deps
  endif
endif

#------------------------------------------------------------------------
# Linker flags

# statically link libstdc++.so, work with gcc but ignored by g++
STATIC_STDCXX = -Wl,-Bstatic -lstdc++ -Wl,-Bdynamic

# Enable linker optimization
LFLAGS += -Xlinker -O1

ifeq ($(USE_CLANG),)
  STATIC_LIBGCC += -static-libgcc

  ifneq (, findstring(debug,$(BUILD_FLAVOR)))
    # for relocations read-only
    LFLAGS += -Xlinker -z -Xlinker relro

    ifeq ($(BUILD_FLAVOR), debug)
      # disable incremental relocations linking
      LFLAGS += -Xlinker -z -Xlinker now
    endif
  endif

  ifeq ($(BUILDARCH), ia64)
    LFLAGS += -Wl,-relax
  endif

  # If this is a --hash-style=gnu system, use --hash-style=both
  #   The gnu .hash section won't work on some Linux systems like SuSE 10.
  _HAS_HASH_STYLE_GNU:=$(shell $(CC) -dumpspecs | grep -- '--hash-style=gnu')
  ifneq ($(_HAS_HASH_STYLE_GNU),)
    LDFLAGS_HASH_STYLE = -Wl,--hash-style=both
  endif
else
  # Don't know how to find out the 'hash style' of a system as '-dumpspecs'
  # doesn't work for Clang. So for now we'll alwys use --hash-style=both
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
AOUT_FLAGS += -Xlinker -export-dynamic

#------------------------------------------------------------------------
# Debug flags

ifeq ($(USE_CLANG), true)
  # Restrict the debug information created by Clang to avoid
  # too big object files and speed the build up a little bit
  # (see http://llvm.org/bugs/show_bug.cgi?id=7554)
  CFLAGS += -flimit-debug-info
endif

# Allow no optimizations.
DEBUG_CFLAGS=-O0

# DEBUG_BINARIES uses full -g debug information for all configs
ifeq ($(DEBUG_BINARIES), true)
  CFLAGS += -g
else
  DEBUG_CFLAGS += $(DEBUG_CFLAGS/$(BUILDARCH))
  ifeq ($(DEBUG_CFLAGS/$(BUILDARCH)),)
    DEBUG_CFLAGS += -g
  endif

  ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
    FASTDEBUG_CFLAGS += $(FASTDEBUG_CFLAGS/$(BUILDARCH))
    ifeq ($(FASTDEBUG_CFLAGS/$(BUILDARCH)),)
      FASTDEBUG_CFLAGS += -g
    endif

    OPT_CFLAGS += $(OPT_CFLAGS/$(BUILDARCH))
    ifeq ($(OPT_CFLAGS/$(BUILDARCH)),)
      OPT_CFLAGS += -g
    endif
  endif
endif

ifeq ($(USE_CLANG),)
  # Enable bounds checking.
  ifeq "$(shell expr \( $(CC_VER_MAJOR) \> 3 \) )" "1"
    # stack smashing checks.
    DEBUG_CFLAGS += -fstack-protector-all --param ssp-buffer-size=1
  endif
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

# Stack walking in the JVM relies on frame pointer (%rbp) to walk thread stack.
# Explicitly specify -fno-omit-frame-pointer because it is off by default
# starting with gcc 4.6.
ifndef USE_SUNCC
  CFLAGS += -fno-omit-frame-pointer
endif

-include $(HS_ALT_MAKE)/linux/makefiles/gcc.make
