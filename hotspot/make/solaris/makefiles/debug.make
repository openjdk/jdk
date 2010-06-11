#
# Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
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

# Compiler specific DEBUG_CFLAGS are passed in from gcc.make, sparcWorks.make
DEBUG_CFLAGS/DEFAULT= $(DEBUG_CFLAGS)
DEBUG_CFLAGS/BYFILE = $(DEBUG_CFLAGS/$@)$(DEBUG_CFLAGS/DEFAULT$(DEBUG_CFLAGS/$@))

ifeq ("${Platform_compiler}", "sparcWorks")

ifeq ($(COMPILER_REV_NUMERIC),508)
  # SS11 SEGV when compiling with -g and -xarch=v8, using different backend
  DEBUG_CFLAGS/compileBroker.o = $(DEBUG_CFLAGS) -xO0
  DEBUG_CFLAGS/jvmtiTagMap.o   = $(DEBUG_CFLAGS) -xO0
endif
endif

CFLAGS += $(DEBUG_CFLAGS/BYFILE)

# Linker mapfiles
MAPFILE = $(GAMMADIR)/make/solaris/makefiles/mapfile-vers \
          $(GAMMADIR)/make/solaris/makefiles/mapfile-vers-debug \
          $(GAMMADIR)/make/solaris/makefiles/mapfile-vers-nonproduct

# This mapfile is only needed when compiling with dtrace support, 
# and mustn't be otherwise.
MAPFILE_DTRACE = $(GAMMADIR)/make/solaris/makefiles/mapfile-vers-$(TYPE)

_JUNK_ := $(shell echo >&2 ""\
 "-------------------------------------------------------------------------\n" \
 "WARNING: 'gnumake debug' is deprecated. It will be removed in the future.\n" \
 "Please use 'gnumake jvmg' to build debug JVM.                            \n" \
 "-------------------------------------------------------------------------\n")

G_SUFFIX = _g
VERSION = debug
SYSDEFS += -DASSERT -DDEBUG
PICFLAGS = DEFAULT
