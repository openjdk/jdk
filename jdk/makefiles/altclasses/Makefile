#
# Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building alternate runtime classes (not used by default)
#

BUILDDIR = ..

PRODUCT = altclasses

include $(BUILDDIR)/common/Defs.gmk

# Root of alternate class sources

ALTCLASSES_SRCDIR = $(CLOSED_SRC)/share/altclasses

# Alternate runtime classes

ALTRT_JAR_FILE = $(LIBDIR)/alt-rt.jar
ALTRT_JAR_SOURCE_FILE = $(TEMPDIR)/alt-rt.jarsrclist
ALTRT_JAR_SOURCES = $(wildcard $(ALTCLASSES_SRCDIR)/java/*/*.java)

# Use a special file suffix for the file that holds the source list

.SUFFIXES: .jarsrclist

# Build rules

all build: 
	@if [ -d $(ALTCLASSES_SRCDIR) ] ; then \
	   $(MAKE) $(ALTRT_JAR_FILE); \
	fi

# Source list file creation

$(ALTRT_JAR_SOURCE_FILE): $(ALTRT_JAR_SOURCES) FRC
	$(prep-target)
	$(ECHO) $(ALTRT_JAR_SOURCES) > $@

clean clobber::
	$(RM) $(ALTRT_JAR_FILE) $(ALTRT_JAR_SOURCE_FILE) 
	$(RM) -r $(ALTRT_JAR_SOURCE_FILE).classes

include $(BUILDDIR)/common/Classes.gmk

# Pattern rule to turn a source list file into a jar file
$(LIBDIR)/%.jar : $(TEMPDIR)/%.jarsrclist
	$(prep-target)
	$(RM) -r $(<).classes
	$(MKDIR) -p $(<).classes
	$(JAVAC_CMD) -implicit:none -d $(<).classes @$<
	$(BOOT_JAR_CMD) cf $@ -C $(<).classes . $(BOOT_JAR_JFLAGS)

# Force target

FRC:

# Non file targets

.PHONY: all build clean clobber

