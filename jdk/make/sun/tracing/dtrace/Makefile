#
# Copyright 2007 Sun Microsystems, Inc. All rights reserved.
# SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
#

#
# Makefile for building dtrace extension
#
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
OTHER_LDLIBS += -ldl
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
