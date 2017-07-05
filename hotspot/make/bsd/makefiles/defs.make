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

# The common definitions for hotspot bsd builds.
# Include the top level defs.make under make directory instead of this one.
# This file is included into make/defs.make.

SLASH_JAVA ?= /java

# Need PLATFORM (os-arch combo names) for jdk and hotspot, plus libarch name
ARCH:=$(shell uname -m)
PATH_SEP = :
ifeq ($(LP64), 1)
  ARCH_DATA_MODEL ?= 64
else
  ARCH_DATA_MODEL ?= 32
endif

# zero
ifeq ($(ZERO_BUILD), true)
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

JDK_INCLUDE_SUBDIR=bsd

# Library suffix
OS_VENDOR:=$(shell uname -s)
ifeq ($(OS_VENDOR),Darwin)
  LIBRARY_SUFFIX=dylib
else
  LIBRARY_SUFFIX=so
endif

# FIXUP: The subdirectory for a debug build is NOT the same on all platforms
VM_DEBUG=jvmg

EXPORT_LIST += $(EXPORT_DOCS_DIR)/platform/jvmti/jvmti.html

# client and server subdirectories have symbolic links to ../libjsig.so
EXPORT_LIST += $(EXPORT_JRE_LIB_ARCH_DIR)/libjsig.$(LIBRARY_SUFFIX)
EXPORT_SERVER_DIR = $(EXPORT_JRE_LIB_ARCH_DIR)/server

ifndef BUILD_CLIENT_ONLY
EXPORT_LIST += $(EXPORT_SERVER_DIR)/Xusage.txt
EXPORT_LIST += $(EXPORT_SERVER_DIR)/libjvm.$(LIBRARY_SUFFIX)
endif

ifneq ($(ZERO_BUILD), true)
  ifeq ($(ARCH_DATA_MODEL), 32)
    EXPORT_CLIENT_DIR = $(EXPORT_JRE_LIB_ARCH_DIR)/client
    EXPORT_LIST += $(EXPORT_CLIENT_DIR)/Xusage.txt
    EXPORT_LIST += $(EXPORT_CLIENT_DIR)/libjvm.$(LIBRARY_SUFFIX)
  endif
endif

# Serviceability Binaries
# No SA Support for PPC, IA64, ARM or zero
ADD_SA_BINARIES/x86   = $(EXPORT_JRE_LIB_ARCH_DIR)/libsaproc.$(LIBRARY_SUFFIX) \
                        $(EXPORT_LIB_DIR)/sa-jdi.jar
ADD_SA_BINARIES/sparc = $(EXPORT_JRE_LIB_ARCH_DIR)/libsaproc.$(LIBRARY_SUFFIX) \
                        $(EXPORT_LIB_DIR)/sa-jdi.jar
ADD_SA_BINARIES/ppc   =
ADD_SA_BINARIES/ia64  =
ADD_SA_BINARIES/arm   =
ADD_SA_BINARIES/zero  =

EXPORT_LIST += $(ADD_SA_BINARIES/$(HS_ARCH))
