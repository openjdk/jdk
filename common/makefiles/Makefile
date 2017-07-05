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

# Setup number of jobs to use. -jN is unfortunately not available for us to parse from the command line,
# hence this workaround.
ifeq ($(JOBS),)
    JOBS=$(NUM_CORES)
endif

ifneq ($(words $(SPEC)),1)
### We have multiple configurations to build, call make repeatedly
all jdk hotspot jaxws jaxp corba langtools install images clean dist-clean:
	@$(foreach spec,$(SPEC),($(MAKE) -f $(makefile_path) SPEC=$(spec) $(VERBOSE) VERBOSE=$(VERBOSE) $@ $(MAKE_ARGS)) &&) true

.PHONY: all jdk hotspot jaxws jaxp corba langtools install images clean dist-clean

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
ifneq (,$(JAVAC_SERVERS))
    $(shell mkdir -p $(JAVAC_SERVERS) && rm -rf $(JAVAC_SERVERS)/*)
endif
# Clean out any notifications from the previous build.
$(shell find $(OUTPUT_ROOT) -name "_the.*.notify" $(FIND_DELETE))

# Reset the build timers.
$(eval $(call ResetTimers))

### Main targets

all: jdk
	@$(if $(JAVAC_SERVERS),rm -rf $(JAVAC_SERVERS)/*.port)
	@$(call AtRootMakeEnd)

langtools: start-make langtools-only
langtools-only:
	@$(call MakeStart,langtools,all)
	@($(CD) $(LANGTOOLS_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) -j$(JOBS) $(MAKE_ARGS))
	@$(call MakeFinish,langtools,all)

corba: langtools corba-only
corba-only:
	@$(call MakeStart,corba,all)
	@($(CD) $(CORBA_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) -j$(JOBS) $(MAKE_ARGS))
	@$(call MakeFinish,corba,all)

jaxp: langtools jaxp-only
jaxp-only:
	@$(call MakeStart,jaxp,all)
	@($(CD) $(JAXP_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) -j$(JOBS) $(MAKE_ARGS))
	@$(call MakeFinish,jaxp,all)

jaxws: langtools jaxp jaxws-only
jaxws-only:
	@$(call MakeStart,jaxws,all)
	@($(CD) $(JAXWS_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) -j$(JOBS) $(MAKE_ARGS))
	@$(call MakeFinish,jaxws,all)

hotspot: langtools hotspot-only
hotspot-only:
	@$(call MakeStart,hotspot,all)
	@($(CD) $(HOTSPOT_TOPDIR)/make && $(BUILD_LOG_WRAPPER) $(MAKE) -j1 $(HOTSPOT_MAKE_ARGS) $(MAKE_ARGS))
	@$(call MakeFinish,hotspot,all)

jdk: langtools corba jaxp jaxws hotspot jdk-only
jdk-only:
	@$(call MakeStart,jdk,all)
	@($(CD) $(JDK_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) -j$(JOBS) $(MAKE_ARGS))
	@$(call MakeFinish,jdk,all)

images: source-tips start-make jdk langtools corba jaxp jaxws hotspot images-only
images-only:
	@$(call MakeStart,jdk-images,$@)
	@($(CD) $(JDK_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) -j$(JOBS) $(JDK_MAKE_ARGS) $(MAKE_ARGS) images)
	@$(call MakeFinish,jdk-images,$@)
	@$(if $(JAVAC_SERVERS),rm -rf $(JAVAC_SERVERS)/*.port)
	@$(call AtRootMakeEnd)

install: source-tips start-make jdk langtools corba jaxp jaxws hotspot install-only
install-only:
	@$(call MakeStart,jdk-images,$@)
	@($(CD) $(JDK_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) -j$(JOBS) $(JDK_MAKE_ARGS) $(MAKE_ARGS) install)
	@$(call MakeFinish,jdk-images,$@)
	@$(if $(JAVAC_SERVERS),rm -rf $(JAVAC_SERVERS)/*.port)
	@$(call AtRootMakeEnd)

start-make:
	@$(call AtRootMakeStart)

.PHONY: jdk hotspot jaxws jaxp corba langtools install images start-make

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
clean:
	@(cd $(OUTPUT_ROOT) && $(RM) -r `$(LS) $(OUTPUT_ROOT) | grep -v spec.gmk | grep -v spec.sh | grep -v Makefile | grep -v config.status | grep -v config.log | grep -v config.h | grep -v configure-arguments | grep -v "localdevenv.*" | grep -v uncygdrive.exe`)
	@$(ECHO) Cleaned everything except the build configuration.
.PHONY: clean

# Remove everything, you have to rerun configure.
dist-clean:
	@$(RM) -r $(OUTPUT_ROOT)
	@$(ECHO) Cleaned everything, you will have to re-run configure.
.PHONY: dist-clean

clean-jdk:
	@(cd $(OUTPUT_ROOT) && $(RM) -r `$(LS) $(OUTPUT_ROOT) | grep -v spec.gmk | grep -v spec.sh | grep -v Makefile | grep -v config.status | grep -v config.log |  grep -v config.h | grep -v configure-arguments | \
                               grep -v langtools | grep -v corba | grep -v jaxp | grep -v jaxws | grep -v hotspot`)
	@$(ECHO) "Cleaned jdk build artifacts (but not langtools,corba,jaxp,jaxws,hotspot nor the build configuration)"
.PHONY: clean

endif

# Here are "global" targets, i.e. targets that can be executed without specifying a single configuration.
# If you addd more global targets, please update the fatal-error macro.

help:
	$(info )
	$(info OpenJDK Makefile help)
	$(info =====================)
	$(info )
	$(info Common make targets)
	$(info .  make [all]            # Compile all code but do not create images)
	$(info .  make images           # Create complete j2sdk and j2re images)
	$(info .  make install          # Install the generated images locally)
	$(info .  make clean            # Remove all files generated by make, but not those generated by configure)
	$(info .  make dist-clean       # Remove all files generated by both make and configure)
	$(info .  make help             # Give some help on using make)
	$(info .  make test             # Run tests, default is all tests (see TEST below))
	$(info )
	$(info Useful make variables)
	$(info .  make CONF=            # Build all configurations (note, assignment is empty))
	$(info .  make CONF=<substring> # Build the configuration(s) with a name matching the given substring)
	$(info )
	$(info .  make LOG=<loglevel>   # Change loglevel from warn (default) to the given loglevel)
	$(info .                        # Available loglevels are: warn, info, debug and trace)
	$(info .                        # To see executed command lines, use LOG=info)
	$(info )
	$(info .  make test TEST=<test> # Only run the given test or tests, e.g.)
	$(info .                        # make test TEST="jdk_lang jdk_net")
	$(info )
.PHONY: help
FRC: # Force target
