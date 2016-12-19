#
# Copyright (c) 1995, 2016, Oracle and/or its affiliates. All rights reserved.
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

NATIVE_TEST_PATH := hotspot/jtreg/native

TREAT_EXIT_CODE_1_AS_0 := true

CLEAN_BEFORE_PREP := true

USE_JTREG_VERSION := 4.1

USE_JTREG_ASSERT := false

LIMIT_JTREG_VM_MEMORY := false

IGNORE_MARKED_TESTS := true

# Get OS name from uname (Cygwin inexplicably adds _NT-5.1)
UNAME_S := $(shell uname -s | cut -f1 -d_)

ifeq ($(UNAME_S), SunOS)
  NUM_CORES := $(shell LC_MESSAGES=C /usr/sbin/psrinfo -v | grep -c on-line)
endif
ifeq ($(UNAME_S), Linux)
  NUM_CORES := $(shell cat /proc/cpuinfo  | grep -c processor)
endif
ifeq ($(UNAME_S), Darwin)
  NUM_CORES := $(shell /usr/sbin/sysctl -n hw.ncpu)
endif
ifeq ($(findstring CYGWIN,$(UNAME_S)), CYGWIN)
  ifneq ($(NUMBER_OF_PROCESSORS), )
    NUM_CORES := $(NUMBER_OF_PROCESSORS)
  else
    ifneq ($(HOTSPOT_BUILD_JOBS), )
      NUM_CORES := $(HOTSPOT_BUILD_JOBS)
    else
      NUM_CORES := 1 # fallback
    endif
  endif
endif

# Concurrency based on min(cores / 2, 12)
CONCURRENCY := $(shell expr $(NUM_CORES) / 2)
ifeq ($(CONCURRENCY), 0)
  CONCURRENCY := 1
else ifeq ($(shell expr $(CONCURRENCY) \> 12), 1)
  CONCURRENCY := 12
endif

# Make sure MaxRAMFraction is high enough to not cause OOM or swapping since we may end up with a lot of JVM's
JTREG_BASIC_OPTIONS += -vmoption:-XX:MaxRAMFraction=$(shell expr $(CONCURRENCY) \* 4)

# Include the common base file with most of the logic
include ../../test/TestCommon.gmk

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

hotspot_internal:
	$(ALT_OUTPUTDIR)/jdk/bin/java -XX:+ExecuteInternalVMTests -XX:+ShowMessageBoxOnError -version

################################################################
ALT_MAKE ?= closed
-include $(ALT_MAKE)/Makefile

# flags used to execute java in test targets
TEST_FLAGS += -version -Xinternalversion -X -help

sanitytest: prep $(PRODUCT_HOME)
	@for flag in $(TEST_FLAGS);                                             \
	do                                                                      \
	    echo Executing java $(JAVA_OPTIONS) $$flag;                         \
	    $(PRODUCT_HOME)/bin/java $(JAVA_OPTIONS) $$flag;                    \
	    res=$$?;                                                            \
	    if [ $$res -ne 0 ]; then                                            \
	        exit $$res;                                                     \
	    fi;                                                                 \
	done

PHONY_LIST += sanitytest

################################################################

# basicvmtest (make sure various basic java options work)

# Set up the directory in which the jvm directories live (client/, server/, etc.)
ifeq ($(PLATFORM),windows)
JVMS_DIR := $(PRODUCT_HOME)/bin
else
JVMS_DIR := $(PRODUCT_HOME)/lib
endif

# Use the existance of a directory as a sign that jvm variant is available
CANDIDATE_JVM_VARIANTS := client minimal server
JVM_VARIANTS := $(strip $(foreach x,$(CANDIDATE_JVM_VARIANTS),$(if $(wildcard $(JVMS_DIR)/$(x)),$(x))))

hotspot_basicvmtest:
	for variant in $(JVM_VARIANTS);                                           \
	do                                                                        \
	    $(MAKE) JAVA_ARGS="$(JAVA_ARGS) -$$variant" hotspot_$${variant}test;  \
	    res=$$?;                                                              \
	    if [ $$res -ne 0 ]; then                                              \
	        exit $$res;                                                       \
	    fi;                                                                   \
	done

PHONY_LIST += hotspot_basicvmtest

################################################################

# clienttest (make sure various basic java client options work)

hotspot_clienttest clienttest: sanitytest
	$(RM) $(PRODUCT_HOME)/jre/lib/*/client/classes.jsa
	$(RM) $(PRODUCT_HOME)/jre/bin/client/classes.jsa
	$(PRODUCT_HOME)/bin/java $(JAVA_OPTIONS) -Xshare:dump

PHONY_LIST += hotspot_clienttest clienttest

################################################################

# minimaltest (make sure various basic java minimal options work)

hotspot_minimaltest minimaltest: sanitytest

PHONY_LIST += hotspot_minimaltest minimaltest

################################################################

# servertest (make sure various basic java server options work)

hotspot_servertest servertest: sanitytest

PHONY_LIST += hotspot_servertest servertest

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
