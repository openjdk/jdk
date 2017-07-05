#
# Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

ifeq "x$(BOOTDIR)" "x"
	JDK_HOME := $(shell dirname $(shell which java))/..
else
	JDK_HOME := $(BOOTDIR)
endif

ifeq "x$(PROPERTY_FILE)" "x"
	PROPERTY_FILE := conf/default.properties
endif

ifeq "x$(TESTBASE_DIR)" "x"
	TESTBASE_DIR := ws/hotspot/test
endif

JAVA = $(JDK_HOME)/bin/java
JAVAC = $(JDK_HOME)/bin/javac
JAR = $(JDK_HOME)/bin/jar

BUILD_DIR = build
CLASSES_DIR = $(BUILD_DIR)/classes
SRC_DIR = src
TEST_DIR = test
MANIFEST = manifest.mf
APPLICATION_ARGS = \
    --property-file $(PROPERTY_FILE) \
    --testbase-dir $(TESTBASE_DIR)
MAIN_CLASS = JitTestGenerator.Automatic

TESTGROUP_FILE = $(TESTBASE_DIR)/TEST.groups
TESTROOT_FILE =  $(TESTBASE_DIR)/TEST.ROOT

DIST_DIR = dist
DIST_JAR = $(DIST_DIR)/JITtester.jar

SRC_FILES = $(shell find $(SRC_DIR) -name '*.java')
TESTLIBRARY_SRC_DIR = ../jdk/test/lib
TESTLIBRARY_SRC_FILES = $(TESTLIBRARY_SRC_DIR)/Asserts.java \
                        $(TESTLIBRARY_SRC_DIR)/JDKToolFinder.java \
                        $(TESTLIBRARY_SRC_DIR)/JDKToolLauncher.java \
                        $(TESTLIBRARY_SRC_DIR)/OutputAnalyzer.java \
                        $(TESTLIBRARY_SRC_DIR)/OutputBuffer.java \
                        $(TESTLIBRARY_SRC_DIR)/Pair.java \
                        $(TESTLIBRARY_SRC_DIR)/Platform.java \
                        $(TESTLIBRARY_SRC_DIR)/ProcessTools.java \
                        $(TESTLIBRARY_SRC_DIR)/StreamPumper.java \
                        $(TESTLIBRARY_SRC_DIR)/Utils.java

.PHONY: cleantmp

all: JAR

JAR: INIT COMPILE manifest
	$(JAR) cfm $(DIST_JAR) $(MANIFEST) -C $(CLASSES_DIR) .

manifest:
	@echo 'Manifest-Version: 1.0' > $(MANIFEST)
	@echo 'X-COMMENT: Main-Class will be added automatically by build' >> $(MANIFEST)
	@echo 'Main-Class: jdk.test.lib.jittester.Automatic' >> $(MANIFEST)

compile_testlib: INIT
	$(JAVAC) -XDignore.symbol.file -Xlint $(TESTLIBRARY_SRC_FILES) -d $(CLASSES_DIR) -source 1.8

COMPILE: INIT filelist compile_testlib
	$(JAVAC) -cp $(CLASSES_DIR) -XDignore.symbol.file -Xlint -sourcepath $(SRC_DIR) -d $(CLASSES_DIR) -source 1.8 @filelist

filelist: $(SRC_FILES)
		@rm -f $@
		@echo $(SRC_FILES) > $@

INIT: $(DIST_DIR)
	$(shell if [ ! -d $(CLASSES_DIR) ]; then mkdir -p $(CLASSES_DIR); fi)

install: clean_testbase testgroup testroot copytestlibrary JAR cleantmp
	$(JAVA) -jar $(DIST_JAR) $(APPLICATION_ARGS)

clean_testbase:
	@rm -rf $(TESTBASE_DIR)

cleantmp:
	@rm filelist
	@rm -rf $(CLASSES_DIR)

copytestlibrary:
	@cp -r src/jdk/test/lib/jittester/jtreg $(TESTBASE_DIR)/
	@cp -r ../jdk $(TESTBASE_DIR)/

testgroup: $(TESTBASE_DIR)
	@echo 'jittester_all = \\' > $(TESTGROUP_FILE)
	@echo '	/' >> $(TESTGROUP_FILE)
	@echo '' >> $(TESTGROUP_FILE)
	@echo 'main = \\' >> $(TESTGROUP_FILE)
	@echo '	Test_0.java' >> $(TESTGROUP_FILE)

testroot: $(TESTBASE_DIR)
	@echo 'groups=TEST.groups' > $(TESTROOT_FILE)

$(TESTBASE_DIR):
	$(shell if [ ! -d $@ ]; then mkdir -p $@; fi)

$(DIST_DIR):
	$(shell if [ ! -d $@ ]; then mkdir -p $@; fi)

