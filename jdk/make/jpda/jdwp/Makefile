#
# Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building JDWP
#

LIBRARY_SUPPORTS_FULL_DEBUG_SYMBOLS=1

BUILDDIR = ../..
PACKAGE = com.sun.tools.jdwp
PRODUCT = jpda
include $(BUILDDIR)/common/Defs.gmk

#
# Where do generated Java source files go?
#
JAVA_SRC_DEST_DIR = $(GENSRCDIR)

#
# Where do generated C source files go?
# the jdwp build is what needs the generated C files
# so they are being placed there for automatic pickup by
# the value of GENNATIVESRCDIR when jdwp is being built.
#
C_SRC_DEST_DIR = $(GENNATIVESRCDIR)/jdwp

JDIPKGDIR = com/sun/tools/jdi
GEN_JDWP_JAVA_DIR = $(JAVA_SRC_DEST_DIR)/$(JDIPKGDIR)
GEN_JDWP_JAVA = $(GEN_JDWP_JAVA_DIR)/JDWP.java
GEN_JDWP_C = $(C_SRC_DEST_DIR)/JDWPCommands.h

JDWP_SPEC       = $(BUILDDIR)/jpda/jdwp/jdwp.spec
JDWPGEN_JARFILE = $(BUILDTOOLJARDIR)/jdwpgen.jar

build: $(GEN_JDWP_JAVA) $(GEN_JDWP_C)

$(GEN_JDWP_JAVA) $(GEN_JDWP_C): $(JDWP_SPEC) $(JDWPGEN_JARFILE)
	$(MKDIR) -p $(GEN_JDWP_JAVA_DIR)
	$(MKDIR) -p $(C_SRC_DEST_DIR)
	$(BOOT_JAVA_CMD) -jar $(JDWPGEN_JARFILE) $(JDWP_SPEC) \
		-jdi $(GEN_JDWP_JAVA) \
		-include $(GEN_JDWP_C)
	@$(java-vm-cleanup)

#
# Extra clean.
#
clean clobber::
	$(RM) -r $(C_SRC_DEST_DIR)
	$(RM) -r $(JAVA_SRC_DEST_DIR)/$(JDIPKGDIR)

#
# Rules
#
include $(BUILDDIR)/common/Rules.gmk

