#
# Copyright 1995-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

# Get OS/ARCH specifics
OSNAME = $(shell uname -s)

# Commands to run on paths to make mixed paths for java on windows
GETMIXEDPATH=echo

# Location of developer shared files
SLASH_JAVA = /java

# Platform specific settings
ifeq ($(OSNAME), SunOS)
  PLATFORM = solaris
  ARCH = $(shell uname -p)
  ifeq ($(ARCH), i386)
    ARCH=i586
  endif
endif
ifeq ($(OSNAME), Linux)
  PLATFORM = linux
  ARCH = $(shell uname -m)
  ifeq ($(ARCH), i386)
    ARCH=i586
  endif
endif

# Cannot trust uname output
ifneq ($(PROCESSOR_IDENTIFIER), )
  PLATFORM = windows
  SLASH_JAVA = J:
  # A variety of ways to say X64 arch :^(
  PROC_ARCH:=$(word 1, $(PROCESSOR_IDENTIFIER))
  PROC_ARCH:=$(subst x86,X86,$(PROC_ARCH))
  PROC_ARCH:=$(subst x64,X64,$(PROC_ARCH))
  PROC_ARCH:=$(subst AMD64,X64,$(PROC_ARCH))
  PROC_ARCH:=$(subst amd64,X64,$(PROC_ARCH))
  PROC_ARCH:=$(subst EM64T,X64,$(PROC_ARCH))
  PROC_ARCH:=$(subst em64t,X64,$(PROC_ARCH))
  PROC_ARCH:=$(subst intel64,X64,$(PROC_ARCH))
  PROC_ARCH:=$(subst Intel64,X64,$(PROC_ARCH))
  PROC_ARCH:=$(subst INTEL64,X64,$(PROC_ARCH))
  PROC_ARCH:=$(subst ia64,IA64,$(PROC_ARCH))
  ifeq ($(PROC_ARCH),IA64)
    ARCH = ia64
  else
    ifeq ($(PROC_ARCH),X64)
      ARCH = x64
    else
      ARCH = i586
    endif
  endif
  EXESUFFIX = .exe
  # These need to be different depending on MKS or CYGWIN
  ifeq ($(findstring cygdrive,$(shell (cd C:/ && pwd))), )
    GETMIXEDPATH=dosname -s
  else
    GETMIXEDPATH=cygpath -m -s
  endif
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
ifdef ALT_OUTPUTDIR
  ABS_OUTPUTDIR = $(ALT_OUTPUTDIR)
else
  ABS_OUTPUTDIR = $(TEST_ROOT)/../build/$(PLATFORM)-$(ARCH)
endif
ABS_BUILD_ROOT = $(ABS_OUTPUTDIR)
ABS_TEST_OUTPUT_DIR := $(ABS_BUILD_ROOT)/testoutput

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
  PRODUCT_HOME := $(PRODUCT_HOME)
endif

# Expect JPRT to set JPRT_PRODUCT_ARGS (e.g. -server etc.)
#   Should be passed into 'java' only.
ifdef JPRT_PRODUCT_ARGS
  JAVA_ARGS = $(JPRT_PRODUCT_ARGS)
endif

# Expect JPRT to set JPRT_PRODUCT_VM_ARGS (e.g. -Xcomp etc.)
#   Should be passed into anything running the vm (java, javac, javadoc, ...).
ifdef JPRT_PRODUCT_VM_ARGS
  JAVA_VM_ARGS = $(JPRT_PRODUCT_VM_ARGS)
endif

# Expect JPRT to set JPRT_ARCHIVE_BUNDLE (path to zip bundle for results)
ARCHIVE_BUNDLE = $(ABS_TEST_OUTPUT_DIR)/ARCHIVE_BUNDLE.zip
ifdef JPRT_ARCHIVE_BUNDLE
  ARCHIVE_BUNDLE = $(JPRT_ARCHIVE_BUNDLE)
endif

# How to create the test bundle (pass or fail, we want to create this)
#   Follow command with ";$(BUNDLE_UP_AND_EXIT)", so it always gets executed.
ZIP_UP_RESULTS = ( $(MKDIR) -p `dirname $(ARCHIVE_BUNDLE)`     \
	           && $(CD) $(ABS_TEST_OUTPUT_DIR)             \
	           && $(ZIP) -q -r $(ARCHIVE_BUNDLE) . )
BUNDLE_UP_AND_EXIT = ( exitCode=$$? && $(ZIP_UP_RESULTS) && exit $${exitCode} )

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
ifndef TESTDIRS
  TESTDIRS = demo/jvmti/gctest demo/jvmti/hprof
endif

# Default JTREG to run (win32 script works for everybody)
JTREG = $(JT_HOME)/win32/bin/jtreg

jtreg_tests: prep $(JT_HOME) $(PRODUCT_HOME) $(JTREG)
	$(RM) $(JTREG).orig
	cp $(JTREG) $(JTREG).orig
	$(RM) $(JTREG)
	sed -e 's@-J\*@-J-*@' $(JTREG).orig > $(JTREG)
	chmod a+x $(JTREG)
	( JT_HOME=$(shell $(GETMIXEDPATH) "$(JT_HOME)");                     \
          export JT_HOME;                                                    \
          $(shell $(GETMIXEDPATH) "$(JTREG)")                                \
            -a -v:fail,error                                                 \
	    -ignore:quiet                                                    \
            $(EXTRA_JTREG_OPTIONS)                                           \
            -r:$(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)")/JTreport    \
            -w:$(shell $(GETMIXEDPATH) "$(ABS_TEST_OUTPUT_DIR)")/JTwork      \
            -jdk:$(shell $(GETMIXEDPATH) "$(PRODUCT_HOME)")                  \
            $(JAVA_ARGS:%=-javaoptions:%)                                     \
            $(JAVA_VM_ARGS:%=-vmoption:%)                                    \
            $(TESTDIRS)                                                      \
	) ; $(BUNDLE_UP_AND_EXIT)

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

