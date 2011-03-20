#
# Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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
PACKAGE = java.lang.management
LIBRARY = management
PRODUCT = java
include $(BUILDDIR)/common/Defs.gmk

MGMT_SRC  = $(SHARE_SRC)/classes/java/lang/management
SMGMT_SRC = $(SHARE_SRC)/classes/sun/management

AGENTJAR = $(LIBDIR)/management-agent.jar
MANIFEST = $(SMGMT_SRC)/manifest

#
# Use mapfile
#
FILES_m = mapfile-vers
include $(BUILDDIR)/common/Mapfile-vers.gmk

#
# Files to compile
#
include FILES_c.gmk

# We don't need snmp here.
AUTO_JAVA_PRUNE = snmp
AUTO_FILES_JAVA_DIRS = java/lang/management com/sun/management sun/management

include Exportedfiles.gmk

ifeq ($(PLATFORM),windows)

FILES_c     +=  OperatingSystem_md.c

FILES_export +=	com/sun/management/OperatingSystem.java

else # PLATFORM (i.e. solaris & linux)

FILES_c     +=  UnixOperatingSystem_md.c

FILES_export +=	com/sun/management/UnixOperatingSystem.java
endif # PLATFORM

#
# Resources
#
LOCALE_SET_DEFINITION = jre
RESOURCE_BUNDLES_COMPILED_PROPERTIES = sun/management/resources/agent.properties

#
# Find C source files
#
vpath %.c $(SHARE_SRC)/native/sun/management
vpath %.c $(PLATFORM_SRC)/native/sun/management
vpath %.c $(PLATFORM_SRC)/native/com/sun/management

#
# Access to management.h 
#

OTHER_INCLUDES += \
  -I$(SHARE_SRC)/native/sun/management 

ifeq ($(PLATFORM),windows)
  OTHER_LDLIBS += $(JVMLIB)
endif

#
# Library to compile.
#
include $(BUILDDIR)/common/Library.gmk

$(AGENTJAR): $(LIBDIR) $(TEMPDIR)/manifest
	$(BOOT_JAR_CMD) -cfm $(AGENTJAR) $(TEMPDIR)/manifest $(BOOT_JAR_JFLAGS)
	@$(java-vm-cleanup)

$(TEMPDIR)/manifest: $(MANIFEST)
	$(install-manifest-file)

build: $(AGENTJAR)

clean clobber::
	$(RM) -r $(CLASSDESTDIR)/java/management
	$(RM) -r $(CLASSDESTDIR)/sun/management
	$(RM) $(TEMPDIR)/manifest $(AGENTJAR)

