#
# Copyright 2007-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

# Makefile for jaxp: wrapper around Ant build.xml file

#
# On Solaris, the 'make' utility from Sun will not work with these makefiles.
#    This little rule is only understood by Sun's make, and is harmless
#    when seen by the GNU make tool. If using Sun's make, this causes the
#    make command to fail.
#
SUN_MAKE_TEST:sh = @echo "ERROR: PLEASE USE GNU VERSION OF MAKE"; exit 33

ifdef QUIET
  ANT_OPTIONS += -quiet
endif

ifdef VERBOSE
  ANT_OPTIONS += -verbose
endif

ifdef JDK_VERSION
  ANT_OPTIONS += -Djdk.version=$(JDK_VERSION)
endif 

ifdef FULL_VERSION
  ANT_OPTIONS += -Dfull.version='$(FULL_VERSION)' # will contain spaces
endif 

ifdef MILESTONE
  ANT_OPTIONS += -Dmilestone=$(MILESTONE)
endif

ifdef BUILD_NUMBER
  ANT_OPTIONS += -Dbuild.number=$(BUILD_NUMBER)
else
  ifdef JDK_BUILD_NUMBER
    ANT_OPTIONS += -Dbuild.number=$(JDK_BUILD_NUMBER)
  endif
endif

ifeq ($(VARIANT), DBG)
  ANT_OPTIONS += -Djavac.debug=true
else
  ifeq ($(VARIANT), OPT)
    ANT_OPTIONS += -Djavac.debug=false
  endif
endif

ifeq ($(DEBUG_CLASSFILES), true)
  ANT_OPTIONS += -Djavac.debug=true
endif

# Note: jdk/make/common/Defs.gmk uses LANGUAGE_VERSION (-source NN)
# and the somewhat misnamed CLASS_VERSION (-target NN)
ifdef TARGET_CLASS_VERSION
  ANT_OPTIONS += -Djavac.target=$(TARGET_CLASS_VERSION)
else
  ifdef JAVAC_TARGET_ARG
    ANT_OPTIONS += -Djavac.target=$(JAVAC_TARGET_ARG)
  endif
endif 

ifdef SOURCE_LANGUAGE_VERSION
  ANT_OPTIONS += -Djavac.source=$(SOURCE_LANGUAGE_VERSION)
else
  ifdef JAVAC_SOURCE_ARG 
    ANT_OPTIONS += -Djavac.source=$(JAVAC_SOURCE_ARG)
  endif
endif 

ifdef ALT_BOOTDIR
  ANT_JAVA_HOME = JAVA_HOME=$(ALT_BOOTDIR)
endif

ifdef ALT_OUTPUTDIR
  OUTPUTDIR = $(ALT_OUTPUTDIR)
  ANT_OPTIONS += -Dbuild.dir=$(ALT_OUTPUTDIR)/build
  ANT_OPTIONS += -Ddist.dir=$(ALT_OUTPUTDIR)/dist
else
  OUTPUTDIR = ..
endif

ifdef ALT_LANGTOOLS_DIST
  ANT_OPTIONS += -Dbootstrap.dir=$(ALT_LANGTOOLS_DIST)/bootstrap
endif

ifdef FINDBUGS_HOME
  ANT_OPTIONS += -Dfindbugs.home=$(FINDBUGS_HOME)
endif

ifdef ANT_HOME
  ANT = $(ANT_HOME)/bin/ant
  ifneq ($(shell test -x $(ANT); echo $$?), 0)
    $(error "$(ANT) not found; please update ANT_HOME")
  endif
else
  ANT = ant
  ifneq ($(shell which $(ANT) > /dev/null; echo $$?), 0)
    $(error "\"ant\" not found; please set ANT_HOME or put \"ant\" on your PATH")
  endif
endif

# Default target and expected 'do everything' target
all: build

# Standard make clobber target
clobber: clean

# All ant targets of interest
ANT_TARGETS = build clean sanity # for now

# Create a make target for each
$(ANT_TARGETS):
	$(ANT_JAVA_HOME) $(ANT) -version
	$(ANT_JAVA_HOME) $(ANT) $(ANT_OPTIONS) $@

# Targets for Sun's internal JPRT build system
JPRT_ARCHIVE_BUNDLE=$(OUTPUTDIR)/jprt.zip
jprt_build_product jprt_build_debug jprt_build_fastdebug: all
	$(RM) $(JPRT_ARCHIVE_BUNDLE)
	( cd $(OUTPUTDIR)/dist && \
	  zip -q -r $(JPRT_ARCHIVE_BUNDLE) . )

# Declare these phony (not filenames)
.PHONY: $(ANT_TARGETS) all clobber \
	jprt_build_product jprt_build_debug jprt_build_fastdebug
