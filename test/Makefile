#
# Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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

# Root of the forest that was built
TOPDIR=..

# This makefile depends on the availability of sibling directories.
LANGTOOLS_DIR=$(TOPDIR)/langtools
JDK_DIR=$(TOPDIR)/jdk

# Macro to run a test target in a subdir
define SUBDIR_TEST # subdirectory target
if [ -d $1 ] ; then \
  if [ -r $1/test/Makefile ] ; then \
    echo "$(MAKE) -k -C $1/test $2" ; \
    $(MAKE) -k -C $1/test $2 ; \
  else \
    echo "ERROR: File does not exist: $1/test/Makefile"; \
    exit 1; \
  fi; \
else \
  echo "WARNING: No testing done, directory does not exist: $1"; \
fi
endef

# Test target list for langtools repository
LANGTOOLS_TEST_LIST = langtools_jtreg

# Test target list for jdk repository
JDK_ALL_TEST_LIST = \
	jdk_beans1 \
	jdk_io  \
	jdk_lang  \
	jdk_math  \
	jdk_other  \
	jdk_net  \
	jdk_nio \
	jdk_security1 \
	jdk_text  \
	jdk_util  \
	jdk_time \
	jdk_awt \
	jdk_beans2 jdk_beans3  \
	jdk_management \
	jdk_jmx \
	jdk_security2 jdk_security3  \
	jdk_rmi \
	jdk_sound \
	jdk_swing \
	jdk_tools \
	jdk_jdi \
	jdk_jfr

# Theses are meta test targets in jdk
JDK_META_TEST_LIST = jdk_all jdk_default jdk_core

# These are the current jck test targets in the jdk repository
JDK_JCK7_LIST = jck7devtools jck7compiler jck7runtime

# Default test target (core)
default: jdk_core $(LANGTOOLS_TEST_LIST)

# All testing
all: $(JDK_ALL_TEST_LIST) $(LANGTOOLS_TEST_LIST)

# Test targets
$(LANGTOOLS_TEST_LIST):
	@$(NO_STOPPING)$(call SUBDIR_TEST, $(LANGTOOLS_DIR), $(subst langtools_,,$@))

$(JDK_ALL_TEST_LIST) $(JDK_META_TEST_LIST) $(JDK_JCK7_LIST):
	@$(NO_STOPPING)$(call SUBDIR_TEST, $(JDK_DIR), $@)

clean:

################################################################

# Phony targets (e.g. these are not filenames)
.PHONY: all clean \
        $(JDK_ALL_TEST_LIST) $(JDK_META_TEST_LIST) $(JDK_JCK7_LIST) \
        $(LANGTOOLS_TEST_LIST)

################################################################

