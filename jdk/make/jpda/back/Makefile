#
# Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
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

BUILDDIR = ../..
MODULE  = debugging
LIBRARY = jdwp
PRODUCT = jpda

FILES_m = mapfile-vers

include $(BUILDDIR)/common/Defs.gmk

# Use the mapfile-vers (See the mapfile located with this Makefile)
ifdef FILES_m
include $(BUILDDIR)/common/Mapfile-vers.gmk
endif

OTHER_INCLUDES = -I$(SHARE_SRC)/transport/export \
                 -I$(SHARE_SRC)/back/export \
                 -I$(SHARE_SRC)/npt \
                 -I$(PLATFORM_SRC)/npt \
                 -I$(SHARE_SRC)/back \
                 -I$(PLATFORM_SRC)/back \
                 -I$(GENNATIVESRCDIR)/jdwp

ifneq ($(PLATFORM), windows)
  OTHER_LDLIBS += -ldl
endif # PLATFORM

#
# This controls the ability to do logging in the library.
#
CPPFLAGS_DBG += -DJDWP_LOGGING
CPPFLAGS_OPT += -DJDWP_LOGGING

#
# Files to compile.
#
FILES_c = \
        ArrayReferenceImpl.c \
        ArrayTypeImpl.c \
        ClassTypeImpl.c \
        ClassLoaderReferenceImpl.c \
        ClassObjectReferenceImpl.c \
        EventRequestImpl.c \
        FieldImpl.c \
        FrameID.c \
        MethodImpl.c \
        ObjectReferenceImpl.c \
        ReferenceTypeImpl.c \
        SDE.c \
        StackFrameImpl.c \
        StringReferenceImpl.c \
        ThreadGroupReferenceImpl.c \
        ThreadReferenceImpl.c \
        VirtualMachineImpl.c \
        bag.c \
        commonRef.c \
        classTrack.c \
        debugDispatch.c \
        debugInit.c \
        debugLoop.c \
        error_messages.c \
        eventFilter.c \
        eventHandler.c \
        eventHelper.c \
        inStream.c \
        invoker.c \
        log_messages.c \
        outStream.c \
        standardHandlers.c \
        stepControl.c \
        stream.c \
        threadControl.c \
        transport.c \
        util.c \
	linker_md.c \
	exec_md.c

#
# Rules.
#
include $(BUILDDIR)/common/Library.gmk

# We don't want to link against -ljava
JAVALIB=

#
# Add to ambient vpath so we pick up the library files
#
ifeq ($(PLATFORM), windows)
vpath %.c $(SHARE_SRC)/back:$(SHARE_SRC)/back
endif # PLATFORM
vpath %.c $(SHARE_SRC)/back:$(PLATFORM_SRC)/back

