#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building AccessBridge
#
BUILDDIR = ../..
PRODUCT = java
PACKAGE = com.sun.java.accessibility

include $(BUILDDIR)/common/Defs.gmk
JARFILE = $(EXTDIR)/access-bridge$(ABSUFFIX).jar

ifeq ($(ARCH_DATA_MODEL), 64)
  ABPLATFORM = 64bit
  ABSUFFIX = -64
else
ifeq ($(ARCH_DATA_MODEL), 32)
ifdef ABLEGACY
  ABSUFFIX = 
  ABPLATFORM = legacy
else
  ABPLATFORM = 32bit
  ABSUFFIX = -32
endif
endif
endif

#
# Java files to compile.
#
FILES_java = com/sun/java/accessibility/AccessBridge.java

#
# Location for the newly built classfiles.
#
CLASSDESTDIR = $(TEMPDIR)/classes

#
# Rules
#
CLASSDESTDIR = $(TEMPDIR)/classes

FILES_class = $(FILES_java:%.java=$(CLASSDESTDIR)/%.class)

build: prebuild

prebuild:
	$(CP) $(CLOSED_PLATFORM_SRC)/classes/com/sun/java/accessibility/$(ABPLATFORM)/AccessBridge.java \
		$(CLOSED_PLATFORM_SRC)/classes/com/sun/java/accessibility

all : build $(JARFILE) 

#
# JAR file
#
$(JARFILE): \
	$(FILES_class)
	$(BOOT_JAR_CMD) -cf $(JARFILE) \
	     -C $(CLASSDESTDIR) com \
	     $(BOOT_JAR_JFLAGS)
	@$(java-vm-cleanup)

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk

clean clobber::
	$(RM) -r $(CLASSDESTDIR) \
		 $(EXTDIR)/$(JARFILE)
