#
# Copyright (c) 2006, 2012, Oracle and/or its affiliates. All rights reserved.
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

# The common definitions for hotspot solaris builds.
# Include the top level defs.make under make directory instead of this one.
# This file is included into make/defs.make.

# Need PLATFORM (os-arch combo names) for jdk and hotspot, plus libarch name
SLASH_JAVA ?= /java
ARCH:=$(shell uname -p)
PATH_SEP = :
ifeq ($(LP64), 1)
  ARCH_DATA_MODEL=64
else
  ARCH_DATA_MODEL=32
endif

ifeq ($(ARCH),sparc)
  ifeq ($(ARCH_DATA_MODEL), 64)
    MAKE_ARGS += LP64=1
    PLATFORM=solaris-sparcv9
    VM_PLATFORM=solaris_sparcv9
  else
    PLATFORM=solaris-sparc
    VM_PLATFORM=solaris_sparc
  endif
  HS_ARCH=sparc
else
  ifeq ($(ARCH_DATA_MODEL), 64)
    MAKE_ARGS += LP64=1
    PLATFORM=solaris-amd64
    VM_PLATFORM=solaris_amd64
    HS_ARCH=x86
  else
    PLATFORM=solaris-i586
    VM_PLATFORM=solaris_i486
    HS_ARCH=x86
  endif
endif

# On 32 bit solaris we build server and client, on 64 bit just server.
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

# determine if HotSpot is being built in JDK6 or earlier version
JDK6_OR_EARLIER=0
ifeq "$(shell expr \( '$(JDK_MAJOR_VERSION)' != '' \& '$(JDK_MINOR_VERSION)' != '' \& '$(JDK_MICRO_VERSION)' != '' \))" "1"
  # if the longer variable names (newer build style) are set, then check those
  ifeq "$(shell expr \( $(JDK_MAJOR_VERSION) = 1 \& $(JDK_MINOR_VERSION) \< 7 \))" "1"
    JDK6_OR_EARLIER=1
  endif
else
  # the longer variables aren't set so check the shorter variable names
  ifeq "$(shell expr \( '$(JDK_MAJOR_VER)' = 1 \& '$(JDK_MINOR_VER)' \< 7 \))" "1"
    JDK6_OR_EARLIER=1
  endif
endif

ifeq ($(JDK6_OR_EARLIER),0)
  # Full Debug Symbols is supported on JDK7 or newer

ifdef ENABLE_FULL_DEBUG_SYMBOLS
  # Only check for Full Debug Symbols support on Solaris if it is
  # specifically enabled. Hopefully, it can be enabled by default
  # once the .debuginfo size issues are worked out.
  
  # Default OBJCOPY comes from the SUNWbinutils package:
  DEF_OBJCOPY=/usr/sfw/bin/gobjcopy
  ifeq ($(VM_PLATFORM),solaris_amd64)
    # On Solaris AMD64/X64, gobjcopy is not happy and fails:
    #
    # usr/sfw/bin/gobjcopy --add-gnu-debuglink=<lib>.debuginfo <lib>.so
    # BFD: stKPaiop: Not enough room for program headers, try linking with -N
    # /usr/sfw/bin/gobjcopy: stKPaiop: Bad value
    # BFD: stKPaiop: Not enough room for program headers, try linking with -N
    # /usr/sfw/bin/gobjcopy: libsaproc.debuginfo: Bad value
    # BFD: stKPaiop: Not enough room for program headers, try linking with -N
    # /usr/sfw/bin/gobjcopy: stKPaiop: Bad value
    _JUNK_ := $(shell \
      echo >&2 "INFO: $(DEF_OBJCOPY) is not working on Solaris AMD64/X64")
    OBJCOPY=
  else
    OBJCOPY=$(shell test -x $(DEF_OBJCOPY) && echo $(DEF_OBJCOPY))
    ifneq ($(ALT_OBJCOPY),)
      _JUNK_ := $(shell echo >&2 "INFO: ALT_OBJCOPY=$(ALT_OBJCOPY)")
      # disable .debuginfo support by setting ALT_OBJCOPY to a non-existent path
      OBJCOPY=$(shell test -x $(ALT_OBJCOPY) && echo $(ALT_OBJCOPY))
    endif
  endif
endif
  
  ifeq ($(OBJCOPY),)
    _JUNK_ := $(shell \
      echo >&2 "INFO: no objcopy cmd found so cannot create .debuginfo files.")
  else
    _JUNK_ := $(shell \
      echo >&2 "INFO: $(OBJCOPY) cmd found so will create .debuginfo files.")
  
    # Library stripping policies for .debuginfo configs:
    #   all_strip - strips everything from the library
    #   min_strip - strips most stuff from the library; leaves minimum symbols
    #   no_strip  - does not strip the library at all
    #
    # Oracle security policy requires "all_strip". A waiver was granted on
    # 2011.09.01 that permits using "min_strip" in the Java JDK and Java JRE.
    #
    DEF_STRIP_POLICY="min_strip"
    ifeq ($(ALT_STRIP_POLICY),)
      STRIP_POLICY=$(DEF_STRIP_POLICY)
    else
      STRIP_POLICY=$(ALT_STRIP_POLICY)
    endif
    _JUNK_ := $(shell \
      echo >&2 "INFO: STRIP_POLICY=$(STRIP_POLICY)")
  endif
endif

JDK_INCLUDE_SUBDIR=solaris

# Library suffix
LIBRARY_SUFFIX=so

# FIXUP: The subdirectory for a debug build is NOT the same on all platforms
VM_DEBUG=jvmg

EXPORT_LIST += $(EXPORT_DOCS_DIR)/platform/jvmti/jvmti.html

# client and server subdirectories have symbolic links to ../libjsig.$(LIBRARY_SUFFIX)
EXPORT_LIST += $(EXPORT_JRE_LIB_ARCH_DIR)/libjsig.$(LIBRARY_SUFFIX)
ifneq ($(OBJCOPY),)
  EXPORT_LIST += $(EXPORT_JRE_LIB_ARCH_DIR)/libjsig.debuginfo
endif

EXPORT_LIST += $(EXPORT_JRE_LIB_DIR)/wb.jar

EXPORT_SERVER_DIR = $(EXPORT_JRE_LIB_ARCH_DIR)/server
EXPORT_CLIENT_DIR = $(EXPORT_JRE_LIB_ARCH_DIR)/client

ifeq ($(JVM_VARIANT_SERVER),true)
  EXPORT_LIST += $(EXPORT_SERVER_DIR)/Xusage.txt
  EXPORT_LIST += $(EXPORT_SERVER_DIR)/libjvm.$(LIBRARY_SUFFIX)
  EXPORT_LIST += $(EXPORT_SERVER_DIR)/libjvm_db.$(LIBRARY_SUFFIX)
  EXPORT_LIST += $(EXPORT_SERVER_DIR)/libjvm_dtrace.$(LIBRARY_SUFFIX)
  ifeq ($(ARCH_DATA_MODEL),32)
    EXPORT_LIST += $(EXPORT_SERVER_DIR)/64/libjvm_db.$(LIBRARY_SUFFIX)
    EXPORT_LIST += $(EXPORT_SERVER_DIR)/64/libjvm_dtrace.$(LIBRARY_SUFFIX)
  endif
  ifneq ($(OBJCOPY),)
    EXPORT_LIST += $(EXPORT_SERVER_DIR)/libjvm.debuginfo
    EXPORT_LIST += $(EXPORT_SERVER_DIR)/libjvm_db.debuginfo
    EXPORT_LIST += $(EXPORT_SERVER_DIR)/libjvm_dtrace.debuginfo
  endif
endif
ifeq ($(JVM_VARIANT_CLIENT),true)
  EXPORT_LIST += $(EXPORT_CLIENT_DIR)/Xusage.txt
  EXPORT_LIST += $(EXPORT_CLIENT_DIR)/libjvm.$(LIBRARY_SUFFIX) 
  EXPORT_LIST += $(EXPORT_CLIENT_DIR)/libjvm_db.$(LIBRARY_SUFFIX) 
  EXPORT_LIST += $(EXPORT_CLIENT_DIR)/libjvm_dtrace.$(LIBRARY_SUFFIX)
  ifeq ($(ARCH_DATA_MODEL),32)
    EXPORT_LIST += $(EXPORT_CLIENT_DIR)/64/libjvm_db.$(LIBRARY_SUFFIX)
    EXPORT_LIST += $(EXPORT_CLIENT_DIR)/64/libjvm_dtrace.$(LIBRARY_SUFFIX)
  endif
  ifneq ($(OBJCOPY),)
    EXPORT_LIST += $(EXPORT_CLIENT_DIR)/libjvm.debuginfo
    EXPORT_LIST += $(EXPORT_CLIENT_DIR)/libjvm_db.debuginfo
    EXPORT_LIST += $(EXPORT_CLIENT_DIR)/libjvm_dtrace.debuginfo
    ifeq ($(ARCH_DATA_MODEL),32)
      EXPORT_LIST += $(EXPORT_CLIENT_DIR)/64/libjvm_db.debuginfo
      EXPORT_LIST += $(EXPORT_CLIENT_DIR)/64/libjvm_dtrace.debuginfo
    endif
  endif
endif

EXPORT_LIST += $(EXPORT_JRE_LIB_ARCH_DIR)/libsaproc.$(LIBRARY_SUFFIX)
ifneq ($(OBJCOPY),)
  EXPORT_LIST += $(EXPORT_JRE_LIB_ARCH_DIR)/libsaproc.debuginfo
endif
EXPORT_LIST += $(EXPORT_LIB_DIR)/sa-jdi.jar 
