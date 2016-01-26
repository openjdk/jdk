#
# Copyright (c) 1995, 2015, Oracle and/or its affiliates. All rights reserved.
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

#
# Makefile to run various hotspot tests
#

ALT_MAKE ?= closed

GETMIXEDPATH=echo

# Utilities used
AWK       = awk
CAT       = cat
CD        = cd
CHMOD     = chmod
CP        = cp
CUT       = cut
DIRNAME   = dirname
ECHO      = echo
EGREP     = egrep
EXPAND    = expand
FIND      = find
MKDIR     = mkdir
PWD       = pwd
SED       = sed
SORT      = sort
TEE       = tee
UNAME     = uname
UNIQ      = uniq
WC        = wc
ZIP       = zip

# Get OS name from uname (Cygwin inexplicably adds _NT-5.1)
UNAME_S := $(shell $(UNAME) -s | $(CUT) -f1 -d_)
ifeq ($(UNAME_S), SunOS)
  PLATFORM = solaris
  SLASH_JAVA = /java
  ARCH = $(shell uname -p)
  ifeq ($(ARCH), i386)
    ARCH=i586
  endif
endif
ifeq ($(UNAME_S), Linux)
  PLATFORM = linux
  SLASH_JAVA = /java
  ARCH = $(shell uname -m)
  ifeq ($(ARCH), i386)
    ARCH = i586
  endif
endif
ifeq ($(UNAME_S), Darwin)
  PLATFORM = bsd
  SLASH_JAVA = /java
  ARCH = $(shell uname -m)
  ifeq ($(ARCH), i386)
    ARCH = i586
  endif
endif
ifeq ($(findstring BSD,$(UNAME_S)), BSD)
  PLATFORM = bsd
  SLASH_JAVA = /java
  ARCH = $(shell uname -m)
  ifeq ($(ARCH), i386)
    ARCH = i586
  endif
endif
ifeq ($(PLATFORM),)
  # detect whether we're running in MKS or cygwin
  ifeq ($(UNAME_S), Windows_NT) # MKS
    GETMIXEDPATH=dosname -s
  endif
  ifeq ($(findstring CYGWIN,$(UNAME_S)), CYGWIN)
    GETMIXEDPATH=cygpath -m
  endif
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

# Root of this test area (important to use full paths in some places)
TEST_ROOT := $(shell pwd)

# Root of all test results
ifdef ALT_OUTPUTDIR
  ABS_BUILD_ROOT = $(ALT_OUTPUTDIR)
else
  ABS_BUILD_ROOT = $(TEST_ROOT)/../build/$(PLATFORM)-$(ARCH)
endif
ABS_TEST_OUTPUT_DIR = $(ABS_BUILD_ROOT)/testoutput/$(UNIQUE_DIR)

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

# jtreg -nativepath <dir>
#
# Local make tests will be TEST_IMAGE_DIR and JPRT with jprt.use.reg.test.bundle=true
# should be JPRT_TESTNATIVE_PATH
ifdef TEST_IMAGE_DIR
  TESTNATIVE_DIR = $(TEST_IMAGE_DIR)
else ifdef JPRT_TESTNATIVE_PATH
  TESTNATIVE_DIR = $(JPRT_TESTNATIVE_PATH)
endif
ifdef TESTNATIVE_DIR
  JTREG_NATIVE_PATH = -nativepath:$(shell $(GETMIXEDPATH) "$(TESTNATIVE_DIR)/hotspot/jtreg/native")
endif

# Expect JPRT to set JPRT_ARCHIVE_BUNDLE (path to zip bundle for results)
ARCHIVE_BUNDLE = $(ABS_TEST_OUTPUT_DIR)/ARCHIVE_BUNDLE.zip
ifdef JPRT_ARCHIVE_BUNDLE
  ARCHIVE_BUNDLE = $(JPRT_ARCHIVE_BUNDLE)
endif

# How to create the test bundle (pass or fail, we want to create this)
#   Follow command with ";$(BUNDLE_UP_AND_EXIT)", so it always gets executed.
ZIP_UP_RESULTS = ( $(MKDIR) -p `$(DIRNAME) $(ARCHIVE_BUNDLE)`     \
	           && $(CD) $(ABS_TEST_OUTPUT_DIR)             \
	           && $(CHMOD) -R a+r . \
	           && $(ZIP) -q -r $(ARCHIVE_BUNDLE) . )

# important results files
SUMMARY_TXT = $(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)/JTreport/text/summary.txt")
STATS_TXT_NAME = Stats.txt
STATS_TXT = $(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)/$(STATS_TXT_NAME)")
RUNLIST   = $(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)/runlist.txt")
PASSLIST  = $(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)/passlist.txt")
FAILLIST  = $(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)/faillist.txt")
EXITCODE  = $(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)/exitcode.txt")

TESTEXIT = \
  if [ ! -s $(EXITCODE) ] ; then \
    $(ECHO) "ERROR: EXITCODE file not filled in."; \
    $(ECHO) "1" > $(EXITCODE); \
  fi ; \
  testExitCode=`$(CAT) $(EXITCODE)`; \
  $(ECHO) "EXIT CODE: $${testExitCode}"; \
  exit $${testExitCode}

BUNDLE_UP_AND_EXIT = \
( \
  jtregExitCode=$$? && \
  _summary="$(SUMMARY_TXT)"; \
  $(RM) -f $(STATS_TXT) $(RUNLIST) $(PASSLIST) $(FAILLIST) $(EXITCODE); \
  $(ECHO) "$${jtregExitCode}" > $(EXITCODE); \
  if [ -r "$${_summary}" ] ; then \
    $(ECHO) "Summary: $(UNIQUE_DIR)" > $(STATS_TXT); \
    $(EXPAND) $${_summary} | $(EGREP) -v ' Not run\.' > $(RUNLIST); \
    $(EGREP) ' Passed\.' $(RUNLIST) \
      | $(EGREP) -v ' Error\.' \
      | $(EGREP) -v ' Failed\.' > $(PASSLIST); \
    ( $(EGREP) ' Failed\.' $(RUNLIST); \
      $(EGREP) ' Error\.' $(RUNLIST); \
      $(EGREP) -v ' Passed\.' $(RUNLIST) ) \
      | $(SORT) | $(UNIQ) > $(FAILLIST); \
    if [ $${jtregExitCode} != 0 -o -s $(FAILLIST) ] ; then \
      $(EXPAND) $(FAILLIST) \
        | $(CUT) -d' ' -f1 \
        | $(SED) -e 's@^@FAILED: @' >> $(STATS_TXT); \
      if [ $${jtregExitCode} = 0 ] ; then \
        jtregExitCode=1; \
      fi; \
    fi; \
    runc="`$(CAT) $(RUNLIST)      | $(WC) -l | $(AWK) '{print $$1;}'`"; \
    passc="`$(CAT) $(PASSLIST)    | $(WC) -l | $(AWK) '{print $$1;}'`"; \
    failc="`$(CAT) $(FAILLIST)    | $(WC) -l | $(AWK) '{print $$1;}'`"; \
    exclc="FIXME CODETOOLS-7900176"; \
    $(ECHO) "TEST STATS: name=$(UNIQUE_DIR)  run=$${runc}  pass=$${passc}  fail=$${failc}" \
      >> $(STATS_TXT); \
  else \
    $(ECHO) "Missing file: $${_summary}" >> $(STATS_TXT); \
  fi; \
  if [ -f $(STATS_TXT) ] ; then \
    $(CAT) $(STATS_TXT); \
  fi; \
  $(ZIP_UP_RESULTS) ; \
  $(TESTEXIT) \
)

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

# Prep for output
prep: clean
	@$(MKDIR) -p $(ABS_TEST_OUTPUT_DIR)
	@$(MKDIR) -p `dirname $(ARCHIVE_BUNDLE)`

# Cleanup
clean:
	@$(RM) -r $(ABS_TEST_OUTPUT_DIR)
	@$(RM) $(ARCHIVE_BUNDLE)

################################################################

# jtreg tests

# Expect JT_HOME to be set for jtreg tests. (home for jtreg)
ifndef JT_HOME
  JT_HOME = $(SLASH_JAVA)/re/jtreg/4.1/promoted/latest/binaries/jtreg
  ifdef JPRT_JTREG_HOME
    JT_HOME = $(JPRT_JTREG_HOME)
  endif
endif

# When called from JPRT the TESTDIRS variable is set to the jtreg tests to run
ifdef TESTDIRS
  TEST_SELECTION = $(TESTDIRS)
endif

ifdef CONCURRENCY
  EXTRA_JTREG_OPTIONS += -concurrency:$(CONCURRENCY)
endif

# Default JTREG to run
JTREG = $(JT_HOME)/bin/jtreg

# Use agent mode
JTREG_BASIC_OPTIONS += -agentvm
# Only run automatic tests
JTREG_BASIC_OPTIONS += -a
# Report details on all failed or error tests, times too
JTREG_BASIC_OPTIONS += -v:fail,error,time
# Retain all files for failing tests
JTREG_BASIC_OPTIONS += -retain:fail,error
# Ignore tests are not run and completely silent about it
JTREG_IGNORE_OPTION = -ignore:quiet
JTREG_BASIC_OPTIONS += $(JTREG_IGNORE_OPTION)
# Multiply by 4 the timeout factor
JTREG_TIMEOUT_OPTION =  -timeoutFactor:4
JTREG_BASIC_OPTIONS += $(JTREG_TIMEOUT_OPTION)
# Add any extra options
JTREG_BASIC_OPTIONS += $(EXTRA_JTREG_OPTIONS)
# Set other vm and test options
JTREG_TEST_OPTIONS = $(JAVA_ARGS:%=-javaoptions:%) $(JAVA_OPTIONS:%=-vmoption:%) $(JAVA_VM_ARGS:%=-vmoption:%)

# Option to tell jtreg to not run tests marked with "ignore"
ifeq ($(PLATFORM), windows)
  JTREG_KEY_OPTION = -k:!ignore
else
  JTREG_KEY_OPTION = -k:\!ignore
endif
JTREG_BASIC_OPTIONS += $(JTREG_KEY_OPTION)

-include $(ALT_MAKE)/Makefile

# Make sure jtreg exists
$(JTREG): $(JT_HOME)

jtreg_tests: prep $(PRODUCT_HOME) $(JTREG)
	(                                                                    \
	  ( JT_HOME=$(shell $(GETMIXEDPATH) "$(JT_HOME)");                   \
            export JT_HOME;                                                  \
            $(shell $(GETMIXEDPATH) "$(JTREG)")                              \
              $(JTREG_BASIC_OPTIONS)                                         \
              -r:$(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)/JTreport")  \
              -w:$(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)/JTwork")    \
              -jdk:$(shell $(GETMIXEDPATH) "$(PRODUCT_HOME)")                \
              $(JTREG_NATIVE_PATH)                                           \
              $(JTREG_EXCLUSIONS)                                            \
              $(JTREG_TEST_OPTIONS)                                          \
              $(TEST_SELECTION)                                              \
	  ) ;                                                                \
	  $(BUNDLE_UP_AND_EXIT)                                              \
	) 2>&1 | $(TEE) $(ABS_TEST_OUTPUT_DIR)/output.txt ; $(TESTEXIT)

PHONY_LIST += jtreg_tests

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
else ifeq ($(PLATFORM),bsd)
JVMS_DIR := $(PRODUCT_HOME)/lib
else
# The jvms live in the architecture directory (amd64, sparcv9,
# etc.). By using a wildcard there's no need to figure out the exact
# name of that directory.
JVMS_DIR := $(PRODUCT_HOME)/lib/*
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

# Phony targets (e.g. these are not filenames)
.PHONY: all clean prep $(PHONY_LIST)

################################################################
