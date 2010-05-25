#
# Copyright (c) 2006, 2008, Oracle and/or its affiliates. All rights reserved.
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
MODULE   = java2d
PRODUCT  = sun
include $(BUILDDIR)/common/Defs.gmk

ifdef OPENJDK
    SUBDIRS += lcms
    ICCPROFILE_SRC_DIR = $(SHARE_SRC)/lib/cmm/lcms
else # !OPENJDK
    SUBDIRS += kcms
    ICCPROFILE_SRC_DIR = $(CLOSED_SRC)/share/lib/cmm/kcms
endif # OPENJDK

include $(BUILDDIR)/common/Subdirs.gmk

#
# ICC Profiles used by the CMM.
#
ICCPROFILE_DEST_DIR = $(LIBDIR)/cmm

iccprofiles: $(ICCPROFILE_DEST_DIR)/sRGB.pf $(ICCPROFILE_DEST_DIR)/GRAY.pf \
	     $(ICCPROFILE_DEST_DIR)/CIEXYZ.pf $(ICCPROFILE_DEST_DIR)/PYCC.pf \
	     $(ICCPROFILE_DEST_DIR)/LINEAR_RGB.pf

$(ICCPROFILE_DEST_DIR)/sRGB.pf: $(ICCPROFILE_SRC_DIR)/sRGB.pf
	$(install-file)
	$(call chmod-file, 444)

$(ICCPROFILE_DEST_DIR)/GRAY.pf: $(ICCPROFILE_SRC_DIR)/GRAY.pf
	$(install-file)
	$(call chmod-file, 444)

$(ICCPROFILE_DEST_DIR)/CIEXYZ.pf: $(ICCPROFILE_SRC_DIR)/CIEXYZ.pf
	$(install-file)
	$(call chmod-file, 444)

$(ICCPROFILE_DEST_DIR)/PYCC.pf: $(ICCPROFILE_SRC_DIR)/PYCC.pf
	$(install-file)
	$(call chmod-file, 444)

$(ICCPROFILE_DEST_DIR)/LINEAR_RGB.pf: $(ICCPROFILE_SRC_DIR)/LINEAR_RGB.pf
	$(install-file)
	$(call chmod-file, 444)

iccprofiles.clean:
	$(RM) -r $(ICCPROFILE_DEST_DIR)


clobber: iccprofiles.clean
	$(SUBDIRS-loop)

clean: iccprofiles.clean
	$(SUBDIRS-loop)

all build:: iccprofiles
	$(SUBDIRS-loop)

openjdk::
	$(MAKE) OPENJDK=true build

.PHONY: iccprofiles iccprofiles.clean copy-files openjdk
