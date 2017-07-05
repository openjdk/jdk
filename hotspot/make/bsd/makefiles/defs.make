#
# Copyright (c) 2006, 2015, Oracle and/or its affiliates. All rights reserved.
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

# The common definitions for hotspot bsd builds.
# Include the top level defs.make under make directory instead of this one.
# This file is included into make/defs.make.

SLASH_JAVA ?= /java

define print_info
  ifneq ($$(LOG_LEVEL), warn)
    $$(shell echo >&2 "INFO: $1")
  endif
endef

# Need PLATFORM (os-arch combo names) for jdk and hotspot, plus libarch name
ARCH:=$(shell uname -m)
PATH_SEP = :
ifeq ($(LP64), 1)
  ARCH_DATA_MODEL ?= 64
else
  ARCH_DATA_MODEL ?= 32
endif

# zero
ifeq ($(findstring true, $(JVM_VARIANT_ZERO) $(JVM_VARIANT_ZEROSHARK)), true)
  ifeq ($(ARCH_DATA_MODEL), 64)
    MAKE_ARGS      += LP64=1
  endif
  PLATFORM         = bsd-zero
  VM_PLATFORM      = bsd_$(subst i386,i486,$(ZERO_LIBARCH))
  HS_ARCH          = zero
  ARCH             = zero
endif

# ia64
ifeq ($(ARCH), ia64)
  ARCH_DATA_MODEL = 64
  MAKE_ARGS      += LP64=1
  PLATFORM        = bsd-ia64
  VM_PLATFORM     = bsd_ia64
  HS_ARCH         = ia64
endif

# sparc
ifeq ($(ARCH), sparc64)
  ifeq ($(ARCH_DATA_MODEL), 64)
    ARCH_DATA_MODEL  = 64
    MAKE_ARGS        += LP64=1
    PLATFORM         = bsd-sparcv9
    VM_PLATFORM      = bsd_sparcv9
  else
    ARCH_DATA_MODEL  = 32
    PLATFORM         = bsd-sparc
    VM_PLATFORM      = bsd_sparc
  endif
  HS_ARCH            = sparc
endif

# amd64
ifneq (,$(findstring $(ARCH), amd64 x86_64))
  ifeq ($(ARCH_DATA_MODEL), 64)
    ARCH_DATA_MODEL = 64
    MAKE_ARGS       += LP64=1
    PLATFORM        = bsd-amd64
    VM_PLATFORM     = bsd_amd64
    HS_ARCH         = x86
  else
    ARCH_DATA_MODEL = 32
    PLATFORM        = bsd-i586
    VM_PLATFORM     = bsd_i486
    HS_ARCH         = x86
    # We have to reset ARCH to i386 since SRCARCH relies on it
    ARCH            = i386
  endif
endif

# i386
ifeq ($(ARCH), i386)
  ifeq ($(ARCH_DATA_MODEL), 64)
    ARCH_DATA_MODEL = 64
    MAKE_ARGS       += LP64=1
    PLATFORM        = bsd-amd64
    VM_PLATFORM     = bsd_amd64
    HS_ARCH         = x86
    # We have to reset ARCH to amd64 since SRCARCH relies on it
    ARCH            = amd64
  else
    ARCH_DATA_MODEL  = 32
    PLATFORM         = bsd-i586
    VM_PLATFORM      = bsd_i486
    HS_ARCH          = x86
  endif
endif

# ARM
ifeq ($(ARCH), arm)
  ARCH_DATA_MODEL  = 32
  PLATFORM         = bsd-arm
  VM_PLATFORM      = bsd_arm
  HS_ARCH          = arm
endif

# PPC
ifeq ($(ARCH), ppc)
  ARCH_DATA_MODEL  = 32
  PLATFORM         = bsd-ppc
  VM_PLATFORM      = bsd_ppc
  HS_ARCH          = ppc
endif

# On 32 bit bsd we build server and client, on 64 bit just server.
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

OS_VENDOR:=$(shell uname -s)

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
  # FULL_DEBUG_SYMBOLS not created for individual static libraries
  ifeq ($(STATIC_BUILD),false)
    ifeq ($(BUILD_FLAVOR), product)
      FULL_DEBUG_SYMBOLS ?= 1
      ENABLE_FULL_DEBUG_SYMBOLS = $(FULL_DEBUG_SYMBOLS)
    else
      # debug variants always get Full Debug Symbols (if available)
      ENABLE_FULL_DEBUG_SYMBOLS = 1
    endif
  endif
  $(eval $(call print_info, "ENABLE_FULL_DEBUG_SYMBOLS=$(ENABLE_FULL_DEBUG_SYMBOLS)"))
  # since objcopy is optional, we set ZIP_DEBUGINFO_FILES later

  ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
    ifeq ($(OS_VENDOR), Darwin)
        # MacOS X doesn't use OBJCOPY or STRIP_POLICY
        OBJCOPY=
        STRIP_POLICY=
        ZIP_DEBUGINFO_FILES ?= 1
    else
      # Default OBJCOPY comes from GNU Binutils on BSD
      ifeq ($(CROSS_COMPILE_ARCH),)
        DEF_OBJCOPY=/usr/bin/objcopy
      else
        # Assume objcopy is part of the cross-compilation toolset
        ifneq ($(ALT_COMPILER_PATH),)
          DEF_OBJCOPY=$(ALT_COMPILER_PATH)/objcopy
        endif
      endif
      OBJCOPY=$(shell test -x $(DEF_OBJCOPY) && echo $(DEF_OBJCOPY))
      ifneq ($(ALT_OBJCOPY),)
        $(eval $(call print_info, "ALT_OBJCOPY=$(ALT_OBJCOPY)"))
        OBJCOPY=$(shell test -x $(ALT_OBJCOPY) && echo $(ALT_OBJCOPY))
      endif

      ifeq ($(OBJCOPY),)
        $(eval $(call print_info, "no objcopy cmd found so cannot create .debuginfo" \
            "files. You may need to set ALT_OBJCOPY."))
        ENABLE_FULL_DEBUG_SYMBOLS=0
        $(eval $(call print_info, "ENABLE_FULL_DEBUG_SYMBOLS=$(ENABLE_FULL_DEBUG_SYMBOLS)"))
      else
        $(eval $(call print_info, "$(OBJCOPY) cmd found so will create .debuginfo" \
            "files."))

        # Library stripping policies for .debuginfo configs:
        #   all_strip - strips everything from the library
        #   min_strip - strips most stuff from the library; leaves
        #               minimum symbols
        #   no_strip  - does not strip the library at all
        #
        # Oracle security policy requires "all_strip". A waiver was
        # granted on 2011.09.01 that permits using "min_strip" in the
        # Java JDK and Java JRE.
        #
        # Currently, STRIP_POLICY is only used when Full Debug Symbols
        # is enabled.
        #
        STRIP_POLICY ?= min_strip

        $(eval $(call print_info, "STRIP_POLICY=$(STRIP_POLICY)"))

        ZIP_DEBUGINFO_FILES ?= 1
      endif

      $(eval $(call print_info, "ZIP_DEBUGINFO_FILES=$(ZIP_DEBUGINFO_FILES)"))
    endif
  endif # ENABLE_FULL_DEBUG_SYMBOLS=1
endif # BUILD_FLAVOR

JDK_INCLUDE_SUBDIR=bsd

# Library suffix
ifneq ($(STATIC_BUILD),true)
  ifeq ($(OS_VENDOR),Darwin)
    LIBRARY_SUFFIX=dylib
  else
    LIBRARY_SUFFIX=so
  endif
else
  LIBRARY_SUFFIX=a
endif


EXPORT_LIST += $(EXPORT_DOCS_DIR)/platform/jvmti/jvmti.html

# jsig library not needed for static builds
ifneq ($(STATIC_BUILD),true)
  # client and server subdirectories have symbolic links to ../libjsig.so
  EXPORT_LIST += $(EXPORT_LIB_ARCH_DIR)/libjsig.$(LIBRARY_SUFFIX)
endif

ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
  ifeq ($(ZIP_DEBUGINFO_FILES),1)
      EXPORT_LIST += $(EXPORT_LIB_ARCH_DIR)/libjsig.diz
  else
    ifeq ($(OS_VENDOR), Darwin)
        EXPORT_LIST += $(EXPORT_LIB_ARCH_DIR)/libjsig.$(LIBRARY_SUFFIX).dSYM
    else
        EXPORT_LIST += $(EXPORT_LIB_ARCH_DIR)/libjsig.debuginfo
    endif
  endif
endif

EXPORT_SERVER_DIR = $(EXPORT_LIB_ARCH_DIR)/server
EXPORT_CLIENT_DIR = $(EXPORT_LIB_ARCH_DIR)/client
EXPORT_MINIMAL_DIR = $(EXPORT_LIB_ARCH_DIR)/minimal

ifeq ($(findstring true, $(JVM_VARIANT_SERVER) $(JVM_VARIANT_ZERO) $(JVM_VARIANT_ZEROSHARK)), true)
  EXPORT_LIST += $(EXPORT_SERVER_DIR)/Xusage.txt
  EXPORT_LIST += $(EXPORT_SERVER_DIR)/libjvm.$(LIBRARY_SUFFIX)
  ifeq ($(STATIC_BUILD),true)
    EXPORT_LIST += $(EXPORT_SERVER_DIR)/libjvm.symbols
  endif

  ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
        EXPORT_LIST += $(EXPORT_SERVER_DIR)/libjvm.diz
    else
      ifeq ($(OS_VENDOR), Darwin)
          EXPORT_LIST += $(EXPORT_SERVER_DIR)/libjvm.$(LIBRARY_SUFFIX).dSYM
      else
          EXPORT_LIST += $(EXPORT_SERVER_DIR)/libjvm.debuginfo
      endif
    endif
  endif
endif

ifeq ($(JVM_VARIANT_CLIENT),true)
  EXPORT_LIST += $(EXPORT_CLIENT_DIR)/Xusage.txt
  EXPORT_LIST += $(EXPORT_CLIENT_DIR)/libjvm.$(LIBRARY_SUFFIX)
  ifeq ($(STATIC_BUILD),true)
    EXPORT_LIST += $(EXPORT_CLIENT_DIR)/libjvm.symbols
  endif

  ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
        EXPORT_LIST += $(EXPORT_CLIENT_DIR)/libjvm.diz
    else
      ifeq ($(OS_VENDOR), Darwin)
          EXPORT_LIST += $(EXPORT_CLIENT_DIR)/libjvm.$(LIBRARY_SUFFIX).dSYM
      else
          EXPORT_LIST += $(EXPORT_CLIENT_DIR)/libjvm.debuginfo
      endif
    endif
  endif
endif

ifeq ($(JVM_VARIANT_MINIMAL1),true)
  EXPORT_LIST += $(EXPORT_MINIMAL_DIR)/Xusage.txt
  EXPORT_LIST += $(EXPORT_MINIMAL_DIR)/libjvm.$(LIBRARY_SUFFIX)
  ifeq ($(STATIC_BUILD),true)
    EXPORT_LIST += $(EXPORT_MINIMAL_DIR)/libjvm.symbols
  endif
endif

# Serviceability Binaries
# No SA Support for PPC, IA64, ARM or zero
ADD_SA_BINARIES/x86   = $(EXPORT_LIB_ARCH_DIR)/libsaproc.$(LIBRARY_SUFFIX) \
                        $(EXPORT_LIB_DIR)/sa-jdi.jar

ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
  ifeq ($(ZIP_DEBUGINFO_FILES),1)
      ADD_SA_BINARIES/x86 += $(EXPORT_LIB_ARCH_DIR)/libsaproc.diz
  else
    ifeq ($(OS_VENDOR), Darwin)
        ADD_SA_BINARIES/x86 += $(EXPORT_LIB_ARCH_DIR)/libsaproc.$(LIBRARY_SUFFIX).dSYM
    else
        ADD_SA_BINARIES/x86 += $(EXPORT_LIB_ARCH_DIR)/libsaproc.debuginfo
    endif
  endif
endif

ADD_SA_BINARIES/sparc = $(EXPORT_LIB_ARCH_DIR)/libsaproc.$(LIBRARY_SUFFIX) \
                        $(EXPORT_LIB_DIR)/sa-jdi.jar
ADD_SA_BINARIES/universal = $(EXPORT_LIB_ARCH_DIR)/libsaproc.$(LIBRARY_SUFFIX) \
                            $(EXPORT_LIB_DIR)/sa-jdi.jar

ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
  ifeq ($(ZIP_DEBUGINFO_FILES),1)
      ADD_SA_BINARIES/universal += $(EXPORT_LIB_ARCH_DIR)/libsaproc.diz
  else
    ifeq ($(OS_VENDOR), Darwin)
        ADD_SA_BINARIES/universal += $(EXPORT_LIB_ARCH_DIR)/libsaproc.$(LIBRARY_SUFFIX).dSYM
    else
        ADD_SA_BINARIES/universal += $(EXPORT_LIB_ARCH_DIR)/libsaproc.debuginfo
    endif
  endif
endif

ADD_SA_BINARIES/ppc   =
ADD_SA_BINARIES/ia64  =
ADD_SA_BINARIES/arm   =
ADD_SA_BINARIES/zero  =

EXPORT_LIST += $(ADD_SA_BINARIES/$(HS_ARCH))

# Universal build settings
ifeq ($(OS_VENDOR), Darwin)
  # Build universal binaries by default on Mac OS X
  MACOSX_UNIVERSAL = true
  ifneq ($(ALT_MACOSX_UNIVERSAL),)
    MACOSX_UNIVERSAL = $(ALT_MACOSX_UNIVERSAL)
  endif
  MAKE_ARGS += MACOSX_UNIVERSAL=$(MACOSX_UNIVERSAL)

  # Universal settings
  ifeq ($(MACOSX_UNIVERSAL), true)

    # Set universal export path but avoid using ARCH or PLATFORM subdirs
    EXPORT_PATH=$(OUTPUTDIR)/export-universal$(EXPORT_SUBDIR)
    ifneq ($(ALT_EXPORT_PATH),)
      EXPORT_PATH=$(ALT_EXPORT_PATH)
    endif

    # Set universal image dir
    JDK_IMAGE_DIR=$(OUTPUTDIR)/jdk-universal$(EXPORT_SUBDIR)
    ifneq ($(ALT_JDK_IMAGE_DIR),)
      JDK_IMAGE_DIR=$(ALT_JDK_IMAGE_DIR)
    endif

    # Binaries to 'universalize' if built
    ifneq ($(STATIC_BUILD),true)
      UNIVERSAL_LIPO_LIST += $(EXPORT_LIB_DIR)/libjsig.$(LIBRARY_SUFFIX)
    endif
    UNIVERSAL_LIPO_LIST += $(EXPORT_LIB_DIR)/libsaproc.$(LIBRARY_SUFFIX)
    UNIVERSAL_LIPO_LIST += $(EXPORT_LIB_DIR)/server/libjvm.$(LIBRARY_SUFFIX)
    UNIVERSAL_LIPO_LIST += $(EXPORT_LIB_DIR)/client/libjvm.$(LIBRARY_SUFFIX)

    # Files to simply copy in place
    UNIVERSAL_COPY_LIST += $(EXPORT_LIB_DIR)/server/Xusage.txt
    UNIVERSAL_COPY_LIST += $(EXPORT_LIB_DIR)/client/Xusage.txt

    ifeq ($(STATIC_BUILD),true)
      UNIVERSAL_COPY_LIST += $(EXPORT_LIB_DIR)/server/libjvm.symbols
      UNIVERSAL_COPY_LIST += $(EXPORT_LIB_DIR)/client/libjvm.symbols
      UNIVERSAL_COPY_LIST += $(EXPORT_LIB_DIR)/minimal/libjvm.symbols
    endif

    ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
      ifeq ($(ZIP_DEBUGINFO_FILES),1)
          UNIVERSAL_COPY_LIST += $(EXPORT_LIB_DIR)/server/libjvm.diz
          UNIVERSAL_COPY_LIST += $(EXPORT_LIB_DIR)/client/libjvm.diz
          UNIVERSAL_COPY_LIST += $(EXPORT_LIB_DIR)/libjsig.diz
          UNIVERSAL_COPY_LIST += $(EXPORT_LIB_DIR)/libsaproc.diz
      else
          UNIVERSAL_COPY_LIST += $(EXPORT_LIB_DIR)/server/libjvm.$(LIBRARY_SUFFIX).dSYM
          UNIVERSAL_COPY_LIST += $(EXPORT_LIB_DIR)/client/libjvm.$(LIBRARY_SUFFIX).dSYM
          UNIVERSAL_COPY_LIST += $(EXPORT_LIB_DIR)/libjsig.$(LIBRARY_SUFFIX).dSYM
          UNIVERSAL_COPY_LIST += $(EXPORT_LIB_DIR)/libsaproc.$(LIBRARY_SUFFIX).dSYM
      endif
    endif

  endif
endif
