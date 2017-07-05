#
# Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
# Copyright 2012, 2013 SAP AG. All rights reserved.
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

# Sets make macros for making debug version of VM

# Compiler specific OPT_CFLAGS are passed in from gcc.make, sparcWorks.make
# Pare down optimization to -O2 if xlCV10.1 is in use.
OPT_CFLAGS/DEFAULT= $(OPT_CFLAGS) $(QV10_OPT_CONSERVATIVE)
OPT_CFLAGS/BYFILE = $(OPT_CFLAGS/$@)$(OPT_CFLAGS/DEFAULT$(OPT_CFLAGS/$@))

# (OPT_CFLAGS/SLOWER is also available, to alter compilation of buggy files)

ifeq ($(BUILDARCH), ia64)
  # Bug in GCC, causes hang.  -O1 will override the -O3 specified earlier
  OPT_CFLAGS/callGenerator.o += -O1
  OPT_CFLAGS/ciTypeFlow.o += -O1
  OPT_CFLAGS/compile.o += -O1
  OPT_CFLAGS/concurrentMarkSweepGeneration.o += -O1
  OPT_CFLAGS/doCall.o += -O1
  OPT_CFLAGS/generateOopMap.o += -O1
  OPT_CFLAGS/generateOptoStub.o += -O1
  OPT_CFLAGS/graphKit.o += -O1
  OPT_CFLAGS/instanceKlass.o += -O1
  OPT_CFLAGS/interpreterRT_ia64.o += -O1
  OPT_CFLAGS/output.o += -O1
  OPT_CFLAGS/parse1.o += -O1
  OPT_CFLAGS/runtime.o += -O1
  OPT_CFLAGS/synchronizer.o += -O1
endif


# If you set HOTSPARC_GENERIC=yes, you disable all OPT_CFLAGS settings
CFLAGS$(HOTSPARC_GENERIC) += $(OPT_CFLAGS/BYFILE)

# Set the environment variable HOTSPARC_GENERIC to "true"
# to inhibit the effect of the previous line on CFLAGS.

# Linker mapfile
MAPFILE = $(GAMMADIR)/make/aix/makefiles/mapfile-vers-debug

# xlc 10.1 parameters for ipa linkage.
#  - remove ipa linkage altogether. Does not seem to benefit performance, 
#    but increases code footprint.
#  - this is a debug build in the end. Extra effort for ipa linkage is thus 
#    not justified.
LFLAGS_QIPA=

VERSION = optimized
SYSDEFS += -DASSERT
PICFLAGS = DEFAULT
