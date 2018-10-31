#
# Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
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
# Makefile to run tests from multiple sibling directories
#

$(info WARNING: This way of running tests ("cd test && make") is deprecated)
$(info Please use "make test TEST=..." instead. See doc/testing.md for details)

# Macro to run a test target in a subdir
define SUBDIR_TEST # subdirectory target
if [ -d $1 ] ; then \
  if [ -r $1/Makefile ] ; then \
    $(MAKE) --no-print-directory -k -C $1 $2 ; \
    echo 'WARNING: This way of running tests ("cd test && make") is deprecated' ; \
    echo 'Please use "make test TEST=..." instead. See doc/testing.md for details' ; \
  else \
    echo "ERROR: File does not exist: $1/Makefile"; \
    exit 1; \
  fi; \
else \
  echo "WARNING: No testing done, directory does not exist: $1"; \
fi
endef

# Default test target (core)
default: jdk_core langtools_jtreg jaxp_all

# All testing
all: jdk_all langtools_all jaxp_all

ifeq ($(TEST_JOBS), 0)
  JDK_TEST_JOBS=$(JOBS)
else
  JDK_TEST_JOBS=$(TEST_JOBS)
endif
# Test targets
langtools_% :
	@$(NO_STOPPING)$(call SUBDIR_TEST, langtools, \
	    $(if $(JDK_TEST_JOBS), CONCURRENCY=$(JDK_TEST_JOBS)) \
	    JT_JAVA=$(PRODUCT_HOME) JTREG_HOME=$(JT_HOME) \
	    TEST="$(subst langtools_,,$@)" $(subst langtools_,,$@))

jdk_% core_%s svc_%:
	@$(NO_STOPPING)$(call SUBDIR_TEST, jdk, \
	    $(if $(JDK_TEST_JOBS), CONCURRENCY=$(JDK_TEST_JOBS)) TEST="$@" $@)

jaxp_%:
	@$(NO_STOPPING)$(call SUBDIR_TEST, jaxp, \
	    $(if $(JDK_TEST_JOBS), CONCURRENCY=$(JDK_TEST_JOBS)) TEST="$@" $@)

nashorn_%:
	@$(NO_STOPPING)$(call SUBDIR_TEST, nashorn, \
	    $(if $(JDK_TEST_JOBS), CONCURRENCY=$(JDK_TEST_JOBS)) TEST="$@" $@)

SUB_MAKE_ARGS :=
ifneq ($(TEST_JOBS), 0)
  ifneq ($(TEST_JOBS), )
    SUB_MAKE_ARGS += CONCURRENCY=$(TEST_JOBS)
  endif
endif
hotspot_%:
	@$(NO_STOPPING)$(call SUBDIR_TEST, hotspot/jtreg, $(SUB_MAKE_ARGS) TEST="$@" $@)

#
# jtreg_tests
#
# Invocation:
#
# make jtreg_tests TESTDIRS=<test-dirs> TEST_SELECTION=<path to test or jtreg group> TEST_OUTPUT_DIR=<path>
#
# where <test-dirs> is something like '../<component>/test/runtime',
# <component> in turn being one of the top level directories (for
# example 'hotspot').
#
# The below will strip the path prefix and delegate to the
# corresponding ../<component>/test/Makefile.

ifneq ($(TESTDIRS),)
  # Extract the component from ../test/<component>/...
  TESTDIRS_NORM := $(patsubst test/%, %, $(patsubst ../%, %, $(TESTDIRS)))
  COMPONENT := $(word 1,$(subst /, ,$(TESTDIRS_NORM)))

  # Strip off the ../<component>/test prefix and pass the rest as TESTDIRS
  # to the delegate Makefile
  # The hotspot tests are in a subdir "java". Accept paths both including
  # and excluding this extra subdir
  TESTDIRS_TESTS := $(patsubst $(COMPONENT)/%,%,$(patsubst hotspot/jtreg/%,%, \
      $(patsubst ../%, %, $(TESTDIRS_NORM))))
endif

jtreg_tests:
	$(MAKE) --no-print-directory TESTDIRS=$(TESTDIRS_TESTS) \
	    $(COMPONENT)_jtreg_tests

################################################################

# Phony targets (e.g. these are not filenames)
.PHONY: all clean

################################################################
