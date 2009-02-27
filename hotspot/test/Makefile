#
# Copyright 1995-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#
#

#
# Makefile to run various jdk tests
#

# Get OS/ARCH specifics
OSNAME = $(shell uname -s)
ifeq ($(OSNAME), SunOS)
  PLATFORM = solaris
  SLASH_JAVA = /java
  ARCH = $(shell uname -p)
  ifeq ($(ARCH), i386)
    ARCH=i586
  endif
endif
ifeq ($(OSNAME), Linux)
  PLATFORM = linux
  SLASH_JAVA = /java
  ARCH = $(shell uname -m)
  ifeq ($(ARCH), i386)
    ARCH = i586
  endif
endif
ifeq ($(OSNAME), Windows_NT)
  PLATFORM = windows
  SLASH_JAVA = J:
  ifeq ($(word 1, $(PROCESSOR_IDENTIFIER)),ia64)
    ARCH = ia64
  else
    ifeq ($(word 1, $(PROCESSOR_IDENTIFIER)),AMD64)
      ARCH = x64
    else
      ifeq ($(word 1, $(PROCESSOR_IDENTIFIER)),EM64T)
        ARCH = x64
      else
        ARCH = i586
      endif
    endif
  endif
  EXESUFFIX = .exe
endif

ifdef ALT_SLASH_JAVA
  SLASH_JAVA = $(ALT_SLASH_JAVA)
endif

# Utilities used
CD    = cd
CP    = cp
ECHO  = echo
MKDIR = mkdir
ZIP   = zip

# Root of this test area (important to use full paths in some places)
TEST_ROOT := $(shell pwd)

# Root of all test results
ABS_BUILD_ROOT = $(TEST_ROOT)/../build/$(PLATFORM)-$(ARCH)
ABS_TEST_OUTPUT_DIR = $(ABS_BUILD_ROOT)/testoutput

# Expect JPRT to set PRODUCT_HOME (the product or jdk in this case to test)
ifndef PRODUCT_HOME
  # Try to use j2sdk-image if it exists
  ABS_JDK_IMAGE = $(ABS_BUILD_ROOT)/j2sdk-image
  PRODUCT_HOME :=                       \
    $(shell                             \
      if [ -d $(ABS_JDK_IMAGE) ] ; then \
         $(ECHO) "$(ABS_JDK_IMAGE)";    \
       else                             \
         $(ECHO) "$(ABS_BUILD_ROOT)" ;  \
       fi)
endif

# Expect JPRT to set JAVA_ARGS (e.g. -server etc.)
JAVA_OPTIONS = 
ifdef JAVA_ARGS
  JAVA_OPTIONS = $(JAVA_ARGS)
endif

# Expect JPRT to set JPRT_ARCHIVE_BUNDLE (path to zip bundle for results)
ARCHIVE_BUNDLE = $(ABS_TEST_OUTPUT_DIR)/ARCHIVE_BUNDLE.zip
ifdef JPRT_ARCHIVE_BUNDLE
  ARCHIVE_BUNDLE = $(JPRT_ARCHIVE_BUNDLE)
endif

# How to create the test bundle (pass or fail, we want to create this)
BUNDLE_UP = ( $(MKDIR) -p `dirname $(ARCHIVE_BUNDLE)`     \
	      && $(CD) $(ABS_TEST_OUTPUT_DIR)             \
	      && $(ZIP) -q -r $(ARCHIVE_BUNDLE) . )
BUNDLE_UP_FAILED = ( exitCode=$$? && $(BUNDLE_UP) && exit $${exitCode} )

################################################################

# Default make rule (runs jtreg_tests)
all: jtreg_tests
	@$(ECHO) "Testing completed successfully"

# Prep for output
prep: clean
	@$(MKDIR) -p $(ABS_TEST_OUTPUT_DIR)
	@$(MKDIR) -p `dirname $(ARCHIVE_BUNDLE)`

# Cleanup
clean:
	$(RM) -r $(ABS_TEST_OUTPUT_DIR)
	$(RM) $(ARCHIVE_BUNDLE)

################################################################

# jtreg tests

# Expect JT_HOME to be set for jtreg tests. (home for jtreg)
JT_HOME = $(SLASH_JAVA)/re/jtreg/4.0/promoted/latest/binaries/jtreg
ifdef JPRT_JTREG_HOME
  JT_HOME = $(JPRT_JTREG_HOME)
endif

# Expect JPRT to set TESTDIRS to the jtreg test dirs
JTREG_TESTDIRS = demo/jvmti/gctest demo/jvmti/hprof
ifdef TESTDIRS
  JTREG_TESTDIRS = $(TESTDIRS)
endif

# Default JTREG to run (win32 script works for everybody)
JTREG = $(JT_HOME)/win32/bin/jtreg

# Option to tell jtreg to not run tests marked with "ignore"
ifeq ($(PLATFORM), windows)
  JTREG_KEY_OPTION = -k:!ignore
else
  JTREG_KEY_OPTION = -k:\!ignore
endif

#EXTRA_JTREG_OPTIONS =

jtreg_tests: prep $(JT_HOME) $(PRODUCT_HOME) $(JTREG)
	$(JTREG) -a -v:fail,error               \
          $(JTREG_KEY_OPTION)                   \
          $(EXTRA_JTREG_OPTIONS)                \
          -r:$(ABS_TEST_OUTPUT_DIR)/JTreport    \
          -w:$(ABS_TEST_OUTPUT_DIR)/JTwork      \
          -jdk:$(PRODUCT_HOME)                  \
          $(JAVA_OPTIONS:%=-vmoption:%)         \
          $(JTREG_TESTDIRS)                     \
	  || $(BUNDLE_UP_FAILED)
	$(BUNDLE_UP)

PHONY_LIST += jtreg_tests

################################################################

# packtest

# Expect JPRT to set JPRT_PACKTEST_HOME.
PACKTEST_HOME = /net/jprt-web.sfbay.sun.com/jprt/allproducts/packtest
ifdef JPRT_PACKTEST_HOME
  PACKTEST_HOME = $(JPRT_PACKTEST_HOME)
endif

#EXTRA_PACKTEST_OPTIONS =

packtest: prep $(PACKTEST_HOME)/ptest $(PRODUCT_HOME)
	( $(CD) $(PACKTEST_HOME) &&            \
	    $(PACKTEST_HOME)/ptest             \
		 -t "$(PRODUCT_HOME)"          \
	         $(PACKTEST_STRESS_OPTION)     \
		 $(EXTRA_PACKTEST_OPTIONS)     \
		 -W $(ABS_TEST_OUTPUT_DIR)     \
                 $(JAVA_OPTIONS:%=-J %)        \
	 ) || $(BUNDLE_UP_FAILED)
	$(BUNDLE_UP)

packtest_stress: PACKTEST_STRESS_OPTION=-s
packtest_stress: packtest

PHONY_LIST += packtest packtest_stress

################################################################

# Phony targets (e.g. these are not filenames)
.PHONY: all clean prep $(PHONY_LIST)

################################################################

