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

# Default test target (core)
default: jdk_core langtools_jtreg

# All testing
all: jdk_all langtools_all

# Test targets
langtools_% :
	@$(NO_STOPPING)$(call SUBDIR_TEST, $(LANGTOOLS_DIR), JT_JAVA=$(PRODUCT_HOME) JTREG_HOME=$(JT_HOME) TEST="$(subst langtools_,,$@)" $(subst langtools_,,$@))

jdk_% :
	@$(NO_STOPPING)$(call SUBDIR_TEST, $(JDK_DIR), TEST="$@" $@)

################################################################

# Phony targets (e.g. these are not filenames)
.PHONY: all clean

################################################################
