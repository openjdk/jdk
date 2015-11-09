#
# Copyright (c) 2006, 2014, Oracle and/or its affiliates. All rights reserved.
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

# The common definitions for hotspot windows builds.
# Include the top level defs.make under make directory instead of this one.
# This file is included into make/defs.make.
# On windows it is only used to construct parameters for
# make/windows/build.make when make/Makefile is used to build VM.

SLASH_JAVA ?= J:
PATH_SEP = ;

MAKE_ARGS += WARNINGS_AS_ERRORS=$(WARNINGS_AS_ERRORS)

# Need PLATFORM (os-arch combo names) for jdk and hotspot, plus libarch name
ifeq ($(ARCH_DATA_MODEL),32)
  ARCH_DATA_MODEL=32
  PLATFORM=windows-i586
  VM_PLATFORM=windows_i486
  HS_ARCH=x86
  MAKE_ARGS += ARCH=x86
  MAKE_ARGS += BUILDARCH=i486
  MAKE_ARGS += Platform_arch=x86
  MAKE_ARGS += Platform_arch_model=x86_32
endif

ifneq ($(shell $(ECHO) $(PROCESSOR_IDENTIFIER) | $(GREP) x86),)
  ARCH_DATA_MODEL=32
  PLATFORM=windows-i586
  VM_PLATFORM=windows_i486
  HS_ARCH=x86
  MAKE_ARGS += ARCH=x86
  MAKE_ARGS += BUILDARCH=i486
  MAKE_ARGS += Platform_arch=x86
  MAKE_ARGS += Platform_arch_model=x86_32
endif

ifneq ($(ARCH_DATA_MODEL),32)
  ifneq ($(shell $(ECHO) $(PROCESSOR_IDENTIFIER) | $(GREP) ia64),)
    ARCH_DATA_MODEL=64
    PLATFORM=windows-ia64
    VM_PLATFORM=windows_ia64
    HS_ARCH=ia64
    MAKE_ARGS += LP64=1
    MAKE_ARGS += ARCH=ia64
    MAKE_ARGS += BUILDARCH=ia64
    MAKE_ARGS += Platform_arch=ia64
    MAKE_ARGS += Platform_arch_model=ia64
  endif

# http://support.microsoft.com/kb/888731 : this can be either
# AMD64 for AMD, or EM64T for Intel chips.
  ifneq ($(shell $(ECHO) $(PROCESSOR_IDENTIFIER) | $(GREP) AMD64),)
    ARCH_DATA_MODEL=64
    PLATFORM=windows-amd64
    VM_PLATFORM=windows_amd64
    HS_ARCH=x86
    MAKE_ARGS += LP64=1
    MAKE_ARGS += ARCH=x86
    MAKE_ARGS += BUILDARCH=amd64
    MAKE_ARGS += Platform_arch=x86
    MAKE_ARGS += Platform_arch_model=x86_64
  endif

ifneq ($(shell $(ECHO) $(PROCESSOR_IDENTIFIER) | $(GREP) EM64T),)
    ARCH_DATA_MODEL=64
    PLATFORM=windows-amd64
    VM_PLATFORM=windows_amd64
    HS_ARCH=x86
    MAKE_ARGS += LP64=1
    MAKE_ARGS += ARCH=x86
    MAKE_ARGS += BUILDARCH=amd64
    MAKE_ARGS += Platform_arch=x86
    MAKE_ARGS += Platform_arch_model=x86_64
  endif

# NB later OS versions than 2003 may report "Intel64"
  ifneq ($(shell $(ECHO) $(PROCESSOR_IDENTIFIER) | $(GREP) Intel64),)
    ARCH_DATA_MODEL=64
    PLATFORM=windows-amd64
    VM_PLATFORM=windows_amd64
    HS_ARCH=x86
    MAKE_ARGS += LP64=1
    MAKE_ARGS += ARCH=x86
    MAKE_ARGS += BUILDARCH=amd64
    MAKE_ARGS += Platform_arch=x86
    MAKE_ARGS += Platform_arch_model=x86_64
  endif
endif

# Full Debug Symbols has been enabled on Windows since JDK1.4.1 so
# there is no need for an "earlier than JDK7 check".
# The Full Debug Symbols (FDS) default for BUILD_FLAVOR == product
# builds is enabled with debug info files ZIP'ed to save space. For
# BUILD_FLAVOR != product builds, FDS is always enabled, after all a
# debug build without debug info isn't very useful.
# The ZIP_DEBUGINFO_FILES option only has meaning when FDS is enabled.
#
# If you invoke a build with FULL_DEBUG_SYMBOLS=0, then FDS will be
# disabled for a BUILD_FLAVOR == product build.
#
# Note: Use of a different variable name for the FDS override option
# versus the FDS enabled check is intentional (FULL_DEBUG_SYMBOLS
# versus ENABLE_FULL_DEBUG_SYMBOLS). For auto build systems that pass
# in options via environment variables, use of distinct variables
# prevents strange behaviours. For example, in a BUILD_FLAVOR !=
# product build, the FULL_DEBUG_SYMBOLS environment variable will be
# 0, but the ENABLE_FULL_DEBUG_SYMBOLS make variable will be 1. If
# the same variable name is used, then different values can be picked
# up by different parts of the build. Just to be clear, we only need
# two variable names because the incoming option value can be
# overridden in some situations, e.g., a BUILD_FLAVOR != product
# build.

# Due to the multiple sub-make processes that occur this logic gets
# executed multiple times. We reduce the noise by at least checking that
# BUILD_FLAVOR has been set.
ifneq ($(BUILD_FLAVOR),)
  ifeq ($(BUILD_FLAVOR), product)
    FULL_DEBUG_SYMBOLS ?= 1
    ENABLE_FULL_DEBUG_SYMBOLS = $(FULL_DEBUG_SYMBOLS)
  else
    # debug variants always get Full Debug Symbols (if available)
    ENABLE_FULL_DEBUG_SYMBOLS = 1
  endif
  _JUNK_ := $(shell \
    echo >&2 "INFO: ENABLE_FULL_DEBUG_SYMBOLS=$(ENABLE_FULL_DEBUG_SYMBOLS)")
  MAKE_ARGS += ENABLE_FULL_DEBUG_SYMBOLS=$(ENABLE_FULL_DEBUG_SYMBOLS)

  ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
    ZIP_DEBUGINFO_FILES ?= 1
  else
    ZIP_DEBUGINFO_FILES=0
  endif
  MAKE_ARGS += ZIP_DEBUGINFO_FILES=$(ZIP_DEBUGINFO_FILES)
endif

MAKE_ARGS += RM="$(RM)"
MAKE_ARGS += ZIPEXE=$(ZIPEXE)
MAKE_ARGS += CP="${CP}"
MAKE_ARGS += MV="${MV}"


# On 32 bit windows we build server and client, on 64 bit just server.
ifeq ($(JVM_VARIANTS),)
  ifeq ($(ARCH_DATA_MODEL), 32)
    JVM_VARIANTS:=client,server
    JVM_VARIANT_CLIENT:=true
    JVM_VARIANT_SERVER:=true
  else
    JVM_VARIANTS:=server
    JVM_VARIANT_SERVER:=true
  endif
endif

JDK_INCLUDE_SUBDIR=win32

# Library suffix
LIBRARY_SUFFIX=dll

# HOTSPOT_RELEASE_VERSION and HOTSPOT_BUILD_VERSION are defined
# and added to MAKE_ARGS list in $(GAMMADIR)/make/defs.make.

# next parameters are defined in $(GAMMADIR)/make/defs.make.
MAKE_ARGS += JDK_MKTG_VERSION=$(JDK_MKTG_VERSION)
MAKE_ARGS += JDK_MAJOR_VERSION=$(JDK_MAJOR_VERSION)
MAKE_ARGS += JDK_MINOR_VERSION=$(JDK_MINOR_VERSION)
MAKE_ARGS += JDK_MICRO_VERSION=$(JDK_MICRO_VERSION)

ifdef COOKED_JDK_UPDATE_VERSION
  MAKE_ARGS += JDK_UPDATE_VER=$(COOKED_JDK_UPDATE_VERSION)
endif

# COOKED_BUILD_NUMBER should only be set if we have a numeric
# build number.  It must not be zero padded.
ifdef COOKED_BUILD_NUMBER
  MAKE_ARGS += JDK_BUILD_NUMBER=$(COOKED_BUILD_NUMBER)
endif

NMAKE= MAKEFLAGS= MFLAGS= EXTRA_CFLAGS="$(EXTRA_CFLAGS)" nmake -NOLOGO
ifndef SYSTEM_UNAME
  SYSTEM_UNAME := $(shell uname)
  export SYSTEM_UNAME
endif

# Check for CYGWIN
ifneq (,$(findstring CYGWIN,$(SYSTEM_UNAME)))
  USING_CYGWIN=true
else
  USING_CYGWIN=false
endif
# Check for MinGW
ifneq (,$(findstring MINGW,$(SYSTEM_UNAME)))
  USING_MINGW=true
endif

# Windows wants particular paths due to nmake (must be after macros defined)
#   It is important that gnumake invokes nmake with C:\\...\\  formated
#   strings so that nmake gets C:\...\ style strings.
# Check for CYGWIN
ifeq ($(USING_CYGWIN), true)
  ABS_OUTPUTDIR   := $(subst /,\\,$(shell /bin/cygpath -m -a "$(OUTPUTDIR)"))
  ABS_BOOTDIR     := $(subst /,\\,$(shell /bin/cygpath -m -a "$(BOOTDIR)"))
  ABS_GAMMADIR    := $(subst /,\\,$(shell /bin/cygpath -m -a "$(GAMMADIR)"))
  ABS_OS_MAKEFILE := $(shell /bin/cygpath -m -a "$(HS_MAKE_DIR)/$(OSNAME)")/build.make
else ifeq ($(USING_MINGW), true)
    ABS_OUTPUTDIR   := $(shell $(CD) $(OUTPUTDIR);$(PWD))
    ABS_BOOTDIR     := $(shell $(CD) $(BOOTDIR);$(PWD))
    ABS_GAMMADIR    := $(shell $(CD) $(GAMMADIR);$(PWD))
    ABS_OS_MAKEFILE := $(shell $(CD) $(HS_MAKE_DIR)/$(OSNAME);$(PWD))/build.make
  else
    ABS_OUTPUTDIR   := $(subst /,\\,$(shell $(CD) $(OUTPUTDIR);$(PWD)))
    ABS_BOOTDIR     := $(subst /,\\,$(shell $(CD) $(BOOTDIR);$(PWD)))
    ABS_GAMMADIR    := $(subst /,\\,$(shell $(CD) $(GAMMADIR);$(PWD)))
    ABS_OS_MAKEFILE := $(subst /,\\,$(shell $(CD) $(HS_MAKE_DIR)/$(OSNAME);$(PWD))/build.make)
endif

# Disable building SA on windows until we are sure
# we want to release it.  If we build it here,
# the SDK makefiles will copy it over and put it into
# the created image.
BUILD_WIN_SA = 1
ifneq ($(ALT_BUILD_WIN_SA),)
  BUILD_WIN_SA = $(ALT_BUILD_WIN_SA)
endif

ifeq ($(BUILD_WIN_SA), 1)
  ifeq ($(ARCH),ia64)
    BUILD_WIN_SA = 0
  endif
endif

EXPORT_SERVER_DIR = $(EXPORT_BIN_DIR)/server
EXPORT_CLIENT_DIR = $(EXPORT_BIN_DIR)/client

ifeq ($(JVM_VARIANT_SERVER),true)
  EXPORT_LIST += $(EXPORT_SERVER_DIR)/Xusage.txt
  EXPORT_LIST += $(EXPORT_SERVER_DIR)/jvm.$(LIBRARY_SUFFIX)
  ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
      EXPORT_LIST += $(EXPORT_SERVER_DIR)/jvm.diz
    else
      EXPORT_LIST += $(EXPORT_SERVER_DIR)/jvm.pdb
      EXPORT_LIST += $(EXPORT_SERVER_DIR)/jvm.map
    endif
  endif
endif
ifeq ($(JVM_VARIANT_CLIENT),true)
  EXPORT_LIST += $(EXPORT_CLIENT_DIR)/Xusage.txt
  EXPORT_LIST += $(EXPORT_CLIENT_DIR)/jvm.$(LIBRARY_SUFFIX)
  ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
      EXPORT_LIST += $(EXPORT_CLIENT_DIR)/jvm.diz
    else
      EXPORT_LIST += $(EXPORT_CLIENT_DIR)/jvm.pdb
      EXPORT_LIST += $(EXPORT_CLIENT_DIR)/jvm.map
    endif
  endif
endif

EXPORT_LIST += $(EXPORT_LIB_DIR)/jvm.lib

ifeq ($(BUILD_WIN_SA), 1)
  EXPORT_LIST += $(EXPORT_BIN_DIR)/sawindbg.$(LIBRARY_SUFFIX)
  ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
      EXPORT_LIST += $(EXPORT_BIN_DIR)/sawindbg.diz
    else
      EXPORT_LIST += $(EXPORT_BIN_DIR)/sawindbg.pdb
      EXPORT_LIST += $(EXPORT_BIN_DIR)/sawindbg.map
    endif
  endif
  EXPORT_LIST += $(EXPORT_LIB_DIR)/sa-jdi.jar
  # Must pass this down to nmake.
  MAKE_ARGS += BUILD_WIN_SA=1
endif

# Propagate compiler and tools paths from configure to nmake.
# Need to make sure they contain \\ and not /.
ifneq ($(SPEC),)
  ifeq ($(USING_CYGWIN), true)
    MAKE_ARGS += CXX="$(subst /,\\,$(shell /bin/cygpath -s -m -a $(CXX)))"
    MAKE_ARGS += LD="$(subst /,\\,$(shell /bin/cygpath -s -m -a $(LD)))"
    MAKE_ARGS += RC="$(subst /,\\,$(shell /bin/cygpath -s -m -a $(RC)))"
    MAKE_ARGS += MT="$(subst /,\\,$(shell /bin/cygpath -s -m -a $(MT)))"
  else
    MAKE_ARGS += CXX="$(subst /,\\,$(CXX))"
    MAKE_ARGS += LD="$(subst /,\\,$(LD))"
    MAKE_ARGS += RC="$(subst /,\\,$(RC))"
    MAKE_ARGS += MT="$(subst /,\\,$(MT))"
  endif
endif
