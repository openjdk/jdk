#
# Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building dtrace extension
#

LIBRARY_SUPPORTS_FULL_DEBUG_SYMBOLS=1

BUILDDIR = ../../..
PACKAGE = sun.tracing.dtrace
LIBRARY = jsdt
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

#
# Use mapfile
#
FILES_m = mapfile-vers
include $(BUILDDIR)/common/Mapfile-vers.gmk

NATIVE_DTRACE_DIR=native/sun/tracing/dtrace

SRCDIR=$(SHARE_SRC)/$(NATIVE_DTRACE_DIR)
PSRCDIR=$(PLATFORM_SRC)/$(NATIVE_DTRACE_DIR)

FILES_c = JVM.c jvm_symbols_md.c

FILES_java = \
    sun/tracing/dtrace/Activation.java \
    sun/tracing/dtrace/DTraceProvider.java \
    sun/tracing/dtrace/DTraceProbe.java \
    sun/tracing/dtrace/DTraceProviderFactory.java \
    sun/tracing/dtrace/JVM.java

FILES_export = $(FILES_java)

ifeq ($(PLATFORM), linux)
OTHER_LDLIBS += $(LIBDL)
endif

#
# Use JNI for generating header files
#
JAVAHFLAGS += -jni

#
# Don't need to link against -ljava
#
JAVALIB=

#
# Rules.
#
include $(BUILDDIR)/common/Library.gmk

#
# Add to ambient vpath so we pick up the library files
#
vpath %.c $(SRCDIR):$(PSRCDIR)
