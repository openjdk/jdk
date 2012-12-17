#
# Copyright (c) 1998, 2012, Oracle and/or its affiliates. All rights reserved.
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

# Sets make macros for making optimized version of Gamma VM
# (This is the "product", not the "release" version.)

# Compiler specific OPT_CFLAGS are passed in from gcc.make, sparcWorks.make
OPT_CFLAGS/DEFAULT= $(OPT_CFLAGS)
OPT_CFLAGS/BYFILE = $(OPT_CFLAGS/$@)$(OPT_CFLAGS/DEFAULT$(OPT_CFLAGS/$@))

# (OPT_CFLAGS/SLOWER is also available, to alter compilation of buggy files)
ifeq ("${Platform_compiler}", "sparcWorks")

ifeq ($(COMPILER_REV_NUMERIC), 510)
# CC 5.10 has bug XXXXX with -xO4
OPT_CFLAGS/jvmtiClassFileReconstituter.o = $(OPT_CFLAGS/O2)
endif # COMPILER_REV_NUMERIC == 510

ifeq ($(shell expr $(COMPILER_REV_NUMERIC) \>= 509), 1)
# dtrace cannot handle tail call optimization (6672627, 6693876)
OPT_CFLAGS/jni.o = $(OPT_CFLAGS/DEFAULT) $(OPT_CCFLAGS/NO_TAIL_CALL_OPT)
endif # COMPILER_NUMERIC_REV >= 509

# Workaround SS11 bug 6345274 (all platforms) (Fixed in SS11 patch and SS12)
ifeq ($(COMPILER_REV_NUMERIC),508)
OPT_CFLAGS/ciTypeFlow.o = $(OPT_CFLAGS/O2)
endif # COMPILER_REV_NUMERIC == 508

endif # Platform_compiler == sparcWorks

# If you set HOTSPARC_GENERIC=yes, you disable all OPT_CFLAGS settings
CFLAGS$(HOTSPARC_GENERIC) += $(OPT_CFLAGS/BYFILE)

# Linker mapfiles
MAPFILE = $(GAMMADIR)/make/solaris/makefiles/mapfile-vers

# This mapfile is only needed when compiling with dtrace support, 
# and mustn't be otherwise.
MAPFILE_DTRACE = $(GAMMADIR)/make/solaris/makefiles/mapfile-vers-$(TYPE)

# Set the environment variable HOTSPARC_GENERIC to "true"
# to inhibit the effect of the previous line on CFLAGS.

VERSION = optimized
