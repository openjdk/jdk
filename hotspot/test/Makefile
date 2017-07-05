#
# Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
# Makefile to run jtreg
#

OSNAME = $(shell uname -s)
ifeq ($(OSNAME), SunOS)
  PLATFORM = solaris
  JCT_PLATFORM = solaris
  ARCH = $(shell uname -p)
  ifeq ($(ARCH), i386)
    ARCH=i586
  endif
endif
ifeq ($(OSNAME), Linux)
  PLATFORM = linux
  JCT_PLATFORM = linux
  ARCH = $(shell uname -m)
  ifeq ($(ARCH), i386)
    ARCH=i586
  endif
endif
ifeq ($(OSNAME), Windows_NT)
  PLATFORM = windows
  JCT_PLATFORM = win32
  ifeq ($(word 1, $(PROCESSOR_IDENTIFIER)),ia64)
    ARCH=ia64
  else
    ifeq ($(word 1, $(PROCESSOR_IDENTIFIER)),AMD64)
      ARCH=x64
    else
      ifeq ($(word 1, $(PROCESSOR_IDENTIFIER)),EM64T)
        ARCH=x64
      else
        ARCH=i586
      endif
    endif
  endif
endif

# Default bundle of all test results (passed or not)
JPRT_ARCHIVE_BUNDLE=$(TEST_ROOT)/JPRT_ARCHIVE_BUNDLE.zip

# Default home for JTREG
ifeq ($(PLATFORM), windows)
  JT_HOME = J:/svc/jct-tools3.2.2_01
else
  JT_HOME = /java/svc/jct-tools3.2.2_01
endif

# Default JTREG to run
JTREG = $(JT_HOME)/$(JCT_PLATFORM)/bin/jtreg

# Root of this test area
TEST_ROOT := $(shell pwd)

# Default JDK to test
JAVA_HOME = $(TEST_ROOT)/../build/$(PLATFORM)-$(ARCH)

# The test directories to run
DEFAULT_TESTDIRS = serviceability
TESTDIRS = $(DEFAULT_TESTDIRS)

# Files that hold total passed and failed counts (passed==0 is bad)
JTREG_TOTALS_DIR = $(TEST_ROOT)/JTREG_TOTALS_$(PLATFORM)_$(ARCH)
JTREG_FAILED = $(JTREG_TOTALS_DIR)/failed_count
JTREG_PASSED = $(JTREG_TOTALS_DIR)/passed_count

# Root of all test results
JTREG_ALL_OUTPUT_DIRNAME = JTREG_OUTPUT_$(PLATFORM)_$(ARCH)
JTREG_ALL_OUTPUT_DIR = $(TEST_ROOT)/$(JTREG_ALL_OUTPUT_DIRNAME)

# Test results for one test directory
JTREG_TEST_OUTPUT_DIR = $(JTREG_ALL_OUTPUT_DIR)/$@
JTREG_TEST_REPORT_DIR = $(JTREG_TEST_OUTPUT_DIR)/JTreport
JTREG_TEST_WORK_DIR   = $(JTREG_TEST_OUTPUT_DIR)/JTwork
JTREG_TEST_SUMMARY    =	$(JTREG_TEST_REPORT_DIR)/summary.txt

# Temp files used by this Makefile
JTREG_TEST_TEMP_DIR   = $(JTREG_ALL_OUTPUT_DIR)/$@/temp
JTREG_TEMP_PASSED     = $(JTREG_TEST_TEMP_DIR)/passed
JTREG_TEMP_FAILED     = $(JTREG_TEST_TEMP_DIR)/failed
JTREG_TEMP_OUTPUT     = $(JTREG_TEST_TEMP_DIR)/output
JTREG_TEMP_RESULTS    = $(JTREG_TEST_TEMP_DIR)/results

# JTREG options (different for 2.1.6 and 3.2.2_01)
JTREG_COMMON_OPTIONS = -r:$(JTREG_TEST_REPORT_DIR) \
                       -w:$(JTREG_TEST_WORK_DIR) \
                       -testjdk:$(JAVA_HOME) \
                       -automatic \
	               -verbose:all
JTREG_216_OPTIONS = $(JTREG_COMMON_OPTIONS) $@ $(JAVA_ARGS)
JTREG_322_OPTIONS = $(JTREG_COMMON_OPTIONS) $(JAVA_ARGS:%=-vmoption:%) $@

# Default make rule
all: clean check tests

# Chaeck to make sure these directories exist
check: $(JT_HOME) $(JAVA_HOME) $(JTREG)

# Prime the test run
primecounts: FRC
	@rm -f -r $(JTREG_TOTALS_DIR)
	@mkdir -p $(JTREG_TOTALS_DIR)
	@echo "0" > $(JTREG_FAILED)
	@echo "0" > $(JTREG_PASSED)

# Run the tests and determine the 'make' command exit status
#   Ultimately we determine the make exit code based on the passed/failed count
tests: primecounts $(TESTDIRS)
	@echo "JTREG TOTAL: passed=`cat $(JTREG_PASSED)` failed=`cat $(JTREG_FAILED)`"
	zip -q -r $(JPRT_ARCHIVE_BUNDLE) $(JTREG_ALL_OUTPUT_DIRNAME)
	@if [ `cat $(JTREG_FAILED)` -ne 0 -o \
	     `cat $(JTREG_PASSED)` -le 0 ] ; then \
	  echo "JTREG FAILED"; \
	  exit 1; \
	else \
	  echo "JTREG PASSED"; \
	  exit 0; \
	fi

# Just make sure these directires exist
$(JT_HOME) $(JAVA_HOME): FRC
	@if [ ! -d $@ ] ; then \
	    echo "ERROR: Directory $@ does not exist"; \
	    exit 1; \
	fi

# Make sure this file exists
$(JTREG): FRC
	@if [ ! -f $@ ] ; then \
	    echo "ERROR: File $@ does not exist"; \
	    exit 1; \
	fi

# Process each test directory one by one, this rule always completes.
#    Note that the use of 'tee' tosses the jtreg process exit status, this
#    is as expected because even if jtreg fails, we need to save the
#    output. So we update the JTREG_PASSED and JTREG_FAILED count files.
#    Note that missing the 'results:' line in the last few lines of output
#    will indicate a failure (or a bump by one of the JTREG_FAILED file.
#    Note that passed: 0 or no passed: indication means a failure.
#    Note that any indication of the word 'failed' indicates failure.
#    Ultimately if the contents of JTREG_FAILED is not 0, we have failed 
#    tests, and if the contents of JTREG_PASSED is 0, we consider that a
#    failure.
$(TESTDIRS):  FRC
	@if [ ! -d $@ ] ; then \
	    echo "ERROR: Directory $@ does not exist"; \
	    exit 1; \
	fi
	@echo "---------------------------------------------------"
	@rm -f -r $(JTREG_TEST_OUTPUT_DIR)
	@mkdir -p $(JTREG_TEST_OUTPUT_DIR)
	@mkdir -p $(JTREG_TEST_WORK_DIR)
	@mkdir -p $(JTREG_TEST_WORK_DIR)/scratch
	@mkdir -p $(JTREG_TEST_REPORT_DIR)
	@mkdir -p $(JTREG_TEST_TEMP_DIR)
	@echo "Testing $@"
	@echo "Using JAVA_HOME=$(JAVA_HOME)"
	@echo "Using JAVA_ARGS=$(JAVA_ARGS)"
	@if [ "`$(JTREG) -help 2>&1 | fgrep -- -vmoption`" != "" ] ; then \
	  echo "Assume we are using jtreg 3.2.2_01 or newer"; \
	  echo "$(JTREG) $(JTREG_322_OPTIONS)"; \
	  $(JTREG) $(JTREG_322_OPTIONS) 2>&1 | tee $(JTREG_TEMP_OUTPUT) ; \
	else \
	  echo "Assume we are using jtreg 2.1.6"; \
	  echo "$(JTREG) $(JTREG_216_OPTIONS)"; \
	  $(JTREG) $(JTREG_216_OPTIONS) 2>&1 | tee $(JTREG_TEMP_OUTPUT) ; \
	fi
	@echo "---------------------------------------------------"
	@echo "Extracting passed and failed counts from jtreg output"
	@tail -10 $(JTREG_TEMP_OUTPUT) | fgrep -i 'results:' | \
	       tail -1 | tee $(JTREG_TEMP_RESULTS)
	@sed -e 's@.*\ passed:\ \([1-9][0-9]*\).*@\1@' $(JTREG_TEMP_RESULTS) \
	       > $(JTREG_TEMP_PASSED)
	@if [ "`cat $(JTREG_TEMP_PASSED)`" = "" ] ; then \
	  echo "ERROR: No passed indication in results"; \
	  expr `cat $(JTREG_FAILED)` '+' 1 > $(JTREG_FAILED); \
	elif [ `cat $(JTREG_TEMP_PASSED)` -le 0 ] ; then \
	  echo "ERROR: Passed count appears to be 0"; \
	  expr `cat $(JTREG_FAILED)` '+' 1 > $(JTREG_FAILED); \
	elif [ "`fgrep -i failed $(JTREG_TEMP_RESULTS)`" = "" ] ; then \
	  echo "No indication anything failed"; \
	  expr `cat $(JTREG_PASSED)` '+' `cat $(JTREG_TEMP_PASSED)` \
		> $(JTREG_PASSED); \
	else \
	  sed -e 's@.*\ failed:\ \([1-9][0-9]*\).*@\1@' $(JTREG_TEMP_FAILED) \
		> $(JTREG_TEMP_FAILED); \
	  if [ "`cat $(JTREG_TEMP_FAILED)`" = "" ] ; then \
	    echo "ERROR: Failed pattern but no failed count in results"; \
	    expr `cat $(JTREG_FAILED)` '+' 1 > $(JTREG_FAILED); \
	  elif [ `cat $(JTREG_TEMP_FAILED)` -le 0 ] ; then \
	    echo "ERROR: Failed count is 0, did something failed or not?"; \
	    expr `cat $(JTREG_FAILED)` '+' 1 > $(JTREG_FAILED); \
	  else \
	    expr `cat $(JTREG_FAILED)` '+' `cat $(JTREG_TEMP_FAILED)` \
		  > $(JTREG_FAILED); \
	  fi; \
	fi
	@echo "---------------------------------------------------"
	@echo "Summary: "
	@if [ -f $(JTREG_TEST_SUMMARY) ] ; then \
	  cat $(JTREG_TEST_SUMMARY) ; \
	else \
	  echo "ERROR: Missing $(JTREG_TEST_SUMMARY)"; \
	fi
	@echo "---------------------------------------------------"

# Cleanup
clean:
	rm -f -r $(JTREG_ALL_OUTPUT_DIR)
	rm -f $(JPRT_ARCHIVE_BUNDLE)

FRC:

