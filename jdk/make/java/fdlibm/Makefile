#
# Copyright (c) 1998, 2010, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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
# Makefile for fdlibm
#
# Note:
# The fdlibm libraries are built using special rules in Library.gmk.
#

BUILDDIR     = ../..
MODULE       = base
LIBRARY      = fdlibm
PRODUCT      = java

include $(BUILDDIR)/common/Defs.gmk

#
# Where is fdlibm in the source tree?
#
FDLIBM_SRC = $(SHARE_SRC)/native/java/lang/fdlibm

# Windows: compiler flags
ifeq ($(PLATFORM),windows)
  # Turn all optimizations off
  OPTIMIZATION_LEVEL = NONE
  FASTDEBUG_OPTIMIZATION_LEVEL = NONE
  OTHER_CFLAGS =
  CPPFLAGS_DBG += -DLOGGING
endif

#
# Linux: Disable optimization to get correctly reproducible
# floating-point results.
#
ifeq ($(PLATFORM),linux)
  # Turn all optimizations off
  OPTIMIZATION_LEVEL = NONE
  FASTDEBUG_OPTIMIZATION_LEVEL = NONE
endif

#
# Include path.
#
OTHER_INCLUDES = -I$(FDLIBM_SRC)/include

#
# Things that must be linked in.
#
OTHER_LDLIBS =

#
# Files to compile.
#
include FILES_c.gmk

#
# Rules for the .a file.
#
include $(BUILDDIR)/common/Library.gmk

#
# Find fdlibm source files.
#
vpath %.c
vpath %.c $(FDLIBM_SRC)/src
