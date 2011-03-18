#
# Copyright (c) 2006, 2010, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for the scriptpad sample code
#

BUILDDIR = ../../..

PRODUCT = java

include $(BUILDDIR)/common/Defs.gmk

SAMPLE_SRC_DIR = $(SHARE_SRC)/sample/scripting/scriptpad
SAMPLE_DST_DIR = $(SAMPLEDIR)/scripting/scriptpad

SAMPLE_FILES =									\
	$(SAMPLE_DST_DIR)/build.xml						\
	$(SAMPLE_DST_DIR)/build.properties					\
	$(SAMPLE_DST_DIR)/README.txt             				\
	$(SAMPLE_DST_DIR)/nbproject/file-targets.xml				\
	$(SAMPLE_DST_DIR)/nbproject/jdk.xml					\
	$(SAMPLE_DST_DIR)/nbproject/netbeans-targets.xml			\
	$(SAMPLE_DST_DIR)/nbproject/project.xml					\
	$(SAMPLE_DST_DIR)/src/com/sun/sample/scriptpad/Main.java		\
	$(SAMPLE_DST_DIR)/src/META-INF/manifest.mf				\
	$(SAMPLE_DST_DIR)/src/resources/conc.js					\
	$(SAMPLE_DST_DIR)/src/resources/gui.js					\
	$(SAMPLE_DST_DIR)/src/resources/Main.js					\
	$(SAMPLE_DST_DIR)/src/resources/mm.js					\
	$(SAMPLE_DST_DIR)/src/resources/scriptpad.js				\
	$(SAMPLE_DST_DIR)/src/scripts/browse.js					\
	$(SAMPLE_DST_DIR)/src/scripts/insertfile.js				\
	$(SAMPLE_DST_DIR)/src/scripts/linewrap.js				\
	$(SAMPLE_DST_DIR)/src/scripts/mail.js					\
	$(SAMPLE_DST_DIR)/src/scripts/memmonitor.js				\
	$(SAMPLE_DST_DIR)/src/scripts/memory.bat				\
	$(SAMPLE_DST_DIR)/src/scripts/memory.sh					\
	$(SAMPLE_DST_DIR)/src/scripts/memory.js					\
	$(SAMPLE_DST_DIR)/src/scripts/README.txt				\
	$(SAMPLE_DST_DIR)/src/scripts/textcolor.js


all build: $(SAMPLE_FILES)

$(SAMPLE_DST_DIR)/%: $(SAMPLE_SRC_DIR)/%
	$(install-file)

clean clobber:
	$(RM) -r $(SAMPLE_DST_DIR)

.PHONY: all build clean clobber
