#
# Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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

#
# IA64 only uses c++ based interpreter
CFLAGS += -DCC_INTERP -D_LP64=1 -DVM_LITTLE_ENDIAN
ifeq ($(VERSION),debug)
ASM_FLAGS= -DDEBUG
else
ASM_FLAGS=
endif
# workaround gcc bug in compiling varargs
OPT_CFLAGS/jni.o = -O0

# gcc/ia64 has a bug that internal gcc functions linked with libjvm.so
# are made public. Hiding those symbols will cause undefined symbol error
# when VM is dropped into older JDK. We probably will need an IA64
# mapfile to include those symbols as a workaround. Disable linker mapfile 
# for now.
LDNOMAP=true
