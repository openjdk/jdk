#
# Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building jobjc

BUILDDIR = ../..
include $(BUILDDIR)/common/Defs.gmk
SRCDIR = $(JDK_TOPDIR)/src/macosx/native/jobjc

ifeq ($(PLATFORM),macosx)

# FRAMEWORKS for which we want to build bridge support
FRAMEWORKS = Foundation CoreFoundation AppKit

# metadata stuff
GEN_DIR = $(OUTPUTDIR)/bridge_metadata
STABLE_GEN_DIR = $(OUTPUTDIR)/stable_bridge_metadata
STABLE_METADATA_FILES = $(addsuffix Full.bridgesupport,$(addprefix $(STABLE_GEN_DIR)/,$(FRAMEWORKS)))

# source files
CORE_SRC = $(shell $(FIND) $(SRCDIR) -type f -name "*.hs" -or -name "*.java" -or -name "*.m" -or -name "*.h" -print)
GENERATOR_SRC = $(shell $(FIND) $(SRCDIR) -type f -name "*.java" -print)
ADDITIONS_SRC = $(shell $(FIND) $(SRCDIR) -type f -name "*.java" -or -name "*.m" -or -name "*.h" -print)
BUILD_SRC = $(SRCDIR)/JObjC.xcodeproj/project.pbxproj $(SRCDIR)/bridgesupport.gmk $(SRCDIR)/build.xml $(SRCDIR)/extract_classes.pl $(SRCDIR)/run-and-write-if-okay $(SRCDIR)/rungen $(SRCDIR)/runjava

# jobjc products for jdk
BUILT_DYLIB = $(OUTPUTDIR)/JObjC.dst/Debug/libJObjC.dylib
BUILT_JAR = $(OUTPUTDIR)/JObjC.build/JObjC.jar

$(GEN_DIR):
	mkdir -p $(GEN_DIR)

stabilize: $(GEN_DIR)
	@echo Updating bridge support in $(GEN_DIR)
	($(CD) $(GEN_DIR); $(MAKE) STABLE_GEN_DIR="$(STABLE_GEN_DIR)" FRAMEWORKS="$(FRAMEWORKS)" -f $(SRCDIR)/bridgesupport.gmk all)

$(STABLE_METADATA_FILES): stabilize

ABS_OUTPUTDIR=$(realpath $(OUTPUTDIR))
ABS_STABLE_GEN_DIR=$(realpath $(STABLE_GEN_DIR))

$(BUILT_DYLIB) $(BUILT_JAR): $(STABLE_METADATA_FILES) $(CORE_SRC) $(GENERATOR_SRC) $(ADDITIONS_SRC) $(BUILD_SRC)
	@echo JObjC dylib or jar out of data wrt FRAMEWORKS '(' $(FRAMEWORKS) ')' or JObjC source '(' core, generator, additions, build ')'
	@echo Running ant with java_home set to ${ALT_BOOTDIR}
	(cd $(SRCDIR); OBJROOT="$(ABS_OUTPUTDIR)/JObjC.build" DSTROOT="$(ABS_OUTPUTDIR)/JObjC.dst" JAVA_HOME=${ALT_BOOTDIR} STABLE_GEN_DIR="$(ABS_STABLE_GEN_DIR)" /usr/bin/ant -verbose all)

all: $(BUILD_DYLIB) $(BUILT_JAR)
	$(CP) $(BUILT_DYLIB) $(LIB_LOCATION)/libJObjC.dylib

clean clobber::
	(cd $(SRCDIR); export OBJROOT=$(OUTPUTDIR)/JObjC.build; export DSTROOT=$(OUTPUTDIR)/JObjC.dst; /usr/bin/ant clean)

endif
