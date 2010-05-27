#
# Copyright 2004-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

# Must also specify if CPU is little endian
CFLAGS += -DVM_LITTLE_ENDIAN

# Not included in includeDB because it has no dependencies
Obj_Files += solaris_x86_64.o

#
# Special case flags for compilers and compiler versions on amd64.
#
ifeq ("${Platform_compiler}", "sparcWorks")

# Temporary until SS10 C++ compiler is fixed
OPT_CFLAGS/generateOptoStub.o = -xO2

else

ifeq ("${Platform_compiler}", "gcc")
# gcc
# The serviceability agent relies on frame pointer (%rbp) to walk thread stack
CFLAGS += -fno-omit-frame-pointer

else
# error
_JUNK2_ := $(shell echo >&2 \
       "*** ERROR: this compiler is not yet supported by this code base!")
       @exit 1
endif
endif
