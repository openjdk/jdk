#
# Copyright (c) 2000, 2005, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building all of JPS (Java Print Service) API
#

BUILDDIR = ../..
MODULE  = print
PACKAGE = javax.print
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

#
# Files to compile
#
AUTO_FILES_JAVA_DIRS = javax/print sun/print

#
# Files that just need cp.
#
SERVICEDIR = $(CLASSBINDIR)/META-INF/services

FILES_copy = \
	$(SERVICEDIR)/javax.print.StreamPrintServiceFactory \
	$(SERVICEDIR)/javax.print.PrintServiceLookup

FILES_mkdirs = \
	$(CLASSBINDIR)/META-INF \
	$(CLASSBINDIR)/META-INF/services

FILES_copydirs = \
	$(FILES_mkdirs)

#
# Resources
#
LOCALE_SET_DEFINITION = jre
RESOURCE_BUNDLES_COMPILED_PROPERTIES = sun/print/resources/serviceui.properties

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk

#
# Rules that copy files.
#

build: copy-files properties

_PSFONTPROPS = \
	psfontj2d.properties            \
	psfont.properties.ja            \

PSFONTPROPS_SRC = $(SHARE_SRC)/classes/sun/print
PSFONTPROPS       = $(_PSFONTPROPS:%=$(LIBDIR)/%)

_RESOURCES_PRINT = \
        orientLandscape.png                \
        orientPortrait.png                \
        orientRevLandscape.png                \
        orientRevPortrait.png                \
        oneside.png                        \
        duplex.png                        \
        tumble.png


RESDIR_PRINT    = $(CLASSBINDIR)/sun/print/resources
RESOURCES_PRINT = $(_RESOURCES_PRINT:%=$(RESDIR_PRINT)/%)

$(LIBDIR)/%: $(PSFONTPROPS_SRC)/% $(FONTPROPS)
	$(install-file)

$(RESDIR_PRINT)/%: $(SHARE_SRC)/classes/sun/print/resources/%
	$(install-file)

properties: $(LIBDIR) $(PSFONTPROPS) $(RESOURCES_PRINT)

properties.clean:
	$(RM) $(PSFONTPROPS)
	$(RM) -r $(RESDIR_PRINT)

copy-files: $(FILES_copy)

$(SERVICEDIR)/%: $(PLATFORM_SRC)/classes/sun/print/services/%
	$(install-file)

#
# Clean up internal-use-only package.
#
clean::
	$(RM) -r $(CLASSDESTDIR)/javax/print $(CLASSDESTDIR)/sun/print

.PHONY: copy-files
