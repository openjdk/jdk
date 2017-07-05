#
# Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

BUILDDIR = ../../..
PACKAGE = oracle.jrockit.jfr
LIBRARY = jfr
PRODUCT = oracle
include $(BUILDDIR)/common/Defs.gmk

#
# Use mapfile
#
FILES_m = $(CLOSED_SHARE_SRC)/native/oracle/jfr/mapfile-vers
include $(BUILDDIR)/common/Mapfile-vers.gmk

#
# Files to compile
#
FILES_c = VMJFR.c

AUTO_FILES_JAVA_DIRS = com/oracle/jrockit/jfr oracle/jrockit/jfr

JFC_XSD = oracle/jrockit/jfr/settings/jfc.xsd
JFC_XSD_SRC = $(CLOSED_SHARE_SRC)/classes/$(JFC_XSD)
JFC_XSD_FILE = $(CLASSDESTDIR)/$(JFC_XSD)


# Find C source files
#
vpath %.c $(CLOSED_SHARE_SRC)/native/oracle/jfr

#
# Library to compile.
#
include $(BUILDDIR)/common/Library.gmk

JVMLIB =
JAVALIB =
OTHER_LDLIBS =

clean clobber::
	$(RM) -r $(CLASSDESTDIR)/com/oracle/jrockit/jfr
	$(RM) -r $(CLASSDESTDIR)/oracle/jrockit/jfr

# Copy pre-shipped .jfc files
JFR_LIBDIR = $(LIBDIR)/jfr
JFC_SRCDIR = $(CLOSED_SHARE_SRC)/classes/oracle/jrockit/jfr/settings

$(JFR_LIBDIR)/%.jfc: $(JFC_SRCDIR)/%.jfc
	$(install-file)

JFC_FILES := $(subst $(JFC_SRCDIR),$(JFR_LIBDIR),$(wildcard $(JFC_SRCDIR)/*.jfc))

$(JFC_XSD_FILE) : $(JFC_XSD_SRC)
	$(install-file)

all build : $(JFC_FILES) $(JFC_XSD_FILE)

