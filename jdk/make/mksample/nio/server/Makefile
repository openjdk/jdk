#
# Copyright (c) 2004, 2006, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for the nioserver sample code
#

BUILDDIR = ../../..

MODULE  = samples
PRODUCT = java

include $(BUILDDIR)/common/Defs.gmk

SAMPLE_SRC_DIR = $(SHARE_SRC)/sample/nio/server
SAMPLE_DST_DIR = $(SAMPLEDIR)/nio/server

SAMPLE_FILES =							\
	$(SAMPLE_DST_DIR)/AcceptHandler.java			\
	$(SAMPLE_DST_DIR)/Acceptor.java				\
	$(SAMPLE_DST_DIR)/B1.java				\
	$(SAMPLE_DST_DIR)/BN.java				\
	$(SAMPLE_DST_DIR)/BP.java				\
	$(SAMPLE_DST_DIR)/ChannelIO.java			\
	$(SAMPLE_DST_DIR)/ChannelIOSecure.java			\
	$(SAMPLE_DST_DIR)/Content.java				\
	$(SAMPLE_DST_DIR)/Dispatcher.java			\
	$(SAMPLE_DST_DIR)/Dispatcher1.java			\
	$(SAMPLE_DST_DIR)/DispatcherN.java			\
	$(SAMPLE_DST_DIR)/FileContent.java			\
	$(SAMPLE_DST_DIR)/Handler.java				\
	$(SAMPLE_DST_DIR)/MalformedRequestException.java	\
	$(SAMPLE_DST_DIR)/N1.java				\
	$(SAMPLE_DST_DIR)/N2.java				\
	$(SAMPLE_DST_DIR)/README.txt				\
	$(SAMPLE_DST_DIR)/Reply.java				\
	$(SAMPLE_DST_DIR)/Request.java				\
	$(SAMPLE_DST_DIR)/RequestHandler.java			\
	$(SAMPLE_DST_DIR)/RequestServicer.java			\
	$(SAMPLE_DST_DIR)/Sendable.java				\
	$(SAMPLE_DST_DIR)/Server.java				\
	$(SAMPLE_DST_DIR)/StringContent.java			\
	$(SAMPLE_DST_DIR)/URLDumper.java

all build: $(SAMPLE_FILES)

$(SAMPLE_DST_DIR)/%: $(SAMPLE_SRC_DIR)/%
	$(install-file)

clean clobber:
	$(RM) -r $(SAMPLE_DST_DIR)

.PHONY: all build clean clobber
