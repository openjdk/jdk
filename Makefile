#
# Copyright 1995-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

ifndef TOPDIR
  TOPDIR:=$(shell \
    if [ -r ./j2se/make/Makefile -o -r ./jdk/make/Makefile ]; then \
      echo "."; \
    else \
      echo "../.."; \
    fi)
endif

ifndef CONTROL_TOPDIR
  CONTROL_TOPDIR=$(TOPDIR)/control
  CONTROL_TOPDIR:=$(shell \
    if [ -r $(TOPDIR)/control/make/Makefile ]; then \
      echo "$(TOPDIR)/control"; \
    else \
      echo "$(TOPDIR)"; \
    fi)
endif

ifndef JDK_TOPDIR
  JDK_TOPDIR=$(TOPDIR)/jdk
endif
ifndef JDK_MAKE_SHARED_DIR
  JDK_MAKE_SHARED_DIR=$(JDK_TOPDIR)/make/common/shared
endif

include $(JDK_MAKE_SHARED_DIR)/Defs-control.gmk

include ./make/Defs-internal.gmk

all::
	@$(ECHO) $(PLATFORM) $(ARCH) $(RELEASE) build started: `$(DATE) '+%y-%m-%d %H:%M'`

# Rules for sanity checks
include ./make/sanity-rules.gmk

dev : dev-build

dev-build:
	$(MAKE) DEV_ONLY=true all
dev-sanity:
	$(MAKE) DEV_ONLY=true sanity
dev-clobber:
	$(MAKE) DEV_ONLY=true clobber

# Rules for various components
include ./make/hotspot-rules.gmk
include ./make/langtools-rules.gmk
include ./make/corba-rules.gmk
include ./make/jaxp-rules.gmk
include ./make/jaxws-rules.gmk
include ./make/jdk-rules.gmk
include ./make/install-rules.gmk
include ./make/sponsors-rules.gmk
include ./make/deploy-rules.gmk

all:: setup build

setup:
	$(MKDIR) -p $(OUTPUTDIR)/j2sdk-image
	$(MKDIR) -p $(ABS_OUTPUTDIR)/j2sdk-image
	$(MKDIR) -p $(OUTPUTDIR)-fastdebug/j2sdk-image
	$(MKDIR) -p $(ABS_OUTPUTDIR)-fastdebug/j2sdk-image

build:: sanity 

clobber::

ifeq ($(BUILD_LANGTOOLS), true)
  build:: langtools
  clobber:: langtools-clobber
endif

ifeq ($(BUILD_CORBA), true)
  build:: corba
  clobber:: corba-clobber
endif

ifeq ($(BUILD_JAXP), true)
  build:: jaxp
  clobber:: jaxp-clobber
endif

ifeq ($(BUILD_JAXWS), true)
  build:: jaxws
  clobber:: jaxws-clobber
endif

ifeq ($(BUILD_HOTSPOT), true)
  build:: $(HOTSPOT) 
  clobber:: hotspot-clobber
endif

ifeq ($(BUILD_JDK), true)
  build:: $(JDK_JAVA_EXE)
  clobber:: jdk-clobber
endif

ifeq ($(BUILD_DEPLOY), true)
  build:: $(DEPLOY)
  clobber:: deploy-clobber
endif

#
# Generic debug build, fastdebug or debug. Needs special handling.
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

COMMON_DEBUG_FLAGS= \
	DEBUG_NAME=$(DEBUG_NAME) \
	ALT_OUTPUTDIR=$(_OUTPUTDIR)-$(DEBUG_NAME) \
	NO_DOCS=true

product_build: setup
	@$(ECHO) $@ build started: `$(DATE) '+%y-%m-%d %H:%M'`
	$(MAKE) SKIP_FASTDEBUG_BUILD=true SKIP_DEBUG_BUILD=true all
	@$(ECHO) $@ build finished: `$(DATE) '+%y-%m-%d %H:%M'`

generic_debug_build:
	@$(ECHO) $@ build started: `$(DATE) '+%y-%m-%d %H:%M'`
	$(MAKE) $(COMMON_DEBUG_FLAGS) setup build
	@$(ECHO) $@ build finished: `$(DATE) '+%y-%m-%d %H:%M'`

debug_build: setup
	$(MAKE) DEBUG_NAME=debug generic_debug_build

fastdebug_build: setup
	$(MAKE) DEBUG_NAME=fastdebug generic_debug_build

ifeq ($(SKIP_FASTDEBUG_BUILD), false)
  all:: fastdebug_build
endif

ifeq ($(SKIP_DEBUG_BUILD), false)
  all:: debug_build
endif

ifeq ($(BUILD_JDK), true)
  ifeq ($(BUNDLE_RULES_AVAILABLE), true)
    all:: openjdk-binary-plugs-bundles
  endif
endif

ifeq ($(BUILD_INSTALL), true)
  all :: $(INSTALL)
  clobber:: install-clobber
endif

ifeq ($(BUILD_SPONSORS), true)
  all :: $(SPONSORS)
  clobber:: sponsors-clobber
endif

ifneq ($(SKIP_COMPARE_IMAGES), true)
  all :: compare-image
endif

ifeq ($(SKIP_OPENJDK_BUILD), false)
  ifeq ($(BUILD_JDK), true)
    ifeq ($(BUNDLE_RULES_AVAILABLE), true)
      # If we have bundle rules, we have a chance here to do a complete cycle
      #   build, of closed and open build.
      # FIXUP: We should create the openjdk source bundle and build that?
      ABS_OPENJDK_PLUGS=$(ABS_OUTPUTDIR)/$(OPENJDK_BINARY_PLUGS_INAME)
      ABS_OPENJDK_OUTPUTDIR=$(ABS_OUTPUTDIR)/openjdk
      OPENJDK_BUILD_NAME_PREFIX \
	= $(J2SDK_NAME)-$(JDK_MKTG_UNDERSCORE_VERSION)-$(MILESTONE)
      OPENJDK_BUILD_NAME_SUFFIX \
	= $(BUILD_NUMBER)-$(PLATFORM)-$(ARCH)-$(BUNDLE_DATE)
      OPENJDK_BUILD_NAME \
	= $(OPENJDK_BUILD_NAME_PREFIX)-openjdk-$(OPENJDK_BUILD_NAME_SUFFIX)
      OPENJDK_BUILD_BINARY_ZIP \
	= $(ABS_BIN_BUNDLEDIR)/$(OPENJDK_BUILD_NAME).zip
  all :: openjdk-build
  openjdk-build:
	@$(ECHO) " "
	@$(ECHO) "================================================="
	@$(ECHO) "Starting openjdk build"
	@$(ECHO) "================================================="
	@$(ECHO) " "
	$(RM) -r $(ABS_OPENJDK_OUTPUTDIR)
	$(MKDIR) -p $(ABS_OPENJDK_OUTPUTDIR)
	$(MAKE) OPENJDK=true \
	  BUILD_LANGTOOLS=$(BUILD_LANGTOOLS) \
	  BUILD_CORBA=$(BUILD_CORBA) \
	  BUILD_JAXP=$(BUILD_JAXP) \
	  BUILD_JAXWS=$(BUILD_JAXWS) \
	  BUILD_HOTSPOT=$(BUILD_HOTSPOT) \
	  ALT_OUTPUTDIR=$(ABS_OPENJDK_OUTPUTDIR) \
	  ALT_BINARY_PLUGS_PATH=$(ABS_OUTPUTDIR)/$(OPENJDK_BINARY_PLUGS_INAME) \
	  ALT_BOOTDIR=$(ABS_OUTPUTDIR)/j2sdk-image \
	  ALT_JDK_IMPORT_PATH=$(ABS_OUTPUTDIR)/j2sdk-image \
		product_build
	$(RM) $(OPENJDK_BUILD_BINARY_ZIP)
	( $(CD) $(ABS_OPENJDK_OUTPUTDIR)/j2sdk-image && \
	  $(ZIPEXE) -q -r $(OPENJDK_BUILD_BINARY_ZIP) .)
	$(RM) -r $(ABS_OPENJDK_OUTPUTDIR)
    endif
  endif
endif

clobber::
	$(RM) -r $(OUTPUTDIR)/*
	$(RM) -r $(OUTPUTDIR)-debug/*
	$(RM) -r $(OUTPUTDIR)-fastdebug/*
	-($(RMDIR) -p $(OUTPUTDIR) > $(DEV_NULL) 2>&1; $(TRUE))

clean: clobber

all:: 
	@$(ECHO) Control build finished: `$(DATE) '+%y-%m-%d %H:%M'`

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
# Cycle build. Build the jdk, use it to build the jdk again.
################################################################
  
ABS_BOOTJDK_OUTPUTDIR=$(ABS_OUTPUTDIR)/bootjdk
  
boot_cycle:
	$(MAKE) ALT_OUTPUTDIR=$(ABS_BOOTJDK_OUTPUTDIR) product_build
	$(MAKE) ALT_BOOTDIR=$(ABS_BOOTJDK_OUTPUTDIR)/j2sdk-image product_build

################################################################
# JPRT rule to build
################################################################

include ./make/jprt.gmk

################################################################
#  PHONY
################################################################

.PHONY: all build what clobber insane \
	fastdebug_build debug_build product_build setup \
        dev dev-build dev-sanity dev-clobber

# FIXUP: Old j2se targets
j2se_fastdebug_only: jdk_fastdebug_only
j2se_only: jdk_only

