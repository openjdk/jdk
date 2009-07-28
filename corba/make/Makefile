#
# Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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

#
# Makefile for building the corba workspace.
#

BUILDDIR=.
include $(BUILDDIR)/common/Defs.gmk
include $(BUILDDIR)/common/CancelImplicits.gmk

#----- commands

CHMOD = chmod
CP = cp
ECHO = echo # FIXME
FIND = find
MKDIR = mkdir
SED = sed
ZIP = zip

#-----  locations and deliverables

TOPDIR=..
SRC_BIN_DIR = $(TOPDIR)/src/share/bin
SRC_CLASSES_DIR = $(TOPDIR)/src/share/classes

ifdef ALT_OUTPUTDIR
  OUTPUTDIR = $(ALT_OUTPUTDIR)
  BUILD_DIR = $(ALT_OUTPUTDIR)
  DIST_DIR = $(ALT_OUTPUTDIR)/dist
else
  OUTPUTDIR = ..
  BUILD_DIR = $(TOPDIR)/build/$(PLATFORM)-$(ARCH)
  DIST_DIR = $(TOPDIR)/dist
endif
ABS_OUTPUTDIR = $(call FullPath,$(OUTPUTDIR))

CLASSES_DIR = $(BUILD_DIR)/classes
GENSRC_DIR = $(BUILD_DIR)/gensrc

BIN_DIR = $(DIST_DIR)/bin
LIB_DIR = $(DIST_DIR)/lib

#-----

ifndef JDK_MAJOR_VERSION
  JDK_MAJOR_VERSION = 1
endif

ifndef JDK_MINOR_VERSION
  JDK_MINOR_VERSION = 7
endif

ifndef JDK_MICRO_VERSION
  JDK_MICRO_VERSION = 0
endif

ifndef JDK_VERSION
  JDK_VERSION = $(JDK_MAJOR_VERSION).$(JDK_MINOR_VERSION).$(JDK_MICRO_VERSION)
endif

ifndef MILESTONE
  MILESTONE = internal
endif

# RELEASE is JDK_VERSION and -MILESTONE if MILESTONE is set
ifneq ($(MILESTONE),fcs)
  RELEASE       = $(JDK_VERSION)-$(MILESTONE)$(BUILD_VARIANT_RELEASE)
else
  RELEASE       = $(JDK_VERSION)$(BUILD_VARIANT_RELEASE)
endif

# FULL_VERSION is RELEASE and -BUILD_NUMBER if BUILD_NUMBER is set
ifdef BUILD_NUMBER
  FULL_VERSION = $(RELEASE)-$(BUILD_NUMBER)
else
  BUILD_NUMBER = b00
  USER_RELEASE_SUFFIX := $(shell echo $(USER)_`date '+%d_%b_%Y_%H_%M' | tr "A-Z" "a-z"`)
  FULL_VERSION = $(RELEASE)-$(USER_RELEASE_SUFFIX)-$(BUILD_NUMBER)
endif

#----- useful macros

SOURCE_LEVEL = 5
BOOTSTRAP_TARGET_LEVEL = 5

ifdef TARGET_CLASS_VERSION
    TARGET_LEVEL = $(TARGET_CLASS_VERSION)
else
    TARGET_LEVEL = 7
endif

ifndef TARGET_JAVA
  TARGET_JAVA = java
endif

SELF = $(lastword $(MAKEFILE_LIST))

# for jdk, we generate the following:
# dist/lib/classes.jar:
#       corba recompiled to run on the target JDK,
#       ready for inclusion in rt.jar
# dist/lib/src.zip
#       .properties and .java files for classes in classes.jar,
#       ready for jdk src.zip
# dist/lib/bin.zip
#       corba specific binaries: orb.idl, ir.idl

# Default target
default: all

#----- classes.jar

CLASSES_JAR = $(LIB_DIR)/classes.jar
$(CLASSES_JAR):
	$(MKDIR) -p $(@D)
	$(BOOT_JAR_CMD) -cf $@ -C $(CLASSES_DIR) .

#----- src.zip

SRC_ZIP_FILES = $(shell $(FIND) $(SRC_CLASSES_DIR) \( -name SCCS -o -name \*-template \) -prune -o -type f -print )

SRC_ZIP = $(LIB_DIR)/src.zip
$(SRC_ZIP): $(SRC_ZIP_FILES)
	abs_src_zip=`cd $(@D) ; pwd`/$(@F) ; \
	( cd $(SRC_CLASSES_DIR) ; $(FIND) . \( -name SCCS -o -name \*-template \) -prune -o -type f -print | $(ZIP) -q $$abs_src_zip -@ ) ; \
	( cd $(GENSRC_DIR) ; $(FIND) . -type f -print | $(ZIP) -q $$abs_src_zip -@ ) ;

#----- bin.zip

BIN_ZIP_FILES = $(BUILD_DIR/lib/orb.idl $(BUILD_DIR)/lib/ir.idl

BIN_ZIP = $(LIB_DIR)/bin.zip
$(BIN_ZIP): $(BIN_ZIP_FILES)
	abs_bin_zip=`cd $(@D) ; pwd`/$(@F) ; \
	( cd $(BUILD_DIR) ; $(FIND) lib -name "*.idl" -print | $(ZIP) -q $$abs_bin_zip -@ ) ;

#
# CORBA
#
SUBDIRS = tools javax org sun com

build:
	$(SUBDIRS-loop)

all : build $(CLASSES_JAR) $(SRC_ZIP) $(BIN_ZIP)

clean clobber:
	$(RM) -r $(BUILD_DIR)
	$(RM) -r $(DIST_DIR)

#-------------------------------------------------------------------
#
# Targets for Sun's internal JPRT build system

CD = cd
ZIP = zip

JPRT_ARCHIVE_BUNDLE=$(ABS_OUTPUTDIR)/$(JPRT_BUILD_FLAVOR)-bundle.zip

jprt_build_product jprt_build_debug jprt_build_fastdebug: all
	( $(CD) $(OUTPUTDIR) && \
	  $(ZIP) -q -r $(JPRT_ARCHIVE_BUNDLE) build dist )

#-------------------------------------------------------------------


#
# Phonies to avoid accidents.
#
.PHONY: all build clean clobber debug jprt_build_product jprt_build_debug jprt_build_fastdebug
