#
# Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
default:

# Inclusion of this pseudo-target will cause make to execute this file
# serially, regardless of -j. Recursively called makefiles will not be
# affected, however. This is required for correct dependency management.
.NOTPARALLEL:

# The shell code below will be executed on /usr/ccs/bin/make on Solaris, but not in GNU make.
# /usr/ccs/bin/make lacks basically every other flow control mechanism.
TEST_FOR_NON_GNUMAKE:sh=echo You are not using GNU make/gmake, this is a requirement. Check your path. 1>&2 && exit 1

# Assume we have GNU make, but check version.
ifeq ($(strip $(foreach v, 3.81% 3.82% 4.%, $(filter $v, $(MAKE_VERSION)))), )
  $(error This version of GNU Make is too low ($(MAKE_VERSION)). Check your path, or upgrade to 3.81 or newer.)
endif

# Locate this Makefile
ifeq ($(filter /%,$(lastword $(MAKEFILE_LIST))),)
  makefile_path:=$(CURDIR)/$(lastword $(MAKEFILE_LIST))
else
  makefile_path:=$(lastword $(MAKEFILE_LIST))
endif
root_dir:=$(dir $(makefile_path))

# ... and then we can include our helper functions
include $(root_dir)/make/MakeHelpers.gmk

$(eval $(call ParseLogLevel))
$(eval $(call ParseConfAndSpec))

# Now determine if we have zero, one or several configurations to build.
ifeq ($(SPEC),)
  # Since we got past ParseConfAndSpec, we must be building a global target. Do nothing.
else
  # In Cygwin, the MAKE variable gets messed up if the make executable is called with
  # a Windows mixed path (c:/cygwin/bin/make.exe). If that's the case, fix it by removing
  # the prepended root_dir.
  ifneq ($(findstring :, $(MAKE)), )
    MAKE := $(patsubst $(root_dir)%, %, $(MAKE))
  endif

  # We are potentially building multiple configurations.
  # First, find out the valid targets
  # Run the makefile with an arbitrary SPEC using -p -q (quiet dry-run and dump rules) to find
  # available PHONY targets. Use this list as valid targets to pass on to the repeated calls.
  all_phony_targets := $(sort $(filter-out $(global_targets), $(strip $(shell \
      cd $(root_dir)/make && $(MAKE) -f Main.gmk -p -q FRC SPEC=$(firstword $(SPEC)) \
      -I $(root_dir)/make/common | grep "^.PHONY:" | head -n 1 | cut -d " " -f 2-))))

  # Loop through the configurations and call the main-wrapper for each one. The wrapper
  # target will execute with a single configuration loaded.
  $(all_phony_targets):
	@$(if $(TARGET_RUN),,\
          $(foreach spec,$(SPEC),\
            (cd $(root_dir) && $(MAKE) SPEC=$(spec) MAIN_TARGETS="$(call GetRealTarget)" \
	    $(VERBOSE) VERBOSE=$(VERBOSE) LOG_LEVEL=$(LOG_LEVEL) main-wrapper) &&) true)
	@echo > /dev/null
	$(eval TARGET_RUN=true)

  .PHONY: $(all_phony_targets)

  ifneq ($(MAIN_TARGETS), )
    # The wrapper target was called so we now have a single configuration. Load the spec file
    # and call the real Main.gmk.
    include $(SPEC)

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
      $(shell $(RM) $(OUTPUT_ROOT)/build-trace-time.log 2> /dev/null)
    endif
    # Remove any javac server logs and port files. This
    # prevents a new make run to reuse the previous servers.
    ifneq (,$(SJAVAC_SERVER_DIR))
      $(shell $(MKDIR) -p $(SJAVAC_SERVER_DIR) && $(RM) -rf $(SJAVAC_SERVER_DIR)/*)
    endif

    # Split out the targets requiring sequential execution. Run these targets separately
    # from the rest so that the rest may still enjoy full parallel execution.
    SEQUENTIAL_TARGETS := $(filter dist-clean clean% reconfigure, $(MAIN_TARGETS))
    PARALLEL_TARGETS := $(filter-out $(SEQUENTIAL_TARGETS), $(MAIN_TARGETS))

    main-wrapper:
        ifneq ($(SEQUENTIAL_TARGETS), )
	  (cd $(SRC_ROOT)/make && $(MAKE) -f Main.gmk SPEC=$(SPEC) -j 1 \
	      $(VERBOSE) VERBOSE=$(VERBOSE) LOG_LEVEL=$(LOG_LEVEL) $(SEQUENTIAL_TARGETS))
        endif
        ifneq ($(PARALLEL_TARGETS), )
	  @$(call AtMakeStart)
	  (cd $(SRC_ROOT)/make && $(BUILD_LOG_WRAPPER) $(MAKE) -f Main.gmk SPEC=$(SPEC) -j $(JOBS) \
	      $(VERBOSE) VERBOSE=$(VERBOSE) LOG_LEVEL=$(LOG_LEVEL) $(PARALLEL_TARGETS) \
	      $(if $(filter true, $(OUTPUT_SYNC_SUPPORTED)), -O$(OUTPUT_SYNC)))
	  @$(call AtMakeEnd)
        endif

     .PHONY: main-wrapper

   endif
endif

# Here are "global" targets, i.e. targets that can be executed without specifying a single configuration.
# If you add more global targets, please update the variable global_targets in MakeHelpers.

help:
	$(info )
	$(info OpenJDK Makefile help)
	$(info =====================)
	$(info )
	$(info Common make targets)
	$(info .  make [default]         # Compile all modules in langtools, hotspot, jaxp, jaxws,)
	$(info .                         # corba and jdk and create a runnable "exploded" image)
	$(info .  make all               # Compile everything, all repos, docs and images)
	$(info .  make images            # Create complete j2sdk and j2re images)
	$(info .  make docs              # Create all docs)
	$(info .  make docs-javadoc      # Create just javadocs, depends on less than full docs)
	$(info .  make profiles          # Create complete j2re compact profile images)
	$(info .  make bootcycle-images  # Build images twice, second time with newly built JDK)
	$(info .  make install           # Install the generated images locally)
	$(info .  make reconfigure       # Rerun configure with the same arguments as last time)
	$(info .  make clean             # Remove all files generated by make, but not those)
	$(info .                         # generated by configure)
	$(info .  make dist-clean        # Remove all files, including configuration)
	$(info .  make help              # Give some help on using make)
	$(info .  make test              # Run tests, default is all tests (see TEST below))
	$(info )
	$(info Targets for specific modules)
	$(info .  make <module>          # Build <module> and everything it depends on. )
	$(info .  make <module>-only     # Build <module> only, without dependencies. This)
	$(info .                         # is faster but can result in incorrect build results!)
	$(info .  make <module>-java     # Compile java classes for <module> and everything it)
	$(info .                         # depends on)
	$(info .  make <module>-libs     # Build native libraries for <module> and everything it)
	$(info .                         # depends on)
	$(info .  make <module>-launchers# Build native executables for <module> and everything it)
	$(info .                         # depends on)
	$(info .  make <module>-gensrc   # Execute the gensrc step for <module> and everything it)
	$(info .                         # depends on)
	$(info )
	$(info Useful make variables)
	$(info .  make CONF=             # Build all configurations (note, assignment is empty))
	$(info .  make CONF=<substring>  # Build the configuration(s) with a name matching)
	$(info .                         # <substring>)
	$(info )
	$(info .  make LOG=<loglevel>    # Change the log level from warn to <loglevel>)
	$(info .                         # Available log levels are:)
	$(info .                         # 'warn' (default), 'info', 'debug' and 'trace')
	$(info .                         # To see executed command lines, use LOG=debug)
	$(info )
	$(info .  make JOBS=<n>          # Run <n> parallel make jobs)
	$(info .                         # Note that -jN does not work as expected!)
	$(info )
	$(info .  make test TEST=<test>  # Only run the given test or tests, e.g.)
	$(info .                         # make test TEST="jdk_lang jdk_net")
	$(info )

.PHONY: help
