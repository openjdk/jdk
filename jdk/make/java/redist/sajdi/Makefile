#
# Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

BUILDDIR = ../../..
PRODUCT  = java
include $(BUILDDIR)/common/Defs.gmk

ifeq ($(PLATFORM), windows)
  LIB_LOCATION = $(BINDIR)
else ifeq ($(PLATFORM), macosx)
  LIB_LOCATION = $(LIBDIR)
else
  LIB_LOCATION = $(LIBDIR)/$(LIBARCH)
endif

# INCLUDE_SA is false on platforms where SA is not supported.
# On platforms where it is supported, we want to allow it to
# not be present, at least temporarily.  So,
# if the SA files (well, just sa-jdi.jar) do not exist
# in the HOTSPOT_IMPORT_PATH, then we won't build SA.
SA_EXISTS := $(shell if [ -r $(HOTSPOT_IMPORT_PATH)/lib/sa-jdi.jar ] ; then \
                          $(ECHO) true; \
                        else \
                          $(ECHO) false; \
                        fi)

ifeq ($(SA_EXISTS), false)
  INCLUDE_SA := false
endif

IMPORT_LIST =
ifeq ($(INCLUDE_SA), true)
  IMPORT_LIST += $(LIBDIR)/sa-jdi.jar \
                 $(LIB_LOCATION)/$(SALIB_NAME)
  ifeq ($(PLATFORM), windows)
    IMPORT_LIST += $(LIB_LOCATION)/$(SAMAP_NAME) \
                   $(LIB_LOCATION)/$(SAPDB_NAME)
  endif
  ifneq ($(OBJCOPY),)
    # the import JDK may not contain .debuginfo files
    ifneq ($(wildcard $(HOTSPOT_IMPORT_PATH)/$(ARCH_VM_SUBDIR)/$(SA_DEBUGINFO_NAME)),)
      IMPORT_LIST += $(LIB_LOCATION)/$(SA_DEBUGINFO_NAME)
    endif
  endif
endif # INCLUDE_SA


ifeq ($(INCLUDE_SA), true)
# The Serviceability Agent is built in the Hotspot workspace.
# It contains two files:
#  - sa-jdi.jar:  This goes into the same dir as tools.jar.
#  - a shared library:  sawindbg.dll on windows / libproc.sa on unix
#		        This goes into the same dir as the other
#			shared libs, eg. libjdwp.so.
$(LIBDIR)/sa-jdi.jar: $(HOTSPOT_IMPORT_PATH)/lib/sa-jdi.jar
	$(install-importonly-file)

$(LIB_LOCATION)/$(SALIB_NAME): $(HOTSPOT_SALIB_PATH)/$(SALIB_NAME)
	$(install-import-file)

ifeq ($(PLATFORM), windows)
$(LIB_LOCATION)/$(SAPDB_NAME): $(HOTSPOT_SALIB_PATH)/$(SAPDB_NAME)
	$(install-import-file)

$(LIB_LOCATION)/$(SAMAP_NAME): $(HOTSPOT_SALIB_PATH)/$(SAMAP_NAME)
	$(install-import-file)
endif # windows

  ifneq ($(OBJCOPY),)
$(LIB_LOCATION)/$(SA_DEBUGINFO_NAME): $(HOTSPOT_SALIB_PATH)/$(SA_DEBUGINFO_NAME)
	$(install-import-file)
  endif
endif # INCLUDE_SA

all: $(IMPORT_LIST)

clean clobber::
