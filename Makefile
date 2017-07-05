#
# Copyright (c) 1995, 2010, Oracle and/or its affiliates. All rights reserved.
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

BUILD_PARENT_DIRECTORY=.

ifndef TOPDIR
  TOPDIR:=.
endif

# Openjdk sources (only used if SKIP_OPENJDK_BUILD!=true)
OPENJDK_SOURCETREE=$(TOPDIR)/openjdk
OPENJDK_BUILDDIR:=$(shell \
  if [ -r $(OPENJDK_SOURCETREE)/Makefile ]; then \
    echo "$(OPENJDK_SOURCETREE)"; \
  else \
    echo "."; \
  fi)

ifndef JDK_TOPDIR
  JDK_TOPDIR=$(TOPDIR)/jdk
endif
ifndef JDK_MAKE_SHARED_DIR
  JDK_MAKE_SHARED_DIR=$(JDK_TOPDIR)/make/common/shared
endif

# For start and finish echo lines
TITLE_TEXT = Control $(PLATFORM) $(ARCH) $(RELEASE)
DATE_STAMP = `$(DATE) '+%y-%m-%d %H:%M'`
START_ECHO  = echo "$(TITLE_TEXT) $@ build started: $(DATE_STAMP)"
FINISH_ECHO = echo "$(TITLE_TEXT) $@ build finished: $(DATE_STAMP)"

default: all

include $(JDK_MAKE_SHARED_DIR)/Defs-control.gmk
include ./make/Defs-internal.gmk
include ./make/sanity-rules.gmk
include ./make/hotspot-rules.gmk
include ./make/langtools-rules.gmk
include ./make/corba-rules.gmk
include ./make/jaxp-rules.gmk
include ./make/jaxws-rules.gmk
include ./make/jdk-rules.gmk
include ./make/install-rules.gmk
include ./make/sponsors-rules.gmk
include ./make/deploy-rules.gmk

# What "all" means
all::
	@$(START_ECHO)

all:: openjdk_check sanity

ifeq ($(SKIP_FASTDEBUG_BUILD), false)
  all:: fastdebug_build
endif

ifeq ($(SKIP_DEBUG_BUILD), false)
  all:: debug_build
endif

ifneq ($(SKIP_OPENJDK_BUILD), true)
  all:: openjdk_build
endif

all:: all_product_build 

all:: 
	@$(FINISH_ECHO)

# Everything for a full product build
all_product_build::
	@$(START_ECHO)

ifeq ($(SKIP_PRODUCT_BUILD), false)
  
  all_product_build:: product_build

  ifeq ($(BUILD_INSTALL), true)
    all_product_build:: $(INSTALL)
    clobber:: install-clobber
  endif
  
  ifeq ($(BUILD_SPONSORS), true)
    all_product_build:: $(SPONSORS)
    clobber:: sponsors-clobber
  endif
  
  ifneq ($(SKIP_COMPARE_IMAGES), true)
    all_product_build:: compare-image
  endif

endif

all_product_build:: 
	@$(FINISH_ECHO)

# Generic build of basic repo series
generic_build_repo_series::
	$(MKDIR) -p $(OUTPUTDIR)
	$(MKDIR) -p $(OUTPUTDIR)/j2sdk-image

ifeq ($(BUILD_LANGTOOLS), true)
  generic_build_repo_series:: langtools
  clobber:: langtools-clobber
endif

ifeq ($(BUILD_CORBA), true)
  generic_build_repo_series:: corba
  clobber:: corba-clobber
endif

ifeq ($(BUILD_JAXP), true)
  generic_build_repo_series:: jaxp
  clobber:: jaxp-clobber
endif

ifeq ($(BUILD_JAXWS), true)
  generic_build_repo_series:: jaxws
  clobber:: jaxws-clobber
endif

ifeq ($(BUILD_HOTSPOT), true)
  generic_build_repo_series:: $(HOTSPOT) 
  clobber:: hotspot-clobber
endif

ifeq ($(BUILD_JDK), true)
  generic_build_repo_series:: $(JDK_JAVA_EXE)
  clobber:: jdk-clobber
endif

ifeq ($(BUILD_DEPLOY), true)
  generic_build_repo_series:: $(DEPLOY)
  clobber:: deploy-clobber
endif

ifeq ($(BUILD_JDK), true)
  ifeq ($(BUNDLE_RULES_AVAILABLE), true)
    generic_build_repo_series:: openjdk-binary-plugs-bundles
  endif
endif

# The debug build, fastdebug or debug. Needs special handling.
#  Note that debug builds do NOT do INSTALL steps, but must be done
#  after the product build and before the INSTALL step of the product build.
#
#   DEBUG_NAME is fastdebug or debug
#   ALT_OUTPUTDIR is changed to have -debug or -fastdebug suffix
#   The resulting j2sdk-image is used by the install makefiles to create a
#     debug install bundle jdk-*-debug-** bundle (tar or zip) 
#     which will install in the debug or fastdebug subdirectory of the
#     normal product install area.
#     The install process needs to know what the DEBUG_NAME is, so
#     look for INSTALL_DEBUG_NAME in the install rules.
#
#   NOTE: On windows, do not use $(ABS_BOOTDIR_OUTPUTDIR)-$(DEBUG_NAME).
#         Due to the use of short paths in $(ABS_OUTPUTDIR), this may 
#         not be the same location.
#

# Location of fresh bootdir output
ABS_BOOTDIR_OUTPUTDIR=$(ABS_OUTPUTDIR)/bootjdk
FRESH_BOOTDIR=$(ABS_BOOTDIR_OUTPUTDIR)/j2sdk-image
FRESH_DEBUG_BOOTDIR=$(ABS_BOOTDIR_OUTPUTDIR)/../$(PLATFORM)-$(ARCH)-$(DEBUG_NAME)/j2sdk-image
  
create_fresh_product_bootdir: FRC
	@$(START_ECHO)
	$(MAKE) ALT_OUTPUTDIR=$(ABS_BOOTDIR_OUTPUTDIR) \
		GENERATE_DOCS=false \
		BOOT_CYCLE_SETTINGS= \
		build_product_image
	@$(FINISH_ECHO)

create_fresh_debug_bootdir: FRC
	@$(START_ECHO)
	$(MAKE) ALT_OUTPUTDIR=$(ABS_BOOTDIR_OUTPUTDIR) \
		GENERATE_DOCS=false \
		BOOT_CYCLE_DEBUG_SETTINGS= \
		build_debug_image
	@$(FINISH_ECHO)

create_fresh_fastdebug_bootdir: FRC
	@$(START_ECHO)
	$(MAKE) ALT_OUTPUTDIR=$(ABS_BOOTDIR_OUTPUTDIR) \
		GENERATE_DOCS=false \
		BOOT_CYCLE_DEBUG_SETTINGS= \
		build_fastdebug_image
	@$(FINISH_ECHO)

# Create boot image?
ifeq ($(SKIP_BOOT_CYCLE),false)
  ifneq ($(PLATFORM)$(ARCH_DATA_MODEL),solaris64)
    DO_BOOT_CYCLE=true
  endif
endif

ifeq ($(DO_BOOT_CYCLE),true)
  
  # Create the bootdir to use in the build
  product_build:: create_fresh_product_bootdir
  debug_build:: create_fresh_debug_bootdir
  fastdebug_build:: create_fresh_fastdebug_bootdir

  # Define variables to be used now for the boot jdk
  BOOT_CYCLE_SETTINGS= \
     ALT_BOOTDIR=$(FRESH_BOOTDIR) \
     ALT_JDK_IMPORT_PATH=$(FRESH_BOOTDIR)
  BOOT_CYCLE_DEBUG_SETTINGS= \
     ALT_BOOTDIR=$(FRESH_DEBUG_BOOTDIR) \
     ALT_JDK_IMPORT_PATH=$(FRESH_DEBUG_BOOTDIR)

else

  # Use the supplied ALT_BOOTDIR as the boot
  BOOT_CYCLE_SETTINGS=
  BOOT_CYCLE_DEBUG_SETTINGS=

endif

build_product_image:
	@$(START_ECHO)
	$(MAKE) \
	        SKIP_FASTDEBUG_BUILD=true \
	        SKIP_DEBUG_BUILD=true \
	        $(BOOT_CYCLE_SETTINGS) \
	        generic_build_repo_series
	@$(FINISH_ECHO)

#   NOTE: On windows, do not use $(ABS_OUTPUTDIR)-$(DEBUG_NAME).
#         Due to the use of short paths in $(ABS_OUTPUTDIR), this may 
#         not be the same location.

generic_debug_build:
	@$(START_ECHO)
	$(MAKE) \
		ALT_OUTPUTDIR=$(ABS_OUTPUTDIR)/../$(PLATFORM)-$(ARCH)-$(DEBUG_NAME) \
	        DEBUG_NAME=$(DEBUG_NAME) \
		GENERATE_DOCS=false \
	        $(BOOT_CYCLE_DEBUG_SETTINGS) \
		generic_build_repo_series
	@$(FINISH_ECHO)

build_debug_image:
	$(MAKE) DEBUG_NAME=debug generic_debug_build

build_fastdebug_image:
	$(MAKE) DEBUG_NAME=fastdebug generic_debug_build

# Build final image
product_build:: build_product_image
debug_build:: build_debug_image
fastdebug_build:: build_fastdebug_image

# Check on whether we really can build the openjdk, need source etc.
openjdk_check: FRC
ifneq ($(SKIP_OPENJDK_BUILD), true)
	@$(ECHO) " "
	@$(ECHO) "================================================="
	@if [ ! -r $(OPENJDK_BUILDDIR)/Makefile ] ; then \
	    $(ECHO) "ERROR: No openjdk source tree available at: $(OPENJDK_BUILDDIR)"; \
	    exit 1; \
	else \
	    $(ECHO) "OpenJDK will be built after JDK is built"; \
	    $(ECHO) "  OPENJDK_BUILDDIR=$(OPENJDK_BUILDDIR)"; \
	fi
	@$(ECHO) "================================================="
	@$(ECHO) " "
endif

# If we have bundle rules, we have a chance here to do a complete cycle
#   build, of production and open build.
# FIXUP: We should create the openjdk source bundle and build that?
#   But how do we reliable create or get at a formal openjdk source tree?
#   The one we have needs to be trimmed of built bits and closed dirs.
#   The repositories might not be available.
#   The openjdk source bundle is probably not available.

ifneq ($(SKIP_OPENJDK_BUILD), true)
  ifeq ($(BUILD_JDK), true)
    ifeq ($(BUNDLE_RULES_AVAILABLE), true)

OPENJDK_PLUGS=$(ABS_OUTPUTDIR)/$(OPENJDK_BINARY_PLUGS_INAME)
OPENJDK_OUTPUTDIR=$(ABS_OUTPUTDIR)/open-output
OPENJDK_BUILD_NAME \
  = openjdk-$(JDK_MINOR_VERSION)-$(BUILD_NUMBER)-$(PLATFORM)-$(ARCH)-$(BUNDLE_DATE)
OPENJDK_BUILD_BINARY_ZIP=$(ABS_BIN_BUNDLEDIR)/$(OPENJDK_BUILD_NAME).zip
BUILT_IMAGE=$(ABS_OUTPUTDIR)/j2sdk-image
ifeq ($(PLATFORM)$(ARCH_DATA_MODEL),solaris64)
  OPENJDK_BOOTDIR=$(BOOTDIR)
  OPENJDK_IMPORTJDK=$(JDK_IMPORT_PATH)
else
  OPENJDK_BOOTDIR=$(BUILT_IMAGE)
  OPENJDK_IMPORTJDK=$(BUILT_IMAGE)
endif

openjdk_build:
	@$(START_ECHO)
	@$(ECHO) " "
	@$(ECHO) "================================================="
	@$(ECHO) "Starting openjdk build"
	@$(ECHO) " Using: ALT_JDK_DEVTOOLS_DIR=$(JDK_DEVTOOLS_DIR)"
	@$(ECHO) "================================================="
	@$(ECHO) " "
	$(RM) -r $(OPENJDK_OUTPUTDIR)
	$(MKDIR) -p $(OPENJDK_OUTPUTDIR)
	($(CD) $(OPENJDK_BUILDDIR) && $(MAKE) \
	  OPENJDK=true \
	  GENERATE_DOCS=false \
	  ALT_JDK_DEVTOOLS_DIR=$(JDK_DEVTOOLS_DIR) \
	  ALT_OUTPUTDIR=$(OPENJDK_OUTPUTDIR) \
	  ALT_BINARY_PLUGS_PATH=$(OPENJDK_PLUGS) \
	  ALT_BOOTDIR=$(OPENJDK_BOOTDIR) \
	  ALT_JDK_IMPORT_PATH=$(OPENJDK_IMPORTJDK) \
		product_build )
	$(RM) $(OPENJDK_BUILD_BINARY_ZIP)
	( $(CD) $(OPENJDK_OUTPUTDIR)/j2sdk-image && \
	  $(ZIPEXE) -q -r $(OPENJDK_BUILD_BINARY_ZIP) .)
	$(RM) -r $(OPENJDK_OUTPUTDIR)
	@$(ECHO) " "
	@$(ECHO) "================================================="
	@$(ECHO) "Finished openjdk build"
	@$(ECHO) " Binary Bundle: $(OPENJDK_BUILD_BINARY_ZIP)"
	@$(ECHO) "================================================="
	@$(ECHO) " "
	@$(FINISH_ECHO)
    
    endif
  endif
endif

clobber::
	$(RM) -r $(OUTPUTDIR)/*
	$(RM) -r $(OUTPUTDIR)/../$(PLATFORM)-$(ARCH)-debug/*
	$(RM) -r $(OUTPUTDIR)/../$(PLATFORM)-$(ARCH)-fastdebug/*
	-($(RMDIR) -p $(OUTPUTDIR) > $(DEV_NULL) 2>&1; $(TRUE))

clean: clobber

#
# Dev builds
#

dev : dev-build

dev-build:
	$(MAKE) DEV_ONLY=true all
dev-sanity:
	$(MAKE) DEV_ONLY=true sanity
dev-clobber:
	$(MAKE) DEV_ONLY=true clobber

#
# Quick jdk verification build
#
jdk_only:
	$(MAKE) SKIP_FASTDEBUG_BUILD=true BUILD_HOTSPOT=false all


#
# Quick jdk verification fastdebug build
#
jdk_fastdebug_only:
	$(MAKE) DEBUG_NAME=fastdebug BUILD_HOTSPOT=false BUILD_DEPLOY=false \
	    BUILD_INSTALL=false BUILD_SPONSORS=false generic_debug_build

#
# Quick deploy verification fastdebug build
#
deploy_fastdebug_only:
	$(MAKE) \
	    DEBUG_NAME=fastdebug \
	    BUILD_HOTSPOT=false \
	    BUILD_JDK=false \
	    BUILD_LANGTOOLS=false \
	    BUILD_CORBA=false \
	    BUILD_JAXP=false \
	    BUILD_JAXWS=false \
	    BUILD_INSTALL=false \
	    BUILD_SPONSORS=false \
	    generic_debug_build

#
# Product build (skip debug builds)
#
product_only:
	$(MAKE) SKIP_FASTDEBUG_BUILD=true all

#
# Check target
#

check: variable_check

#
# Help target
#
help: intro_help target_help variable_help notes_help examples_help

# Intro help message
intro_help:
	@$(ECHO) "\
Makefile for the JDK builds (all the JDK). \n\
"

# Target help
target_help:
	@$(ECHO) "\
--- Common Targets ---  \n\
all               -- build the core JDK (default target) \n\
help              -- Print out help information \n\
check             -- Check make variable values for correctness \n\
sanity            -- Perform detailed sanity checks on system and settings \n\
fastdebug_build   -- build the core JDK in 'fastdebug' mode (-g -O) \n\
debug_build       -- build the core JDK in 'debug' mode (-g) \n\
clean             -- remove all built and imported files \n\
clobber           -- same as clean \n\
"

# Variable help (only common ones used by this Makefile)
variable_help: variable_help_intro variable_list variable_help_end
variable_help_intro:
	@$(ECHO) "--- Common Variables ---"
variable_help_end:
	@$(ECHO) " "

# One line descriptions for the variables
OUTPUTDIR.desc             = Output directory
PARALLEL_COMPILE_JOBS.desc = Solaris/Linux parallel compile run count
SLASH_JAVA.desc            = Root of all build tools, e.g. /java or J:
BOOTDIR.desc               = JDK used to boot the build
JDK_IMPORT_PATH.desc       = JDK used to import components of the build
COMPILER_PATH.desc         = Compiler install directory
CACERTS_FILE.desc          = Location of certificates file
DEVTOOLS_PATH.desc         = Directory containing zip and gnumake
CUPS_HEADERS_PATH.desc     = Include directory location for CUPS header files
DXSDK_PATH.desc            = Root directory of DirectX SDK
MSDEVTOOLS_PATH.desc       = Root directory of VC++ tools (e.g. rc.exe)
MSVCRT_DLL_PATH.desc       = Directory containing mscvrt.dll

# Make variables to print out (description and value)
VARIABLE_PRINTVAL_LIST +=       \
    OUTPUTDIR                   \
    PARALLEL_COMPILE_JOBS       \
    SLASH_JAVA                  \
    BOOTDIR                     \
    JDK_IMPORT_PATH             \
    COMPILER_PATH               \
    CACERTS_FILE                \
    DEVTOOLS_PATH

# Make variables that should refer to directories that exist
VARIABLE_CHECKDIR_LIST +=       \
    SLASH_JAVA                  \
    BOOTDIR                     \
    JDK_IMPORT_PATH             \
    COMPILER_PATH               \
    DEVTOOLS_PATH 

# Make variables that should refer to files that exist
VARIABLE_CHECKFIL_LIST +=       \
    CACERTS_FILE

# Some are windows specific
ifeq ($(PLATFORM), windows)

VARIABLE_PRINTVAL_LIST +=       \
    DXSDK_PATH                  \
    MSDEVTOOLS_PATH             \
    MSVCRT_DLL_PATH

VARIABLE_CHECKDIR_LIST +=       \
    DXSDK_PATH                  \
    MSDEVTOOLS_PATH             \
    MSVCRT_DLL_PATH

endif

# For pattern rules below, so all are treated the same
DO_PRINTVAL_LIST=$(VARIABLE_PRINTVAL_LIST:%=%.printval)
DO_CHECKDIR_LIST=$(VARIABLE_CHECKDIR_LIST:%=%.checkdir)
DO_CHECKFIL_LIST=$(VARIABLE_CHECKFIL_LIST:%=%.checkfil)

# Complete variable check
variable_check: $(DO_CHECKDIR_LIST) $(DO_CHECKFIL_LIST)
variable_list: $(DO_PRINTVAL_LIST) variable_check

# Pattern rule for printing out a variable
%.printval:
	@$(ECHO) "  ALT_$* - $($*.desc)"
	@$(ECHO) "  \t $*=$($*)"

# Pattern rule for checking to see if a variable with a directory exists
%.checkdir:
	@if [ ! -d $($*) ] ; then \
	    $(ECHO) "WARNING: $* does not exist, try $(MAKE) sanity"; \
	fi

# Pattern rule for checking to see if a variable with a file exists
%.checkfil:
	@if [ ! -f $($*) ] ; then \
	    $(ECHO) "WARNING: $* does not exist, try $(MAKE) sanity"; \
	fi

# Misc notes on help
notes_help:
	@$(ECHO) "\
--- Notes --- \n\
- All builds use same output directory unless overridden with \n\
 \t ALT_OUTPUTDIR=<dir>, changing from product to fastdebug you may want \n\
 \t to use the clean target first. \n\
- JDK_IMPORT_PATH must refer to a compatible build, not all past promoted \n\
 \t builds or previous release JDK builds will work. \n\
- The fastest builds have been when the sources and the BOOTDIR are on \n\
 \t local disk. \n\
"

examples_help:
	@$(ECHO) "\
--- Examples --- \n\
  $(MAKE) fastdebug_build \n\
  $(MAKE) ALT_OUTPUTDIR=/tmp/foobar all \n\
  $(MAKE) ALT_OUTPUTDIR=/tmp/foobar fastdebug_build \n\
  $(MAKE) ALT_OUTPUTDIR=/tmp/foobar all \n\
  $(MAKE) ALT_BOOTDIR=/opt/java/jdk1.5.0 \n\
  $(MAKE) ALT_JDK_IMPORT_PATH=/opt/java/jdk1.6.0 \n\
"

################################################################
# Source and binary plug bundling
################################################################
ifeq ($(BUNDLE_RULES_AVAILABLE), true)
  include $(BUNDLE_RULES)
endif

################################################################
# rule to test
################################################################

.NOTPARALLEL: test_run

test:
	$(MAKE) test_run

test_run: test_clean test_start test_summary

test_start:
	@$(ECHO) "Tests started at `$(DATE)`"

test_clean:
	$(RM) $(OUTPUTDIR)/test_failures.txt $(OUTPUTDIR)/test_log.txt

test_summary: $(OUTPUTDIR)/test_failures.txt
	@$(ECHO) "#################################################"
	@$(ECHO) "Tests completed at `$(DATE)`"
	@( $(EGREP) '^TEST STATS:' $(OUTPUTDIR)/test_log.txt \
          || $(ECHO) "No TEST STATS seen in log" )
	@$(ECHO) "For complete details see: $(OUTPUTDIR)/test_log.txt"
	@$(ECHO) "#################################################"
	@if [ -s $< ] ; then                                           \
          $(ECHO) "ERROR: Test failure count: `$(CAT) $< | $(WC) -l`"; \
          $(CAT) $<;                                                   \
          exit 1;                                                      \
        else                                                           \
          $(ECHO) "Success! No failures detected";                     \
        fi

# Get failure list from log
$(OUTPUTDIR)/test_failures.txt: $(OUTPUTDIR)/test_log.txt
	@$(RM) $@
	@( $(EGREP) '^FAILED:' $< || $(ECHO) "" ) | $(NAWK) 'length>0' > $@

# Get log file of all tests run
JDK_TO_TEST := $(shell 							\
  if [ -d "$(ABS_OUTPUTDIR)/j2sdk-image" ] ; then 			\
    $(ECHO) "$(ABS_OUTPUTDIR)/j2sdk-image"; 				\
  elif [ -d "$(ABS_OUTPUTDIR)/bin" ] ; then 				\
    $(ECHO) "$(ABS_OUTPUTDIR)"; 					\
  elif [ "$(PRODUCT_HOME)" != "" -a -d "$(PRODUCT_HOME)/bin" ] ; then 	\
    $(ECHO) "$(PRODUCT_HOME)"; 						\
  fi 									\
)
TEST_TARGETS=all
$(OUTPUTDIR)/test_log.txt:
	$(RM) $@
	( $(CD) test &&                                                     \
          $(MAKE) NO_STOPPING=- PRODUCT_HOME=$(JDK_TO_TEST) $(TEST_TARGETS) \
        ) | tee $@

################################################################
# JPRT rule to build
################################################################

include ./make/jprt.gmk

################################################################
#  PHONY
################################################################

.PHONY: all  test test_run test_start test_summary test_clean \
	generic_build_repo_series \
	what clobber insane \
        dev dev-build dev-sanity dev-clobber \
        product_build \
        fastdebug_build \
        debug_build  \
        build_product_image  \
        build_debug_image  \
        build_fastdebug_image \
        create_fresh_product_bootdir \
        create_fresh_debug_bootdir \
        create_fresh_fastdebug_bootdir \
        generic_debug_build

# Force target
FRC:

