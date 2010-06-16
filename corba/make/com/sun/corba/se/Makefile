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

BUILDDIR = ../../../..
PRODUCT = se
include $(BUILDDIR)/common/Defs.gmk

# IDL code generation is moved out to an extra step before the
# actual classes are compiled
# the files should be compiled in the following order, core to be built
# before connection

# Note that sources generates the logging wrappers, and pept compiles
# them, because pept is the earliest build target that can use
# the log wrappers.  Note that rmi is moved here after pept because
# the rmic iiop backend needs some classes that are part of the impl/util
# package.  These classes use log wrappers, so they must be built after
# pept and before rmi.
SUBDIRS = sources pept corespi core spi impl rmi
all build clean clobber::
	$(SUBDIRS-loop)

