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

# Find out which variables were passed explicitely on the make command line. These
# will be passed on to sub-makes, overriding spec.gmk settings.
MAKE_ARGS=$(foreach var,$(subst =command,,$(filter %=command,$(foreach var,$(.VARIABLES),$(var)=$(firstword $(origin $(var)))))),$(var)=$($(var)))

define fatal-error
    # If the user specificed a "global" target (e.g. 'help'), do not exit but continue running
    $$(if $$(findstring help,$$(MAKECMDGOALS)),,$$(error Cannot continue))
endef

ifeq ($(origin VERBOSE),undefined)
    # Setup logging according to LOG (but only if VERBOSE is not given)
    ifeq ($(LOG),)
        # Set LOG to "warn" as default if not set (and no VERBOSE given)
        LOG=warn
    endif
    ifeq ($(LOG),warn)
        VERBOSE=-s
    else ifeq ($(LOG),info)
        VERBOSE=
    else ifeq ($(LOG),debug)
        VERBOSE=
    else ifeq ($(LOG),trace)
        VERBOSE=-d -p
    else
        $(info Error: LOG must be one of: warn, info, debug or trace.)
        $(eval $(call fatal-error))
    endif
else
    ifneq ($(LOG),)
        # We have both a VERBOSE and a LOG argument. This is OK only if this is a repeated call by ourselves,
        # but complain if this is the top-level make call.
        ifeq ($(MAKELEVEL),0)
            $(info Cannot use LOG=$(LOG) and VERBOSE=$(VERBOSE) at the same time. Choose one.)
            $(eval $(call fatal-error))
        endif
    endif
endif

# TODO: Fix duplication in MakeBase.gmk
define SetupLogging
    ifneq ($(findstring $(LOG),debug trace),)
        # Shell redefinition trick inspired by http://www.cmcrossroads.com/ask-mr-make/6535-tracing-rule-execution-in-gnu-make
        OLD_SHELL:=$$(SHELL)
        SHELL = $$(warning Building $$@$$(if $$<, (from $$<))$(if $$?, ($$? newer)))$$(OLD_SHELL) -x
    endif
endef

$(eval $(call SetupLogging))

# Find all environment or command line variables that begin with ALT.
list_alt_overrides_with_origins = $(filter ALT_%=environment ALT_%=command,$(foreach var,$(.VARIABLES),$(var)=$(firstword $(origin $(var)))))
list_alt_overrides=$(subst =command,,$(subst =environment,,$(list_alt_overrides_with_origins)))

ifeq ($(filter /%,$(lastword $(MAKEFILE_LIST))),)
    makefile_path=$(CURDIR)/$(lastword $(MAKEFILE_LIST))
else
    makefile_path=$(lastword $(MAKEFILE_LIST))
endif
root_dir=$(patsubst %/common/makefiles/Makefile,%,$(makefile_path))
output_dir=$(root_dir)/build

ifneq ($(origin SPEC),undefined)
    # We have been given a SPEC, check that it works out properly
    ifeq ($(wildcard $(SPEC)),)
        $(info Cannot locate spec.gmk, given by SPEC=$(SPEC))
        $(eval $(call fatal-error))
    endif
    ifneq ($(origin CONF),undefined)
        # We also have a CONF argument. This is OK only if this is a repeated call by ourselves,
        # but complain if this is the top-level make call.
        ifeq ($(MAKELEVEL),0)
            $(info Cannot use CONF=$(CONF) and SPEC=$(SPEC) at the same time. Choose one.)
            $(eval $(call fatal-error))
        endif
    endif
    # ... OK, we're satisfied, we'll use this SPEC later on
else
    # Find all spec.gmk files in the build output directory
    all_spec_files=$(wildcard $(output_dir)/*/spec.gmk)
    ifeq ($(all_spec_files),)
        $(info No configurations found for $(root_dir)! Please run configure to create a configuration.)
        $(eval $(call fatal-error))
    endif
    # Extract the configuration names from the path
    all_confs=$(patsubst %/spec.gmk,%,$(patsubst $(output_dir)/%,%,$(all_spec_files)))

    ifneq ($(origin CONF),undefined)
        # User have given a CONF= argument.
        ifeq ($(CONF),)
            # If given CONF=, match all configurations
            matching_confs=$(strip $(all_confs))
        else
            # Otherwise select those that contain the given CONF string
            matching_confs=$(strip $(foreach var,$(all_confs),$(if $(findstring $(CONF),$(var)),$(var))))
        endif
        ifeq ($(matching_confs),)
            $(info No configurations found matching CONF=$(CONF))
            $(info Available configurations:)
            $(foreach var,$(all_confs),$(info * $(var)))
            $(eval $(call fatal-error))
        else
            ifeq ($(words $(matching_confs)),1)
                $(info Building '$(matching_confs)' (matching CONF=$(CONF)))
            else
                $(info Building the following configurations (matching CONF=$(CONF)):)
                $(foreach var,$(matching_confs),$(info * $(var)))
            endif
        endif

        # Create a SPEC definition. This will contain the path to one or more spec.gmk files.
        SPEC=$(addsuffix /spec.gmk,$(addprefix $(output_dir)/,$(matching_confs)))
    else
        # No CONF or SPEC given, check the available configurations
        ifneq ($(words $(all_spec_files)),1)
            $(info No CONF or SPEC given, but more than one spec.gmk found in $(output_dir).)
            $(info Available configurations:)
            $(foreach var,$(all_confs),$(info * $(var)))
            $(info Please retry building with CONF=<config> or SPEC=<specfile>)
            $(eval $(call fatal-error))
        endif

        # We found exactly one configuration, use it
        SPEC=$(strip $(all_spec_files))
    endif
endif

ifneq ($(words $(SPEC)),1)
# We have multiple configurations to build, call make repeatedly
all jdk hotspot jaxws jaxp corba langtools install images packages clean dist-clean:
	@$(foreach spec,$(SPEC),($(MAKE) -f $(makefile_path) SPEC=$(spec) $(VERBOSE) VERBOSE=$(VERBOSE) $@ $(MAKE_ARGS)) &&) true

.PHONY: all jdk hotspot jaxws jaxp corba langtools install images packages clean dist-clean

else
# This is the main part of the Makefile, for the normal case with SPEC specifying a single existing spec.gmk file.

# Now load the spec
-include $(SPEC)

# Load the vital tools for all the makefiles. 
-include $(SRC_ROOT)/common/makefiles/MakeBase.gmk

# Remove any build.log from a previous run
ifneq (,$(BUILD_LOG))
    $(shell $(RM) $(BUILD_LOG))
endif

# Remove any javac server logs and port files. This
# prevents a new make run to reuse the previous servers.
ifneq (,$(JAVAC_SERVERS))
    $(shell mkdir -p $(JAVAC_SERVERS) && rm -rf $(JAVAC_SERVERS)/*)
endif
# Reset the build timers.
$(eval $(call ResetTimers))
# Clean out any notifications from the previous build.
$(shell find $(OUTPUT_ROOT) -name "_the.*.notify" $(FIND_DELETE))

# If make was called explicitely with -j, don't add a -j ourself to sub-makes, since
# this will be inherited automatically by make. Otherwise use our default for sub-makes.
# The -j in MAKEFLAGS is only visible when executing a recipe, hence this macro.
define GetMakeJobFlag
    $(if $(findstring -j,$(MAKEFLAGS)),,-j$(NUM_CORES))
endef

define CheckEnvironment
    $(if $(list_alt_overrides),
        @$(PRINTF) "\nWARNING: You have the following ALT_ variables set:\n"
    @$(PRINTF) "$(foreach var,$(list_alt_overrides),$(var)=$$$(var))\n"
    @$(PRINTF) "ALT_ variables are deprecated and will be ignored. Please clean your environment.\n"
    )
endef

define PrintStartMessage
    $(if $(VERBOSE),,@$(ECHO) Running make as $(MAKE) $(MFLAGS) $(MAKE_ARGS))
    $(call CheckEnvironment)
    @$(ECHO) "Building OpenJDK for target $(if $(MAKECMDGOALS),'$(MAKECMDGOALS)','all') in configuration '$(CONF_NAME)'"
endef

define PrintEndMessage
    @$(ECHO) "Finished building OpenJDK for target '$@'"
    $(call CheckEnvironment)
endef

all: jdk
	@$(if $(JAVAC_SERVERS),rm -rf $(JAVAC_SERVERS)/*.port)
	@$(call AtRootMakeEnd)

langtools: start-make
	@$(call MakeStart,langtools,all)
	@($(CD) $(LANGTOOLS_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) $(call GetMakeJobFlag) $(LANGTOOLS_MAKE_ARGS) $(MAKE_ARGS))
	@$(call MakeFinish,langtools,all)

corba: langtools
	@$(call MakeStart,corba,all)
	@($(CD) $(CORBA_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) $(call GetMakeJobFlag) $(CORBA_MAKE_ARGS) $(MAKE_ARGS))
	@$(call MakeFinish,corba,all)

jaxp: langtools
	@$(call MakeStart,jaxp,all)
	@($(CD) $(JAXP_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) $(call GetMakeJobFlag) $(CORBA_MAKE_ARGS) $(MAKE_ARGS))
	@$(call MakeFinish,jaxp,all)

jaxws: langtools jaxp
	@$(call MakeStart,jaxws,all)
	@($(CD) $(JAXWS_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) $(call GetMakeJobFlag) $(CORBA_MAKE_ARGS) $(MAKE_ARGS))
	@$(call MakeFinish,jaxws,all)

hotspot: langtools
	@$(call MakeStart,hotspot,all)
	@($(CD) $(HOTSPOT_TOPDIR)/make && $(BUILD_LOG_WRAPPER) $(MAKE) -j1 $(HOTSPOT_MAKE_ARGS) $(MAKE_ARGS))
	@$(call MakeFinish,hotspot,all)

jdk: langtools corba jaxp jaxws hotspot
	@$(call MakeStart,jdk,all)
	@($(CD) $(JDK_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) $(call GetMakeJobFlag) $(JDK_MAKE_ARGS) $(MAKE_ARGS))
	@$(call MakeFinish,jdk,all)

images install packages: source-tips start-make jdk langtools corba jaxp jaxws hotspot
	@$(call MakeStart,jdk-images,$@)
	@($(CD) $(JDK_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) $(call GetMakeJobFlag) $(JDK_MAKE_ARGS) $(MAKE_ARGS) $@)
	@$(call MakeFinish,jdk-images,$@)
	@$(if $(JAVAC_SERVERS),rm -rf $(JAVAC_SERVERS)/*.port)
	@$(call AtRootMakeEnd)

old-images: source-tips start-make jdk langtools corba jaxp jaxws hotspot
	@$(call MakeStart,jdk-old-images,$@)
	@($(CD) $(JDK_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) $(call GetMakeJobFlag) $(JDK_MAKE_ARGS) $(MAKE_ARGS) $@)
	@$(call MakeFinish,old-jdk-images,$@)
	@$(if $(JAVAC_SERVERS),rm -rf $(JAVAC_SERVERS)/*.port)
	@$(call AtRootMakeEnd)

start-make:
	@$(call AtRootMakeStart)

.PHONY: jdk hotspot jaxws jaxp corba langtools install images packages start-make

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
	@(cd $(OUTPUT_ROOT) && $(RM) -r `$(LS) $(OUTPUT_ROOT) | grep -v spec.gmk | grep -v Makefile | grep -v config.status | grep -v config.log | grep -v config.h | grep -v configure-arguments | grep -v "localdevenv.*" | grep -v uncygdrive.exe`)
	@$(ECHO) Cleaned everything except the build configuration.
.PHONY: clean

# Remove everything, you have to rerun configure.
dist-clean:
	@$(RM) -r $(OUTPUT_ROOT)
	@$(ECHO) Cleaned everything, you will have to re-run configure.
.PHONY: dist-clean

clean-jdk:
	@(cd $(OUTPUT_ROOT) && $(RM) -r `$(LS) $(OUTPUT_ROOT) | grep -v spec.gmk | grep -v Makefile | grep -v config.status | grep -v config.log |  grep -v config.h | grep -v configure-arguments | \
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
