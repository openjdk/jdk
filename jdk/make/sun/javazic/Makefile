#
# Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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

# Makefile for building javazic utility & TimeZone resource files


BUILDDIR = ../..
PACKAGE = sun.javazic
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

# Time zone data file creation
TZDATA = ./tzdata/
TZDATA_VER = `$(GREP) '^tzdata' $(TZDATA)VERSION`
TZFILE = \
    africa antarctica asia australasia europe northamerica \
    pacificnew southamerica backward \
    etcetera solar87 solar88 solar89 systemv
JDKTZDATA = ./tzdata_jdk/
JDKTZFILES = gmt jdk11_backward
TZFILES = \
    $(addprefix $(TZDATA),$(TZFILE)) \
    $(addprefix $(JDKTZDATA),$(JDKTZFILES))

WORKDIR = $(TEMPDIR)/zi
INSTALLDIR = $(LIBDIR)/zi
MAPFILE = ZoneInfoMappings

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk

# Add to the build rule
build: $(INSTALLDIR)/$(MAPFILE)

$(BUILDTOOLJARDIR)/javazic.jar:
	($(CD) $(BUILDDIR)/tools/javazic && $(MAKE))

$(WORKDIR)/$(MAPFILE): $(BUILDTOOLJARDIR)/javazic.jar $(TZFILES)
	$(RM) -r $(@D)
	$(prep-target)
	$(BOOT_JAVA_CMD) -jar $(BUILDTOOLJARDIR)/javazic.jar -V "$(TZDATA_VER)" -d $(WORKDIR) $(TZFILES)
	@$(java-vm-cleanup)

$(INSTALLDIR)/$(MAPFILE): $(WORKDIR)/$(MAPFILE)
	$(RM) -r $(@D)
	$(prep-target)
	$(CP) -r $(WORKDIR)/* $(@D)

clean clobber::
	$(RM) -r $(TEMPDIR) $(INSTALLDIR)
