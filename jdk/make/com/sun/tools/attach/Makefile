#
# Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
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


BUILDDIR = ../../../..
PACKAGE = com.sun.tools.attach
LIBRARY = attach
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

ifeq ($(PLATFORM), solaris)
FILES_m = mapfile-solaris
endif
ifeq ($(PLATFORM), linux)
FILES_m = mapfile-linux
endif
include $(BUILDDIR)/common/Mapfile-vers.gmk

#
# Files to compile
#
include FILES_c.gmk
include FILES_java.gmk
include Exportedfiles.gmk

ifeq ($(PLATFORM), solaris)
OTHER_LDLIBS += -ldoor
endif
ifeq ($(PLATFORM), windows)
EXTRA_LIBS += psapi.lib
endif

vpath %.c $(PLATFORM_SRC)/native/sun/tools/attach

all: classes copy-files

#
# Copy the service provider configuration files into the resource
# directory. Copy the configuration file also uncomments any
# providers that need to be enabled for this platform.
#
                                                                                                
copy-files: $(FILES_copy)
                                                                                                
$(SERVICEDIR)/%: $(SHARE_SRC)/classes/sun/tools/attach/META-INF/services/%
	@$(MKDIR) -p $(@D)
	@$(RM) $@
	@$(CAT) $< | $(SED) -e "s/^#\[$(PLATFORM)\]//" > $@

.PHONY: copy-files


clean::
	$(RM) -r $(CLASSDESTDIR)/com/sun/tools/attach
	$(RM) -r $(CLASSDESTDIR)/sun/tools/attach
	$(RM) $(FILES_copy)

#
# Library to compile.
#
include $(BUILDDIR)/common/Library.gmk






