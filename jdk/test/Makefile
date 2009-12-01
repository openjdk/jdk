#
# Copyright 1995-2009 Sun Microsystems, Inc.  All Rights Reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Sun designates this
# particular file as subject to the "Classpath" exception as provided
# by Sun in the LICENSE file that accompanied this code.
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
ECHO      = echo
EGREP     = egrep
EXPAND    = expand
EXPR      = expr
KILL      = /usr/bin/kill
MKDIR     = mkdir
NOHUP     = nohup
PWD       = pwd
SED       = sed
SLEEP     = sleep
SORT      = sort
TEE       = tee
UNAME     = uname
UNIQ      = uniq
WC        = wc
XHOST     = xhost
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
  OS_VERSION := $(shell $(UNAME) -r)
endif
ifndef OS_NAME
  ifneq ($(PROCESSOR_IDENTIFIER), )
    OS_NAME = windows
    SLASH_JAVA = J:
    # A variety of ways to say X64 arch :^(
    OS_ARCH:=$(word 1, $(PROCESSOR_IDENTIFIER))
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
endif

# Only want major and minor numbers from os version
OS_VERSION := $(shell $(ECHO) "$(OS_VERSION)" | $(CUT) -d'.' -f1,2)

# Try and use names i586, x64, and ia64 consistently  
OS_ARCH:=$(subst X64,x64,$(OS_ARCH))
OS_ARCH:=$(subst AMD64,x64,$(OS_ARCH))
OS_ARCH:=$(subst amd64,x64,$(OS_ARCH))
OS_ARCH:=$(subst x86_64,x64,$(OS_ARCH))
OS_ARCH:=$(subst EM64T,x64,$(OS_ARCH))
OS_ARCH:=$(subst em64t,x64,$(OS_ARCH))
OS_ARCH:=$(subst intel64,x64,$(OS_ARCH))
OS_ARCH:=$(subst Intel64,x64,$(OS_ARCH))
OS_ARCH:=$(subst INTEL64,x64,$(OS_ARCH))
OS_ARCH:=$(subst IA64,ia64,$(OS_ARCH))
OS_ARCH:=$(subst X86,i586,$(OS_ARCH))
OS_ARCH:=$(subst x86,i586,$(OS_ARCH))
OS_ARCH:=$(subst i386,i586,$(OS_ARCH))
OS_ARCH:=$(subst i486,i586,$(OS_ARCH))
OS_ARCH:=$(subst i686,i586,$(OS_ARCH))

# Check for ARCH_DATA_MODEL, adjust OS_ARCH accordingly
ifndef ARCH_DATA_MODEL
  ARCH_DATA_MODEL=32
endif
ARCH_DATA_MODEL_ERROR= \
  ARCH_DATA_MODEL=$(ARCH_DATA_MODEL) cannot be used with $(OS_NAME)-$(ARCH)
ifeq ($(ARCH_DATA_MODEL),64)
  ifeq ($(OS_NAME)-$(OS_ARCH),solaris-i586)
    OS_ARCH=x64
  endif
  ifeq ($(OS_NAME)-$(OS_ARCH),solaris-sparc)
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
    ifeq ($(OS_ARCH),x64)
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

# Root of this test area (important to use full paths in some places)
TEST_ROOT := $(shell $(PWD))

# Root of all test results
ifdef ALT_OUTPUTDIR
  ABS_OUTPUTDIR = $(ALT_OUTPUTDIR)
else
  ABS_OUTPUTDIR = $(TEST_ROOT)/../build/$(OS_NAME)-$(OS_ARCH)
endif
ABS_BUILD_ROOT = $(ABS_OUTPUTDIR)
ABS_TEST_OUTPUT_DIR := $(ABS_BUILD_ROOT)/testoutput/$(UNIQUE_DIR)

# Expect JPRT to set PRODUCT_HOME (the product or jdk in this case to test)
ifndef PRODUCT_HOME
  # Try to use j2sdk-image if it exists
  ABS_JDK_IMAGE = $(ABS_BUILD_ROOT)/j2sdk-image
  PRODUCT_HOME :=                       \
    $(shell                             \
      if [ -d $(ABS_JDK_IMAGE) ] ; then \
         $(ECHO) "$(ABS_JDK_IMAGE)";    \
       else                             \
         $(ECHO) "$(ABS_BUILD_ROOT)";   \
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

# Expect JPRT to set JPRT_ARCHIVE_BUNDLE (path to zip bundle for results)
ARCHIVE_BUNDLE = $(ABS_TEST_OUTPUT_DIR)/ARCHIVE_BUNDLE.zip
ifdef JPRT_ARCHIVE_BUNDLE
  ARCHIVE_BUNDLE = $(JPRT_ARCHIVE_BUNDLE)
endif

# DISPLAY settings for virtual frame buffer
START_XVFB = start-Xvfb.sh
NOHUP_OUTPUT = $(ABS_TEST_OUTPUT_DIR)/start-Xvfb.nohup-output.txt
DISPLAY_PID_FILE=$(ABS_TEST_OUTPUT_DIR)/xvfb-display-number.txt
DISPLAY_SLEEP_TIME=10
DISPLAY_MAX_SLEEPS=10
ifeq ($(OS_NAME),solaris)
  VIRTUAL_FRAME_BUFFER = true
endif
ifeq ($(OS_NAME),linux)
  VIRTUAL_FRAME_BUFFER = true
endif

# Does not work yet, display dies as soon as it gets used. :^(
VIRTUAL_FRAME_BUFFER = false

# Are we using a VIRTUAL_FRAME_BUFFER (Xvfb)
ifeq ($(VIRTUAL_FRAME_BUFFER),true)
  
  PREP_DISPLAY = \
    $(CP) $(START_XVFB) $(ABS_TEST_OUTPUT_DIR); \
    $(CHMOD) a+x $(ABS_TEST_OUTPUT_DIR)/$(START_XVFB); \
    ( $(CD) $(ABS_TEST_OUTPUT_DIR) && \
      $(NOHUP) $(ABS_TEST_OUTPUT_DIR)/$(START_XVFB) $(DISPLAY_PID_FILE) > $(NOHUP_OUTPUT) 2>&1 && \
      $(SLEEP) $(DISPLAY_SLEEP_TIME) ) & \
    count=1; \
    while [ ! -s $(DISPLAY_PID_FILE) ] ; do \
      $(ECHO) "Sleeping $(DISPLAY_SLEEP_TIME) more seconds, DISPLAY not ready"; \
      $(SLEEP) $(DISPLAY_SLEEP_TIME); \
      count=`$(EXPR) $${count} '+' 1`; \
      if [ $${count} -gt $(DISPLAY_MAX_SLEEPS) ] ; then \
        $(ECHO) "ERROR: DISPLAY not ready, giving up on DISPLAY"; \
        exit 9; \
      fi; \
    done ; \
    DISPLAY=":`$(CAT) $(DISPLAY_PID_FILE)`"; \
    export DISPLAY; \
    $(CAT) $(NOHUP_OUTPUT); \
    $(ECHO) "Prepared DISPLAY=$${DISPLAY}"; \
    $(XHOST) || \
    ( $(ECHO) "ERROR: No display" ; exit 8)

  KILL_DISPLAY = \
    ( \
      DISPLAY=":`$(CAT) $(DISPLAY_PID_FILE)`"; \
      export DISPLAY; \
      if [ -s "$(DISPLAY_PID_FILE)" ] ; then \
        $(KILL) `$(CAT) $(DISPLAY_PID_FILE)` > /dev/null 2>&1; \
        $(KILL) -9 `$(CAT) $(DISPLAY_PID_FILE)` > /dev/null 2>&1; \
      fi; \
      $(ECHO) "Killed DISPLAY=$${DISPLAY}"; \
    )

else
  
  PREP_DISPLAY = $(ECHO) "VIRTUAL_FRAME_BUFFER=$(VIRTUAL_FRAME_BUFFER)"
  KILL_DISPLAY = $(ECHO) "VIRTUAL_FRAME_BUFFER=$(VIRTUAL_FRAME_BUFFER)"

endif

# How to create the test bundle (pass or fail, we want to create this)
#   Follow command with ";$(BUNDLE_UP_AND_EXIT)", so it always gets executed.
ZIP_UP_RESULTS = ( $(MKDIR) -p `dirname $(ARCHIVE_BUNDLE)`     \
	           && $(CD) $(ABS_TEST_OUTPUT_DIR)             \
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
  exit ${testExitCode}

BUNDLE_UP_AND_EXIT = \
( \
  jtregExitCode=$$? && \
  _summary="$(SUMMARY_TXT)"; \
  $(RM) -f $(STATS_TXT) $(RUNLIST) $(PASSLIST) $(FAILLIST) $(EXITCODE); \
  $(ECHO) "$${jtregExitCode}" > $(EXITCODE); \
  if [ -r "$${_summary}" ] ; then \
    $(ECHO) "Summary: $${_summary}" > $(STATS_TXT); \
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
    $(ECHO) "TEST STATS: run=$${runc}  pass=$${passc}  fail=$${failc}  excluded=$${exclc}" \
      >> $(STATS_TXT); \
  else \
    $(ECHO) "Missing file: $${_summary}" >> $(STATS_TXT); \
  fi; \
  $(CAT) $(STATS_TXT); \
  $(ZIP_UP_RESULTS) && $(KILL_DISPLAY) ; \
  $(TESTEXIT) \
)

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
  EXTRA_JTREG_OPTIONS += -samevm $(JAVA_ARGS) $(JAVA_ARGS:%=-vmoption:%)
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
$(EXCLUDELIST): $(PROBLEM_LISTS) $(TESTDIRS)
	@$(ECHO) "NOTHING_EXCLUDED" > $@
else
$(EXCLUDELIST): $(PROBLEM_LISTS) $(TESTDIRS)
	@$(RM) $@ $@.temp1 $@.temp2
	@(($(CAT) $(PROBLEM_LISTS) | $(EGREP) -- '$(OS_NAME)-all'          ) ;\
	  ($(CAT) $(PROBLEM_LISTS) | $(EGREP) -- '$(OS_NAME)-$(OS_ARCH)'   ) ;\
	  ($(CAT) $(PROBLEM_LISTS) | $(EGREP) -- '$(OS_NAME)-$(OS_VERSION)') ;\
	  ($(CAT) $(PROBLEM_LISTS) | $(EGREP) -- 'generic-$(OS_ARCH)'      ) ;\
          ($(CAT) $(PROBLEM_LISTS) | $(EGREP) -- 'generic-all'             ) ;\
          ($(ECHO) "#") ;\
        ) | $(SED) -e 's@^[\ ]*@@' \
          | $(EGREP) -v '^#' > $@.temp1
	@for tdir in $(TESTDIRS) ; do \
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
$(ECHO) "Running tests in samevm mode: $(call TestDirs, $?)"
$(MAKE) TESTDIRS="$(call TestDirs, $?)" USE_JTREG_SAMEVM=true  UNIQUE_DIR=$@ jtreg_tests
endef
define RunOthervmBatch
$(ECHO) "Running tests in othervm mode: $(call TestDirs, $?)"
$(MAKE) TESTDIRS="$(call TestDirs, $?)" USE_JTREG_SAMEVM=false UNIQUE_DIR=$@ jtreg_tests
endef
define SummaryInfo
$(ECHO) "Summary for: $?"
$(CAT) $(?:%=$(ABS_TEST_OUTPUT_DIR)/%/$(STATS_TXT_NAME))
endef

# ------------------------------------------------------------------

# Batches of tests (somewhat arbitrary assigments to jdk_* targets)
JDK_ALL_TARGETS =

# Stable othervm testruns (minus items from PROBLEM_LIST)
#   Using samevm has problems, and doesn't help performance as much as others.
JDK_ALL_TARGETS += jdk_awt
jdk_awt: com/sun/awt java/awt sun/awt
	$(call RunOthervmBatch)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_beans1
jdk_beans1: java/beans/beancontext java/beans/PropertyChangeSupport \
            java/beans/Introspector java/beans/Performance \
            java/beans/VetoableChangeSupport java/beans/Statement
	$(call RunSamevmBatch)

# Stable othervm testruns (minus items from PROBLEM_LIST)
#   Using samevm has serious problems with these tests
JDK_ALL_TARGETS += jdk_beans2
jdk_beans2: java/beans/Beans java/beans/EventHandler java/beans/XMLDecoder \
            java/beans/PropertyEditor
	$(call RunOthervmBatch)
JDK_ALL_TARGETS += jdk_beans3
jdk_beans3: java/beans/XMLEncoder
	$(call RunOthervmBatch)

jdk_beans: jdk_beans1 jdk_beans2 jdk_beans3
	@$(SummaryInfo)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_io
jdk_io: java/io
	$(call RunSamevmBatch)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_lang
jdk_lang: java/lang
	$(call RunSamevmBatch)

# Stable othervm testruns (minus items from PROBLEM_LIST)
#   Using samevm has serious problems with these tests
JDK_ALL_TARGETS += jdk_management1
jdk_management1: javax/management
	$(call RunOthervmBatch)

# Stable othervm testruns (minus items from PROBLEM_LIST)
#   Using samevm has serious problems with these tests
JDK_ALL_TARGETS += jdk_management2
jdk_management2: com/sun/jmx com/sun/management sun/management
	$(call RunOthervmBatch)

jdk_management: jdk_management1 jdk_management2
	@$(SummaryInfo)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_math
jdk_math: java/math
	$(call RunSamevmBatch)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_misc
jdk_misc: demo javax/imageio javax/naming javax/print javax/script \
          javax/smartcardio javax/sound com/sun/java com/sun/jndi \
	  com/sun/org sun/misc sun/pisces
	$(call RunSamevmBatch)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_net
jdk_net: com/sun/net java/net sun/net
	$(call RunSamevmBatch)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_nio1
jdk_nio1: java/nio/file
	$(call RunSamevmBatch)

# Stable othervm testruns (minus items from PROBLEM_LIST)
#   Using samevm has serious problems with these tests
JDK_ALL_TARGETS += jdk_nio2
jdk_nio2: java/nio/Buffer java/nio/ByteOrder \
          java/nio/channels java/nio/BufferPoolMXBean java/nio/MappedByteBuffer
	$(call RunOthervmBatch)
JDK_ALL_TARGETS += jdk_nio3
jdk_nio3: com/sun/nio sun/nio
	$(call RunOthervmBatch)

jdk_nio: jdk_nio1 jdk_nio2 jdk_nio3
	@$(SummaryInfo)

# Stable othervm testruns (minus items from PROBLEM_LIST)
#   Using samevm has serious problems with these tests
JDK_ALL_TARGETS += jdk_rmi
jdk_rmi: java/rmi javax/rmi sun/rmi
	$(call RunOthervmBatch)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_security1
jdk_security1: java/security
	$(call RunSamevmBatch)

# Stable othervm testruns (minus items from PROBLEM_LIST)
#   Using samevm has serious problems with these tests
JDK_ALL_TARGETS += jdk_security2
jdk_security2: javax/crypto com/sun/crypto
	$(call RunOthervmBatch)
JDK_ALL_TARGETS += jdk_security3
jdk_security3: com/sun/security lib/security javax/security sun/security
	$(call RunOthervmBatch)

jdk_security: jdk_security1 jdk_security2 jdk_security3
	@$(SummaryInfo)

# Stable othervm testruns (minus items from PROBLEM_LIST)
#   Using samevm has problems, and doesn't help performance as much as others.
JDK_ALL_TARGETS += jdk_swing
jdk_swing: javax/swing sun/java2d
	$(call RunOthervmBatch)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_text
jdk_text: java/text sun/text
	$(call RunSamevmBatch)

# Stable othervm testruns (minus items from PROBLEM_LIST)
#   Using samevm has serious problems with these tests
JDK_ALL_TARGETS += jdk_tools1
jdk_tools1: com/sun/jdi
	$(call RunSamevmBatch)
JDK_ALL_TARGETS += jdk_tools2
jdk_tools2: com/sun/tools sun/jvmstat sun/tools tools vm com/sun/servicetag com/sun/tracing
	$(call RunOthervmBatch)

jdk_tools: jdk_tools1 jdk_tools2
	@$(SummaryInfo)

# Stable samevm testruns (minus items from PROBLEM_LIST)
JDK_ALL_TARGETS += jdk_util
jdk_util: java/util sun/util
	$(call RunSamevmBatch)

# ------------------------------------------------------------------

# Run all tests
jdk_all: $(filter-out jdk_awt jdk_rmi jdk_swing, $(JDK_ALL_TARGETS))
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
# Report details on all failed or error tests, times too
JTREG_BASIC_OPTIONS += -v:fail,error,time
# Retain all files for failing tests
JTREG_BASIC_OPTIONS += -retain:fail,error
# Ignore tests are not run and completely silent about it
JTREG_BASIC_OPTIONS += -ignore:quiet
# Multiple by 2 the timeout numbers
JTREG_BASIC_OPTIONS += -timeoutFactor:2
# Boost the max memory for jtreg to avoid gc thrashing
JTREG_BASIC_OPTIONS += -J-Xmx512m

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
	    $(PREP_DISPLAY) &&                                               \
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

PHONY_LIST += jtreg_tests

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
PERFTEST_HOME = ${TEST_ROOT}/perf
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
VMSQE_HOME = /java/sqe/comp/vm/testbase/sqe/vm/current/build/latest/vm
ifdef JPRT_VMSQE_HOME
  VMSQE_HOME = $(JPRT_VMSQE_HOME)
endif

# Expect JPRT to set JPRT_RUNVMSQE_HOME.
RUNVMSQE_HOME = /net/jprt-web.sfbay.sun.com/jprt/allproducts/runvmsqe
ifdef JPRT_RUNVMSQE_HOME
  RUNVMSQE_HOME = $(JPRT_RUNVMSQE_HOME)
endif

# Expect JPRT to set JPRT_TONGA3_HOME.
TONGA3_HOME = /java/sqe//tools/gtee/harness/tonga
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

JCK_WORK_DIR     = $(ABS_TEST_OUTPUT_DIR)/JCKwork
JCK_REPORT_DIR   = $(ABS_TEST_OUTPUT_DIR)/JCKreport
JCK_PROPERTIES   = $(ABS_TEST_OUTPUT_DIR)/jck.properties
JCK_CONFIG       = $(ABS_TEST_OUTPUT_DIR)/jck.config

JCK_JAVA_EXE     = $(PRODUCT_HOME)/bin/java$(EXESUFFIX)

JCK_JAVATEST_JAR = $(JCK_HOME)/lib/javatest.jar
JCK_JAVATEST     = $(ALT_BOOTDIR)/bin/java -jar $(JCK_JAVATEST_JAR)

$(JCK_CONFIG): $(TEST_ROOT)/JCK-$(JCK_BUNDLE_NAME)-$(JCK_RELEASE)-base.jti
	$(RM) $@
	$(MKDIR) -p $(@D)
	$(CP) $< $@

$(JCK_PROPERTIES): $(PRODUCT_HOME) $(JCK_JAVA_EXE)
	$(RM) $@
	$(MKDIR) -p $(@D)
	$(ECHO) "jck.env.compiler.compRefExecute.cmdAsFile=$(JCK_JAVA_EXE)" >> $@
	$(ECHO) "jck.env.compiler.compRefExecute.systemRoot=$(SYSTEMROOT)" >> $@
	$(ECHO) "jck.env.compiler.testCompile.testCompileAPImultiJVM.cmdAsFile=$(JCK_JAVA_EXE)" >> $@
	$(ECHO) "jck.tests.tests=$(JCK_BUNDLE_TESTDIRS)" >> $@

jck_tests: prep $(JCK_HOME) $(JCK_PROPERTIES) $(JCK_CONFIG) $(JCK_JAVATEST_JAR)
	$(MKDIR) -p $(JCK_WORK_DIR)
	( $(JCK_JAVATEST)                            \
		-verbose:commands,non-pass           \
		-testSuite    $(JCK_HOME)            \
		-workDir      $(JCK_WORK_DIR)        \
		-config       $(JCK_CONFIG)          \
		-set -file    $(JCK_PROPERTIES)      \
		-runtests                            \
		-writeReport  $(JCK_REPORT_DIR)      \
        ) ; $(BUNDLE_UP_AND_EXIT)

PHONY_LIST += jck_tests

################################################################

# jck6 tests

JCK6_RELEASE = 6b
JCK6_DEFAULT_HOME = $(SLASH_JAVA)/re/jck/$(JCK6_RELEASE)/archive/fcs/binaries

# Expect JPRT to set JPRT_JCK6COMPILER_HOME.
JCK6COMPILER_HOME = $(JCK6_DEFAULT_HOME)/JCK-compiler-$(JCK6_RELEASE)
ifdef JPRT_JCK6COMPILER_HOME
  JCK6COMPILER_HOME = $(JPRT_JCK6COMPILER_HOME)
endif

# Expect JPRT to set JPRT_JCK6RUNTIME_HOME.
JCK6RUNTIME_HOME = $(JCK6_DEFAULT_HOME)/JCK-runtime-$(JCK6_RELEASE)
ifdef JPRT_JCK6RUNTIME_HOME
  JCK6RUNTIME_HOME = $(JPRT_JCK6RUNTIME_HOME)
endif

# Expect JPRT to set JPRT_JCK6DEVTOOLS_HOME.
JCK6DEVTOOLS_HOME = $(JCK6_DEFAULT_HOME)/JCK-devtools-$(JCK6_RELEASE)
ifdef JPRT_JCK6DEVTOOLS_HOME
  JCK6DEVTOOLS_HOME = $(JPRT_JCK6DEVTOOLS_HOME)
endif

jck6_tests: JCK_HOME=$(JCK6_HOME)
jck6_tests: JCK_RELEASE=$(JCK6_RELEASE)
jck6_tests: jck_tests

jck6compiler: JCK6_HOME=$(JCK6COMPILER_HOME)
jck6compiler: JCK_BUNDLE_NAME=compiler
jck6compiler: jck6_tests

jck6compiler_lang: JCK_BUNDLE_TESTDIRS=lang
jck6compiler_lang: jck6compiler

jck6runtime: JCK6_HOME=$(JCK6RUNTIME_HOME)
jck6runtime: JCK_BUNDLE_NAME=runtime
jck6runtime: jck6_tests

jck6runtime_lang: JCK_BUNDLE_TESTDIRS=lang
jck6runtime_lang: jck6runtime

jck6devtools: JCK6_HOME=$(JCK6DEVTOOLS_HOME)
jck6devtools: JCK_BUNDLE_NAME=devtools
jck6devtools: jck6_tests

jck6devtools_lang: JCK_BUNDLE_TESTDIRS=lang
jck6devtools_lang: jck6devtools

PHONY_LIST += jck6compiler jck6runtime jck6devtools jck6_tests \
	      jck6compiler_lang jck6runtime_lang jck6devtools_lang

################################################################

# jck7 tests

JCK7_RELEASE = 7
JCK7_DEFAULT_HOME = $(SLASH_JAVA)/re/jck/$(JCK7_RELEASE)/archive/fcs/binaries

# Expect JPRT to set JPRT_JCK7COMPILER_HOME.
JCK7COMPILER_HOME = $(JCK7_DEFAULT_HOME)/JCK-compiler-$(JCK7_RELEASE)
ifdef JPRT_JCK7COMPILER_HOME
  JCK7COMPILER_HOME = $(JPRT_JCK7COMPILER_HOME)
endif

# Expect JPRT to set JPRT_JCK7RUNTIME_HOME.
JCK7RUNTIME_HOME = $(JCK7_DEFAULT_HOME)/JCK-runtime-$(JCK7_RELEASE)
ifdef JPRT_JCK7RUNTIME_HOME
  JCK7RUNTIME_HOME = $(JPRT_JCK7RUNTIME_HOME)
endif

# Expect JPRT to set JPRT_JCK7DEVTOOLS_HOME.
JCK7DEVTOOLS_HOME = $(JCK7_DEFAULT_HOME)/JCK-devtools-$(JCK7_RELEASE)
ifdef JPRT_JCK7DEVTOOLS_HOME
  JCK7DEVTOOLS_HOME = $(JPRT_JCK7DEVTOOLS_HOME)
endif

jck7_tests: JCK_HOME=$(JCK7_HOME)
jck7_tests: JCK_RELEASE=$(JCK7_RELEASE)
jck7_tests: jck_tests

jck7compiler: JCK7_HOME=$(JCK7COMPILER_HOME)
jck7compiler: JCK_BUNDLE_NAME=compiler
jck7compiler: jck7_tests

jck7compiler_lang: JCK_BUNDLE_TESTDIRS=lang
jck7compiler_lang: jck7compiler

jck7runtime: JCK7_HOME=$(JCK7RUNTIME_HOME)
jck7runtime: JCK_BUNDLE_NAME=runtime
jck7runtime: jck7_tests

jck7runtime_lang: JCK_BUNDLE_TESTDIRS=lang
jck7runtime_lang: jck7runtime

jck7devtools: JCK7_HOME=$(JCK7DEVTOOLS_HOME)
jck7devtools: JCK_BUNDLE_NAME=devtools
jck7devtools: jck7_tests

jck7devtools_lang: JCK_BUNDLE_TESTDIRS=lang
jck7devtools_lang: jck7devtools

PHONY_LIST += jck7compiler jck7runtime jck7devtools jck7_tests \
	      jck7compiler_lang jck7runtime_lang jck7devtools_lang

################################################################

# Phony targets (e.g. these are not filenames)
.PHONY: all clean prep $(PHONY_LIST)

################################################################

