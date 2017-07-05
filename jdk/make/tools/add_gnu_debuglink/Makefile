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

# Rules to build add_gnu_debuglink, used by various Makefiles on Solaris

BUILDDIR = ../..
include $(BUILDDIR)/common/Defs.gmk

# Default name
PROGRAM = add_gnu_debuglink
ADD_GNU_DEBUGLINK_PROG = $(ABS_BUILDTOOLBINDIR)/$(PROGRAM)

ADD_GNU_DEBUGLINK_SRC     = add_gnu_debuglink.c
ADD_GNU_DEBUGLINK_FLAGS   = 
LIBS_ADD_GNU_DEBUGLINK   += -lelf

all: $(ADD_GNU_DEBUGLINK)

$(ADD_GNU_DEBUGLINK_PROG): $(ADD_GNU_DEBUGLINK_SRC)
	$(MKDIR) -p $(BUILDTOOLBINDIR)
	$(CC) -g -o $@ $< $(ADD_GNU_DEBUGLINK_FLAGS) $(LIBS_ADD_GNU_DEBUGLINK)

clean::
	$(RM) $(ADD_GNU_DEBUGLINK_PROG)

