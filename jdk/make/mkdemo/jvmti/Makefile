#
# Copyright (c) 2004, 2010, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building the jvmti demo(s)
#

BUILDDIR = ../..
PRODUCT = demos
include $(BUILDDIR)/common/Defs.gmk

# Can be built in any order, the JRE version of hprof and java_crw_demo are
#   really built in make/java.
#   The hprof target here just delivers the sources and README files.
#   The java_crw_demo and agent_util files are copied into each demo that
#   uses them.
SUBDIRS = \
	  versionCheck \
	  compiledMethodLoad \
	  gctest \
	  heapViewer \
	  heapTracker \
	  minst \
	  mtrace \
	  waiters \
	  hprof

include $(BUILDDIR)/common/Subdirs.gmk

all build clean clobber::
	$(SUBDIRS-loop)

all build :: $(DEMODIR)/jvmti/index.html

$(DEMODIR)/jvmti/index.html: $(SHARE_SRC)/demo/jvmti/index.html
	$(install-file)

