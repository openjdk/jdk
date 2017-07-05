#
# Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building com/sun
#

BUILDDIR = ../..
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

ifndef OPENJDK
  ORG_EXISTS := $(call DirExists,$(CLOSED_SRC)/share/classes/sun/org,,)
  ifneq ("$(ORG_EXISTS)", "") 
    SCRIPT_SUBDIR = script
  endif
endif

# jarsigner is part of JRE
SUBDIRS = java security net/ssl jarsigner

SUBDIRS_management = jmx
SUBDIRS_desktop    = image
SUBDIRS_enterprise = crypto/provider jndi \
                     org xml rowset net/httpserver
SUBDIRS_misc       = $(SCRIPT_SUBDIR) tracing servicetag nio demo

# Omit mirror since it's built with the apt tool.
SUBDIRS_tools      = tools

include $(BUILDDIR)/common/Subdirs.gmk

all build clean clobber::
	$(SUBDIRS-loop)

