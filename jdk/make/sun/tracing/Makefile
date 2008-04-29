#
# Copyright 2007 Sun Microsystems, Inc. All rights reserved.
# SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
#

#
# Makefile for building tracing package implementation classes
#

BUILDDIR = ../..
PACKAGE = sun.tracing
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

SUBDIRS = dtrace 
all build:
	$(SUBDIRS-loop)
clean clobber::
	$(SUBDIRS-loop)

AUTO_FILES_JAVA_DIRS = sun/tracing

#
# Rules.
#
include $(BUILDDIR)/common/Classes.gmk
