#
# Copyright (c) 2006, 2010, Oracle and/or its affiliates. All rights reserved.
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

# The common definitions for hotspot builds.

# Default to verbose build logs (show all compile lines):
MAKE_VERBOSE=y

# Make macros for install files or preparing targets
CD=cd
CP=cp
ECHO=echo
GREP=grep
MKDIR=mkdir
MV=mv
PWD=pwd
RM=rm -f
SED=sed
TAR=tar
ZIPEXE=zip

define install-file
@$(MKDIR) -p $(@D)
@$(RM) $@
$(CP) $< $@
endef
define prep-target
@$(MKDIR) -p $(@D)
@$(RM) $@
endef

# Directory paths and user name
# Unless GAMMADIR is set on the command line, search upward from
# the current directory for a parent directory containing "src/share/vm".
# If that fails, look for $GAMMADIR in the environment.
# When the tree of subdirs is built, this setting is stored in each flags.make.
GAMMADIR := $(shell until ([ -d dev ]&&echo $${GAMMADIR:-/GAMMADIR/}) || ([ -d src/share/vm ]&&pwd); do cd ..; done)
HS_SRC_DIR=$(GAMMADIR)/src
HS_MAKE_DIR=$(GAMMADIR)/make
HS_BUILD_DIR=$(GAMMADIR)/build

ifeq ($(USER),)
  USER=$(USERNAME)
endif

# hotspot version definitions
include $(GAMMADIR)/make/hotspot_version

# Java versions needed
ifeq ($(PREVIOUS_JDK_VERSION),)
  PREVIOUS_JDK_VERSION=$(JDK_PREVIOUS_VERSION)
endif
ifeq ($(JDK_MAJOR_VERSION),)
  JDK_MAJOR_VERSION=$(JDK_MAJOR_VER)
endif
ifeq ($(JDK_MINOR_VERSION),)
  JDK_MINOR_VERSION=$(JDK_MINOR_VER)
endif
ifeq ($(JDK_MICRO_VERSION),)
  JDK_MICRO_VERSION=$(JDK_MICRO_VER)
endif
ifeq ($(JDK_MKTG_VERSION),)
  JDK_MKTG_VERSION=$(JDK_MINOR_VERSION).$(JDK_MICRO_VERSION)
endif
ifeq ($(JDK_VERSION),)
  JDK_VERSION=$(JDK_MAJOR_VERSION).$(JDK_MINOR_VERSION).$(JDK_MICRO_VERSION)
endif
ifeq ($(FULL_VERSION),)
  FULL_VERSION="$(JDK_VERSION)"
endif

# FULL_VERSION is only used to define JRE_RELEASE_VERSION which is used
# as JRE version in VM -Xinternalversion output.
ifndef JRE_RELEASE_VERSION
  JRE_RELEASE_VERSION=$(FULL_VERSION)
endif

ifndef HOTSPOT_RELEASE_VERSION
  HOTSPOT_RELEASE_VERSION=$(HS_MAJOR_VER).$(HS_MINOR_VER)-b$(HS_BUILD_NUMBER)
endif

ifdef HOTSPOT_BUILD_VERSION
# specified in command line
else
  ifdef COOKED_BUILD_NUMBER
# JRE build
    HOTSPOT_BUILD_VERSION=
  else
    ifdef USER_RELEASE_SUFFIX
      HOTSPOT_BUILD_VERSION=internal-$(USER_RELEASE_SUFFIX)
    else
      HOTSPOT_BUILD_VERSION=internal
    endif
  endif
endif

# Windows should have OS predefined
ifeq ($(OS),)
  OS   := $(shell uname -s)
  HOST := $(shell uname -n)
endif

# If not SunOS and not Linux, assume Windows
ifneq ($(OS), Linux)
  ifneq ($(OS), SunOS)
    OSNAME=windows
  else
    OSNAME=solaris
  endif
else
  OSNAME=linux
endif

# Determinations of default make arguments and platform specific settings
MAKE_ARGS=

# ARCH_DATA_MODEL==64 is equivalent to LP64=1
ifeq ($(ARCH_DATA_MODEL), 64)
  ifndef LP64
    LP64 := 1
  endif
endif

# Defaults set for product build
EXPORT_SUBDIR=

# Change default /java path if requested
ifneq ($(ALT_SLASH_JAVA),)
  SLASH_JAVA=$(ALT_SLASH_JAVA)
endif

# Default OUTPUTDIR
OUTPUTDIR=$(HS_BUILD_DIR)/$(OSNAME)
ifneq ($(ALT_OUTPUTDIR),)
  OUTPUTDIR=$(ALT_OUTPUTDIR)
endif

# Find latest promoted JDK area
JDK_IMPORT_PATH=$(SLASH_JAVA)/re/j2se/$(JDK_VERSION)/promoted/latest/binaries/$(PLATFORM)
ifneq ($(ALT_JDK_IMPORT_PATH),)
  JDK_IMPORT_PATH=$(ALT_JDK_IMPORT_PATH)
endif

# Find JDK used for javac compiles
BOOTDIR=$(SLASH_JAVA)/re/j2se/$(PREVIOUS_JDK_VERSION)/latest/binaries/$(PLATFORM)
ifneq ($(ALT_BOOTDIR),)
  BOOTDIR=$(ALT_BOOTDIR)
endif

# The platform dependent defs.make defines platform specific variable such 
# as ARCH, EXPORT_LIST etc. We must place the include here after BOOTDIR is defined.
include $(GAMMADIR)/make/$(OSNAME)/makefiles/defs.make

# We are trying to put platform specific defintions
# files to make/$(OSNAME)/makefiles dictory. However
# some definitions are common for both linux and solaris,
# so we put them here.
ifneq ($(OSNAME),windows)
  ABS_OUTPUTDIR     := $(shell mkdir -p $(OUTPUTDIR); $(CD) $(OUTPUTDIR); $(PWD))
  ABS_BOOTDIR       := $(shell $(CD) $(BOOTDIR); $(PWD))
  ABS_GAMMADIR      := $(shell $(CD) $(GAMMADIR); $(PWD))
  ABS_OS_MAKEFILE   := $(shell $(CD) $(HS_MAKE_DIR)/$(OSNAME); $(PWD))/Makefile

  # uname, HotSpot source directory, build directory and JDK use different names
  # for CPU architectures.
  #   ARCH      - uname output
  #   SRCARCH   - where to find HotSpot cpu and os_cpu source files
  #   BUILDARCH - build directory
  #   LIBARCH   - directory name in JDK/JRE

  # Use uname output for SRCARCH, but deal with platform differences. If ARCH
  # is not explicitly listed below, it is treated as x86. 
  SRCARCH     = $(ARCH/$(filter sparc sparc64 ia64 amd64 x86_64 arm ppc zero,$(ARCH)))
  ARCH/       = x86
  ARCH/sparc  = sparc
  ARCH/sparc64= sparc
  ARCH/ia64   = ia64
  ARCH/amd64  = x86
  ARCH/x86_64 = x86
  ARCH/ppc64  = ppc
  ARCH/ppc    = ppc
  ARCH/arm    = arm
  ARCH/zero   = zero

  # BUILDARCH is usually the same as SRCARCH, except for sparcv9
  BUILDARCH = $(SRCARCH)
  ifeq ($(BUILDARCH), x86)
    ifdef LP64
      BUILDARCH = amd64
    else
      BUILDARCH = i486
    endif
  endif
  ifeq ($(BUILDARCH), sparc)
    ifdef LP64
      BUILDARCH = sparcv9
    endif
  endif

  # LIBARCH is 1:1 mapping from BUILDARCH
  LIBARCH         = $(LIBARCH/$(BUILDARCH))
  LIBARCH/i486    = i386
  LIBARCH/amd64   = amd64
  LIBARCH/sparc   = sparc
  LIBARCH/sparcv9 = sparcv9
  LIBARCH/ia64    = ia64
  LIBARCH/ppc64   = ppc
  LIBARCH/ppc     = ppc
  LIBARCH/arm     = arm
  LIBARCH/zero    = $(ZERO_LIBARCH)

  LP64_ARCH = sparcv9 amd64 ia64 zero
endif

# Required make macro settings for all platforms
MAKE_ARGS += JAVA_HOME=$(ABS_BOOTDIR)
MAKE_ARGS += OUTPUTDIR=$(ABS_OUTPUTDIR)
MAKE_ARGS += GAMMADIR=$(ABS_GAMMADIR)
MAKE_ARGS += MAKE_VERBOSE=$(MAKE_VERBOSE)
MAKE_ARGS += HOTSPOT_RELEASE_VERSION=$(HOTSPOT_RELEASE_VERSION)
MAKE_ARGS += JRE_RELEASE_VERSION=$(JRE_RELEASE_VERSION)

# Pass HOTSPOT_BUILD_VERSION as argument to OS specific Makefile
# to overwrite the default definition since OS specific Makefile also
# includes this make/defs.make file.
MAKE_ARGS += HOTSPOT_BUILD_VERSION=$(HOTSPOT_BUILD_VERSION)

# Select name of export directory
EXPORT_PATH=$(OUTPUTDIR)/export-$(PLATFORM)$(EXPORT_SUBDIR)
ifneq ($(ALT_EXPORT_PATH),)
  EXPORT_PATH=$(ALT_EXPORT_PATH)
endif

# Default jdk image if one is created for you with create_jdk
JDK_IMAGE_DIR=$(OUTPUTDIR)/jdk-$(PLATFORM)

# Various export sub directories
EXPORT_INCLUDE_DIR = $(EXPORT_PATH)/include
EXPORT_DOCS_DIR = $(EXPORT_PATH)/docs
EXPORT_LIB_DIR = $(EXPORT_PATH)/lib
EXPORT_JRE_DIR = $(EXPORT_PATH)/jre
EXPORT_JRE_BIN_DIR = $(EXPORT_JRE_DIR)/bin
EXPORT_JRE_LIB_DIR = $(EXPORT_JRE_DIR)/lib
EXPORT_JRE_LIB_ARCH_DIR = $(EXPORT_JRE_LIB_DIR)/$(LIBARCH)

# Common export list of files
EXPORT_LIST += $(EXPORT_INCLUDE_DIR)/jvmti.h
EXPORT_LIST += $(EXPORT_INCLUDE_DIR)/jvmticmlr.h
EXPORT_LIST += $(EXPORT_INCLUDE_DIR)/jni.h
EXPORT_LIST += $(EXPORT_INCLUDE_DIR)/$(JDK_INCLUDE_SUBDIR)/jni_md.h
EXPORT_LIST += $(EXPORT_INCLUDE_DIR)/jmm.h
