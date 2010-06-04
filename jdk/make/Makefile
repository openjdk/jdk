#
# Copyright (c) 1995, 2007, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building and packaging all of the JDK and the JRE. See
# also included files.
#

BUILDDIR=.
include $(BUILDDIR)/common/Defs.gmk

#
# Make sure we are clear what the default target is
#
default_target: all

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
Makefile for the main JDK workspace. \n\
Default behavior is to use the BOOTDIR javac to bootstrap the build and \n\
import in pre-built components like the VM from the JDK_IMPORT_PATH. \n\
"

# Target help
target_help:
	@$(ECHO) "\
--- Common Targets ---  \n\
all               -- build the core JDK (default target) \n\
help              -- Print out help information \n\
check             -- Check make variable values for correctness \n\
sanity            -- Perform detailed sanity checks on system and settings \n\
openjdk           -- synonym for 'OPENJDK=true all' \n\
fastdebug         -- build the core JDK in 'fastdebug' mode (-g -O) \n\
debug             -- build the core JDK in 'debug' mode (-g) \n\
clean             -- remove all built and imported files \n\
clobber           -- same as clean \n\
docs              -- run javadoc to generate the JDK documentation \n\
images            -- build the jdk and jre image directories \n\
import            -- copy in the pre-built components (e.g. VM) \n\
import_product    -- copy in the product components \n\
import_fastdebug  -- copy in the fastdebug components \n\
import_debug      -- copy in the debug components \n\
modules           -- build the jdk and jre module images (experimental) \n\
sccs_get          -- make sure all SCCS files are up-to-date (need SCCS) \n\
create_links      -- create softlinks in Solaris 32bit build to 64bit dirs \n\
"

# Variable help (only common ones used by this workspace)
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
LANGTOOLS_DIST.desc        = langtools dist area used to build
CORBA_DIST.desc            = corba dist area
JAXP_DIST.desc             = jaxp dist area
JAXWS_DIST.desc            = jaxws dist area
JDK_IMPORT_PATH.desc       = JDK used to import components of the build
COMPILER_PATH.desc         = Compiler install directory
CACERTS_FILE.desc          = Location of certificates file
DEVTOOLS_PATH.desc         = Directory containing zip and unzip
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
    LANGTOOLS_DIST              \
    JAXWS_DIST                  \
    CORBA_DIST                  \
    JAXP_DIST                   \
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
- LANGTOOLS_DIST must refer to a langtools dist area,  used to build. \n\
- CORBA_DIST must refer to a corba dist area. \n\
- JAXP_DIST must refer to a jaxp dist area. \n\
- JAXWS_DIST must refer to a jaxws dist area. \n\
- JDK_IMPORT_PATH must refer to a compatible build, not all past promoted \n\
 \t builds or previous release JDK builds will work. \n\
- The 'debug' target and 'import_debug' only works when a debug promoted \n\
 \t build is available, and they currently are not. \n\
- The fastest builds have been when the workspace and the BOOTDIR are on \n\
 \t local disk. \n\
"

examples_help:
	@$(ECHO) "\
--- Examples --- \n\
  $(MAKE) fastdebug \n\
  $(MAKE) ALT_OUTPUTDIR=/tmp/foobar all \n\
  $(MAKE) ALT_OUTPUTDIR=/tmp/foobar fastdebug images \n\
  $(MAKE) ALT_OUTPUTDIR=/tmp/foobar all docs images \n\
  $(MAKE) ALT_BOOTDIR=/opt/java/jdk1.6.0 \n\
  $(MAKE) ALT_JDK_IMPORT_PATH=/opt/java/jdk1.7.0 \n\
"

#
# 'all' target intro
#
all:: 
	@$(ECHO) $(PLATFORM) $(ARCH) $(RELEASE) build started: $(shell $(DATE) '+%y-%m-%d %H:%M')

# Just in case anyone uses this old name, same as 'build'
optimized: build

openjdk:
	$(MAKE) OPENJDK=true all

#
# Special debug rules (You may also want to set ALT_OUTPUTDIR)
#
debug:
	$(MAKE) VARIANT=DBG all
fastdebug:
	$(MAKE) VARIANT=DBG FASTDEBUG=true all

#
# Rules to re-import VM and other JDK files
#
import:
	$(CD) java/redist; $(MAKE) clean all

import_fastdebug:
	$(MAKE) VARIANT=DBG FASTDEBUG=true import

import_product:
	$(MAKE) VARIANT=OPT FASTDEBUG=false import

#
# Core.
#

all build:: sanity-all post-sanity-all

SUBDIRS       = tools java javax sun com
SUBDIRS_tools = launchers
SUBDIRS_misc  = org sunw jpda mkdemo mksample

include $(BUILDDIR)/common/Subdirs.gmk

all build::
	$(SUBDIRS-loop)

clean clobber::
	$(RM) -r $(OUTPUTDIR)

#
# Docs
#
OTHERSUBDIRS = docs
docs:: sanity-docs post-sanity-docs
	$(OTHERSUBDIRS-loop)

#
# Release engineering targets.
#
include $(BUILDDIR)/common/Release.gmk
include $(BUILDDIR)/common/Modules.gmk

#
# Cscope targets.
#
include $(BUILDDIR)/common/Cscope.gmk

#
# Sanity checks.
#
include $(BUILDDIR)/common/Sanity.gmk

$(OUTPUTDIR) $(TEMPDIR):
	$(MKDIR) -p $@

# cleanup everything. If the workspace is not being built by the control
# workspace, and if it is a Teamware workspace, then see if there are
# any files which are not under SCCS control.
clean clobber:: 
ifndef EXTERNALSANITYCONTROL
	@if [ -d $(TOPDIR)/Codemgr_wsdata ]; then \
	  $(ECHO) '\nPerforming workspace scan for remnant files.\n' \
		'  Any files listed below are not under SCCS control in the workspace\n' \
		'  and you should review them and possibly remove them manually:' ; \
	  $(FIND) $(TOPDIR)/make $(TOPDIR)/src -type f | \
		$(SED) 's+SCCS/[ps]\.++' | $(SORT) | $(UNIQ) -c | $(NAWK) '$$1<2 {print $$2;}' ; \
	  $(ECHO) 'End of workspace scan.' ; \
	fi
endif

# this should be the last rule in this file:
all::
	@if [ -r $(WARNING_FILE) ]; then \
	  $(CAT) $(WARNING_FILE) ; \
	fi
	@$(ECHO) $(PLATFORM) $(ARCH) $(RELEASE) build finished: $(shell $(DATE) '+%y-%m-%d %H:%M')

#
# Developer rule that links 32 and 64 bit builds on Solaris by creating
#   softlinks in the 32bit outputdir to the 64bit outputdir.
#
ifeq ($(PLATFORM), solaris)
  ifeq ($(ARCH_FAMILY), sparc)
    ARCH32 = sparc
    ARCH64 = sparcv9
  else
    ARCH32 = i586
    ARCH64 = amd64
  endif
  OUTPUTDIR32      = $(ABS_OUTPUTDIR)/../$(PLATFORM)-$(ARCH32)
  OUTPUTDIR64      = $(ABS_OUTPUTDIR)/../$(PLATFORM)-$(ARCH64)
endif

create_links:
ifeq ($(PLATFORM), solaris)
	@if [ -d $(OUTPUTDIR32) -a -d $(OUTPUTDIR64) ] ; then \
	    dirlist=`($(CD) $(OUTPUTDIR64); $(FIND) . -name $(ARCH64))`; \
	    for sd in $$dirlist ; do \
	      pdir=`$(DIRNAME) $$sd`; \
	      if [ -d $(OUTPUTDIR32)/$$pdir ] ; then \
	        echo "Creating link for $$sd"; \
	        (cd $(OUTPUTDIR32)/$$pdir; $(RM) $(ARCH64); \
	         $(LN) -s $(OUTPUTDIR64)/$$sd ); \
	      fi; \
	    done; \
	else \
	    $(ECHO) "Build both 32 and 64 bit versions first"; \
	fi
else
	$(ECHO) "Rule $@ does not apply on $(PLATFORM)-$(ARCH)"
endif

#
# Binary Plug rules and macros
#
include $(BUILDDIR)/common/internal/BinaryPlugs.gmk

#
# Get top level sccs_get rule
#
include $(BUILDDIR)/common/Rules-SCCS.gmk

# JPRT rules
include jprt.gmk

#
# Phonies to avoid accidents.
#
.PHONY: all build clean clobber optimized debug fastdebug create_links \
	import import_product import_fastdebug import_debug

