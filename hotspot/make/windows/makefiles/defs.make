#
# Copyright 2006-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

# The common definitions for hotspot windows builds.
# Include the top level defs.make under make directory instead of this one.
# This file is included into make/defs.make.
# On windows it is only used to construct parameters for
# make/windows/build.make when make/Makefile is used to build VM.

SLASH_JAVA ?= J:
PATH_SEP = ;

# Need PLATFORM (os-arch combo names) for jdk and hotspot, plus libarch name
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

# NB later OS versions than 2003 may report "Intel64"
ifneq ($(shell $(ECHO) $(PROCESSOR_IDENTIFIER) | $(GREP) "EM64T\|Intel64"),)
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

JDK_INCLUDE_SUBDIR=win32

# HOTSPOT_RELEASE_VERSION and HOTSPOT_BUILD_VERSION are defined
# and added to MAKE_ARGS list in $(GAMMADIR)/make/defs.make.

# next parameters are defined in $(GAMMADIR)/make/defs.make.
MAKE_ARGS += JDK_MKTG_VERSION=$(JDK_MKTG_VERSION)
MAKE_ARGS += JDK_MAJOR_VER=$(JDK_MAJOR_VERSION)
MAKE_ARGS += JDK_MINOR_VER=$(JDK_MINOR_VERSION)
MAKE_ARGS += JDK_MICRO_VER=$(JDK_MICRO_VERSION)

ifdef COOKED_JDK_UPDATE_VERSION
  MAKE_ARGS += JDK_UPDATE_VER=$(COOKED_JDK_UPDATE_VERSION)
endif

# COOKED_BUILD_NUMBER should only be set if we have a numeric
# build number.  It must not be zero padded.
ifdef COOKED_BUILD_NUMBER
  MAKE_ARGS += JDK_BUILD_NUMBER=$(COOKED_BUILD_NUMBER)
endif

NMAKE= MAKEFLAGS= MFLAGS= nmake /NOLOGO

# Check for CYGWIN
ifneq (,$(findstring CYGWIN,$(shell uname)))
  USING_CYGWIN=true
else
  USING_CYGWIN=false
endif
# FIXUP: The subdirectory for a debug build is NOT the same on all platforms
VM_DEBUG=debug

# Windows wants particular paths due to nmake (must be after macros defined)
#   It is important that gnumake invokes nmake with C:\\...\\  formated
#   strings so that nmake gets C:\...\ style strings.
# Check for CYGWIN
ifeq ($(USING_CYGWIN), true)
  ABS_OUTPUTDIR   := $(subst /,\\,$(shell /bin/cygpath -m -a "$(OUTPUTDIR)"))
  ABS_BOOTDIR     := $(subst /,\\,$(shell /bin/cygpath -m -a "$(BOOTDIR)"))
  ABS_GAMMADIR    := $(subst /,\\,$(shell /bin/cygpath -m -a "$(GAMMADIR)"))
  ABS_OS_MAKEFILE := $(shell /bin/cygpath -m -a "$(HS_MAKE_DIR)/$(OSNAME)")/build.make
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

EXPORT_SERVER_DIR = $(EXPORT_JRE_BIN_DIR)/server
EXPORT_LIST += $(EXPORT_SERVER_DIR)/Xusage.txt
EXPORT_LIST += $(EXPORT_SERVER_DIR)/jvm.dll
EXPORT_LIST += $(EXPORT_SERVER_DIR)/jvm.pdb
EXPORT_LIST += $(EXPORT_SERVER_DIR)/jvm.map
EXPORT_LIST += $(EXPORT_LIB_DIR)/jvm.lib
ifeq ($(ARCH_DATA_MODEL), 32)
  EXPORT_CLIENT_DIR = $(EXPORT_JRE_BIN_DIR)/client
  EXPORT_LIST += $(EXPORT_CLIENT_DIR)/Xusage.txt
  EXPORT_LIST += $(EXPORT_CLIENT_DIR)/jvm.dll
  EXPORT_LIST += $(EXPORT_CLIENT_DIR)/jvm.pdb
  EXPORT_LIST += $(EXPORT_CLIENT_DIR)/jvm.map
  # kernel vm
  EXPORT_KERNEL_DIR = $(EXPORT_JRE_BIN_DIR)/kernel
  EXPORT_LIST += $(EXPORT_KERNEL_DIR)/Xusage.txt
  EXPORT_LIST += $(EXPORT_KERNEL_DIR)/jvm.dll
  EXPORT_LIST += $(EXPORT_KERNEL_DIR)/jvm.pdb
  EXPORT_LIST += $(EXPORT_KERNEL_DIR)/jvm.map
endif

ifeq ($(BUILD_WIN_SA), 1)
  EXPORT_LIST += $(EXPORT_JRE_BIN_DIR)/sawindbg.dll
  EXPORT_LIST += $(EXPORT_JRE_BIN_DIR)/sawindbg.pdb
  EXPORT_LIST += $(EXPORT_JRE_BIN_DIR)/sawindbg.map
  EXPORT_LIST += $(EXPORT_LIB_DIR)/sa-jdi.jar
  # Must pass this down to nmake.
  MAKE_ARGS += BUILD_WIN_SA=1
endif
