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

# Rules to build fix_empty_sec_hdr_flags, used by various Makefiles on Solaris

BUILDDIR = ../..
include $(BUILDDIR)/common/Defs.gmk

# Default name
PROGRAM = fix_empty_sec_hdr_flags
FIX_EMPTY_SEC_HDR_FLAGS_PROG = $(ABS_BUILDTOOLBINDIR)/$(PROGRAM)

FIX_EMPTY_SEC_HDR_FLAGS_SRC     = fix_empty_sec_hdr_flags.c
FIX_EMPTY_SEC_HDR_FLAGS_FLAGS   = 
LIBS_FIX_EMPTY_SEC_HDR_FLAGS   += -lelf

all: $(FIX_EMPTY_SEC_HDR_FLAGS)

$(FIX_EMPTY_SEC_HDR_FLAGS_PROG): $(FIX_EMPTY_SEC_HDR_FLAGS_SRC)
	$(MKDIR) -p $(BUILDTOOLBINDIR)
	$(CC) -g -o $@ $< $(FIX_EMPTY_SEC_HDR_FLAGS_FLAGS) $(LIBS_FIX_EMPTY_SEC_HDR_FLAGS)

clean::
	$(RM) $(FIX_EMPTY_SEC_HDR_FLAGS_PROG)

