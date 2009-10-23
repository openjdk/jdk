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

# Makefile wrapper around Ant build.xml file

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

ifeq ($(VARIANT), OPT)
  ifneq ($(DEBUG_CLASSFILES), true)
    ANT_OPTIONS += -Djavac.debug=false
  endif
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

# Figure out the platform we are using
_SYSTEM_UNAME := $(shell uname)
_PLATFORM_KIND = unix
ifeq ($(_SYSTEM_UNAME), Windows_NT)
  _PLATFORM_KIND = windows
endif
ifneq (,$(findstring CYGWIN,$(_SYSTEM_UNAME)))
  _PLATFORM_KIND = windows
endif

# Where is /java in case we need it
ifdef ALT_SLASH_JAVA
  _SLASHJAVA = $(ALT_SLASH_JAVA)
else
  ifeq ($(_PLATFORM_KIND), windows)
    _SLASHJAVA=J:/
  else
    _SLASHJAVA=/java
  endif
endif

# Where is /java/devtools in case we need it
ifdef ALT_JDK_DEVTOOLS_DIR
  _DEVTOOLS = $(ALT_JDK_DEVTOOLS_DIR)
else
  _DEVTOOLS = $(_SLASHJAVA)/devtools
endif

# Add in path to devtools
ANT_OPTIONS += -Ddevtools=$(_DEVTOOLS)

ifdef ALT_OUTPUTDIR
  OUTPUTDIR = $(ALT_OUTPUTDIR)
  ANT_OPTIONS += -Doutput.dir=$(ALT_OUTPUTDIR)
else
  OUTPUTDIR = ..
endif

ifdef ALT_LANGTOOLS_DIST
  ifdef ALT_BOOTDIR
    ANT_JAVA_HOME = JAVA_HOME=$(ALT_BOOTDIR)
  endif
  ANT_OPTIONS += -Dbootstrap.dir=$(ALT_LANGTOOLS_DIST)/bootstrap
else
  ifdef ALT_JDK_IMPORT_PATH
    ANT_JAVA_HOME = JAVA_HOME=$(ALT_JDK_IMPORT_PATH)
  endif
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
default: all

# All ant targets of interest
ANT_TARGETS = all source drop_included build dist clobber clean sanity

# Create a make target for each
$(ANT_TARGETS):
	cd .. && $(ANT_JAVA_HOME) $(ANT) -version
	cd .. && $(ANT_JAVA_HOME) $(ANT) $(ANT_OPTIONS) $@

# Help target
define helpenvline
@echo "    $1";echo "        $2"
endef
help:
	@echo "----------------------------------------------------------"
	@echo " "
	@echo "Help information for this Makefile:"
	@echo " "
	@echo "  Targets (see ant project information for descriptions):"
	@echo "    $(ANT_TARGETS)"
	@echo " "
	@echo "  Environment or command line variables (all optional):"
	$(call helpenvline, ALT_BOOTDIR,\
	       "JAVA_HOME to use when running ant")
	$(call helpenvline, ALT_LANGTOOLS_DIST,\
	       "path to langtools repository dist directory")
	$(call helpenvline, ALT_OUTPUTDIR,\
	       "path to root of output")
	$(call helpenvline, DEBUG_CLASSFILES,\
	       "if set makes sure ant property javac.debug is true")
	$(call helpenvline, JAVAC_SOURCE_ARG,\
	       "if SOURCE_LANGUAGE_VERSION not set uses this to set ant property javac.source")
	$(call helpenvline, JAVAC_TARGET_ARG,\
	       "if TARGET_CLASS_VERSION not set uses this to set ant property javac.target")
	$(call helpenvline, SOURCE_LANGUAGE_VERSION,\
	       "if set uses this to set ant property javac.source")
	$(call helpenvline, QUIET,\
	       "if set will pass -quiet to ant")
	$(call helpenvline, TARGET_CLASS_VERSION,\
	       "JAVA_HOME to use when running ant")
	$(call helpenvline, VARIANT,\
	       "if set to OPT means optimized build will set javac.debug to false")
	$(call helpenvline, VERBOSE,\
	       "if set will pass -verbose to ant")
	@echo " "
	@echo "----------------------------------------------------------"
	@echo " "
	@echo "Ant project file help information:"
	@echo " "
	@$(ANT_JAVA_HOME) cd .. && $(ANT) $(ANT_OPTIONS) -p
	@echo " "
	@echo "----------------------------------------------------------"

# Targets for Sun's internal JPRT build system
JPRT_ARCHIVE_BUNDLE=$(OUTPUTDIR)/jprt.zip
jprt_build_product jprt_build_debug jprt_build_fastdebug: all
	$(RM) $(JPRT_ARCHIVE_BUNDLE)
	( cd $(OUTPUTDIR)/dist && \
	   zip -q -r $(JPRT_ARCHIVE_BUNDLE) . )

# Declare these phony (not filenames)
.PHONY: $(ANT_TARGETS) \
	jprt_build_product jprt_build_debug jprt_build_fastdebug
