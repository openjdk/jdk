#
# Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

# This must be the first rule
default: all

# Inclusion of this pseudo-target will cause make to execute this file
# serially, regardless of -j. Recursively called makefiles will not be
# affected, however. This is required for correct dependency management.
.NOTPARALLEL:

# Locate this Makefile
ifeq ($(filter /%,$(lastword $(MAKEFILE_LIST))),)
    makefile_path:=$(CURDIR)/$(lastword $(MAKEFILE_LIST))
else
    makefile_path:=$(lastword $(MAKEFILE_LIST))
endif
root_dir:=$(patsubst %/common/makefiles/Makefile,%,$(makefile_path))

# ... and then we can include our helper functions
include $(dir $(makefile_path))/MakeHelpers.gmk

$(eval $(call ParseLogLevel))
$(eval $(call SetupLogging))
$(eval $(call ParseConfAndSpec))

ifneq ($(words $(SPEC)),1)
### We have multiple configurations to build, call make repeatedly
all clean dist-clean:
langtools corba jaxp jaxws hotspot jdk images overlay-images install:
langtools-only corba-only jaxp-only jaxws-only hotspot-only jdk-only images-only overlay-images-only install-only:
clean-langtools clean-corba clean-jaxp clean-jaxws clean-hotspot clean-jdk clean-images:
	@$(foreach spec,$(SPEC),($(MAKE) -f $(makefile_path) SPEC=$(spec) $(VERBOSE) VERBOSE=$(VERBOSE) $@) &&) true

.PHONY: all clean dist-clean
.PHONY: langtools corba jaxp jaxws hotspot jdk images overlay-images install
.PHONY: langtools-only corba-only jaxp-only jaxws-only hotspot-only jdk-only images-only overlay-images-only install-only
.PHONY: clean-langtools clean-corba clean-jaxp clean-jaxws clean-hotspot clean-jdk clean-images

else
### This is the main part of the Makefile, for the normal case with SPEC specifying a single existing spec.gmk file.

# Now load the spec
include $(SPEC)

# Load the vital tools for all the makefiles. 
include $(SRC_ROOT)/common/makefiles/MakeBase.gmk

### Clean up from previous run

# Remove any build.log from a previous run, if they exist
ifneq (,$(BUILD_LOG))
    ifneq (,$(BUILD_LOG_PREVIOUS))
        # Rotate old log
        $(shell $(RM) $(BUILD_LOG_PREVIOUS) 2> /dev/null)
        $(shell $(MV) $(BUILD_LOG) $(BUILD_LOG_PREVIOUS) 2> /dev/null)
    else
        $(shell $(RM) $(BUILD_LOG) 2> /dev/null)
    endif
endif
# Remove any javac server logs and port files. This
# prevents a new make run to reuse the previous servers.
ifneq (,$(SJAVAC_SERVER_DIR))
    $(shell $(MKDIR) -p $(SJAVAC_SERVER_DIR) && $(RM) -rf $(SJAVAC_SERVER_DIR)/*)
endif
# Clean out any notifications from the previous build.
$(shell $(FIND) $(OUTPUT_ROOT) -name "_the.*.notify" $(FIND_DELETE))

# Reset the build timers.
$(eval $(call ResetTimers))

# Setup number of jobs to use. -jN is unfortunately not available for us to parse from the command line,
# hence this workaround.
ifeq ($(JOBS),)
    JOBS=$(NUM_CORES)
endif
MAKE_ARGS:=$(MAKE_ARGS) -j$(JOBS)

### Main targets

all: jdk
	@$(if $(SJAVAC_SERVER_DIR),$(RM) -rf $(SJAVAC_SERVER_DIR)/*.port)
	@$(call AtRootMakeEnd)
.PHONY: all

langtools: start-make langtools-only
langtools-only:
	@$(call MakeStart,langtools,all)
	@($(CD) $(LANGTOOLS_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) $(MAKE_ARGS))
	@$(call MakeFinish,langtools,all)

corba: langtools corba-only
corba-only:
	@$(call MakeStart,corba,all)
	@($(CD) $(CORBA_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) $(MAKE_ARGS))
	@$(call MakeFinish,corba,all)

jaxp: langtools jaxp-only
jaxp-only:
	@$(call MakeStart,jaxp,all)
	@($(CD) $(JAXP_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) $(MAKE_ARGS))
	@$(call MakeFinish,jaxp,all)

jaxws: langtools jaxp jaxws-only
jaxws-only:
	@$(call MakeStart,jaxws,all)
	@($(CD) $(JAXWS_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) $(MAKE_ARGS))
	@$(call MakeFinish,jaxws,all)

hotspot: langtools hotspot-only
hotspot-only:
	@$(call MakeStart,hotspot,all)
	@($(CD) $(SRC_ROOT)/common/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) $(MAKE_ARGS) -f HotspotWrapper.gmk)
	@$(call MakeFinish,hotspot,all)

jdk: langtools corba jaxp jaxws hotspot jdk-only
jdk-only:
	@$(call MakeStart,jdk,all)
	@($(CD) $(JDK_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) $(MAKE_ARGS) $(JDK_TARGET))
	@$(call MakeFinish,jdk,all)

images: source-tips start-make jdk langtools corba jaxp jaxws hotspot images-only
images-only:
	@$(call MakeStart,jdk-images,$@)
	@($(CD) $(JDK_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) $(MAKE_ARGS) images)
	@$(call MakeFinish,jdk-images,$@)
	@$(if $(SJAVAC_SERVER_DIR),$(RM) -rf $(SJAVAC_SERVER_DIR)/*.port)
	@$(call AtRootMakeEnd)

overlay-images: source-tips start-make jdk langtools corba jaxp jaxws hotspot overlay-images-only
overlay-images-only:
	@$(call MakeStart,jdk-overlay-images,$@)
	@($(CD) $(JDK_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) $(MAKE_ARGS) overlay-images)
	@$(call MakeFinish,jdk-overlay-images,$@)
	@$(if $(SJAVAC_SERVER_DIR),$(RM) -rf $(SJAVAC_SERVER_DIR)/*.port)
	@$(call AtRootMakeEnd)

install: source-tips start-make jdk langtools corba jaxp jaxws hotspot install-only
install-only:
	@$(call MakeStart,jdk-images,$@)
	@($(CD) $(JDK_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) $(MAKE_ARGS) install)
	@$(call MakeFinish,jdk-images,$@)
	@$(if $(SJAVAC_SERVER_DIR),$(RM) -rf $(SJAVAC_SERVER_DIR)/*.port)
	@$(call AtRootMakeEnd)

docs: start-make jdk docs-only
docs-only:
	@$(call MakeStart,docs,$@)
	@($(CD) $(SRC_ROOT)/common/makefiles/javadoc && $(BUILD_LOG_WRAPPER) $(MAKE) $(MAKE_ARGS) -f Javadoc.gmk docs)
	@$(call MakeFinish,docs,$@)
	@$(if $(SJAVAC_SERVER_DIR),$(RM) -rf $(SJAVAC_SERVER_DIR)/*.port)
	@$(call AtRootMakeEnd)


.PHONY: langtools corba jaxp jaxws hotspot jdk images install
.PHONY: langtools-only corba-only jaxp-only jaxws-only hotspot-only jdk-only images-only install-only

start-make:
	@$(call AtRootMakeStart)
.PHONY: start-make

bootcycle-images:
	@$(ECHO) Boot cycle build step 1: Building the JDK image normally
	@($(CD) $(SRC_ROOT)/common/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) SPEC=$(SPEC) images)
	@$(ECHO) Boot cycle build step 2: Building a new JDK image using previously built image
	@($(CD) $(SRC_ROOT)/common/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) SPEC=$(dir $(SPEC))bootcycle-spec.gmk images)

test: start-make
	@$(call MakeStart,test,$(if $(TEST),$(TEST),all))
	@($(CD) $(SRC_ROOT)/test && $(BUILD_LOG_WRAPPER) $(MAKE) MAKEFLAGS= -j1 PRODUCT_HOME=$(OUTPUT_ROOT)/jdk JPRT_JAVA_HOME=$(OUTPUT_ROOT)/jdk ALT_OUTPUTDIR=$(OUTPUT_ROOT) $(TEST)) || true
	@$(call MakeFinish,test,$(if $(TEST),$(TEST),all))
	@$(call AtRootMakeEnd)
.PHONY: test


# Stores the tips for each repository. This file is be used when constructing the jdk image and can be
# used to track the exact sources used to build that image.
source-tips: $(OUTPUT_ROOT)/source_tips
$(OUTPUT_ROOT)/source_tips: FRC
	@$(MKDIR) -p $(@D)
	@$(RM) $@
	@$(call GetSourceTips)


# Remove everything, except the output from configure.
clean: clean-langtools clean-corba clean-jaxp clean-jaxws clean-hotspot clean-jdk clean-images clean-bootcycle-build
	@($(CD) $(OUTPUT_ROOT) && $(RM) -r tmp source_tips build.log*)
	@$(ECHO) Cleaned everything except the build configuration.
.PHONY: clean

# Remove everything, you have to rerun configure.
dist-clean:
	@$(RM) -r $(OUTPUT_ROOT)
	@$(ECHO) Cleaned everything, you will have to re-run configure.
.PHONY: dist-clean

clean-langtools:
	$(call CleanComponent,langtools)
clean-corba:
	$(call CleanComponent,corba)
clean-jaxp:
	$(call CleanComponent,jaxp)
clean-jaxws:
	$(call CleanComponent,jaxws)
clean-hotspot:
	$(call CleanComponent,hotspot)
clean-jdk:
	$(call CleanComponent,jdk)
clean-images:
	$(call CleanComponent,images)
clean-bootcycle-build:
	$(call CleanComponent,bootcycle-build)

.PHONY: clean-langtools clean-corba clean-jaxp clean-jaxws clean-hotspot clean-jdk clean-images

endif

# Here are "global" targets, i.e. targets that can be executed without specifying a single configuration.
# If you addd more global targets, please update the fatal-error macro.

help:
	$(info )
	$(info OpenJDK Makefile help)
	$(info =====================)
	$(info )
	$(info Common make targets)
	$(info .  make [all]             # Compile all code but do not create images)
	$(info .  make images            # Create complete j2sdk and j2re images)
	$(info .  make overlay-images    # Create limited images for sparc 64 bit platforms)
	$(info .  make bootcycle-images  # Build images twice, second time with newly build JDK)
	$(info .  make install           # Install the generated images locally)
	$(info .  make clean             # Remove all files generated by make, but not those)
	$(info .                         # generated by configure)
	$(info .  make dist-clean        # Remove all files, including configuration)
	$(info .  make help              # Give some help on using make)
	$(info .  make test              # Run tests, default is all tests (see TEST below))
	$(info )
	$(info Targets for specific components)
	$(info (Component is any of langtools, corba, jaxp, jaxws, hotspot, jdk or images))
	$(info .  make <component>       # Build <component> and everything it depends on. )
	$(info .  make <component>-only  # Build <component> only, without dependencies. This)
	$(info .                         # is faster but can result in incorrect build results!)
	$(info .  make clean-<component> # Remove files generated by make for <component>)
	$(info )
	$(info Useful make variables)
	$(info .  make CONF=             # Build all configurations (note, assignment is empty))
	$(info .  make CONF=<substring>  # Build the configuration(s) with a name matching)
	$(info .                         # <substring>)
	$(info )
	$(info .  make LOG=<loglevel>    # Change the log level from warn to <loglevel>)
	$(info .                         # Available log levels are:)
	$(info .                         # 'warn' (default), 'info', 'debug' and 'trace')
	$(info .                         # To see executed command lines, use LOG=info)
	$(info )
	$(info .  make JOBS=<n>          # Run <n> parallel make jobs)
	$(info .                         # Note that -jN does not work as expected!)
	$(info )
	$(info .  make test TEST=<test>  # Only run the given test or tests, e.g.)
	$(info .                         # make test TEST="jdk_lang jdk_net")
	$(info )
.PHONY: help
FRC: # Force target
