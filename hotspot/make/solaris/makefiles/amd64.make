#
# Copyright 2004-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
# Obj_Files += solaris_amd64.o
Obj_Files += solaris_x86_64.o

#
# Special case flags for compilers and compiler versions on amd64.
#
ifeq ("${Platform_compiler}", "sparcWorks")

# Temporary until C++ compiler is fixed

# _lwp_create_interpose must have a frame
OPT_CFLAGS/os_solaris_x86_64.o = -xO1
# force C++ interpreter to be full optimization
#OPT_CFLAGS/interpret.o = -fast -O4

# Temporary until SS10 C++ compiler is fixed
OPT_CFLAGS/generateOptoStub.o = -xO2
OPT_CFLAGS/thread.o = -xO2

# Work around for 6624782
OPT_CFLAGS/instanceKlass.o = -Qoption ube -no_a2lf
OPT_CFLAGS/objArrayKlass.o = -Qoption ube -no_a2lf

else

ifeq ("${Platform_compiler}", "gcc")
# gcc
# The serviceability agent relies on frame pointer (%rbp) to walk thread stack
CFLAGS += -fno-omit-frame-pointer
# force C++ interpreter to be full optimization
#OPT_CFLAGS/interpret.o = -O3

else
# error
_JUNK2_ := $(shell echo >&2 \
       "*** ERROR: this compiler is not yet supported by this code base!")
       @exit 1
endif
endif
