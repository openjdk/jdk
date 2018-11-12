#
# Copyright (c) 1995, 2018, Oracle and/or its affiliates. All rights reserved.
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
#

NATIVE_TEST_PATH := hotspot/jtreg/native

CLEAN_BEFORE_PREP := true

USE_JTREG_VERSION := 4.1

USE_JTREG_ASSERT := false

LIMIT_JTREG_VM_MEMORY := false

IGNORE_MARKED_TESTS := true

# Include the common base file with most of the logic
include ../../TestCommon.gmk

################################################################
# Default make rule (runs jtreg_tests)
all: hotspot_all
	@$(ECHO) "Testing completed successfully"

# Support "hotspot_" prefixed test make targets (too)
# The hotspot_% targets are used by the top level Makefile
# Unless explicitly defined below, hotspot_<x> is interpreted as a jtreg test group name
hotspot_%:
	$(ECHO) "Running tests: $@"
	$(MAKE) -j 1 TEST_SELECTION=":$@" UNIQUE_DIR=$@ jtreg_tests;

################################################################

# Set up the directory in which the jvm directories live (client/, server/, etc.)
ifeq ($(PLATFORM),windows)
JVMS_DIR := $(PRODUCT_HOME)/bin
else
JVMS_DIR := $(PRODUCT_HOME)/lib
endif

# Use the existance of a directory as a sign that jvm variant is available
CANDIDATE_JVM_VARIANTS := client minimal server
JVM_VARIANTS := $(strip $(foreach x,$(CANDIDATE_JVM_VARIANTS),$(if $(wildcard $(JVMS_DIR)/$(x)),$(x))))

################################################################

# Run the native gtest tests from the test image

define NEWLINE


endef


hotspot_gtest:
	$(foreach v, $(JVM_VARIANTS), \
	  $(MAKE) hotspot_gtest$v $(NEWLINE) )

hotspot_gtestserver hotspot_gtestclient hotspot_gtestminimal: hotspot_gtest%:
	$(TESTNATIVE_DIR)/hotspot/gtest/$*/gtestLauncher \
	    -jdk $(shell $(GETMIXEDPATH) "$(PRODUCT_HOME)")

PHONY_LIST += hotspot_gtest hotspot_gtestserver hotspot_gtestclient \
    hotspot_gtestminimal
