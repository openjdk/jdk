#
# Copyright 1999-2009 Sun Microsystems, Inc.  All Rights Reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Sun designates this
# particular file as subject to the "Classpath" exception as provided
# by Sun in the LICENSE file that accompanied this code.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#

#
# Makefile for building all of java
#

BUILDDIR = ../../..
PACKAGE = com.sun.tools.corba.se.idl
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

# This program must contain a manifest that defines the execution level
# needed to follow standard Vista User Access Control Guidelines
# This must be set before Program.gmk is included
#
BUILD_MANIFEST=true

#
# Files
#
include FILES_java.gmk

#
# Resources
#
LOCALE_SET_DEFINITION = jdk
RESOURCE_BUNDLES_PROPERTIES = \
	 $(PKGDIR)/idl.prp \
	 $(PKGDIR)/toJavaPortable/toJavaPortable.prp

#
# IDL files
#

IDL_FILES = orb.idl ir.idl

IDL_SRC_DIR  = $(SHARE_SRC)/classes/com/sun/tools/corba/se/idl
IDL_DEST_DIR = $(LIBDIR)

IFILES = $(IDL_FILES:%=$(IDL_DEST_DIR)/%)

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk

build: $(IFILES)

$(IDL_DEST_DIR)/%: $(IDL_SRC_DIR)/%
	$(install-file)

