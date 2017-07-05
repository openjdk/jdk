#
# Copyright (c) 1998, 2011, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building the JDI back-end implementation
#

BUILDDIR = ../../..
LIBRARY = dt_socket
PRODUCT = jbug

# Mapfile only used on Solaris/Linux
FILES_m = mapfile-vers

include $(BUILDDIR)/common/Defs.gmk

ifeq ($(PLATFORM), linux)
  OTHER_LDLIBS += $(LIBNSL) $(LIBSOCKET) -lpthread
endif

ifeq ($(PLATFORM), macosx))
  LIBSOCKET =
  OTHER_LDLIBS += -pthread
endif

ifeq ($(PLATFORM), solaris)
  OTHER_LDLIBS += $(LIBNSL) $(LIBSOCKET)
endif

ifeq ($(PLATFORM), windows)
  OTHER_LDLIBS += ws2_32.lib
endif

OTHER_INCLUDES = -I$(INCLUDEDIR) -I$(PLATFORM_INCLUDE) \
                 -I$(SHARE_SRC)/transport/export \
                 -I$(SHARE_SRC)/transport/socket \
                 -I$(PLATFORM_SRC)/transport/socket \
                 -I$(SHARE_SRC)/back/export \
                 -I$(PLATFORM_SRC)/back

#
# Files to compile.
#
FILES_c = \
        socketTransport.c \
        socket_md.c

# Use the mapfile-vers (See the mapfile located with this Makefile)
ifdef FILES_m
  include $(BUILDDIR)/common/Mapfile-vers.gmk
endif

#
# Rules.
#
include $(BUILDDIR)/common/Library.gmk

# We don't want to link against -ljava
JAVALIB=

# Add -export options to explicitly spell exported symbols
ifeq ($(PLATFORM), windows)
  OTHER_LCF += -export:jdwpTransport_OnLoad
endif

#
# Add to ambient vpath so we pick up the library files
#
vpath %.c $(SHARE_SRC)/transport/socket:$(PLATFORM_SRC)/transport/socket
