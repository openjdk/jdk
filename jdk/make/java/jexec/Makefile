#
# Copyright (c) 1999, 2005, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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
# Makefile for building jexec, a wrapper for direct execution of JAR files
# on UNIX systems.
#

BUILDDIR = ../..

PACKAGE = java.jexec
PRODUCT = sun
PROGRAM = jexec

include $(BUILDDIR)/common/Defs.gmk

ifeq ($(COMPILE_APPROACH),batch)
  override COMPILE_APPROACH = normal
endif

include $(BUILDDIR)/common/Rules.gmk

JEXE_SRC =      $(PLATFORM_SRC)/bin

FILES_c =       jexec.c
FILES_o =       $(patsubst %.c,$(OBJDIR)/%.o,$(FILES_c))

vpath %.c       $(JEXE_SRC)

ifeq ($(PLATFORM), linux)
  #
  # On Linux jexec goes in lib, not lib/<arch>
  #
  LIB_LOCATION = $(LIBDIR)

  #
  # Also include the manifest_info.h header file.
  #
  OTHER_INCLUDES += -I$(SHARE_SRC)/bin
endif # PLATFORM

build: $(LIB_LOCATION)/$(PROGRAM)

$(LIB_LOCATION)/$(PROGRAM):: $(FILES_o)
	$(prep-target)
	$(LINK_PRE_CMD) $(CC) $(LDFLAGS) $(FILES_o) $(CC_OBJECT_OUTPUT_FLAG)$@

clean clobber:: 
	$(RM) $(FILES_o) $(LIB_LOCATION)/$(PROGRAM)

