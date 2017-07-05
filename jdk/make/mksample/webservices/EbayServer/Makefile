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
# Makefile for the EbayServer sample code
#

BUILDDIR = ../../..

MODULE  = samples
PRODUCT = java

include $(BUILDDIR)/common/Defs.gmk

SAMPLE_SRC_DIR = $(CLOSED_SHARE_SRC)/sample/webservices/EbayServer
SAMPLE_DST_DIR = $(SAMPLEDIR)/webservices/EbayServer

SAMPLE_FILES =							\
	$(SAMPLE_DST_DIR)/src/ebay/server/Main.java	\
	$(SAMPLE_DST_DIR)/src/ebay/server/Ebay.java	\
	$(SAMPLE_DST_DIR)/src/ebay/server/ImageProvider.java	\
	$(SAMPLE_DST_DIR)/build.xml \
	$(SAMPLE_DST_DIR)/build.properties \
	$(SAMPLE_DST_DIR)/eBaySvc.wsdl \
	$(SAMPLE_DST_DIR)/manifest.mf \
	$(SAMPLE_DST_DIR)/index.html \
	$(SAMPLE_DST_DIR)/nbproject/project.xml \
	$(SAMPLE_DST_DIR)/nbproject/jdk.xml \
	$(SAMPLE_DST_DIR)/nbproject/netbeans-targets.xml \
	$(SAMPLE_DST_DIR)/nbproject/file-targets.xml \
	$(SAMPLE_DST_DIR)/images/8d_2.jpg \
	$(SAMPLE_DST_DIR)/images/sun_blade_1000_h400px.jpg \
	$(SAMPLE_DST_DIR)/images/Sun_Fire_E20K_Server.jpg \

all build: $(SAMPLE_FILES)

$(SAMPLE_DST_DIR)/%: $(SAMPLE_SRC_DIR)/%
	$(install-file)

clean clobber:
	$(RM) -r $(SAMPLE_DST_DIR)

.PHONY: all build clean clobber
