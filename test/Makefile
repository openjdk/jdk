#
# Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
    echo "$(MAKE) -C $1/test $2" ; \
    $(MAKE) -C $1/test $2 ; \
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
JDK_TEST_LIST = \
	jdk_awt \
	jdk_beans jdk_beans1 jdk_beans2 jdk_beans3  \
	jdk_io  \
	jdk_lang  \
	jdk_management jdk_management1 jdk_management2  \
	jdk_math  \
	jdk_misc  \
	jdk_net  \
	jdk_nio jdk_nio1 jdk_nio2 jdk_nio3  \
	jdk_rmi \
	jdk_security jdk_security1 jdk_security2 jdk_security3  \
	jdk_swing \
	jdk_text  \
	jdk_tools jdk_tools1 jdk_tools2  \
	jdk_util

# Default test target (everything)
all: $(JDK_TEST_LIST) $(LANGTOOLS_TEST_LIST)

# Test targets
$(LANGTOOLS_TEST_LIST):
	@$(call SUBDIR_TEST, $(LANGTOOLS_DIR), $(subst langtools_,,$@))
$(JDK_TEST_LIST):
	@$(call SUBDIR_TEST, $(JDK_DIR), $@)

clean:

################################################################

# Phony targets (e.g. these are not filenames)
.PHONY: all clean $(JDK_TEST_LIST) $(LANGTOOLS_TEST_LIST)

################################################################

