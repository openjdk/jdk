#
# Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
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

# Utility Makefile that creates wb.jar, which is used to simplify using 
# the Whitebox API outside the jtreg-testbase.

ifneq "x$(ALT_BOOTDIR)" "x"
	BOOTDIR := $(ALT_BOOTDIR)
endif

ifeq "x$(BOOTDIR)" "x"
	JDK_HOME := $(shell dirname $(shell which java))/..
else
	JDK_HOME := $(BOOTDIR)
endif

SRC_DIR = ./
BUILD_DIR = build
OUTPUT_DIR = $(BUILD_DIR)/classes

JAVAC = $(JDK_HOME)/bin/javac
JAR = $(JDK_HOME)/bin/jar

WB_SRC_FILES = $(shell find $(SRC_DIR)/sun/hotspot -name '*.java')
# test-lib.jar will contain only hprof classes until JDK-8081381 is resolved
SHARE_SRC_FILES = $(shell find $(SRC_DIR)/share/classes/jdk/test/lib/hprof -name '*.java')

.PHONY: wb.filelist share.filelist clean cleantmp

all: wb.jar test-lib.jar cleantmp

wb.jar: wb.filelist
	@mkdir -p $(OUTPUT_DIR)
	$(JAVAC) -sourcepath $(SRC_DIR) -d $(OUTPUT_DIR) -cp $(OUTPUT_DIR) @wb.filelist
	$(JAR) cf wb.jar -C $(OUTPUT_DIR) .
	@rm -rf $(OUTPUT_DIR)

wb.filelist: $(WB_SRC_FILES)
	@rm -f $@
	@echo $(WB_SRC_FILES) > $@

test-lib.jar: share.filelist
	@mkdir -p $(OUTPUT_DIR)
	$(JAVAC) -sourcepath $(SRC_DIR) -d $(OUTPUT_DIR) -cp $(OUTPUT_DIR) @share.filelist
	$(JAR) cf test-lib.jar -C $(OUTPUT_DIR) .
	@rm -rf $(OUTPUT_DIR)

share.filelist: $(SHARE_SRC_FILES)
	@rm -f $@
	@echo $(SHARE_SRC_FILES) > $@

clean: cleantmp
	@rm -rf wb.jar
	@rm -rf test-list.jar

cleantmp:
	@rm -rf wb.filelist
	@rm -rf share.filelist
	@rm -rf $(BUILD_DIR)
