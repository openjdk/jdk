# Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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

BUILDDIR = ../../..
PACKAGE = com.sun.servicetag
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

#
# Files to compile
#
AUTO_FILES_JAVA_DIRS = com/sun/servicetag

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk

SERVICETAG_LIBDIR = $(LIBDIR)/servicetag
SERVICETAG_RESOURCES_DIR = $(CLASSDESTDIR)/com/sun/servicetag/resources
FILES_copy = $(SERVICETAG_RESOURCES_DIR)/product_registration.xsd \
             $(SERVICETAG_RESOURCES_DIR)/register.html \
             $(SERVICETAG_RESOURCES_DIR)/register_ja.html \
             $(SERVICETAG_RESOURCES_DIR)/register_zh_CN.html \
             $(SERVICETAG_LIBDIR)/jdk_header.png

# Add all properties files to the FILES_copy list
SWORDFISH_properties := $(shell \
       $(CD) $(SHARE_SRC)/classes/com/sun/servicetag/resources; \
       $(FIND) . -name 'javase_*_swordfish.properties' -print ; \
    )
FILES_copy += $(shell \
   for f in $(SWORDFISH_properties) ; do \
       echo $(SERVICETAG_RESOURCES_DIR)/$$f ; \
   done \
)


#
#OTHER_JAVACFLAGS += -Xlint:unchecked

build: install-servicetag-lib copy-files

copy-files: $(FILES_copy)

$(CLASSBINDIR)/%: $(SHARE_SRC)/classes/%
	$(install-file)

$(SERVICETAG_LIBDIR)/jdk_header.png: $(SHARE_SRC)/classes/com/sun/servicetag/resources/jdk_header.png
	$(install-file)
	$(call chmod-file, 444)

install-servicetag-lib:
	@$(RM) -rf $(SERVICETAG_LIBDIR)
	$(MKDIR) $(SERVICETAG_LIBDIR)

clean clobber::
	@$(RM) $(FILES_copy)

.PHONY: copy-files
