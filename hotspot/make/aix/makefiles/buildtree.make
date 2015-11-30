#
# Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
# Copyright 2012, 2013 SAP AG. All rights reserved.
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
# $(MAKE) -f buildtree.make SRCARCH=srcarch BUILDARCH=buildarch LIBARCH=libarch
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
# HOTSPOT_RELEASE_VERSION - <major_ver>.<minor_ver>.<micro_ver>[-<identifier>][-<debug_target>][-b<nn>]
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
# trace.make	- generate tracing event and type definitions
# jvmti.make	- generate JVMTI bindings from the spec (JSR-163)
#
# The makefiles are split this way so that "make foo" will run faster by not
# having to read the dependency files for the vm.

-include $(SPEC)
include $(GAMMADIR)/make/scm.make
include $(GAMMADIR)/make/defs.make
include $(GAMMADIR)/make/altsrc.make


# 'gmake MAKE_VERBOSE=y' or 'gmake QUIETLY=' gives all the gory details.
QUIETLY$(MAKE_VERBOSE)	= @

ifeq ($(findstring true, $(JVM_VARIANT_ZERO) $(JVM_VARIANT_ZEROSHARK)), true)
  PLATFORM_FILE = $(shell dirname $(shell dirname $(shell pwd)))/platform_zero
else
  ifdef USE_SUNCC
    PLATFORM_FILE = $(GAMMADIR)/make/$(OS_FAMILY)/platform_$(BUILDARCH).suncc
  else
    PLATFORM_FILE = $(GAMMADIR)/make/$(OS_FAMILY)/platform_$(BUILDARCH)
  endif
endif

# Allow overriding of the arch part of the directory but default
# to BUILDARCH if nothing is specified
ifeq ($(VARIANTARCH),)
  VARIANTARCH=$(BUILDARCH)
endif

ifdef FORCE_TIERED
ifeq		($(VARIANT),tiered)
PLATFORM_DIR	= $(OS_FAMILY)_$(VARIANTARCH)_compiler2
else
PLATFORM_DIR	= $(OS_FAMILY)_$(VARIANTARCH)_$(VARIANT)
endif
else
PLATFORM_DIR    = $(OS_FAMILY)_$(VARIANTARCH)_$(VARIANT)
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
	$(PLATFORM_DIR)/generated/jvmtifiles \
	$(PLATFORM_DIR)/generated/tracefiles

TARGETS      = debug fastdebug optimized product
SUBMAKE_DIRS = $(addprefix $(PLATFORM_DIR)/,$(TARGETS))

# For dependencies and recursive makes.
BUILDTREE_MAKE	= $(GAMMADIR)/make/$(OS_FAMILY)/makefiles/buildtree.make

BUILDTREE_TARGETS = Makefile flags.make flags_vm.make vm.make adlc.make jvmti.make trace.make

BUILDTREE_VARS	= GAMMADIR=$(GAMMADIR) OS_FAMILY=$(OS_FAMILY) \
	SRCARCH=$(SRCARCH) BUILDARCH=$(BUILDARCH) LIBARCH=$(LIBARCH) VARIANT=$(VARIANT)

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

# if hotspot-only build and/or OPENJDK isn't passed down, need to set OPENJDK
ifndef OPENJDK
  ifneq ($(call if-has-altsrc,$(HS_COMMON_SRC)/,true,false),true)
    OPENJDK=true
  endif
endif

BUILDTREE_VARS += HOTSPOT_RELEASE_VERSION=$(HS_BUILD_VER) HOTSPOT_BUILD_VERSION=  JRE_RELEASE_VERSION=$(JRE_RELEASE_VERSION)

BUILDTREE	= \
	$(MAKE) -f $(BUILDTREE_MAKE) $(BUILDTREE_TARGETS) $(BUILDTREE_VARS)

BUILDTREE_COMMENT	= echo "\# Generated by $(BUILDTREE_MAKE)"

all:  $(SUBMAKE_DIRS)

# Run make in each subdirectory recursively.
$(SUBMAKE_DIRS): $(SIMPLE_DIRS) FORCE
	$(QUIETLY) [ -d $@ ] || { mkdir -p $@; }
	+$(QUIETLY) cd $@ && $(BUILDTREE) TARGET=$(@F)
	$(QUIETLY) touch $@

$(SIMPLE_DIRS):
	$(QUIETLY) mkdir -p $@

# Convenience macro which takes a source relative path, applies $(1) to the
# absolute path, and then replaces $(GAMMADIR) in the result with a
# literal "$(GAMMADIR)/" suitable for inclusion in a Makefile.
gamma-path=$(subst $(GAMMADIR),\$$(GAMMADIR),$(call $(1),$(HS_COMMON_SRC)/$(2)))

# This bit is needed to enable local rebuilds.
# Unless the makefile itself sets LP64, any environmental
# setting of LP64 will interfere with the build.
LP64_SETTING/32 = LP64 = \#empty
LP64_SETTING/64 = LP64 = 1

DATA_MODE/ppc64 = 64

DATA_MODE = $(DATA_MODE/$(BUILDARCH))

flags.make: $(BUILDTREE_MAKE) ../shared_dirs.lst
	@echo $(LOG_INFO) Creating $@ ...
	$(QUIETLY) ( \
	$(BUILDTREE_COMMENT); \
	echo; \
	echo "Platform_file = $(PLATFORM_FILE)" | sed 's|$(GAMMADIR)|$$(GAMMADIR)|'; \
	sed -n '/=/s/^ */Platform_/p' < $(PLATFORM_FILE); \
	echo; \
	echo "GAMMADIR = $(GAMMADIR)"; \
	echo "HS_ALT_MAKE = $(HS_ALT_MAKE)"; \
	echo "OSNAME = $(OSNAME)"; \
	echo "SYSDEFS = \$$(Platform_sysdefs)"; \
	echo "SRCARCH = $(SRCARCH)"; \
	echo "BUILDARCH = $(BUILDARCH)"; \
	echo "LIBARCH = $(LIBARCH)"; \
	echo "TARGET = $(TARGET)"; \
	echo "HS_BUILD_VER = $(HS_BUILD_VER)"; \
	echo "JRE_RELEASE_VER = $(JRE_RELEASE_VERSION)"; \
	echo "HOTSPOT_BUILD_USER = $(HOTSPOT_BUILD_USER)"; \
	echo "HOTSPOT_VM_DISTRO = $(HOTSPOT_VM_DISTRO)"; \
	echo "OPENJDK = $(OPENJDK)"; \
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
	echo "$(call gamma-path,altsrc,cpu/$(SRCARCH)/vm) \\"; \
	echo "$(call gamma-path,commonsrc,cpu/$(SRCARCH)/vm) \\"; \
	echo "$(call gamma-path,altsrc,os_cpu/$(OS_FAMILY)_$(SRCARCH)/vm) \\"; \
	echo "$(call gamma-path,commonsrc,os_cpu/$(OS_FAMILY)_$(SRCARCH)/vm) \\"; \
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
	echo "$(call gamma-path,altsrc,cpu/$(SRCARCH)/vm) \\"; \
	echo "$(call gamma-path,commonsrc,cpu/$(SRCARCH)/vm) \\"; \
	echo "$(call gamma-path,altsrc,os_cpu/$(OS_FAMILY)_$(SRCARCH)/vm) \\"; \
	echo "$(call gamma-path,commonsrc,os_cpu/$(OS_FAMILY)_$(SRCARCH)/vm) \\"; \
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
	[ -n "$(INCLUDE_TRACE)" ] && \
	    echo && echo "INCLUDE_TRACE = $(INCLUDE_TRACE)"; \
	echo; \
	[ -n "$(SPEC)" ] && \
	    echo "include $(SPEC)"; \
	echo "CP ?= cp"; \
	echo "MV ?= mv"; \
	echo "include \$$(GAMMADIR)/make/$(OS_FAMILY)/makefiles/$(VARIANT).make"; \
	echo "include \$$(GAMMADIR)/make/excludeSrc.make"; \
	echo "include \$$(GAMMADIR)/make/$(OS_FAMILY)/makefiles/$(COMPILER).make"; \
	) > $@

flags_vm.make: $(BUILDTREE_MAKE) ../shared_dirs.lst
	@echo $(LOG_INFO) Creating $@ ...
	$(QUIETLY) ( \
	$(BUILDTREE_COMMENT); \
	echo; \
	echo "include \$$(GAMMADIR)/make/$(OS_FAMILY)/makefiles/$(TARGET).make"; \
	) > $@

../shared_dirs.lst:  $(BUILDTREE_MAKE) $(GAMMADIR)/src/share/vm
	@echo $(LOG_INFO) Creating directory list $@
	$(QUIETLY) if [ -d $(HS_ALT_SRC)/share/vm ]; then \
          find $(HS_ALT_SRC)/share/vm/* -prune \
	  -type d \! \( $(TOPLEVEL_EXCLUDE_DIRS) \) -exec find {} \
          \( $(ALWAYS_EXCLUDE_DIRS) \) -prune -o -type d -print \; > $@; \
        fi;
	$(QUIETLY) find $(HS_COMMON_SRC)/share/vm/* -prune \
	-type d \! \( $(TOPLEVEL_EXCLUDE_DIRS) \) -exec find {} \
        \( $(ALWAYS_EXCLUDE_DIRS) \) -prune -o -type d -print \; >> $@

Makefile: $(BUILDTREE_MAKE)
	@echo $(LOG_INFO) Creating $@ ...
	$(QUIETLY) ( \
	$(BUILDTREE_COMMENT); \
	echo; \
	echo include flags.make; \
	echo; \
	echo "include \$$(GAMMADIR)/make/$(OS_FAMILY)/makefiles/top.make"; \
	) > $@

vm.make: $(BUILDTREE_MAKE)
	@echo $(LOG_INFO) Creating $@ ...
	$(QUIETLY) ( \
	$(BUILDTREE_COMMENT); \
	echo; \
	echo include flags.make; \
	echo include flags_vm.make; \
	echo; \
	echo "include \$$(GAMMADIR)/make/$(OS_FAMILY)/makefiles/$(@F)"; \
	) > $@

adlc.make: $(BUILDTREE_MAKE)
	@echo $(LOG_INFO) Creating $@ ...
	$(QUIETLY) ( \
	$(BUILDTREE_COMMENT); \
	echo; \
	echo include flags.make; \
	echo; \
	echo "include \$$(GAMMADIR)/make/$(OS_FAMILY)/makefiles/$(@F)"; \
	) > $@

jvmti.make: $(BUILDTREE_MAKE)
	@echo $(LOG_INFO) Creating $@ ...
	$(QUIETLY) ( \
	$(BUILDTREE_COMMENT); \
	echo; \
	echo include flags.make; \
	echo; \
	echo "include \$$(GAMMADIR)/make/$(OS_FAMILY)/makefiles/$(@F)"; \
	) > $@

trace.make: $(BUILDTREE_MAKE)
	@echo $(LOG_INFO) Creating $@ ...
	$(QUIETLY) ( \
	$(BUILDTREE_COMMENT); \
	echo; \
	echo include flags.make; \
	echo; \
	echo "include \$$(GAMMADIR)/make/$(OS_FAMILY)/makefiles/$(@F)"; \
	) > $@

FORCE:

.PHONY:  all FORCE

.NOTPARALLEL:
