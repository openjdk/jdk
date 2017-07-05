#
# Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for com.sun.nio.sctp
#

BUILDDIR = ../../../..
MODULE  = sctp
PACKAGE = com.sun.nio.sctp
LIBRARY = sctp
PRODUCT = sun
#OTHER_JAVACFLAGS += -Xmaxwarns 1000 -Xlint
include $(BUILDDIR)/common/Defs.gmk

#
# Files to compile
#
include FILES_c.gmk
include FILES_java.gmk
include Exportedfiles.gmk

ifneq ($(PLATFORM), windows)
include $(BUILDDIR)/common/Mapfile-vers.gmk
include $(BUILDDIR)/common/Library.gmk

#
# Find platform-specific C source files
#
vpath %.c $(PLATFORM_SRC)/native/sun/nio/ch

#
# Include nio.h, net_util.h, sun_nio_ch_IOStatus.h, etc
#
OTHER_INCLUDES += \
  -I$(SHARE_SRC)/native/sun/nio/ch \
  -I$(SHARE_SRC)/native/java/net \
  -I$(PLATFORM_SRC)/native/java/net \
  -I$(CLASSHDRDIR)/../../../../java/java.nio/nio/CClassHeaders

ifeq ($(PLATFORM), linux)
COMPILER_WARNINGS_FATAL=true
#OTHER_LDLIBS += -L$(LIBDIR)/$(LIBARCH) -ljava -lnet -lpthread -ldl
OTHER_LDLIBS += -L$(LIBDIR)/$(LIBARCH) -lnio -lnet -lpthread -ldl
endif
ifeq ($(PLATFORM), solaris)
#LIBSCTP = -lsctp
OTHER_LDLIBS += $(LIBSOCKET) -L$(LIBDIR)/$(LIBARCH) -lnet -lnio
endif # PLATFORM

else # windows
include $(BUILDDIR)/common/Classes.gmk
endif # ifneq windows


clean clobber::
	$(RM) -r $(CLASSDESTDIR)/com/sun/nio/sctp
	$(RM) -r $(CLASSDESTDIR)/sun/nio/ch

