#
# Copyright (c) 1995, 2011, Oracle and/or its affiliates. All rights reserved.
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

# Get OS name from uname
UNAME_S := $(shell $(UNAME) -s)

# Commands to run on paths to make mixed paths for java on windows
GETMIXEDPATH=$(ECHO)

# Location of developer shared files
SLASH_JAVA = /java

# Platform specific settings
ifeq ($(UNAME_S), SunOS)
  OS_NAME     = solaris
  OS_ARCH    := $(shell $(UNAME) -p)
  OS_VERSION := $(shell $(UNAME) -r)
endif
ifeq ($(UNAME_S), Linux)
  OS_NAME     = linux
  OS_ARCH    := $(shell $(UNAME) -m)
  # Check for unknown arch, try uname -p if uname -m says unknown
  ifeq ($(OS_ARCH),unknown)
    OS_ARCH    := $(shell $(UNAME) -p)
  endif
  OS_VERSION := $(shell $(UNAME) -r)
endif
ifeq ($(OS_NAME),)
  OS_NAME = windows
  # GNU Make or MKS overrides $(PROCESSOR_ARCHITECTURE) to always
  # return "x86". Use the first word of $(PROCESSOR_IDENTIFIER) instead.
  ifeq ($(PROCESSOR_IDENTIFIER),)
    PROC_ARCH:=$(shell $(UNAME) -m)
  else
    PROC_ARCH:=$(word 1, $(PROCESSOR_IDENTIFIER))
  endif
  OS_ARCH:=$(PROC_ARCH)
  SLASH_JAVA = J:
  EXESUFFIX = .exe
  # These need to be different depending on MKS or CYGWIN
  ifeq ($(findstring cygdrive,$(shell ($(CD) C:/ && $(PWD)))), )
    GETMIXEDPATH  = dosname -s
    OS_VERSION   := $(shell $(UNAME) -r)
  else
    GETMIXEDPATH  = cygpath -m -s
    OS_VERSION   := $(shell $(UNAME) -s | $(CUT) -d'-' -f2)
  endif
endif

# Only want major and minor numbers from os version
OS_VERSION := $(shell $(ECHO) "$(OS_VERSION)" | $(CUT) -d'.' -f1,2)

# Name to use for x86_64 arch (historically amd64, but should change someday)
OS_ARCH_X64_NAME:=amd64
#OS_ARCH_X64_NAME:=x64

# Alternate arch names (in case this arch is known by a second name)
#   PROBLEM_LISTS may use either name.
OS_ARCH2-amd64:=x64
#OS_ARCH2-x64:=amd64

# Try and use the arch names consistently
OS_ARCH:=$(patsubst x64,$(OS_ARCH_X64_NAME),$(OS_ARCH))
OS_ARCH:=$(patsubst X64,$(OS_ARCH_X64_NAME),$(OS_ARCH))
OS_ARCH:=$(patsubst AMD64,$(OS_ARCH_X64_NAME),$(OS_ARCH))
OS_ARCH:=$(patsubst amd64,$(OS_ARCH_X64_NAME),$(OS_ARCH))
OS_ARCH:=$(patsubst x86_64,$(OS_ARCH_X64_NAME),$(OS_ARCH))
OS_ARCH:=$(patsubst 8664,$(OS_ARCH_X64_NAME),$(OS_ARCH))
OS_ARCH:=$(patsubst EM64T,$(OS_ARCH_X64_NAME),$(OS_ARCH))
OS_ARCH:=$(patsubst em64t,$(OS_ARCH_X64_NAME),$(OS_ARCH))
OS_ARCH:=$(patsubst intel64,$(OS_ARCH_X64_NAME),$(OS_ARCH))
OS_ARCH:=$(patsubst Intel64,$(OS_ARCH_X64_NAME),$(OS_ARCH))
OS_ARCH:=$(patsubst INTEL64,$(OS_ARCH_X64_NAME),$(OS_ARCH))
OS_ARCH:=$(patsubst IA64,ia64,$(OS_ARCH))
OS_ARCH:=$(patsubst X86,i586,$(OS_ARCH))
OS_ARCH:=$(patsubst x86,i586,$(OS_ARCH))
OS_ARCH:=$(patsubst i386,i586,$(OS_ARCH))
OS_ARCH:=$(patsubst i486,i586,$(OS_ARCH))
OS_ARCH:=$(patsubst i686,i586,$(OS_ARCH))
OS_ARCH:=$(patsubst 386,i586,$(OS_ARCH))
OS_ARCH:=$(patsubst 486,i586,$(OS_ARCH))
OS_ARCH:=$(patsubst 586,i586,$(OS_ARCH))
OS_ARCH:=$(patsubst 686,i586,$(OS_ARCH))

# Default  ARCH_DATA_MODEL settings
ARCH_DATA_MODEL-i586 = 32
ARCH_DATA_MODEL-$(OS_ARCH_X64_NAME) = 64
ARCH_DATA_MODEL-ia64 = 64
ARCH_DATA_MODEL-sparc = 32
ARCH_DATA_MODEL-sparcv9 = 64

# If ARCH_DATA_MODEL is not defined, try and pick a reasonable default
ifndef ARCH_DATA_MODEL
  ARCH_DATA_MODEL:=$(ARCH_DATA_MODEL-$(OS_ARCH))
endif
ifndef ARCH_DATA_MODEL
  ARCH_DATA_MODEL=32
endif

# Platform directory name
PLATFORM_OS = $(OS_NAME)-$(OS_ARCH)

# Check ARCH_DATA_MODEL, adjust OS_ARCH accordingly on solaris
ARCH_DATA_MODEL_ERROR= \
  ARCH_DATA_MODEL=$(ARCH_DATA_MODEL) cannot be used with $(PLATFORM_OS)
ifeq ($(ARCH_DATA_MODEL),64)
  ifeq ($(PLATFORM_OS),solaris-i586)
    OS_ARCH=$(OS_ARCH_X64_NAME)
  endif
  ifeq ($(PLATFORM_OS),solaris-sparc)
    OS_ARCH=sparcv9
  endif
  ifeq ($(OS_ARCH),i586)
    x:=$(warning "WARNING: $(ARCH_DATA_MODEL_ERROR)")
  endif
  ifeq ($(OS_ARCH),sparc)
    x:=$(warning "WARNING: $(ARCH_DATA_MODEL_ERROR)")
  endif
else
  ifeq ($(ARCH_DATA_MODEL),32)
    ifeq ($(OS_ARCH),$(OS_ARCH_X64_NAME))
      x:=$(warning "WARNING: $(ARCH_DATA_MODEL_ERROR)")
    endif
    ifeq ($(OS_ARCH),ia64)
      x:=$(warning "WARNING: $(ARCH_DATA_MODEL_ERROR)")
    endif
    ifeq ($(OS_ARCH),sparcv9)
      x:=$(warning "WARNING: $(ARCH_DATA_MODEL_ERROR)")
    endif
  else
    x:=$(warning "WARNING: $(ARCH_DATA_MODEL_ERROR)")
  endif
endif

# Alternate OS_ARCH name (defaults to OS_ARCH)
OS_ARCH2:=$(OS_ARCH2-$(OS_ARCH))
ifeq ($(OS_ARCH2),)
  OS_ARCH2:=$(OS_ARCH)
endif

# Root of this test area (important to use full paths in some places)
TEST_ROOT := $(shell $(PWD))

# Root of all test results
ifdef ALT_OUTPUTDIR
  ABS_OUTPUTDIR = $(ALT_OUTPUTDIR)
else
  ABS_OUTPUTDIR = $(TEST_ROOT)/../build/$(PLATFORM_OS)
endif
ABS_PLATFORM_BUILD_ROOT = $(ABS_OUTPUTDIR)
ABS_TEST_OUTPUT_DIR := $(ABS_PLATFORM_BUILD_ROOT)/testoutput/$(UNIQUE_DIR)

# Expect JPRT to set PRODUCT_HOME (the product or jdk in this case to test)
ifndef PRODUCT_HOME
  # Try to use j2sdk-image if it exists
  ABS_JDK_IMAGE = $(ABS_PLATFORM_BUILD_ROOT)/j2sdk-image
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

# Check JAVA_ARGS arguments based on ARCH_DATA_MODEL etc.
ifeq ($(OS_NAME),solaris)
  D64_ERROR_MESSAGE=Mismatch between ARCH_DATA_MODEL=$(ARCH_DATA_MODEL) and use of -d64 in JAVA_ARGS=$(JAVA_ARGS)
  ifeq ($(ARCH_DATA_MODEL),32)
    ifneq ($(findstring -d64,$(JAVA_ARGS)),)
      x:=$(warning "WARNING: $(D64_ERROR_MESSAGE)")
    endif
  endif
  ifeq ($(ARCH_DATA_MODEL),64)
    ifeq ($(findstring -d64,$(JAVA_ARGS)),)
      x:=$(warning "WARNING: $(D64_ERROR_MESSAGE)")
    endif
  endif
endif

# Macro to run make and set the shared library permissions
define SharedLibraryPermissions
$(MAKE) SHARED_LIBRARY_DIR=$1 UNIQUE_DIR=$@ shared_library_permissions
endef

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
SUMMARY_TXT = $(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)")/JTreport/text/summary.txt
STATS_TXT_NAME = Stats.txt
STATS_TXT = $(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)")/$(STATS_TXT_NAME)
RUNLIST   = $(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)")/runlist.txt
PASSLIST  = $(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)")/passlist.txt
FAILLIST  = $(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)")/faillist.txt
EXITCODE  = $(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)")/exitcode.txt

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
    exclc="`$(CAT) $(EXCLUDELIST) | $(WC) -l | $(AWK) '{print $$1;}'`"; \
    $(ECHO) "TEST STATS: name=$(UNIQUE_DIR)  run=$${runc}  pass=$${passc}  fail=$${failc}  excluded=$${exclc}" \
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
all: jtreg_tests
	@$(ECHO) "Testing completed successfully"

# Prep for output
prep: clean
	@$(MKDIR) -p $(ABS_TEST_OUTPUT_DIR)
	@$(MKDIR) -p `$(DIRNAME) $(ARCHIVE_BUNDLE)`

# Cleanup
clean:
	$(RM) -r $(ABS_TEST_OUTPUT_DIR)
	$(RM) $(ARCHIVE_BUNDLE)

################################################################

# jtreg tests

# Expect JT_HOME to be set for jtreg tests. (home for jtreg)
ifndef JT_HOME
  JT_HOME = $(SLASH_JAVA)/re/jtreg/4.0/promoted/latest/binaries/jtreg
  ifdef JPRT_JTREG_HOME
    JT_HOME = $(JPRT_JTREG_HOME)
  endif
endif

# Expect JPRT to set TESTDIRS to the jtreg test dirs
ifndef TESTDIRS
  TESTDIRS = demo
endif

# Samevm settings (default is false)
ifndef USE_JTREG_SAMEVM
  USE_JTREG_SAMEVM=false
endif
# With samevm, you cannot use -javaoptions?
ifeq ($(USE_JTREG_SAMEVM),true)
  JTREG_SAMEVM_OPTION = -samevm
  EXTRA_JTREG_OPTIONS += $(JTREG_SAMEVM_OPTION) $(JAVA_ARGS) $(JAVA_ARGS:%=-vmoption:%)
  JTREG_TEST_OPTIONS = $(JAVA_VM_ARGS:%=-vmoption:%)
else
  JTREG_TEST_OPTIONS = $(JAVA_ARGS:%=-javaoptions:%) $(JAVA_VM_ARGS:%=-vmoption:%)
endif

# Some tests annoy me and fail frequently
PROBLEM_LIST=ProblemList.txt
PROBLEM_LISTS=$(PROBLEM_LIST) $(wildcard closed/$(PROBLEM_LIST))
EXCLUDELIST=$(ABS_TEST_OUTPUT_DIR)/excludelist.txt

# Create exclude list for this platform and arch
ifdef NO_EXCLUDES
$(EXCLUDELIST): $(PROBLEM_LISTS) $(TEST_DEPENDENCIES)
	@$(ECHO) "NOTHING_EXCLUDED" > $@
else
$(EXCLUDELIST): $(PROBLEM_LISTS) $(TEST_DEPENDENCIES)
	@$(RM) $@ $@.temp1 $@.temp2
	@(($(CAT) $(PROBLEM_LISTS) | $(EGREP) -- '$(OS_NAME)-all'          ) ;\
	  ($(CAT) $(PROBLEM_LISTS) | $(EGREP) -- '$(PLATFORM_OS)'          ) ;\
	  ($(CAT) $(PROBLEM_LISTS) | $(EGREP) -- '$(OS_NAME)-$(OS_ARCH2)'  ) ;\
	  ($(CAT) $(PROBLEM_LISTS) | $(EGREP) -- '$(OS_NAME)-$(OS_VERSION)') ;\
	  ($(CAT) $(PROBLEM_LISTS) | $(EGREP) -- 'generic-$(OS_ARCH)'      ) ;\
	  ($(CAT) $(PROBLEM_LISTS) | $(EGREP) -- 'generic-$(OS_ARCH2)'     ) ;\
          ($(CAT) $(PROBLEM_LISTS) | $(EGREP) -- 'generic-all'             ) ;\
          ($(ECHO) "#") ;\
        ) | $(SED) -e 's@^[\ ]*@@' \
          | $(EGREP) -v '^#' > $@.temp1
	for tdir in $(TESTDIRS) SOLARIS_10_SH_BUG_NO_EMPTY_FORS ; do \
          ( ( $(CAT) $@.temp1 | $(EGREP) "^$${tdir}" ) ; $(ECHO) "#" ) >> $@.temp2 ; \
        done
	@$(ECHO) "# at least one line" >> $@.temp2
	@( $(EGREP) -v '^#' $@.temp2 ; true ) > $@
	@$(ECHO) "Excluding list contains `$(EXPAND) $@ | $(WC) -l` items"
endif

# Select list of directories that exist
define TestDirs
$(foreach i,$1,$(wildcard ${i})) $(foreach i,$1,$(wildcard closed/${i}))
endef
# Running batches of tests with or without samevm
define RunSamevmBatch
$(ECHO) "Running tests in samevm mode: $?"
$(MAKE) TEST_DEPENDENCIES="$?" TESTDIRS="$?" USE_JTREG_SAMEVM=true  UNIQUE_DIR=$@ jtreg_tests
endef
define RunOthervmBatch
$(ECHO) "Running tests in othervm mode: $?"
$(MAKE) TEST_DEPENDENCIES="$?" TESTDIRS="$?" USE_JTREG_SAMEVM=false UNIQUE_DIR=$@ jtreg_tests
endef
define SummaryInfo
$(ECHO) "########################################################"
$(CAT) $(?:%=$(ABS_TEST_OUTPUT_DIR)/%/$(STATS_TXT_NAME))
$(ECHO) "########################################################"
endef

# ------------------------------------------------------------------

# Batches of tests (somewhat arbitrary assigments to jdk_* targets)
JDK_ALL_TARGETS =

# Stable othervm testruns (minus items from PROBLEM_LIST)
#   Using samevm has problems, and doesn't help performance as much as others.
JDK_ALL_TARGETS += jdk_awt
jdk_awt: $(call TestDirs, com/sun/awt java/awt sun/awt)
	$(call RunOthervmBatch)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_beans1
jdk_beans1: $(call TestDirs, \
            java/beans/beancontext java/beans/PropertyChangeSupport \
            java/beans/Introspector java/beans/Performance \
            java/beans/VetoableChangeSupport java/beans/Statement)
	$(call RunSamevmBatch)

# Stable othervm testruns (minus items from PROBLEM_LIST)
#   Using samevm has serious problems with these tests
JDK_ALL_TARGETS += jdk_beans2
jdk_beans2: $(call TestDirs, \
            java/beans/Beans java/beans/EventHandler java/beans/XMLDecoder \
            java/beans/PropertyEditor)
	$(call RunOthervmBatch)

# Stable othervm testruns (minus items from PROBLEM_LIST)
#   Using samevm has serious problems with these tests
JDK_ALL_TARGETS += jdk_beans3
jdk_beans3: $(call TestDirs, java/beans/XMLEncoder)
	$(call RunOthervmBatch)

# All beans tests
jdk_beans: jdk_beans1 jdk_beans2 jdk_beans3
	@$(SummaryInfo)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_io
jdk_io: $(call TestDirs, java/io)
	$(call RunSamevmBatch)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_lang
jdk_lang: $(call TestDirs, java/lang)
	$(call RunSamevmBatch)

# Stable othervm testruns (minus items from PROBLEM_LIST)
#   Using samevm has serious problems with these tests
JDK_ALL_TARGETS += jdk_management1
jdk_management1: $(call TestDirs, javax/management)
	$(call RunOthervmBatch)

# Stable othervm testruns (minus items from PROBLEM_LIST)
#   Using samevm has serious problems with these tests
JDK_ALL_TARGETS += jdk_management2
jdk_management2: $(call TestDirs, com/sun/jmx com/sun/management sun/management)
	$(call RunOthervmBatch)

# All management tests
jdk_management: jdk_management1 jdk_management2
	@$(SummaryInfo)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_math
jdk_math: $(call TestDirs, java/math)
	$(call RunSamevmBatch)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_misc
jdk_misc: $(call TestDirs, \
          demo javax/imageio javax/naming javax/print javax/script \
          javax/smartcardio javax/sound com/sun/java com/sun/jndi \
	  com/sun/org com/sun/xml sun/misc sun/pisces)
	$(call RunSamevmBatch)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_net
jdk_net: $(call TestDirs, com/sun/net java/net sun/net)
	$(call RunSamevmBatch)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_nio1
jdk_nio1: $(call TestDirs, java/nio/file)
	$(call RunSamevmBatch)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_nio2
jdk_nio2: $(call TestDirs, java/nio/Buffer java/nio/ByteOrder \
          java/nio/channels java/nio/MappedByteBuffer)
	$(call SharedLibraryPermissions,java/nio/channels)
	$(call RunSamevmBatch)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_nio3
jdk_nio3: $(call TestDirs, com/sun/nio sun/nio)
	$(call RunSamevmBatch)

# All nio tests
jdk_nio: jdk_nio1 jdk_nio2 jdk_nio3
	@$(SummaryInfo)

# Stable othervm testruns (minus items from PROBLEM_LIST)
#   Using samevm has serious problems with these tests
JDK_ALL_TARGETS += jdk_rmi
jdk_rmi: $(call TestDirs, java/rmi javax/rmi sun/rmi)
	$(call RunOthervmBatch)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_security1
jdk_security1: $(call TestDirs, java/security)
	$(call RunSamevmBatch)

# Stable othervm testruns (minus items from PROBLEM_LIST)
#   Using samevm has serious problems with these tests
JDK_ALL_TARGETS += jdk_security2
jdk_security2: $(call TestDirs, javax/crypto com/sun/crypto)
	$(call RunOthervmBatch)

# Stable othervm testruns (minus items from PROBLEM_LIST)
#   Using samevm has serious problems with these tests
JDK_ALL_TARGETS += jdk_security3
jdk_security3: $(call TestDirs, com/sun/security lib/security \
               javax/security sun/security)
	$(call SharedLibraryPermissions,sun/security)
	$(call RunOthervmBatch)

# All security tests
jdk_security: jdk_security1 jdk_security2 jdk_security3
	@$(SummaryInfo)

# Stable othervm testruns (minus items from PROBLEM_LIST)
#   Using samevm has problems, and doesn't help performance as much as others.
JDK_ALL_TARGETS += jdk_swing
jdk_swing: $(call TestDirs, javax/swing sun/java2d)
	$(call RunOthervmBatch)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_text
jdk_text: $(call TestDirs, java/text sun/text)
	$(call RunSamevmBatch)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_tools1
jdk_tools1: $(call TestDirs, com/sun/jdi)
	$(call RunSamevmBatch)

# Stable othervm testruns (minus items from PROBLEM_LIST)
#   Using samevm has serious problems with these tests
JDK_ALL_TARGETS += jdk_tools2
jdk_tools2: $(call TestDirs, \
            com/sun/tools sun/jvmstat sun/tools tools vm \
            com/sun/servicetag com/sun/tracing)
	$(call SharedLibraryPermissions,tools/launcher)
	$(call RunSamevmBatch)

# All tools tests
jdk_tools: jdk_tools1 jdk_tools2
	@$(SummaryInfo)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_util
jdk_util: $(call TestDirs, java/util sun/util)
	$(call RunSamevmBatch)

# ------------------------------------------------------------------

# Run all tests
FILTER_OUT_LIST=jdk_awt jdk_rmi jdk_swing
JDK_ALL_STABLE_TARGETS := $(filter-out $(FILTER_OUT_LIST), $(JDK_ALL_TARGETS))
jdk_all: $(JDK_ALL_STABLE_TARGETS)
	@$(SummaryInfo)

# These are all phony targets
PHONY_LIST += $(JDK_ALL_TARGETS)

# ------------------------------------------------------------------

# Default JTREG to run (win32 script works for everybody)
JTREG = $(JT_HOME)/win32/bin/jtreg
# Add any extra options (samevm etc.)
JTREG_BASIC_OPTIONS += $(EXTRA_JTREG_OPTIONS)
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
# Boost the max memory for jtreg to avoid gc thrashing
JTREG_MEMORY_OPTION = -J-Xmx512m
JTREG_BASIC_OPTIONS += $(JTREG_MEMORY_OPTION)

# Make sure jtreg exists
$(JTREG): $(JT_HOME)

# Run jtreg
jtreg_tests: prep $(PRODUCT_HOME) $(JTREG) $(EXCLUDELIST)
	@$(EXPAND) $(EXCLUDELIST) \
            | $(CUT) -d' ' -f1 \
            | $(SED) -e 's@^@Excluding: @'
	(                                                                    \
	  ( JT_HOME=$(shell $(GETMIXEDPATH) "$(JT_HOME)");                   \
            export JT_HOME;                                                  \
            $(shell $(GETMIXEDPATH) "$(JTREG)")                              \
              $(JTREG_BASIC_OPTIONS)                                         \
              -r:$(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)")/JTreport  \
              -w:$(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)")/JTwork    \
              -jdk:$(shell $(GETMIXEDPATH) "$(PRODUCT_HOME)")                \
              -exclude:$(shell $(GETMIXEDPATH) "$(EXCLUDELIST)")             \
              $(JTREG_TEST_OPTIONS)                                          \
              $(TESTDIRS)                                                    \
	  ) ; $(BUNDLE_UP_AND_EXIT)                                          \
	) 2>&1 | $(TEE) $(ABS_TEST_OUTPUT_DIR)/output.txt ; $(TESTEXIT)

# Rule that may change execute permissions on shared library files.
#  Files in repositories should never have execute permissions, but there
#  are some tests that have pre-built shared libraries, and these windows
#  dll files must have execute permission. Adding execute permission
#  may happen automatically on windows when using certain versions of mercurial
#  but it cannot be guaranteed. And blindly adding execute permission might
#  be seen as a mercurial 'change', so we avoid adding execute permission to
#  repository files. But testing from a plain source tree needs the chmod a+rx.
#  Used on select directories and applying the chmod to all shared libraries
#  not just dll files. On windows, this may not work with MKS if the files
#  were installed with CYGWIN unzip or untar (MKS chmod may not do anything).
#  And with CYGWIN and sshd service, you may need CYGWIN=ntsec for this to work.
#
shared_library_permissions: $(SHARED_LIBRARY_DIR)
	if [ ! -d $(TEST_ROOT)/../.hg ] ; then                          \
	  $(FIND) $< \( -name \*.dll -o -name \*.DLL -o -name \*.so \)  \
	        -exec $(CHMOD) a+rx {} \; ;                             \
        fi

PHONY_LIST += jtreg_tests shared_library_permissions

################################################################

# packtest

# Expect JPRT to set JPRT_PACKTEST_HOME.
PACKTEST_HOME = /net/jprt-web.sfbay.sun.com/jprt/allproducts/packtest
ifdef JPRT_PACKTEST_HOME
  PACKTEST_HOME = $(JPRT_PACKTEST_HOME)
endif

packtest: prep $(PACKTEST_HOME)/ptest $(PRODUCT_HOME)
	( $(CD) $(PACKTEST_HOME) &&            \
	    $(PACKTEST_HOME)/ptest             \
		 -t "$(PRODUCT_HOME)"          \
	         $(PACKTEST_STRESS_OPTION)     \
		 $(EXTRA_PACKTEST_OPTIONS)     \
		 -W $(ABS_TEST_OUTPUT_DIR)     \
                 $(JAVA_ARGS:%=-J %)           \
                 $(JAVA_VM_ARGS:%=-J %)        \
	 ) ; $(BUNDLE_UP_AND_EXIT)

packtest_stress: PACKTEST_STRESS_OPTION=-s
packtest_stress: packtest

PHONY_LIST += packtest packtest_stress

################################################################

# perftest to collect statistics

# Expect JPRT to set JPRT_PACKTEST_HOME.
PERFTEST_HOME = $(TEST_ROOT)/perf
ifdef JPRT_PERFTEST_HOME
  PERFTEST_HOME = $(JPRT_PERFTEST_HOME)
endif

perftest: ( $(PERFTEST_HOME)/perftest          \
                 -t $(shell $(GETMIXEDPATH) "$(PRODUCT_HOME)")               \
                 -w $(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)") \
                 -h $(PERFTEST_HOME) \
	 ) ; $(BUNDLE_UP_AND_EXIT)


PHONY_LIST += perftest

################################################################

# vmsqe tests

# Expect JPRT to set JPRT_VMSQE_HOME.
VMSQE_HOME = $(SLASH_JAVA)/sqe/comp/vm/testbase/sqe/vm/current/build/latest/vm
ifdef JPRT_VMSQE_HOME
  VMSQE_HOME = $(JPRT_VMSQE_HOME)
endif

# Expect JPRT to set JPRT_RUNVMSQE_HOME.
RUNVMSQE_HOME = /net/jprt-web.sfbay.sun.com/jprt/allproducts/runvmsqe
ifdef JPRT_RUNVMSQE_HOME
  RUNVMSQE_HOME = $(JPRT_RUNVMSQE_HOME)
endif

# Expect JPRT to set JPRT_TONGA3_HOME.
TONGA3_HOME = $(SLASH_JAVA)/sqe/tools/gtee/harness/tonga
ifdef JPRT_TONGA3_HOME
  TONGA3_HOME = $(JPRT_TONGA3_HOME)
endif

RUNVMSQE_BIN = $(RUNVMSQE_HOME)/bin/runvmsqe

vmsqe_tests: prep $(VMSQE_HOME)/vm $(TONGA3_HOME) $(RUNVMSQE_BIN) $(PRODUCT_HOME)
	$(RM) -r $(ABS_TEST_OUTPUT_DIR)/vmsqe
	( $(CD) $(ABS_TEST_OUTPUT_DIR) &&          \
	    $(RUNVMSQE_BIN)                        \
		 -jdk "$(PRODUCT_HOME)"            \
		 -o "$(ABS_TEST_OUTPUT_DIR)/vmsqe" \
		 -testbase "$(VMSQE_HOME)/vm"      \
		 -tonga "$(TONGA3_HOME)"           \
		 -tongajdk "$(ALT_BOOTDIR)"        \
                 $(JAVA_ARGS)                      \
                 $(JAVA_VM_ARGS)                   \
	         $(RUNVMSQE_TEST_OPTION)           \
		 $(EXTRA_RUNVMSQE_OPTIONS)         \
	 ) ; $(BUNDLE_UP_AND_EXIT)

vmsqe_jdwp: RUNVMSQE_TEST_OPTION=-jdwp
vmsqe_jdwp: vmsqe_tests

vmsqe_jdi: RUNVMSQE_TEST_OPTION=-jdi
vmsqe_jdi: vmsqe_tests

vmsqe_jdb: RUNVMSQE_TEST_OPTION=-jdb
vmsqe_jdb: vmsqe_tests

vmsqe_quick-jdi: RUNVMSQE_TEST_OPTION=-quick-jdi
vmsqe_quick-jdi: vmsqe_tests

vmsqe_sajdi: RUNVMSQE_TEST_OPTION=-sajdi
vmsqe_sajdi: vmsqe_tests

vmsqe_jvmti: RUNVMSQE_TEST_OPTION=-jvmti
vmsqe_jvmti: vmsqe_tests

vmsqe_hprof: RUNVMSQE_TEST_OPTION=-hprof
vmsqe_hprof: vmsqe_tests

vmsqe_monitoring: RUNVMSQE_TEST_OPTION=-monitoring
vmsqe_monitoring: vmsqe_tests

PHONY_LIST += vmsqe_jdwp vmsqe_jdi vmsqe_jdb vmsqe_quick-jdi vmsqe_sajdi \
              vmsqe_jvmti vmsqe_hprof vmsqe_monitoring vmsqe_tests

################################################################

# jck tests

# Default is to use jck 7 from /java/re
JCK7_DEFAULT_HOME = $(SLASH_JAVA)/re/jck/7/promoted/latest/binaries

# Expect JPRT to set JPRT_JCK7COMPILER_HOME.
JCK7COMPILER_HOME = $(JCK7_DEFAULT_HOME)/JCK-compiler-7
ifdef JPRT_JCK7COMPILER_HOME
  JCK7COMPILER_HOME = $(JPRT_JCK7COMPILER_HOME)/JCK-compiler-7
endif

# Expect JPRT to set JPRT_JCK7RUNTIME_HOME.
JCK7RUNTIME_HOME = $(JCK7_DEFAULT_HOME)/JCK-runtime-7
ifdef JPRT_JCK7RUNTIME_HOME
  JCK7RUNTIME_HOME = $(JPRT_JCK7RUNTIME_HOME)/JCK-runtime-7
endif

# Expect JPRT to set JPRT_JCK7DEVTOOLS_HOME.
JCK7DEVTOOLS_HOME = $(JCK7_DEFAULT_HOME)/JCK-devtools-7
ifdef JPRT_JCK7DEVTOOLS_HOME
  JCK7DEVTOOLS_HOME = $(JPRT_JCK7DEVTOOLS_HOME)/JCK-devtools-7
endif

# The jtjck.jar utility to use to run the tests
JTJCK_JAR = $(JCK_HOME)/lib/jtjck.jar
JTJCK_JAVA_ARGS =  -XX:MaxPermSize=256m -Xmx512m
JTJCK_OPTIONS = -headless -v 

# Default tests to run
ifndef JCK_COMPILER_TESTS
  JCK_COMPILER_TESTS = 
endif
ifndef JCK_RUNTIME_TESTS
  JCK_RUNTIME_TESTS  = 
endif
ifndef JCK_DEVTOOLS_TESTS
  JCK_DEVTOOLS_TESTS = 
endif

# Generic rule used to run jck tests
_generic_jck_tests: prep $(PRODUCT_HOME) $(EXCLUDELIST)
	@$(EXPAND) $(EXCLUDELIST) \
            | $(CUT) -d' ' -f1 \
            | $(SED) -e 's@^@Excluding: @'
	( $(CD) $(ABS_TEST_OUTPUT_DIR) &&          			   \
	  $(PRODUCT_HOME)/bin/java $(JTJCK_JAVA_ARGS) 			   \
	    -jar "$(JTJCK_JAR)" 					   \
	    $(JTJCK_OPTIONS) 						   \
            -r:$(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)")/JTreport  \
            -w:$(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)")/JTwork    \
            -jdk:$(shell $(GETMIXEDPATH) "$(PRODUCT_HOME)")                \
	    $(TESTDIRS) 						   \
        ) ; $(BUNDLE_UP_AND_EXIT)

# JCK7 compiler tests
jck7compiler:
	$(MAKE) UNIQUE_DIR=$@ \
	        JCK_HOME=$(JCK7COMPILER_HOME) \
	        TESTDIRS="$(JCK_COMPILER_TESTS)" \
                _generic_jck_tests

# JCK7 runtime tests
jck7runtime: 
	$(MAKE) UNIQUE_DIR=$@ \
	        JCK_HOME=$(JCK7RUNTIME_HOME) \
	        TESTDIRS="$(JCK_RUNTIME_TESTS)" \
                _generic_jck_tests

# JCK7 devtools tests
jck7devtools: 
	$(MAKE) UNIQUE_DIR=$@ \
	        JCK_HOME=$(JCK7DEVTOOLS_HOME) \
                TESTDIRS="$(JCK_DEVTOOLS_TESTS)" \
                _generic_jck_tests

# Run all 3 sets of JCK7 tests
jck_all: jck7runtime jck7devtools jck7compiler

PHONY_LIST += jck_all _generic_jck_tests \
	      jck7compiler jck7runtime jck7devtools

################################################################

# Phony targets (e.g. these are not filenames)
.PHONY: all clean prep $(PHONY_LIST)

################################################################

