#
# Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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

# Usage:
#
# $(MAKE) -f buildtree.make ARCH=arch BUILDARCH=buildarch LIBARCH=libarch
#         GAMMADIR=dir OS_FAMILY=os VARIANT=variant
#
# The macros ARCH, GAMMADIR, OS_FAMILY and VARIANT must be defined in the
# environment or on the command-line:
#
# ARCH		- sparc, i486, ... HotSpot cpu and os_cpu source directory
# BUILDARCH     - build directory
# LIBARCH       - the corresponding directory in JDK/JRE
# GAMMADIR	- top of workspace
# OS_FAMILY	- operating system
# VARIANT	- core, compiler1, compiler2, or tiered
# HOTSPOT_RELEASE_VERSION - <major>.<minor>-b<nn> (11.0-b07)
# HOTSPOT_BUILD_VERSION   - internal, internal-$(USER_RELEASE_SUFFIX) or empty
# JRE_RELEASE_VERSION     - <major>.<minor>.<micro> (1.7.0)
#
# Builds the directory trees with makefiles plus some convenience files in
# each directory:
#
# Makefile	- for "make foo"
# flags.make	- with macro settings
# vm.make	- to support making "$(MAKE) -v vm.make" in makefiles
# adlc.make	- 
# jvmti.make	- generate JVMTI bindings from the spec (JSR-163)
# sa.make	- generate SA jar file and natives
# env.[ck]sh	- environment settings
# test_gamma	- script to run the Queens program
# 
# The makefiles are split this way so that "make foo" will run faster by not
# having to read the dependency files for the vm.

-include $(SPEC)
include $(GAMMADIR)/make/scm.make
include $(GAMMADIR)/make/altsrc.make

# 'gmake MAKE_VERBOSE=y' or 'gmake QUIETLY=' gives all the gory details.
QUIETLY$(MAKE_VERBOSE)	= @

# For now, until the compiler is less wobbly:
TESTFLAGS	= -Xbatch -Xmx32m -showversion

### maye ARCH_XXX instead?
ifdef USE_GCC
PLATFORM_FILE	= $(GAMMADIR)/make/$(OS_FAMILY)/platform_$(BUILDARCH).gcc
GCC_LIB         = /usr/local/lib
else
PLATFORM_FILE	= $(GAMMADIR)/make/$(OS_FAMILY)/platform_$(BUILDARCH)
GCC_LIB         = 
endif

ifdef FORCE_TIERED
ifeq		($(VARIANT),tiered)
PLATFORM_DIR	= $(OS_FAMILY)_$(BUILDARCH)_compiler2
else
PLATFORM_DIR	= $(OS_FAMILY)_$(BUILDARCH)_$(VARIANT)
endif
else
PLATFORM_DIR    = $(OS_FAMILY)_$(BUILDARCH)_$(VARIANT)
endif

#
# We do two levels of exclusion in the shared directory.
# TOPLEVEL excludes are pruned, they are not recursively searched,
# but lower level directories can be named without fear of collision.
# ALWAYS excludes are excluded at any level in the directory tree.
#

ALWAYS_EXCLUDE_DIRS     = $(SCM_DIRS)

ifeq		($(VARIANT),tiered)
TOPLEVEL_EXCLUDE_DIRS	= $(ALWAYS_EXCLUDE_DIRS) -o -name adlc -o -name agent
else
ifeq		($(VARIANT),compiler2)
TOPLEVEL_EXCLUDE_DIRS	= $(ALWAYS_EXCLUDE_DIRS) -o -name adlc -o -name c1 -o -name agent
else
# compiler1 and core use the same exclude list
TOPLEVEL_EXCLUDE_DIRS	= $(ALWAYS_EXCLUDE_DIRS) -o -name adlc -o -name opto -o -name libadt -o -name agent
endif
endif

# Get things from the platform file.
COMPILER	= $(shell sed -n 's/^compiler[ 	]*=[ 	]*//p' $(PLATFORM_FILE))

SIMPLE_DIRS	= \
	$(PLATFORM_DIR)/generated/dependencies \
	$(PLATFORM_DIR)/generated/adfiles \
	$(PLATFORM_DIR)/generated/jvmtifiles

TARGETS      = debug fastdebug jvmg optimized product profiled
SUBMAKE_DIRS = $(addprefix $(PLATFORM_DIR)/,$(TARGETS))

# For dependencies and recursive makes.
BUILDTREE_MAKE	= $(GAMMADIR)/make/$(OS_FAMILY)/makefiles/buildtree.make

BUILDTREE_TARGETS = Makefile flags.make flags_vm.make vm.make adlc.make jvmti.make sa.make \
        env.sh env.csh jdkpath.sh .dbxrc test_gamma

BUILDTREE_VARS	= GAMMADIR=$(GAMMADIR) OS_FAMILY=$(OS_FAMILY) \
	ARCH=$(ARCH) BUILDARCH=$(BUILDARCH) LIBARCH=$(LIBARCH) VARIANT=$(VARIANT)

# Define variables to be set in flags.make.
# Default values are set in make/defs.make.
ifeq ($(HOTSPOT_BUILD_VERSION),)
  HS_BUILD_VER=$(HOTSPOT_RELEASE_VERSION)
else
  HS_BUILD_VER=$(HOTSPOT_RELEASE_VERSION)-$(HOTSPOT_BUILD_VERSION)
endif
# Set BUILD_USER from system-dependent hints:  $LOGNAME, $(whoami)
ifndef HOTSPOT_BUILD_USER
  HOTSPOT_BUILD_USER := $(shell echo $$LOGNAME)
endif
ifndef HOTSPOT_BUILD_USER
  HOTSPOT_BUILD_USER := $(shell whoami)
endif
# Define HOTSPOT_VM_DISTRO based on settings in make/openjdk_distro
# or make/hotspot_distro.
ifndef HOTSPOT_VM_DISTRO
  ifeq ($(call if-has-altsrc,$(HS_COMMON_SRC)/,true,false),true)
    include $(GAMMADIR)/make/hotspot_distro
  else
    include $(GAMMADIR)/make/openjdk_distro
  endif
endif

BUILDTREE_VARS += HOTSPOT_RELEASE_VERSION=$(HS_BUILD_VER) HOTSPOT_BUILD_VERSION= JRE_RELEASE_VERSION=$(JRE_RELEASE_VERSION) 

BUILDTREE	= \
	$(MAKE) -f $(BUILDTREE_MAKE) $(BUILDTREE_TARGETS) $(BUILDTREE_VARS)

BUILDTREE_COMMENT	= echo "\# Generated by $(BUILDTREE_MAKE)"

all:  $(SUBMAKE_DIRS)

# Run make in each subdirectory recursively.
$(SUBMAKE_DIRS): $(SIMPLE_DIRS) FORCE
	$(QUIETLY) [ -d $@ ] || { mkdir -p $@; }
	$(QUIETLY) cd $@ && $(BUILDTREE) TARGET=$(@F)
	$(QUIETLY) touch $@

$(SIMPLE_DIRS):
	$(QUIETLY) mkdir -p $@

# Convenience macro which takes a source relative path, applies $(1) to the
# absolute path, and then replaces $(GAMMADIR) in the result with a 
# literal "$(GAMMADIR)/" suitable for inclusion in a Makefile.  
gamma-path=$(subst $(GAMMADIR),\$$(GAMMADIR),$(call $(1),$(HS_COMMON_SRC)/$(2)))

flags.make: $(BUILDTREE_MAKE) ../shared_dirs.lst
	@echo Creating $@ ...
	$(QUIETLY) ( \
	$(BUILDTREE_COMMENT); \
	echo; \
	echo "Platform_file = $(PLATFORM_FILE)" | sed 's|$(GAMMADIR)|$$(GAMMADIR)|'; \
	sed -n '/=/s/^ */Platform_/p' < $(PLATFORM_FILE); \
	echo; \
	echo "GAMMADIR = $(GAMMADIR)"; \
	echo "SYSDEFS = \$$(Platform_sysdefs)"; \
	echo "SRCARCH = $(ARCH)"; \
	echo "BUILDARCH = $(BUILDARCH)"; \
	echo "LIBARCH = $(LIBARCH)"; \
	echo "TARGET = $(TARGET)"; \
	echo "HS_BUILD_VER = $(HS_BUILD_VER)"; \
	echo "JRE_RELEASE_VER = $(JRE_RELEASE_VERSION)"; \
	echo "SA_BUILD_VERSION = $(HS_BUILD_VER)"; \
	echo "HOTSPOT_BUILD_USER = $(HOTSPOT_BUILD_USER)"; \
	echo "HOTSPOT_VM_DISTRO = $(HOTSPOT_VM_DISTRO)"; \
	echo "$(LP64_SETTING/$(DATA_MODE))"; \
	echo; \
	echo "# Used for platform dispatching"; \
	echo "TARGET_DEFINES  = -DTARGET_OS_FAMILY_\$$(Platform_os_family)"; \
	echo "TARGET_DEFINES += -DTARGET_ARCH_\$$(Platform_arch)"; \
	echo "TARGET_DEFINES += -DTARGET_ARCH_MODEL_\$$(Platform_arch_model)"; \
	echo "TARGET_DEFINES += -DTARGET_OS_ARCH_\$$(Platform_os_arch)"; \
	echo "TARGET_DEFINES += -DTARGET_OS_ARCH_MODEL_\$$(Platform_os_arch_model)"; \
	echo "TARGET_DEFINES += -DTARGET_COMPILER_\$$(Platform_compiler)"; \
	echo "CFLAGS += \$$(TARGET_DEFINES)"; \
	echo; \
	echo "Src_Dirs_V = \\"; \
	sed 's/$$/ \\/;s|$(GAMMADIR)|$$(GAMMADIR)|' ../shared_dirs.lst; \
	echo "$(call gamma-path,altsrc,cpu/$(ARCH)/vm) \\"; \
	echo "$(call gamma-path,commonsrc,cpu/$(ARCH)/vm) \\"; \
	echo "$(call gamma-path,altsrc,os_cpu/$(OS_FAMILY)_$(ARCH)/vm) \\"; \
	echo "$(call gamma-path,commonsrc,os_cpu/$(OS_FAMILY)_$(ARCH)/vm) \\"; \
	echo "$(call gamma-path,altsrc,os/$(OS_FAMILY)/vm) \\"; \
	echo "$(call gamma-path,commonsrc,os/$(OS_FAMILY)/vm) \\"; \
	echo "$(call gamma-path,altsrc,os/posix/vm) \\"; \
	echo "$(call gamma-path,commonsrc,os/posix/vm)"; \
	echo; \
	echo "Src_Dirs_I = \\"; \
	echo "$(call gamma-path,altsrc,share/vm/prims) \\"; \
	echo "$(call gamma-path,commonsrc,share/vm/prims) \\"; \
	echo "$(call gamma-path,altsrc,share/vm) \\"; \
	echo "$(call gamma-path,commonsrc,share/vm) \\"; \
	echo "$(call gamma-path,altsrc,share/vm/precompiled) \\"; \
	echo "$(call gamma-path,commonsrc,share/vm/precompiled) \\"; \
	echo "$(call gamma-path,altsrc,cpu/$(ARCH)/vm) \\"; \
	echo "$(call gamma-path,commonsrc,cpu/$(ARCH)/vm) \\"; \
	echo "$(call gamma-path,altsrc,os_cpu/$(OS_FAMILY)_$(ARCH)/vm) \\"; \
	echo "$(call gamma-path,commonsrc,os_cpu/$(OS_FAMILY)_$(ARCH)/vm) \\"; \
	echo "$(call gamma-path,altsrc,os/$(OS_FAMILY)/vm) \\"; \
	echo "$(call gamma-path,commonsrc,os/$(OS_FAMILY)/vm) \\"; \
	echo "$(call gamma-path,altsrc,os/posix/vm) \\"; \
	echo "$(call gamma-path,commonsrc,os/posix/vm)"; \
	[ -n "$(CFLAGS_BROWSE)" ] && \
	    echo && echo "CFLAGS_BROWSE = $(CFLAGS_BROWSE)"; \
	[ -n "$(ENABLE_FULL_DEBUG_SYMBOLS)" ] && \
	    echo && echo "ENABLE_FULL_DEBUG_SYMBOLS = $(ENABLE_FULL_DEBUG_SYMBOLS)"; \
	[ -n "$(OBJCOPY)" ] && \
	    echo && echo "OBJCOPY = $(OBJCOPY)"; \
	[ -n "$(STRIP_POLICY)" ] && \
	    echo && echo "STRIP_POLICY = $(STRIP_POLICY)"; \
	[ -n "$(ZIP_DEBUGINFO_FILES)" ] && \
	    echo && echo "ZIP_DEBUGINFO_FILES = $(ZIP_DEBUGINFO_FILES)"; \
	[ -n "$(ZIPEXE)" ] && \
	    echo && echo "ZIPEXE = $(ZIPEXE)"; \
	[ -n "$(HOTSPOT_EXTRA_SYSDEFS)" ] && \
	    echo && \
	    echo "HOTSPOT_EXTRA_SYSDEFS\$$(HOTSPOT_EXTRA_SYSDEFS) = $(HOTSPOT_EXTRA_SYSDEFS)" && \
	    echo "SYSDEFS += \$$(HOTSPOT_EXTRA_SYSDEFS)"; \
	echo; \
	[ -n "$(SPEC)" ] && \
	    echo "include $(SPEC)"; \
	echo "include \$$(GAMMADIR)/make/$(OS_FAMILY)/makefiles/$(VARIANT).make"; \
	echo "include \$$(GAMMADIR)/make/$(OS_FAMILY)/makefiles/$(COMPILER).make"; \
	) > $@

flags_vm.make: $(BUILDTREE_MAKE) ../shared_dirs.lst
	@echo Creating $@ ...
	$(QUIETLY) ( \
	$(BUILDTREE_COMMENT); \
	echo; \
	[ "$(TARGET)" = profiled ] && \
	echo "include \$$(GAMMADIR)/make/$(OS_FAMILY)/makefiles/optimized.make"; \
	echo "include \$$(GAMMADIR)/make/$(OS_FAMILY)/makefiles/$(TARGET).make"; \
	) > $@

../shared_dirs.lst:  $(BUILDTREE_MAKE) $(GAMMADIR)/src/share/vm
	@echo Creating directory list $@
	$(QUIETLY) if [ -d $(HS_ALT_SRC)/share/vm ]; then \
          find $(HS_ALT_SRC)/share/vm/* -prune \
	  -type d \! \( $(TOPLEVEL_EXCLUDE_DIRS) \) -exec find {} \
          \( $(ALWAYS_EXCLUDE_DIRS) \) -prune -o -type d -print \; > $@; \
        fi;
	$(QUIETLY) find $(HS_COMMON_SRC)/share/vm/* -prune \
	-type d \! \( $(TOPLEVEL_EXCLUDE_DIRS) \) -exec find {} \
        \( $(ALWAYS_EXCLUDE_DIRS) \) -prune -o -type d -print \; >> $@

Makefile: $(BUILDTREE_MAKE)
	@echo Creating $@ ...
	$(QUIETLY) ( \
	$(BUILDTREE_COMMENT); \
	echo; \
	echo include flags.make; \
	echo; \
	echo "include \$$(GAMMADIR)/make/$(OS_FAMILY)/makefiles/top.make"; \
	) > $@

vm.make: $(BUILDTREE_MAKE)
	@echo Creating $@ ...
	$(QUIETLY) ( \
	$(BUILDTREE_COMMENT); \
	echo; \
	echo include flags.make; \
	echo include flags_vm.make; \
	echo; \
	echo "include \$$(GAMMADIR)/make/$(OS_FAMILY)/makefiles/$(@F)"; \
	) > $@

adlc.make: $(BUILDTREE_MAKE)
	@echo Creating $@ ...
	$(QUIETLY) ( \
	$(BUILDTREE_COMMENT); \
	echo; \
	echo include flags.make; \
	echo; \
	echo "include \$$(GAMMADIR)/make/$(OS_FAMILY)/makefiles/$(@F)"; \
	) > $@

jvmti.make: $(BUILDTREE_MAKE)
	@echo Creating $@ ...
	$(QUIETLY) ( \
	$(BUILDTREE_COMMENT); \
	echo; \
	echo include flags.make; \
	echo; \
	echo "include \$$(GAMMADIR)/make/$(OS_FAMILY)/makefiles/$(@F)"; \
	) > $@

sa.make: $(BUILDTREE_MAKE)
	@echo Creating $@ ...
	$(QUIETLY) ( \
	$(BUILDTREE_COMMENT); \
	echo; \
	echo include flags.make; \
	echo; \
	echo "include \$$(GAMMADIR)/make/$(OS_FAMILY)/makefiles/$(@F)"; \
	) > $@

env.sh: $(BUILDTREE_MAKE)
	@echo Creating $@ ...
	$(QUIETLY) ( \
	$(BUILDTREE_COMMENT); \
	[ -n "$$JAVA_HOME" ] && { echo ": \$${JAVA_HOME:=$${JAVA_HOME}}"; }; \
	{ \
	echo "CLASSPATH=$${CLASSPATH:+$$CLASSPATH:}.:\$${JAVA_HOME}/jre/lib/rt.jar:\$${JAVA_HOME}/jre/lib/i18n.jar"; \
	} | sed s:$${JAVA_HOME:--------}:\$${JAVA_HOME}:g; \
	echo "HOTSPOT_BUILD_USER=\"$${LOGNAME:-$$USER} in `basename $(GAMMADIR)`\""; \
	echo "export JAVA_HOME LD_LIBRARY_PATH CLASSPATH HOTSPOT_BUILD_USER"; \
	) > $@

env.csh: env.sh
	@echo Creating $@ ...
	$(QUIETLY) ( \
	$(BUILDTREE_COMMENT); \
	[ -n "$$JAVA_HOME" ] && \
	{ echo "if (! \$$?JAVA_HOME) setenv JAVA_HOME \"$$JAVA_HOME\""; }; \
	sed -n 's/^\([A-Za-z_][A-Za-z0-9_]*\)=/setenv \1 /p' $?; \
	) > $@

jdkpath.sh: $(BUILDTREE_MAKE)
	@echo Creating $@ ...
	$(QUIETLY) ( \
	$(BUILDTREE_COMMENT); \
	echo "JDK=${JAVA_HOME}"; \
	) > $@	   

.dbxrc:  $(BUILDTREE_MAKE)
	@echo Creating $@ ...
	$(QUIETLY) ( \
	echo "echo '# Loading $(PLATFORM_DIR)/$(TARGET)/.dbxrc'"; \
	echo "if [ -f \"\$${HOTSPOT_DBXWARE}\" ]"; \
	echo "then"; \
	echo "	source \"\$${HOTSPOT_DBXWARE}\""; \
	echo "elif [ -f \"\$$HOME/.dbxrc\" ]"; \
	echo "then"; \
	echo "	source \"\$$HOME/.dbxrc\""; \
	echo "fi"; \
	) > $@

# Skip the test for product builds (which only work when installed in a JDK), to
# avoid exiting with an error and causing make to halt.
NO_TEST_MSG	= \
	echo "$@:  skipping the test--this build must be tested in a JDK."

NO_JAVA_HOME_MSG	= \
	echo "JAVA_HOME must be set to run this test."

DATA_MODE = $(DATA_MODE/$(BUILDARCH))
JAVA_FLAG = $(JAVA_FLAG/$(DATA_MODE))

DATA_MODE/i486    = 32
DATA_MODE/sparc   = 32
DATA_MODE/sparcv9 = 64
DATA_MODE/amd64   = 64
DATA_MODE/ia64    = 64

# This bit is needed to enable local rebuilds.
# Unless the makefile itself sets LP64, any environmental
# setting of LP64 will interfere with the build.
LP64_SETTING/32 = LP64 = \#empty
LP64_SETTING/64 = LP64 = 1

JAVA_FLAG/32 = -d32
JAVA_FLAG/64 = -d64

WRONG_DATA_MODE_MSG = \
	echo "JAVA_HOME must point to a $(DATA_MODE)-bit OpenJDK."

CROSS_COMPILING_MSG = \
	echo "Cross compiling for ARCH $(CROSS_COMPILE_ARCH), skipping gamma run."

test_gamma:  $(BUILDTREE_MAKE) $(GAMMADIR)/make/test/Queens.java
	@echo Creating $@ ...
	$(QUIETLY) ( \
	echo "#!/bin/sh"; \
	echo ""; \
	$(BUILDTREE_COMMENT); \
	echo ""; \
	echo "# Include environment settings for gamma run"; \
	echo ""; \
	echo ". ./env.sh"; \
	echo ""; \
	echo "# Do not run gamma test for cross compiles"; \
	echo ""; \
	echo "if [ -n \"$(CROSS_COMPILE_ARCH)\" ]; then "; \
	echo "  $(CROSS_COMPILING_MSG)"; \
	echo "  exit 0"; \
	echo "fi"; \
	echo ""; \
	echo "# Make sure JAVA_HOME is set as it is required for gamma"; \
	echo ""; \
	echo "if [ -z \"\$${JAVA_HOME}\" ]; then "; \
	echo "  $(NO_JAVA_HOME_MSG)"; \
	echo "  exit 0"; \
	echo "fi"; \
	echo ""; \
	echo "# Check JAVA_HOME version to be used for the test"; \
	echo ""; \
	echo "\$${JAVA_HOME}/bin/java $(JAVA_FLAG) -fullversion > /dev/null 2>&1"; \
	echo "if [ \$$? -ne 0 ]; then "; \
	echo "  $(WRONG_DATA_MODE_MSG)"; \
	echo "  exit 0"; \
	echo "fi"; \
	echo ""; \
	echo "# Use gamma_g if it exists"; \
	echo ""; \
	echo "GAMMA_PROG=gamma"; \
	echo "if [ -f gamma_g ]; then "; \
	echo "  GAMMA_PROG=gamma_g"; \
	echo "fi"; \
	echo ""; \
	echo "if [ \"$(OS_VENDOR)\" = \"Darwin\" ]; then "; \
	echo "  # Ensure architecture for gamma and JAVA_HOME is the same."; \
	echo "  # NOTE: gamma assumes the OpenJDK directory layout."; \
	echo ""; \
	echo "  GAMMA_ARCH=\"\`file \$${GAMMA_PROG} | awk '{print \$$NF}'\`\""; \
	echo "  JVM_LIB=\"\$${JAVA_HOME}/jre/lib/libjava.$(LIBRARY_SUFFIX)\""; \
	echo "  if [ ! -f \$${JVM_LIB} ]; then"; \
	echo "    JVM_LIB=\"\$${JAVA_HOME}/jre/lib/$${LIBARCH}/libjava.$(LIBRARY_SUFFIX)\""; \
	echo "  fi"; \
	echo "  if [ ! -f \$${JVM_LIB} ] || [ -z \"\`file \$${JVM_LIB} | grep \$${GAMMA_ARCH}\`\" ]; then "; \
	echo "    $(WRONG_DATA_MODE_MSG)"; \
	echo "    exit 0"; \
	echo "  fi"; \
	echo "fi"; \
	echo ""; \
	echo "# Compile Queens program for test"; \
	echo ""; \
	echo "rm -f Queens.class"; \
	echo "\$${JAVA_HOME}/bin/javac -d . $(GAMMADIR)/make/test/Queens.java"; \
	echo ""; \
	echo "# Set library path solely for gamma launcher test run"; \
	echo ""; \
	echo "LD_LIBRARY_PATH=.:$${LD_LIBRARY_PATH:+$$LD_LIBRARY_PATH:}\$${JAVA_HOME}/jre/lib/${LIBARCH}/native_threads:\$${JAVA_HOME}/jre/lib/${LIBARCH}:${GCC_LIB}"; \
	echo "export LD_LIBRARY_PATH"; \
	echo "unset LD_LIBRARY_PATH_32"; \
	echo "unset LD_LIBRARY_PATH_64"; \
	echo ""; \
	echo "if [ \"$(OS_VENDOR)\" = \"Darwin\" ]; then "; \
	echo "  DYLD_LIBRARY_PATH=.:$${DYLD_LIBRARY_PATH:+$$DYLD_LIBRARY_PATH:}\$${JAVA_HOME}/jre/lib/native_threads:\$${JAVA_HOME}/jre/lib:$${DYLD_LIBRARY_PATH:+$$DYLD_LIBRARY_PATH:}\$${JAVA_HOME}/jre/lib/${LIBARCH}/native_threads:\$${JAVA_HOME}/jre/lib/${LIBARCH}:${GCC_LIB}"; \
	echo "  export DYLD_LIBRARY_PATH"; \
	echo "fi"; \
	echo ""; \
	echo "# Use the gamma launcher and JAVA_HOME to run the test"; \
	echo ""; \
	echo "./\$${GAMMA_PROG} $(TESTFLAGS) Queens < /dev/null"; \
	) > $@
	$(QUIETLY) chmod +x $@

FORCE:

.PHONY:  all FORCE
