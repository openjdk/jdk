#
# Copyright (c) 2006, 2011, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for the dtrace sample code
#

BUILDDIR = ../..

PRODUCT = java

include $(BUILDDIR)/common/Defs.gmk

SAMPLE_SRC_DIR = $(PLATFORM_SRC)/sample/dtrace
SAMPLE_DST_DIR = $(SAMPLEDIR)/dtrace

SAMPLE_FILES =									\
	$(SAMPLE_DST_DIR)/README.txt             				\
	$(SAMPLE_DST_DIR)/helpers/dtrace_helper.d				\
	$(SAMPLE_DST_DIR)/hotspot_jni/README.txt				\
	$(SAMPLE_DST_DIR)/hotspot_jni/CriticalSection.d				\
	$(SAMPLE_DST_DIR)/hotspot_jni/CriticalSection_slow.d			\
	$(SAMPLE_DST_DIR)/hotspot_jni/hotspot_jni_calls_stat.d			\
	$(SAMPLE_DST_DIR)/hotspot_jni/hotspot_jni_calls_tree.d			\
	$(SAMPLE_DST_DIR)/hotspot/README.txt					\
	$(SAMPLE_DST_DIR)/hotspot/class_loading_stat.d				\
	$(SAMPLE_DST_DIR)/hotspot/gc_time_stat.d				\
	$(SAMPLE_DST_DIR)/hotspot/hotspot_calls_tree.d				\
	$(SAMPLE_DST_DIR)/hotspot/method_compile_stat.d				\
	$(SAMPLE_DST_DIR)/hotspot/method_invocation_stat.d			\
	$(SAMPLE_DST_DIR)/hotspot/method_invocation_stat_filter.d		\
	$(SAMPLE_DST_DIR)/hotspot/method_invocation_tree.d			\
	$(SAMPLE_DST_DIR)/hotspot/monitors.d					\
	$(SAMPLE_DST_DIR)/hotspot/object_allocation_stat.d


all build: $(SAMPLE_FILES)

$(SAMPLE_DST_DIR)/%: $(SAMPLE_SRC_DIR)/%
	$(install-file)

clean clobber:
	$(RM) -r $(SAMPLE_DST_DIR)

.PHONY: all build clean clobber
