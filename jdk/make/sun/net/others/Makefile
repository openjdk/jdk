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

BUILDDIR = ../../..
MODULE  = base
PACKAGE = sun.net
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
include $(BUILDDIR)/sun/net/FILES_java.gmk

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk

build: properties nameprep-profile

clean clobber:: properties.clean nameprep-profile.clean

#
# Install/nuke properties.
#
PROPS = $(PLATFORM_SRC)/lib/content-types.properties

$(LIBDIR)/content-types.properties: $(PROPS)
	$(install-file)

properties: $(LIBDIR)/content-types.properties

properties.clean:
	$(RM) -r $(LIBDIR)/content-types.properties

#
# Install/nuke nameprep profile.
#
NAMEPREP_PROFILE_SRC   = $(TOPDIR)/src/share/classes/sun/net/idn/uidna.spp
NAMEPREP_PROFILE_BUILD = $(CLASSBINDIR)/sun/net/idn/uidna.spp

nameprep-profile: $(NAMEPREP_PROFILE_BUILD)

nameprep-profile.clean:
	$(RM) -r $(NAMEPREP_PROFILE_BUILD)

$(NAMEPREP_PROFILE_BUILD): $(NAMEPREP_PROFILE_SRC)
	$(install-file)

$(NAMEPREP_PROFILE_SRC):

.PHONY: properties properties.clean nameprep-profile nameprep-profile.clean

