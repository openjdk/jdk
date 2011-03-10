#
# Copyright (c) 1995, 2010, Oracle and/or its affiliates. All rights reserved.
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
PACKAGE = java.awt
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk


#
# Files
#
AUTO_FILES_JAVA_DIRS = java/awt sun/awt/geom

build: properties cursors

#
# Resources
#
RESOURCE_BUNDLES_COMPILED_PROPERTIES = sun/awt/resources/awt.properties
LOCALE_SET_DEFINITION = jre

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk


clean:: properties.clean sun.geom.clean cursors.clean

_LIBPROPS = \
	flavormap.properties

LIBPROPS = $(_LIBPROPS:%=$(LIBDIR)/%)

properties: $(LIBDIR) $(LIBPROPS)

$(LIBDIR)/%.properties: $(PLATFORM_SRC)/lib/%.properties
	$(install-file)

properties.clean :
	$(RM) $(LIBPROPS)

.SUFFIXES : $(SUFFIXES) .gif .properties

CURSORSRC     = $(TOPDIR)/src/share/lib/images/cursors
CURSORDIR     = $(LIBDIR)/images/cursors

ifeq ($(PLATFORM), windows)
CURSORPROPSRC = $(TOPDIR)/src/windows/lib/images/cursors
CURSORS   = $(CURSORDIR)/win32_CopyDrop32x32.gif      \
            $(CURSORDIR)/win32_MoveDrop32x32.gif      \
            $(CURSORDIR)/win32_LinkDrop32x32.gif      \
            $(CURSORDIR)/win32_CopyNoDrop32x32.gif    \
            $(CURSORDIR)/win32_MoveNoDrop32x32.gif    \
            $(CURSORDIR)/win32_LinkNoDrop32x32.gif    \
            $(CURSORDIR)/invalid32x32.gif
else # PLATFORM
CURSORPROPSRC = $(TOPDIR)/src/solaris/lib/images/cursors
CURSORS   = $(CURSORDIR)/motif_CopyDrop32x32.gif	\
	    $(CURSORDIR)/motif_MoveDrop32x32.gif	\
	    $(CURSORDIR)/motif_LinkDrop32x32.gif	\
	    $(CURSORDIR)/motif_CopyNoDrop32x32.gif	\
	    $(CURSORDIR)/motif_MoveNoDrop32x32.gif	\
	    $(CURSORDIR)/motif_LinkNoDrop32x32.gif	\
	    $(CURSORDIR)/invalid32x32.gif
endif # PLATFORM

$(CURSORDIR)/cursors.properties : $(CURSORPROPSRC)/cursors.properties
	$(install-file)

$(CURSORDIR)/%.gif : $(CURSORSRC)/%.gif
	$(install-file)

cursors : $(CURSORDIR)/cursors.properties $(CURSORS)

sun.geom.clean :
	$(RM) -r $(CLASSBINDIR)/sun/awt/geom

cursors.clean :
	$(RM) -r $(CURSORDIR)

.PHONY: properties properties.clean \
	cursors sun.geom.clean cursors.clean

