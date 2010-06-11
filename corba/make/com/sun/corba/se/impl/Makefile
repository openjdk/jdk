#
# Copyright (c) 1999, 2005, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building all of java
#

BUILDDIR = ../../../../..
PRODUCT = impl
include $(BUILDDIR)/common/Defs.gmk

# order is important

ifdef STANDALONE_CORBA_WS
SUBDIRS = oa naming activation txpoa interceptors logging monitoring
else
SUBDIRS = oa naming activation       interceptors logging monitoring
endif

LOG_GENDIRECTORY          = $(GENSRCDIR)/com/sun/corba/se/impl/logging
LOG_CLASS_OUTPUTDIRECTORY = $(CLASSBINDIR)/com/sun/corba/se/impl/logging
LOGSTRINGS_PROPERTIES     = LogStrings.properties
all build clean clobber::
	$(SUBDIRS-loop)

# Copy the generated LogStrings.properties to the Classes directory
all build:: $(LOG_CLASS_OUTPUTDIRECTORY)/$(LOGSTRINGS_PROPERTIES)

$(LOG_CLASS_OUTPUTDIRECTORY)/$(LOGSTRINGS_PROPERTIES): $(LOG_GENDIRECTORY)/$(LOGSTRINGS_PROPERTIES)
	$(install-file)

