#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

# Rules to build add_gnu_debuglink, used by vm.make on Solaris

# Allow $(ADD_GNU_DEBUGLINK) to be called from any directory.
# We don't set or use the GENERATED macro to avoid affecting
# other HotSpot Makefiles.
TOPDIR                    = $(shell echo `pwd`)
ADD_GNU_DEBUGLINK         = $(TOPDIR)/../generated/add_gnu_debuglink

ADD_GNU_DEBUGLINK_DIR     = $(GAMMADIR)/src/os/solaris/add_gnu_debuglink
ADD_GNU_DEBUGLINK_SRC     = $(ADD_GNU_DEBUGLINK_DIR)/add_gnu_debuglink.c
ADD_GNU_DEBUGLINK_FLAGS   = 
LIBS_ADD_GNU_DEBUGLINK   += -lelf

ifeq ("${Platform_compiler}", "sparcWorks")
# Enable the following ADD_GNU_DEBUGLINK_FLAGS addition if you need to
# compare the built ELF objects.
#
# The -g option makes static data global and the "-W0,-noglobal"
# option tells the compiler to not globalize static data using a unique
# globalization prefix. Instead force the use of a static globalization
# prefix based on the source filepath so the objects from two identical
# compilations are the same.
#
# Note: The blog says to use "-W0,-xglobalstatic", but that doesn't
#       seem to work. I got "-W0,-noglobal" from Kelly and that works.
#ADD_GNU_DEBUGLINK_FLAGS += -W0,-noglobal
endif # Platform_compiler == sparcWorks

$(ADD_GNU_DEBUGLINK): $(ADD_GNU_DEBUGLINK_SRC)
	$(CC) -g -o $@ $< $(ADD_GNU_DEBUGLINK_FLAGS) $(LIBS_ADD_GNU_DEBUGLINK)
