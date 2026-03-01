#
# Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

PKGLIST = jdk.tools.codecache

# JDK source path (for WhiteBox)
JDK_SOURCE_PATH := $(shell cd ../../.. && pwd)

WHITEBOX_DIR := $(JDK_SOURCE_PATH)/test/lib/jdk/test/whitebox

SRC_DIR    = src
BUILD_DIR  = build
OUTPUT_DIR = $(BUILD_DIR)/classes

FILELIST = \
$(SRC_DIR)/main/java/jdk/tools/codecache/CodeCacheFragAgent.java \
$(WHITEBOX_DIR)/WhiteBox.java \
$(WHITEBOX_DIR)/parser/DiagnosticCommand.java \
$(WHITEBOX_DIR)/code/*.java

# Boot JDK
ifneq "x$(ALT_BOOTDIR)" "x"
  BOOTDIR := $(ALT_BOOTDIR)
endif

ifeq "x$(BOOTDIR)" "x"
  JDK_HOME := $(shell dirname $(shell which java))/..
else
  JDK_HOME := $(BOOTDIR)
endif

JAVAC = $(JDK_HOME)/bin/javac
JAR   = $(JDK_HOME)/bin/jar

all: codecachefragmenter.jar

codecachefragmenter.jar: manifest.mf
	@mkdir -p $(OUTPUT_DIR)
	$(JAVAC) -sourcepath $(SRC_DIR) -d $(OUTPUT_DIR) $(FILELIST)
	$(JAR) cvfm $@ manifest.mf -C $(OUTPUT_DIR) jdk

clean:
	rm -rf codecachefragmenter.jar
	rm -rf $(BUILD_DIR)
