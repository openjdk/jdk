#
# Copyright 2000-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

BUILDDIR = ../..
PACKAGE = java.util.logging
PRODUCT = java
include $(BUILDDIR)/common/Defs.gmk

#
# Files to compile.
#
AUTO_FILES_JAVA_DIRS = java/util/logging sun/util/logging

#
# Resources
#
LOCALE_SET_DEFINITION = jre
RESOURCE_BUNDLES_COMPILED_PROPERTIES = \
	 sun/util/logging/resources/logging.properties

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk

properties: $(LIBDIR)/logging.properties
$(LIBDIR)/logging.properties: $(SHARE_SRC)/lib/logging.properties
	$(install-file)

build: properties

