#
# Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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

BUILDDIR = ../..
PACKAGE = sun.pisces
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

#
# Files
#

#
# Include all Java source files in sun/java2d/pisces
#
AUTO_FILES_JAVA_DIRS = sun/java2d/pisces

#
# Rules.
#
include $(BUILDDIR)/common/Classes.gmk

SERVICEDIR = $(CLASSBINDIR)/META-INF/services

FILES_copy = \
	$(SERVICEDIR)/sun.java2d.pipe.RenderingEngine

build: copy-files

copy-files: $(FILES_copy)

ifneq ($(PLATFORM), windows)
 SERVICE_SRC = $(PLATFORM_SRC)
else
 SERVICE_SRC = $(SHARE_SRC)
endif

$(SERVICEDIR)/%: $(SERVICE_SRC)/classes/sun/java2d/pisces/META-INF/services/%
	$(install-file)
