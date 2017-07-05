#
# Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building tools
#

BUILDDIR = ../..
MODULE  = tools
PACKAGE = sun.tools
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
AUTO_FILES_JAVA_DIRS = \
  sun/tools/jinfo \
  sun/tools/jmap \
  sun/tools/jps \
  sun/tools/jstack \
  sun/tools/jstat \
  sun/tools/jstatd \
  com/sun/tools/hat \
  com/sun/tools/script/shell \
  sun/jvmstat

# Extra jstat files
FILES_copy += \
  $(CLASSDESTDIR)/sun/tools/jstat/resources/jstat_options \
  $(CLASSDESTDIR)/sun/tools/jstat/resources/jstat_unsupported_options

# Extra jhat files
JHAT_RESOURCEDIR = $(CLASSDESTDIR)/com/sun/tools/hat/resources
FILES_copy += \
  $(JHAT_RESOURCEDIR)/hat.js \
  $(JHAT_RESOURCEDIR)/oqlhelp.html \
  $(JHAT_RESOURCEDIR)/platform_names.txt

# Extra jrunscript files
JRUNSCRIPT_RESOURCEDIR = $(CLASSDESTDIR)/com/sun/tools/script/shell
FILES_copy += \
  $(JRUNSCRIPT_RESOURCEDIR)/init.js \
  $(JRUNSCRIPT_RESOURCEDIR)/messages.properties 

# Extra jvmstat files
FILES_copy += \
  $(CLASSDESTDIR)/sun/jvmstat/perfdata/resources/aliasmap

# Build should include the file copies
build: copy-files

copy-files: $(FILES_copy)

$(CLASSDESTDIR)/%: $(SHARE_SRC)/classes/%
	$(install-file)

clean clobber::
	$(RM) $(FILES_copy)

.PHONY: copy-files

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk

