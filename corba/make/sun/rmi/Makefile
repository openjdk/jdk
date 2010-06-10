#
# Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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
# Build rmi tools, and sun.rmi.* implementation classes
#
BUILDDIR = ../..
include $(BUILDDIR)/common/Defs.gmk

LOG_GENDIRECTORY  = $(GENSRCDIR)/com/sun/corba/se/impl/logging
LOG_CLASS_OUTPUTDIRECTORY = $(CLASSBINDIR)/com/sun/corba/se/impl/logging
LOGSTRINGS_PROPERTIES     = LogStrings.properties
#
# Order is important -- you can't build stubs without building rmic first.
# We need corbalogsources to be built first, since it builds LogWrappers to
# log the exceptions and generate the right minorcodes. These classes are
# references in com/sun/corba/se/impl/io and util classes which in turn will
# be built from rmic Makefile.
#
CORBALOG_SUBDIRS = corbalogsources corbalogcompile rmic

# Do these subdirs first
OTHERSUBDIRS=$(CORBALOG_SUBDIRS)
all build clean clobber ::
	$(OTHERSUBDIRS-loop)

$(LOG_CLASS_OUTPUTDIRECTORY)/$(LOGSTRINGS_PROPERTIES):  $(LOG_GENDIRECTORY)/$(LOGSTRINGS_PROPERTIES)
	$(install-file)

# Then do the rest
all build clean clobber :: $(LOG_CLASS_OUTPUTDIRECTORY)/$(LOGSTRINGS_PROPERTIES)
	$(SUBDIRS-loop)

clean::
	$(RM) -r $(CLASSBINDIR)/sun/rmi $(CLASSBINDIR)/com/sun/rmi
