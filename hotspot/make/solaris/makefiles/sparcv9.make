#
# Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

ASFLAGS += $(AS_ARCHFLAG)

ifeq ("${Platform_compiler}", "sparcWorks")
ifeq ($(shell expr $(COMPILER_REV_NUMERIC) \< 505), 1)
# When optimized fully, stubGenerator_sparc.cpp 
# has bogus code for the routine 
# StubGenerator::generate_flush_callers_register_windows() 
OPT_CFLAGS/stubGenerator_sparc.o = $(OPT_CFLAGS/SLOWER)

# For now ad_sparc file is compiled with -O2 %%%% remove when adlc is fixed
OPT_CFLAGS/ad_sparc.o = $(OPT_CFLAGS/SLOWER)
OPT_CFLAGS/dfa_sparc.o = $(OPT_CFLAGS/SLOWER)

# CC brings an US-II to its knees compiling the vmStructs asserts under -xO4
OPT_CFLAGS/vmStructs.o = $(OPT_CFLAGS/O2)
endif

else
#Options for gcc
OPT_CFLAGS/stubGenerator_sparc.o = $(OPT_CFLAGS/SLOWER)
OPT_CFLAGS/ad_sparc.o = $(OPT_CFLAGS/SLOWER)
OPT_CFLAGS/dfa_sparc.o = $(OPT_CFLAGS/SLOWER)
OPT_CFLAGS/vmStructs.o = $(OPT_CFLAGS/O2)
endif
