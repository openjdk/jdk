#
# Copyright (c) 1995, 2014, Oracle and/or its affiliates. All rights reserved.
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
# Makefile to run various jdk tests
#

.DEFAULT : all

# Empty these to get rid of some default rules
.SUFFIXES:
.SUFFIXES: .java
CO=
GET=

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

# Commands to run on paths to make mixed paths for java on windows
ifeq ($(UNAME_S), CYGWIN)
  # Location of developer shared files
  SLASH_JAVA = J:
  GETMIXEDPATH = cygpath -m
else
  # Location of developer shared files
  SLASH_JAVA = /java

  GETMIXEDPATH=$(ECHO)
endif

# Root of this test area (important to use full paths in some places)
TEST_ROOT := $(shell $(PWD))

# Root of all test results
ifdef ALT_OUTPUTDIR
  ABS_OUTPUTDIR = $(shell $(CD) $(ALT_OUTPUTDIR) && $(PWD))
else
  ABS_OUTPUTDIR = $(shell $(CD) $(TEST_ROOT)/.. && $(PWD))
endif

ABS_PLATFORM_BUILD_ROOT = $(ABS_OUTPUTDIR)
ABS_TEST_OUTPUT_DIR := $(ABS_PLATFORM_BUILD_ROOT)/testoutput/$(UNIQUE_DIR)

# Expect JPRT to set PRODUCT_HOME (the product or jdk in this case to test)
ifndef PRODUCT_HOME
  # Try to use j2sdk-image if it exists
  ABS_JDK_IMAGE = $(ABS_PLATFORM_BUILD_ROOT)/images/j2sdk-image
  PRODUCT_HOME :=                       		\
    $(shell                             		\
      if [ -d $(ABS_JDK_IMAGE) ] ; then 		\
         $(ECHO) "$(ABS_JDK_IMAGE)";    		\
       else                             		\
         $(ECHO) "$(ABS_PLATFORM_BUILD_ROOT)";		\
       fi)
  PRODUCT_HOME := $(PRODUCT_HOME)
endif

# Expect JPRT to set JPRT_PRODUCT_ARGS (e.g. -server etc.)
#   Should be passed into 'java' only.
#   Could include: -d64 -server -client OR any java option
ifdef JPRT_PRODUCT_ARGS
  JAVA_ARGS = $(JPRT_PRODUCT_ARGS)
endif

# Expect JPRT to set JPRT_PRODUCT_VM_ARGS (e.g. -Xcomp etc.)
#   Should be passed into anything running the vm (java, javac, javadoc, ...).
ifdef JPRT_PRODUCT_VM_ARGS
  JAVA_VM_ARGS = $(JPRT_PRODUCT_VM_ARGS)
endif

# Expect JPRT to set JPRT_ARCHIVE_BUNDLE (path to zip bundle for results)
ifdef JPRT_ARCHIVE_BUNDLE
  ARCHIVE_BUNDLE = $(JPRT_ARCHIVE_BUNDLE)
else
  ARCHIVE_BUNDLE = $(ABS_TEST_OUTPUT_DIR)/ARCHIVE_BUNDLE.zip
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

# Default make rule (runs default jdk tests)
all: jdk_default
	@$(ECHO) "Testing completed successfully"

# Prep for output
# Change execute permissions on shared library files.
# Files in repositories should never have execute permissions, but
# there are some tests that have pre-built shared libraries, and these
# windows dll files must have execute permission. Adding execute
# permission may happen automatically on windows when using certain
# versions of mercurial but it cannot be guaranteed. And blindly
# adding execute permission might be seen as a mercurial 'change', so
# we avoid adding execute permission to repository files. But testing
# from a plain source tree needs the chmod a+rx. Applying the chmod to
# all shared libraries not just dll files. And with CYGWIN and sshd
# service, you may need CYGWIN=ntsec for this to work.
prep:
	@$(MKDIR) -p $(ABS_TEST_OUTPUT_DIR)
	@$(MKDIR) -p `$(DIRNAME) $(ARCHIVE_BUNDLE)`
	@if [ ! -d $(TEST_ROOT)/../.hg ] ; then                          \
	  $(FIND) $(TEST_ROOT) \( -name \*.dll -o -name \*.DLL -o -name \*.so \)  \
	        -exec $(CHMOD) a+rx {} \; ;                             \
        fi

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

# Problematic tests to be excluded
PROBLEM_LISTS=$(call MixedDirs,$(wildcard ProblemList.txt closed/ProblemList.txt))

# Create exclude list for this platform and arch
ifdef NO_EXCLUDES
  JTREG_EXCLUSIONS =
else
  JTREG_EXCLUSIONS = $(PROBLEM_LISTS:%=-exclude:%)
endif

# convert list of directories to dos paths
define MixedDirs
$(foreach i,$1,$(shell $(GETMIXEDPATH) "${i}"))
endef

define SummaryInfo
$(ECHO) "########################################################"
$(CAT) $(?:%=$(ABS_TEST_OUTPUT_DIR)/%/$(STATS_TXT_NAME))
$(ECHO) "########################################################"
endef

# ------------------------------------------------------------------

jdk_% core_% svc_%:
	$(ECHO) "Running tests: $@"
	for each in $@; do \
	        $(MAKE) -j 1 TEST_SELECTION=":$$each" UNIQUE_DIR=$$each jtreg_tests; \
	done

# ------------------------------------------------------------------

# When called from JPRT the TESTDIRS variable is set to the jtreg tests to run
ifdef TESTDIRS
  TEST_SELECTION = $(TESTDIRS)
endif

ifdef CONCURRENCY
  EXTRA_JTREG_OPTIONS += -concurrency:$(CONCURRENCY)
endif

# Default JTREG to run
JTREG = $(JT_HOME)/bin/jtreg
# run in agentvm mode
JTREG_BASIC_OPTIONS += -agentvm
# Only run automatic tests
JTREG_BASIC_OPTIONS += -a
# Always turn on assertions
JTREG_ASSERT_OPTION = -ea -esa
JTREG_BASIC_OPTIONS += $(JTREG_ASSERT_OPTION)
# Report details on all failed or error tests, times too
JTREG_BASIC_OPTIONS += -v:fail,error,time
# Retain all files for failing tests
JTREG_BASIC_OPTIONS += -retain:fail,error
# Ignore tests are not run and completely silent about it
JTREG_IGNORE_OPTION = -ignore:quiet
JTREG_BASIC_OPTIONS += $(JTREG_IGNORE_OPTION)
# Multiple by 4 the timeout numbers
JTREG_TIMEOUT_OPTION =  -timeoutFactor:4
JTREG_BASIC_OPTIONS += $(JTREG_TIMEOUT_OPTION)
# Set the max memory for jtreg control vm
JTREG_MEMORY_OPTION = -J-Xmx512m
JTREG_BASIC_OPTIONS += $(JTREG_MEMORY_OPTION)
# Add any extra options
JTREG_BASIC_OPTIONS += $(EXTRA_JTREG_OPTIONS)
# Set other vm and test options
JTREG_TEST_OPTIONS = $(JAVA_ARGS:%=-javaoptions:%) $(JAVA_VM_ARGS:%=-vmoption:%)
# Set the GC options for test vms
#JTREG_GC_OPTION = -vmoption:-XX:+UseSerialGC
#JTREG_TEST_OPTIONS += $(JTREG_GC_OPTION)
# Set the max memory for jtreg target test vms
JTREG_TESTVM_MEMORY_OPTION = -vmoption:-Xmx512m
JTREG_TEST_OPTIONS += $(JTREG_TESTVM_MEMORY_OPTION)

# Make sure jtreg exists
$(JTREG): $(JT_HOME)

# Run jtreg
jtreg_tests: prep $(PRODUCT_HOME) $(JTREG)
	(                                                                    \
	  ( JT_HOME=$(shell $(GETMIXEDPATH) "$(JT_HOME)");                   \
            export JT_HOME;                                                  \
            $(shell $(GETMIXEDPATH) "$(JTREG)")                              \
              $(JTREG_BASIC_OPTIONS)                                         \
              -r:$(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)/JTreport")  \
              -w:$(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)/JTwork")    \
              -jdk:$(shell $(GETMIXEDPATH) "$(PRODUCT_HOME)")                \
              $(JTREG_EXCLUSIONS)                                            \
              $(JTREG_TEST_OPTIONS)                                          \
              $(TEST_SELECTION)                                                    \
	  ) ;                                                                \
	  $(BUNDLE_UP_AND_EXIT)                                              \
	) 2>&1 | $(TEE) $(ABS_TEST_OUTPUT_DIR)/output.txt ; $(TESTEXIT)

PHONY_LIST += jtreg_tests

################################################################

# Phony targets (e.g. these are not filenames)
.PHONY: all clean prep $(PHONY_LIST)

################################################################
