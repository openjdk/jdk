#
# Copyright (c) 1998, 2005, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building the JDI interface and 
# front-end implementation
#

BUILDDIR = ../..
PACKAGE = com.sun.tools.jdi
PRODUCT = jpda
include $(BUILDDIR)/common/Defs.gmk

#
#  Java Files to compile are found automatically.
#
AUTO_FILES_JAVA_DIRS = com/sun/jdi com/sun/tools/jdi

# 
# Files that need to be copied 
# 
SERVICEDIR = $(CLASSBINDIR)/META-INF/services
  
FILES_copy = \
	$(SERVICEDIR)/com.sun.jdi.connect.spi.TransportService \
	$(SERVICEDIR)/com.sun.jdi.connect.Connector

#
# Resources
#
LOCALE_SET_DEFINITION = jdk
RESOURCE_BUNDLES_COMPILED_PROPERTIES = \
      com/sun/tools/jdi/resources/jdi.properties

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk

# 
# Copy the service provider configuration files into the resource
# directory. Copy the configuration file also uncomments any
# providers that need to be enabled for this platform.
#

build: copy-files

copy-files: $(FILES_copy)

$(SERVICEDIR)/%: $(SHARE_SRC)/classes/com/sun/tools/jdi/META-INF/services/%
	$(prep-target)
	$(RM) $@.temp
	@$(CAT) $< | $(SED) -e "s/^#\[$(PLATFORM)\]//" > $@.temp
	$(MV) $@.temp $@

clean clobber::
	$(RM) $(FILES_copy)

.PHONY: copy-files

