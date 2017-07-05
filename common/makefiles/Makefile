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

# Default to sane output from make.
# Override with empty string to get insane amount of output.
# Override with -d to get even more insane amount of debugging output.
# Override with "-d -p" to get it all.
VERBOSE=-s

# Find all environment or command line variables that begin with ALT.
list_alt_overrides_with_origins = $(filter ALT_%=environment ALT_%=command,$(foreach var,$(.VARIABLES),$(var)=$(firstword $(origin $(var)))))
list_alt_overrides=$(subst =command,,$(subst =environment,,$(list_alt_overrides_with_origins)))
ifneq ($(list_alt_overrides),)
    $(info You have set the following ALT_ variables:)
    $(foreach var,$(list_alt_overrides), $(info $(var)=$($(var))))
    $(error Using ALT_ variables is deprecated! Please clean your environment!)
endif

# The spec.gmk file contains the variables extracted by the configure script.
# It is usually set with SPEC=....spec.gmk on the make command line.
# However if you simply type make from the openjdk source root, it will go looking
# for a spec file, if only one is found, use it. If more than one is found,
# complain. If none is found, request the user to run configure!
SPEC ?= $(wildcard $(CURDIR)/../../build/*/spec.gmk)

ifeq ($(words $(SPEC)),0)
    $(error You must run configure!)
endif

ifneq ($(words $(SPEC)),1)
    ifeq ($(MAKECMDGOALS),all-conf)
        SPECS:=$(shell echo $(SPEC) | sed -e 's|$(CURDIR)/build/||g' -e 's|/spec.gmk|\\n|g' -e 's| ||g')
        allconf:
		@echo Building configurations:
		@printf "$(SPECS)"
		@$(foreach s,$(SPEC),($(MAKE) SPEC=$s $(VERBOSE) VERBOSE=$(VERBOSE) images) &&) true
		@echo Done building configurations:
		@printf "$(SPECS)"
        .PHONY: all-conf
    else
        $(error Since you have more than one output dir configured under build, \
            you have to either run make from the output dir of your choice \
            or specify run "make SPEC=build/.../spec.gmk" or run all the build configurations \
	    using "make all-conf")
    endif
else

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

all: jdk
	@$(call StopTimer)
	@$(if $(JAVAC_SERVERS),rm -rf $(JAVAC_SERVERS)/*.port)

langtools: start-timer
	@$(call MakeStart,langtools,all)
	@($(CD) $(LANGTOOLS_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) -j$(NUM_CORES) $(LANGTOOLS_MAKE_ARGS))
	@$(call MakeFinish,langtools,all)

corba: langtools
	@$(call MakeStart,corba,all)
	@($(CD) $(CORBA_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) -j$(NUM_CORES) $(CORBA_MAKE_ARGS))
	@$(call MakeFinish,corba,all)

jaxp: langtools
	@$(call MakeStart,jaxp,all)
	@($(CD) $(JAXP_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) -j$(NUM_CORES) $(CORBA_MAKE_ARGS))
	@$(call MakeFinish,jaxp,all)

jaxws: langtools jaxp
	@$(call MakeStart,jaxws,all)
	@($(CD) $(JAXWS_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) -j$(NUM_CORES) $(CORBA_MAKE_ARGS))
	@$(call MakeFinish,jaxws,all)

hotspot: langtools
	@$(call MakeStart,hotspot,all)
	@($(CD) $(HOTSPOT_TOPDIR)/make && $(BUILD_LOG_WRAPPER) $(MAKE) -j1 $(HOTSPOT_MAKE_ARGS))
	@$(call MakeFinish,hotspot,all)

jdk: langtools corba jaxp jaxws hotspot
	@$(call MakeStart,jdk,all)
	@($(CD) $(JDK_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) -j$(NUM_CORES) $(JDK_MAKE_ARGS))
	@$(call MakeFinish,jdk,all)

images install packages: start-timer jdk langtools corba jaxp jaxws hotspot
	@$(call MakeStart,jdk-images,$@)
	@($(CD) $(JDK_TOPDIR)/makefiles && $(BUILD_LOG_WRAPPER) $(MAKE) -j$(NUM_CORES) $(JDK_MAKE_ARGS) $@)
	@$(call MakeFinish,jdk-images,$@)
	@$(call StopTimer)
	@$(if $(JAVAC_SERVERS),rm -rf $(JAVAC_SERVERS)/*.port)

start-timer:
	@$(call StartTimer)

.PHONY: jdk hotspot jaxws jaxp corba langtools install images packages start-timer

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

help:
	$(info     )
	$(info Typical make commands:)
	$(info     make)
	$(info     make VERBOSE=        # print all commands)
	$(info     make VERBOSE="-d -p" # debug make as well)
	$(info     make all-conf        # build images for all configurations)
	$(info     make clean           # remove build artifacts)
	$(info     make dist-clean      # you have to rerun configure)
#	$(info     make test            # run tests)
	$(info     make images          # create the jdk and jre images)
	$(info     make install         # install the jdk image)
#	$(info     make modules         # EXPERIMENTAL: Migrate JDK into a modularized form!)
	$(info     make packages        # create zips and other packages)

#	$(info     make eclipse_workspace  # Create an Eclipse workspace)
#	$(info     make netbeans_workspace # Create a NetBeans workspace)
#	$(info     make vs_workspace       # Create a Visual Studio workspace)

.PHONY: help

endif
